package com.example.podcastbackend.service;

import com.example.podcastbackend.request.ShowSearchRequest;
import com.example.podcastbackend.response.ShowSearchResponse;
import com.example.podcastbackend.search.client.ElasticsearchSearchClient;
import com.example.podcastbackend.search.mapper.ShowSearchMapper;
import com.example.podcastbackend.search.query.ShowSearchQueryBuilder;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private final ShowSearchQueryBuilder queryBuilder;
    private final ElasticsearchSearchClient esClient;
    private final ShowSearchMapper mapper;

    public SearchService(
            ShowSearchQueryBuilder queryBuilder,
            ElasticsearchSearchClient esClient,
            ShowSearchMapper mapper
    ) {
        this.queryBuilder = queryBuilder;
        this.esClient = esClient;
        this.mapper = mapper;
    }

    public ShowSearchResponse searchShows(ShowSearchRequest request) {

        if (request.getPage() != null && request.getPage() > 1) {
            return ShowSearchResponse.error(
                    "BAD_REQUEST",
                    "Only page=1 is supported currently"
            );
        }

        String queryJson = queryBuilder.build(request);

        System.out.println("=== ES QUERY ===");
        System.out.println(queryJson);

        var esResult = esClient.search("shows", queryJson);

        return mapper.toResponse(esResult, request);
    }
}