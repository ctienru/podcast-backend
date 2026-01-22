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
        String language,
        Audio audio,
        ShowInfo podcast
) {

    public record Audio(
            String url,
            String type,
            Long lengthBytes
    ) {}

    public record ShowInfo(
            String showId,
            String title,
            String publisher,
            String imageUrl,
            ExternalUrl externalUrl
    ) {}

    public record ExternalUrl(
            String applePodcastUrl
    ) {}
}