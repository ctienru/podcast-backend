package com.example.podcastbackend.search.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SuggestQueryBuilderTest {

    private SuggestQueryBuilder queryBuilder;

    @BeforeEach
    void setUp() throws Exception {
        queryBuilder = new SuggestQueryBuilder();
    }

    // =====================
    // Show Suggest Tests
    // =====================

    @Test
    void buildShowSuggestQuery_containsQueryText() {
        String query = queryBuilder.buildShowSuggestQuery("podcast", 5);

        assertTrue(query.contains("podcast"), "Query should contain the search text");
    }

    @Test
    void buildShowSuggestQuery_containsLimit() {
        String query = queryBuilder.buildShowSuggestQuery("test", 10);

        assertTrue(query.contains("\"size\": 10"), "Query should contain the correct limit");
    }

    @Test
    void buildShowSuggestQuery_usesAutocompleteField() {
        String query = queryBuilder.buildShowSuggestQuery("tech", 5);

        assertTrue(query.contains("title.autocomplete"), "Query should search title.autocomplete field");
    }

    @Test
    void buildShowSuggestQuery_usesChineseField() {
        String query = queryBuilder.buildShowSuggestQuery("科技", 5);

        assertTrue(query.contains("title.chinese"), "Query should search title.chinese field");
    }

    @Test
    void buildShowSuggestQuery_usesPrefixWithBoost() {
        String query = queryBuilder.buildShowSuggestQuery("pod", 5);

        assertTrue(query.contains("prefix"), "Query should use prefix matching");
        assertTrue(query.contains("title.keyword"), "Query should use title.keyword for prefix");
        assertTrue(query.contains("\"boost\": 3"), "Prefix match should have boost 3");
    }

    @Test
    void buildShowSuggestQuery_returnsRequiredFields() {
        String query = queryBuilder.buildShowSuggestQuery("test", 5);

        assertTrue(query.contains("show_id"), "Query should return show_id");
        assertTrue(query.contains("title"), "Query should return title");
        assertTrue(query.contains("publisher"), "Query should return publisher");
        assertTrue(query.contains("image_url"), "Query should return image_url");
    }

    @Test
    void buildShowSuggestQuery_sortsByScoreAndEpisodeCount() {
        String query = queryBuilder.buildShowSuggestQuery("test", 5);

        assertTrue(query.contains("_score"), "Query should sort by score");
        assertTrue(query.contains("episode_count"), "Query should also sort by episode_count");
    }

    // =====================
    // Episode Suggest Tests
    // =====================

    @Test
    void buildEpisodeSuggestQuery_containsQueryText() {
        String query = queryBuilder.buildEpisodeSuggestQuery("podcast", 5);

        assertTrue(query.contains("podcast"), "Query should contain the search text");
    }

    @Test
    void buildEpisodeSuggestQuery_containsLimit() {
        String query = queryBuilder.buildEpisodeSuggestQuery("test", 8);

        assertTrue(query.contains("\"size\": 8"), "Query should contain the correct limit");
    }

    @Test
    void buildEpisodeSuggestQuery_usesAutocompleteField() {
        String query = queryBuilder.buildEpisodeSuggestQuery("tech", 5);

        assertTrue(query.contains("title.autocomplete"), "Query should search title.autocomplete field");
    }

    @Test
    void buildEpisodeSuggestQuery_usesCollapse() {
        String query = queryBuilder.buildEpisodeSuggestQuery("test", 5);

        assertTrue(query.contains("collapse"), "Query should use collapse for deduplication");
        assertTrue(query.contains("show.show_id"), "Query should collapse by show.show_id");
    }

    @Test
    void buildEpisodeSuggestQuery_returnsRequiredFields() {
        String query = queryBuilder.buildEpisodeSuggestQuery("test", 5);

        assertTrue(query.contains("episode_id"), "Query should return episode_id");
        assertTrue(query.contains("title"), "Query should return title");
        assertTrue(query.contains("show.title"), "Query should return show.title");
        assertTrue(query.contains("image_url"), "Query should return image_url");
    }

    @Test
    void buildEpisodeSuggestQuery_sortsByScoreAndDate() {
        String query = queryBuilder.buildEpisodeSuggestQuery("test", 5);

        assertTrue(query.contains("_score"), "Query should sort by score");
        assertTrue(query.contains("published_at"), "Query should also sort by published_at");
    }
}
