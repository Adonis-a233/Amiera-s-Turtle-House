package com.example.community.recommend.service;

import java.util.List;

public interface RecommendService {

    /**
     * 为用户生成推荐帖子 ID 列表（已按推荐分排序，最多20条）。
     * 新用户或无历史时降级为热度列表。
     */
    List<Long> recommend(Long userId);
}
