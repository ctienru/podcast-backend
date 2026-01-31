package com.example.podcastbackend.search.query;

import com.example.podcastbackend.request.EpisodeSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EpisodeSearchQueryBuilderTest {

    private EpisodeSearchQueryBuilder queryBuilder;

    @BeforeEach
    void setUp() throws Exception {
        queryBuilder = new EpisodeSearchQueryBuilder(
                "podcast-spec/es/search_episodes/bm25.query.template.mustache",
                "podcast-spec/es/search_episodes/knn.query.template.mustache",
                "podcast-spec/es/search_episodes/exact.query.template.mustache");
    }

    // =====================
    // Time Decay Tests
    // =====================

    @Test
    void buildBm25Query_withTimeDecayEnabled_includesFunctionScore() {
        EpisodeSearchRequest request = createRequest("AI podcast");

        String query = queryBuilder.buildBm25Query(request);

        assertTrue(query.contains("function_score"), "Query should contain function_score");
        assertTrue(query.contains("gauss"), "Query should contain gauss decay function");
        assertTrue(query.contains("published_at"), "Query should decay on published_at field");
        assertTrue(query.contains("90d"), "Default scale should be 90d");
        assertTrue(query.contains("0.5"), "Default decay rate should be 0.5");
    }

    @Test
    void buildBm25Query_withTimeDecayDisabled_excludesFunctionScore() {
        EpisodeSearchRequest request = createRequest("AI podcast");
        setField(request, "timeDecay", false);

        String query = queryBuilder.buildBm25Query(request);

        assertFalse(query.contains("function_score"), "Query should not contain function_score");
        assertFalse(query.contains("gauss"), "Query should not contain gauss decay function");
        assertTrue(query.contains("bool"), "Query should still contain bool query");
    }

    @Test
    void buildBm25Query_withCustomTimeDecayScale_usesCustomScale() {
        EpisodeSearchRequest request = createRequest("AI podcast");
        setField(request, "timeDecayScale", "60d");

        String query = queryBuilder.buildBm25Query(request);

        assertTrue(query.contains("60d"), "Query should use custom scale 60d");
        assertFalse(query.contains("\"90d\""), "Query should not use default scale");
    }

    @Test
    void buildBm25Query_withCustomTimeDecayRate_usesCustomRate() {
        EpisodeSearchRequest request = createRequest("AI podcast");
        setField(request, "timeDecayRate", 0.3);

        String query = queryBuilder.buildBm25Query(request);

        assertTrue(query.contains("0.3"), "Query should use custom decay rate 0.3");
    }

    @Test
    void buildBm25QueryForHybrid_withTimeDecayEnabled_includesFunctionScore() {
        EpisodeSearchRequest request = createRequest("AI podcast");

        String query = queryBuilder.buildBm25QueryForHybrid(request, 100);

        assertTrue(query.contains("function_score"), "Hybrid query should contain function_score");
        assertTrue(query.contains("gauss"), "Hybrid query should contain gauss decay function");
    }

    @Test
    void buildBm25QueryForHybrid_withTimeDecayDisabled_excludesFunctionScore() {
        EpisodeSearchRequest request = createRequest("AI podcast");
        setField(request, "timeDecay", false);

        String query = queryBuilder.buildBm25QueryForHybrid(request, 100);

        assertFalse(query.contains("function_score"), "Hybrid query should not contain function_score");
    }

    // =====================
    // Chinese Field Tests
    // =====================

    @Test
    void buildBm25Query_includesChineseFields() {
        EpisodeSearchRequest request = createRequest("人工智慧");

        String query = queryBuilder.buildBm25Query(request);

        assertTrue(query.contains("title.chinese"), "Query should include title.chinese field");
        assertTrue(query.contains("description.chinese"), "Query should include description.chinese field");
    }

    // =====================
    // Language Filter Tests
    // =====================

    @Test
    void buildBm25Query_withLanguageFilter_includesLanguageTerms() {
        EpisodeSearchRequest request = createRequest("podcast");
        setField(request, "language", List.of("en", "zh"));

        String query = queryBuilder.buildBm25Query(request);

        assertTrue(query.contains("language"), "Query should contain language filter");
        assertTrue(query.contains("en") && query.contains("zh"), "Query should filter by specified languages");
    }

    @Test
    void buildBm25Query_withoutLanguageFilter_excludesLanguageTerms() {
        EpisodeSearchRequest request = createRequest("podcast");

        String query = queryBuilder.buildBm25Query(request);

        // The query should not have the terms filter for language
        // Note: "language" appears in _source.includes which is expected
        assertFalse(query.contains("\"terms\""), "Query should not contain terms filter when language not specified");
        assertFalse(query.contains("filter"), "Query should not contain filter section when language not specified");
    }

    // =====================
    // Exact Match Tests
    // =====================

    @Test
    void buildExactQuery_usesMatchPhrase() {
        EpisodeSearchRequest request = createRequest("AI podcast");

        String query = queryBuilder.buildExactQuery(request);

        assertTrue(query.contains("match_phrase"), "Exact query should use match_phrase");
        assertFalse(query.contains("multi_match"), "Exact query should not use multi_match");
        assertFalse(query.contains("function_score"), "Exact query should not use function_score (no time decay)");
    }

    @Test
    void buildExactQuery_includesChineseFields() {
        EpisodeSearchRequest request = createRequest("人工智慧");

        String query = queryBuilder.buildExactQuery(request);

        assertTrue(query.contains("title.chinese"), "Exact query should include title.chinese field");
        assertTrue(query.contains("description.chinese"), "Exact query should include description.chinese field");
    }

    @Test
    void buildExactQuery_withLanguageFilter_includesFilter() {
        EpisodeSearchRequest request = createRequest("podcast");
        setField(request, "language", List.of("en"));

        String query = queryBuilder.buildExactQuery(request);

        assertTrue(query.contains("filter"), "Exact query should include language filter");
        assertTrue(query.contains("\"en\""), "Exact query should filter by specified language");
    }

    @Test
    void buildExactQuery_includesPaginationParams() {
        EpisodeSearchRequest request = createRequest("test");
        setField(request, "page", 2);
        setField(request, "size", 10);

        String query = queryBuilder.buildExactQuery(request);

        assertTrue(query.contains("\"from\": 10"), "Exact query should have correct from value");
        assertTrue(query.contains("\"size\": 10"), "Exact query should have correct size value");
    }

    // =====================
    // Helper Methods
    // =====================

    private EpisodeSearchRequest createRequest(String query) {
        EpisodeSearchRequest request = new EpisodeSearchRequest();
        setField(request, "q", query);
        setField(request, "page", 1);
        setField(request, "size", 20);
        return request;
    }

    private void setField(Object obj, String fieldName, Object value) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
