package com.example.podcastbackend.service;

/**
 * Service for encoding text into embedding vectors.
 *
 * Uses the paraphrase-multilingual-MiniLM-L12-v2 model (384 dimensions)
 * for semantic search capabilities.
 */
public interface EmbeddingService {

    /**
     * Encode a text query into an embedding vector.
     *
     * @param text The text to encode
     * @return A 384-dimensional float array representing the embedding
     */
    float[] encode(String text);

    /**
     * Get the dimensionality of the embedding vectors.
     *
     * @return The number of dimensions (384 for MiniLM)
     */
    default int getDimensions() {
        return 384;
    }

    /**
     * Check if the embedding service is available and ready.
     *
     * @return true if the service can process requests
     */
    boolean isAvailable();
}
