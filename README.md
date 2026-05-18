# Craving 美食社区平台 — 项目介绍与技术设计文档

> 技术栈：Spring Boot 3.3.3 · Java 21 · MyBatis-Plus · MySQL · Redis · RabbitMQ · Elasticsearch · WebSocket

---

## 一、项目概述

Craving 是一个以美食为主题的内容社区平台，提供文章发布、评论互动、用户关注、收藏等基础社交功能，并在此之上构建了三个技术亮点子模块：

| 模块            | 核心能力                                                     |
| --------------- | ------------------------------------------------------------ |
| **IM 即时通讯** | WebSocket 实时收发、AES 端对端加密、RabbitMQ 异步落库、离线补发、敏感词过滤 |
| **AI 搜索**     | 文本/图片/混合三模态向量搜索（Elasticsearch kNN + RRF 融合） |
| **AI 推荐**     | 多路召回 → RRF 融合 → DIN 近似打分 → 打散过滤的全链路推荐流水线 |

---

## 二、IM 即时通讯模块

### 2.1 整体架构

```
客户端 (WebSocket)
    │  JWT 握手鉴权
    ▼
JwtHandshakeInterceptor
    │
    ▼
ImWebSocketHandler
    ├─ 幂等去重（Redis im:dedup:{clientMsgId}，TTL 24h）
    ├─ 拉黑/互关检查
    ├─ AES 解密 → 敏感词过滤 → 重新加密
    ├─ Redis 热数据（im:conv:{convId} 最近200条）
    ├─ RabbitMQ 异步落库
    └─ 推送接收方 + 未读数更新（im:unread:{userId}）
            │
            ▼
    ImMessageConsumer（批量刷盘，手动 ACK）
            │
            ▼
        MySQL im_message 表
```

### 2.2 消息可靠性设计（可达有序、不重不漏）

#### 上行可靠 — 幂等去重

客户端每条消息携带 `clientMsgId`（客户端生成的 UUID），服务端用 `Redis SETNX` 检查：

```
im:dedup:{clientMsgId}  TTL=24h
```

若 key 已存在说明重复投递，直接回 `{"duplicate":true}` ACK，不进入任何业务逻辑。这覆盖了网络抖动导致的客户端重发场景。

#### 下行可靠 — recv_ack 机制

每次推送消息时，服务端同步将 `convId:seq` 写入 `im:pending:{username}`（Redis Set，TTL 24h）：

```
SADD im:pending:{username}  {convId}:{seq}
```

接收方应用层渲染消息后，发送控制帧 `{"controlType":"recv_ack","conversationId":x,"seq":42}`，服务端将该条目从 pending set 移除。用户重连时，`pushOfflineMessages` 通过 `selectUnreadSince` 扫描 DB 兜底补发未 ACK 消息，形成完整的下行确认回路。

#### 消息时序 — 单调递增 seq

每个会话在 Redis 维护独立计数器 `im:seq:{convId}`，每条消息 `INCR` 后将 seq 写入消息体。Redis 不可用时降级到本地时间戳，客户端按 seq 排序保证展示顺序。

```java
// seq 冷启动：Redis 重启后从 DB 最大 seq 继续，避免与历史数据重叠
if (!stringRedisTemplate.hasKey(seqKey)) {
    long maxSeq = messageMapper.selectMaxSeq(convId);
    stringRedisTemplate.opsForValue().setIfAbsent(seqKey, String.valueOf(maxSeq));
}
return stringRedisTemplate.opsForValue().increment(seqKey);
```

### 2.3 端对端加密设计

采用 **信封加密（KEK + DEK）** 方案：

- **DEK（Data Encryption Key）**：每个会话独立的 AES-256 密钥，用于加密消息内容
- **KEK（Key Encryption Key）**：服务端持有的主密钥，用于加密 DEK 后存 DB

```
会话创建时：
  plainDek = AesUtil.generateKey()          // AES-256 随机密钥
  encryptedDek = imKeyService.encryptDek(plainDek)  // KEK 加密
  存 DB: im_conversation.secret_key = encryptedDek

消息收发时：
  从 Redis 读 plainDek（缓存1h），否则从 DB 取 encryptedDek → KEK 解密
  AES-CBC/PKCS5 加解密消息内容（IV 随机生成，随密文一起传输）
```

这样 DB 中存储的是 DEK 密文而非明文，即使 DB 泄露也无法直接解密消息历史，同时服务端仍能进行敏感词过滤（解密 → 过滤 → 重加密）。

### 2.4 消息持久化 — 写扩散模型

采用**写扩散**（单份存储）：每条消息在 `im_message` 表中只写一条记录，通过 `conversation_id` 关联。相比读扩散（每个接收者各存一份），写扩散在群聊场景下大幅节省存储，代价是查询时需 JOIN 或在应用层组装。

