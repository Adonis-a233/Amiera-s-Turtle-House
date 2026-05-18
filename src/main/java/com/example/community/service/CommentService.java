package com.example.community.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.community.entity.Comment;
import com.example.community.vo.CommentVO;

import java.util.List;

/**
 * 评论Service接口
 */
public interface CommentService extends IService<Comment> {

    /**
     * 添加评论
     */
    void addComment(Long articleId, String content, Long parentId);

    /**
     * 获取文章的评论树
     */
    List<CommentVO> getTreeList(Long articleId);

    /**
     * 删除评论（仅本人可删）
     */
    void delete(Long commentId);
}