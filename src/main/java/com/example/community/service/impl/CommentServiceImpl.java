package com.example.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.community.entity.Article;
import com.example.community.entity.Comment;
import com.example.community.entity.User;
import com.example.community.mapper.ArticleMapper;
import com.example.community.mapper.CommentMapper;
import com.example.community.service.CommentService;
import com.example.community.service.UserService;
import com.example.community.vo.CommentVO;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评论Service实现类
 */
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment>
        implements CommentService {

    /** 最大嵌套深度（0-indexed）：允许 depth 0..4，共5层，超出则挂到同层 */
    private static final int MAX_DEPTH = 4;

    @Resource
    private UserService userService;

    @Resource
    private ArticleMapper articleMapper;

    /**
     * 添加评论
     */
    @Override
    public void addComment(Long articleId, String content, Long parentId) {
        // 验证评论内容
        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("评论内容不能为空");
        }

        // 获取当前登录用户
        String username = (String) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        User user = userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证文章是否存在
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new RuntimeException("文章不存在");
        }

        // 确定实际父评论 ID 和深度
        long actualParentId = (parentId == null) ? 0L : parentId;
        int depth = 0;

        if (actualParentId != 0L) {
            Comment parentComment = this.getById(actualParentId);
            if (parentComment == null) {
                throw new RuntimeException("父评论不存在");
            }

            int parentDepth = parentComment.getDepth() == null ? 0 : parentComment.getDepth();
            if (parentDepth >= MAX_DEPTH) {
                // 父评论已在最大深度，将新评论挂到父评论的父节点下，成为同层兄弟。
                // 这样树结构永远不会超过 MAX_DEPTH+1 层（5层），避免答辩中提到的深层递归性能问题。
                actualParentId = parentComment.getParentId();
                depth = MAX_DEPTH;
            } else {
                depth = parentDepth + 1;
            }
        }

        // 创建评论
        Comment comment = new Comment();
        comment.setArticleId(articleId);
        comment.setContent(content);
        comment.setUserId(user.getId());
        comment.setParentId(actualParentId);
        comment.setDepth(depth);

        this.save(comment);
    }

    /**
     * 获取文章的评论树
     */
    @Override
    public List<CommentVO> getTreeList(Long articleId) {
        // 1. 查出文章下所有评论，按创建时间升序
        List<Comment> allComments = this.list(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getArticleId, articleId)
                        .orderByAsc(Comment::getCreateTime)
        );

        if (allComments.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 批量获取用户信息（避免循环查库）
        List<Long> userIds = allComments.stream()
                .map(Comment::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, User> userMap = userService.listByIds(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 3. 转为VO
        List<CommentVO> allVos = allComments.stream()
                .map(c -> {
                    CommentVO vo = new CommentVO();
                    BeanUtils.copyProperties(c, vo);

                    User u = userMap.get(c.getUserId());
                    if (u != null) {
                        vo.setUsername(u.getUsername());
                        vo.setUserAvatar(u.getAvatar());
                    }

                    return vo;
                })
                .collect(Collectors.toList());

        // 4. 构建树形结构（内存处理）
        // [修复] 原代码 vo.getParentId() == 0，其中 getParentId() 返回 Long（包装类型）
        // 与 int 字面量 0 比较时，Java 会自动拆箱（unboxing）为 long == 0，
        // 虽然通常不会空指针（因为我们总是赋值为0L），但这是不安全的写法，
        // 且 Long == Long 不能用 == 比较（超出 -128~127 缓存范围就是不同对象）。
        // 修复为 Long.valueOf(0L).equals(vo.getParentId()) 或 vo.getParentId() == 0L（unboxing安全）
        // 更清晰的写法：明确与 0L 的 equals 比较。
        List<CommentVO> rootNodes = allVos.stream()
                .filter(vo -> Long.valueOf(0L).equals(vo.getParentId()))
                .collect(Collectors.toList());

        // 递归找子节点
        for (CommentVO root : rootNodes) {
            findChildren(root, allVos);
        }

        return rootNodes;
    }

    /**
     * 删除评论（仅本人可删）
     */
    @Override
    public void delete(Long commentId) {
        Long userId = userService.getCurrentUserId();
        Comment comment = this.getById(commentId);
        if (comment == null || comment.getDeleted() == 1) {
            throw new RuntimeException("评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new RuntimeException("无权限删除他人评论");
        }
        this.removeById(commentId);
    }

    /**
     * 递归查找子评论
     */
    private void findChildren(CommentVO parent, List<CommentVO> allList) {
        List<CommentVO> children = allList.stream()
                .filter(vo -> vo.getParentId().equals(parent.getId()))
                .collect(Collectors.toList());

        parent.setChildren(children);

        // 递归处理子节点
        for (CommentVO child : children) {
            findChildren(child, allList);
        }
    }
}
