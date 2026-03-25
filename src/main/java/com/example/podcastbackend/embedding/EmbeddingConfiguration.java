package com.example.podcastbackend.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfiguration {

    @Bean
    @ConditionalOnProperty(name = "embedding.strategy", havingValue = "local", matchIfMissing = true)
    public EmbeddingProvider localEmbeddingProvider(
            @Value("${embedding.service.url}") String url,
            @Value("${embedding.service.connect-timeout-ms}") int connectTimeout,
            @Value("${embedding.service.read-timeout-ms}") int readTimeout,
            ObjectMapper objectMapper
    ) {
        return new PodcastSearchEmbeddingProvider(url, connectTimeout, readTimeout, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "embedding.strategy", havingValue = "api")
    public EmbeddingProvider externalEmbeddingProvider(
            @Value("${embedding.external.url}") String url,
            @Value("${embedding.external.key}") String key,
            @Value("${embedding.external.model:}") String model
    ) {
        return new ExternalEmbeddingProvider(url, key, model);
    }
}
