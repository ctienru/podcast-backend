package com.example.podcastbackend.response;

import java.util.Map;

public record ShowBatchResponse(
        String status,
        Map<String, ShowDetail> data,
        ErrorInfo error
) {
    public static ShowBatchResponse ok(Map<String, ShowDetail> data) {
        return new ShowBatchResponse("ok", data, null);
    }

    public static ShowBatchResponse error(String code, String message) {
        return new ShowBatchResponse("error", null, new ErrorInfo(code, message));
    }

    public record ErrorInfo(String code, String message) {}
}
