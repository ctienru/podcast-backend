package com.example.podcastbackend.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single episode search event written asynchronously to the
 * {@code query-logs} Elasticsearch index.
 */
public record QueryLogEntry(
        String requestId,
        String timestamp,
        String query,
        String queryLang,
        String selectedLang,
        String mode,
        String targetIndex,
        boolean crossLang,
        int resultCount,
        List<String> resultIds,
        List<String> resultLanguages,
        int page,
        long latencyMs,
        boolean wasDegraded,
        String degradationReason
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Serialises to a snake_case JSON document ready for ES indexing. */
    public String toJson() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("request_id", requestId);
        doc.put("timestamp", timestamp);
        doc.put("query", query);
        doc.put("query_lang", queryLang);
        doc.put("selected_lang", selectedLang);
        doc.put("mode", mode);
        doc.put("target_index", targetIndex);
        doc.put("is_cross_lang", crossLang);
        doc.put("result_count", resultCount);
        doc.put("result_ids", resultIds);
        doc.put("result_languages", resultLanguages);
        doc.put("page", page);
        doc.put("latency_ms", latencyMs);
        doc.put("was_degraded", wasDegraded);
        if (degradationReason != null) {
            doc.put("degradation_reason", degradationReason);
        }
        try {
            return MAPPER.writeValueAsString(doc);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize QueryLogEntry", e);
        }
    }
}
