package com.example.community.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 零样本美食图片分类器，基于 qwen3-vl-embedding 的跨模态对齐空间。
 *
 * 步骤：
 *   1. 图片编码为向量 V_img
 *   2. FOOD_PROMPT / NON_FOOD_PROMPT 各编码为文本向量（进程内缓存）
 *   3. 余弦相似度：sim(food) > threshold && sim(food) > sim(non_food) → 美食
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FoodClassifierService {

    private final DashScopeEmbeddingService embeddingService;

    @Value("${ollama.food-classifier.threshold}")
    private float threshold;

    private static final String FOOD_PROMPT = "a photo of delicious food or a dish";
    private static final String NON_FOOD_PROMPT = "a photo that does not contain any food";

    // 进程内缓存 prompt 向量，避免重复计算
    private volatile float[] foodPromptVector;
    private volatile float[] nonFoodPromptVector;

    /** base64 图片分类（用户搜索上传场景）。 */
    public boolean isFood(String base64Image, String mimeType) {
        ensurePromptVectorsInitialized();
        float[] imageVector = embeddingService.embedImage(base64Image, mimeType);
        return classify(imageVector);
    }

    /** URL 图片分类（文章索引场景，不需要先下载图片）。 */
    public boolean isFoodUrl(String imageUrl) {
        ensurePromptVectorsInitialized();
        float[] imageVector = embeddingService.embedImageUrl(imageUrl);
        return classify(imageVector);
    }

    private boolean classify(float[] imageVector) {
        if (imageVector == null) return false;
        float simFood    = dot(imageVector, foodPromptVector);
        float simNonFood = dot(imageVector, nonFoodPromptVector);
        log.debug("Food classifier: sim_food={}, sim_non_food={}", simFood, simNonFood);
        return simFood > threshold && simFood > simNonFood;
    }

    private void ensurePromptVectorsInitialized() {
        if (foodPromptVector == null) {
            synchronized (this) {
                if (foodPromptVector == null) {
                    foodPromptVector    = embeddingService.embedText(FOOD_PROMPT);
                    nonFoodPromptVector = embeddingService.embedText(NON_FOOD_PROMPT);
                    log.info("Food classifier prompt vectors initialized.");
                }
            }
        }
    }

    /** 点积 = 余弦相似度（nomic 模型输出已 L2 归一化）。 */
    private float dot(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度不一致: " + a.length + " vs " + b.length);
        }
        float sum = 0f;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }
}
