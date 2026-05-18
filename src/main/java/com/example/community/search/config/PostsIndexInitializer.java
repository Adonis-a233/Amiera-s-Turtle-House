package com.example.community.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 启动时确保 ES 索引 posts 存在，并包含 kNN 所需的 dense_vector 配置。
 * 字段名使用 camelCase 与 Spring Data ES 序列化保持一致。
 * 索引已存在时跳过，不影响已有数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostsIndexInitializer {

    private final ElasticsearchClient esClient;

    @PostConstruct
    public void init() {
        try {
            boolean exists = esClient.indices()
                    .exists(ExistsRequest.of(e -> e.index("posts")))
                    .value();
            if (exists) {
                log.info("ES index posts already exists, skipping creation.");
                return;
            }

            esClient.indices().create(CreateIndexRequest.of(c -> c
                    .index("posts")
                    .mappings(m -> m
                            .properties("title",        p -> p.text(t -> t))
                            .properties("content",      p -> p.text(t -> t))
                            .properties("coverImageUrl",p -> p.keyword(k -> k))
                            .properties("tags",         p -> p.keyword(k -> k))
                            .properties("userId",       p -> p.long_(l -> l))
                            .properties("publishedAt",  p -> p.date(d -> d))
                            .properties("textVector",   p -> p.denseVector(dv -> dv
                                    .dims(1024).index(true).similarity("cosine")))
                            .properties("imageVector",  p -> p.denseVector(dv -> dv
                                    .dims(1024).index(true).similarity("cosine")))
                    )
            ));
            log.info("ES index posts created.");
        } catch (Exception e) {
            log.warn("PostsIndexInitializer: ES may not be running - {}", e.getMessage());
        }
    }
}
