package com.example.podcastbackend.search.query;

import com.example.podcastbackend.request.ShowSearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Component
public class ShowSearchQueryBuilder {

    private final Mustache bm25Template;
    private final Mustache knnTemplate;
    private final ObjectMapper objectMapper;

    private static final int KNN_NUM_CANDIDATES = 100;

    public ShowSearchQueryBuilder(
            @Value("${search.show.template.path:podcast-spec/es/search_shows/query.template.mustache}") String defaultPath,
            @Value("${search.show.template.bm25.path:podcast-spec/es/search_shows/bm25.query.template.mustache}") String bm25Path,
            @Value("${search.show.template.knn.path:podcast-spec/es/search_shows/knn.query.template.mustache}") String knnPath,
            ObjectMapper objectMapper
    ) throws IOException {
        this.objectMapper = objectMapper;
        this.bm25Template = loadTemplate(bm25Path, "bm25");
        this.knnTemplate = loadTemplate(knnPath, "knn");
    }

    private Mustache loadTemplate(String path, String name) throws IOException {
        var mf = new DefaultMustacheFactory();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path);
        if (inputStream == null) {
            throw new IOException("Template not found in classpath: " + path);
        }
        var reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        return mf.compile(reader, name);
    }

    /**
     * Build BM25 query for text-based search (default).
     * This is the original build() method renamed for clarity.
     */
    public String build(ShowSearchRequest request) {
        return buildBm25Query(request);
    }

    /**
     * Build BM25 query for text-based search.
     */
    public String buildBm25Query(ShowSearchRequest request) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("query", request.getQ());

        int page = request.getPage() != null ? request.getPage() : 1;
        int size = request.getSize() != null ? request.getSize() : 10;
        int from = (page - 1) * size;

        ctx.put("from", from);
        ctx.put("size", size);

        if (request.getLanguage() != null && !request.getLanguage().isEmpty()) {
            try {
                ctx.put("languagesJson", objectMapper.writeValueAsString(request.getLanguage()));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize languages", e);
            }
        }

        StringWriter writer = new StringWriter();
        bm25Template.execute(writer, ctx);
        return writer.toString();
    }

    /**
     * Build kNN query for semantic search.
     *
     * @param request The search request
     * @param queryVector The embedding vector for the query (384 dimensions)
     */
    public String buildKnnQuery(ShowSearchRequest request, float[] queryVector) {
        Map<String, Object> ctx = buildKnnContext(queryVector, request.getLanguage());
        ctx.put("size", request.getSize() != null ? request.getSize() : 10);

        StringWriter writer = new StringWriter();
        knnTemplate.execute(writer, ctx);
        return writer.toString();
    }

    /**
     * Build BM25 query with larger size for RRF fusion.
     */
    public String buildBm25QueryForHybrid(ShowSearchRequest request, int windowSize) {
        Map<String, Object> ctx = new HashMap<>();

        ctx.put("query", request.getQ());
        ctx.put("from", 0);
        ctx.put("size", windowSize);

        if (request.getLanguage() != null && !request.getLanguage().isEmpty()) {
            try {
                ctx.put("languagesJson", objectMapper.writeValueAsString(request.getLanguage()));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize languages", e);
            }
        }

        StringWriter writer = new StringWriter();
        bm25Template.execute(writer, ctx);
        return writer.toString();
    }

    /**
     * Build kNN query with larger size for RRF fusion.
     */
    public String buildKnnQueryForHybrid(ShowSearchRequest request, float[] queryVector, int windowSize) {
        Map<String, Object> ctx = buildKnnContext(queryVector, request.getLanguage());
        ctx.put("size", windowSize);

        StringWriter writer = new StringWriter();
        knnTemplate.execute(writer, ctx);
        return writer.toString();
    }

    private Map<String, Object> buildKnnContext(float[] queryVector, List<String> languages) {
        Map<String, Object> ctx = new HashMap<>();
        try {
            ctx.put("query_vector", objectMapper.writeValueAsString(queryVector));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize query vector", e);
        }

        if (languages != null && !languages.isEmpty()) {
            try {
                ctx.put("languagesJson", objectMapper.writeValueAsString(languages));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize languages", e);
            }
        }

        ctx.put("num_candidates", KNN_NUM_CANDIDATES);
        ctx.put("toJson", new ToJsonLambda(objectMapper));
        return ctx;
    }

    /**
     * Mustache lambda for JSON serialization.
     */
    private record ToJsonLambda(ObjectMapper mapper) implements com.github.mustachejava.TemplateFunction {
        @Override
        public String apply(String input) {
            return input;
        }
    }
}
