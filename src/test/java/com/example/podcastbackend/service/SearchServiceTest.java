package com.example.podcastbackend.service;

import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.example.podcastbackend.request.EpisodeSearchRequest;
import com.example.podcastbackend.request.ShowSearchRequest;
import com.example.podcastbackend.response.*;
import com.example.podcastbackend.search.client.ElasticsearchSearchClient;
import com.example.podcastbackend.search.mapper.EpisodeSearchMapper;
import com.example.podcastbackend.search.mapper.ShowSearchMapper;
import com.example.podcastbackend.search.query.EpisodeSearchQueryBuilder;
import com.example.podcastbackend.search.query.ShowSearchQueryBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private EmbeddingService embeddingService;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(
                showQueryBuilder,
                episodeQueryBuilder,
                esClient,
                showMapper,
                episodeMapper,
                embeddingService,
                "shows",
                "episodes"
        );
    }

    // =====================
    // Show Search Tests
    // =====================

    @Test
    void searchShows_delegatesToQueryBuilderAndClient() {
        ShowSearchRequest request = mock(ShowSearchRequest.class);
        when(request.getQ()).thenReturn("technology");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);

        String expectedQuery = "{\"query\":{\"match\":{\"title\":\"technology\"}}}";
        when(showQueryBuilder.build(request)).thenReturn(expectedQuery);

        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> mockEsResponse = mock(SearchResponse.class);
        HitsMetadata<JsonNode> mockHits = mock(HitsMetadata.class);
        TotalHits totalHits = new TotalHits.Builder().value(5).relation(TotalHitsRelation.Eq).build();
        when(mockHits.total()).thenReturn(totalHits);
        when(mockEsResponse.hits()).thenReturn(mockHits);

        when(esClient.search(eq("shows"), eq(expectedQuery))).thenReturn(mockEsResponse);

        ShowSearchItem item = new ShowSearchItem(
                "show:apple:123", "Tech Podcast", "A tech podcast", "en",
                "Publisher", "https://img.url", 100, Map.of(), Map.of(), Map.of()
        );
        ShowSearchResponseData data = new ShowSearchResponseData(1, 10, 5, List.of(item));
        when(showMapper.toResponse(mockEsResponse, request)).thenReturn(ShowSearchResponse.ok(data));

        ShowSearchResponse response = searchService.searchShows(request);

        assertEquals("ok", response.status());
        assertNotNull(response.data());
        assertEquals(5, response.data().total());
        verify(showQueryBuilder).build(request);
        verify(esClient).search("shows", expectedQuery);
        verify(showMapper).toResponse(mockEsResponse, request);
    }

    // =====================
    // Episode BM25 Search Tests
    // =====================

    @Test
    void searchEpisodes_bm25Mode_executesBm25Query() {
        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);
        when(request.getQ()).thenReturn("podcast");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);
        when(request.getSearchMode()).thenReturn(EpisodeSearchRequest.SearchMode.BM25);

        String expectedQuery = "{\"query\":{\"match\":{\"title\":\"podcast\"}}}";
        when(episodeQueryBuilder.buildBm25Query(request)).thenReturn(expectedQuery);

        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> mockEsResponse = mock(SearchResponse.class);
        HitsMetadata<JsonNode> mockHits = mock(HitsMetadata.class);
        TotalHits totalHits = new TotalHits.Builder().value(10).relation(TotalHitsRelation.Eq).build();
        when(mockHits.total()).thenReturn(totalHits);
        when(mockEsResponse.hits()).thenReturn(mockHits);

        when(esClient.search(eq("episodes"), eq(expectedQuery))).thenReturn(mockEsResponse);

        EpisodeSearchResponseData data = new EpisodeSearchResponseData(1, 10, 10, List.of());
        when(episodeMapper.toResponse(mockEsResponse, request)).thenReturn(EpisodeSearchResponse.ok(data));

        EpisodeSearchResponse response = searchService.searchEpisodes(request);

        assertEquals("ok", response.status());
        verify(episodeQueryBuilder).buildBm25Query(request);
        verify(esClient).search("episodes", expectedQuery);
    }

    // =====================
    // Episode kNN Search Tests
    // =====================

    @Test
    void searchEpisodes_knnMode_usesEmbeddingService() {
        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);
        when(request.getQ()).thenReturn("machine learning");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);
        when(request.getSearchMode()).thenReturn(EpisodeSearchRequest.SearchMode.KNN);

        when(embeddingService.isAvailable()).thenReturn(true);
        float[] mockVector = new float[384];
        when(embeddingService.encode("machine learning")).thenReturn(mockVector);

        String expectedQuery = "{\"knn\":{\"field\":\"embedding\"}}";
        when(episodeQueryBuilder.buildKnnQuery(eq(request), eq(mockVector))).thenReturn(expectedQuery);

        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> mockEsResponse = mock(SearchResponse.class);
        HitsMetadata<JsonNode> mockHits = mock(HitsMetadata.class);
        TotalHits totalHits = new TotalHits.Builder().value(5).relation(TotalHitsRelation.Eq).build();
        when(mockHits.total()).thenReturn(totalHits);
        when(mockEsResponse.hits()).thenReturn(mockHits);

        when(esClient.search(eq("episodes"), eq(expectedQuery))).thenReturn(mockEsResponse);

        EpisodeSearchResponseData data = new EpisodeSearchResponseData(1, 10, 5, List.of());
        when(episodeMapper.toResponse(mockEsResponse, request)).thenReturn(EpisodeSearchResponse.ok(data));

        EpisodeSearchResponse response = searchService.searchEpisodes(request);

        assertEquals("ok", response.status());
        verify(embeddingService).isAvailable();
        verify(embeddingService).encode("machine learning");
        verify(episodeQueryBuilder).buildKnnQuery(request, mockVector);
    }

    @Test
    void searchEpisodes_knnMode_fallsBackToBm25WhenEmbeddingUnavailable() {
        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);
        when(request.getQ()).thenReturn("podcast");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);
        when(request.getSearchMode()).thenReturn(EpisodeSearchRequest.SearchMode.KNN);

        when(embeddingService.isAvailable()).thenReturn(false);

        String bm25Query = "{\"query\":{\"match\":{\"title\":\"podcast\"}}}";
        when(episodeQueryBuilder.buildBm25Query(request)).thenReturn(bm25Query);

        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> mockEsResponse = mock(SearchResponse.class);
        HitsMetadata<JsonNode> mockHits = mock(HitsMetadata.class);
        TotalHits totalHits = new TotalHits.Builder().value(10).relation(TotalHitsRelation.Eq).build();
        when(mockHits.total()).thenReturn(totalHits);
        when(mockEsResponse.hits()).thenReturn(mockHits);

        when(esClient.search(eq("episodes"), eq(bm25Query))).thenReturn(mockEsResponse);

        EpisodeSearchResponseData data = new EpisodeSearchResponseData(1, 10, 10, List.of());
        when(episodeMapper.toResponse(mockEsResponse, request)).thenReturn(EpisodeSearchResponse.ok(data));

        EpisodeSearchResponse response = searchService.searchEpisodes(request);

        assertEquals("ok", response.status());
        verify(embeddingService).isAvailable();
        verify(embeddingService, never()).encode(anyString());
        verify(episodeQueryBuilder).buildBm25Query(request);
        verify(episodeQueryBuilder, never()).buildKnnQuery(any(), any());
    }

    // =====================
    // Episode Hybrid Search Tests
    // =====================

    @Test
    void searchEpisodes_hybridMode_executesBothQueriesAndFuses() {
        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);
        when(request.getQ()).thenReturn("AI podcast");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);
        when(request.getSearchMode()).thenReturn(EpisodeSearchRequest.SearchMode.HYBRID);

        when(embeddingService.isAvailable()).thenReturn(true);
        float[] mockVector = new float[384];
        when(embeddingService.encode("AI podcast")).thenReturn(mockVector);

        String bm25Query = "{\"query\":{\"match\":{}},\"size\":100}";
        String knnQuery = "{\"knn\":{},\"size\":100}";
        when(episodeQueryBuilder.buildBm25QueryForHybrid(eq(request), eq(100))).thenReturn(bm25Query);
        when(episodeQueryBuilder.buildKnnQueryForHybrid(eq(mockVector), eq(100))).thenReturn(knnQuery);

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

        when(esClient.search(eq("episodes"), eq(bm25Query))).thenReturn(bm25Response);
        when(esClient.search(eq("episodes"), eq(knnQuery))).thenReturn(knnResponse);

        EpisodeSearchResponse response = searchService.searchEpisodes(request);

        assertEquals("ok", response.status());
        verify(esClient).search("episodes", bm25Query);
        verify(esClient).search("episodes", knnQuery);
        verify(embeddingService).encode("AI podcast");
    }

    @Test
    void searchEpisodes_hybridMode_fallsBackToBm25WhenEmbeddingUnavailable() {
        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);
        when(request.getQ()).thenReturn("podcast");
        when(request.getPage()).thenReturn(1);
        when(request.getSize()).thenReturn(10);
        when(request.getSearchMode()).thenReturn(EpisodeSearchRequest.SearchMode.HYBRID);

        when(embeddingService.isAvailable()).thenReturn(false);

        String bm25Query = "{\"query\":{\"match\":{\"title\":\"podcast\"}}}";
        when(episodeQueryBuilder.buildBm25Query(request)).thenReturn(bm25Query);

        @SuppressWarnings("unchecked")
        SearchResponse<JsonNode> mockEsResponse = mock(SearchResponse.class);
        HitsMetadata<JsonNode> mockHits = mock(HitsMetadata.class);
        TotalHits totalHits = new TotalHits.Builder().value(10).relation(TotalHitsRelation.Eq).build();
        when(mockHits.total()).thenReturn(totalHits);
        when(mockEsResponse.hits()).thenReturn(mockHits);

        when(esClient.search(eq("episodes"), eq(bm25Query))).thenReturn(mockEsResponse);

        EpisodeSearchResponseData data = new EpisodeSearchResponseData(1, 10, 10, List.of());
        when(episodeMapper.toResponse(mockEsResponse, request)).thenReturn(EpisodeSearchResponse.ok(data));

        EpisodeSearchResponse response = searchService.searchEpisodes(request);

        assertEquals("ok", response.status());
        verify(embeddingService).isAvailable();
        verify(embeddingService, never()).encode(anyString());
    }
}
