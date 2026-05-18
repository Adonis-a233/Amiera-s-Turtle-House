package com.example.community.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Data
@TableName("sys_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String avatar;

    private String email;

    /**
     * 个人简介
     */
    private String bio;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // ==================== 新增字段 ====================

    /**
     * 用户等级，默认 1。
     * 可根据发帖数、获赞数、活跃天数等规则计算，由定时任务或事件触发更新。
     */
    private Integer level;

    /**
     * 虚拟货币（金币）余额，默认 0。
     * 用于平台激励体系：发帖/签到获得，兑换特权或道具消耗。
     */
    private Integer coins;

    /**
     * 粉丝数（关注我的人数）。
     * 冗余计数字段，由关注/取关事件触发更新，避免实时 COUNT 查询。
     */
    private Integer followerCount;

    /**
     * 关注数（我关注的人数）。
     * 同上，冗余维护。
     */
    private Integer followingCount;

    /**
     * 发帖总数（已发布状态）。
     * 冗余计数字段，发布/删除帖子时同步更新。
     * 注意：草稿不计入此字段。
     */
    private Integer postCount;

    /**
     * 用户角色，默认 "USER"，管理员为 "ADMIN"。
     * 用于 Spring Security ROLE_ 鉴权。
     */
    private String role;
}