**Redis + MySQL 双层存储**：

- **热数据**：`im:conv:{convId}` 存最近 200 条（`LPUSH + LTRIM`），读聊天记录优先命中 Redis
- **冷数据**：RabbitMQ 消费者批量刷盘到 MySQL，每 20 条或每 500ms 触发一次批量 INSERT

### 2.5 RabbitMQ 异步落库

消费者 `ImMessageConsumer` 使用批量缓冲区减少 DB 写入次数：

```
收到消息 → 加入 buffer（最多20条）→ 批量 batchInsert → 逐条 basicAck
                                           │
                              失败时读 x-retry-count header
                              < 3次：basicAck 原消息 + republish（计数+1）
                              ≥ 3次：路由进 DLQ
```

使用自定义 `x-retry-count` header 而非 `x-death`，原因：`requeue=true` 场景下 `x-death` 不自增，会导致死循环重试。

### 2.6 离线补发

用户重连后，`offlineMessageExecutor`（虚拟线程池）异步执行补发：

- 批量查询该用户所有会话的 `lastReadSeq`，找出未读消息
- **批量加载 sender 信息**（`Map<Long, User>`），消除 N+1 查询
- 分批（每批50条）推送，避免一次性打满 WebSocket 缓冲区
- 外层/内层双重 try-catch，单个会话失败不影响其余会话补发

### 2.7 心跳保活

服务端每 30s 向所有在线连接发 `PingMessage`，同时检查 `LAST_ACTIVE_TIME`：超过 90s 无任何活跃迹象（消息收发或 Pong 响应）则主动关闭死连接并从 `ONLINE_SESSIONS` 移除，将断连感知时间从 TCP keepalive 的 2 小时缩短到 90s 内。

### 2.8 用户保护机制

| 场景       | 策略                                                         |
| ---------- | ------------------------------------------------------------ |
| 拉黑       | 每次发消息前检查双向拉黑（包括已有会话路径），拒绝发送       |
| 陌生人骚扰 | 未互关时只允许发第一条消息（打招呼），`selectMaxSeq > 0` 则拒绝 |
| 敏感词     | AC 自动机（Trie）过滤，`AtomicReference<Trie>` 实现无锁热更新 |
| IP 归属地  | 连接建立时解析并写 Redis（`user:ip:{username}`，TTL 24h）    |

### 2.9 服务端推送方案选型

| 方案          | 优点                     | 缺点                             |
| ------------- | ------------------------ | -------------------------------- |
| 短轮询        | 实现简单                 | 高频请求浪费资源，延迟高         |
| 长轮询        | 降低无效请求             | 连接数依然高，推送延迟仍存在     |
| **WebSocket** | 全双工，低延迟，连接复用 | 需维护连接状态，多实例需额外协调 |

选择 WebSocket：聊天场景要求毫秒级延迟和双向通信，WebSocket 一次握手后保持长连接，服务端可主动推送，是 IM 场景的标准选择。

---

## 三、AI 搜索模块

### 3.1 三模态向量搜索

索引：Elasticsearch `posts`，向量字段 `textVector` / `imageVector`（1024维，DashScope `qwen3-vl-embedding`）。

| 模式       | 流程                                                       |
| ---------- | ---------------------------------------------------------- |
| **TEXT**   | 查询扩展（LLM 改写扩充） → 文本 embedding → 单路 kNN       |
| **IMAGE**  | 食物分类检查（非食物直接拒绝） → 图片 embedding → 单路 kNN |
| **HYBRID** | 文本 kNN + 图片 kNN + ES 原生 RRF 融合排序                 |

使用原生 `ElasticsearchClient` 而非 Spring Data ES，原因：Spring Data ES 高阶封装不支持 RRF 的 `rank` 参数配置。

### 3.2 查询扩展

TEXT 模式下，`QueryExpansionService` 调用 Mimo/Qwen LLM 将用户查询改写扩充（如"好吃的面"→"牛肉面、兰州拉面、热干面"），再做 embedding，提升召回覆盖率。

---

## 四、AI 推荐模块

### 4.1 推荐流水线

```
用户请求
    │
    ▼ 无历史行为
热榜 Top-N ──────────────────────────────────────► 返回

    │ 有历史行为
    ▼
多路召回
    ├─ 语义召回：用户历史 embedding 加权均值 → ES kNN（150条）
    ├─ 热榜召回：Redis 热榜 Top50
    └─ 曝光池召回：DB 曝光池（30条）
    │
    ▼
RRF 融合（合并三路候选，去重）
    │
    ▼
DIN 近似打分
    ├─ 注意力机制：候选文章 embedding 与用户历史向量逐一计算相似度，加权求和
    └─ 热度衰减：指数衰减（λ=0.02），越新的文章热度分越高
    │
    ▼
打散过滤
    ├─ 同作者不超过2篇
    ├─ 同标签不超过3篇
    └─ 已点赞/收藏帖子降权
    │
    ▼
返回 Top-20，异步写曝光日志
```

