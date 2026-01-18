package com.example.podcastbackend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class AppleChartClient {

    private static final Logger log = LoggerFactory.getLogger(AppleChartClient.class);

    private static final String PODCAST_CHART_URL =
            "https://rss.applemarketingtools.com/api/v2/%s/podcasts/top/100/podcasts.json";

    private static final String EPISODE_CHART_URL =
            "https://rss.applemarketingtools.com/api/v2/%s/podcasts/top/100/podcast-episodes.json";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AppleChartClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    @CircuitBreaker(name = "appleApi", fallbackMethod = "fallbackPodcastChart")
    public JsonNode fetchPodcastChart(String region) {
        String url = String.format(PODCAST_CHART_URL, region);
        return fetchChart(url, "podcast", region);
    }

    @CircuitBreaker(name = "appleApi", fallbackMethod = "fallbackEpisodeChart")
    public JsonNode fetchEpisodeChart(String region) {
        String url = String.format(EPISODE_CHART_URL, region);
        return fetchChart(url, "episode", region);
    }

    private JsonNode fetchChart(String url, String type, String region) {
        try {
            log.info("Fetching {} chart for region: {}", type, region);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Apple API returned status {}: {}", response.statusCode(), response.body());
                throw new RuntimeException("Apple API returned status " + response.statusCode());
            }

            JsonNode json = objectMapper.readTree(response.body());
            log.info("Successfully fetched {} chart for region: {}", type, region);
            return json;

        } catch (Exception e) {
            log.error("Failed to fetch {} chart for region: {}", type, region, e);
            throw new RuntimeException("Failed to fetch chart from Apple API", e);
        }
    }

    @SuppressWarnings("unused")
    private JsonNode fallbackPodcastChart(String region, Throwable t) {
        log.warn("Circuit breaker fallback for podcast chart, region: {}, error: {}", region, t.getMessage());
        return null;
    }

    @SuppressWarnings("unused")
    private JsonNode fallbackEpisodeChart(String region, Throwable t) {
        log.warn("Circuit breaker fallback for episode chart, region: {}, error: {}", region, t.getMessage());
        return null;
    }
}
