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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final ShowSearchQueryBuilder showQueryBuilder;
    private final EpisodeSearchQueryBuilder episodeQueryBuilder;
    private final ElasticsearchSearchClient esClient;
    private final ShowSearchMapper showMapper;
    private final EpisodeSearchMapper episodeMapper;
    private final String showsIndex;
    private final String episodesIndex;

    public SearchService(
            ShowSearchQueryBuilder showQueryBuilder,
            EpisodeSearchQueryBuilder episodeQueryBuilder,
            ElasticsearchSearchClient esClient,
            ShowSearchMapper showMapper,
            EpisodeSearchMapper episodeMapper,
            @Value("${elasticsearch.indices.shows:shows}") String showsIndex,
            @Value("${elasticsearch.indices.episodes:episodes}") String episodesIndex
    ) {
        this.showQueryBuilder = showQueryBuilder;
        this.episodeQueryBuilder = episodeQueryBuilder;
        this.esClient = esClient;
        this.showMapper = showMapper;
        this.episodeMapper = episodeMapper;
        this.showsIndex = showsIndex;
        this.episodesIndex = episodesIndex;
    }

    public ShowSearchResponse searchShows(ShowSearchRequest request) {
        log.info("Searching shows with query: '{}', page: {}, size: {}",
                request.getQ(), request.getPage(), request.getSize());

        String queryJson = showQueryBuilder.build(request);
        var esResult = esClient.search(showsIndex, queryJson);
        var response = showMapper.toResponse(esResult, request);

        log.debug("Show search completed, found {} results", esResult.hits().total().value());
        return response;
    }

    public EpisodeSearchResponse searchEpisodes(EpisodeSearchRequest request) {
        log.info("Searching episodes with query: '{}', page: {}, size: {}, sort: {}",
                request.getQ(), request.getPage(), request.getSize(), request.getSort());

        String queryJson = episodeQueryBuilder.build(request);
        var esResult = esClient.search(episodesIndex, queryJson);
        var response = episodeMapper.toResponse(esResult, request);

        log.debug("Episode search completed, found {} results", esResult.hits().total().value());
        return response;
    }
}
