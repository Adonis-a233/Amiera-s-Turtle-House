package com.example.community.search.service;

import com.example.community.entity.Article;
import com.example.community.search.document.PostDocument;
import com.example.community.search.event.PostPublishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 帖子索引写入服务。
 *
 * 监听 PostPublishedEvent（由 ArticleServiceImpl 发布），通过 @Async 在独立线程池执行。
 * 写入流程：美食分类检查 → text_vector → image_vector（可选）→ 写入 ES。
 * 失败时记录日志但不中断业务链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostIndexService {

    private final ElasticsearchOperations esOperations;
    private final DashScopeEmbeddingService embeddingService;
    private final FoodClassifierService foodClassifier;

    @Async("searchIndexExecutor")
    @EventListener
    public void onPostPublished(PostPublishedEvent event) {
        Article article = event.getArticle();
        log.info("Indexing post to ES: id={}", article.getId());
        try {
            PostDocument doc = buildDocument(article);
            esOperations.save(doc);
            log.info("Post indexed successfully: id={}", article.getId());
        } catch (Exception e) {
            log.error("Failed to index post id={}: {}", article.getId(), e.getMessage(), e);
        }
    }

    private PostDocument buildDocument(Article article) {
        PostDocument doc = new PostDocument();
        doc.setId(String.valueOf(article.getId()));
        doc.setTitle(article.getTitle());
        doc.setContent(article.getSummary());
        doc.setTags(article.getTags());
        doc.setUserId(article.getUserId());
        doc.setPublishedAt(article.getCreateTime());

        String textForEmbed = buildTextForEmbed(article);
        doc.setTextVector(embeddingService.embedText(textForEmbed));

        if (!CollectionUtils.isEmpty(article.getImageUrls())) {
            String coverUrl = article.getImageUrls().get(0);
            doc.setCoverImageUrl(coverUrl);
            tryBuildImageVector(doc, coverUrl);
        }

        return doc;
    }

    private String buildTextForEmbed(Article article) {
        StringBuilder sb = new StringBuilder();
        sb.append(article.getTitle());
        if (StringUtils.hasText(article.getSummary())) {
            sb.append("\n").append(article.getSummary());
        }
        if (!CollectionUtils.isEmpty(article.getTags())) {
            sb.append("\n").append(String.join(" ", article.getTags()));
        }
        return sb.toString();
    }

    /**
     * 生成 imageVector。DashScope 直接接受 URL，无需本地下载图片。
     * 失败时仅记录日志，text_vector 单独也能检索。
     */
    private void tryBuildImageVector(PostDocument doc, String imageUrl) {
        try {
            if (!foodClassifier.isFoodUrl(imageUrl)) {
                log.info("Post id={} cover image is not food, skipping imageVector.", doc.getId());
                return;
            }
            float[] vec = embeddingService.embedImageUrl(imageUrl);
            if (vec != null) doc.setImageVector(vec);
        } catch (Exception e) {
            log.warn("Failed to build imageVector for post id={}: {}", doc.getId(), e.getMessage());
        }
    }
}
