package com.example.community.search.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 封装对 Ollama /api/embed 端点的调用。
 *
 * Ollama embed API（v0.2+）格式：
 *   POST /api/embed
 *   { "model": "...", "input": "..." }
 *   Response: { "embeddings": [[0.1, 0.2, ...]] }
 *
 * 图片 embedding 通过 data URI 格式传入（"data:image/jpeg;base64,..."）。
 */
@Slf4j
@Service
public class OllamaEmbeddingService {

    private final RestClient restClient;

    @Value("${ollama.models.text-embed}")
    private String textEmbedModel;

    @Value("${ollama.models.vision-embed}")
    private String visionEmbedModel;

    @Autowired
    public OllamaEmbeddingService(@Qualifier("ollamaRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public float[] embedText(String text) {
        return callEmbedApi(textEmbedModel, text);
    }

    public float[] embedImage(String base64Image, String mimeType) {
        String dataUri = "data:" + mimeType + ";base64," + base64Image;
        return callEmbedApi(visionEmbedModel, dataUri);
    }

    @SuppressWarnings("unchecked")
    private float[] callEmbedApi(String model, String input) {
        Map<String, Object> requestBody = Map.of("model", model, "input", input);

        Map<String, Object> response = restClient.post()
                .uri("/api/embed")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("embeddings")) {
            throw new RuntimeException("Ollama embed 响应格式异常, model=" + model);
        }

        List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");
        List<Double> vector = embeddings.get(0);

        float[] result = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            result[i] = vector.get(i).floatValue();
        }
        return result;
    }
}
