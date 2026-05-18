package com.example.community.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 调用本地 Ollama 的 HTTP 客户端，不依赖 Spring AI，直接走 java.net.http。
 */
@Component
public class OllamaClient {

    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ollama.embed-text-model:nomic-embed-text-v1.5}")
    private String embedTextModel;

    @Value("${ollama.chat-model:qwen2.5:7b}")
    private String chatModel;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 文本 embedding，返回 float[]（768维）。失败返回 null，调用方需判空。
     */
    public float[] embedText(String text) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", embedTextModel);
            body.put("prompt", text);

            String respJson = post("/api/embeddings", body.toString());
            JsonNode root = mapper.readTree(respJson);
            JsonNode embNode = root.path("embedding");
            if (embNode.isMissingNode() || !embNode.isArray()) return null;

            float[] result = new float[embNode.size()];
            for (int i = 0; i < embNode.size(); i++) {
                result[i] = (float) embNode.get(i).asDouble();
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 调用 chat 模型生成文本（stream=false），返回原始响应文本。失败返回 null。
     */
    public String generate(String prompt) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", chatModel);
            body.put("prompt", prompt);
            body.put("stream", false);

            String respJson = post("/api/generate", body.toString());
            JsonNode root = mapper.readTree(respJson);
            return root.path("response").asText("");
        } catch (Exception e) {
            return null;
        }
    }

    /** float[] → List<Double>，供 ES dense_vector 写入使用。 */
    public static List<Double> toDoubleList(float[] arr) {
        List<Double> list = new ArrayList<>(arr.length);
        for (float v : arr) list.add((double) v);
        return list;
    }

    private String post(String path, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(60))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }
}
