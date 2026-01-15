package com.example.podcastbackend.search.mapper;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.podcastbackend.request.EpisodeSearchRequest;
import com.example.podcastbackend.response.EpisodeSearchItem;
import com.example.podcastbackend.response.EpisodeSearchResponse;
import com.example.podcastbackend.response.EpisodeSearchResponseData;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class EpisodeSearchMapper {

    public EpisodeSearchResponse toResponse(
            SearchResponse<JsonNode> esResponse,
            EpisodeSearchRequest request
    ) {

        List<EpisodeSearchItem> items = esResponse.hits().hits().stream()
                .map(this::toItem)
                .toList();

        long total = esResponse.hits().total() != null
                ? esResponse.hits().total().value()
                : items.size();

        var data = new EpisodeSearchResponseData(
                request.getPage(),
                request.getSize(),
                Math.toIntExact(total),
                items
        );

        return EpisodeSearchResponse.ok(data);
    }

    private EpisodeSearchItem toItem(Hit<JsonNode> hit) {

        JsonNode src = hit.source();
        Map<String, List<String>> highlights =
                hit.highlight() != null ? hit.highlight() : Map.of();

        assert src != null;
        return new EpisodeSearchItem(
                text(src, "episode_id"),
                text(src, "title"),
                text(src, "description"),
                highlights,
                text(src, "published_at"),
                intValue(src, "duration_sec"),
                text(src, "image_url"),
                podcastInfo(src)
        );
    }

    private EpisodeSearchItem.PodcastInfo podcastInfo(JsonNode src) {
        JsonNode show = src.path("show");
        if (show.isMissingNode()) {
            return null;
        }

        return new EpisodeSearchItem.PodcastInfo(
                text(show, "show_id"),
                text(show, "title"),
                text(show, "publisher"),
                text(show, "image_url")
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
}