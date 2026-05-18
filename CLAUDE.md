# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# 打包（跳过测试，pom.xml 已配置 skipTests=true）
mvn clean package

# 本地启动
mvn spring-boot:run

# 生产服务器启动（覆盖文件路径配置）
java -jar target/community-0.0.1-SNAPSHOT.jar \
  --file.upload-dir=/root/uploads/avatars \
  --file.access-prefix=http://115.29.214.88:8080/uploads/avatars
```

## 环境配置

项目通过 `spring-dotenv` 自动加载根目录的 `.env` 文件，需要配置以下变量（参考 `.env.example`）：

```
DB_PASSWORD=      # MySQL 密码
JWT_SECRET=       # JWT 密钥（至少256位）
MIMO_API_KEY=     # Mimo LLM API Key（首选 LLM）
DASHSCOPE_API_KEY= # 阿里云 DashScope Key（embedding + fallback LLM）
```

本地依赖服务：MySQL（3306）、Redis（6379）、Elasticsearch（9200）、RabbitMQ（5672，用户 `craving`）。

## 架构概览

项目是一个美食社区平台，Spring Boot 3.3.3 + Java 21，分三个主要子模块：

### 包结构

```
com.example.community
├── controller/service/mapper/entity/dto/vo/  # 核心业务（文章、评论、用户、收藏）
├── im/                                        # 即时通讯模块
├── search/                                    # 搜索模块
└── recommend/                                 # 推荐模块
```

### 核心业务层

标准分层：`Controller → Service → Mapper (MyBatis-Plus)`。所有 API 响应统一用 `Result<T>` 包装，全局异常由 `GlobalExceptionHandler` 处理。实体类使用 MyBatis-Plus 逻辑删除（`deleted` 字段）。

`@MapperScan` 只扫描 `com.example.community.mapper`，IM 子模块的 Mapper 需加 `@Mapper` 注解。

认证：JWT 无状态，`SecurityConfig` 内置过滤器（不能声明为 `@Bean`，否则双重注册）。只读接口公开，写操作需要认证，`/api/review/admin/**` 和 `/api/admin/sensitive/**` 需要 ADMIN 角色。

### IM 模块（`im/`）

WebSocket 实时通讯 + RabbitMQ 异步落库。

- `ImWebSocketHandler`：消息收发核心。消息流：WebSocket 接收 → 幂等去重（Redis `im:dedup:{clientMsgId}`）→ AES 解密 → 敏感词过滤 → 重新加密 → 存 Redis 热数据 + 投递 MQ + 推送接收方
- 会话密钥存 DB，Redis 缓存1小时（`conv:key:{convId}`）
- 在线 Session 用 `ConcurrentHashMap<String, WebSocketSession>` 维护（按 username）
- 用户上线时新开线程补发所有未读消息
- 消息序号：Redis `im:seq:{convId}` 单调递增
- RabbitMQ 消费者 `ImMessageConsumer` 异步写库（手动 ACK）

### 搜索模块（`search/`）

Elasticsearch 向量搜索，索引名 `posts`，字段 `textVector`/`imageVector`（1024维，DashScope `qwen3-vl-embedding`）。

三种模式（`PostSearchService`）：
- **TEXT**：查询扩展（`QueryExpansionService` → Mimo/Qwen LLM）→ 文本 embedding → 单路 kNN
- **IMAGE**：食物分类检查（`FoodClassifierService`，非食物直接拒绝）→ 图片 embedding → 单路 kNN
- **HYBRID**：双路 kNN + ES 原生 RRF 融合

使用原生 `ElasticsearchClient`（非 Spring Data ES），因为 Spring Data 封装不支持 RRF rank 参数。

### 推荐模块（`recommend/`）

多阶段推荐流水线（`RecommendServiceImpl`）：

1. **多路召回**：语义召回（ES kNN，用户历史 embedding 加权均值）+ 热榜召回（Redis）+ 曝光池召回（DB）
2. **RRF 融合**：合并三路候选
3. **DIN 近似打分**：每篇文章用注意力机制对比用户历史兴趣向量，加上热度衰减分（指数衰减）
4. **打散过滤**：同作者不超过2篇，同标签不超过3篇，已点赞/收藏帖子降级
5. **曝光日志**：异步写 `user_behavior` 表

`QwenMetaStrategyService`：离线异步调用 DashScope LLM 分析近7天行为，生成并写入 `RecConfig`（w1 权重、兴趣衰减半衰期、多样性开关）存 Redis，用于推荐打分。

### LLM / Embedding 服务

- **Mimo**（首选）：`DashScopeClient`，`mimo-v2.5` 模型，最大并发8个 slot，超出时降级到 DashScope
- **DashScope**：embedding（`qwen3-vl-embedding`，1024维）+ fallback LLM（`qwen3.6-flash`）
- **Ollama**：配置保留，Bean 存在，但当前不再主动调用

### 基础设施 Key 约定（Redis）

| Key 前缀 | 用途 |
|---|---|
| `im:dedup:{clientMsgId}` | 消息幂等去重（10分钟TTL） |
| `im:seq:{convId}` | 会话消息序号 |
| `im:conv:{convId}` | 会话最近200条消息热数据 |
| `conv:key:{convId}` | 会话 AES 密钥缓存（1小时TTL） |
| `user:ip:{username}` | 用户 IP 及归属地（24小时TTL） |

### 注意事项

- MyBatis-Plus 日志（`log-impl: StdOutImpl`）在生产环境已注释掉，本地调试时可开启
- 日志级别生产为 `info`，本地调试可改为 `debug`
- JVM 时区强制设为 `Asia/Shanghai`（`CommunityApplication.main`），避免 Linux 服务器 UTC 偏差
- 头像上传目录和访问前缀在本地和服务器不同，通过启动参数覆盖
