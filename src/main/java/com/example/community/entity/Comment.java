package com.example.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评论实体类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("comment")
public class Comment extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    private Long userId;

    /**
     * 父评论ID，0表示顶级评论
     */
    private Long parentId;

    /**
     * 评论嵌套深度（0-indexed：根评论=0，最大值为 MAX_DEPTH=4，即5层）。
     * 插入时由 addComment 计算并写入，避免查询时逐层向上追溯。
     */
    private Integer depth;

    private String content;
}