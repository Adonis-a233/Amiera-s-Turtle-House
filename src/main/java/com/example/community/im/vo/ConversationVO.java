package com.example.community.im.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 1-单聊  2-群聊 */
    private Integer type;

    /** 单聊时为对方昵称，群聊时为群名 */
    private String name;

    /** 单聊时为对方头像，群聊时为群头像 */
    private String avatar;

    private String lastMsg;
    private LocalDateTime lastMsgTime;

    /** 1-文字  2-图片 */
    private Integer lastMsgType;

    private long unreadCount;
}