### 4.2 用户兴趣建模

`QwenMetaStrategyService` 离线分析近7天行为，调用 DashScope LLM 生成个性化推荐配置 `RecConfig`（权重 w1、兴趣衰减半衰期、多样性开关），存 Redis 供在线推荐读取。

---

## 五、问题清单与解决思路

### P0 — 必须解决（共5项）

#### P0-1：AES 密钥明文存 DB（已修复）

**问题**：原始设计将 DEK 明文存入 `im_conversation.secret_key`，任何有 DB 访问权限者可解密全部历史消息，服务端加密≠E2EE。

**解决方案**：引入信封加密。DEK 由 KEK 加密后存 DB（`encryptedDek`），Redis 缓存明文 DEK（TTL 1h），通信链路中不传输密钥。DB 泄露只暴露密文，需同时获取 KEK 才能解密。

---

#### P0-2：离线补发裸 `new Thread()` + N+1 查询（已修复）

**问题**：原始代码用 `new Thread()` 启动离线补发，无线程池管理，高并发上线场景会产生大量非托管线程；每条消息单独查 sender，N 条消息产生 N 次 DB 查询；外层无异常捕获，单条失败导致后续消息全部丢失。

**解决方案**：

- 改用 `offlineMessageExecutor`（配置好的虚拟线程池）执行补发任务
- 批量收集 `senderIds` → 一次 `listByIds` 查询，构建 `Map<Long, User>` 复用
- 外层捕获整体异常，内层按会话粒度隔离，单个会话失败不影响其他会话

---

#### P0-3：分页 `total` 写死为 `list.size()`（已修复）

**问题**：`ConversationServiceImpl` 返回分页结果时，`total` 字段直接取当页 `list.size()`，导致前端永远认为只有一页，第二页功能失效。

**解决方案**：执行独立的 `COUNT(*)` 查询获取真实总数，或使用 MyBatis-Plus `Page` 对象自动填充 `total`。

---

#### P0-4：MQ 重试计数依赖 `x-death`（已修复）

**问题**：原消费者通过读 `x-death` header 判断重试次数，但 `basicNack(requeue=true)` 不产生 `x-death` 记录，导致重试计数恒为 0，消息在死循环中无限重试；`buffer.clear()` 之后若 JVM 崩溃，消息已从内存移除但尚未 ACK，形成丢失窗口。

**解决方案**：

- 改用自定义 `x-retry-count` header，重发时在应用层自增并重新 publish
- `buffer.clear()` 前先 copy 到局部变量，ACK 之后再清理（现有实现已采用 `new ArrayList<>(buffer)` 拷贝）
- 超过最大重试次数（3次）后路由进 DLQ，人工排查

---

#### P0-5：会话列表 SQL O(2N) 相关子查询（待优化）

**问题**：`ConversationMapper.xml` 查询会话列表时，每行用相关子查询计算未读数，N=50 个会话产生 100 次索引扫描，随会话数线性增长。

**解决方案**：将未读数改为 Redis `HGETALL im:unread:{userId}` 批量读取，彻底消除 SQL 子查询；或改写为 LEFT JOIN `im_read_cursor` 聚合，从 O(2N) 子查询降为单次扫描。

---

### P1 — 高频追问（共8项）

#### P1-1：KEK 信封加密（已修复，见 P0-1）

IV 管理本身正确（随机生成，随密文一起存储），需能解释信封加密的威胁模型：KEK 泄露 ≠ 消息泄露，DEK 泄露仅影响单个会话。

---

#### P1-2：`ONLINE_SESSIONS` JVM 本地 Map，多实例部署跨节点消息丢失

**问题**：`ConcurrentHashMap<String, WebSocketSession>` 是 JVM 进程内状态，用户连接在节点A，但消息推送逻辑在节点B执行，`sendToUser` 找不到 session，消息静默丢失。

**解决方案（方向）**：引入 Redis Pub/Sub 或一致性哈希路由，将推送请求路由到持有该 session 的节点；或使用粘性会话（Nginx `ip_hash`）保证同一用户始终连同一节点（简单但有单点风险）。当前单实例部署下此问题不暴露。

---

#### P1-3：`followingCount` 并发竞态（TOCTOU）

**问题**：`ContactServiceImpl` 先查后写（check-then-act），并发关注请求可能绕过计数检查，导致 `followingCount` 被多加。

**解决方案**：改为数据库原子操作（`UPDATE ... SET following_count = following_count + 1 WHERE id = ? AND following_count < limit`）或加分布式锁。

