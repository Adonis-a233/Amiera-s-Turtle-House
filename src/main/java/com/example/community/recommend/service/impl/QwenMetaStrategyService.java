package com.example.community.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.community.entity.Article;
import com.example.community.mapper.ArticleMapper;
import com.example.community.recommend.dto.RecConfig;
import com.example.community.recommend.entity.UserBehavior;
import com.example.community.recommend.mapper.UserBehaviorMapper;
import com.example.community.utils.DashScopeClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 离线异步：调用本地 Qwen 分析用户近7天行为摘要，生成推荐权重配置写入 Redis。
 * 单次推理约2-5秒，只能异步调用，绝不在请求链路上执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QwenMetaStrategyService {

    private final UserBehaviorMapper behaviorMapper;
    private final ArticleMapper articleMapper;
    private final DashScopeClient dashScopeClient;
    private final RecRedisService recRedisService;
    private final ObjectMapper objectMapper;

    @Async("recommendTaskExecutor")
    public void analyzeAndUpdate(Long userId) {
        try {
            String summary = buildSummary(userId);
            if (summary == null) return;

            String response = dashScopeClient.generate(buildPrompt(summary));
            if (response == null || response.isBlank()) return;

            RecConfig cfg = parseConfig(response);
            if (cfg != null) {
                recRedisService.saveRecConfig(userId, cfg);
                log.debug("Qwen meta-strategy updated userId={} w1={}", userId, cfg.getW1());
            }
        } catch (Exception e) {
            log.warn("QwenMetaStrategy failed userId={}: {}", userId, e.getMessage());
        }
    }

    private String buildSummary(Long userId) {
        LambdaQueryWrapper<UserBehavior> w = new LambdaQueryWrapper<>();
        w.eq(UserBehavior::getUserId, userId)
                .ge(UserBehavior::getCreatedAt, LocalDateTime.now().minusDays(7));
        List<UserBehavior> behaviors = behaviorMapper.selectList(w);
        if (behaviors.isEmpty()) return null;

        List<Long> clickedIds = behaviors.stream()
                .filter(b -> b.getEventType() == UserBehavior.CLICK)
                .map(UserBehavior::getArticleId).distinct().collect(Collectors.toList());

        Map<String, Integer> tagClickMap = new LinkedHashMap<>();
        if (!clickedIds.isEmpty()) {
            articleMapper.selectList(new LambdaQueryWrapper<Article>()
                    .in(Article::getId, clickedIds)).forEach(a -> {
                if (a.getTags() != null) {
                    a.getTags().forEach(tag -> tagClickMap.merge(tag, 1, Integer::sum));
                }
            });
        }

        long likes    = behaviors.stream().filter(b -> b.getEventType() == UserBehavior.LIKE).count();
        long collects = behaviors.stream().filter(b -> b.getEventType() == UserBehavior.COLLECT).count();
        long clicks   = behaviors.stream().filter(b -> b.getEventType() == UserBehavior.CLICK).count();

        String topTags = tagClickMap.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(5)
                .map(e -> e.getKey() + ":" + e.getValue() + "次")
                .collect(Collectors.joining(", "));

        return String.format("点击%d次，点赞%d次，收藏%d次。热门标签：%s。",
                clicks, likes, collects, topTags.isEmpty() ? "无" : topTags);
    }

    private String buildPrompt(String summary) {
        return "你是推荐系统权重调优助手。用户近7天行为摘要：" + summary +
                "\n请根据以上信息输出推荐权重JSON配置，只输出JSON，不要任何解释。" +
                "字段：w1(0.4-0.8，个性化相关性权重)，interest_halflife_h(24-168，兴趣衰减半衰期小时)，" +
                "diversity_boost(true/false，是否加强多样性)。" +
                "\n示例：{\"w1\":0.65,\"interest_halflife_h\":48,\"diversity_boost\":false}";
    }

    private RecConfig parseConfig(String response) {
        try {
            String json = response.trim();
            int start = json.indexOf('{');
            int end   = json.lastIndexOf('}');
            if (start < 0 || end < 0) return null;
            json = json.substring(start, end + 1);

            JsonNode node = objectMapper.readTree(json);
            RecConfig cfg = new RecConfig();
            if (node.has("w1")) {
                cfg.setW1(Math.max(0.4f, Math.min(0.8f, (float) node.get("w1").asDouble(0.6))));
            }
            if (node.has("interest_halflife_h")) {
                cfg.setInterestHalflifeH(Math.max(24f, Math.min(168f,
                        (float) node.get("interest_halflife_h").asDouble(48))));
            }
            if (node.has("diversity_boost")) {
                cfg.setDiversityBoost(node.get("diversity_boost").asBoolean(false));
            }
            return cfg;
        } catch (Exception e) {
            log.warn("parseConfig failed: {}", e.getMessage());
            return null;
        }
    }
}
