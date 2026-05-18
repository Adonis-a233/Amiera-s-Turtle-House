package com.example.community.recommend.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.community.entity.Article;
import com.example.community.mapper.ArticleMapper;
import com.example.community.recommend.mapper.ArticleExposurePoolMapper;
import com.example.community.recommend.service.impl.RecRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 推荐系统定时任务：
 *   - 每小时重算热度 ZSet（article:hot:rank）
 *   - 每小时清理曝光池过期记录
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendScheduler {

    private final ArticleMapper articleMapper;
    private final ArticleExposurePoolMapper exposurePoolMapper;
    private final RecRedisService recRedisService;

    /** 每小时重算近72h发布文章的热度分，写入 article:hot:rank ZSet。 */
    @Scheduled(fixedRate = 3600_000)
    public void refreshHotRank() {
        try {
            LambdaQueryWrapper<Article> w = new LambdaQueryWrapper<>();
            w.eq(Article::getStatus, 1)
                    .ge(Article::getCreateTime, LocalDateTime.now().minusHours(72));
            List<Article> articles = articleMapper.selectList(w);

            for (Article a : articles) {
                long hoursSince = a.getCreateTime() != null
                        ? ChronoUnit.HOURS.between(a.getCreateTime(), LocalDateTime.now()) : 0;
                int likes    = a.getLikeCount()    == null ? 0 : a.getLikeCount();
                int collects = a.getCollectCount() == null ? 0 : a.getCollectCount();
                int views    = a.getViewCount()    == null ? 0 : a.getViewCount();
                double heat  = Math.log1p(likes + collects * 1.5 + views * 0.1)
                        * Math.exp(-0.02 * hoursSince);
                recRedisService.setHotScore(a.getId(), heat);
            }
            log.debug("Hot rank refreshed: {} articles", articles.size());
        } catch (Exception e) {
            log.warn("refreshHotRank failed: {}", e.getMessage());
        }
    }

    /** 每小时清理曝光池过期记录。 */
    @Scheduled(fixedRate = 3600_000, initialDelay = 60_000)
    public void cleanExposurePool() {
        try {
            exposurePoolMapper.deleteExpired();
            log.debug("Exposure pool cleaned.");
        } catch (Exception e) {
            log.warn("cleanExposurePool failed: {}", e.getMessage());
        }
    }
}
