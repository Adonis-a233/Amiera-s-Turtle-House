package com.example.community.search.service;

import com.example.community.utils.DashScopeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 用 qwen3.6-flash-2026-04-16 对用户文字 query 进行语义扩展（Query Expansion）。
 *
 * 例："麻辣" → "麻辣 川菜 火锅 辣椒 重口味 红油 花椒"
 * 扩展后的字符串一起编码为向量，覆盖同义词和相关词的语义。
 *
 * enabled=false 或调用失败时降级返回原始 query，不影响搜索可用性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryExpansionService {

    private final DashScopeClient dashScopeClient;

    @Value("${ollama.query-expansion.enabled}")
    private boolean enabled;

    private static final String SYSTEM_PROMPT =
            "你是一个美食搜索引擎的查询优化助手。根据用户搜索词输出 5~8 个相关扩展词或短语（同义词、相关菜系、口味描述等），" +
            "用空格分隔，不要有序号、标点或解释，只输出词语本身。";

    private static final String EXPANSION_PROMPT_TEMPLATE = "用户搜索词：%s";

    public String expand(String originalQuery) {
        if (!enabled || originalQuery == null || originalQuery.isBlank()) {
            return originalQuery;
        }
        try {
            String expansionWords = dashScopeClient.chat(
                    SYSTEM_PROMPT,
                    String.format(EXPANSION_PROMPT_TEMPLATE, originalQuery)
            );
            if (expansionWords == null || expansionWords.isBlank()) return originalQuery;
            String expanded = originalQuery + " " + expansionWords.trim();
            log.debug("Query expansion: [{}] → [{}]", originalQuery, expanded);
            return expanded;
        } catch (Exception e) {
            log.warn("Query expansion failed, using original query. Error: {}", e.getMessage());
            return originalQuery;
        }
    }
}
