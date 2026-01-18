package com.example.podcastbackend.response;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShowSearchResponseTest {

    @Test
    void ok_setsStatusToOk() {
        ShowSearchResponseData data = new ShowSearchResponseData(1, 10, 100, List.of());

        ShowSearchResponse response = ShowSearchResponse.ok(data);

        assertEquals("ok", response.status());
        assertNotNull(response.data());
        assertNull(response.warning());
        assertNull(response.error());
    }

    @Test
    void partial_setsStatusToPartialSuccess() {
        ShowSearchResponseData data = new ShowSearchResponseData(1, 10, 100, List.of());
        String warning = "Show enrichment service unavailable";

        ShowSearchResponse response = ShowSearchResponse.partial(data, warning);

        assertEquals("partial_success", response.status());
        assertNotNull(response.data());
        assertEquals(warning, response.warning());
        assertNull(response.error());
    }

    @Test
    void error_setsStatusToError() {
        ShowSearchResponse response = ShowSearchResponse.error("BAD_REQUEST", "Only page=1 is supported");

        assertEquals("error", response.status());
        assertNull(response.data());
        assertNull(response.warning());
        assertNotNull(response.error());
        assertEquals("BAD_REQUEST", response.error().code());
        assertEquals("Only page=1 is supported", response.error().message());
    }
}
