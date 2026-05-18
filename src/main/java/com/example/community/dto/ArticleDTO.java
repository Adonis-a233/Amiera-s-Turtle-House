package com.example.community.dto;

import lombok.Data;

import java.util.List;

/**
 * 帖子发布/编辑 DTO
 */
@Data
public class ArticleDTO {

    /**
     * 编辑时传入帖子 ID；发布时为 null
     */
    private Long id;

    private String title;

    private String content;

    /**
     * 摘要（编辑时可手动传入；发布时由 Service 自动截取）
     */
    private String summary;

    /**
     * true 为草稿，false 或 null 为已发布
     */
    private Boolean isDraft;

    // ==================== 新增字段 ====================

    /**
     * 图片URL列表，由前端上传图片后获得 CDN URL，再随帖子一起提交。
     * 最大数量限制建议在 Service 层校验（如 <= 9 张）。
     */
    private List<String> imageUrls;

    /**
     * 标签列表，如 ["美食", "川菜"]。
     * 建议 Service 层限制数量（如 <= 5 个）和单个标签长度（如 <= 20 字）。
     */
    private List<String> tags;

    /**
     * 所属圈子/话题 ID，可为 null（不属于任何圈子）。
     */
    private Long topicId;
}
