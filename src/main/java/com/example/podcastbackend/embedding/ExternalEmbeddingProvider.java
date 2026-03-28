package com.example.podcastbackend.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class ExternalEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(ExternalEmbeddingProvider.class);

    private final String apiUrl;
    private final String apiKey;
    private final String modelZh;
    private final String modelEn;
    private final int timeoutMs;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ExternalEmbeddingProvider(
            String apiUrl,
            String apiKey,
            String modelZh,
            String modelEn,
            int timeoutMs,
            ObjectMapper objectMapper) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.modelZh = modelZh;
        this.modelEn = modelEn;
        this.timeoutMs = timeoutMs;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public boolean isAvailable() {
        return apiUrl != null && !apiUrl.isBlank();
    }

    @Override
    public float[] embed(String text, EmbeddingProfile profile) {
        if (profile == EmbeddingProfile.NONE) {
            throw new IllegalArgumentException("embed() called with NONE profile");
        }

        String model = profile == EmbeddingProfile.ZH ? modelZh : modelEn;

        long start = System.currentTimeMillis();

        try {
            String requestJson = objectMapper.writeValueAsString(Map.of("model", model, "input", text));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                log.error("embedding_api_auth_failed",
                        kv("model", model), kv("profile", profile.name()), kv("status", response.statusCode()));
                throw new EmbeddingUnavailableException(
                        "Embedding API auth failed: HTTP " + response.statusCode());
            }

            if (response.statusCode() != 200) {
                log.warn("embedding_api_error",
                        kv("model", model), kv("profile", profile.name()), kv("status", response.statusCode()));
                throw new EmbeddingUnavailableException(
                        "Embedding API returned HTTP " + response.statusCode());
            }

            EmbedResponse embedResponse = objectMapper.readValue(response.body(), EmbedResponse.class);

            if (embedResponse.data == null || embedResponse.data.isEmpty()) {
                throw new EmbeddingUnavailableException("Empty data in embedding API response");
            }

            List<Double> embedding = embedResponse.data.stream()
                    .min(Comparator.comparingInt(d -> d.index))
                    .orElseThrow(() -> new EmbeddingUnavailableException("No embedding object in response")).embedding;

            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }

            log.debug("embedding_api_ok",
                    kv("model", model), kv("profile", profile.name()),
                    kv("latency_ms", System.currentTimeMillis() - start),
                    kv("dimensions", result.length));
            return result;

        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("embedding_api_timeout",
                    kv("model", model), kv("profile", profile.name()), kv("timeout_ms", timeoutMs));
            throw new EmbeddingUnavailableException("Embedding API timeout", e);
        } catch (EmbeddingUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("embedding_provider_failed", kv("error", e.getMessage()));
            throw new EmbeddingUnavailableException("Failed to call embedding API: " + e.getMessage(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EmbedResponse {
        @JsonProperty("data")
        public List<EmbeddingData> data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EmbeddingData {
        @JsonProperty("index")
        public int index;

        @JsonProperty("embedding")
        public List<Double> embedding;
    }
}
