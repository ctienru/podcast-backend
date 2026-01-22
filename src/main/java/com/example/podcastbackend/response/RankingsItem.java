package com.example.podcastbackend.response;

import java.util.Map;

public record RankingsItem(
        int rank,
        String showId,
        String title,
        String publisher,
        String imageUrl,
        String language,
        Integer episodeCount,
        Map<String, String> externalUrls
) {}
