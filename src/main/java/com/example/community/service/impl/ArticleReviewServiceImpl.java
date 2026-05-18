package com.example.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.community.entity.Article;
import com.example.community.mapper.ArticleMapper;
import com.example.community.service.ArticleReviewService;
import com.example.community.utils.DashScopeClient;
import com.example.community.utils.PageResult;
import com.example.community.vo.ArticleVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ArticleReviewServiceImpl implements ArticleReviewService {

    @Resource
    private ArticleMapper articleMapper;

    @Resource
    private DashScopeClient dashScopeClient;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    @Async("recommendTaskExecutor")
    public void reviewByAi(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) return;

        String prompt = "你是社区内容审核助手，请判断以下帖子是否符合社区规范（不含违法、色情、暴力、诈骗、广告等内容）。\n"
                + "标题：" + article.getTitle() + "\n"
                + "内容：" + article.getContent() + "\n\n"
                + "只输出 JSON，不要任何解释：{\"pass\": true/false, \"confident\": true/false, \"reason\": \"...\"}";

        try {
            String response = dashScopeClient.generate(prompt);
            if (response == null || response.isBlank()) {
                updateReviewStatus(articleId, 3, "AI无响应，转人工审核", null);
                return;
            }

            String json = response.trim();
            int start = json.indexOf('{');
            int end   = json.lastIndexOf('}');
            if (start < 0 || end < 0) {
                updateReviewStatus(articleId, 3, "AI响应格式异常，转人工审核", null);
                return;
            }
            json = json.substring(start, end + 1);

            JsonNode node = objectMapper.readTree(json);
            boolean pass      = node.path("pass").asBoolean(false);
            boolean confident = node.path("confident").asBoolean(false);
            String  reason    = node.path("reason").asText("");

            int status;
            if (!confident) {
                status = 3;  // 不确定 → 人工审核
            } else if (pass) {
                status = 1;  // AI 通过
            } else {
                status = 2;  // AI 拒绝
            }
            updateReviewStatus(articleId, status, reason, null);

        } catch (Exception e) {
            log.error("AI 审核异常 articleId={}", articleId, e);
            updateReviewStatus(articleId, 3, "AI异常，转人工审核", null);
        }
    }

    @Override
    public void adminApprove(Long articleId, Long adminId) {
        Article article = new Article();
        article.setId(articleId);
        article.setReviewStatus(1);
        article.setReviewedBy(adminId);
        article.setReviewedAt(LocalDateTime.now());
        articleMapper.updateById(article);
    }

    @Override
    public void adminReject(Long articleId, Long adminId, String reason) {
        Article article = new Article();
        article.setId(articleId);
        article.setReviewStatus(2);
        article.setReviewReason(reason);
        article.setReviewedBy(adminId);
        article.setReviewedAt(LocalDateTime.now());
        articleMapper.updateById(article);
    }

    @Override
    public PageResult<ArticleVO> getPendingList(int page, int size) {
        Page<Article> p = articleMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Article>().eq(Article::getReviewStatus, 3)
        );
        List<ArticleVO> vos = p.getRecords().stream().map(a -> {
            ArticleVO vo = new ArticleVO();
            BeanUtils.copyProperties(a, vo);
            return vo;
        }).toList();
        return new PageResult<>(p.getTotal(), vos);
    }

    private void updateReviewStatus(Long articleId, int status, String reason, Long reviewedBy) {
        Article update = new Article();
        update.setId(articleId);
        update.setReviewStatus(status);
        update.setReviewReason(reason);
        update.setReviewedBy(reviewedBy);
        if (reviewedBy != null) update.setReviewedAt(LocalDateTime.now());
        articleMapper.updateById(update);
    }
}
