package com.example.podcastbackend.service;

import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.example.podcastbackend.exception.CrossIndexPageLimitException;
import com.example.podcastbackend.exception.InvalidSearchParamException;
import com.example.podcastbackend.embedding.CachedEmbeddingService;
import com.example.podcastbackend.embedding.EmbeddingProfile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.example.podcastbackend.log.QueryLogService;
import com.example.podcastbackend.request.EpisodeSearchRequest;
import com.example.podcastbackend.request.ShowSearchRequest;
import com.example.podcastbackend.response.*;
import com.example.podcastbackend.search.IndexRouter;
import com.example.podcastbackend.search.LangParam;
import com.example.podcastbackend.search.client.ElasticsearchSearchClient;
import com.example.podcastbackend.search.mapper.EpisodeSearchMapper;
import com.example.podcastbackend.search.mapper.ShowSearchMapper;
import com.example.podcastbackend.search.query.EpisodeSearchQueryBuilder;
import com.example.podcastbackend.search.query.ShowSearchQueryBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ShowSearchQueryBuilder showQueryBuilder;
    @Mock
    private EpisodeSearchQueryBuilder episodeQueryBuilder;
    @Mock
    private ElasticsearchSearchClient esClient;
    @Mock
    private ShowSearchMapper showMapper;
    @Mock
    private EpisodeSearchMapper episodeMapper;
    @Mock
    private CachedEmbeddingService cachedEmbeddingService;
    @Mock
    private IndexRouter indexRouter;
    @Mock
    private QueryLogService queryLogService;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(
                showQueryBuilder,
                episodeQueryBuilder,
                esClient,
                showMapper,
                episodeMapper,
                cachedEmbeddingService,
                indexRouter,
                queryLogService,
                new SimpleMeterRegistry(),
                "shows");
    }

    // =====================
    // Show Search Tests
    // =====================

    @Test
    void searchShows_bm25Mode_executesBm25Query() {
        ShowSearchRequest request = mock(ShowSearchRequest.class);
        when(request.getQ()).thenReturn("technology");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);
        when(request.getSearchMode()).thenReturn(ShowSearchRequest.SearchMode.BM25);

        String expectedQuery = "{\"query\":{\"match\":{\"title\":\"technology\"}}}";
        when(showQueryBuilder.buildBm25Query(request)).thenReturn(expectedQuery);

        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> mockEsResponse = mock(SearchResponse.class);
        HitsMetadata<JsonNode> mockHits = mock(HitsMetadata.class);
        TotalHits totalHits = new TotalHits.Builder().value(5).relation(TotalHitsRelation.Eq).build();
        when(mockHits.total()).thenReturn(totalHits);
        when(mockEsResponse.hits()).thenReturn(mockHits);

        when(esClient.search(eq("shows"), eq(expectedQuery))).thenReturn(mockEsResponse);

        ShowSearchItem item = new ShowSearchItem(
                "show:apple:123", "Tech Podcast", "A tech podcast", "en",
                "Publisher", "https://img.url", 100, Map.of(), Map.of(), Map.of());
        ShowSearchResponseData data = new ShowSearchResponseData(1, 10, 5, List.of(item));
        when(showMapper.toResponse(mockEsResponse, request)).thenReturn(ShowSearchResponse.ok(data));

        ShowSearchResponse response = searchService.searchShows(request);

        assertEquals("ok", response.status());
        assertNotNull(response.data());
        assertEquals(5, response.data().total());
        verify(showQueryBuilder).buildBm25Query(request);
        verify(esClient).search("shows", expectedQuery);
        verify(showMapper).toResponse(mockEsResponse, request);
    }

    @Test
    @DisplayName("show KNN: uses EN profile when language filter is exclusively English")
    void searchShows_knn_usesEnProfileForEnglishLanguageFilter() {
        ShowSearchRequest request = mock(ShowSearchRequest.class);
        when(request.getQ()).thenReturn("technology");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);
        when(request.getSearchMode()).thenReturn(ShowSearchRequest.SearchMode.KNN);
        when(request.getLanguage()).thenReturn(List.of("en"));
        when(cachedEmbeddingService.isAvailable()).thenReturn(true);

        float[] mockVector = new float[384];
        when(cachedEmbeddingService.embed("technology", EmbeddingProfile.EN)).thenReturn(mockVector);

        String queryJson = "{\"knn\":{}}";
        when(showQueryBuilder.buildKnnQuery(eq(request), eq(mockVector))).thenReturn(queryJson);

        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> mockEsResponse = mock(SearchResponse.class);
        HitsMetadata<JsonNode> mockHits = mock(HitsMetadata.class);
        TotalHits totalHits = new TotalHits.Builder().value(3).relation(TotalHitsRelation.Eq).build();
        when(mockHits.total()).thenReturn(totalHits);
        when(mockEsResponse.hits()).thenReturn(mockHits);
        when(esClient.search(eq("shows"), eq(queryJson))).thenReturn(mockEsResponse);

        ShowSearchResponseData data = new ShowSearchResponseData(1, 10, 3, List.of());
        when(showMapper.toResponse(mockEsResponse, request)).thenReturn(ShowSearchResponse.ok(data));

        searchService.searchShows(request);

        verify(cachedEmbeddingService).embed("technology", EmbeddingProfile.EN);
    }

    @Test
    @DisplayName("show KNN: uses ZH profile when language filter is null")
    void searchShows_knn_usesZhProfileWhenLanguageFilterNull() {
        ShowSearchRequest request = mock(ShowSearchRequest.class);
        when(request.getQ()).thenReturn("科技");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);
        when(request.getSearchMode()).thenReturn(ShowSearchRequest.SearchMode.KNN);
        when(request.getLanguage()).thenReturn(null);
        when(cachedEmbeddingService.isAvailable()).thenReturn(true);

        float[] mockVector = new float[384];
        when(cachedEmbeddingService.embed("科技", EmbeddingProfile.ZH)).thenReturn(mockVector);

        String queryJson = "{\"knn\":{}}";
        when(showQueryBuilder.buildKnnQuery(eq(request), eq(mockVector))).thenReturn(queryJson);

        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> mockEsResponse = mock(SearchResponse.class);
        HitsMetadata<JsonNode> mockHits = mock(HitsMetadata.class);
        TotalHits totalHits = new TotalHits.Builder().value(5).relation(TotalHitsRelation.Eq).build();
        when(mockHits.total()).thenReturn(totalHits);
        when(mockEsResponse.hits()).thenReturn(mockHits);
        when(esClient.search(eq("shows"), eq(queryJson))).thenReturn(mockEsResponse);

        ShowSearchResponseData data = new ShowSearchResponseData(1, 10, 5, List.of());
        when(showMapper.toResponse(mockEsResponse, request)).thenReturn(ShowSearchResponse.ok(data));

        searchService.searchShows(request);

        verify(cachedEmbeddingService).embed("科技", EmbeddingProfile.ZH);
    }

    @Test
    @DisplayName("show KNN: uses ZH profile when language filter contains mixed languages")
    void searchShows_knn_usesZhProfileForMixedLanguageFilter() {
        ShowSearchRequest request = mock(ShowSearchRequest.class);
        when(request.getQ()).thenReturn("podcast");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);
        when(request.getSearchMode()).thenReturn(ShowSearchRequest.SearchMode.KNN);
        when(request.getLanguage()).thenReturn(List.of("en", "zh-tw"));
        when(cachedEmbeddingService.isAvailable()).thenReturn(true);

        float[] mockVector = new float[384];
        when(cachedEmbeddingService.embed("podcast", EmbeddingProfile.ZH)).thenReturn(mockVector);

        String queryJson = "{\"knn\":{}}";
        when(showQueryBuilder.buildKnnQuery(eq(request), eq(mockVector))).thenReturn(queryJson);

        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> mockEsResponse = mock(SearchResponse.class);
        HitsMetadata<JsonNode> mockHits = mock(HitsMetadata.class);
        TotalHits totalHits = new TotalHits.Builder().value(2).relation(TotalHitsRelation.Eq).build();
        when(mockHits.total()).thenReturn(totalHits);
        when(mockEsResponse.hits()).thenReturn(mockHits);
        when(esClient.search(eq("shows"), eq(queryJson))).thenReturn(mockEsResponse);

        ShowSearchResponseData data = new ShowSearchResponseData(1, 10, 2, List.of());
        when(showMapper.toResponse(mockEsResponse, request)).thenReturn(ShowSearchResponse.ok(data));

        searchService.searchShows(request);

        verify(cachedEmbeddingService).embed("podcast", EmbeddingProfile.ZH);
    }

    // =====================
    // Episode Validation Tests
    // =====================

    @Test
    @DisplayName("page > 100 throws InvalidSearchParamException")
    void searchEpisodes_pageExceedsLimit_throws() {
        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);
        when(request.getQ()).thenReturn("podcast");
        when(request.getPage()).thenReturn(101);
        when(request.getSize()).thenReturn(10);
        when(request.getSearchMode()).thenReturn(EpisodeSearchRequest.SearchMode.BM25);

        assertThrows(InvalidSearchParamException.class,
                () -> searchService.searchEpisodes(request));
        verifyNoInteractions(indexRouter);
    }

    @Test
    @DisplayName("size > 50 throws InvalidSearchParamException")
    void searchEpisodes_sizeExceedsLimit_throws() {
        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);
        when(request.getQ()).thenReturn("podcast");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(51);
        when(request.getSearchMode()).thenReturn(EpisodeSearchRequest.SearchMode.BM25);

        assertThrows(InvalidSearchParamException.class,
                () -> searchService.searchEpisodes(request));
        verifyNoInteractions(indexRouter);
    }

    @Test
    @DisplayName("zh-both + page > 5 throws CrossIndexPageLimitException")
    void searchEpisodes_zhBothPageExceedsLimit_throws() {
        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);
        when(request.getQ()).thenReturn("podcast");
        when(request.getPage()).thenReturn(6);
        when(request.getSize()).thenReturn(10);
        when(request.getLang()).thenReturn("zh-both");
        when(request.getSearchMode()).thenReturn(EpisodeSearchRequest.SearchMode.BM25);
        when(indexRouter.isCrossIndex("zh-both")).thenReturn(true);

        assertThrows(CrossIndexPageLimitException.class,
                () -> searchService.searchEpisodes(request));
    }

    @Test
    @DisplayName("zh-both + non-BM25 mode throws InvalidSearchParamException")
    void searchEpisodes_zhBothNonBm25_throws() {
        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);
        when(request.getQ()).thenReturn("podcast");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);
        when(request.getLang()).thenReturn("zh-both");
        when(request.getSearchMode()).thenReturn(EpisodeSearchRequest.SearchMode.HYBRID);
        when(indexRouter.isCrossIndex("zh-both")).thenReturn(true);

        InvalidSearchParamException exception = assertThrows(InvalidSearchParamException.class,
                () -> searchService.searchEpisodes(request));

        assertEquals("zh-both only supports mode=bm25 currently", exception.getMessage());
    }

    // =====================
    // Episode Routing Tests
    // =====================

    @Test
    @DisplayName("BM25: uses IndexRouter to resolve single-lang index")
    void searchEpisodes_bm25_usesIndexRouter() {
        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);
        when(request.getQ()).thenReturn("podcast");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);
        when(request.getLang()).thenReturn("zh-tw");
        when(request.getSearchMode()).thenReturn(EpisodeSearchRequest.SearchMode.BM25);
        when(indexRouter.isCrossIndex("zh-tw")).thenReturn(false);
        when(indexRouter.resolveIndex("zh-tw")).thenReturn("episodes-zh-tw");
        when(indexRouter.resolveLangParam("zh-tw")).thenReturn(LangParam.ZH_TW);

        String queryJson = "{\"query\":{\"match\":{}}}";
        when(episodeQueryBuilder.buildBm25Query(request)).thenReturn(queryJson);

        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> mockEsResponse = mock(SearchResponse.class);
        HitsMetadata<JsonNode> mockHits = mock(HitsMetadata.class);
        TotalHits totalHits = new TotalHits.Builder().value(5).relation(TotalHitsRelation.Eq).build();
        when(mockHits.total()).thenReturn(totalHits);
        when(mockEsResponse.hits()).thenReturn(mockHits);
        when(esClient.search(eq("episodes-zh-tw"), eq(queryJson))).thenReturn(mockEsResponse);

        EpisodeSearchResponseData data = new EpisodeSearchResponseData(1, 10, 5, List.of());
        when(episodeMapper.toResponse(mockEsResponse, request)).thenReturn(EpisodeSearchResponse.ok(data));

        EpisodeSearchResponse response = searchService.searchEpisodes(request);

        assertEquals("ok", response.status());
        verify(indexRouter).resolveIndex("zh-tw");
        verify(esClient).search("episodes-zh-tw", queryJson);

        ArgumentCaptor<com.example.podcastbackend.log.QueryLogEntry> entryCaptor = ArgumentCaptor
                .forClass(com.example.podcastbackend.log.QueryLogEntry.class);
        verify(queryLogService).logQuery(entryCaptor.capture());
        assertFalse(entryCaptor.getValue().wasDegraded());
        assertNull(entryCaptor.getValue().degradationReason());
    }

    @Test
    @DisplayName("KNN: uses embedding service with resolved index")
    void searchEpisodes_knn_usesEmbeddingServiceAndIndexRouter() {
        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);
        when(request.getQ()).thenReturn("machine learning");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);
        when(request.getLang()).thenReturn("en");
        when(request.getSearchMode()).thenReturn(EpisodeSearchRequest.SearchMode.KNN);
        when(indexRouter.isCrossIndex("en")).thenReturn(false);
        when(indexRouter.resolveIndex("en")).thenReturn("episodes-en");
        when(indexRouter.resolveLangParam("en")).thenReturn(LangParam.EN);
        when(cachedEmbeddingService.isAvailable()).thenReturn(true);

        float[] mockVector = new float[384];
        when(cachedEmbeddingService.embed("machine learning", EmbeddingProfile.EN)).thenReturn(mockVector);

        String queryJson = "{\"knn\":{\"field\":\"embedding\"}}";
        when(episodeQueryBuilder.buildKnnQuery(eq(request), eq(mockVector))).thenReturn(queryJson);

        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> mockEsResponse = mock(SearchResponse.class);
        HitsMetadata<JsonNode> mockHits = mock(HitsMetadata.class);
        TotalHits totalHits = new TotalHits.Builder().value(5).relation(TotalHitsRelation.Eq).build();
        when(mockHits.total()).thenReturn(totalHits);
        when(mockEsResponse.hits()).thenReturn(mockHits);
        when(esClient.search(eq("episodes-en"), eq(queryJson))).thenReturn(mockEsResponse);

        EpisodeSearchResponseData data = new EpisodeSearchResponseData(1, 10, 5, List.of());
        when(episodeMapper.toResponse(mockEsResponse, request)).thenReturn(EpisodeSearchResponse.ok(data));

        EpisodeSearchResponse response = searchService.searchEpisodes(request);

        assertEquals("ok", response.status());
        verify(indexRouter).resolveIndex("en");
        verify(esClient).search("episodes-en", queryJson);
        verify(cachedEmbeddingService).embed("machine learning", EmbeddingProfile.EN);
    }

    @Test
    @DisplayName("KNN: falls back to BM25 when embedding unavailable")
    void searchEpisodes_knn_fallsBackToBm25WhenEmbeddingUnavailable() {
        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);
        when(request.getQ()).thenReturn("podcast");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);
        when(request.getLang()).thenReturn("zh-tw");
        when(request.getSearchMode()).thenReturn(EpisodeSearchRequest.SearchMode.KNN);
        when(indexRouter.isCrossIndex("zh-tw")).thenReturn(false);
        when(indexRouter.resolveIndex("zh-tw")).thenReturn("episodes-zh-tw");
        when(indexRouter.resolveLangParam("zh-tw")).thenReturn(LangParam.ZH_TW);
        when(cachedEmbeddingService.isAvailable()).thenReturn(false);

        String bm25Query = "{\"query\":{\"match\":{\"title\":\"podcast\"}}}";
        when(episodeQueryBuilder.buildBm25Query(request)).thenReturn(bm25Query);

        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> mockEsResponse = mock(SearchResponse.class);
        HitsMetadata<JsonNode> mockHits = mock(HitsMetadata.class);
        TotalHits totalHits = new TotalHits.Builder().value(10).relation(TotalHitsRelation.Eq).build();
        when(mockHits.total()).thenReturn(totalHits);
        when(mockEsResponse.hits()).thenReturn(mockHits);
        when(esClient.search(eq("episodes-zh-tw"), eq(bm25Query))).thenReturn(mockEsResponse);

        EpisodeSearchResponseData data = new EpisodeSearchResponseData(1, 10, 10, List.of());
        when(episodeMapper.toResponse(mockEsResponse, request)).thenReturn(EpisodeSearchResponse.ok(data));

        EpisodeSearchResponse response = searchService.searchEpisodes(request);

        assertEquals("partial_success", response.status());
        assertNotNull(response.warning());
        verify(cachedEmbeddingService, never()).embed(any(), any());
        verify(episodeQueryBuilder, never()).buildKnnQuery(any(), any());
        verify(esClient).search("episodes-zh-tw", bm25Query);

        ArgumentCaptor<com.example.podcastbackend.log.QueryLogEntry> entryCaptor = ArgumentCaptor
                .forClass(com.example.podcastbackend.log.QueryLogEntry.class);
        verify(queryLogService).logQuery(entryCaptor.capture());
        assertTrue(entryCaptor.getValue().wasDegraded());
        assertEquals("embedding_unavailable", entryCaptor.getValue().degradationReason());
        assertEquals("bm25", entryCaptor.getValue().mode());
    }

    @Test
    @DisplayName("HYBRID: executes both queries against resolved index and fuses results")
    void searchEpisodes_hybrid_executesBothQueriesAndFuses() {
        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);
        when(request.getQ()).thenReturn("AI podcast");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);
        when(request.getLang()).thenReturn("zh-tw");
        when(request.getSearchMode()).thenReturn(EpisodeSearchRequest.SearchMode.HYBRID);
        when(indexRouter.isCrossIndex("zh-tw")).thenReturn(false);
        when(indexRouter.resolveIndex("zh-tw")).thenReturn("episodes-zh-tw");
        when(indexRouter.resolveLangParam("zh-tw")).thenReturn(LangParam.ZH_TW);
        when(cachedEmbeddingService.isAvailable()).thenReturn(true);

        float[] mockVector = new float[384];
        when(cachedEmbeddingService.embed("AI podcast", EmbeddingProfile.ZH)).thenReturn(mockVector);

        String bm25Query = "{\"query\":{\"match\":{}},\"size\":100}";
        String knnQuery = "{\"knn\":{},\"size\":100}";
        when(episodeQueryBuilder.buildBm25QueryForHybrid(eq(request), eq(100))).thenReturn(bm25Query);
        when(episodeQueryBuilder.buildKnnQueryForHybrid(any(), eq(mockVector), eq(100))).thenReturn(knnQuery);

        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> bm25Response = mock(SearchResponse.class);
        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> knnResponse = mock(SearchResponse.class);
        HitsMetadata<JsonNode> bm25Hits = mock(HitsMetadata.class);
        HitsMetadata<JsonNode> knnHits = mock(HitsMetadata.class);
        TotalHits bm25Total = new TotalHits.Builder().value(50).relation(TotalHitsRelation.Eq).build();
        TotalHits knnTotal = new TotalHits.Builder().value(30).relation(TotalHitsRelation.Eq).build();
        when(bm25Hits.total()).thenReturn(bm25Total);
        when(knnHits.total()).thenReturn(knnTotal);
        when(bm25Hits.hits()).thenReturn(List.of());
        when(knnHits.hits()).thenReturn(List.of());
        when(bm25Response.hits()).thenReturn(bm25Hits);
        when(knnResponse.hits()).thenReturn(knnHits);
        when(esClient.search(eq("episodes-zh-tw"), eq(bm25Query))).thenReturn(bm25Response);
        when(esClient.search(eq("episodes-zh-tw"), eq(knnQuery))).thenReturn(knnResponse);

        EpisodeSearchResponse response = searchService.searchEpisodes(request);

        assertEquals("ok", response.status());
        verify(esClient).search("episodes-zh-tw", bm25Query);
        verify(esClient).search("episodes-zh-tw", knnQuery);
        verify(cachedEmbeddingService).embed("AI podcast", EmbeddingProfile.ZH);
    }

    @Test
    @DisplayName("HYBRID page>1: items are offset correctly and do not repeat page 1 results")
    void searchEpisodes_hybrid_page2_returnsCorrectOffset() {
        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);
        when(request.getQ()).thenReturn("AI podcast");
        when(request.getPage()).thenReturn(2);
        when(request.getSize()).thenReturn(5);
        when(request.from()).thenReturn(5); // (2-1)*5 = 5
        when(request.getLang()).thenReturn("en");
        when(request.getSearchMode()).thenReturn(EpisodeSearchRequest.SearchMode.HYBRID);
        when(indexRouter.isCrossIndex("en")).thenReturn(false);
        when(indexRouter.resolveIndex("en")).thenReturn("episodes-en");
        when(indexRouter.resolveLangParam("en")).thenReturn(LangParam.EN);
        when(cachedEmbeddingService.isAvailable()).thenReturn(true);

        float[] mockVector = new float[384];
        when(cachedEmbeddingService.embed("AI podcast", EmbeddingProfile.EN)).thenReturn(mockVector);

        String bm25Query = "{\"query\":{\"match\":{}},\"size\":100}";
        String knnQuery = "{\"knn\":{},\"size\":100}";
        when(episodeQueryBuilder.buildBm25QueryForHybrid(eq(request), eq(100))).thenReturn(bm25Query);
        when(episodeQueryBuilder.buildKnnQueryForHybrid(any(), eq(mockVector), eq(100))).thenReturn(knnQuery);

        // Create 10 ordered hits for BM25: e1 (rank 1) through e10 (rank 10)
        List<Hit<JsonNode>> bm25HitsList = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            @SuppressWarnings("unchecked")
            Hit<JsonNode> hit = mock(Hit.class);
            when(hit.id()).thenReturn("e" + i);
            bm25HitsList.add(hit);
        }

        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> bm25Response = mock(SearchResponse.class);
        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> knnResponse = mock(SearchResponse.class);
        HitsMetadata<JsonNode> bm25Hits = mock(HitsMetadata.class);
        HitsMetadata<JsonNode> knnHits = mock(HitsMetadata.class);
        TotalHits bm25Total = new TotalHits.Builder().value(10).relation(TotalHitsRelation.Eq).build();
        TotalHits knnTotal = new TotalHits.Builder().value(0).relation(TotalHitsRelation.Eq).build();
        when(bm25Hits.total()).thenReturn(bm25Total);
        when(knnHits.total()).thenReturn(knnTotal);
        when(bm25Hits.hits()).thenReturn(bm25HitsList);
        when(knnHits.hits()).thenReturn(List.of());
        when(bm25Response.hits()).thenReturn(bm25Hits);
        when(knnResponse.hits()).thenReturn(knnHits);
        when(esClient.search(eq("episodes-en"), eq(bm25Query))).thenReturn(bm25Response);
        when(esClient.search(eq("episodes-en"), eq(knnQuery))).thenReturn(knnResponse);

        // Mock mapper to return identifiable items for hits e6-e10 (page 2)
        for (int i = 6; i <= 10; i++) {
            Hit<JsonNode> hit = bm25HitsList.get(i - 1);
            EpisodeSearchItem item = new EpisodeSearchItem(
                    "e" + i, "Episode " + i, null, Map.of(), null, null, null, null, null, null);
            when(episodeMapper.hitToItem(hit)).thenReturn(item);
        }

        EpisodeSearchResponse response = searchService.searchEpisodes(request);

        assertEquals("ok", response.status());
        assertNotNull(response.data());
        assertEquals(5, response.data().items().size());

        // Page 2 must return items e6-e10, not e1-e5
        List<String> returnedIds = response.data().items().stream()
                .map(EpisodeSearchItem::episodeId)
                .toList();
        assertEquals(List.of("e6", "e7", "e8", "e9", "e10"), returnedIds);
    }

    @Test
    @DisplayName("HYBRID: falls back to BM25 when embedding unavailable")
    void searchEpisodes_hybrid_fallsBackToBm25WhenEmbeddingUnavailable() {
        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);
        when(request.getQ()).thenReturn("podcast");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);
        when(request.getLang()).thenReturn("zh-tw");
        when(request.getSearchMode()).thenReturn(EpisodeSearchRequest.SearchMode.HYBRID);
        when(indexRouter.isCrossIndex("zh-tw")).thenReturn(false);
        when(indexRouter.resolveIndex("zh-tw")).thenReturn("episodes-zh-tw");
        when(indexRouter.resolveLangParam("zh-tw")).thenReturn(LangParam.ZH_TW);
        when(cachedEmbeddingService.isAvailable()).thenReturn(false);

        String bm25Query = "{\"query\":{\"match\":{\"title\":\"podcast\"}}}";
        when(episodeQueryBuilder.buildBm25Query(request)).thenReturn(bm25Query);

        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> mockEsResponse = mock(SearchResponse.class);
        HitsMetadata<JsonNode> mockHits = mock(HitsMetadata.class);
        TotalHits totalHits = new TotalHits.Builder().value(10).relation(TotalHitsRelation.Eq).build();
        when(mockHits.total()).thenReturn(totalHits);
        when(mockEsResponse.hits()).thenReturn(mockHits);
        when(esClient.search(eq("episodes-zh-tw"), eq(bm25Query))).thenReturn(mockEsResponse);

        EpisodeSearchResponseData data = new EpisodeSearchResponseData(1, 10, 10, List.of());
        when(episodeMapper.toResponse(mockEsResponse, request)).thenReturn(EpisodeSearchResponse.ok(data));

        EpisodeSearchResponse response = searchService.searchEpisodes(request);

        assertEquals("partial_success", response.status());
        assertNotNull(response.warning());
        verify(cachedEmbeddingService, never()).embed(any(), any());
        verify(esClient).search("episodes-zh-tw", bm25Query);

        ArgumentCaptor<com.example.podcastbackend.log.QueryLogEntry> entryCaptor = ArgumentCaptor
                .forClass(com.example.podcastbackend.log.QueryLogEntry.class);
        verify(queryLogService).logQuery(entryCaptor.capture());
        assertTrue(entryCaptor.getValue().wasDegraded());
        assertEquals("embedding_unavailable", entryCaptor.getValue().degradationReason());
        assertEquals("bm25", entryCaptor.getValue().mode());
    }

    @Test
    @DisplayName("response contains a non-null UUID as searchRequestId")
    void searchEpisodes_responseContainsRequestId() {
        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);
        when(request.getQ()).thenReturn("podcast");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);
        when(request.getLang()).thenReturn("zh-tw");
        when(request.getSearchMode()).thenReturn(EpisodeSearchRequest.SearchMode.BM25);
        when(indexRouter.isCrossIndex("zh-tw")).thenReturn(false);
        when(indexRouter.resolveIndex("zh-tw")).thenReturn("episodes-zh-tw");
        when(indexRouter.resolveLangParam("zh-tw")).thenReturn(LangParam.ZH_TW);

        String queryJson = "{\"query\":{\"match\":{}}}";
        when(episodeQueryBuilder.buildBm25Query(request)).thenReturn(queryJson);

        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> mockEsResponse = mock(SearchResponse.class);
        HitsMetadata<JsonNode> mockHits = mock(HitsMetadata.class);
        TotalHits totalHits = new TotalHits.Builder().value(5).relation(TotalHitsRelation.Eq).build();
        when(mockHits.total()).thenReturn(totalHits);
        when(mockEsResponse.hits()).thenReturn(mockHits);
        when(esClient.search(anyString(), anyString())).thenReturn(mockEsResponse);

        EpisodeSearchResponseData data = new EpisodeSearchResponseData(1, 10, 5, List.of());
        when(episodeMapper.toResponse(mockEsResponse, request)).thenReturn(EpisodeSearchResponse.ok(data));

        EpisodeSearchResponse response = searchService.searchEpisodes(request);

        assertNotNull(response.searchRequestId());
        assertTrue(response.searchRequestId().matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    @DisplayName("zh-both BM25 logs executed cross-lang mode")
    void searchEpisodes_zhBothBm25_logsExecutedMode() {
        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);
        when(request.getQ()).thenReturn("podcast");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);
        when(request.getLang()).thenReturn("zh-both");
        when(request.getSearchMode()).thenReturn(EpisodeSearchRequest.SearchMode.BM25);
        when(indexRouter.isCrossIndex("zh-both")).thenReturn(true);
        when(indexRouter.resolveIndices("zh-both")).thenReturn(List.of("episodes-zh-tw", "episodes-zh-cn"));
        when(indexRouter.resolveLangParam("zh-both")).thenReturn(LangParam.ZH_BOTH);

        String bm25Query = "{\"query\":{\"match\":{}}}";
        when(episodeQueryBuilder.buildBm25QueryForHybrid(eq(request), eq(100))).thenReturn(bm25Query);

        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> zhTwResponse = mock(SearchResponse.class);
        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> zhCnResponse = mock(SearchResponse.class);
        HitsMetadata<JsonNode> zhTwHits = mock(HitsMetadata.class);
        HitsMetadata<JsonNode> zhCnHits = mock(HitsMetadata.class);
        TotalHits zhTwTotal = new TotalHits.Builder().value(0).relation(TotalHitsRelation.Eq).build();
        TotalHits zhCnTotal = new TotalHits.Builder().value(0).relation(TotalHitsRelation.Eq).build();
        when(zhTwHits.total()).thenReturn(zhTwTotal);
        when(zhCnHits.total()).thenReturn(zhCnTotal);
        when(zhTwHits.hits()).thenReturn(List.of());
        when(zhCnHits.hits()).thenReturn(List.of());
        when(zhTwResponse.hits()).thenReturn(zhTwHits);
        when(zhCnResponse.hits()).thenReturn(zhCnHits);
        when(esClient.search(eq("episodes-zh-tw"), eq(bm25Query))).thenReturn(zhTwResponse);
        when(esClient.search(eq("episodes-zh-cn"), eq(bm25Query))).thenReturn(zhCnResponse);

        EpisodeSearchResponse response = searchService.searchEpisodes(request);

        assertEquals("ok", response.status());

        ArgumentCaptor<com.example.podcastbackend.log.QueryLogEntry> entryCaptor = ArgumentCaptor
                .forClass(com.example.podcastbackend.log.QueryLogEntry.class);
        verify(queryLogService).logQuery(entryCaptor.capture());
        assertEquals("cross_lang_bm25_rrf", entryCaptor.getValue().mode());
    }
}