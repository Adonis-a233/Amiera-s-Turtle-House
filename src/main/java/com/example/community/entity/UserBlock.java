package com.example.community.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserBlock {

    private Long id;
    private Long blockerId;
    private Long blockedId;
    private LocalDateTime createTime;
}
