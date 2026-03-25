package com.example.podcastbackend.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class PodcastSearchEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(PodcastSearchEmbeddingProvider.class);

    private final String serviceUrl;
    private final int readTimeoutMs;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PodcastSearchEmbeddingProvider(
            @Value("${embedding.service.url:http://localhost:8081}") String serviceUrl,
            @Value("${embedding.service.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${embedding.service.read-timeout-ms:2000}") int readTimeoutMs,
            ObjectMapper objectMapper
    ) {
        this.serviceUrl = serviceUrl;
        this.readTimeoutMs = readTimeoutMs;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    public float[] embed(String query, EmbeddingProfile profile) {
        if (profile == EmbeddingProfile.NONE) {
            throw new IllegalArgumentException("embed() called with NONE profile");
        }

        String language = resolveLanguage(profile);

        try {
            EmbedRequest reqBody = new EmbedRequest(List.of(query), language);
            String requestJson = objectMapper.writeValueAsString(reqBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/embed"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .timeout(Duration.ofMillis(readTimeoutMs))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new EmbeddingUnavailableException(
                        "podcast-search /embed returned HTTP " + response.statusCode());
            }

            EmbedResponse embedResponse = objectMapper.readValue(response.body(), EmbedResponse.class);

            if (embedResponse.embeddings == null || embedResponse.embeddings.isEmpty()) {
                throw new EmbeddingUnavailableException("Empty embeddings response from podcast-search");
            }

            List<Double> embedding = embedResponse.embeddings.get(0);
            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }
            return result;

        } catch (EmbeddingUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("embedding_provider_failed", kv("error", e.getMessage()));
            throw new EmbeddingUnavailableException("Failed to call podcast-search /embed: " + e.getMessage(), e);
        }
    }

    private String resolveLanguage(EmbeddingProfile profile) {
        return switch (profile) {
            case ZH -> "zh-tw";
            case EN -> "en";
            case NONE -> throw new IllegalArgumentException("NONE profile has no language mapping");
        };
    }

    private record EmbedRequest(
            List<String> texts,
            String language
    ) {}

    private static class EmbedResponse {
        @JsonProperty("embeddings")
        public List<List<Double>> embeddings;

        @JsonProperty("model")
        public String model;

        @JsonProperty("dimensions")
        public int dimensions;
    }
}
