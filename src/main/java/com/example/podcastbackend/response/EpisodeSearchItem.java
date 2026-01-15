package com.example.podcastbackend.response;

import java.util.List;
import java.util.Map;

public record EpisodeSearchItem(
        String episodeId,
        String title,
        String description,
        Map<String, List<String>> highlights,
        String publishedAt,
        Integer durationSec,
        String imageUrl,
        PodcastInfo podcast
) {

    public record PodcastInfo(
            String podcastId,
            String title,
            String publisher,
            String imageUrl
    ) {}
}