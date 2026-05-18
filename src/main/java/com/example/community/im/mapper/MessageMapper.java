package com.example.community.im.mapper;

import com.example.community.im.entity.ImMessage;
import com.example.community.im.vo.MessageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper {

    void batchInsert(@Param("list") List<ImMessage> list);

    /**
     * 游标分页：beforeSeq 为 null 时查最新 limit 条
     */
    List<MessageVO> selectHistory(@Param("conversationId") Long conversationId,
                                  @Param("beforeSeq") Long beforeSeq,
                                  @Param("limit") int limit);

    /**
     * 撤回消息：仅允许 2 分钟内、本人发送的消息。返回 affected rows。
     */
    int recallMessage(@Param("messageId") Long messageId, @Param("senderId") Long senderId);

    /** 返回会话最新消息的 seq，用于更新 read cursor */
    Long selectLatestSeq(@Param("conversationId") Long conversationId);

    /** 返回会话 seq 最大值，用于 Redis 冷启动时初始化计数器（P2-4） */
    long selectMaxSeq(@Param("conversationId") Long conversationId);

    /** 补发未读消息：返回 seq > sinceSeq 的所有消息，上限200条 */
    List<ImMessage> selectUnreadSince(@Param("conversationId") Long conversationId,
                                      @Param("sinceSeq") Long sinceSeq);
}
