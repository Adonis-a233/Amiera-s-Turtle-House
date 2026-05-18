package com.example.community.im.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ImMessage {

    private Long id;
    private Long conversationId;
    private Long senderId;

    /** 1-文字  2-图片 */
    private Integer type;

    private String content;
    private String imageUrl;
    private LocalDateTime sendTime;

    /** false=正常  true=已撤回 */
    private boolean recalled;
    private String clientMsgId;
    private Long seq;
}
