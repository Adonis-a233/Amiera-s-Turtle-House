package com.example.community.im.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ImMessageVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long receiverId;

    /** 1-文字  2-图片 */
    private Integer type;

    private String content;

    private String imageUrl;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long senderId;

    private String senderName;

    private String senderAvatar;

    /** 格式：yyyy-MM-dd HH:mm:ss */
    private String sendTime;

    private boolean recalled;
    private Long seq;
}
