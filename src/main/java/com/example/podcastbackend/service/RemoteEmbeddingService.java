package com.example.podcastbackend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Remote embedding service that calls the Python embedding API.
 *
 * The Python API runs on podcast-search and uses sentence-transformers
 * with paraphrase-multilingual-MiniLM-L12-v2 (384 dimensions).
 *
 * Enable with: embedding.service.type=remote (default)
 *
 * Configuration:
 *   embedding.service.url=http://localhost:8081
 *   embedding.service.timeout=5000
 */
@Service
@ConditionalOnProperty(name = "embedding.service.type", havingValue = "remote", matchIfMissing = true)
public class RemoteEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(RemoteEmbeddingService.class);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int dimensions;

    private volatile boolean available = false;

    public RemoteEmbeddingService(
            @Value("${embedding.service.url:http://localhost:8081}") String baseUrl,
            @Value("${embedding.service.timeout:5000}") int timeoutMs,
            ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();

        log.info("RemoteEmbeddingService configured: url={}, timeout={}ms", baseUrl, timeoutMs);

        // Check health and get dimensions
        this.dimensions = checkHealthAndGetDimensions();
    }

    private int checkHealthAndGetDimensions() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                HealthResponse health = objectMapper.readValue(response.body(), HealthResponse.class);
                available = true;
                log.info("Embedding API connected: model={}, dimensions={}",
                        health.model, health.dimensions);
                return health.dimensions;
            } else {
                log.warn("Embedding API health check failed: status={}", response.statusCode());
                return 384; // Default to MiniLM
            }
        } catch (Exception e) {
            log.warn("Embedding API not available: {}. kNN/Hybrid search will be disabled.", e.getMessage());
            return 384;
        }
    }

    @Override
    public float[] encode(String text) {
        if (text == null || text.isBlank()) {
            return new float[dimensions];
        }

        try {
            EmbedRequest request = new EmbedRequest(List.of(text));
            String requestBody = objectMapper.writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/embed"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Embedding API error: status={}, body={}", response.statusCode(), response.body());
                throw new RuntimeException("Embedding API returned " + response.statusCode());
            }

            EmbedResponse embedResponse = objectMapper.readValue(response.body(), EmbedResponse.class);

            if (embedResponse.embeddings == null || embedResponse.embeddings.isEmpty()) {
                throw new RuntimeException("Empty embeddings response");
            }

            // Convert List<Double> to float[]
            List<Double> embedding = embedResponse.embeddings.get(0);
            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }

            available = true;
            return result;

        } catch (Exception e) {
            log.error("Failed to encode text: {}", e.getMessage());
            available = false;
            throw new RuntimeException("Embedding API call failed", e);
        }
    }

    @Override
    public int getDimensions() {
        return dimensions;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    // Request/Response DTOs

    private record EmbedRequest(List<String> texts) {}

    private static class EmbedResponse {
        @JsonProperty("embeddings")
        public List<List<Double>> embeddings;

        @JsonProperty("model")
        public String model;

        @JsonProperty("dimensions")
        public int dimensions;
    }

    private static class HealthResponse {
        @JsonProperty("status")
        public String status;

        @JsonProperty("model")
        public String model;

        @JsonProperty("dimensions")
        public int dimensions;
    }
}
