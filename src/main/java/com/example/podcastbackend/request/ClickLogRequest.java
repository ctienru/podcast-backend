package com.example.podcastbackend.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/log/click}.
 * All required fields use Bean Validation; missing required field → 400.
 */
public class ClickLogRequest {

    @NotBlank
    private String requestId;

    @NotBlank
    private String timestamp;

    @NotBlank
    private String query;

    @NotBlank
    private String selectedLang;

    @NotBlank
    private String clickedEpisodeId;

    @Min(1)
    private int clickedRank;

    @NotBlank
    private String clickedLanguage;

    private Integer timeToClickSec;  // optional

    public String getRequestId() { return requestId; }
    public String getTimestamp() { return timestamp; }
    public String getQuery() { return query; }
    public String getSelectedLang() { return selectedLang; }
    public String getClickedEpisodeId() { return clickedEpisodeId; }
    public int getClickedRank() { return clickedRank; }
    public String getClickedLanguage() { return clickedLanguage; }
    public Integer getTimeToClickSec() { return timeToClickSec; }
}
