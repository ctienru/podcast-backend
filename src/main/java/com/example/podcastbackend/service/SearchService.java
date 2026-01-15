package com.example.podcastbackend.service;

import com.example.podcastbackend.request.EpisodeSearchRequest;
import com.example.podcastbackend.request.ShowSearchRequest;
import com.example.podcastbackend.response.EpisodeSearchResponse;
import com.example.podcastbackend.response.ShowSearchResponse;
import com.example.podcastbackend.search.client.ElasticsearchSearchClient;
import com.example.podcastbackend.search.mapper.EpisodeSearchMapper;
import com.example.podcastbackend.search.mapper.ShowSearchMapper;
import com.example.podcastbackend.search.query.EpisodeSearchQueryBuilder;
import com.example.podcastbackend.search.query.ShowSearchQueryBuilder;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private final ShowSearchQueryBuilder showQueryBuilder;
    private final EpisodeSearchQueryBuilder episodeQueryBuilder;
    private final ElasticsearchSearchClient esClient;
    private final ShowSearchMapper showMapper;
    private final EpisodeSearchMapper episodeMapper;

    public SearchService(
            ShowSearchQueryBuilder showQueryBuilder,
            EpisodeSearchQueryBuilder episodeQueryBuilder,
            ElasticsearchSearchClient esClient,
            ShowSearchMapper showMapper,
            EpisodeSearchMapper episodeMapper
    ) {
        this.showQueryBuilder = showQueryBuilder;
        this.episodeQueryBuilder = episodeQueryBuilder;
        this.esClient = esClient;
        this.showMapper = showMapper;
        this.episodeMapper = episodeMapper;
    }

    public ShowSearchResponse searchShows(ShowSearchRequest request) {

        if (request.getPage() != null && request.getPage() > 1) {
            return ShowSearchResponse.error(
                    "BAD_REQUEST",
                    "Only page=1 is supported currently"
            );
        }

        String queryJson = showQueryBuilder.build(request);
        var esResult = esClient.search("shows", queryJson);

        return showMapper.toResponse(esResult, request);
    }

    public EpisodeSearchResponse searchEpisodes(EpisodeSearchRequest request) {

        String queryJson = episodeQueryBuilder.build(request);
        var esResult = esClient.search("episodes", queryJson);

        return episodeMapper.toResponse(esResult, request);
    }
}