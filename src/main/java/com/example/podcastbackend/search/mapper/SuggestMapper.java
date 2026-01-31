package com.example.podcastbackend.search.mapper;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.podcastbackend.response.EpisodeSuggestItem;
import com.example.podcastbackend.response.ShowSuggestItem;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SuggestMapper {

    private static final Logger log = LoggerFactory.getLogger(SuggestMapper.class);

    public List<ShowSuggestItem> toShowSuggestions(SearchResponse<JsonNode> response) {
        List<ShowSuggestItem> items = new ArrayList<>();

        for (Hit<JsonNode> hit : response.hits().hits()) {
            try {
                JsonNode source = hit.source();
                if (source == null) continue;

                items.add(new ShowSuggestItem(
                        getTextOrNull(source, "show_id"),
                        getTextOrNull(source, "title"),
                        getTextOrNull(source, "publisher"),
                        getTextOrNull(source, "image_url")
                ));
            } catch (Exception e) {
                log.warn("Failed to map show suggestion: {}", e.getMessage());
            }
        }

        return items;
    }

    public List<EpisodeSuggestItem> toEpisodeSuggestions(SearchResponse<JsonNode> response) {
        List<EpisodeSuggestItem> items = new ArrayList<>();

        for (Hit<JsonNode> hit : response.hits().hits()) {
            try {
                JsonNode source = hit.source();
                if (source == null) continue;

                String showTitle = null;
                JsonNode showNode = source.get("show");
                if (showNode != null) {
                    showTitle = getTextOrNull(showNode, "title");
                }

                items.add(new EpisodeSuggestItem(
                        getTextOrNull(source, "episode_id"),
                        getTextOrNull(source, "title"),
                        showTitle,
                        getTextOrNull(source, "image_url")
                ));
            } catch (Exception e) {
                log.warn("Failed to map episode suggestion: {}", e.getMessage());
            }
        }

        return items;
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asText() : null;
    }
}
