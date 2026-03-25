package com.example.podcastbackend.controller;

import com.example.podcastbackend.log.ClickLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClickLogService clickLogService;

    private static final String VALID_BODY = """
            {
              "requestId": "550e8400-e29b-41d4-a716-446655440000",
              "timestamp": "2026-03-22T10:00:00Z",
              "query": "人工智慧",
              "selectedLang": "zh-tw",
              "clickedEpisodeId": "ep:apple:123:ep1",
              "clickedRank": 1,
              "clickedLanguage": "zh-tw"
            }
            """;

    @Test
    @DisplayName("valid click request returns 200 and delegates to ClickLogService")
    void clickLog_validRequest_returns200() throws Exception {
        mockMvc.perform(post("/api/log/click")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());

        verify(clickLogService).logClick(any());
    }

    @Test
    @DisplayName("optional timeToClickSec omitted → still 200")
    void clickLog_withoutTimeToClick_returns200() throws Exception {
        mockMvc.perform(post("/api/log/click")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("missing requestId returns 400")
    void clickLog_missingRequestId_returns400() throws Exception {
        mockMvc.perform(post("/api/log/click")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "timestamp": "2026-03-22T10:00:00Z",
                                  "query": "podcast",
                                  "selectedLang": "zh-tw",
                                  "clickedEpisodeId": "ep:apple:123:ep1",
                                  "clickedRank": 1,
                                  "clickedLanguage": "zh-tw"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("missing clickedEpisodeId returns 400")
    void clickLog_missingClickedEpisodeId_returns400() throws Exception {
        mockMvc.perform(post("/api/log/click")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "550e8400-e29b-41d4-a716-446655440000",
                                  "timestamp": "2026-03-22T10:00:00Z",
                                  "query": "podcast",
                                  "selectedLang": "zh-tw",
                                  "clickedRank": 1,
                                  "clickedLanguage": "zh-tw"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("clickedRank < 1 returns 400")
    void clickLog_rankZero_returns400() throws Exception {
        mockMvc.perform(post("/api/log/click")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "550e8400-e29b-41d4-a716-446655440000",
                                  "timestamp": "2026-03-22T10:00:00Z",
                                  "query": "podcast",
                                  "selectedLang": "zh-tw",
                                  "clickedEpisodeId": "ep:apple:123:ep1",
                                  "clickedRank": 0,
                                  "clickedLanguage": "zh-tw"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
