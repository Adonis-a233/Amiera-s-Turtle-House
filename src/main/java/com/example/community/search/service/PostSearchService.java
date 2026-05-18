package com.example.community.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.community.search.document.PostDocument;
import com.example.community.search.dto.SearchRequestDTO;
import com.example.community.search.dto.SearchResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 搜索编排服务。
 *
 * 三种模式：
 *   TEXT   : query expansion → embed text → single kNN on textVector
 *   IMAGE  : food check → embed image → single kNN on imageVector
 *   HYBRID : 两路 kNN + ES 原生 RRF 融合
 *
 * 使用原生 ElasticsearchClient，因为 Spring Data ES 高阶封装不直接支持 RRF rank 参数。
 * ES 字段名使用 camelCase（textVector / imageVector），与 PostsIndexInitializer 保持一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostSearchService {

    private final ElasticsearchClient esClient;
    private final DashScopeEmbeddingService embeddingService;
    private final QueryExpansionService queryExpansionService;
    private final FoodClassifierService foodClassifier;

    @Value("${search.knn.k}")
    private int knnK;

    @Value("${search.knn.num-candidates}")
    private int numCandidates;

    @Value("${search.rrf.window-size}")
    private int rrfWindowSize;

    @Value("${search.rrf.rank-constant}")
    private int rrfRankConstant;

    @Value("${search.result-size}")
    private int resultSize;

    public SearchResultDTO search(SearchRequestDTO request) {
        boolean hasText  = StringUtils.hasText(request.getTextQuery());
        boolean hasImage = StringUtils.hasText(request.getImageBase64());

        if (!hasText && !hasImage) {
            throw new IllegalArgumentException("textQuery 和 imageBase64 不能同时为空");
        }

        if (hasText && hasImage) return searchHybrid(request);
        if (hasText)             return searchByText(request.getTextQuery());
        return searchByImage(request.getImageBase64(), request.getImageMimeType());
    }

    private SearchResultDTO searchByText(String query) {
        String expandedQuery = queryExpansionService.expand(query);
        float[] textVector   = embeddingService.embedText(expandedQuery);
        return executeSearch(List.of(buildKnn("textVector", textVector)), false, "TEXT");
    }

    private SearchResultDTO searchByImage(String base64Image, String mimeType) {
        if (!foodClassifier.isFood(base64Image, mimeType)) {
            log.info("Search image is not food, returning empty result.");
            return SearchResultDTO.builder().hits(List.of()).totalHits(0).queryMode("IMAGE_REJECTED").build();
        }
        float[] imageVector = embeddingService.embedImage(base64Image, mimeType);
        return executeSearch(List.of(buildKnn("imageVector", imageVector)), false, "IMAGE");
    }

    private SearchResultDTO searchHybrid(SearchRequestDTO request) {
        if (!foodClassifier.isFood(request.getImageBase64(), request.getImageMimeType())) {
            log.info("Hybrid search: image not food, fallback to text-only.");
            return searchByText(request.getTextQuery());
        }

        String expandedQuery = queryExpansionService.expand(request.getTextQuery());
        float[] textVector   = embeddingService.embedText(expandedQuery);
        float[] imageVector  = embeddingService.embedImage(request.getImageBase64(), request.getImageMimeType());

        List<KnnSearch> knns = List.of(
                buildKnn("textVector",  textVector),
                buildKnn("imageVector", imageVector)
        );
        return executeSearch(knns, true, "HYBRID");
    }

    private KnnSearch buildKnn(String field, float[] vector) {
        List<Float> queryVector = new ArrayList<>(vector.length);
        for (float v : vector) queryVector.add(v);

        return KnnSearch.of(k -> k
                .field(field)
                .queryVector(queryVector)
                .k((long) knnK)
                .numCandidates((long) numCandidates)
        );
    }

    private SearchResultDTO executeSearch(List<KnnSearch> knns, boolean useRrf, String mode) {
        try {
            SearchResponse<PostDocument> response = esClient.search(s -> {
                s.index("posts")
                 .knn(knns)
                 .size(resultSize)
                 .source(src -> src.filter(f -> f.excludes("textVector", "imageVector")));

                if (useRrf) {
                    s.rank(r -> r.rrf(rrf -> rrf
                            .windowSize((long) rrfWindowSize)
                            .rankConstant((long) rrfRankConstant)
                    ));
                }
                return s;
            }, PostDocument.class);

            List<SearchResultDTO.PostHit> hits = response.hits().hits().stream()
                    .map(this::toPostHit)
                    .collect(Collectors.toList());

            long total = response.hits().total() != null
                    ? response.hits().total().value() : hits.size();

            return SearchResultDTO.builder().hits(hits).totalHits(total).queryMode(mode).build();

        } catch (Exception e) {
            log.error("ES search failed: {}", e.getMessage(), e);
            throw new RuntimeException("搜索服务暂时不可用", e);
        }
    }

    private SearchResultDTO.PostHit toPostHit(Hit<PostDocument> hit) {
        PostDocument doc = hit.source();
        return SearchResultDTO.PostHit.builder()
                .postId(hit.id())
                .title(doc != null ? doc.getTitle() : "")
                .content(doc != null ? doc.getContent() : "")
                .coverImageUrl(doc != null ? doc.getCoverImageUrl() : null)
                .tags(doc != null ? doc.getTags() : List.of())
                .score(hit.score() != null ? hit.score() : 0.0)
                .build();
    }
}
