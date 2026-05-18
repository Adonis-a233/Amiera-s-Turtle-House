package com.example.community.recommend.service.impl;

import com.example.community.recommend.entity.UserBehavior;
import com.example.community.recommend.mapper.UserBehaviorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 行为事件异步写入：
 *   1. 落库 user_behavior
 *   2. 更新 Redis embedding 历史（仅点击/点赞/收藏）
 *   3. 每累计50次行为触发 Qwen 元策略分析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorEventService {

    private static final int QWEN_TRIGGER_THRESHOLD = 50;

    private final UserBehaviorMapper behaviorMapper;
    private final RecRedisService recRedisService;
    private final EsArticleService esArticleService;
    private final QwenMetaStrategyService qwenService;

    @Async("recommendTaskExecutor")
    public void record(Long userId, Long articleId, int eventType, Integer dwellTime) {
        try {
            UserBehavior b = new UserBehavior();
            b.setUserId(userId);
            b.setArticleId(articleId);
            b.setEventType(eventType);
            b.setDwellTime(dwellTime);
            b.setCreatedAt(LocalDateTime.now());
            behaviorMapper.insert(b);

            // 点击/点赞/收藏时更新 embedding 历史
            if (eventType == UserBehavior.CLICK
                    || eventType == UserBehavior.LIKE
                    || eventType == UserBehavior.COLLECT) {
                List<Double> vec = esArticleService.getTextVector(articleId);
                if (vec != null) {
                    float[] arr = new float[vec.size()];
                    for (int i = 0; i < vec.size(); i++) arr[i] = vec.get(i).floatValue();
                    recRedisService.pushEmbedding(userId, arr);
                }
            }

            // 触发 Qwen 元策略
            int total = behaviorMapper.countByUserId(userId);
            if (total % QWEN_TRIGGER_THRESHOLD == 0) {
                qwenService.analyzeAndUpdate(userId);
            }
        } catch (Exception e) {
            log.warn("BehaviorEventService.record failed userId={} articleId={}: {}",
                    userId, articleId, e.getMessage());
        }
    }
}
