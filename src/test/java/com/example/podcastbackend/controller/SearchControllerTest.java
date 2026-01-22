package com.example.podcastbackend.controller;

import com.example.podcastbackend.exception.SearchServiceException;
import com.example.podcastbackend.request.ShowSearchRequest;
import com.example.podcastbackend.request.EpisodeSearchRequest;
import com.example.podcastbackend.response.*;
import com.example.podcastbackend.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock
    private SearchService searchService;

    private SearchController controller;

    @BeforeEach
    void setUp() {
        controller = new SearchController(searchService);
    }

    // =====================
    // Shows API Tests
    // =====================

    @Test
    void searchShows_returnsShowIdInResponse() {
        ShowSearchItem item = new ShowSearchItem(
                "show_123",  // showId (not podcastId)
                "Test Podcast",
                "Description",
                "en",
                "Publisher",
                "https://example.com/image.jpg",
                100,
                Map.of(),
                Map.of(),
                Map.of()
        );
        ShowSearchResponseData data = new ShowSearchResponseData(1, 10, 1, List.of(item));
        when(searchService.searchShows(any())).thenReturn(ShowSearchResponse.ok(data));

        ShowSearchRequest request = mock(ShowSearchRequest.class);

        ShowSearchResponse response = controller.searchShows(request);

        assertEquals("ok", response.status());
        assertNotNull(response.data());

        @SuppressWarnings("unchecked")
        ShowSearchResponseData responseData = (ShowSearchResponseData) response.data();
        assertEquals(1, responseData.items().size());
        assertEquals("show_123", ((ShowSearchItem) responseData.items().get(0)).showId());
    }

    @Test
    void searchShows_returnsErrorForInvalidPage() {
        when(searchService.searchShows(any()))
                .thenReturn(ShowSearchResponse.error("BAD_REQUEST", "Only page=1 is supported currently"));

        ShowSearchRequest request = mock(ShowSearchRequest.class);

        ShowSearchResponse response = controller.searchShows(request);

        assertEquals("error", response.status());
        assertNull(response.data());
        assertNotNull(response.error());
        assertEquals("BAD_REQUEST", response.error().code());
    }

    // =====================
    // Episodes API Tests
    // =====================

    @Test
    void searchEpisodes_returnsShowIdInPodcastInfo() {
        EpisodeSearchItem.ShowInfo showInfo = new EpisodeSearchItem.ShowInfo(
                "show_456",  // showId (not podcastId)
                "Test Podcast",
                "Publisher",
                "https://example.com/image.jpg",
                null
        );
        EpisodeSearchItem item = new EpisodeSearchItem(
                "episode_789",
                "Test Episode",
                "Description",
                Map.of(),
                "2024-01-01T00:00:00Z",
                3600,
                null,
                "en",
                null,
                showInfo
        );
        EpisodeSearchResponseData data = new EpisodeSearchResponseData(1, 10, 1, List.of(item));
        when(searchService.searchEpisodes(any())).thenReturn(EpisodeSearchResponse.ok(data));

        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);

        EpisodeSearchResponse response = controller.searchEpisodes(request);

        assertEquals("ok", response.status());
        assertNotNull(response.data());
        assertEquals(1, response.data().items().size());

        EpisodeSearchItem resultItem = (EpisodeSearchItem) response.data().items().get(0);
        assertEquals("show_456", resultItem.podcast().showId());
    }

    @Test
    void searchEpisodes_returnsPartialSuccessWithWarning() {
        EpisodeSearchResponseData data = new EpisodeSearchResponseData(1, 10, 0, List.of());
        when(searchService.searchEpisodes(any()))
                .thenReturn(EpisodeSearchResponse.partial(data, "Highlight service unavailable"));

        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);

        EpisodeSearchResponse response = controller.searchEpisodes(request);

        assertEquals("partial_success", response.status());
        assertEquals("Highlight service unavailable", response.warning());
        assertNull(response.error());
    }

    // =====================
    // Error Handling Tests
    // =====================

    @Test
    void searchShows_throwsSearchServiceException() {
        when(searchService.searchShows(any()))
                .thenThrow(new SearchServiceException("ES connection failed", new RuntimeException()));

        ShowSearchRequest request = mock(ShowSearchRequest.class);

        assertThrows(SearchServiceException.class, () -> controller.searchShows(request));
    }

    @Test
    void searchEpisodes_throwsSearchServiceException() {
        when(searchService.searchEpisodes(any()))
                .thenThrow(new SearchServiceException("ES timeout", new RuntimeException()));

        EpisodeSearchRequest request = mock(EpisodeSearchRequest.class);

        assertThrows(SearchServiceException.class, () -> controller.searchEpisodes(request));
    }
}
