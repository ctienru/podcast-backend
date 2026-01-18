package com.example.podcastbackend.response;

import java.time.Instant;
import java.util.List;

public record RankingsResponseData(
        String country,
        String type,
        List<RankingsItem> items,
        Instant updatedAt
) {}
