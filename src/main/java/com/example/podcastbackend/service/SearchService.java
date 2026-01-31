package com.example.podcastbackend.service;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.example.podcastbackend.request.EpisodeSearchRequest;
import com.example.podcastbackend.request.ShowSearchRequest;
import com.example.podcastbackend.response.EpisodeSearchItem;
import com.example.podcastbackend.response.EpisodeSearchResponse;
import com.example.podcastbackend.response.EpisodeSearchResponseData;
import com.example.podcastbackend.response.EpisodeSuggestItem;
import com.example.podcastbackend.response.ShowSearchItem;
import com.example.podcastbackend.response.ShowSearchResponse;
import com.example.podcastbackend.response.ShowSearchResponseData;
import com.example.podcastbackend.response.ShowSuggestItem;
import com.example.podcastbackend.response.SuggestResponse;
import com.example.podcastbackend.response.SuggestResponseData;
import com.example.podcastbackend.search.client.ElasticsearchSearchClient;
import com.example.podcastbackend.search.fusion.RrfFusion;
import com.example.podcastbackend.search.mapper.EpisodeSearchMapper;
import com.example.podcastbackend.search.mapper.ShowSearchMapper;
import com.example.podcastbackend.search.mapper.SuggestMapper;
import com.example.podcastbackend.search.query.EpisodeSearchQueryBuilder;
import com.example.podcastbackend.search.query.ShowSearchQueryBuilder;
import com.example.podcastbackend.search.query.SuggestQueryBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    // RRF parameters
    private static final int RRF_WINDOW_SIZE = 100;
    private static final int RRF_RANK_CONSTANT = 60;

    private final ShowSearchQueryBuilder showQueryBuilder;
    private final EpisodeSearchQueryBuilder episodeQueryBuilder;
    private final SuggestQueryBuilder suggestQueryBuilder;
    private final ElasticsearchSearchClient esClient;
    private final ShowSearchMapper showMapper;
    private final EpisodeSearchMapper episodeMapper;
    private final SuggestMapper suggestMapper;
    private final EmbeddingService embeddingService;
    private final RrfFusion rrfFusion;
    private final String showsIndex;
    private final String episodesIndex;

    public SearchService(
            ShowSearchQueryBuilder showQueryBuilder,
            EpisodeSearchQueryBuilder episodeQueryBuilder,
            SuggestQueryBuilder suggestQueryBuilder,
            ElasticsearchSearchClient esClient,
            ShowSearchMapper showMapper,
            EpisodeSearchMapper episodeMapper,
            SuggestMapper suggestMapper,
            EmbeddingService embeddingService,
            @Value("${elasticsearch.indices.shows:shows}") String showsIndex,
            @Value("${elasticsearch.indices.episodes:episodes}") String episodesIndex
    ) {
        this.showQueryBuilder = showQueryBuilder;
        this.episodeQueryBuilder = episodeQueryBuilder;
        this.suggestQueryBuilder = suggestQueryBuilder;
        this.esClient = esClient;
        this.showMapper = showMapper;
        this.episodeMapper = episodeMapper;
        this.suggestMapper = suggestMapper;
        this.embeddingService = embeddingService;
        this.rrfFusion = new RrfFusion(RRF_RANK_CONSTANT);
        this.showsIndex = showsIndex;
        this.episodesIndex = episodesIndex;
    }

    public ShowSearchResponse searchShows(ShowSearchRequest request) {
        var mode = request.getSearchMode();

        log.info("Searching shows with query: '{}', mode: {}, page: {}, size: {}",
                request.getQ(), mode, request.getPage(), request.getSize());

        return switch (mode) {
            case BM25 -> searchShowsBm25(request);
            case KNN -> searchShowsKnn(request);
            case HYBRID -> searchShowsHybrid(request);
        };
    }

    private ShowSearchResponse searchShowsBm25(ShowSearchRequest request) {
        String queryJson = showQueryBuilder.buildBm25Query(request);
        var esResult = esClient.search(showsIndex, queryJson);
        var response = showMapper.toResponse(esResult, request);

        log.debug("BM25 show search completed, found {} results", esResult.hits().total().value());
        return response;
    }

    private ShowSearchResponse searchShowsKnn(ShowSearchRequest request) {
        if (!embeddingService.isAvailable()) {
            log.warn("Embedding service not available, falling back to BM25");
            return searchShowsBm25(request);
        }

        float[] queryVector = embeddingService.encode(request.getQ());
        String queryJson = showQueryBuilder.buildKnnQuery(request, queryVector);
        var esResult = esClient.search(showsIndex, queryJson);
        var response = showMapper.toResponse(esResult, request);

        log.debug("kNN show search completed, found {} results", esResult.hits().total().value());
        return response;
    }

    private ShowSearchResponse searchShowsHybrid(ShowSearchRequest request) {
        if (!embeddingService.isAvailable()) {
            log.warn("Embedding service not available, falling back to BM25");
            return searchShowsBm25(request);
        }

        // 1. Execute BM25 query
        String bm25QueryJson = showQueryBuilder.buildBm25QueryForHybrid(request, RRF_WINDOW_SIZE);
        SearchResponse<JsonNode> bm25Result = esClient.search(showsIndex, bm25QueryJson);

        // 2. Execute kNN query
        float[] queryVector = embeddingService.encode(request.getQ());
        String knnQueryJson = showQueryBuilder.buildKnnQueryForHybrid(queryVector, RRF_WINDOW_SIZE);
        SearchResponse<JsonNode> knnResult = esClient.search(showsIndex, knnQueryJson);

        // 3. Apply RRF fusion
        List<RrfFusion.FusedResult> fusedResults = rrfFusion.fuse(
                bm25Result,
                knnResult,
                request.getSize()
        );

        // 4. Convert to response
        List<ShowSearchItem> items = fusedResults.stream()
                .map(r -> showMapper.hitToItem(r.hit()))
                .toList();

        int total = Math.min(
                (int) bm25Result.hits().total().value() + (int) knnResult.hits().total().value(),
                RRF_WINDOW_SIZE * 2
        );

        var data = new ShowSearchResponseData(
                request.getPage(),
                request.getSize(),
                total,
                items
        );

        log.info("Hybrid show search completed: bm25={}, knn={}, fused={}",
                bm25Result.hits().hits().size(),
                knnResult.hits().hits().size(),
                fusedResults.size());

        return ShowSearchResponse.ok(data);
    }

    public EpisodeSearchResponse searchEpisodes(EpisodeSearchRequest request) {
        var mode = request.getSearchMode();

        log.info("Searching episodes with query: '{}', mode: {}, page: {}, size: {}",
                request.getQ(), mode, request.getPage(), request.getSize());

        return switch (mode) {
            case BM25 -> searchEpisodesBm25(request);
            case KNN -> searchEpisodesKnn(request);
            case HYBRID -> searchEpisodesHybrid(request);
            case EXACT -> searchEpisodesExact(request);
        };
    }

    private EpisodeSearchResponse searchEpisodesBm25(EpisodeSearchRequest request) {
        String queryJson = episodeQueryBuilder.buildBm25Query(request);
        var esResult = esClient.search(episodesIndex, queryJson);
        var response = episodeMapper.toResponse(esResult, request);

        log.debug("BM25 search completed, found {} results", esResult.hits().total().value());
        return response;
    }

    private EpisodeSearchResponse searchEpisodesKnn(EpisodeSearchRequest request) {
        if (!embeddingService.isAvailable()) {
            log.warn("Embedding service not available, falling back to BM25");
            return searchEpisodesBm25(request);
        }

        float[] queryVector = embeddingService.encode(request.getQ());
        String queryJson = episodeQueryBuilder.buildKnnQuery(request, queryVector);
        var esResult = esClient.search(episodesIndex, queryJson);
        var response = episodeMapper.toResponse(esResult, request);

        log.debug("kNN search completed, found {} results", esResult.hits().total().value());
        return response;
    }

    private EpisodeSearchResponse searchEpisodesHybrid(EpisodeSearchRequest request) {
        if (!embeddingService.isAvailable()) {
            log.warn("Embedding service not available, falling back to BM25");
            return searchEpisodesBm25(request);
        }

        // 1. Execute BM25 query
        String bm25QueryJson = episodeQueryBuilder.buildBm25QueryForHybrid(request, RRF_WINDOW_SIZE);
        SearchResponse<JsonNode> bm25Result = esClient.search(episodesIndex, bm25QueryJson);

        // 2. Execute kNN query
        float[] queryVector = embeddingService.encode(request.getQ());
        String knnQueryJson = episodeQueryBuilder.buildKnnQueryForHybrid(queryVector, RRF_WINDOW_SIZE);
        SearchResponse<JsonNode> knnResult = esClient.search(episodesIndex, knnQueryJson);

        // 3. Apply RRF fusion
        List<RrfFusion.FusedResult> fusedResults = rrfFusion.fuse(
                bm25Result,
                knnResult,
                request.getSize()
        );

        // 4. Convert to response
        List<EpisodeSearchItem> items = fusedResults.stream()
                .map(r -> episodeMapper.hitToItem(r.hit()))
                .toList();

        int total = Math.min(
                (int) bm25Result.hits().total().value() + (int) knnResult.hits().total().value(),
                RRF_WINDOW_SIZE * 2
        );

        var data = new EpisodeSearchResponseData(
                request.getPage(),
                request.getSize(),
                total,
                items
        );

        log.info("Hybrid search completed: bm25={}, knn={}, fused={}",
                bm25Result.hits().hits().size(),
                knnResult.hits().hits().size(),
                fusedResults.size());

        return EpisodeSearchResponse.ok(data);
    }

    private EpisodeSearchResponse searchEpisodesExact(EpisodeSearchRequest request) {
        String queryJson = episodeQueryBuilder.buildExactQuery(request);
        var esResult = esClient.search(episodesIndex, queryJson);
        var response = episodeMapper.toResponse(esResult, request);

        log.debug("Exact search completed, found {} results", esResult.hits().total().value());
        return response;
    }

    public SuggestResponse suggest(String query, int showLimit, int episodeLimit) {
        log.info("Getting suggestions for query: '{}', showLimit: {}, episodeLimit: {}",
                query, showLimit, episodeLimit);

        try {
            // Execute show suggestions
            String showQueryJson = suggestQueryBuilder.buildShowSuggestQuery(query, showLimit);
            var showResult = esClient.search(showsIndex, showQueryJson);
            List<ShowSuggestItem> shows = suggestMapper.toShowSuggestions(showResult);

            // Execute episode suggestions
            String episodeQueryJson = suggestQueryBuilder.buildEpisodeSuggestQuery(query, episodeLimit);
            var episodeResult = esClient.search(episodesIndex, episodeQueryJson);
            List<EpisodeSuggestItem> episodes = suggestMapper.toEpisodeSuggestions(episodeResult);

            log.debug("Suggestions completed: {} shows, {} episodes", shows.size(), episodes.size());

            return SuggestResponse.ok(new SuggestResponseData(shows, episodes));
        } catch (Exception e) {
            log.error("Suggest failed: {}", e.getMessage(), e);
            return SuggestResponse.error("SUGGEST_ERROR", "Failed to get suggestions");
        }
    }
}
