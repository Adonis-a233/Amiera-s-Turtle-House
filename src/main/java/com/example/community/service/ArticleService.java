package com.example.community.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.community.dto.ArticleDTO;
import com.example.community.entity.Article;
import com.example.community.vo.ArticleVO;

/**
 * 文章Service接口
 */
public interface ArticleService extends IService<Article> {

    /**
     * 发布文章
     */
    void publish(ArticleDTO dto);

    /**
     * 获取文章列表
     */
    Page<ArticleVO> getList(int page, int size, String sortType);

    /**
     * 获取文章详情
     */
    ArticleVO getDetail(Long id);

    /**
     * 点赞/取消点赞文章
     */
    void likeArticle(Long id);

    /**
     * 获取指定用户发布的文章列表
     */
    Page<ArticleVO> getUserArticles(Long userId, int page, int size);

    /**
     * 获取当前用户点赞过的文章列表
     */
    Page<ArticleVO> getLikedArticles(int page, int size);

    /**
     * 获取当前用户的草稿列表
     */
    Page<ArticleVO> getDrafts(int page, int size);

    /**
     * 模糊搜索文章（按标题和内容）
     */
    Page<ArticleVO> search(String keyword, int page, int size);

    /**
     * 个性化推荐列表（sortType="recommend"时走此链路）
     */
    Page<ArticleVO> getRecommendList(int page, int size);

    /**
     * 记录用户行为（点击/点赞/收藏等），异步写入
     */
    void recordBehavior(Long articleId, int eventType, Integer dwellTime);

    /**
     * 编辑帖子（仅本人可编辑，编辑后重新进入待审核）
     */
    void update(ArticleDTO dto);

    /**
     * 删除帖子（仅本人可删，@TableLogic 软删除）
     */
    void delete(Long id);

    /**
     * 收藏/取消收藏帖子（幂等切换）
     */
    void collect(Long articleId);

    /**
     * 获取当前用户收藏的帖子列表
     */
    Page<ArticleVO> getCollectedArticles(int page, int size);

    /**
     * 分享计数 +1（无需登录）
     */
    void share(Long articleId);
}
