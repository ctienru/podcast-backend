package com.example.podcastbackend.search.mapper;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.podcastbackend.exception.SearchParseException;
import com.example.podcastbackend.request.EpisodeSearchRequest;
import com.example.podcastbackend.response.EpisodeSearchItem;
import com.example.podcastbackend.response.EpisodeSearchResponse;
import com.example.podcastbackend.response.EpisodeSearchResponseData;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class EpisodeSearchMapper {

    private static final Logger log = LoggerFactory.getLogger(EpisodeSearchMapper.class);

    public EpisodeSearchResponse toResponse(
            SearchResponse<JsonNode> esResponse,
            EpisodeSearchRequest request
    ) {
        // 防禦性檢查
        if (esResponse == null || esResponse.hits() == null) {
            throw new SearchParseException(
                    "ES_PARSE_ERROR",
                    "Invalid response from search service"
            );
        }

        if (esResponse.hits().hits() == null) {
            throw new SearchParseException(
                    "ES_PARSE_ERROR",
                    "Missing hits in search response"
            );
        }

        int originalCount = esResponse.hits().hits().size();

        // 解析每筆資料，失敗的會回傳 null 並被過濾掉
        List<EpisodeSearchItem> items = esResponse.hits().hits().stream()
                .map(this::toItemSafe)
                .filter(Objects::nonNull)
                .toList();

        int skipped = originalCount - items.size();

        long total = esResponse.hits().total() != null
                ? esResponse.hits().total().value()
                : items.size();

        // 安全轉換，避免 overflow
        int totalInt = total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;

        var data = new EpisodeSearchResponseData(
                request.getPage(),
                request.getSize(),
                totalInt,
                items
        );

        // 如果有部分資料解析失敗，回傳 partial_success
        if (skipped > 0) {
            String warning = skipped + " item(s) skipped due to parse errors";
            log.warn("Episode search partial success: {}", warning);
            return EpisodeSearchResponse.partial(data, warning);
        }

        return EpisodeSearchResponse.ok(data);
    }

    /**
     * Convert a single ES hit to an EpisodeSearchItem.
     * Used by Hybrid search to convert fused results.
     */
    public EpisodeSearchItem hitToItem(Hit<JsonNode> hit) {
        return toItemSafe(hit);
    }

    private EpisodeSearchItem toItemSafe(Hit<JsonNode> hit) {
        try {
            return toItem(hit);
        } catch (Exception e) {
            log.error("Failed to parse search hit: {}", e.getMessage(), e);
            return null;
        }
    }

    private EpisodeSearchItem toItem(Hit<JsonNode> hit) {
        JsonNode src = hit.source();

        if (src == null) {
            throw new IllegalStateException("Missing source in search hit");
        }

        Map<String, List<String>> highlights =
                hit.highlight() != null ? hit.highlight() : Map.of();

        return new EpisodeSearchItem(
                text(src, "episode_id"),
                text(src, "title"),
                text(src, "description"),
                highlights,
                text(src, "published_at"),
                intValue(src, "duration_sec"),
                text(src, "image_url"),
                text(src, "language"),
                audioInfo(src),
                podcastInfo(src)
        );
    }

    private EpisodeSearchItem.Audio audioInfo(JsonNode src) {
        JsonNode audio = src.path("audio");
        if (audio.isMissingNode()) {
            return null;
        }

        return new EpisodeSearchItem.Audio(
                text(audio, "url"),
                text(audio, "type"),
                longValue(audio, "length_bytes")
        );
    }

    private EpisodeSearchItem.ShowInfo podcastInfo(JsonNode src) {
        JsonNode show = src.path("show");
        if (show.isMissingNode()) {
            return null;
        }

        // Extract external URLs
        EpisodeSearchItem.ExternalUrl externalUrl = null;
        JsonNode externalUrls = show.path("external_urls");
        if (!externalUrls.isMissingNode()) {
            String applePodcastUrl = text(externalUrls, "apple_podcasts");
            if (applePodcastUrl != null) {
                externalUrl = new EpisodeSearchItem.ExternalUrl(applePodcastUrl);
            }
        }

        return new EpisodeSearchItem.ShowInfo(
                text(show, "show_id"),
                text(show, "title"),
                text(show, "publisher"),
                text(show, "image_url"),
                externalUrl
        );
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    private Integer intValue(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isNumber() ? v.asInt() : null;
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isNumber() ? v.asLong() : null;
    }
}
