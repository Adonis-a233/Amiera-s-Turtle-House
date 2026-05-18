package com.example.community.im.filter;

import com.example.community.im.mapper.SensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class SensitiveWordFilter {

    @Resource
    private SensitiveWordMapper sensitiveWordMapper;

    /**
     * AtomicReference 替代 volatile Trie：
     * - reload() 构建好新 Trie 后原子替换，期间 filter() 持有旧 Trie 快照继续运行，零阻塞
     * - 原 synchronized 的 reload() 与无锁的 filter() 组合在并发替换时行为不明确
     * - Trie 本身构建后不可变，持有快照引用是安全的
     */
    private final AtomicReference<Trie> trieRef = new AtomicReference<>();

    @PostConstruct
    public void init() {
        reload();
    }

    public String filter(String text) {
        Trie trie = trieRef.get();
        if (text == null || text.isBlank() || trie == null) return text;
        List<Emit> emits = new ArrayList<>(trie.parseText(text));
        if (emits.isEmpty()) return text;
        emits.sort(Comparator.comparingInt(Emit::getStart).reversed());
        StringBuilder sb = new StringBuilder(text);
        for (Emit emit : emits) {
            int start = emit.getStart();
            int end = emit.getEnd() + 1;
            sb.replace(start, end, "*".repeat(end - start));
        }
        return sb.toString();
    }

    public void reload() {
        List<String> words = sensitiveWordMapper.selectAllWords();
        Trie.TrieBuilder builder = Trie.builder().ignoreCase();
        for (String word : words) {
            if (word != null && !word.isBlank()) {
                builder.addKeyword(word);
            }
        }
        trieRef.set(builder.build());
        log.info("敏感词库加载完成，共 {} 个词", words.size());
    }
}
