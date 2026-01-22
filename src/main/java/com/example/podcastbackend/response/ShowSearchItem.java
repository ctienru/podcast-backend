package com.example.podcastbackend.response;

import java.util.List;
import java.util.Map;

public record ShowSearchItem(
        String showId,
        String title,
        String description,
        String language,
        String publisher,
        String imageUrl,
        Integer episodeCount,
        Map<String, List<String>> highlights,
        Map<String, String> externalIds,
        Map<String, String> externalUrls
) {}
