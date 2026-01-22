package com.example.podcastbackend.response;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EpisodeSearchResponseTest {

    @Test
    void ok_setsStatusToOk() {
        EpisodeSearchResponseData data = new EpisodeSearchResponseData(1, 10, 100, List.of());

        EpisodeSearchResponse response = EpisodeSearchResponse.ok(data);

        assertEquals("ok", response.status());
        assertNotNull(response.data());
        assertNull(response.warning());
        assertNull(response.error());
    }

    @Test
    void partial_setsStatusToPartialSuccess() {
        EpisodeSearchResponseData data = new EpisodeSearchResponseData(1, 10, 100, List.of());
        String warning = "Highlight service unavailable";

        EpisodeSearchResponse response = EpisodeSearchResponse.partial(data, warning);

        assertEquals("partial_success", response.status());
        assertNotNull(response.data());
        assertEquals(warning, response.warning());
        assertNull(response.error());
    }

    @Test
    void error_setsStatusToError() {
        EpisodeSearchResponse response = EpisodeSearchResponse.error("BAD_REQUEST", "Invalid query");

        assertEquals("error", response.status());
        assertNull(response.data());
        assertNull(response.warning());
        assertNotNull(response.error());
        assertEquals("BAD_REQUEST", response.error().code());
        assertEquals("Invalid query", response.error().message());
    }

    @Test
    void error_withSearchServiceError() {
        EpisodeSearchResponse response = EpisodeSearchResponse.error(
                "SEARCH_SERVICE_ERROR",
                "Search service temporarily unavailable"
        );

        assertEquals("error", response.status());
        assertEquals("SEARCH_SERVICE_ERROR", response.error().code());
    }
}
