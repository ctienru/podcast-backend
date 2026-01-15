package com.example.podcastbackend.search.mapper;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.podcastbackend.request.ShowSearchRequest;
import com.example.podcastbackend.response.ShowSearchResponse;
import com.example.podcastbackend.response.ShowSearchResponseData;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShowSearchMapper {

    public ShowSearchResponse toResponse(
            SearchResponse<JsonNode> esResponse,
            ShowSearchRequest request
    ) {

        List<JsonNode> items = esResponse.hits().hits().stream()
                .map(Hit::source)
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
}