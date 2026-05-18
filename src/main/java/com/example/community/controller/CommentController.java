package com.example.community.controller;

import com.example.community.service.CommentService;
import com.example.community.utils.Result;
import com.example.community.vo.CommentVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 评论Controller
 */
@RestController
@RequestMapping("/api/comment")
public class CommentController {

    @Resource
    private CommentService commentService;

    /**
     * 发表评论
     */
    @PostMapping("/add")
    public Result<String> add(@RequestBody Map<String, Object> params) {
        Long articleId = Long.valueOf(params.get("articleId").toString());
        String content = (String) params.get("content");

        // 如果是回复某条评论，传parentId
        Long parentId = params.containsKey("parentId") ?
                Long.valueOf(params.get("parentId").toString()) : 0L;

        commentService.addComment(articleId, content, parentId);
        return Result.success("评论成功", "评论成功");
    }

    /**
     * 获取评论树
     */
    @GetMapping("/list/{articleId}")
    public Result<List<CommentVO>> list(@PathVariable Long articleId) {
        return Result.success(commentService.getTreeList(articleId));
    }

    /**
     * 删除评论（仅本人）
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        commentService.delete(id);
        return Result.success("删除成功", "删除成功");
    }
}
