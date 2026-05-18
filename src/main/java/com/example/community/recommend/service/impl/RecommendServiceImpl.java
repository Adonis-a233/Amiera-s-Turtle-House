package com.example.community.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.community.entity.Article;
import com.example.community.mapper.ArticleMapper;
import com.example.community.recommend.dto.RecConfig;
import com.example.community.recommend.entity.UserBehavior;
import com.example.community.recommend.mapper.ArticleExposurePoolMapper;
import com.example.community.recommend.mapper.UserBehaviorMapper;
import com.example.community.recommend.service.RecommendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendServiceImpl implements RecommendService {

    private static final int RECALL_SEMANTIC = 150;
    private static final int RECALL_HOT      = 50;
    private static final int RECALL_POOL     = 30;
    private static final int SCORE_TOP       = 50;
    private static final int RESULT_SIZE     = 20;
    private static final double HEAT_DECAY_LAMBDA = 0.02;

    private final RecRedisService recRedisService;
    private final EsArticleService esArticleService;
    private final ArticleMapper articleMapper;
    private final UserBehaviorMapper behaviorMapper;
    private final ArticleExposurePoolMapper exposurePoolMapper;

    @Override
    public List<Long> recommend(Long userId) {
        boolean hasHistory = userId != null && recRedisService.hasEmbeddings(userId);
        if (!hasHistory) {
            return recRedisService.getHotTopN(RESULT_SIZE);
        }

        RecConfig cfg = recRedisService.getRecConfig(userId);
        List<float[]> embedHistory = recRedisService.getEmbeddings(userId);

        // Step 2: 多路召回
        float[] userVec = computeWeightedAvg(embedHistory);
        List<Long> semanticIds = (userVec != null)
                ? esArticleService.knnRecall(toDoubleList(userVec), RECALL_SEMANTIC, null)
                : Collections.emptyList();
        List<Long> hotIds  = recRedisService.getHotTopN(RECALL_HOT);
        List<Long> poolIds = exposurePoolMapper.selectActivePoolIds(RECALL_POOL);

        List<Long> candidates = rrfMerge(semanticIds, hotIds, poolIds);

        // Step 3: DIN 近似打分
        List<Article> articles = fetchArticles(candidates);
        List<ScoredArticle> scored = score(articles, embedHistory, cfg);
        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<Long> top = scored.stream()
                .limit(SCORE_TOP)
                .map(s -> s.article().getId())
                .collect(Collectors.toList());

        // Step 4: 打散过滤
        List<Long> likedOrCollected = behaviorMapper.selectLikedOrCollectedArticleIds(userId);
        List<Long> result = diversify(top, articles, likedOrCollected);

        // Step 5: 异步写曝光日志
        asyncLogExposure(userId, result);

        return result;
    }

    // ========== 向量工具 ==========

    private float[] computeWeightedAvg(List<float[]> history) {
        if (history.isEmpty()) return null;
        int dim = history.get(0).length;
        float[] avg = new float[dim];
        float totalW = 0f;
        for (int i = 0; i < history.size(); i++) {
            float w = (float) Math.exp(-0.1 * i);
            float[] emb = history.get(i);
            for (int d = 0; d < dim; d++) avg[d] += emb[d] * w;
            totalW += w;
        }
        if (totalW > 0) for (int d = 0; d < dim; d++) avg[d] /= totalW;
        return avg;
    }

    private List<Double> toDoubleList(float[] arr) {
        List<Double> list = new ArrayList<>(arr.length);
        for (float v : arr) list.add((double) v);
        return list;
    }

    private float[] toFloatArr(List<Double> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i).floatValue();
        return arr;
    }

    // ========== RRF 融合 ==========

    @SafeVarargs
    private List<Long> rrfMerge(List<Long>... lists) {
        Map<Long, Double> scores = new HashMap<>();
        for (List<Long> list : lists) {
            for (int rank = 0; rank < list.size(); rank++) {
                scores.merge(list.get(rank), 1.0 / (60.0 + rank + 1), Double::sum);
            }
        }
        return scores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // ========== DIN 打分 ==========

    private List<ScoredArticle> score(List<Article> articles,
                                      List<float[]> embedHistory,
                                      RecConfig cfg) {
        List<ScoredArticle> result = new ArrayList<>(articles.size());
        for (Article a : articles) {
            List<Double> artVecD = esArticleService.getTextVector(a.getId());
            double relevance = 0.0;
            if (artVecD != null && !artVecD.isEmpty()) {
                float[] artVec = toFloatArr(artVecD);
                float[] userInterest = dinAttention(artVec, embedHistory);
                if (userInterest != null) relevance = cosine(userInterest, artVec);
            }

            long hoursSince = a.getCreateTime() != null
                    ? ChronoUnit.HOURS.between(a.getCreateTime(), LocalDateTime.now()) : 0;
            int likes    = a.getLikeCount()    == null ? 0 : a.getLikeCount();
            int collects = a.getCollectCount() == null ? 0 : a.getCollectCount();
            int views    = a.getViewCount()    == null ? 0 : a.getViewCount();
            double heat = Math.log1p(likes + collects * 1.5 + views * 0.1)
                    * Math.exp(-HEAT_DECAY_LAMBDA * hoursSince);

            result.add(new ScoredArticle(a, cfg.getW1() * relevance + cfg.getW2() * heat));
        }
        return result;
    }

    private float[] dinAttention(float[] candidateVec, List<float[]> history) {
        if (history.isEmpty()) return null;
        int dim = candidateVec.length;
        float[] weights = new float[history.size()];
        float sumW = 0f;
        for (int i = 0; i < history.size(); i++) {
            float sim = (float) cosine(candidateVec, history.get(i));
            weights[i] = Math.max(0f, sim) * (float) Math.exp(-0.1 * i);
            sumW += weights[i];
        }
        if (sumW == 0f) return computeWeightedAvg(history);
        float[] interest = new float[dim];
        for (int i = 0; i < history.size(); i++) {
            float w = weights[i] / sumW;
            float[] emb = history.get(i);
            for (int d = 0; d < dim; d++) interest[d] += emb[d] * w;
        }
        return interest;
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            na  += (double) a[i] * a[i];
            nb  += (double) b[i] * b[i];
        }
        return (na == 0 || nb == 0) ? 0.0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    // ========== 打散过滤 ==========

    private List<Long> diversify(List<Long> top,
                                 List<Article> articles,
                                 List<Long> likedOrCollected) {
        Map<Long, Article> articleMap = articles.stream()
                .collect(Collectors.toMap(Article::getId, a -> a));
        Map<Long, Integer> authorCount = new HashMap<>();
        Map<String, Integer> tagCount  = new HashMap<>();
        List<Long> main     = new ArrayList<>();
        List<Long> deferred = new ArrayList<>();

        for (Long id : top) {
            Article a = articleMap.get(id);
            if (a == null) continue;
            if (likedOrCollected.contains(id)) { deferred.add(id); continue; }
            if (authorCount.getOrDefault(a.getUserId(), 0) >= 2) continue;

            boolean tagBlocked = false;
            if (main.size() < 10 && a.getTags() != null) {
                for (String tag : a.getTags()) {
                    if (tagCount.getOrDefault(tag, 0) >= 3) { tagBlocked = true; break; }
                }
            }
            if (tagBlocked) continue;

            main.add(id);
            authorCount.merge(a.getUserId(), 1, Integer::sum);
            if (a.getTags() != null) {
                for (String tag : a.getTags()) tagCount.merge(tag, 1, Integer::sum);
            }
            if (main.size() >= RESULT_SIZE) break;
        }

        main.addAll(deferred);
        return main.subList(0, Math.min(RESULT_SIZE, main.size()));
    }

    // ========== 批量查文章 ==========

    private List<Article> fetchArticles(List<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyList();
        LambdaQueryWrapper<Article> w = new LambdaQueryWrapper<>();
        w.in(Article::getId, ids).eq(Article::getStatus, 1);
        return articleMapper.selectList(w);
    }

    // ========== 异步写曝光 ==========

    @Async("recommendTaskExecutor")
    public void asyncLogExposure(Long userId, List<Long> articleIds) {
        for (Long aid : articleIds) {
            UserBehavior b = new UserBehavior();
            b.setUserId(userId);
            b.setArticleId(aid);
            b.setEventType(UserBehavior.EXPOSE);
            b.setCreatedAt(LocalDateTime.now());
            behaviorMapper.insert(b);
        }
    }

    private record ScoredArticle(Article article, double score) {}
}
