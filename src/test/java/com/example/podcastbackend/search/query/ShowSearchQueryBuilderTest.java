package com.example.podcastbackend.search.query;

import com.example.podcastbackend.request.ShowSearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShowSearchQueryBuilderTest {

    private ShowSearchQueryBuilder queryBuilder;

    @BeforeEach
    void setUp() throws Exception {
        queryBuilder = new ShowSearchQueryBuilder(
                "podcast-spec/es/search_shows/query.template.mustache",
                "podcast-spec/es/search_shows/bm25.query.template.mustache",
                "podcast-spec/es/search_shows/knn.query.template.mustache",
                new ObjectMapper());
    }

    @Test
    void buildKnnQueryForHybrid_serializesQueryVectorAsJsonArray() throws Exception {
        ShowSearchRequest request = new ShowSearchRequest();
        setField(request, "language", List.of("en"));

        String query = queryBuilder.buildKnnQueryForHybrid(request, new float[] {0.1f, 0.2f, 0.3f}, 100);

        JsonNode root = new ObjectMapper().readTree(query);
        JsonNode queryVector = root.path("knn").path("query_vector");
        assertTrue(queryVector.isArray(), "query_vector should be rendered as a JSON array");
        assertFalse(query.contains("[F@"), "query_vector should not contain the default Java float[] string representation");

        JsonNode language = root.path("knn").path("filter").path("terms").path("language");
        assertTrue(language.isArray() && "en".equals(language.get(0).asText()),
            "kNN hybrid query should include language terms filter when language is provided");
    }

    @Test
    void buildKnnQuery_serializesQueryVectorAsJsonArray() throws Exception {
        ShowSearchRequest request = new ShowSearchRequest();
        setField(request, "q", "AI");
        setField(request, "size", 10);
        setField(request, "language", List.of("en"));

        String query = queryBuilder.buildKnnQuery(request, new float[] {1.0f, 2.0f});

        JsonNode root = new ObjectMapper().readTree(query);
        JsonNode queryVector = root.path("knn").path("query_vector");
        assertTrue(queryVector.isArray(), "query_vector should be rendered as a JSON array");
        assertFalse(query.contains("[F@"), "query_vector should not contain the default Java float[] string representation");

        JsonNode language = root.path("knn").path("filter").path("terms").path("language");
        assertTrue(language.isArray() && "en".equals(language.get(0).asText()),
            "kNN query should include language terms filter when language is provided");
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
