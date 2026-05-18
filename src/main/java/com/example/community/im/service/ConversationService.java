package com.example.community.im.service;

import com.example.community.im.vo.ConversationVO;
import com.example.community.im.vo.MessageVO;
import com.example.community.utils.PageResult;

import java.util.List;

public interface ConversationService {

    PageResult<ConversationVO> getMyConversations(int page, int size);

    Long createGroup(String groupName, String groupAvatar, List<Long> memberIds);

    void markRead(Long conversationId);

    List<MessageVO> getHistory(Long conversationId, Long beforeSeq, int limit);

    /** 校验成员身份后返回会话密钥，非成员返回 null */
    String getSecretKey(Long convId);

    /** 用密钥加入群聊，匹配返回 true，不匹配返回 false */
    boolean joinGroup(Long convId, String key);

    /** 当前用户所有会话的总未读数 */
    long getTotalUnread();
}
