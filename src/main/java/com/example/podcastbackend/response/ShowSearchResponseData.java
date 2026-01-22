package com.example.podcastbackend.response;

import java.util.List;

public record ShowSearchResponseData(
        int page,
        int size,
        int total,
        List<ShowSearchItem> items
) {}