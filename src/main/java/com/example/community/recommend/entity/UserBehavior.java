package com.example.community.recommend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_behavior")
public class UserBehavior {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long articleId;

    /** 1曝光 2点击 3点赞 4评论 5收藏 6跳过 */
    private Integer eventType;

    /** 停留秒数，eventType=2时有效 */
    private Integer dwellTime;

    private LocalDateTime createdAt;

    public static final int EXPOSE  = 1;
    public static final int CLICK   = 2;
    public static final int LIKE    = 3;
    public static final int COMMENT = 4;
    public static final int COLLECT = 5;
    public static final int SKIP    = 6;
}
