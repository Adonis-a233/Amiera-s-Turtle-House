package com.example.community.im.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ImConversation {

    private Long id;

    /** 1-单聊  2-群聊 */
    private Integer type;

    /** 单聊成员 A（存 LEAST(userA, userB)） */
    private Long participantA;

    /** 单聊成员 B（存 GREATEST(userA, userB)） */
    private Long participantB;

    /** 群聊名称 */
    private String groupName;

    /** 群聊头像 */
    private String groupAvatar;

    /** 群主 userId */
    private Long ownerId;

    private String secretKey;

    private String lastMsg;
    private LocalDateTime lastMsgTime;

    /** 1-文字  2-图片 */
    private Integer lastMsgType;

    private LocalDateTime createTime;
}
