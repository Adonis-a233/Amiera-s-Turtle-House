package com.example.community.search.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 专用于 Ollama 的 RestClient bean。
 * ElasticsearchClient 由 Spring Data ES 自动配置（spring.elasticsearch.uris 生效即可）。
 */
@Configuration
public class ElasticsearchConfig {

    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    @Bean("ollamaRestClient")
    public RestClient ollamaRestClient() {
        return RestClient.builder()
                .baseUrl(ollamaBaseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
