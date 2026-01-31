package com.example.podcastbackend.response;

public record ShowSuggestItem(
        String showId,
        String title,
        String publisher,
        String imageUrl
) {}
