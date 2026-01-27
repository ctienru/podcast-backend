package com.example.podcastbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Random;

/**
 * Stub implementation of EmbeddingService for development and testing.
 *
 * Generates deterministic pseudo-random vectors based on the input text hash.
 * This allows consistent results during development without requiring the ONNX model.
 *
 * Enable with: embedding.service.type=stub (default)
 * Disable with: embedding.service.type=onnx
 */
@Service
@ConditionalOnProperty(name = "embedding.service.type", havingValue = "stub", matchIfMissing = false)
public class StubEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(StubEmbeddingService.class);
    private static final int DIMENSIONS = 384;  // MiniLM model

    public StubEmbeddingService() {
        log.warn("Using STUB EmbeddingService - vectors are not semantically meaningful!");
        log.warn("Set embedding.service.type=onnx to use real embeddings");
    }

    @Override
    public float[] encode(String text) {
        if (text == null || text.isBlank()) {
            return new float[DIMENSIONS];
        }

        // Generate deterministic pseudo-random vector based on text hash
        long seed = hashText(text);
        Random random = new Random(seed);

        float[] embedding = new float[DIMENSIONS];
        float norm = 0f;

        // Generate random values
        for (int i = 0; i < DIMENSIONS; i++) {
            embedding[i] = (float) random.nextGaussian();
            norm += embedding[i] * embedding[i];
        }

        // Normalize to unit vector (cosine similarity friendly)
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < DIMENSIONS; i++) {
            embedding[i] /= norm;
        }

        log.debug("Generated stub embedding for text: '{}' (length={})",
                truncate(text, 50), text.length());

        return embedding;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private long hashText(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            // Use first 8 bytes as long seed
            long seed = 0;
            for (int i = 0; i < 8; i++) {
                seed = (seed << 8) | (hash[i] & 0xFF);
            }
            return seed;
        } catch (Exception e) {
            return text.hashCode();
        }
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
