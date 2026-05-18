package com.example.community.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * DashScope HTTP 客户端，封装多模态 Embedding 和 LLM Chat 两个接口。
 *
 * Embedding endpoint (DashScope 原生协议):
 *   POST https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding
 *   请求: {"model":"qwen3-vl-embedding","input":{"contents":[{"text":"..."}]},"parameters":{"dimension":1024}}
 *   响应: output.embeddings[0].embedding
 *
 * LLM endpoint (OpenAI 兼容):
 *   POST https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
 */
@Slf4j
@Component
public class DashScopeClient {

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.llm-model:qwen3.6-flash-2026-04-16}")
    private String llmModel;

    @Value("${dashscope.embedding.model:qwen3-vl-embedding}")
    private String embeddingModel;

    @Value("${dashscope.embedding.dimension:1024}")
    private int dimension;

    @Value("${mimo.base-url}")
    private String mimoBaseUrl;

    @Value("${mimo.api-key}")
    private String mimoApiKey;

    @Value("${mimo.model:mimo-v2.5}")
    private String mimoModel;

    @Value("${mimo.max-concurrency:8}")
    private int mimoMaxConcurrency;

    private Semaphore mimoSemaphore;

    private static final String EMBEDDING_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding";

    private static final String CHAT_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @jakarta.annotation.PostConstruct
    private void init() {
        mimoSemaphore = new Semaphore(mimoMaxConcurrency);
    }

    /** 文本向量化，失败返回 null。 */
    public float[] embedText(String text) {
        try {
            ObjectNode root = buildEmbeddingRequest();
            contentsArray(root).addObject().put("text", text);
            return callEmbeddingApi(mapper.writeValueAsString(root));
        } catch (Exception e) {
            log.error("DashScope embedText failed: {}", e.getMessage());
            return null;
        }
    }

    /** 通过公网 URL 对图片向量化，失败返回 null。 */
    public float[] embedImageUrl(String imageUrl) {
        try {
            ObjectNode root = buildEmbeddingRequest();
            contentsArray(root).addObject().put("image", imageUrl);
            return callEmbeddingApi(mapper.writeValueAsString(root));
        } catch (Exception e) {
            log.error("DashScope embedImageUrl failed url={}: {}", imageUrl, e.getMessage());
            return null;
        }
    }

    /** 通过 base64 对图片向量化，内部转为 data URI，失败返回 null。 */
    public float[] embedImageBase64(String base64, String mimeType) {
        try {
            String dataUri = "data:" + mimeType + ";base64," + base64;
            ObjectNode root = buildEmbeddingRequest();
            contentsArray(root).addObject().put("image", dataUri);
            return callEmbeddingApi(mapper.writeValueAsString(root));
        } catch (Exception e) {
            log.error("DashScope embedImageBase64 failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * LLM 对话，返回 choices[0].message.content，失败返回 null。
     * 优先调用 mimo-v2.5；当 mimo 并发 slot 耗尽时降级到 qwen3.6-flash。
     */
    public String chat(String systemPrompt, String userMessage) {
        if (mimoSemaphore.tryAcquire()) {
            try {
                return callChatApi(mimoBaseUrl + "/chat/completions", mimoApiKey, mimoModel,
                        systemPrompt, userMessage);
            } catch (Exception e) {
                log.error("Mimo chat failed: {}", e.getMessage());
                return null;
            } finally {
                mimoSemaphore.release();
            }
        }
        log.warn("Mimo concurrent slots exhausted (max={}), falling back to qwen", mimoMaxConcurrency);
        return callQwen(systemPrompt, userMessage);
    }

    /** 无 system prompt 的单轮生成（兼容 OllamaClient.generate 调用方）。 */
    public String generate(String prompt) {
        return chat(null, prompt);
    }

    private String callQwen(String systemPrompt, String userMessage) {
        try {
            return callChatApi(CHAT_URL, apiKey, llmModel, systemPrompt, userMessage);
        } catch (Exception e) {
            log.error("Qwen chat failed: {}", e.getMessage());
            return null;
        }
    }

    private String callChatApi(String url, String key, String model,
                                String systemPrompt, String userMessage) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        ArrayNode messages = root.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.addObject().put("role", "system").put("content", systemPrompt);
        }
        messages.addObject().put("role", "user").put("content", userMessage);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(root)))
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("LLM API [" + model + "] " + response.statusCode() + ": " + response.body());
        }
        JsonNode respNode = mapper.readTree(response.body());
        return respNode.path("choices").get(0).path("message").path("content").asText();
    }

    /** float[] → List<Double>，供 ES dense_vector 写入使用。 */
    public static List<Double> toDoubleList(float[] arr) {
        List<Double> list = new ArrayList<>(arr.length);
        for (float v : arr) list.add((double) v);
        return list;
    }

    // ── 内部方法 ──────────────────────────────────────────────────

    private ObjectNode buildEmbeddingRequest() {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", embeddingModel);
        ObjectNode input = root.putObject("input");
        input.putArray("contents");
        root.putObject("parameters").put("dimension", dimension);
        return root;
    }

    /**
     * 取 root.input.contents 数组。
     * Jackson 2.15 起 withArray(String) 从 JsonNode 基类移除，只保留在 ObjectNode；
     * 用 path().path() 直接定位已存在的 ArrayNode 并强转，避免调用被移除的 API。
     */
    private ArrayNode contentsArray(ObjectNode root) {
        return (ArrayNode) root.path("input").path("contents");
    }

    private float[] callEmbeddingApi(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(EMBEDDING_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Embedding API " + response.statusCode() + ": " + response.body());
        }
        return parseEmbedding(response.body());
    }

    private float[] parseEmbedding(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode embArray = root.path("output").path("embeddings").get(0).path("embedding");
        if (!embArray.isArray() || embArray.isEmpty()) {
            throw new RuntimeException("Embedding 响应格式异常: " + json);
        }
        float[] result = new float[embArray.size()];
        for (int i = 0; i < embArray.size(); i++) {
            result[i] = (float) embArray.get(i).asDouble();
        }
        return result;
    }
}