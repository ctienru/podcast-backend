package com.example.podcastbackend.embedding;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CachedEmbeddingServiceTest {

    private EmbeddingProvider provider;
    private CachedEmbeddingService service;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        provider = mock(EmbeddingProvider.class);
        QueryNormalizer normalizer = new QueryNormalizer();

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        circuitBreaker = registry.circuitBreaker("embeddingApi");

        service = new CachedEmbeddingService(provider, normalizer, registry, new SimpleMeterRegistry(), 30, 1000);
    }

    @Test
    void embed_cacheMiss_callsProvider() {
        float[] expected = new float[]{0.1f, 0.2f, 0.3f};
        when(provider.embed("人工智慧", EmbeddingProfile.ZH)).thenReturn(expected);

        float[] result = service.embed("人工智慧", EmbeddingProfile.ZH);

        assertArrayEquals(expected, result);
        verify(provider, times(1)).embed("人工智慧", EmbeddingProfile.ZH);
    }

    @Test
    void embed_cacheHit_doesNotCallProviderAgain() {
        float[] expected = new float[]{0.1f, 0.2f};
        when(provider.embed(any(), eq(EmbeddingProfile.ZH))).thenReturn(expected);

        service.embed("AI", EmbeddingProfile.ZH);
        service.embed("AI", EmbeddingProfile.ZH);

        verify(provider, times(1)).embed(any(), any());
    }

    @Test
    void embed_differentProfiles_cachedSeparately() {
        float[] zhVector = new float[]{0.1f};
        float[] enVector = new float[]{0.9f};
        when(provider.embed("test", EmbeddingProfile.ZH)).thenReturn(zhVector);
        when(provider.embed("test", EmbeddingProfile.EN)).thenReturn(enVector);

        float[] zh = service.embed("test", EmbeddingProfile.ZH);
        float[] en = service.embed("test", EmbeddingProfile.EN);

        assertArrayEquals(zhVector, zh);
        assertArrayEquals(enVector, en);
        verify(provider, times(1)).embed("test", EmbeddingProfile.ZH);
        verify(provider, times(1)).embed("test", EmbeddingProfile.EN);
    }

    @Test
    void embed_circuitBreakerOpen_throwsEmbeddingUnavailableException() {
        when(provider.embed(any(), any())).thenThrow(new EmbeddingUnavailableException("down"));

        // Force circuit breaker to OPEN by exhausting the sliding window
        for (int i = 0; i < 4; i++) {
            try { service.embed("q" + i, EmbeddingProfile.ZH); } catch (EmbeddingUnavailableException ignored) {}
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertThrows(EmbeddingUnavailableException.class,
                () -> service.embed("another query", EmbeddingProfile.ZH));
    }

    @Test
    void isAvailable_closedState_returnsTrue() {
        assertTrue(service.isAvailable());
    }

    @Test
    void isAvailable_openState_returnsFalse() {
        when(provider.embed(any(), any())).thenThrow(new EmbeddingUnavailableException("down"));
        for (int i = 0; i < 4; i++) {
            try { service.embed("q" + i, EmbeddingProfile.ZH); } catch (EmbeddingUnavailableException ignored) {}
        }

        assertFalse(service.isAvailable());
    }
}
