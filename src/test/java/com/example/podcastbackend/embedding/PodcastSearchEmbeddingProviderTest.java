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

class PodcastSearchEmbeddingProviderTest {

    private MockWebServer mockServer;
    private PodcastSearchEmbeddingProvider provider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        provider = new PodcastSearchEmbeddingProvider(
                mockServer.url("/").toString().replaceAll("/$", ""),
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

    @Test
    void embed_zh_sendsCorrectRequestFormat() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"embeddings\":[[0.1,0.2,0.3]],\"model\":\"test\",\"dimensions\":3}")
                .setHeader("Content-Type", "application/json"));

        float[] result = provider.embed("人工智慧", EmbeddingProfile.ZH);

        RecordedRequest req = mockServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(req);
        assertEquals("POST", req.getMethod());
        assertEquals("/embed", req.getPath());

        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"texts\""));
        assertTrue(body.contains("人工智慧"));
        assertTrue(body.contains("\"language\""));
        assertTrue(body.contains("zh-tw"));

        assertEquals(3, result.length);
        assertEquals(0.1f, result[0], 0.001f);
    }

    @Test
    void embed_en_sendsEnLanguage() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"embeddings\":[[0.5,0.6]],\"model\":\"test\",\"dimensions\":2}")
                .setHeader("Content-Type", "application/json"));

        provider.embed("machine learning", EmbeddingProfile.EN);

        RecordedRequest req = mockServer.takeRequest(1, TimeUnit.SECONDS);
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"en\""));
    }

    @Test
    void embed_nonOkStatus_throwsEmbeddingUnavailableException() {
        mockServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

        assertThrows(EmbeddingUnavailableException.class,
                () -> provider.embed("test", EmbeddingProfile.ZH));
    }

    @Test
    void embed_emptyEmbeddings_throwsEmbeddingUnavailableException() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"embeddings\":[],\"model\":\"test\",\"dimensions\":0}")
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
}
