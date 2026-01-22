package com.example.podcastbackend.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationException(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();

        return Map.of(
                "status", "error",
                "error", Map.of(
                        "code", "INVALID_PARAMETER",
                        "message", String.join("; ", errors),
                        "details", errors
                )
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleConstraintViolationException(ConstraintViolationException e) {
        List<String> errors = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();

        return Map.of(
                "status", "error",
                "error", Map.of(
                        "code", "INVALID_PARAMETER",
                        "message", String.join("; ", errors),
                        "details", errors
                )
        );
    }

    @ExceptionHandler(SearchParseException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleSearchParseException(SearchParseException e) {
        log.error("Search parse error: {}", e.getMessage(), e);
        return Map.of(
                "status", "error",
                "error", Map.of(
                        "code", e.getErrorCode(),
                        "message", e.getMessage()
                )
        );
    }

    @ExceptionHandler(SearchServiceException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> handleSearchServiceException(SearchServiceException e) {
        log.error("Search service error", e);
        return Map.of(
                "status", "error",
                "error", Map.of(
                        "code", "SEARCH_SERVICE_ERROR",
                        "message", "Search service temporarily unavailable"
                )
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgumentException(IllegalArgumentException e) {
        return Map.of(
                "status", "error",
                "error", Map.of(
                        "code", "BAD_REQUEST",
                        "message", e.getMessage()
                )
        );
    }

    @ExceptionHandler(RequestNotPermitted.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Map<String, Object> handleRateLimitException(RequestNotPermitted e) {
        log.warn("Rate limit exceeded: {}", e.getMessage());
        return Map.of(
                "status", "error",
                "error", Map.of(
                        "code", "RATE_LIMIT_EXCEEDED",
                        "message", "Too many requests. Please try again later."
                )
        );
    }

    @ExceptionHandler(CallNotPermittedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> handleCircuitBreakerException(CallNotPermittedException e) {
        log.warn("Circuit breaker open: {}", e.getMessage());
        return Map.of(
                "status", "error",
                "error", Map.of(
                        "code", "SERVICE_UNAVAILABLE",
                        "message", "Service temporarily unavailable. Please try again later."
                )
        );
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleRuntimeException(RuntimeException e) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unexpected error occurred [traceId={}]", traceId, e);
        return Map.of(
                "status", "error",
                "error", Map.of(
                        "code", "INTERNAL_ERROR",
                        "message", "An unexpected error occurred",
                        "traceId", traceId
                )
        );
    }
}
