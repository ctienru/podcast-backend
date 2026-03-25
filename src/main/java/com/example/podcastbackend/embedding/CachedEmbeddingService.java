package com.example.podcastbackend.embedding;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.concurrent.TimeUnit;

@Service
public class CachedEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(CachedEmbeddingService.class);

    private final EmbeddingProvider provider;
    private final QueryNormalizer normalizer;
    private final Cache<String, float[]> cache;
    private final CircuitBreaker circuitBreaker;
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter circuitBreakerOpen;
    private final Timer apiLatency;

    public CachedEmbeddingService(
            EmbeddingProvider provider,
            QueryNormalizer normalizer,
            CircuitBreakerRegistry circuitBreakerRegistry,
            MeterRegistry meterRegistry,
            @Value("${embedding.cache.ttl-minutes:30}") int ttlMinutes,
            @Value("${embedding.cache.max-size:1000}") int maxSize
    ) {
        this.provider = provider;
        this.normalizer = normalizer;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(maxSize)
                .build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("embeddingApi");
        this.cacheHits = meterRegistry.counter("embedding.cache.hits");
        this.cacheMisses = meterRegistry.counter("embedding.cache.misses");
        this.circuitBreakerOpen = meterRegistry.counter("embedding.circuit_breaker.open");
        this.apiLatency = meterRegistry.timer("embedding.api.latency");
    }

    public float[] embed(String query, EmbeddingProfile profile) {
        if (profile == null || profile == EmbeddingProfile.NONE) {
            throw new IllegalArgumentException("EmbeddingProfile.NONE or null is not valid for embedding");
        }
        String normalized = normalizer.normalize(query, profile);
        String key = "embedding:" + profile.name().toLowerCase() + ":" + normalized;

        float[] cached = cache.getIfPresent(key);
        if (cached != null) {
            cacheHits.increment();
            return cached;
        }
        cacheMisses.increment();

        try {
            float[] vector = apiLatency.recordCallable(
                    () -> circuitBreaker.executeSupplier(() -> provider.embed(normalized, profile)));
            cache.put(key, vector);
            return vector;
        } catch (CallNotPermittedException e) {
            circuitBreakerOpen.increment();
            log.warn("embedding_circuit_breaker_open", kv("profile", profile.name()));
            throw new EmbeddingUnavailableException("Embedding circuit breaker is OPEN", e);
        } catch (EmbeddingUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingUnavailableException("Embedding call failed: " + e.getMessage(), e);
        }
    }

    public boolean isAvailable() {
        return provider.isAvailable() && circuitBreaker.getState() != CircuitBreaker.State.OPEN;
    }
}
