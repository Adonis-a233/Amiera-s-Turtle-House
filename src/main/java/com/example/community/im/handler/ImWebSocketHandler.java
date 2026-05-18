package com.example.community.im.handler;

import com.example.community.entity.User;
import com.example.community.im.config.ImMqConfig;
import com.example.community.im.dto.ImMessageDTO;
import com.example.community.im.entity.ImConversation;
import com.example.community.im.entity.ImMessage;
import com.example.community.im.filter.SensitiveWordFilter;
import com.example.community.im.mapper.ConversationMapper;
import com.example.community.im.mapper.MessageMapper;
import com.example.community.im.service.ImKeyService;
import com.example.community.im.utils.AesUtil;
import com.example.community.im.vo.ImMessageVO;
import com.example.community.mapper.ContactMapper;
import com.example.community.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ImWebSocketHandler extends TextWebSocketHandler {

    private static final ConcurrentHashMap<String, WebSocketSession> ONLINE_SESSIONS =
            new ConcurrentHashMap<>();

    /**
     * 每个在线用户最后一次活跃时间（毫秒）。
     * 在消息收发、Pong 响应时更新；心跳任务据此判断连接是否存活。
     */
    private static final ConcurrentHashMap<String, Long> LAST_ACTIVE_TIME =
            new ConcurrentHashMap<>();

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 离线补发每批推送条数，防止一次性塞满 WebSocket 缓冲区 */
    private static final int OFFLINE_BATCH_SIZE = 50;

    /** 心跳超时阈值（毫秒）：超过此时间没有任何活跃迹象，视为死连接 */
    private static final long DEAD_THRESHOLD_MS = 90_000L;

    @Resource
    private ContactMapper contactMapper;

    @Resource
    private ConversationMapper conversationMapper;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private UserService userService;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private SensitiveWordFilter sensitiveWordFilter;

    @Resource
    private ImKeyService imKeyService;

    @Resource
    @Qualifier("offlineMessageExecutor")
    private Executor offlineMessageExecutor;

    /** 向指定用户的在线 WebSocket 会话推送消息（静态工具方法，供其他 Bean 调用） */
    public static void sendToUser(String username, String message) {
        WebSocketSession session = ONLINE_SESSIONS.get(username);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                log.warn("推送消息给 {} 失败: {}", username, e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String username = (String) session.getAttributes().get("username");
        ONLINE_SESSIONS.put(username, session);
        LAST_ACTIVE_TIME.put(username, System.currentTimeMillis());
        log.info("WebSocket connected: {}", username);

        String userIp = (String) session.getAttributes().get("userIp");
        String userLocation = (String) session.getAttributes().get("userLocation");
        if (userIp != null) {
            stringRedisTemplate.opsForValue().set(
                    "user:ip:" + username,
                    userIp + "|" + (userLocation != null ? userLocation : "未知"),
                    24, TimeUnit.HOURS);
        }

        Long finalUserId = userService.getByUsername(username).getId();
        offlineMessageExecutor.execute(() -> pushOfflineMessages(username, finalUserId));
    }

    /**
     * 补发所有会话的未读消息。
     * 每个会话最多补发 200 条（LIMIT in SQL），超出部分用户通过 getHistory 上拉加载。
     * 补发按每批 50 条推送，避免一次性撑满 WebSocket 缓冲区。
     */
    private void pushOfflineMessages(String username, Long userId) {
        try {
            List<ImConversation> convs = conversationMapper.findMyConversations(userId);
            for (ImConversation conv : convs) {
                try {
                    Long lastReadSeq = conversationMapper.getLastReadSeq(conv.getId(), userId);
                    List<ImMessage> unread = messageMapper.selectUnreadSince(
                            conv.getId(), lastReadSeq == null ? 0L : lastReadSeq);

                    // 批量查 sender，消除 N+1
                    Set<Long> senderIds = unread.stream()
                            .map(ImMessage::getSenderId)
                            .collect(Collectors.toSet());
                    Map<Long, User> senderMap = userService.listByIds(senderIds).stream()
                            .collect(Collectors.toMap(User::getId, u -> u));

                    // 分批推送（每批 OFFLINE_BATCH_SIZE 条）
                    for (int i = 0; i < unread.size(); i += OFFLINE_BATCH_SIZE) {
                        List<ImMessage> batch = unread.subList(i,
                                Math.min(i + OFFLINE_BATCH_SIZE, unread.size()));
                        for (ImMessage m : batch) {
                            User sender = senderMap.get(m.getSenderId());
                            if (sender == null) continue;
                            try {
                                String voJson = objectMapper.writeValueAsString(buildVO(m, sender));
                                sendToUser(username, voJson);
                            } catch (Exception e) {
                                log.warn("离线消息补发单条失败 msgId={}: {}", m.getId(), e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("离线消息补发会话 convId={} 失败: {}", conv.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("离线消息补发整体失败 userId={}: {}", userId, e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String username = (String) session.getAttributes().get("username");
        // 收到任何消息都刷新活跃时间，防止误判死连接
        LAST_ACTIVE_TIME.put(username, System.currentTimeMillis());

        ImMessageDTO dto = objectMapper.readValue(message.getPayload(), ImMessageDTO.class);

        // ── 控制消息：recv_ack ──────────────────────────────────────────────────
        // 接收方应用层收到并渲染消息后，发 {"controlType":"recv_ack","conversationId":x,"seq":42}
        // 服务端将该消息从 im:pending:{username} 集合中移除，表示下行已确认。
        if ("recv_ack".equals(dto.getControlType())) {
            if (dto.getConversationId() != null && dto.getSeq() != null) {
                String pendingKey = "im:pending:" + username;
                stringRedisTemplate.opsForSet().remove(
                        pendingKey, dto.getConversationId() + ":" + dto.getSeq());
            }
            return;
        }

        // ── 幂等检查（TTL 24小时，覆盖手机断网后恢复重发场景） ────────────────
        if (dto.getClientMsgId() != null) {
            String dedupKey = "im:dedup:" + dto.getClientMsgId();
            Boolean isNew = stringRedisTemplate.opsForValue()
                    .setIfAbsent(dedupKey, "1", 24, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                session.sendMessage(new TextMessage(
                    "{\"type\":\"ack\",\"status\":\"ok\",\"duplicate\":true,\"clientMsgId\":\""
                    + dto.getClientMsgId() + "\"}"));
                return;
            }
        }

        User sender = userService.getByUsername(username);
        Long senderId = sender.getId();

        // ── 确定 convId 和 receiverId ──────────────────────────────────────────
        // 客户端可以只传 convId（复用已有会话），也可以只传 receiverId（首次发消息）。
        // 群聊消息只传 convId，receiverId 为 null，跳过单聊相关检查。
        Long convId = dto.getConversationId();
        Long receiverId = dto.getReceiverId();

        if (convId == null && receiverId == null) {
            session.sendMessage(new TextMessage("{\"error\":\"必须提供 conversationId 或 receiverId\"}"));
            return;
        }

        // 已有 convId 但没有 receiverId：从单聊会话里反查对端 userId
        if (convId != null && receiverId == null) {
            ImConversation existingConv = conversationMapper.findById(convId);
            if (existingConv != null && existingConv.getType() == 1) {
                receiverId = existingConv.getParticipantA().equals(senderId)
                        ? existingConv.getParticipantB()
                        : existingConv.getParticipantA();
            }
        }

        // 只有 receiverId，尝试找已有单聊会话（不创建，后面再决定是否创建）
        if (convId == null) {
            convId = conversationMapper.findSingleConversation(senderId, receiverId);
        }

        // ── 拉黑检查（前移，无论是否已有会话每次都检查） ─────────────────────
        // 旧代码只在 convId==null 分支检查，已有会话路径可绕过。
        // 现在：B 拉黑 A 之后，即使已有会话，A 也无法再发消息。
        if (receiverId != null && contactMapper.isBlocked(senderId, receiverId)) {
            session.sendMessage(new TextMessage(
                    "{\"error\":\"无法发送消息，对方已拉黑您或您已拉黑对方\"}"));
            return;
        }

        // ── 互关检查（单聊）：未互关时只允许发第一条消息 ─────────────────────
        // 业务语义：陌生人可以发一条"打招呼"，双方互关后才能正常聊天。
        // selectMaxSeq>0 说明该会话已有历史消息，即打招呼已发过，拒绝继续。
        if (receiverId != null) {
            boolean mutual = contactMapper.isMutualFollow(senderId, receiverId);
            if (!mutual) {
                long existingMsgCount = (convId != null) ? messageMapper.selectMaxSeq(convId) : 0L;
                if (existingMsgCount > 0) {
                    session.sendMessage(new TextMessage(
                            "{\"error\":\"请先互相关注才能继续聊天\"}"));
                    return;
                }
            }
        }

        // ── 创建新会话（只有在上面所有检查通过且依然没有 convId 时才执行） ────
        if (convId == null) {
            String plainDek;
            String encryptedDek = null;
            try {
                plainDek = AesUtil.generateKey();
                encryptedDek = imKeyService.encryptDek(plainDek);
            } catch (Exception e) {
                log.error("会话密钥生成/加密失败", e);
                plainDek = null;
            }
            conversationMapper.createSingleConversation(senderId, receiverId, encryptedDek);
            convId = conversationMapper.findSingleConversation(senderId, receiverId);
            if (plainDek != null && convId != null) {
                stringRedisTemplate.opsForValue().set(
                        "conv:key:" + convId, plainDek, 1, TimeUnit.HOURS);
            }
        }

        // 获取会话密钥（优先读 Redis 缓存明文，否则查库解密后缓存）
        String secretKey = getSecretKeyWithCache(convId);

        // 文本消息：解密 → 敏感词过滤 → 重新加密
        String finalContent = dto.getContent();
        if (Integer.valueOf(1).equals(dto.getType()) && secretKey != null && finalContent != null) {
            try {
                String plaintext = AesUtil.decrypt(finalContent, secretKey);
                String filtered = sensitiveWordFilter.filter(plaintext);
                finalContent = AesUtil.encrypt(filtered, secretKey);
            } catch (Exception e) {
                log.warn("消息加解密失败: {}", e.getMessage());
            }
        }

        ImMessage msg = new ImMessage();
        msg.setConversationId(convId);
        msg.setSenderId(senderId);
        msg.setType(dto.getType());
        msg.setContent(finalContent);
        msg.setImageUrl(dto.getImageUrl());
        msg.setSendTime(LocalDateTime.now());
        msg.setRecalled(false);

        // 分配会话内单调递增序号（Redis 不可用时降级到本地时间戳）
        Long seq = getNextSeq(convId);
        msg.setSeq(seq);
        msg.setClientMsgId(dto.getClientMsgId());

        String msgJson = objectMapper.writeValueAsString(msg);

        // 热数据存 Redis（密文）
        String redisKey = "im:conv:" + convId;
        stringRedisTemplate.opsForList().leftPush(redisKey, msgJson);
        stringRedisTemplate.opsForList().trim(redisKey, 0, 199);

        // 更新会话最后一条消息
        String lastMsgContent = (dto.getType() != null && dto.getType() == 2)
                ? "[图片]"
                : finalContent;
        conversationMapper.updateLastMsg(convId, lastMsgContent, msg.getSendTime(), dto.getType());

        // 投递 MQ（落库存密文）
        rabbitTemplate.convertAndSend(ImMqConfig.EXCHANGE, ImMqConfig.ROUTING_KEY, msgJson);

        // 推送给接收方（密文，前端用持有密钥解密展示）
        ImMessageVO vo = buildVO(msg, sender);
        String voJson = objectMapper.writeValueAsString(vo);
        List<Long> recipientIds = getRecipientIds(convId, senderId);
        pushAndNotifyUnread(convId, recipientIds, voJson, seq);

        String ack = String.format(
                "{\"type\":\"ack\",\"status\":\"ok\",\"clientMsgId\":\"%s\",\"seq\":%d}",
                dto.getClientMsgId() != null ? dto.getClientMsgId() : "",
                msg.getSeq());
        session.sendMessage(new TextMessage(ack));
    }

    /**
     * 收到客户端 Pong 帧时刷新活跃时间。
     * Spring WebSocket 在收到 PONG 帧后自动调用此方法（服务端发 PING，客户端回 PONG）。
     */
    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        String username = (String) session.getAttributes().get("username");
        if (username != null) {
            LAST_ACTIVE_TIME.put(username, System.currentTimeMillis());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String username = (String) session.getAttributes().get("username");
        if (username != null) {
            ONLINE_SESSIONS.remove(username);
            LAST_ACTIVE_TIME.remove(username);
            log.info("WebSocket disconnected: {}", username);
        }
    }

    /**
     * 心跳任务：每30秒向所有在线会话发一次 Ping 帧；
     * 同时检查是否有超过90秒没有任何活跃迹象的死连接，发现则主动关闭并移除。
     *
     * 为什么需要心跳：客户端意外断网时不发 TCP FIN，服务端 TCP 层要等
     * keepalive 超时（默认2小时）才感知断连。这期间 ONLINE_SESSIONS 保留着死 session，
     * 所有推送都会写入死连接的发送 Buffer 然后静默丢失（IOException 被 catch 吞掉），
     * 且用户被误认为在线，不触发离线补发。心跳让断连感知时间从2小时缩短到90秒内。
     */
    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        long now = System.currentTimeMillis();
        ONLINE_SESSIONS.forEach((username, session) -> {
            Long lastActive = LAST_ACTIVE_TIME.get(username);
            if (lastActive != null && (now - lastActive) > DEAD_THRESHOLD_MS) {
                log.warn("心跳超时，主动关闭死连接: {}", username);
                try {
                    session.close(CloseStatus.SESSION_NOT_RELIABLE);
                } catch (Exception ignored) {}
                ONLINE_SESSIONS.remove(username);
                LAST_ACTIVE_TIME.remove(username);
                return; // 已关闭，不再发 Ping
            }
            if (session.isOpen()) {
                try {
                    session.sendMessage(new PingMessage());
                } catch (Exception e) {
                    log.warn("发送 Ping 失败 {}: {}", username, e.getMessage());
                }
            }
        });
    }

    /**
     * 优先读 Redis 明文缓存；缺失时从 DB 读加密 DEK 并用 KEK 解密后回写缓存。
     */
    private String getSecretKeyWithCache(Long convId) {
        String cacheKey = "conv:key:" + convId;
        String secretKey = stringRedisTemplate.opsForValue().get(cacheKey);
        if (secretKey == null) {
            String encryptedDek = conversationMapper.selectSecretKey(convId);
            if (encryptedDek != null) {
                try {
                    secretKey = imKeyService.decryptDek(encryptedDek);
                } catch (Exception e) {
                    log.error("DEK 解密失败 convId={}: {}", convId, e.getMessage());
                    return null;
                }
                stringRedisTemplate.opsForValue().set(cacheKey, secretKey, 1, TimeUnit.HOURS);
            }
        }
        return secretKey;
    }

    private List<Long> getRecipientIds(Long convId, Long senderId) {
        ImConversation conv = conversationMapper.findById(convId);
        if (conv == null) return List.of();

        List<Long> recipientIds = new ArrayList<>();
        if (conv.getType() == 1) {
            Long other = conv.getParticipantA().equals(senderId)
                    ? conv.getParticipantB()
                    : conv.getParticipantA();
            recipientIds.add(other);
        } else {
            for (Long id : conversationMapper.findGroupMemberIds(convId)) {
                if (!id.equals(senderId)) recipientIds.add(id);
            }
        }
        return recipientIds;
    }

    /**
     * 推送消息并更新接收方未读数（Redis HINCRBY）。
     * 同时将本次推送写入 im:pending:{username} 集合跟踪下行投递状态：
     *   - 接收方应用层收到后发 recv_ack → 从集合移除（确认下行成功）
     *   - 未 ACK 的消息在用户重连时通过 selectUnreadSince 兜底补发
     */
    private void pushAndNotifyUnread(Long convId, List<Long> recipientIds, String voJson, Long seq) {
        for (Long recipientId : recipientIds) {
            User recipient = userService.getById(recipientId);
            if (recipient == null) continue;

            // 推送消息密文
            sendToUser(recipient.getUsername(), voJson);

            // 记录待确认推送：接收方 recv_ack 后移除，否则重连时兜底补发
            String pendingKey = "im:pending:" + recipient.getUsername();
            stringRedisTemplate.opsForSet().add(pendingKey, convId + ":" + seq);
            stringRedisTemplate.expire(pendingKey, 24, TimeUnit.HOURS);

            // Redis HINCRBY 累加未读数（原子操作，无需查 DB）
            String unreadHashKey = "im:unread:" + recipientId;
            Long unreadCount = stringRedisTemplate.opsForHash()
                    .increment(unreadHashKey, String.valueOf(convId), 1);

            // 在线时推送未读数更新
            WebSocketSession recipientSession = ONLINE_SESSIONS.get(recipient.getUsername());
            if (recipientSession != null && recipientSession.isOpen()) {
                String unreadJson = String.format(
                        "{\"type\":\"unread_update\",\"convId\":%d,\"count\":%d}",
                        convId, unreadCount);
                sendToUser(recipient.getUsername(), unreadJson);
            }
        }
    }

    /**
     * seq 计数器冷启动初始化。
     * Redis 不可用时降级到本地时间戳（毫秒 * 10000 + 随机4位），
     * 保证单条消息不因 Redis 故障而阻塞；降级 seq 不严格单调递增，
     * 客户端排序可能出现小幅乱序，属已知 trade-off。
     */
    private Long getNextSeq(Long convId) {
        String seqKey = "im:seq:" + convId;
        try {
            if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(seqKey))) {
                long maxSeq = messageMapper.selectMaxSeq(convId);
                stringRedisTemplate.opsForValue().setIfAbsent(seqKey, String.valueOf(maxSeq));
            }
            return stringRedisTemplate.opsForValue().increment(seqKey);
        } catch (Exception e) {
            log.warn("Redis seq 获取失败，降级本地时间戳: convId={}", convId, e);
            return System.currentTimeMillis() * 10000L + (long) (Math.random() * 10000);
        }
    }

    private ImMessageVO buildVO(ImMessage msg, User sender) {
        ImMessageVO vo = new ImMessageVO();
        vo.setConversationId(msg.getConversationId());
        vo.setSenderId(msg.getSenderId());
        vo.setType(msg.getType());
        vo.setContent(msg.getContent());
        vo.setImageUrl(msg.getImageUrl());
        vo.setSenderName(sender.getUsername());
        vo.setSenderAvatar(sender.getAvatar());
        vo.setSendTime(msg.getSendTime().format(FORMATTER));
        vo.setRecalled(false);
        vo.setSeq(msg.getSeq());
        return vo;
    }
}
