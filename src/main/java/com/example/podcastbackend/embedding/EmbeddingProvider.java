package com.example.podcastbackend.embedding;

public interface EmbeddingProvider {
    float[] embed(String text, EmbeddingProfile profile);

    default boolean isAvailable() {
        return true;
    }
}
