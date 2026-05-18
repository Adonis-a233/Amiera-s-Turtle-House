package com.example.community.im.mapper;

import com.example.community.im.entity.ImConversation;
import com.example.community.im.entity.ImGroupMember;
import com.example.community.im.vo.ConversationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ConversationMapper {

    Long findSingleConversation(@Param("userA") Long userA, @Param("userB") Long userB);

    void createSingleConversation(@Param("userA") Long userA,
                                  @Param("userB") Long userB,
                                  @Param("secretKey") String secretKey);

    void createGroupConversation(ImConversation conversation);

    void batchInsertGroupMembers(@Param("members") List<ImGroupMember> members);

    void insertSingleGroupMember(@Param("convId") Long convId, @Param("userId") Long userId);

    void updateLastMsg(@Param("conversationId") Long conversationId,
                       @Param("lastMsg") String lastMsg,
                       @Param("lastMsgTime") LocalDateTime lastMsgTime,
                       @Param("lastMsgType") Integer lastMsgType);

    List<ConversationVO> selectMyConversations(@Param("userId") Long userId,
                                               @Param("offset") int offset,
                                               @Param("pageSize") int pageSize);

    void upsertReadCursor(@Param("conversationId") Long conversationId,
                          @Param("userId") Long userId,
                          @Param("lastReadSeq") Long lastReadSeq);

    ImConversation findById(@Param("convId") Long convId);

    List<Long> findGroupMemberIds(@Param("convId") Long convId);

    String selectSecretKey(@Param("convId") Long convId);

    long countUnread(@Param("conversationId") Long conversationId,
                     @Param("userId") Long userId);

    long countTotalUnread(@Param("userId") Long userId);

    List<ImConversation> findMyConversations(@Param("userId") Long userId);

    Long getLastReadSeq(@Param("convId") Long convId, @Param("userId") Long userId);

    /** 用于分页 total（P0-3） */
    long countMyConversations(@Param("userId") Long userId);

    /**
     * 批量计算未读数（冷启动时从 DB 初始化 Redis unread hash，P0-5）。
     * 返回 List<Map>，每个 Map 含 convId 和 cnt 两个 key。
     */
    List<Map<String, Object>> batchCountUnread(@Param("userId") Long userId,
                                               @Param("convIds") List<Long> convIds);
}
