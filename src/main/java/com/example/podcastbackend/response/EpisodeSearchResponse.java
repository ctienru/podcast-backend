package com.example.podcastbackend.response;

public record EpisodeSearchResponse(
        String status,
        EpisodeSearchResponseData data
) {
    public static EpisodeSearchResponse ok(EpisodeSearchResponseData data) {
        return new EpisodeSearchResponse("ok", data);
    }
}