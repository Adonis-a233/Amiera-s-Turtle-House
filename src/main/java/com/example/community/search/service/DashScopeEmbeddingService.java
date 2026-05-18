package com.example.community.search.service;

import com.example.community.utils.DashScopeClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 搜索模块专用的向量化服务，委托 DashScopeClient 实现，
 * 保持与 OllamaEmbeddingService 相同接口方便切换。
 */
@Service
@RequiredArgsConstructor
public class DashScopeEmbeddingService {

    private final DashScopeClient dashScopeClient;

    /** 文本向量（标题 + 摘要 + 标签）。 */
    public float[] embedText(String text) {
        return dashScopeClient.embedText(text);
    }

    /** 通过 base64 对图片向量化（搜索查询时用户上传图片）。 */
    public float[] embedImage(String base64Image, String mimeType) {
        return dashScopeClient.embedImageBase64(base64Image, mimeType);
    }

    /** 通过公网 URL 对图片向量化（文章发布时索引封面图）。 */
    public float[] embedImageUrl(String imageUrl) {
        return dashScopeClient.embedImageUrl(imageUrl);
    }
}
