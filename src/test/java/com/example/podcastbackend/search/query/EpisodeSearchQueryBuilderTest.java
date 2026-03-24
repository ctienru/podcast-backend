package com.example.podcastbackend.search.query;

import com.example.podcastbackend.request.EpisodeSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class EpisodeSearchQueryBuilderTest {

    private EpisodeSearchQueryBuilder queryBuilder;

    @BeforeEach
    void setUp() throws Exception {
        queryBuilder = new EpisodeSearchQueryBuilder(
                "podcast-spec/es/search_episodes_zh_tw/query.template.mustache",
                "podcast-spec/es/search_episodes_zh_cn/query.template.mustache",
                "podcast-spec/es/search_episodes_en/query.template.mustache",
                "zh-tw");
    }

    // =====================
    // Language-specific field tests
    // =====================

    @Test
    void buildBm25Query_zhTw_usesChineseFields() {
        EpisodeSearchRequest request = createRequest("人工智慧", "zh-tw");

        String query = queryBuilder.buildBm25Query(request);

        assertTrue(query.contains("title.chinese"), "zh-tw query should use title.chinese");
        assertTrue(query.contains("description.chinese"), "zh-tw query should use description.chinese");
    }

    @Test
    void buildBm25Query_zhCn_usesChineseFields() {
        EpisodeSearchRequest request = createRequest("人工智能", "zh-cn");

        String query = queryBuilder.buildBm25Query(request);

        assertTrue(query.contains("title.chinese"), "zh-cn query should use title.chinese");
        assertTrue(query.contains("description.chinese"), "zh-cn query should use description.chinese");
    }

    @Test
    void buildBm25Query_en_usesStandardFields() {
        EpisodeSearchRequest request = createRequest("AI podcast", "en");

        String query = queryBuilder.buildBm25Query(request);

        assertFalse(query.contains("title.chinese"), "en query should not use title.chinese");
        assertTrue(query.contains("\"title^"), "en query should use title field");
        assertTrue(query.contains("description^"), "en query should use description field");
    }

    // =====================
    // No language filter (v2: routing is index-level)
    // =====================

    @Test
    void buildBm25Query_noLanguageFilter() {
        EpisodeSearchRequest request = createRequest("podcast", "zh-tw");

        String query = queryBuilder.buildBm25Query(request);

        assertFalse(query.contains("\"terms\""), "Query should not contain terms filter");
        assertTrue(query.contains("\"filter\": []"), "Filter section should be empty");
    }

    @Test
    void buildExactQuery_noLanguageFilter() {
        EpisodeSearchRequest request = createRequest("podcast", "en");

        String query = queryBuilder.buildExactQuery(request);

        assertFalse(query.contains("\"terms\""), "Exact query should not contain terms filter");
        assertTrue(query.contains("\"filter\": []"), "Filter section should be empty");
    }

    // =====================
    // Exact match
    // =====================

    @Test
    void buildExactQuery_zhTw_usesMatchPhrase() {
        EpisodeSearchRequest request = createRequest("人工智慧", "zh-tw");

        String query = queryBuilder.buildExactQuery(request);

        assertTrue(query.contains("match_phrase"), "Exact query should use match_phrase");
        assertFalse(query.contains("multi_match"), "Exact query should not use multi_match");
        assertTrue(query.contains("title.chinese"), "zh-tw exact query should use title.chinese");
    }

    @Test
    void buildExactQuery_en_usesMatchPhrase() {
        EpisodeSearchRequest request = createRequest("AI podcast", "en");

        String query = queryBuilder.buildExactQuery(request);

        assertTrue(query.contains("match_phrase"), "Exact query should use match_phrase");
        assertFalse(query.contains("title.chinese"), "en exact query should not use title.chinese");
    }

    // =====================
    // KNN mode
    // =====================

    @Test
    void buildKnnQueryForHybrid_containsKnnSection() {
        float[] vector = new float[]{0.1f, 0.2f, 0.3f};

        String query = queryBuilder.buildKnnQueryForHybrid("zh-tw", vector, 100);

        assertTrue(query.contains("\"knn\""), "KNN query should contain knn section");
        assertTrue(query.contains("title_vector"), "KNN query should reference title_vector field");
    }

    @Test
    void buildKnnQueryForHybrid_doesNotContainQuerySection() {
        float[] vector = new float[]{0.1f, 0.2f, 0.3f};

        String query = queryBuilder.buildKnnQueryForHybrid("zh-tw", vector, 100);

        assertFalse(query.contains("multi_match"), "KNN-only query should not contain multi_match");
        assertFalse(query.contains("match_phrase"), "KNN-only query should not contain match_phrase");
    }

    @Test
    void buildBm25Query_doesNotContainKnnSection() {
        EpisodeSearchRequest request = createRequest("podcast", "zh-tw");

        String query = queryBuilder.buildBm25Query(request);

        assertFalse(query.contains("\"knn\""), "BM25 query should not contain knn section");
        assertTrue(query.contains("multi_match"), "BM25 query should use multi_match");
    }

    // =====================
    // Pagination
    // =====================

    @Test
    void buildBm25Query_correctPaginationParams() {
        EpisodeSearchRequest request = createRequest("test", "zh-tw");
        setField(request, "page", 2);
        setField(request, "size", 10);

        String query = queryBuilder.buildBm25Query(request);

        assertTrue(query.contains("\"from\": 10"), "from should be (page-1)*size = 10");
        assertTrue(query.contains("\"size\": 10"), "size should be 10");
    }

    @Test
    void buildExactQuery_correctPaginationParams() {
        EpisodeSearchRequest request = createRequest("test", "en");
        setField(request, "page", 3);
        setField(request, "size", 5);

        String query = queryBuilder.buildExactQuery(request);

        assertTrue(query.contains("\"from\": 10"), "from should be (page-1)*size = 10");
        assertTrue(query.contains("\"size\": 5"), "size should be 5");
    }

    // =====================
    // Helper Methods
    // =====================

    private EpisodeSearchRequest createRequest(String query, String lang) {
        EpisodeSearchRequest request = new EpisodeSearchRequest();
        setField(request, "q", query);
        setField(request, "page", 1);
        setField(request, "size", 20);
        setField(request, "lang", lang);
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
