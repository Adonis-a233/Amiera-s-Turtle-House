package com.example.community.service;

import com.example.community.vo.ArticleVO;
import com.example.community.utils.PageResult;

public interface ArticleReviewService {

    /** 异步 AI 审核：由发帖流程触发，不阻塞发布响应 */
    void reviewByAi(Long articleId);

    void adminApprove(Long articleId, Long adminId);

    void adminReject(Long articleId, Long adminId, String reason);

    PageResult<ArticleVO> getPendingList(int page, int size);
}
