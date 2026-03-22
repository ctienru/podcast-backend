package com.example.podcastbackend.service;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.example.podcastbackend.exception.CrossIndexPageLimitException;
import com.example.podcastbackend.exception.InvalidSearchParamException;
import com.example.podcastbackend.log.QueryLogEntry;
import com.example.podcastbackend.log.QueryLogService;
import com.example.podcastbackend.request.EpisodeSearchRequest;
import com.example.podcastbackend.request.ShowSearchRequest;
import com.example.podcastbackend.response.EpisodeSearchItem;
import com.example.podcastbackend.response.EpisodeSearchResponse;
import com.example.podcastbackend.response.EpisodeSearchResponseData;
import com.example.podcastbackend.response.ShowSearchItem;
import com.example.podcastbackend.response.ShowSearchResponse;
import com.example.podcastbackend.response.ShowSearchResponseData;
import com.example.podcastbackend.search.IndexRouter;
import com.example.podcastbackend.search.client.ElasticsearchSearchClient;
import com.example.podcastbackend.search.fusion.RrfFusion;
import com.example.podcastbackend.search.mapper.EpisodeSearchMapper;
import com.example.podcastbackend.search.mapper.ShowSearchMapper;
import com.example.podcastbackend.search.query.EpisodeSearchQueryBuilder;
import com.example.podcastbackend.search.query.ShowSearchQueryBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    // RRF parameters
    private static final int RRF_WINDOW_SIZE = 100;
    private static final int RRF_RANK_CONSTANT = 60;

    private final ShowSearchQueryBuilder showQueryBuilder;
    private final EpisodeSearchQueryBuilder episodeQueryBuilder;
    private final ElasticsearchSearchClient esClient;
    private final ShowSearchMapper showMapper;
    private final EpisodeSearchMapper episodeMapper;
    private final EmbeddingService embeddingService;
    private final RrfFusion rrfFusion;
    private final IndexRouter indexRouter;
    private final QueryLogService queryLogService;
    private final boolean languageSplitEnabled;
    private final String showsIndex;
    private final String episodesIndex;

    public SearchService(
            ShowSearchQueryBuilder showQueryBuilder,
            EpisodeSearchQueryBuilder episodeQueryBuilder,
            ElasticsearchSearchClient esClient,
            ShowSearchMapper showMapper,
            EpisodeSearchMapper episodeMapper,
            EmbeddingService embeddingService,
            IndexRouter indexRouter,
            QueryLogService queryLogService,
            @Value("${features.language-split:false}") boolean languageSplitEnabled,
            @Value("${elasticsearch.indices.shows:shows}") String showsIndex,
            @Value("${elasticsearch.indices.episodes:episodes}") String episodesIndex
    ) {
        this.showQueryBuilder = showQueryBuilder;
        this.episodeQueryBuilder = episodeQueryBuilder;
        this.esClient = esClient;
        this.showMapper = showMapper;
        this.episodeMapper = episodeMapper;
        this.embeddingService = embeddingService;
        this.rrfFusion = new RrfFusion(RRF_RANK_CONSTANT);
        this.indexRouter = indexRouter;
        this.queryLogService = queryLogService;
        this.languageSplitEnabled = languageSplitEnabled;
        this.showsIndex = showsIndex;
        this.episodesIndex = episodesIndex;
    }

    // =====================================================
    // Show Search
    // =====================================================

    public ShowSearchResponse searchShows(ShowSearchRequest request) {
        var mode = request.getSearchMode();

        log.info("search_shows_start",
                kv("query", request.getQ()), kv("mode", mode),
                kv("page", request.getPage()), kv("size", request.getSize()));

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

        log.debug("search_shows_bm25_completed", kv("count", esResult.hits().total().value()));
        return response;
    }

    private ShowSearchResponse searchShowsKnn(ShowSearchRequest request) {
        if (!embeddingService.isAvailable()) {
            log.warn("embedding_unavailable", kv("fallback", "bm25"), kv("mode", "knn"), kv("entity", "shows"));
            return searchShowsBm25(request);
        }

        float[] queryVector = embeddingService.encode(request.getQ());
        String queryJson = showQueryBuilder.buildKnnQuery(request, queryVector);
        var esResult = esClient.search(showsIndex, queryJson);
        var response = showMapper.toResponse(esResult, request);

        log.debug("search_shows_knn_completed", kv("count", esResult.hits().total().value()));
        return response;
    }

    private ShowSearchResponse searchShowsHybrid(ShowSearchRequest request) {
        if (!embeddingService.isAvailable()) {
            log.warn("embedding_unavailable", kv("fallback", "bm25"), kv("mode", "hybrid"), kv("entity", "shows"));
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

        log.info("search_shows_hybrid_completed",
                kv("bm25_count", bm25Result.hits().hits().size()),
                kv("knn_count", knnResult.hits().hits().size()),
                kv("fused_count", fusedResults.size()));

        return ShowSearchResponse.ok(data);
    }

    // =====================================================
    // Episode Search — public entry point
    // =====================================================

    public EpisodeSearchResponse searchEpisodes(EpisodeSearchRequest request) {
        var mode = request.getSearchMode();

        log.info("search_episodes_start",
                kv("query", request.getQ()), kv("mode", mode),
                kv("page", request.getPage()), kv("size", request.getSize()));

        // Feature flag: when off, fall back to v1 single-index behaviour
        if (!languageSplitEnabled) {
            return searchEpisodesLegacy(request);
        }

        // Page / size upper-bound validation (lang-split path only)
        if (request.getPage() > 100) {
            throw new InvalidSearchParamException("page must be <= 100");
        }
        if (request.getSize() > 50) {
            throw new InvalidSearchParamException("size must be <= 50");
        }

        // Cache cross-index flag to avoid redundant calls
        boolean isCrossLang = indexRouter.isCrossIndex(request.getLang());

        // zh-both cross-index page limit
        if (isCrossLang && request.getPage() > 5) {
            throw new CrossIndexPageLimitException("zh-both search only supports up to 5 pages");
        }

        String requestId = UUID.randomUUID().toString();

        log.info("search_episodes_routed",
                kv("lang", request.getLang()),
                kv("cross_index", isCrossLang),
                kv("request_id", requestId));

        long startTime = System.currentTimeMillis();
        String targetIndex;
        EpisodeSearchResponse response;

        if (isCrossLang) {
            List<String> indices = indexRouter.resolveIndices(request.getLang());
            targetIndex = String.join(",", indices);
            response = searchEpisodesCrossLang(request);
        } else {
            targetIndex = indexRouter.resolveIndex(request.getLang());
            response = switch (mode) {
                case BM25 -> searchEpisodesBm25(request, targetIndex);
                case KNN -> searchEpisodesKnn(request, targetIndex);
                case HYBRID -> searchEpisodesHybrid(request, targetIndex);
                case EXACT -> searchEpisodesExact(request, targetIndex);
            };
        }

        long latencyMs = System.currentTimeMillis() - startTime;

        // Async query log (Batch 7) — silent drop on failure, never blocks the response
        EpisodeSearchResponseData responseData = response.data();
        List<EpisodeSearchItem> items;
        if (responseData != null && responseData.items() != null) {
            items = responseData.items();
        } else {
            items = List.of();
        }
        queryLogService.logQuery(new QueryLogEntry(
                requestId,
                Instant.now().toString(),
                request.getQ(),
                request.getLang(),
                request.getLang(),
                mode.name().toLowerCase(),
                targetIndex,
                isCrossLang,
                items.size(),
                items.stream().map(EpisodeSearchItem::episodeId).toList(),
                items.stream().map(e -> e.language() != null ? e.language() : "unknown").toList(),
                request.getPage(),
                latencyMs
        ));

        return new EpisodeSearchResponse(
                response.status(), response.data(), response.warning(), response.error(), requestId);
    }

    // =====================================================
    // Episode Search — v1 legacy path (feature flag off)
    // =====================================================

    private EpisodeSearchResponse searchEpisodesLegacy(EpisodeSearchRequest request) {
        var mode = request.getSearchMode();
        return switch (mode) {
            case BM25 -> searchEpisodesBm25(request, episodesIndex);
            case KNN -> searchEpisodesKnn(request, episodesIndex);
            case HYBRID -> searchEpisodesHybrid(request, episodesIndex);
            case EXACT -> searchEpisodesExact(request, episodesIndex);
        };
    }

    // =====================================================
    // Episode Search — cross-index (zh-both via RRF)
    // =====================================================

    private EpisodeSearchResponse searchEpisodesCrossLang(EpisodeSearchRequest request) {
        List<String> indices = indexRouter.resolveIndices(request.getLang());
        String bm25QueryJson = episodeQueryBuilder.buildBm25QueryForHybrid(request, RRF_WINDOW_SIZE);

        SearchResponse<JsonNode> zhTwResult = esClient.search(indices.get(0), bm25QueryJson);
        SearchResponse<JsonNode> zhCnResult = esClient.search(indices.get(1), bm25QueryJson);

        List<RrfFusion.FusedResult> fusedResults = rrfFusion.fuse(zhTwResult, zhCnResult, request.getSize());

        List<EpisodeSearchItem> items = fusedResults.stream()
                .map(r -> episodeMapper.hitToItem(r.hit()))
                .toList();

        int total = Math.min(
                (int) zhTwResult.hits().total().value() + (int) zhCnResult.hits().total().value(),
                RRF_WINDOW_SIZE * 2
        );

        var data = new EpisodeSearchResponseData(request.getPage(), request.getSize(), total, items);

        log.info("search_episodes_cross_lang_completed",
                kv("zh_tw_count", zhTwResult.hits().hits().size()),
                kv("zh_cn_count", zhCnResult.hits().hits().size()),
                kv("fused_count", fusedResults.size()));

        return EpisodeSearchResponse.ok(data);
    }

    // =====================================================
    // Episode Search — per-mode private methods
    // =====================================================

    private EpisodeSearchResponse searchEpisodesBm25(EpisodeSearchRequest request, String targetIndex) {
        String queryJson = episodeQueryBuilder.buildBm25Query(request);
        var esResult = esClient.search(targetIndex, queryJson);
        var response = episodeMapper.toResponse(esResult, request);

        log.debug("search_episodes_bm25_completed", kv("count", esResult.hits().total().value()));
        return response;
    }

    private EpisodeSearchResponse searchEpisodesKnn(EpisodeSearchRequest request, String targetIndex) {
        if (!embeddingService.isAvailable()) {
            log.warn("embedding_unavailable", kv("fallback", "bm25"), kv("mode", "knn"), kv("entity", "episodes"));
            return searchEpisodesBm25(request, targetIndex);
        }

        float[] queryVector = embeddingService.encode(request.getQ());
        String queryJson = episodeQueryBuilder.buildKnnQuery(request, queryVector);
        var esResult = esClient.search(targetIndex, queryJson);
        var response = episodeMapper.toResponse(esResult, request);

        log.debug("search_episodes_knn_completed", kv("count", esResult.hits().total().value()));
        return response;
    }

    private EpisodeSearchResponse searchEpisodesHybrid(EpisodeSearchRequest request, String targetIndex) {
        if (!embeddingService.isAvailable()) {
            log.warn("embedding_unavailable", kv("fallback", "bm25"), kv("mode", "hybrid"), kv("entity", "episodes"));
            return searchEpisodesBm25(request, targetIndex);
        }

        // 1. Execute BM25 query
        String bm25QueryJson = episodeQueryBuilder.buildBm25QueryForHybrid(request, RRF_WINDOW_SIZE);
        SearchResponse<JsonNode> bm25Result = esClient.search(targetIndex, bm25QueryJson);

        // 2. Execute kNN query
        float[] queryVector = embeddingService.encode(request.getQ());
        String knnQueryJson = episodeQueryBuilder.buildKnnQueryForHybrid(queryVector, RRF_WINDOW_SIZE);
        SearchResponse<JsonNode> knnResult = esClient.search(targetIndex, knnQueryJson);

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

        log.info("search_episodes_hybrid_completed",
                kv("bm25_count", bm25Result.hits().hits().size()),
                kv("knn_count", knnResult.hits().hits().size()),
                kv("fused_count", fusedResults.size()));

        return EpisodeSearchResponse.ok(data);
    }

    private EpisodeSearchResponse searchEpisodesExact(EpisodeSearchRequest request, String targetIndex) {
        String queryJson = episodeQueryBuilder.buildExactQuery(request);
        var esResult = esClient.search(targetIndex, queryJson);
        var response = episodeMapper.toResponse(esResult, request);

        log.debug("search_episodes_exact_completed", kv("count", esResult.hits().total().value()));
        return response;
    }
}
