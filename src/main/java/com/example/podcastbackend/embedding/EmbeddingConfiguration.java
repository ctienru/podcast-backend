package com.example.podcastbackend.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfiguration {

    @Bean
    public EmbeddingProvider embeddingProvider(
            @Value("${embedding.external.url}") String url,
            @Value("${embedding.external.key}") String key,
            @Value("${embedding.external.model-zh:paraphrase-multilingual-MiniLM-L12-v2}") String modelZh,
            @Value("${embedding.external.model-en:paraphrase-multilingual-MiniLM-L12-v2}") String modelEn,
            @Value("${embedding.external.timeout-ms:2000}") int timeoutMs,
            @Value("${embedding.external.provider-type:openai}") String providerType,
            ObjectMapper objectMapper
    ) {
        if ("runpod".equals(providerType)) {
            return new RunPodEmbeddingProvider(url, key, modelZh, modelEn, timeoutMs, objectMapper);
        }
        return new ExternalEmbeddingProvider(url, key, modelZh, modelEn, timeoutMs, objectMapper);
    }
}
