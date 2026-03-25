package com.example.podcastbackend.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class EmbeddingConfigurationTest {

    private final EmbeddingConfiguration config = new EmbeddingConfiguration();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void localEmbeddingProvider_returnsPodcastSearchProvider() {
        EmbeddingProvider provider = config.localEmbeddingProvider(
                "http://localhost:8081", 1000, 2000, objectMapper);
        assertInstanceOf(PodcastSearchEmbeddingProvider.class, provider);
    }

    @Test
    void externalEmbeddingProvider_returnsExternalProvider() {
        EmbeddingProvider provider = config.externalEmbeddingProvider(
                "http://api.example.com", "test-key", "text-embedding-3-small");
        assertInstanceOf(ExternalEmbeddingProvider.class, provider);
    }
}
