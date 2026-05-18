package com.example.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子实体类（原 Article，重命名为 Post 语义，表名暂保持 article 以兼容存量数据）
 *
 * ⚠️ autoResultMap = true 是必须的：
 *    MybatisPlus 的 TypeHandler（如 JacksonTypeHandler）只在自动构建的 ResultMap 中生效。
 *    若不加此注解，image_urls / tags 列查出来是 null，不会报错，排查极难。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "article", autoResultMap = true)
public class Article extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String content;

    private String summary;

    private Integer viewCount;

    private Integer likeCount;

    /**
     * 状态：0-草稿，1-已发布
     */
    private Integer status;

    // ==================== 新增字段 ====================

    /**
     * 图片URL列表，存储为 JSON 数组。
     * 例：["https://cdn.example.com/img/a.jpg", "https://cdn.example.com/img/b.jpg"]
     *
     * typeHandler = JacksonTypeHandler.class：
     *   MybatisPlus 在写入时将 List<String> 序列化为 JSON 字符串存入 MySQL JSON 列；
     *   读取时反序列化回 List<String>。需要 autoResultMap = true 才能触发。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> imageUrls;

    /**
     * 标签列表，存储为 JSON 数组。
     * 例：["美食", "川菜", "探店"]
     * 与 imageUrls 同理，使用 JacksonTypeHandler。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    /**
     * 收藏数。
     * 与 likeCount 分开存储：点赞和收藏是两个独立行为。
     */
    private Integer collectCount;

    /**
     * 转发/分享数。
     * 目前由后端接收前端上报，或在分享接口中自增。
     */
    private Integer shareCount;

    /**
     * 所属圈子/话题 ID，外键关联 topic 表（该表待创建）。
     * 暂时可为 null，圈子功能上线后再强制非空。
     */
    private Long topicId;

    // ==================== 审核字段 ====================

    /**
     * 审核状态：0-待审核 1-AI通过 2-拒绝 3-待人工审核
     */
    private Integer reviewStatus;

    /** 审核拒绝原因 */
    private String reviewReason;

    /** 人工审核操作员 userId */
    private Long reviewedBy;

    /** 人工审核时间 */
    private LocalDateTime reviewedAt;
}
