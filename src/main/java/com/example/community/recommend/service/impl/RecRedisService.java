package com.example.community.recommend.service.impl;

import com.example.community.recommend.dto.RecConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 推荐系统专用 Redis 操作。
 *
 * Key 设计：
 *   user:behavior:embeddings:{userId}  → List  最近20条交互帖子 embedding（JSON float[]）
 *   user:rec:config:{userId}           → Hash  Qwen 生成的动态权重配置
 *   article:hot:rank                   → ZSet  热度分
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecRedisService {

    private static final String EMBED_KEY_PREFIX  = "user:behavior:embeddings:";
    private static final String CONFIG_KEY_PREFIX = "user:rec:config:";
    private static final String HOT_RANK_KEY      = "article:hot:rank";
    private static final int    MAX_EMBED_HISTORY  = 20;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    // ========== 用户 embedding 历史 ==========

    public void pushEmbedding(Long userId, float[] embedding) {
        String key = EMBED_KEY_PREFIX + userId;
        try {
            String json = objectMapper.writeValueAsString(embedding);
            redis.opsForList().leftPush(key, json);
            redis.opsForList().trim(key, 0, MAX_EMBED_HISTORY - 1);
            redis.expire(key, 30, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("pushEmbedding failed userId={}: {}", userId, e.getMessage());
        }
    }

    public List<float[]> getEmbeddings(Long userId) {
        String key = EMBED_KEY_PREFIX + userId;
        try {
            List<String> raw = redis.opsForList().range(key, 0, MAX_EMBED_HISTORY - 1);
            if (raw == null || raw.isEmpty()) return Collections.emptyList();
            List<float[]> result = new ArrayList<>(raw.size());
            for (String s : raw) result.add(objectMapper.readValue(s, float[].class));
            return result;
        } catch (Exception e) {
            log.warn("getEmbeddings failed userId={}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public boolean hasEmbeddings(Long userId) {
        Long size = redis.opsForList().size(EMBED_KEY_PREFIX + userId);
        return size != null && size > 0;
    }

    // ========== 用户推荐权重配置 ==========

    public void saveRecConfig(Long userId, RecConfig config) {
        String key = CONFIG_KEY_PREFIX + userId;
        try {
            Map<String, String> map = new HashMap<>();
            map.put("w1", String.valueOf(config.getW1()));
            map.put("interest_halflife_h", String.valueOf(config.getInterestHalflifeH()));
            map.put("diversity_boost", String.valueOf(config.isDiversityBoost()));
            redis.opsForHash().putAll(key, map);
            redis.expire(key, 7, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("saveRecConfig failed userId={}: {}", userId, e.getMessage());
        }
    }

    public RecConfig getRecConfig(Long userId) {
        String key = CONFIG_KEY_PREFIX + userId;
        try {
            Map<Object, Object> map = redis.opsForHash().entries(key);
            if (map.isEmpty()) return RecConfig.defaultConfig();
            RecConfig cfg = new RecConfig();
            cfg.setW1(parseFloat(map.get("w1"), 0.6f));
            cfg.setInterestHalflifeH(parseFloat(map.get("interest_halflife_h"), 48f));
            cfg.setDiversityBoost("true".equals(String.valueOf(map.get("diversity_boost"))));
            return cfg;
        } catch (Exception e) {
            return RecConfig.defaultConfig();
        }
    }

    // ========== 热度排行 ZSet ==========

    public void setHotScore(Long articleId, double score) {
        redis.opsForZSet().add(HOT_RANK_KEY, String.valueOf(articleId), score);
    }

    public List<Long> getHotTopN(int n) {
        Set<String> members = redis.opsForZSet().reverseRange(HOT_RANK_KEY, 0, n - 1);
        if (members == null || members.isEmpty()) return Collections.emptyList();
        List<Long> ids = new ArrayList<>(members.size());
        for (String m : members) {
            try { ids.add(Long.valueOf(m)); } catch (NumberFormatException ignored) {}
        }
        return ids;
    }

    // ========== 工具 ==========

    private float parseFloat(Object val, float defaultVal) {
        if (val == null) return defaultVal;
        try { return Float.parseFloat(val.toString()); }
        catch (Exception e) { return defaultVal; }
    }
}
