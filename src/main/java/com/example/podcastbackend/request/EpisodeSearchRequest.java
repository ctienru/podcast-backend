package com.example.podcastbackend.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class EpisodeSearchRequest {

    @NotBlank(message = "Search query cannot be empty")
    private String q;

    @NotNull(message = "Page must not be null")
    @Min(value = 1, message = "Page must be at least 1")
    private Integer page = 1;

    @NotNull(message = "Size must not be null")
    @Min(value = 1, message = "Size must be at least 1")
    @Max(value = 50, message = "Size must not exceed 50")
    private Integer size = 20;

    private String sort; // "relevance" | "date"
    private List<String> language;
    private String lang; // v2: "zh-tw" | "zh-cn" | "en" | "zh-both"
    private String mode; // "bm25" | "knn" | "hybrid" (default: "bm25")

    public String getQ() {
        return q;
    }

    public Integer getPage() {
        return page;
    }

    public Integer getSize() {
        return size;
    }

    public String getSort() {
        return sort;
    }

    public List<String> getLanguage() {
        return language;
    }

    public String getMode() {
        return mode;
    }

    public String getLang() {
        return lang;
    }

    public boolean sortByDate() {
        return "date".equalsIgnoreCase(sort);
    }

    public SearchMode getSearchMode() {
        if (mode == null)
            return SearchMode.BM25; // Default to BM25 for backward compatibility
        return switch (mode.toLowerCase()) {
            case "knn" -> SearchMode.KNN;
            case "hybrid" -> SearchMode.HYBRID;
            case "exact" -> SearchMode.EXACT;
            default -> SearchMode.BM25;
        };
    }

    public enum SearchMode {
        BM25, KNN, HYBRID, EXACT
    }

    public int from() {
        int p = page != null ? page : 1;
        int s = size != null ? size : 20;
        return (p - 1) * s;
    }
}
