package com.example.podcastbackend.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class EmbeddingConfigurationTest {

    private final EmbeddingConfiguration config = new EmbeddingConfiguration();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void embeddingProvider_returnsExternalProvider() {
        EmbeddingProvider provider = config.embeddingProvider(
                "http://api.example.com/v1/embeddings",
                "test-key",
                "paraphrase-multilingual-MiniLM-L12-v2",
                "paraphrase-multilingual-MiniLM-L12-v2",
                2000,
                "openai",
                objectMapper);
        assertInstanceOf(ExternalEmbeddingProvider.class, provider);
    }

    @Test
    void embeddingProvider_withRunpodType_returnsRunPodProvider() {
        EmbeddingProvider provider = config.embeddingProvider(
                "https://api.runpod.ai/v2/model/run",
                "test-key",
                "paraphrase-multilingual-MiniLM-L12-v2",
                "paraphrase-multilingual-MiniLM-L12-v2",
                2000,
                "runpod",
                objectMapper);
        assertInstanceOf(RunPodEmbeddingProvider.class, provider);
    }

    @Test
    void embeddingProvider_withOpenaiType_returnsExternalProvider() {
        EmbeddingProvider provider = config.embeddingProvider(
                "http://api.example.com/v1/embeddings",
                "test-key",
                "paraphrase-multilingual-MiniLM-L12-v2",
                "paraphrase-multilingual-MiniLM-L12-v2",
                2000,
                "openai",
                objectMapper);
        assertInstanceOf(ExternalEmbeddingProvider.class, provider);
    }

    @Test
    void embeddingProvider_defaultEmptyType_returnsExternalProvider() {
        EmbeddingProvider provider = config.embeddingProvider(
                "http://api.example.com/v1/embeddings",
                "test-key",
                "paraphrase-multilingual-MiniLM-L12-v2",
                "paraphrase-multilingual-MiniLM-L12-v2",
                2000,
                "",
                objectMapper);
        assertInstanceOf(ExternalEmbeddingProvider.class, provider);
    }
}
