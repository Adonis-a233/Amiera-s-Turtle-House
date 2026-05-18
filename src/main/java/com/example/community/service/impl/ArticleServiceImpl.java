package com.example.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.community.dto.ArticleDTO;
import com.example.community.entity.Article;
import com.example.community.entity.User;
import com.example.community.mapper.ArticleMapper;
import com.example.community.recommend.entity.ArticleEsDoc;
import com.example.community.entity.UserCollect;
import com.example.community.mapper.UserCollectMapper;
import com.example.community.recommend.entity.ArticleExposurePool;
import com.example.community.recommend.entity.UserBehavior;
import com.example.community.recommend.mapper.ArticleExposurePoolMapper;
import com.example.community.recommend.service.RecommendService;
import com.example.community.recommend.service.impl.BehaviorEventService;
import com.example.community.recommend.service.impl.EsArticleService;
import com.example.community.search.event.PostPublishedEvent;
import com.example.community.service.ArticleReviewService;
import com.example.community.service.ArticleService;
import com.example.community.service.UserService;
import com.example.community.utils.DashScopeClient;
import com.example.community.utils.RedisUtil;
import com.example.community.vo.ArticleVO;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 帖子 Service 实现类
 */
@Service
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article>
        implements ArticleService {

    @Resource
    private UserService userService;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RecommendService recommendService;

    @Resource
    private BehaviorEventService behaviorEventService;

    @Resource
    private EsArticleService esArticleService;

    @Resource
    private DashScopeClient dashScopeClient;

    @Resource
    private ArticleExposurePoolMapper exposurePoolMapper;

    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    @Resource
    private ArticleReviewService articleReviewService;

    @Resource
    private UserCollectMapper userCollectMapper;

    /**
     * 辅助方法：从 SecurityContext 获取当前用户 ID
     */
    private Long getCurrentUserId() {
        return userService.getCurrentUserId();
    }

    /**
     * 安全获取当前用户 ID（未登录时返回 null，不抛异常）
     */
    private Long getCurrentUserIdSafe() {
        try {
            return getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 发布帖子
     */
    @Override
    public void publish(ArticleDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new RuntimeException("帖子标题不能为空");
        }

        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            throw new RuntimeException("帖子内容不能为空");
        }

        Article article = new Article();
        // 复制同名字段：title、content、imageUrls、tags、topicId
        BeanUtils.copyProperties(dto, article);

        article.setUserId(getCurrentUserId());
        article.setStatus(Boolean.TRUE.equals(dto.getIsDraft()) ? 0 : 1);
        article.setViewCount(0);
        article.setLikeCount(0);

        article.setCollectCount(0);
        article.setShareCount(0);
        article.setReviewStatus(0);

        // 生成摘要
        String content = dto.getContent();
        String summary = content.length() > 100 ?
                content.substring(0, 100) + "..." : content;
        article.setSummary(summary);

        this.save(article);

        // 已发布的帖子：异步生成 embedding + 入曝光池 + AI 内容审核
        if (article.getStatus() == 1) {
            final Long savedId = article.getId();
            final String textForEmbed = buildEmbedText(article);
            final String firstImage = (article.getImageUrls() != null && !article.getImageUrls().isEmpty())
                    ? article.getImageUrls().get(0) : null;
            Thread.ofVirtual().start(() -> indexArticleAsync(savedId, textForEmbed, firstImage));

            // 触发搜索模块索引（PostIndexService 监听此事件，异步写入 posts 索引）
            applicationEventPublisher.publishEvent(new PostPublishedEvent(this, article));

            // 异步 AI 审核，不阻塞发布响应
            articleReviewService.reviewByAi(savedId);
        }
    }

    private String buildEmbedText(Article a) {
        StringBuilder sb = new StringBuilder();
        if (a.getTitle() != null) sb.append(a.getTitle()).append(" ");
        if (a.getContent() != null) {
            String c = a.getContent();
            sb.append(c, 0, Math.min(c.length(), 500));
        }
        if (a.getTags() != null) sb.append(" ").append(String.join(" ", a.getTags()));
        return sb.toString().trim();
    }

    private void indexArticleAsync(Long articleId, String text, String imageUrl) {
        try {
            float[] textVec = dashScopeClient.embedText(text);
            if (textVec == null) return;

            ArticleEsDoc doc = new ArticleEsDoc();
            doc.setId(String.valueOf(articleId));
            doc.setArticleId(articleId);
            doc.setTextVector(DashScopeClient.toDoubleList(textVec));
            esArticleService.upsert(doc);

            // 入曝光池，存活48h
            ArticleExposurePool pool = new ArticleExposurePool();
            pool.setArticleId(articleId);
            pool.setEnteredAt(LocalDateTime.now());
            pool.setEarlyCtr(0f);
            pool.setExpiresAt(LocalDateTime.now().plusHours(48));
            exposurePoolMapper.insert(pool);
        } catch (Exception e) {
            // 非阻塞，失败仅记录
        }
    }

    /**
     * 获取帖子列表
     */
    @Override
    public Page<ArticleVO> getList(int page, int size, String sortType) {
        if ("recommend".equals(sortType)) {
            return getRecommendList(page, size);
        }

        Page<Article> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(Article::getStatus, 1);

        if ("hot".equals(sortType)) {
            wrapper.orderByDesc(Article::getViewCount)
                    .orderByDesc(Article::getLikeCount);
        } else {
            wrapper.orderByDesc(Article::getCreateTime);
        }

        Page<Article> articlePage = this.page(pageParam, wrapper);
        return convertPageToVo(articlePage);
    }

    /**
     * 获取帖子详情
     */
    @Override
    public ArticleVO getDetail(Long id) {
        Article article = this.getById(id);

        if (article == null) {
            throw new RuntimeException("帖子不存在");
        }

        // 增加阅读数
        this.baseMapper.incrementViewCount(id);
        article.setViewCount(article.getViewCount() + 1);

        // 异步写点击行为（dwell_time 由前端通过 recordBehavior 上报）
        Long currentUserId = getCurrentUserIdSafe();
        if (currentUserId != null) {
            behaviorEventService.record(currentUserId, id, UserBehavior.CLICK, null);
        }

        return convertToVo(article);
    }

    /**
     * 点赞/取消点赞帖子
     */
    @Override
    public void likeArticle(Long id) {
        Article article = this.getById(id);
        if (article == null) {
            throw new RuntimeException("帖子不存在");
        }

        Long userId = getCurrentUserId();

        // 分布式锁
        String lockKey = "article:like:lock:" + id + ":" + userId;
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(5));

        if (Boolean.FALSE.equals(locked)) {
            throw new RuntimeException("操作过于频繁，请稍后再试");
        }

        try {
            Boolean isMember = redisUtil.isLiked(id, userId);

            if (Boolean.TRUE.equals(isMember)) {
                redisUtil.unlike(id, userId);
                this.baseMapper.updateLikeCount(id, -1);
            } else {
                redisUtil.like(id, userId);
                this.baseMapper.updateLikeCount(id, 1);
                // 新增点赞行为（仅点赞时记录，取消点赞不写行为）
                behaviorEventService.record(userId, id, UserBehavior.LIKE, null);
            }
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    /**
     * 获取指定用户发布的帖子列表
     */
    @Override
    public Page<ArticleVO> getUserArticles(Long userId, int page, int size) {
        Page<Article> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(Article::getUserId, userId)
                .eq(Article::getStatus, 1)
                .orderByDesc(Article::getCreateTime);

        Page<Article> articlePage = this.page(pageParam, wrapper);
        return convertPageToVo(articlePage);
    }

    /**
     * 获取当前用户点赞过的帖子列表
     *
     * [修复] 使用 SCAN 替代 KEYS，避免阻塞 Redis 主线程。
     * 详见原注释。
     */
    @Override
    public Page<ArticleVO> getLikedArticles(int page, int size) {
        Long userId = getCurrentUserId();
        List<Long> likedArticleIds = new ArrayList<>();

        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match("article:like:*")
                .count(100)
                .build();

        try (Cursor<String> cursor = stringRedisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                String key = cursor.next();

                if (key.contains("lock")) {
                    continue;
                }

                Boolean isMember = stringRedisTemplate.opsForSet()
                        .isMember(key, userId.toString());

                if (Boolean.TRUE.equals(isMember)) {
                    String articleIdStr = key.replace("article:like:", "");
                    try {
                        likedArticleIds.add(Long.valueOf(articleIdStr));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        if (likedArticleIds.isEmpty()) {
            Page<ArticleVO> emptyPage = new Page<>(page, size);
            emptyPage.setRecords(new ArrayList<>());
            emptyPage.setTotal(0);
            return emptyPage;
        }

        Page<Article> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Article::getId, likedArticleIds)
                .eq(Article::getStatus, 1)
                .orderByDesc(Article::getCreateTime);

        Page<Article> articlePage = this.page(pageParam, wrapper);
        return convertPageToVo(articlePage);
    }

    /**
     * 获取当前用户的草稿列表
     */
    @Override
    public Page<ArticleVO> getDrafts(int page, int size) {
        Long userId = getCurrentUserId();

        Page<Article> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(Article::getUserId, userId)
                .eq(Article::getStatus, 0)
                .orderByDesc(Article::getCreateTime);

        Page<Article> articlePage = this.page(pageParam, wrapper);
        return convertPageToVo(articlePage);
    }

    /**
     * 模糊搜索帖子（当前为 MySQL LIKE，后续替换为语义搜索）
     */
    @Override
    public Page<ArticleVO> search(String keyword, int page, int size) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new RuntimeException("搜索关键字不能为空");
        }

        Page<Article> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(Article::getStatus, 1)
                .and(w -> w
                        .like(Article::getTitle, keyword)
                        .or()
                        .like(Article::getContent, keyword)
                )
                .orderByDesc(Article::getCreateTime);

        Page<Article> articlePage = this.page(pageParam, wrapper);
        return convertPageToVo(articlePage);
    }

    /**
     * 个性化推荐列表。
     */
    @Override
    public Page<ArticleVO> getRecommendList(int page, int size) {
        Long userId = getCurrentUserIdSafe();
        List<Long> recommendIds = recommendService.recommend(userId);

        if (recommendIds.isEmpty()) {
            return getList(page, size, "hot");
        }

        // 分页截取
        int start = (page - 1) * size;
        if (start >= recommendIds.size()) {
            Page<ArticleVO> empty = new Page<>(page, size);
            empty.setRecords(new ArrayList<>());
            empty.setTotal(recommendIds.size());
            return empty;
        }
        int end = Math.min(start + size, recommendIds.size());
        List<Long> pageIds = recommendIds.subList(start, end);

        // 按 ID 批量查文章，保持推荐顺序
        LambdaQueryWrapper<Article> w = new LambdaQueryWrapper<>();
        w.in(Article::getId, pageIds).eq(Article::getStatus, 1);
        List<Article> articles = this.list(w);

        Map<Long, Article> articleMap = articles.stream()
                .collect(Collectors.toMap(Article::getId, a -> a));

        List<Long> userIds = articles.stream().map(Article::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userIds.isEmpty() ? new HashMap<>() :
                userService.listByIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        // 按推荐顺序排列
        List<ArticleVO> voList = pageIds.stream()
                .map(articleMap::get)
                .filter(Objects::nonNull)
                .map(a -> convertToVo(a, userMap))
                .collect(Collectors.toList());

        Page<ArticleVO> voPage = new Page<>(page, size);
        voPage.setRecords(voList);
        voPage.setTotal(recommendIds.size());
        return voPage;
    }

    /**
     * 前端上报行为（停留时间等）。
     */
    @Override
    public void recordBehavior(Long articleId, int eventType, Integer dwellTime) {
        Long userId = getCurrentUserIdSafe();
        if (userId == null) return;
        behaviorEventService.record(userId, articleId, eventType, dwellTime);
    }

    @Override
    public void update(ArticleDTO dto) {
        Long userId = getCurrentUserId();
        Article existing = this.getById(dto.getId());
        if (existing == null || existing.getDeleted() == 1) {
            throw new RuntimeException("帖子不存在");
        }
        if (!existing.getUserId().equals(userId)) {
            throw new RuntimeException("无权限修改他人帖子");
        }
        Article article = new Article();
        article.setId(dto.getId());
        article.setTitle(dto.getTitle());
        article.setContent(dto.getContent());
        article.setSummary(dto.getSummary());
        article.setImageUrls(dto.getImageUrls());
        article.setTags(dto.getTags());
        article.setReviewStatus(0);
        article.setStatus(0);
        this.updateById(article);
    }

    @Override
    public void delete(Long id) {
        Long userId = getCurrentUserId();
        Article existing = this.getById(id);
        if (existing == null || existing.getDeleted() == 1) {
            throw new RuntimeException("帖子不存在");
        }
        if (!existing.getUserId().equals(userId)) {
            throw new RuntimeException("无权限删除他人帖子");
        }
        this.removeById(id);
    }

    @Override
    public void collect(Long articleId) {
        Long userId = getCurrentUserId();
        LambdaQueryWrapper<UserCollect> qw = new LambdaQueryWrapper<UserCollect>()
                .eq(UserCollect::getUserId, userId)
                .eq(UserCollect::getArticleId, articleId);
        UserCollect existing = userCollectMapper.selectOne(qw);
        if (existing != null) {
            userCollectMapper.delete(qw);
            baseMapper.updateCollectCount(articleId, -1);
        } else {
            UserCollect uc = new UserCollect();
            uc.setUserId(userId);
            uc.setArticleId(articleId);
            userCollectMapper.insert(uc);
            baseMapper.updateCollectCount(articleId, 1);
        }
    }

    @Override
    public Page<ArticleVO> getCollectedArticles(int page, int size) {
        Long userId = getCurrentUserId();
        List<Long> ids = userCollectMapper.selectList(
                new LambdaQueryWrapper<UserCollect>().eq(UserCollect::getUserId, userId)
        ).stream().map(UserCollect::getArticleId).toList();

        if (ids.isEmpty()) {
            Page<ArticleVO> empty = new Page<>(page, size);
            empty.setRecords(new ArrayList<>());
            empty.setTotal(0);
            return empty;
        }

        Page<Article> pageParam = new Page<>(page, size);
        Page<Article> articlePage = this.page(pageParam,
                new LambdaQueryWrapper<Article>()
                        .in(Article::getId, ids)
                        .eq(Article::getStatus, 1)
                        .orderByDesc(Article::getCreateTime));
        return convertPageToVo(articlePage);
    }

    @Override
    public void share(Long articleId) {
        baseMapper.incrementShareCount(articleId);
    }

    // =========== 内部辅助方法 ===========

    /**
     * 将 Article 分页转换为 ArticleVO 分页（批量查询用户信息）
     */
    private Page<ArticleVO> convertPageToVo(Page<Article> articlePage) {
        List<Long> userIds = articlePage.getRecords()
                .stream()
                .map(Article::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, User> userMap = userIds.isEmpty() ? new HashMap<>() :
                userService.listByIds(userIds)
                        .stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<ArticleVO> voList = articlePage.getRecords()
                .stream()
                .map(article -> convertToVo(article, userMap))
                .collect(Collectors.toList());

        Page<ArticleVO> voPage = new Page<>();
        BeanUtils.copyProperties(articlePage, voPage);
        voPage.setRecords(voList);
        return voPage;
    }

    private ArticleVO convertToVo(Article article, Map<Long, User> userMap) {
        ArticleVO vo = new ArticleVO();
        // 复制同名字段，包括 imageUrls、tags、collectCount、shareCount、topicId
        BeanUtils.copyProperties(article, vo);

        User author = userMap.get(article.getUserId());
        if (author != null) {
            vo.setAuthorName(author.getUsername());
            vo.setAuthorAvatar(author.getAvatar());
        }

        Long currentUserId = getCurrentUserIdSafe();
        if (currentUserId != null) {
            vo.setIsLiked(redisUtil.isLiked(article.getId(), currentUserId));
        } else {
            vo.setIsLiked(false);
        }

        if (currentUserId != null) {
            Long collectCount = userCollectMapper.selectCount(
                    new LambdaQueryWrapper<UserCollect>()
                            .eq(UserCollect::getUserId, currentUserId)
                            .eq(UserCollect::getArticleId, article.getId())
            );
            vo.setIsCollected(collectCount > 0);
        } else {
            vo.setIsCollected(false);
        }

        return vo;
    }

    /**
     * 将单个 Article 转为 ArticleVO（需单独查询作者信息）
     */
    private ArticleVO convertToVo(Article article) {
        ArticleVO vo = new ArticleVO();
        BeanUtils.copyProperties(article, vo);

        User author = userService.getById(article.getUserId());
        if (author != null) {
            vo.setAuthorName(author.getUsername());
            vo.setAuthorAvatar(author.getAvatar());
        }

        Long currentUserId = getCurrentUserIdSafe();
        if (currentUserId != null) {
            vo.setIsLiked(redisUtil.isLiked(article.getId(), currentUserId));
            Long collectCount = userCollectMapper.selectCount(
                    new LambdaQueryWrapper<UserCollect>()
                            .eq(UserCollect::getUserId, currentUserId)
                            .eq(UserCollect::getArticleId, article.getId())
            );
            vo.setIsCollected(collectCount > 0);
        } else {
            vo.setIsLiked(false);
            vo.setIsCollected(false);
        }

        return vo;
    }
}
