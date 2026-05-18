package com.example.community.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子 VO（前端展示用）
 */
@Data
public class ArticleVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String title;

    /**
     * 列表页可不传，详情页传
     */
    private String content;

    private String summary;

    private Integer viewCount;

    private Integer likeCount;

    /**
     * 帖子状态：0-草稿，1-已发布
     */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // 作者信息
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String authorName;

    private String authorAvatar;

    /**
     * 当前用户是否已点赞
     */
    private Boolean isLiked;

    // ==================== 新增字段 ====================

    /**
     * 图片URL列表。
     * BeanUtils.copyProperties 会自动从 Article 复制此字段（字段名相同）。
     */
    private List<String> imageUrls;

    /**
     * 标签列表。
     */
    private List<String> tags;

    /**
     * 收藏数
     */
    private Integer collectCount;

    /**
     * 转发/分享数
     */
    private Integer shareCount;

    /**
     * 所属圈子/话题 ID，null 表示不属于任何圈子
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long topicId;

    /**
     * 当前用户是否已收藏。
     * 需要在 Service 层单独查询收藏状态后设置（类似 isLiked 的处理方式）。
     * 目前默认为 false，收藏功能实现后补充逻辑。
     */
    private Boolean isCollected;
}
