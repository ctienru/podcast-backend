package com.example.podcastbackend.response;

public record EpisodeSearchResponse(
        String status,
        EpisodeSearchResponseData data,
        String warning,
        ErrorResponse error,
        String searchRequestId
) {
    public static EpisodeSearchResponse ok(EpisodeSearchResponseData data) {
        return new EpisodeSearchResponse("ok", data, null, null, null);
    }

    public static EpisodeSearchResponse partial(EpisodeSearchResponseData data, String warning) {
        return new EpisodeSearchResponse("partial_success", data, warning, null, null);
    }

    public static EpisodeSearchResponse error(String code, String message) {
        return new EpisodeSearchResponse("error", null, null, new ErrorResponse(code, message), null);
    }
}
