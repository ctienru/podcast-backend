package com.example.podcastbackend.response;

public record ShowSearchResponse(
        String status,
        ShowSearchResponseData data,
        String warning,
        ErrorResponse error
) {
    public static ShowSearchResponse ok(ShowSearchResponseData data) {
        return new ShowSearchResponse("ok", data, null, null);
    }

    public static ShowSearchResponse partial(ShowSearchResponseData data, String warning) {
        return new ShowSearchResponse("partial_success", data, warning, null);
    }

    public static ShowSearchResponse error(String code, String message) {
        return new ShowSearchResponse("error", null, null, new ErrorResponse(code, message));
    }
}
