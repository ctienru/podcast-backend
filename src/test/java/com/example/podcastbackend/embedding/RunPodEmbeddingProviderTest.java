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

class RunPodEmbeddingProviderTest {

    private MockWebServer mockServer;
    private RunPodEmbeddingProvider provider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String MODEL_ZH = "paraphrase-multilingual-MiniLM-L12-v2";
    private static final String MODEL_EN = "paraphrase-multilingual-MiniLM-L12-v2-en";

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        provider = new RunPodEmbeddingProvider(
                mockServer.url("/v2/model/run").toString(),
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

    private String runpodSuccessResponse(String... values) {
        String vals = String.join(",", values);
        return String.format(
                "{\"id\":\"test\",\"status\":\"COMPLETED\"," +
                "\"output\":{\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[%s]}]}}",
                vals);
    }

    @Test
    void embed_zh_wrapsRequestInInputEnvelope() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody(runpodSuccessResponse("0.1", "0.2", "0.3"))
                .setHeader("Content-Type", "application/json"));

        provider.embed("人工智慧", EmbeddingProfile.ZH);

        RecordedRequest req = mockServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(req);
        String body = req.getBody().readUtf8();
        // RunPod format: outer "input" envelope wrapping model+input
        assertTrue(body.startsWith("{\"input\":"), "Request must be wrapped in {\"input\": ...}");
        assertTrue(body.contains("\"" + MODEL_ZH + "\""), "Model name must be inside the input envelope");
        assertTrue(body.contains("人工智慧"), "Text must be inside the input envelope");
    }

    @Test
    void embed_parsesEmbeddingFromOutputData() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody(runpodSuccessResponse("1.0", "2.0", "3.0"))
                .setHeader("Content-Type", "application/json"));

        float[] result = provider.embed("test", EmbeddingProfile.ZH);

        assertEquals(3, result.length);
        assertEquals(1.0f, result[0], 0.001f);
        assertEquals(2.0f, result[1], 0.001f);
        assertEquals(3.0f, result[2], 0.001f);
    }

    @Test
    void embed_zh_sendsCorrectModelAndText() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody(runpodSuccessResponse("0.1", "0.2", "0.3"))
                .setHeader("Content-Type", "application/json"));

        provider.embed("人工智慧", EmbeddingProfile.ZH);

        RecordedRequest req = mockServer.takeRequest(1, TimeUnit.SECONDS);
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"" + MODEL_ZH + "\""), "Must include zh model name");
        assertTrue(body.contains("人工智慧"), "Must include the input text");
    }

    @Test
    void embed_en_sendsCorrectModel() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody(runpodSuccessResponse("0.5", "0.6"))
                .setHeader("Content-Type", "application/json"));

        provider.embed("machine learning", EmbeddingProfile.EN);

        RecordedRequest req = mockServer.takeRequest(1, TimeUnit.SECONDS);
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"" + MODEL_EN + "\""), "Must include en model name");
    }

    @Test
    void embed_sendsAuthorizationHeader() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody(runpodSuccessResponse("0.1", "0.2"))
                .setHeader("Content-Type", "application/json"));

        provider.embed("test", EmbeddingProfile.ZH);

        RecordedRequest req = mockServer.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("Bearer test-api-key", req.getHeader("Authorization"));
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
    void embed_500_throwsImmediately() {
        mockServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

        assertThrows(EmbeddingUnavailableException.class,
                () -> provider.embed("test", EmbeddingProfile.ZH));
        assertEquals(1, mockServer.getRequestCount(), "Must not retry on 500");
    }

    @Test
    void embed_emptyOutputData_throwsEmbeddingUnavailableException() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"test\",\"status\":\"COMPLETED\",\"output\":{\"data\":[]}}")
                .setHeader("Content-Type", "application/json"));

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
        RunPodEmbeddingProvider disabled = new RunPodEmbeddingProvider(
                "", "key", MODEL_ZH, MODEL_EN, 2000, objectMapper);
        assertFalse(disabled.isAvailable());
    }

    @Test
    void isAvailable_withNullUrl_returnsFalse() {
        RunPodEmbeddingProvider disabled = new RunPodEmbeddingProvider(
                null, "key", MODEL_ZH, MODEL_EN, 2000, objectMapper);
        assertFalse(disabled.isAvailable());
    }
}
