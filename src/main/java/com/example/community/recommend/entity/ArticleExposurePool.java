package com.example.community.recommend.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article_exposure_pool")
public class ArticleExposurePool {

    @TableId
    private Long articleId;

    private LocalDateTime enteredAt;

    private Float earlyCtr;

    private LocalDateTime expiresAt;
}
