package com.example.podcastbackend.response;

import java.util.List;

public record ShowDetail(
        String showId,
        String description,
        List<String> categories,
        String language,
        Integer episodeCount
) {}
