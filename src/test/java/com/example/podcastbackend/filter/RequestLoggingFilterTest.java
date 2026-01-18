package com.example.podcastbackend.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestLoggingFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private RequestLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();
    }

    @Test
    void doFilterInternal_callsFilterChain() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/search/shows");
        when(request.getQueryString()).thenReturn(null);
        when(response.getStatus()).thenReturn(200);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_handlesQueryString() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/rankings");
        when(request.getQueryString()).thenReturn("country=tw&type=podcast");
        when(response.getStatus()).thenReturn(200);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotFilter_returnsTrueForActuatorEndpoints() {
        when(request.getRequestURI()).thenReturn("/actuator/health");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_returnsFalseForApiEndpoints() {
        when(request.getRequestURI()).thenReturn("/api/search/shows");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_returnsFalseForHealthEndpoint() {
        when(request.getRequestURI()).thenReturn("/health");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void doFilterInternal_logsEvenWhenExceptionOccurs() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/search/shows");
        when(request.getQueryString()).thenReturn(null);
        when(response.getStatus()).thenReturn(500);

        RuntimeException exception = new RuntimeException("Test exception");
        doThrow(exception).when(filterChain).doFilter(request, response);

        assertThrows(RuntimeException.class, () ->
            filter.doFilterInternal(request, response, filterChain)
        );
    }
}
