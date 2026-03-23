package com.example.podcastbackend.embedding;

import org.springframework.stereotype.Component;

@Component
public class QueryNormalizer {

    public String normalize(String query, EmbeddingProfile profile) {
        String q = query.trim().replaceAll("\\s+", " ");
        if (profile == EmbeddingProfile.EN) {
            q = q.toLowerCase();
        }
        return q;
    }
}
