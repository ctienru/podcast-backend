package com.example.podcastbackend.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.podcastbackend.response.ShowBatchResponse;
import com.example.podcastbackend.response.ShowDetail;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ShowsService {

    private static final Logger log = LoggerFactory.getLogger(ShowsService.class);

    private final ElasticsearchClient esClient;

    public ShowsService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    /**
     * Batch fetch show details from Elasticsearch
     */
    public ShowBatchResponse batchGetShows(List<String> showIds) {
        if (showIds == null || showIds.isEmpty()) {
            return ShowBatchResponse.ok(Map.of());
        }

        try {
            // Query ES for these show IDs using ids query
            SearchResponse<JsonNode> response = esClient.search(s -> s
                    .index("shows")
                    .query(q -> q
                            .ids(ids -> ids.values(showIds))
                    )
                    .size(showIds.size())
                    .source(src -> src
                            .filter(f -> f
                                    .includes("show_id", "description", "categories", "language", "episode_count")
                            )
                    ),
                    JsonNode.class
            );

            Map<String, ShowDetail> results = new HashMap<>();

            if (response.hits().hits() != null) {
                for (Hit<JsonNode> hit : response.hits().hits()) {
                    JsonNode source = hit.source();
                    if (source == null) continue;

                    String showId = text(source, "show_id");
                    if (showId == null) continue;

                    ShowDetail detail = new ShowDetail(
                            showId,
                            text(source, "description"),
                            parseCategories(source.get("categories")),
                            text(source, "language"),
                            intValue(source, "episode_count")
                    );

                    results.put(showId, detail);
                }
            }

            log.info("Batch fetched {} shows from ES", results.size());
            return ShowBatchResponse.ok(results);

        } catch (Exception e) {
            log.error("Failed to batch fetch shows", e);
            return ShowBatchResponse.error("ES_ERROR", e.getMessage());
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() && v.isTextual() ? v.asText() : null;
    }

    private Integer intValue(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isNumber() ? v.asInt() : null;
    }

    private List<String> parseCategories(JsonNode categoriesNode) {
        if (categoriesNode == null || !categoriesNode.isArray()) {
            return null;
        }

        List<String> categories = new java.util.ArrayList<>();
        for (JsonNode cat : categoriesNode) {
            if (cat.isTextual()) {
                categories.add(cat.asText());
            }
        }

        return categories.isEmpty() ? null : categories;
    }
}
