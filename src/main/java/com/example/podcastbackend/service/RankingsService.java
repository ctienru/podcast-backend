package com.example.podcastbackend.service;

import com.example.podcastbackend.cache.RankingsCache;
import com.example.podcastbackend.client.AppleChartClient;
import com.example.podcastbackend.request.RankingsRequest;
import com.example.podcastbackend.response.RankingsItem;
import com.example.podcastbackend.response.RankingsResponse;
import com.example.podcastbackend.response.RankingsResponseData;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class RankingsService {

    private static final Logger log = LoggerFactory.getLogger(RankingsService.class);

    private final AppleChartClient appleChartClient;
    private final RankingsCache rankingsCache;

    public RankingsService(
            AppleChartClient appleChartClient,
            RankingsCache rankingsCache
    ) {
        this.appleChartClient = appleChartClient;
        this.rankingsCache = rankingsCache;
    }

    public RankingsResponse getRankings(RankingsRequest request) {
        String type = request.getType();
        String country = request.getCountry();

        try {
            List<RankingsItem> items;

            if ("episode".equals(type)) {
                items = getEpisodeRankings(country, request.getLimit());
            } else {
                items = getPodcastRankings(country, request.getLimit());
            }

            // Get the cached timestamp (will be set after fetching or from existing cache)
            Instant updatedAt = rankingsCache.getCachedAt(country, type);
            if (updatedAt == null) {
                updatedAt = Instant.now();
            }

            var data = new RankingsResponseData(country, type, items, updatedAt);
            return RankingsResponse.ok(data);

        } catch (Exception e) {
            log.error("Failed to get rankings", e);
            return RankingsResponse.error("RANKINGS_ERROR", e.getMessage());
        }
    }

    /**
     * Get podcast rankings from Apple API with caching
     */
    private List<RankingsItem> getPodcastRankings(String country, int limit) {
        // Check cache first
        List<RankingsItem> cached = rankingsCache.get(country, "podcast");
        if (cached != null) {
            return cached.stream().limit(limit).toList();
        }

        // Fetch from Apple API
        JsonNode chartData = appleChartClient.fetchPodcastChart(country);

        if (chartData == null) {
            // API failed - try to return stale cache
            List<RankingsItem> stale = rankingsCache.getStale(country, "podcast");
            if (stale != null) {
                log.warn("Using stale cache for podcast rankings: {}", country);
                return stale.stream().limit(limit).toList();
            }
            return List.of();
        }

        // Parse Apple API response
        List<RankingsItem> items = parseAppleChartResponse(chartData, "podcast");

        // Cache the results
        rankingsCache.put(country, "podcast", items);

        return items.stream().limit(limit).toList();
    }

    /**
     * Get episode rankings from Apple API with caching
     */
    private List<RankingsItem> getEpisodeRankings(String country, int limit) {
        // Check cache first
        List<RankingsItem> cached = rankingsCache.get(country, "episode");
        if (cached != null) {
            return cached.stream().limit(limit).toList();
        }

        // Fetch from Apple API
        JsonNode chartData = appleChartClient.fetchEpisodeChart(country);

        if (chartData == null) {
            // API failed - try to return stale cache
            List<RankingsItem> stale = rankingsCache.getStale(country, "episode");
            if (stale != null) {
                log.warn("Using stale cache for episode rankings: {}", country);
                return stale.stream().limit(limit).toList();
            }
            return List.of();
        }

        // Parse Apple API response
        List<RankingsItem> items = parseAppleChartResponse(chartData, "episode");

        // Cache the results
        rankingsCache.put(country, "episode", items);

        return items.stream().limit(limit).toList();
    }

    /**
     * Parse Apple Chart API response into RankingsItem list
     */
    private List<RankingsItem> parseAppleChartResponse(JsonNode chartData, String type) {
        List<RankingsItem> items = new ArrayList<>();

        JsonNode feed = chartData.path("feed");
        JsonNode results = feed.path("results");

        if (!results.isArray()) {
            log.warn("No results array in Apple chart response");
            return items;
        }

        int rank = 1;
        for (JsonNode entry : results) {
            String id = text(entry, "id");
            String name = text(entry, "name");
            String artistName = text(entry, "artistName");
            String artworkUrl = text(entry, "artworkUrl100");

            // For episodes, get the collection (podcast) info
            String collectionName = text(entry, "collectionName");

            String idPrefix = "podcast".equals(type) ? "show:apple:" : "episode:apple:";

            items.add(new RankingsItem(
                    rank++,
                    id != null ? idPrefix + id : null,
                    name,
                    artistName != null ? artistName : collectionName,
                    artworkUrl,
                    null, // language not in chart response
                    null, // episodeCount not applicable
                    null  // externalUrls
            ));
        }

        return items;
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }
}
