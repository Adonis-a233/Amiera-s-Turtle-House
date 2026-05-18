package com.example.community.recommend.dto;

import lombok.Data;

/**
 * 推荐系统动态权重配置，由 Qwen 离线分析生成，存入 Redis user:rec:config:{userId}。
 */
@Data
public class RecConfig {

    /** 个性化相关性权重（0.4~0.8） */
    private float w1 = 0.6f;

    /** 兴趣衰减半衰期（小时） */
    private float interestHalflifeH = 48f;

    /** 是否处于探索期（加强多样性） */
    private boolean diversityBoost = false;

    public static RecConfig defaultConfig() {
        return new RecConfig();
    }

    /** 热度权重 = 1 - w1 - 0.1（地理权重固定 0.1） */
    public float getW2() {
        return Math.max(0f, 1f - w1 - 0.1f);
    }
}
