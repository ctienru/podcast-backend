package com.example.podcastbackend.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ExternalEmbeddingProviderTest {

    private MockWebServer mockServer;
    private ExternalEmbeddingProvider provider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String MODEL_ZH = "paraphrase-multilingual-MiniLM-L12-v2";
    private static final String MODEL_EN = "paraphrase-multilingual-MiniLM-L12-v2";

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        provider = new ExternalEmbeddingProvider(
                mockServer.url("/v1/embeddings").toString(),
                "test-api-key",
                MODEL_ZH,
                MODEL_EN,
                2000,
                objectMapper
        );
    }

    @AfterEach
    void tearDown() {
        try {
            mockServer.shutdown();
        } catch (IOException ignored) {
        }
    }

    private String successResponse(String model, String... values) {
        String vals = String.join(",", values);
        return String.format(
                "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[%s]}],\"model\":\"%s\"}",
                vals, model);
    }

    @Test
    void embed_zh_sendsCorrectModelAndFormat() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody(successResponse(MODEL_ZH, "0.1", "0.2", "0.3"))
                .setHeader("Content-Type", "application/json"));

        float[] result = provider.embed("人工智慧", EmbeddingProfile.ZH);

        RecordedRequest req = mockServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(req);
        assertEquals("POST", req.getMethod());
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"" + MODEL_ZH + "\""), "Request must include zh model name");
        assertTrue(body.contains("人工智慧"), "Request must include the input text");
        assertTrue(body.contains("\"input\""), "Request must use 'input' field (OpenAI format)");
        assertFalse(body.contains("\"language\""), "Request must NOT contain old 'language' field");

        assertEquals(3, result.length);
        assertEquals(0.1f, result[0], 0.001f);
    }

    @Test
    void embed_en_sendsCorrectModel() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody(successResponse(MODEL_EN, "0.5", "0.6"))
                .setHeader("Content-Type", "application/json"));

        provider.embed("machine learning", EmbeddingProfile.EN);

        RecordedRequest req = mockServer.takeRequest(1, TimeUnit.SECONDS);
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"" + MODEL_EN + "\""), "Request must include en model name");
    }

    @Test
    void embed_sendsAuthorizationHeader() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody(successResponse(MODEL_ZH, "0.1", "0.2"))
                .setHeader("Content-Type", "application/json"));

        provider.embed("test", EmbeddingProfile.ZH);

        RecordedRequest req = mockServer.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("Bearer test-api-key", req.getHeader("Authorization"));
    }

    @Test
    void embed_usesIndexToSelectEmbedding() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[1.0,2.0,3.0]}],\"model\":\"" + MODEL_ZH + "\"}")
                .setHeader("Content-Type", "application/json"));

        float[] result = provider.embed("test", EmbeddingProfile.ZH);

        assertEquals(3, result.length);
        assertEquals(1.0f, result[0], 0.001f);
        assertEquals(2.0f, result[1], 0.001f);
        assertEquals(3.0f, result[2], 0.001f);
    }

    @Test
    void embed_401_throwsEmbeddingUnavailableException() {
        mockServer.enqueue(new MockResponse().setResponseCode(401).setBody("Unauthorized"));

        assertThrows(EmbeddingUnavailableException.class,
                () -> provider.embed("test", EmbeddingProfile.ZH));
    }

    @Test
    void embed_403_throwsEmbeddingUnavailableException() {
        mockServer.enqueue(new MockResponse().setResponseCode(403).setBody("Forbidden"));

        assertThrows(EmbeddingUnavailableException.class,
                () -> provider.embed("test", EmbeddingProfile.ZH));
    }

    @Test
    void embed_500_throwsEmbeddingUnavailableException() {
        mockServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

        assertThrows(EmbeddingUnavailableException.class,
                () -> provider.embed("test", EmbeddingProfile.ZH));
    }

    @Test
    void embed_emptyData_throwsEmbeddingUnavailableException() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"object\":\"list\",\"data\":[],\"model\":\"" + MODEL_ZH + "\"}")
                .setHeader("Content-Type", "application/json"));

        assertThrows(EmbeddingUnavailableException.class,
                () -> provider.embed("test", EmbeddingProfile.ZH));
    }

    @Test
    void embed_serverUnavailable_throwsEmbeddingUnavailableException() throws IOException {
        mockServer.shutdown();

        assertThrows(EmbeddingUnavailableException.class,
                () -> provider.embed("test", EmbeddingProfile.ZH));
    }

    @Test
    void embed_noneProfile_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.embed("test", EmbeddingProfile.NONE));
    }

    @Test
    void isAvailable_withConfiguredUrl_returnsTrue() {
        assertTrue(provider.isAvailable());
    }

    @Test
    void isAvailable_withBlankUrl_returnsFalse() {
        ExternalEmbeddingProvider disabled = new ExternalEmbeddingProvider(
                "", "key", MODEL_ZH, MODEL_EN, 2000, objectMapper);
        assertFalse(disabled.isAvailable());
    }

    @Test
    void isAvailable_withNullUrl_returnsFalse() {
        ExternalEmbeddingProvider disabled = new ExternalEmbeddingProvider(
                null, "key", MODEL_ZH, MODEL_EN, 2000, objectMapper);
        assertFalse(disabled.isAvailable());
    }
}
