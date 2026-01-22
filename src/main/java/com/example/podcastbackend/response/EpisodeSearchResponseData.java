package com.example.podcastbackend.response;

import java.util.List;

public record EpisodeSearchResponseData(
        int page,
        int size,
        int total,
        List<?> items
) {}