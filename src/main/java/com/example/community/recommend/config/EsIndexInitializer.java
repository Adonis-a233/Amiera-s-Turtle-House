package com.example.community.recommend.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 应用启动时确保 ES 索引 article_recommend 存在，并包含 kNN 所需的 dense_vector 配置。
 * 索引已存在时跳过，不影响已有数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsIndexInitializer {

    private final ElasticsearchClient esClient;

    @PostConstruct
    public void init() {
        try {
            boolean exists = esClient.indices()
                    .exists(ExistsRequest.of(e -> e.index("article_recommend")))
                    .value();
            if (exists) {
                log.info("ES index article_recommend already exists, skipping creation.");
                return;
            }

            esClient.indices().create(CreateIndexRequest.of(c -> c
                    .index("article_recommend")
                    .mappings(m -> m
                            .properties("articleId",   p -> p.long_(l -> l))
                            .properties("title",       p -> p.text(t -> t))
                            .properties("tags",        p -> p.keyword(k -> k))
                            .properties("textVector",  p -> p.denseVector(dv -> dv
                                    .dims(1024).index(true).similarity("cosine")))
                            .properties("imageVector", p -> p.denseVector(dv -> dv
                                    .dims(1024).index(true).similarity("cosine")))
                    )
            ));
            log.info("ES index article_recommend created.");
        } catch (Exception e) {
            log.warn("EsIndexInitializer: ES may not be running - {}", e.getMessage());
        }
    }
}
