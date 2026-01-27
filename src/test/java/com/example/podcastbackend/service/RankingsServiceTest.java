package com.example.podcastbackend.service;

import com.example.podcastbackend.cache.RankingsCache;
import com.example.podcastbackend.client.AppleChartClient;
import com.example.podcastbackend.request.RankingsRequest;
import com.example.podcastbackend.response.RankingsItem;
import com.example.podcastbackend.response.RankingsResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RankingsServiceTest {

    @Mock
    private AppleChartClient appleChartClient;

    @Mock
    private RankingsCache rankingsCache;

    private RankingsService rankingsService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        rankingsService = new RankingsService(appleChartClient, rankingsCache);
    }

    // =====================
    // Podcast Rankings Tests
    // =====================

    @Test
    void getRankings_podcastFromCache_returnsCache() {
        RankingsRequest request = new RankingsRequest("tw", "podcast", 10);

        List<RankingsItem> cachedItems = List.of(
                new RankingsItem(1, "show:apple:123", "Podcast 1", "Artist 1", "https://img.url/1", null, null, null),
                new RankingsItem(2, "show:apple:456", "Podcast 2", "Artist 2", "https://img.url/2", null, null, null)
        );
        when(rankingsCache.get("tw", "podcast")).thenReturn(cachedItems);
        when(rankingsCache.getCachedAt("tw", "podcast")).thenReturn(Instant.now());

        RankingsResponse response = rankingsService.getRankings(request);

        assertEquals("ok", response.status());
        assertNotNull(response.data());
        assertEquals(2, response.data().items().size());
        verify(rankingsCache).get("tw", "podcast");
        verify(appleChartClient, never()).fetchPodcastChart(anyString());
    }

    @Test
    void getRankings_podcastCacheMiss_fetchesFromApi() throws Exception {
        RankingsRequest request = new RankingsRequest("tw", "podcast", 10);

        when(rankingsCache.get("tw", "podcast")).thenReturn(null);

        String appleResponse = """
                {
                    "feed": {
                        "results": [
                            {"id": "123", "name": "Tech Podcast", "artistName": "Tech Corp", "artworkUrl100": "https://img.url/1"},
                            {"id": "456", "name": "News Daily", "artistName": "News Inc", "artworkUrl100": "https://img.url/2"}
                        ]
                    }
                }
                """;
        JsonNode chartData = objectMapper.readTree(appleResponse);
        when(appleChartClient.fetchPodcastChart("tw")).thenReturn(chartData);
        when(rankingsCache.getCachedAt("tw", "podcast")).thenReturn(Instant.now());

        RankingsResponse response = rankingsService.getRankings(request);

        assertEquals("ok", response.status());
        assertNotNull(response.data());
        assertEquals(2, response.data().items().size());
        assertEquals("show:apple:123", response.data().items().get(0).showId());
        assertEquals("Tech Podcast", response.data().items().get(0).title());
        verify(appleChartClient).fetchPodcastChart("tw");
        verify(rankingsCache).put(eq("tw"), eq("podcast"), anyList());
    }

    @Test
    void getRankings_podcastApiFails_usesStaleCacheAsFallback() {
        RankingsRequest request = new RankingsRequest("tw", "podcast", 10);

        when(rankingsCache.get("tw", "podcast")).thenReturn(null);
        when(appleChartClient.fetchPodcastChart("tw")).thenReturn(null);

        List<RankingsItem> staleItems = List.of(
                new RankingsItem(1, "show:apple:old", "Stale Podcast", "Artist", "https://img.url", null, null, null)
        );
        when(rankingsCache.getStale("tw", "podcast")).thenReturn(staleItems);
        when(rankingsCache.getCachedAt("tw", "podcast")).thenReturn(Instant.now().minusSeconds(7200));

        RankingsResponse response = rankingsService.getRankings(request);

        assertEquals("ok", response.status());
        assertNotNull(response.data());
        assertEquals(1, response.data().items().size());
        assertEquals("Stale Podcast", response.data().items().get(0).title());
        verify(rankingsCache).getStale("tw", "podcast");
    }

    @Test
    void getRankings_podcastApiFails_noCache_returnsEmptyList() {
        RankingsRequest request = new RankingsRequest("tw", "podcast", 10);

        when(rankingsCache.get("tw", "podcast")).thenReturn(null);
        when(appleChartClient.fetchPodcastChart("tw")).thenReturn(null);
        when(rankingsCache.getStale("tw", "podcast")).thenReturn(null);
        when(rankingsCache.getCachedAt("tw", "podcast")).thenReturn(null);

        RankingsResponse response = rankingsService.getRankings(request);

        assertEquals("ok", response.status());
        assertNotNull(response.data());
        assertTrue(response.data().items().isEmpty());
    }

    // =====================
    // Episode Rankings Tests
    // =====================

    @Test
    void getRankings_episodeFromCache_returnsCache() {
        RankingsRequest request = new RankingsRequest("us", "episode", 5);

        List<RankingsItem> cachedItems = List.of(
                new RankingsItem(1, "episode:apple:ep1", "Episode 1", "Show Name", "https://img.url/1", null, null, null),
                new RankingsItem(2, "episode:apple:ep2", "Episode 2", "Show Name", "https://img.url/2", null, null, null)
        );
        when(rankingsCache.get("us", "episode")).thenReturn(cachedItems);
        when(rankingsCache.getCachedAt("us", "episode")).thenReturn(Instant.now());

        RankingsResponse response = rankingsService.getRankings(request);

        assertEquals("ok", response.status());
        assertNotNull(response.data());
        assertEquals(2, response.data().items().size());
        verify(rankingsCache).get("us", "episode");
        verify(appleChartClient, never()).fetchEpisodeChart(anyString());
    }

    @Test
    void getRankings_episodeCacheMiss_fetchesFromApi() throws Exception {
        RankingsRequest request = new RankingsRequest("us", "episode", 10);

        when(rankingsCache.get("us", "episode")).thenReturn(null);

        String appleResponse = """
                {
                    "feed": {
                        "results": [
                            {"id": "ep1", "name": "Breaking News", "collectionName": "Daily News", "artworkUrl100": "https://img.url/1"},
                            {"id": "ep2", "name": "Tech Review", "artistName": "Tech Show", "artworkUrl100": "https://img.url/2"}
                        ]
                    }
                }
                """;
        JsonNode chartData = objectMapper.readTree(appleResponse);
        when(appleChartClient.fetchEpisodeChart("us")).thenReturn(chartData);
        when(rankingsCache.getCachedAt("us", "episode")).thenReturn(Instant.now());

        RankingsResponse response = rankingsService.getRankings(request);

        assertEquals("ok", response.status());
        assertNotNull(response.data());
        assertEquals(2, response.data().items().size());
        assertEquals("episode:apple:ep1", response.data().items().get(0).showId());
        assertEquals("Breaking News", response.data().items().get(0).title());
        assertEquals("Daily News", response.data().items().get(0).publisher());
        verify(appleChartClient).fetchEpisodeChart("us");
        verify(rankingsCache).put(eq("us"), eq("episode"), anyList());
    }

    // =====================
    // Limit Tests
    // =====================

    @Test
    void getRankings_respectsLimitFromRequest() {
        RankingsRequest request = new RankingsRequest("tw", "podcast", 2);

        List<RankingsItem> cachedItems = List.of(
                new RankingsItem(1, "show:apple:1", "Podcast 1", "Artist", "url", null, null, null),
                new RankingsItem(2, "show:apple:2", "Podcast 2", "Artist", "url", null, null, null),
                new RankingsItem(3, "show:apple:3", "Podcast 3", "Artist", "url", null, null, null),
                new RankingsItem(4, "show:apple:4", "Podcast 4", "Artist", "url", null, null, null)
        );
        when(rankingsCache.get("tw", "podcast")).thenReturn(cachedItems);
        when(rankingsCache.getCachedAt("tw", "podcast")).thenReturn(Instant.now());

        RankingsResponse response = rankingsService.getRankings(request);

        assertEquals("ok", response.status());
        assertEquals(2, response.data().items().size());
        assertEquals("Podcast 1", response.data().items().get(0).title());
        assertEquals("Podcast 2", response.data().items().get(1).title());
    }

    // =====================
    // Error Handling Tests
    // =====================

    @Test
    void getRankings_exceptionThrown_returnsError() {
        RankingsRequest request = new RankingsRequest("tw", "podcast", 10);

        when(rankingsCache.get("tw", "podcast")).thenThrow(new RuntimeException("Cache failure"));

        RankingsResponse response = rankingsService.getRankings(request);

        assertEquals("error", response.status());
        assertNotNull(response.error());
        assertEquals("RANKINGS_ERROR", response.error().code());
    }

    // =====================
    // Country Tests
    // =====================

    @Test
    void getRankings_differentCountries_useSeparateCache() {
        RankingsRequest twRequest = new RankingsRequest("tw", "podcast", 10);
        RankingsRequest usRequest = new RankingsRequest("us", "podcast", 10);

        List<RankingsItem> twItems = List.of(
                new RankingsItem(1, "show:apple:tw1", "台灣播客", "發行者", "url", null, null, null)
        );
        List<RankingsItem> usItems = List.of(
                new RankingsItem(1, "show:apple:us1", "US Podcast", "Publisher", "url", null, null, null)
        );

        when(rankingsCache.get("tw", "podcast")).thenReturn(twItems);
        when(rankingsCache.get("us", "podcast")).thenReturn(usItems);
        when(rankingsCache.getCachedAt(anyString(), anyString())).thenReturn(Instant.now());

        RankingsResponse twResponse = rankingsService.getRankings(twRequest);
        RankingsResponse usResponse = rankingsService.getRankings(usRequest);

        assertEquals("台灣播客", twResponse.data().items().get(0).title());
        assertEquals("US Podcast", usResponse.data().items().get(0).title());
        verify(rankingsCache).get("tw", "podcast");
        verify(rankingsCache).get("us", "podcast");
    }
}
