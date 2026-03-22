package com.example.podcastbackend.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleSearchServiceException_returnsCorrectErrorFormat() {
        SearchServiceException exception = new SearchServiceException("ES connection failed", new RuntimeException());

        Map<String, Object> response = handler.handleSearchServiceException(exception);

        assertEquals("error", response.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertEquals("SEARCH_SERVICE_ERROR", error.get("code"));
        assertEquals("Search service temporarily unavailable", error.get("message"));
    }

    @Test
    void handleIllegalArgumentException_returnsCorrectErrorFormat() {
        IllegalArgumentException exception = new IllegalArgumentException("Invalid page number");

        Map<String, Object> response = handler.handleIllegalArgumentException(exception);

        assertEquals("error", response.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertEquals("BAD_REQUEST", error.get("code"));
        assertEquals("Invalid page number", error.get("message"));
    }

    @Test
    void handleRuntimeException_returnsCorrectErrorFormat() {
        RuntimeException exception = new RuntimeException("Something went wrong");

        Map<String, Object> response = handler.handleRuntimeException(exception);

        assertEquals("error", response.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertEquals("INTERNAL_ERROR", error.get("code"));
        assertEquals("An unexpected error occurred", error.get("message"));
    }

    @Test
    void handleRuntimeException_doesNotExposeInternalMessage() {
        RuntimeException exception = new RuntimeException("Database password: secret123");

        Map<String, Object> response = handler.handleRuntimeException(exception);

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertFalse(error.get("message").toString().contains("secret"));
        assertEquals("An unexpected error occurred", error.get("message"));
    }

    @Test
    void handleRateLimitException_returnsCorrectErrorFormat() {
        RateLimiter rateLimiter = RateLimiter.of("test", RateLimiterConfig.custom().build());
        RequestNotPermitted exception = RequestNotPermitted.createRequestNotPermitted(rateLimiter);

        Map<String, Object> response = handler.handleRateLimitException(exception);

        assertEquals("error", response.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertEquals("RATE_LIMIT_EXCEEDED", error.get("code"));
        assertEquals("Too many requests. Please try again later.", error.get("message"));
    }

    // --- v2 exceptions ---

    @Test
    void handleCrossIndexPageLimit_returnsNewFlatFormat() {
        CrossIndexPageLimitException exception =
                new CrossIndexPageLimitException("zh-both search only supports up to 5 pages");

        Map<String, Object> response = handler.handleCrossIndexPageLimit(exception);

        assertEquals("CROSS_INDEX_PAGE_LIMIT", response.get("errorCode"));
        assertEquals("zh-both search only supports up to 5 pages", response.get("message"));
        assertFalse(response.containsKey("status"));
    }

    @Test
    void handleInvalidLangParam_returnsNewFlatFormat() {
        InvalidLangParamException exception =
                new InvalidLangParamException("Invalid lang parameter: foo. Allowed: zh-tw, zh-cn, en, zh-both");

        Map<String, Object> response = handler.handleInvalidLangParam(exception);

        assertEquals("INVALID_LANG_PARAM", response.get("errorCode"));
        assertEquals("Invalid lang parameter: foo. Allowed: zh-tw, zh-cn, en, zh-both", response.get("message"));
        assertFalse(response.containsKey("status"));
    }

    @Test
    void handleInvalidSearchParam_returnsNewFlatFormat() {
        InvalidSearchParamException exception = new InvalidSearchParamException("page must be <= 100");

        Map<String, Object> response = handler.handleInvalidSearchParam(exception);

        assertEquals("INVALID_SEARCH_PARAM", response.get("errorCode"));
        assertEquals("page must be <= 100", response.get("message"));
        assertFalse(response.containsKey("status"));
    }

    @Test
    void handleCircuitBreakerException_returnsCorrectErrorFormat() {
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test");
        CallNotPermittedException exception = CallNotPermittedException.createCallNotPermittedException(circuitBreaker);

        Map<String, Object> response = handler.handleCircuitBreakerException(exception);

        assertEquals("error", response.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertEquals("SERVICE_UNAVAILABLE", error.get("code"));
        assertEquals("Service temporarily unavailable. Please try again later.", error.get("message"));
    }
}