---

#### P1-4：Token 在 WebSocket URL 中传输

**问题**：`JwtHandshakeInterceptor` 从 URL 参数读取 Token，Token 会出现在 Nginx access log、浏览器历史、Referer 头中，存在泄露风险。

**解决方案**：握手完成后通过首条 WebSocket 消息传递 Token 进行二次鉴权，或使用 `Sec-WebSocket-Protocol` 子协议头传递（浏览器 `WebSocket` API 支持该 header）。

---

#### P1-5：每次推送都查 `countUnread`（已修复）

**问题**：原代码每次推送消息时执行 `COUNT(*)` 查询计算未读数，高频消息场景产生大量 DB 查询。

**解决方案**：改用 Redis `HINCRBY im:unread:{userId} {convId} 1` 原子递增，读取时 `HGET`，完全消除未读数相关的 DB 查询。

---

#### P1-6：已读游标用 DB 自增 `id` 而非 `seq`

**问题**：`im_read_cursor` 记录 `last_read_id`（DB 自增主键），批量插入时 id 分配顺序可能与消息到达顺序不一致，导致已读位置语义混乱。

**解决方案**：改用业务 `seq` 作为已读游标（`last_read_seq`），seq 由 Redis 单调递增保证顺序语义，与消息时序完全对齐。

---

#### P1-7：心跳配置（已修复）

**问题**：原始代码无心跳，客户端意外断网后服务端最长 2 小时才感知，`ONLINE_SESSIONS` 持有死 session，所有推送静默丢失，用户被误认为在线。

**解决方案**：`@Scheduled(fixedRate=30_000)` 发送 `PingMessage`，`LAST_ACTIVE_TIME` 记录最后活跃时间，超过 90s 主动关闭并移除 session。

---

#### P1-8：互关限制与拉黑二次检查（已修复）

**问题**：原始设计缺少陌生人保护；已有会话路径不检查拉黑，可绕过拉黑继续发消息。

**解决方案**：

- 拉黑检查**前移**到所有路径（包括已有会话），每次发消息都执行
- 未互关时允许发第一条消息（打招呼），`selectMaxSeq > 0` 则拒绝

---

### P2 — 优化/加分（共4项）

#### P2-1：`SensitiveWordFilter` 并发安全（已修复）

**问题**：`reload()` 用 `synchronized` 但 `filter()` 未加锁，热更新期间存在读写竞态。

**解决方案**：用 `AtomicReference<Trie>` 持有过滤器实例，`reload()` 构建新 Trie 后原子替换，`filter()` 读取引用无需加锁，实现无锁并发安全热更新。

---

#### P2-2：群聊密钥设计（设计局限，已知）

**问题**：群成员共用同一 DEK，成员退群后旧密钥仍在手，可解密退群前的历史消息，不具备前向保密性。

**正确方案**：参考 Signal Sender Keys 协议，退群时触发密钥轮换，新 DEK 只分发给现有成员，旧消息对退群成员永久不可解密。当前方案属已知 trade-off，实现复杂度较高。

---

#### P2-3：历史记录越权读取（已知风险）

**问题**：`ConversationServiceImpl` 查历史记录未校验调用者是否是该会话成员，理论上可构造请求读取任意会话消息。

**解决方案**：查询前校验 `SELECT 1 FROM im_conversation_member WHERE conv_id=? AND user_id=?`，或在 SQL 层加 `AND (participant_a=? OR participant_b=?)` 条件。

---

#### P2-4：Redis seq 计数器冷启动（已修复）

**问题**：原始代码 Redis 重启后 seq 从 1 重计，与已落库的 seq 重叠，导致消息排序混乱。

**解决方案**：`getNextSeq()` 首次初始化时从 DB 读取 `selectMaxSeq(convId)` 作为起点，用 `SETNX` 原子写入，确保 Redis 重启后 seq 从历史最大值续接。

---

## 六、并发与性能设计总结

| 场景       | 方案                                                    |
| ---------- | ------------------------------------------------------- |
| 离线补发   | 虚拟线程池（`offlineMessageExecutor`），非阻塞 I/O 友好 |
| 未读数统计 | Redis `HINCRBY` 原子操作，彻底去 DB                     |
| 消息落库   | RabbitMQ 批量缓冲（20条或500ms），减少 DB 写 IOPS       |
| 热聊天记录 | Redis List（最近200条），读聊天记录 P99 在内存完成      |
| 序号生成   | Redis `INCR`（原子，单调递增），无锁高并发              |
| 会话密钥   | Redis 缓存 DEK（TTL 1h），避免每条消息都查库+解密       |
| 心跳感知   | 30s 发 Ping + 90s 死连接检测，减少无效 session 持有     |
