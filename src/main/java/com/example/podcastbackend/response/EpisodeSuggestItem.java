package com.example.podcastbackend.response;

public record EpisodeSuggestItem(
        String episodeId,
        String title,
        String showTitle,
        String imageUrl
) {}
