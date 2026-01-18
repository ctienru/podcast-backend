package com.example.podcastbackend.controller;

import com.example.podcastbackend.exception.SearchParseException;
import com.example.podcastbackend.exception.SearchServiceException;
import com.example.podcastbackend.response.EpisodeSearchResponse;
import com.example.podcastbackend.response.EpisodeSearchResponseData;
import com.example.podcastbackend.response.ShowSearchResponse;
import com.example.podcastbackend.response.ShowSearchResponseData;
import com.example.podcastbackend.service.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    // =====================================================
    // Request Validation Tests (P1-4: size limit validation)
    // =====================================================

    @Nested
    @DisplayName("Request Parameter Validation")
    class ValidationTests {

        @Test
        @DisplayName("size exceeds limit 100 should return 400 + INVALID_PARAMETER")
        void episodeSearch_sizeExceedsLimit_returns400() throws Exception {
            mockMvc.perform(post("/api/search/episodes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"q": "test", "size": 999}
                                """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
                    .andExpect(jsonPath("$.error.message", containsString("size")))
                    .andExpect(jsonPath("$.error.message", containsString("must not exceed 100")));
        }

        @Test
        @DisplayName("size less than 1 should return 400 + INVALID_PARAMETER")
        void episodeSearch_sizeTooSmall_returns400() throws Exception {
            mockMvc.perform(post("/api/search/episodes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"q": "test", "size": 0}
                                """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
                    .andExpect(jsonPath("$.error.message", containsString("size")))
                    .andExpect(jsonPath("$.error.message", containsString("must be at least 1")));
        }

        @Test
        @DisplayName("page less than 1 should return 400 + INVALID_PARAMETER")
        void episodeSearch_pageInvalid_returns400() throws Exception {
            mockMvc.perform(post("/api/search/episodes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"q": "test", "page": 0}
                                """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
                    .andExpect(jsonPath("$.error.message", containsString("page")))
                    .andExpect(jsonPath("$.error.message", containsString("must be at least 1")));
        }

        @Test
        @DisplayName("empty query should return 400 + INVALID_PARAMETER")
        void episodeSearch_emptyQuery_returns400() throws Exception {
            mockMvc.perform(post("/api/search/episodes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"q": "", "size": 10}
                                """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
                    .andExpect(jsonPath("$.error.message", containsString("q")))
                    .andExpect(jsonPath("$.error.message", containsString("cannot be empty")));
        }

        @Test
        @DisplayName("multiple invalid parameters should return all error messages")
        void episodeSearch_multipleErrors_returnsAllErrors() throws Exception {
            mockMvc.perform(post("/api/search/episodes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"q": "", "page": 0, "size": 999}
                                """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
                    // message should contain all errors
                    .andExpect(jsonPath("$.error.message", containsString("q")))
                    .andExpect(jsonPath("$.error.message", containsString("page")))
                    .andExpect(jsonPath("$.error.message", containsString("size")))
                    // details should be an array
                    .andExpect(jsonPath("$.error.details").isArray())
                    .andExpect(jsonPath("$.error.details", hasSize(greaterThanOrEqualTo(3))));
        }

        @Test
        @DisplayName("valid parameters should pass validation")
        void episodeSearch_validParams_passesValidation() throws Exception {
            var data = new EpisodeSearchResponseData(1, 20, 0, List.of());
            when(searchService.searchEpisodes(any())).thenReturn(EpisodeSearchResponse.ok(data));

            mockMvc.perform(post("/api/search/episodes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"q": "podcast", "page": 1, "size": 50}
                                """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"));
        }

        @Test
        @DisplayName("size=100 boundary value should pass validation")
        void episodeSearch_sizeAtLimit_passesValidation() throws Exception {
            var data = new EpisodeSearchResponseData(1, 100, 0, List.of());
            when(searchService.searchEpisodes(any())).thenReturn(EpisodeSearchResponse.ok(data));

            mockMvc.perform(post("/api/search/episodes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"q": "test", "size": 100}
                                """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"));
        }

        // Show Search Validation

        @Test
        @DisplayName("Show search: size exceeds limit should return 400")
        void showSearch_sizeExceedsLimit_returns400() throws Exception {
            mockMvc.perform(post("/api/search/shows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"q": "test", "size": 200}
                                """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
        }
    }

    // =====================================================
    // ES Parse Error Tests (P1-5: ES parse error explicit error code)
    // =====================================================

    @Nested
    @DisplayName("ES Parse Error Handling")
    class EsParseErrorTests {

        @Test
        @DisplayName("ES parse failure should return 500 + ES_PARSE_ERROR")
        void episodeSearch_esParseError_returns500() throws Exception {
            when(searchService.searchEpisodes(any()))
                    .thenThrow(new SearchParseException("ES_PARSE_ERROR", "Invalid response from search service"));

            mockMvc.perform(post("/api/search/episodes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"q": "test"}
                                """))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.error.code").value("ES_PARSE_ERROR"))
                    .andExpect(jsonPath("$.error.message").value("Invalid response from search service"));
        }

        @Test
        @DisplayName("ES missing required field should return 500 + ES_MISSING_FIELD")
        void episodeSearch_esMissingField_returns500() throws Exception {
            when(searchService.searchEpisodes(any()))
                    .thenThrow(new SearchParseException("ES_MISSING_FIELD", "Missing source in search hit"));

            mockMvc.perform(post("/api/search/episodes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"q": "test"}
                                """))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.error.code").value("ES_MISSING_FIELD"))
                    .andExpect(jsonPath("$.error.message").value("Missing source in search hit"));
        }
    }

    // =====================================================
    // Service Exception Tests (ES connection failure)
    // =====================================================

    @Nested
    @DisplayName("Search Service Exception Handling")
    class ServiceExceptionTests {

        @Test
        @DisplayName("ES service unavailable should return 503 + SEARCH_SERVICE_ERROR")
        void episodeSearch_serviceUnavailable_returns503() throws Exception {
            when(searchService.searchEpisodes(any()))
                    .thenThrow(new SearchServiceException("ES connection failed", new RuntimeException()));

            mockMvc.perform(post("/api/search/episodes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"q": "test"}
                                """))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.error.code").value("SEARCH_SERVICE_ERROR"))
                    .andExpect(jsonPath("$.error.message").value("Search service temporarily unavailable"));
        }

        @Test
        @DisplayName("Show search: ES service unavailable should return 503")
        void showSearch_serviceUnavailable_returns503() throws Exception {
            when(searchService.searchShows(any()))
                    .thenThrow(new SearchServiceException("ES timeout", new RuntimeException()));

            mockMvc.perform(post("/api/search/shows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"q": "test"}
                                """))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value("SEARCH_SERVICE_ERROR"));
        }
    }

    // =====================================================
    // Partial Success Tests (partial data parse failure)
    // =====================================================

    @Nested
    @DisplayName("Partial Success Response")
    class PartialSuccessTests {

        @Test
        @DisplayName("partial data parse failure should return partial_success + warning")
        void episodeSearch_partialSuccess_returnsWarning() throws Exception {
            var data = new EpisodeSearchResponseData(1, 10, 8, List.of());
            when(searchService.searchEpisodes(any()))
                    .thenReturn(EpisodeSearchResponse.partial(data, "2 item(s) skipped due to parse errors"));

            mockMvc.perform(post("/api/search/episodes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"q": "test"}
                                """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("partial_success"))
                    .andExpect(jsonPath("$.warning").value("2 item(s) skipped due to parse errors"))
                    .andExpect(jsonPath("$.data").exists())
                    .andExpect(jsonPath("$.error").doesNotExist());
        }
    }

    // =====================================================
    // Success Response Tests
    // =====================================================

    @Nested
    @DisplayName("Successful Response")
    class SuccessTests {

        @Test
        @DisplayName("Episode search success should return ok status")
        void episodeSearch_success_returnsOk() throws Exception {
            var data = new EpisodeSearchResponseData(1, 20, 100, List.of());
            when(searchService.searchEpisodes(any())).thenReturn(EpisodeSearchResponse.ok(data));

            mockMvc.perform(post("/api/search/episodes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"q": "test"}
                                """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"))
                    .andExpect(jsonPath("$.data.page").value(1))
                    .andExpect(jsonPath("$.data.size").value(20))
                    .andExpect(jsonPath("$.data.total").value(100))
                    .andExpect(jsonPath("$.warning").doesNotExist())
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("Show search success should return ok status")
        void showSearch_success_returnsOk() throws Exception {
            var data = new ShowSearchResponseData(1, 10, 50, List.of());
            when(searchService.searchShows(any())).thenReturn(ShowSearchResponse.ok(data));

            mockMvc.perform(post("/api/search/shows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"q": "test"}
                                """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"));
        }
    }
}
