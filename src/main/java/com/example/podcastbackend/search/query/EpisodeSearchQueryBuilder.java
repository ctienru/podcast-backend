package com.example.podcastbackend.search.query;

import com.example.podcastbackend.request.EpisodeSearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class EpisodeSearchQueryBuilder {

    private final Mustache bm25Template;
    private final Mustache knnTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int KNN_NUM_CANDIDATES = 100;

    public EpisodeSearchQueryBuilder(
            @Value("${search.episode.template.bm25.path:podcast-spec/es/search_episodes/bm25.query.template.mustache}") String bm25Path,
            @Value("${search.episode.template.knn.path:podcast-spec/es/search_episodes/knn.query.template.mustache}") String knnPath
    ) throws Exception {
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
     * Build BM25 query for text-based search.
     */
    public String buildBm25Query(EpisodeSearchRequest request) {
        Map<String, Object> ctx = new HashMap<>();

        ctx.put("query", request.getQ());
        ctx.put("from", request.from());
        ctx.put("size", request.getSize());

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
     * @param queryVector The embedding vector for the query (768 dimensions)
     */
    public String buildKnnQuery(EpisodeSearchRequest request, float[] queryVector) {
        Map<String, Object> ctx = new HashMap<>();

        ctx.put("query_vector", queryVector);
        ctx.put("size", request.getSize());
        ctx.put("num_candidates", KNN_NUM_CANDIDATES);
        ctx.put("toJson", new ToJsonLambda(objectMapper));

        StringWriter writer = new StringWriter();
        knnTemplate.execute(writer, ctx);
        return writer.toString();
    }

    /**
     * Build BM25 query with larger size for RRF fusion.
     */
    public String buildBm25QueryForHybrid(EpisodeSearchRequest request, int windowSize) {
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
    public String buildKnnQueryForHybrid(float[] queryVector, int windowSize) {
        Map<String, Object> ctx = new HashMap<>();

        ctx.put("query_vector", queryVector);
        ctx.put("size", windowSize);
        ctx.put("num_candidates", KNN_NUM_CANDIDATES);
        ctx.put("toJson", new ToJsonLambda(objectMapper));

        StringWriter writer = new StringWriter();
        knnTemplate.execute(writer, ctx);
        return writer.toString();
    }

    /**
     * Mustache lambda for JSON serialization.
     */
    private record ToJsonLambda(ObjectMapper mapper) implements com.github.mustachejava.TemplateFunction {
        @Override
        public String apply(String input) {
            try {
                // Input is the variable name, we need to handle this differently
                // For now, return input as-is since we handle JSON in the template
                return input;
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize to JSON", e);
            }
        }
    }
}
