package com.example.community.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户个人资料 VO
 */
@Data
public class UserProfileVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String username;

    private String avatar;

    private String email;

    /**
     * 个人简介
     */
    private String bio;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 已发布帖子数量。
     * 字段名从 articleCount 改为 postCount，与 User 实体对齐。
     *
     * ⚠️ 注意：UserServiceImpl 中若有手动 set 此字段的代码，
     *    需同步将 setArticleCount(...) 改为 setPostCount(...)，否则编译报错。
     */
    private Integer postCount;

    /**
     * 获赞总数（所有帖子的 likeCount 之和）。
     * 当前实现：UserServiceImpl 实时聚合查询，数据量大后考虑改为冗余字段。
     */
    private Integer totalLikes;

    // ==================== 新增字段 ====================

    /**
     * 用户等级
     */
    private Integer level;

    /**
     * 金币余额
     */
    private Integer coins;

    /**
     * 粉丝数
     */
    private Integer followerCount;

    /**
     * 关注数
     */
    private Integer followingCount;
}
