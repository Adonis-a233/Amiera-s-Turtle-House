package com.example.community.recommend.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.community.recommend.entity.ArticleEsDoc;
import com.example.community.recommend.mapper.ArticleEsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ES 操作：索引帖子 embedding + kNN 语义召回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsArticleService {

    private final ElasticsearchClient esClient;
    private final ArticleEsRepository esRepository;

    /** 保存或更新帖子的 embedding 到 ES。 */
    public void upsert(ArticleEsDoc doc) {
        try {
            esRepository.save(doc);
        } catch (Exception e) {
            log.warn("ES upsert failed articleId={}: {}", doc.getArticleId(), e.getMessage());
        }
    }

    /**
     * 根据用户兴趣向量做 kNN 语义召回，返回帖子 ID 列表（按相关性排序）。
     *
     * @param userVector 用户兴趣向量（768维），由历史 embedding 加权平均得来
     * @param topK       召回数量
     * @param excludeIds 要排除的帖子 ID（可为 null）
     */
    public List<Long> knnRecall(List<Double> userVector, int topK, List<Long> excludeIds) {
        try {
            SearchResponse<ArticleEsDoc> resp = esClient.search(s -> s
                    .index("article_recommend")
                    .knn(k -> k
                            .field("textVector")
                            .queryVector(userVector.stream().map(Double::floatValue).toList())
                            .k((long) topK)
                            .numCandidates((long) topK * 3)
                    )
                    .size(topK),
                    ArticleEsDoc.class
            );

            List<Long> result = new ArrayList<>();
            for (Hit<ArticleEsDoc> hit : resp.hits().hits()) {
                if (hit.source() == null) continue;
                Long aid = hit.source().getArticleId();
                if (excludeIds == null || !excludeIds.contains(aid)) result.add(aid);
            }
            return result;
        } catch (Exception e) {
            log.warn("ES kNN recall failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 按 articleId 从 ES 取 textVector，供用户行为触发时写入 Redis embedding 历史。 */
    public List<Double> getTextVector(Long articleId) {
        try {
            return esRepository.findById(String.valueOf(articleId))
                    .map(ArticleEsDoc::getTextVector)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("ES getTextVector failed articleId={}: {}", articleId, e.getMessage());
            return null;
        }
    }
}
