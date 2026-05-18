package com.example.community.im.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ImGroupMember {

    private Long id;
    private Long conversationId;
    private Long userId;
    private LocalDateTime joinTime;

    public ImGroupMember() {}

    public ImGroupMember(Long conversationId, Long userId) {
        this.conversationId = conversationId;
        this.userId = userId;
    }
}
