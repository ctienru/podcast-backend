package com.example.podcastbackend.response;

public record SuggestResponse(
        String status,
        SuggestResponseData data,
        ErrorResponse error
) {
    public static SuggestResponse ok(SuggestResponseData data) {
        return new SuggestResponse("ok", data, null);
    }

    public static SuggestResponse error(String code, String message) {
        return new SuggestResponse("error", null, new ErrorResponse(code, message));
    }
}
