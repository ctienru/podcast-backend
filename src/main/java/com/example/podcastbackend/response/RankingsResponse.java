package com.example.podcastbackend.response;

public record RankingsResponse(
        String status,
        RankingsResponseData data,
        String warning,
        ErrorResponse error
) {
    public static RankingsResponse ok(RankingsResponseData data) {
        return new RankingsResponse("ok", data, null, null);
    }

    public static RankingsResponse partial(RankingsResponseData data, String warning) {
        return new RankingsResponse("partial_success", data, warning, null);
    }

    public static RankingsResponse error(String code, String message) {
        return new RankingsResponse("error", null, null, new ErrorResponse(code, message));
    }
}
