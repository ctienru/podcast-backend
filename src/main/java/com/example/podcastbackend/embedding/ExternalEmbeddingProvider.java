package com.example.podcastbackend.embedding;

public class ExternalEmbeddingProvider implements EmbeddingProvider {
    private final String apiUrl;
    private final String apiKey;
    private final String model;

    public ExternalEmbeddingProvider(String apiUrl, String apiKey, String model) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public float[] embed(String text, EmbeddingProfile profile) {
        // TODO: implement OpenAI-compatible call once external API is confirmed.
        // POST apiUrl, {"model": model, "input": text}, Bearer apiKey
        // → response: {"data": [{"embedding": [...]}]}
        throw new IllegalStateException(
            "ExternalEmbeddingProvider not yet implemented — set EMBEDDING_API_URL, EMBEDDING_API_KEY, EMBEDDING_API_MODEL");
    }
}
