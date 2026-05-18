package com.example.community.controller;

import com.example.community.service.ArticleReviewService;
import com.example.community.service.UserService;
import com.example.community.utils.PageResult;
import com.example.community.utils.Result;
import com.example.community.vo.ArticleVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/review")
public class ArticleReviewController {

    @Resource
    private ArticleReviewService articleReviewService;

    @Resource
    private UserService userService;

    /** 人工审核通过 */
    @PostMapping("/admin/approve/{articleId}")
    public Result<Void> approve(@PathVariable Long articleId) {
        Long adminId = userService.getCurrentUserId();
        articleReviewService.adminApprove(articleId, adminId);
        return Result.success(null);
    }

    /** 人工审核拒绝，body: {"reason": "..."} */
    @PostMapping("/admin/reject/{articleId}")
    public Result<Void> reject(@PathVariable Long articleId,
                               @RequestBody Map<String, String> body) {
        Long adminId = userService.getCurrentUserId();
        articleReviewService.adminReject(articleId, adminId, body.get("reason"));
        return Result.success(null);
    }

    /** 待人工审核列表（review_status = 3） */
    @GetMapping("/admin/pending")
    public Result<PageResult<ArticleVO>> pending(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(articleReviewService.getPendingList(page, size));
    }
}
