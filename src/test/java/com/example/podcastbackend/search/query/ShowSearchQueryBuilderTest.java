package com.example.podcastbackend.search.query;

import com.example.podcastbackend.request.ShowSearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

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
    void buildKnnQueryForHybrid_serializesQueryVectorAsJsonArray() {
        String query = queryBuilder.buildKnnQueryForHybrid(new float[] {0.1f, 0.2f, 0.3f}, 100);

        assertTrue(query.contains("\"query_vector\": [0.1,0.2,0.3]"),
                "query_vector should be rendered as a JSON array");
        assertFalse(query.contains("[F@"),
                "query_vector should not contain the default Java float[] string representation");
    }

    @Test
    void buildKnnQuery_serializesQueryVectorAsJsonArray() {
        ShowSearchRequest request = new ShowSearchRequest();
        setField(request, "q", "AI");
        setField(request, "size", 10);

        String query = queryBuilder.buildKnnQuery(request, new float[] {1.0f, 2.0f});

        assertTrue(query.contains("\"query_vector\": [1.0,2.0]"),
                "query_vector should be rendered as a JSON array");
        assertFalse(query.contains("[F@"),
                "query_vector should not contain the default Java float[] string representation");
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
