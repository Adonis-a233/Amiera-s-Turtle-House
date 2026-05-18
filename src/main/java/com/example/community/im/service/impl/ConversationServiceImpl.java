package com.example.community.im.service.impl;

import com.example.community.entity.User;
import com.example.community.im.entity.ImConversation;
import com.example.community.im.entity.ImGroupMember;
import com.example.community.im.entity.ImMessage;
import com.example.community.im.handler.ImWebSocketHandler;
import com.example.community.im.mapper.ConversationMapper;
import com.example.community.im.mapper.MessageMapper;
import com.example.community.im.service.ConversationService;
import com.example.community.im.service.ImKeyService;
import com.example.community.im.utils.AesUtil;
import com.example.community.im.vo.ConversationVO;
import com.example.community.im.vo.MessageVO;
import com.example.community.service.UserService;
import com.example.community.utils.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

    @Resource
    private ConversationMapper conversationMapper;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private UserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ImKeyService imKeyService;

    /**
     * P0-3 修复：total 改为 countMyConversations() 真实查询。
     * P0-5 修复：unreadCount 改从 Redis HASH 读取，SQL 不再跑 O(2N) 相关子查询。
     */
    @Override
    public PageResult<ConversationVO> getMyConversations(int page, int size) {
        Long userId = userService.getCurrentUserId();
        int offset = (page - 1) * size;
        long total = conversationMapper.countMyConversations(userId);
        List<ConversationVO> list = conversationMapper.selectMyConversations(userId, offset, size);
        if (!list.isEmpty()) {
            fillUnreadCounts(userId, list);
        }
        return new PageResult<>(total, list);
    }

    /**
     * 从 Redis HASH（im:unread:{userId}）批量读取未读数并填充进 VO。
     * 若 Redis 中某会话未命中（冷启动/重启），则从 DB 批量补查并回写 Redis。
     */
    private void fillUnreadCounts(Long userId, List<ConversationVO> list) {
        String hashKey = "im:unread:" + userId;
        List<String> fields = list.stream()
                .map(v -> String.valueOf(v.getId()))
                .collect(Collectors.toList());

        List<Object> vals = stringRedisTemplate.opsForHash().multiGet(hashKey, Collections.unmodifiableList(fields));

        // 收集 Redis 未命中的会话 ID，做冷启动补查
        List<Long> missingConvIds = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (vals.get(i) == null) {
                missingConvIds.add(list.get(i).getId());
            }
        }

        Map<Long, Long> dbCounts = Collections.emptyMap();
        if (!missingConvIds.isEmpty()) {
            List<Map<String, Object>> rows = conversationMapper.batchCountUnread(userId, missingConvIds);
            dbCounts = new HashMap<>();
            Map<String, String> redisEntries = new HashMap<>();
            for (Map<String, Object> row : rows) {
                Long convId = ((Number) row.get("convId")).longValue();
                Long cnt = ((Number) row.get("cnt")).longValue();
                dbCounts.put(convId, cnt);
                redisEntries.put(String.valueOf(convId), String.valueOf(cnt));
            }
            // 未出现在 DB 结果中的会话（无未读）也写入 Redis 为 0，避免下次重复查 DB
            for (Long convId : missingConvIds) {
                redisEntries.putIfAbsent(String.valueOf(convId), "0");
            }
            stringRedisTemplate.opsForHash().putAll(hashKey, redisEntries);
        }

        // 填充 unreadCount
        for (int i = 0; i < list.size(); i++) {
            ConversationVO vo = list.get(i);
            Object val = vals.get(i);
            if (val != null) {
                vo.setUnreadCount(Long.parseLong(val.toString()));
            } else {
                vo.setUnreadCount(dbCounts.getOrDefault(vo.getId(), 0L));
            }
        }
    }

    @Override
    @Transactional
    public Long createGroup(String groupName, String groupAvatar, List<Long> memberIds) {
        Long currentUserId = userService.getCurrentUserId();

        String plainDek;
        String encryptedDek;
        try {
            plainDek = AesUtil.generateKey();
            encryptedDek = imKeyService.encryptDek(plainDek);  // KEK 包装后入库
        } catch (Exception e) {
            throw new RuntimeException("群聊密钥生成失败", e);
        }

        ImConversation conv = new ImConversation();
        conv.setGroupName(groupName);
        conv.setGroupAvatar(groupAvatar);
        conv.setOwnerId(currentUserId);
        conv.setSecretKey(encryptedDek);
        conversationMapper.createGroupConversation(conv);
        Long convId = conv.getId();

        List<ImGroupMember> members = new ArrayList<>();
        members.add(new ImGroupMember(convId, currentUserId));
        for (Long memberId : memberIds) {
            if (!memberId.equals(currentUserId)) {
                members.add(new ImGroupMember(convId, memberId));
            }
        }
        conversationMapper.batchInsertGroupMembers(members);
        return convId;
    }

    @Override
    public void markRead(Long conversationId) {
        Long userId = userService.getCurrentUserId();
        Long latestSeq = messageMapper.selectLatestSeq(conversationId);
        if (latestSeq != null && latestSeq > 0) {
            conversationMapper.upsertReadCursor(conversationId, userId, latestSeq);
            // P0-5：清零 Redis 未读数
            stringRedisTemplate.opsForHash().put(
                    "im:unread:" + userId, String.valueOf(conversationId), "0");
            User user = userService.getById(userId);
            if (user != null) {
                String ackJson = String.format(
                        "{\"type\":\"read_ack\",\"convId\":%d,\"unread\":0}", conversationId);
                ImWebSocketHandler.sendToUser(user.getUsername(), ackJson);
            }
        }
    }

    @Override
    public List<MessageVO> getHistory(Long conversationId, Long beforeSeq, int limit) {
        // P2-3：校验当前用户是该会话成员，防止越权拉取任意会话消息
        Long userId = userService.getCurrentUserId();
        if (!isMember(conversationId, userId)) {
            throw new RuntimeException("无权限访问该会话");
        }
        String redisKey = "im:conv:" + conversationId;
        List<String> cached = stringRedisTemplate.opsForList().range(redisKey, 0, 199);

        if (cached != null && !cached.isEmpty()) {
            List<MessageVO> fromCache = new ArrayList<>();
            for (String json : cached) {
                try {
                    ImMessage msg = objectMapper.readValue(json, ImMessage.class);
                    if (beforeSeq == null || msg.getSeq() < beforeSeq) {
                        fromCache.add(toVO(msg));
                        if (fromCache.size() >= limit) break;
                    }
                } catch (Exception e) {
                    log.warn("Redis 缓存消息反序列化失败: {}", e.getMessage());
                }
            }
            if (fromCache.size() >= limit) {
                return fromCache;
            }
        }

        return messageMapper.selectHistory(conversationId, beforeSeq, limit);
    }

    /**
     * P0-1 修复：从 DB 读出的是加密 DEK，需用 KEK 解密后返回给前端。
     */
    @Override
    public String getSecretKey(Long convId) {
        Long userId = userService.getCurrentUserId();
        ImConversation conv = conversationMapper.findById(convId);
        if (conv == null) return null;

        boolean isMember;
        if (conv.getType() == 1) {
            isMember = userId.equals(conv.getParticipantA()) || userId.equals(conv.getParticipantB());
        } else {
            isMember = conversationMapper.findGroupMemberIds(convId).contains(userId);
        }
        if (!isMember) return null;

        String encryptedDek = conversationMapper.selectSecretKey(convId);
        if (encryptedDek == null) return null;
        try {
            return imKeyService.decryptDek(encryptedDek);
        } catch (Exception e) {
            log.error("DEK 解密失败 convId={}: {}", convId, e.getMessage());
            return null;
        }
    }

    /**
     * P0-1 修复：从 DB 读出加密 DEK，解密后与入参比较。
     */
    @Override
    public boolean joinGroup(Long convId, String key) {
        if (key == null) return false;
        String encryptedDek = conversationMapper.selectSecretKey(convId);
        if (encryptedDek == null) return false;
        String plainDek;
        try {
            plainDek = imKeyService.decryptDek(encryptedDek);
        } catch (Exception e) {
            log.error("joinGroup DEK 解密失败 convId={}: {}", convId, e.getMessage());
            return false;
        }
        if (!plainDek.equals(key)) return false;
        Long userId = userService.getCurrentUserId();
        conversationMapper.insertSingleGroupMember(convId, userId);
        return true;
    }

    @Override
    public long getTotalUnread() {
        Long userId = userService.getCurrentUserId();
        return conversationMapper.countTotalUnread(userId);
    }

    private boolean isMember(Long conversationId, Long userId) {
        ImConversation conv = conversationMapper.findById(conversationId);
        if (conv == null) return false;
        if (conv.getType() == 1) {
            return userId.equals(conv.getParticipantA()) || userId.equals(conv.getParticipantB());
        }
        return conversationMapper.findGroupMemberIds(conversationId).contains(userId);
    }

    private MessageVO toVO(ImMessage msg) {
        MessageVO vo = new MessageVO();
        vo.setId(msg.getId());
        vo.setConversationId(msg.getConversationId());
        vo.setSenderId(msg.getSenderId());
        vo.setType(msg.getType());
        vo.setContent(msg.getContent());
        vo.setImageUrl(msg.getImageUrl());
        vo.setSendTime(msg.getSendTime());
        vo.setRecalled(msg.isRecalled());
        vo.setSeq(msg.getSeq());
        User sender = userService.getById(msg.getSenderId());
        if (sender != null) {
            vo.setSenderName(sender.getUsername());
            vo.setSenderAvatar(sender.getAvatar());
        }
        return vo;
    }
}
