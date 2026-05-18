package com.example.community.search.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SearchResultDTO {
    private List<PostHit> hits;
    private long totalHits;
    /** "TEXT" | "IMAGE" | "HYBRID" | "IMAGE_REJECTED" — 前端 debug 用。 */
    private String queryMode;

    @Data
    @Builder
    public static class PostHit {
        private String postId;
        private String title;
        private String content;
        private String coverImageUrl;
        private List<String> tags;
        private double score;
    }
}
