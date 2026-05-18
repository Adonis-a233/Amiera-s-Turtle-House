package com.example.community.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论VO
 */
@Data
public class CommentVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String content;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String username;

    private String userAvatar;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    /**
     * 子评论列表（树形结构核心）
     */
    private List<CommentVO> children;
}
