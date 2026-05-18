package com.example.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.community.dto.ArticleDTO;
import com.example.community.service.ArticleService;
import com.example.community.utils.Result;
import com.example.community.vo.ArticleVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 文章Controller
 */
@RestController
@RequestMapping("/api/article")
public class ArticleController {

    @Resource
    private ArticleService articleService;

    /**
     * 发布文章
     */
    @PostMapping("/publish")
    public Result<String> publish(@RequestBody ArticleDTO dto) {
        articleService.publish(dto);
        return Result.success("操作成功", "操作成功");
    }

    /**
     * 文章列表
     * 支持 /list?sortType=hot 或 /list?sortType=new
     */
    @GetMapping("/list")
    public Result<Page<ArticleVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "new") String sortType) {
        return Result.success(articleService.getList(page, size, sortType));
    }

    /**
     * 文章详情
     */
    @GetMapping("/{id}")
    public Result<ArticleVO> detail(@PathVariable Long id) {
        return Result.success(articleService.getDetail(id));
    }

    /**
     * 点赞/取消点赞（需要登录）
     */
    @PostMapping("/like/{id}")
    public Result<String> like(@PathVariable Long id) {
        articleService.likeArticle(id);
        return Result.success("操作成功", "操作成功");
    }

    /**
     * 获取指定用户发布的文章列表
     */
    @GetMapping("/user/{userId}")
    public Result<Page<ArticleVO>> getUserArticles(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(articleService.getUserArticles(userId, page, size));
    }

    /**
     * 获取当前用户点赞过的文章列表
     */
    @GetMapping("/liked")
    public Result<Page<ArticleVO>> getLikedArticles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(articleService.getLikedArticles(page, size));
    }

    /**
     * 获取当前用户的草稿列表
     */
    @GetMapping("/drafts")
    public Result<Page<ArticleVO>> getDrafts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(articleService.getDrafts(page, size));
    }

    /**
     * 模糊搜索文章
     */
    @GetMapping("/search")
    public Result<Page<ArticleVO>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(articleService.search(keyword, page, size));
    }

    /**
     * 前端上报行为（停留时间等）。
     * eventType: 2=点击(含停留) 6=跳过
     * dwellTime: 停留秒数（eventType=2时传）
     */
    @PostMapping("/behavior/{id}")
    public Result<String> reportBehavior(
            @PathVariable Long id,
            @RequestParam int eventType,
            @RequestParam(required = false) Integer dwellTime) {
        articleService.recordBehavior(id, eventType, dwellTime);
        return Result.success("ok", "ok");
    }

    /**
     * 编辑帖子（仅本人，编辑后重新进入待审核）
     */
    @PutMapping("/update")
    public Result<String> update(@RequestBody ArticleDTO dto) {
        articleService.update(dto);
        return Result.success("修改成功", "修改成功");
    }

    /**
     * 删除帖子（仅本人，软删除）
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        articleService.delete(id);
        return Result.success("删除成功", "删除成功");
    }

    /**
     * 收藏/取消收藏（切换）
     */
    @PostMapping("/collect/{id}")
    public Result<String> collect(@PathVariable Long id) {
        articleService.collect(id);
        return Result.success("操作成功", "操作成功");
    }

    /**
     * 获取当前用户收藏的帖子列表
     */
    @GetMapping("/collected")
    public Result<Page<ArticleVO>> getCollectedArticles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(articleService.getCollectedArticles(page, size));
    }

    /**
     * 分享计数 +1（无需登录）
     */
    @PostMapping("/share/{id}")
    public Result<String> share(@PathVariable Long id) {
        articleService.share(id);
        return Result.success("ok", "ok");
    }
}
