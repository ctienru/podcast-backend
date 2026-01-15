package com.example.podcastbackend.response;

public class ShowSearchResponse {

    private String status;
    private Object data;
    private String warning;
    private ErrorResponse error;

    public static ShowSearchResponse ok(Object data) {
        ShowSearchResponse r = new ShowSearchResponse();
        r.status = "ok";
        r.data = data;
        return r;
    }

    public static ShowSearchResponse partial(Object data, String warning) {
        ShowSearchResponse r = new ShowSearchResponse();
        r.status = "partial_success";
        r.data = data;
        r.warning = warning;
        return r;
    }

    public static ShowSearchResponse error(String code, String message) {
        ShowSearchResponse r = new ShowSearchResponse();
        r.status = "error";
        r.error = new ErrorResponse(code, message);
        return r;
    }
}