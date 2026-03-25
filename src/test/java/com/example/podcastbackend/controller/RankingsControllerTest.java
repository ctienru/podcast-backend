package com.example.podcastbackend.controller;

import com.example.podcastbackend.response.RankingsResponse;
import com.example.podcastbackend.response.RankingsResponseData;
import com.example.podcastbackend.service.RankingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RankingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RankingsService rankingsService;

    @Test
    void getRankings_withRegionCn_passesRegionToService() throws Exception {
        when(rankingsService.getRankings(any())).thenReturn(okResponse("cn", "episode"));

        mockMvc.perform(get("/api/rankings")
                .param("region", "cn")
                .param("type", "episode")
                .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(rankingsService).getRankings(argThat(request -> "cn".equals(request.getRegion())
                && "episode".equals(request.getType())
                && request.getLimit() == 5));
    }

    @Test
    void getRankings_withLegacyCountryParam_usesDefaultRegionTw() throws Exception {
        when(rankingsService.getRankings(any())).thenReturn(okResponse("tw", "podcast"));

        mockMvc.perform(get("/api/rankings")
                .param("country", "cn")
                .param("type", "podcast")
                .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(rankingsService).getRankings(argThat(request -> "tw".equals(request.getRegion())
                && "podcast".equals(request.getType())
                && request.getLimit() == 10));
    }

    private RankingsResponse okResponse(String region, String type) {
        return RankingsResponse.ok(new RankingsResponseData(region, type, List.of(), Instant.now()));
    }
}
