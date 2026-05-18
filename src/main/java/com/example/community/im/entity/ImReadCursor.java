package com.example.community.im.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ImReadCursor {

    private Long id;
    private Long conversationId;
    private Long userId;
    private Long lastReadMsgId;
    private LocalDateTime updateTime;
}
