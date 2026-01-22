package com.example.podcastbackend.search.mapper;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.podcastbackend.request.ShowSearchRequest;
import com.example.podcastbackend.response.ShowSearchItem;
import com.example.podcastbackend.response.ShowSearchResponse;
import com.example.podcastbackend.response.ShowSearchResponseData;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ShowSearchMapper {

    public ShowSearchResponse toResponse(
            SearchResponse<JsonNode> esResponse,
            ShowSearchRequest request
    ) {

        List<ShowSearchItem> items = esResponse.hits().hits().stream()
                .map(this::toItem)
                .toList();

        long total = esResponse.hits().total() != null
                ? esResponse.hits().total().value()
                : items.size();

        var data = new ShowSearchResponseData(
                request.getPage(),
                request.getSize(),
                Math.toIntExact(total),
                items
        );

        return ShowSearchResponse.ok(data);
    }

    private ShowSearchItem toItem(Hit<JsonNode> hit) {
        JsonNode src = hit.source();
        Map<String, List<String>> highlights =
                hit.highlight() != null ? hit.highlight() : Map.of();

        if (src == null) {
            throw new IllegalStateException("Elasticsearch hit source is null for hit id: " + hit.id());
        }
        return new ShowSearchItem(
                text(src, "show_id"),
                text(src, "title"),
                text(src, "description"),
                text(src, "language"),
                text(src, "publisher"),
                text(src, "image_url"),
                intValue(src, "episode_count"),
                highlights,
                objectToMap(src, "external_ids"),
                objectToMap(src, "external_urls")
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

    private Map<String, String> objectToMap(JsonNode node, String field) {
        JsonNode obj = node.get(field);
        if (obj == null || obj.isNull() || !obj.isObject()) {
            return Map.of();
        }

        Map<String, String> result = new java.util.HashMap<>();
        obj.fields().forEachRemaining(entry -> {
            String value = entry.getValue().asText();
            if (value != null) {
                result.put(entry.getKey(), value);
            }
        });
        return result;
    }
}