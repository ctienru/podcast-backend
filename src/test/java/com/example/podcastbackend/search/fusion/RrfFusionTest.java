package com.example.podcastbackend.search.fusion;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RrfFusionTest {

    private RrfFusion rrfFusion;

    @BeforeEach
    void setUp() {
        rrfFusion = new RrfFusion(); // default rank constant = 60
    }

    @Nested
    class ScoreCalculation {

        @Test
        void singleDocumentInBm25_calculatesCorrectScore() {
            // rank 0 -> score = 1 / (60 + 0 + 1) = 1/61
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of(
                    createMockHit("doc1")
            ));
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of());

            List<RrfFusion.FusedResult> results = rrfFusion.fuse(bm25Response, knnResponse, 10);

            assertEquals(1, results.size());
            assertEquals("doc1", results.get(0).id());
            assertEquals(1.0 / 61, results.get(0).rrfScore(), 0.0001);
        }

        @Test
        void singleDocumentInKnn_calculatesCorrectScore() {
            // rank 0 -> score = 1 / (60 + 0 + 1) = 1/61
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of());
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of(
                    createMockHit("doc1")
            ));

            List<RrfFusion.FusedResult> results = rrfFusion.fuse(bm25Response, knnResponse, 10);

            assertEquals(1, results.size());
            assertEquals("doc1", results.get(0).id());
            assertEquals(1.0 / 61, results.get(0).rrfScore(), 0.0001);
        }

        @Test
        void documentInBothResults_sumsBothContributions() {
            // BM25 rank 0: 1/61, kNN rank 0: 1/61 -> total = 2/61
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of(
                    createMockHit("doc1")
            ));
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of(
                    createMockHit("doc1")
            ));

            List<RrfFusion.FusedResult> results = rrfFusion.fuse(bm25Response, knnResponse, 10);

            assertEquals(1, results.size());
            assertEquals("doc1", results.get(0).id());
            assertEquals(2.0 / 61, results.get(0).rrfScore(), 0.0001);
        }

        @Test
        void multipleDocuments_calculatesScoresBasedOnRank() {
            // BM25: doc1 rank 0 (1/61), doc2 rank 1 (1/62)
            // kNN: doc2 rank 0 (1/61), doc3 rank 1 (1/62)
            // Expected: doc2 = 1/62 + 1/61, doc1 = 1/61, doc3 = 1/62
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of(
                    createMockHit("doc1"),
                    createMockHit("doc2")
            ));
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of(
                    createMockHit("doc2"),
                    createMockHit("doc3")
            ));

            List<RrfFusion.FusedResult> results = rrfFusion.fuse(bm25Response, knnResponse, 10);

            assertEquals(3, results.size());

            // doc2 should be first (highest score: 1/61 + 1/62)
            assertEquals("doc2", results.get(0).id());
            double expectedDoc2Score = (1.0 / 61) + (1.0 / 62);
            assertEquals(expectedDoc2Score, results.get(0).rrfScore(), 0.0001);

            // doc1 should be second (1/61)
            assertEquals("doc1", results.get(1).id());
            assertEquals(1.0 / 61, results.get(1).rrfScore(), 0.0001);

            // doc3 should be third (1/62)
            assertEquals("doc3", results.get(2).id());
            assertEquals(1.0 / 62, results.get(2).rrfScore(), 0.0001);
        }
    }

    @Nested
    class ResultOrdering {

        @Test
        void resultsAreOrderedByScoreDescending() {
            // Create scenario where kNN top result beats BM25 top result
            // BM25: doc1 rank 0, doc2 rank 1
            // kNN: doc3 rank 0, doc2 rank 1
            // doc2 appears in both -> highest score
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of(
                    createMockHit("doc1"),
                    createMockHit("doc2")
            ));
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of(
                    createMockHit("doc3"),
                    createMockHit("doc2")
            ));

            List<RrfFusion.FusedResult> results = rrfFusion.fuse(bm25Response, knnResponse, 10);

            // doc2 has highest combined score
            assertEquals("doc2", results.get(0).id());

            // Verify descending order
            for (int i = 0; i < results.size() - 1; i++) {
                assertTrue(results.get(i).rrfScore() >= results.get(i + 1).rrfScore(),
                        "Results should be ordered by score descending");
            }
        }
    }

    @Nested
    class SizeLimiting {

        @Test
        void limitsResultsToRequestedSize() {
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of(
                    createMockHit("doc1"),
                    createMockHit("doc2"),
                    createMockHit("doc3")
            ));
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of(
                    createMockHit("doc4"),
                    createMockHit("doc5"),
                    createMockHit("doc6")
            ));

            List<RrfFusion.FusedResult> results = rrfFusion.fuse(bm25Response, knnResponse, 2);

            assertEquals(2, results.size());
        }

        @Test
        void returnsAllResultsWhenFewerThanSize() {
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of(
                    createMockHit("doc1")
            ));
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of(
                    createMockHit("doc2")
            ));

            List<RrfFusion.FusedResult> results = rrfFusion.fuse(bm25Response, knnResponse, 100);

            assertEquals(2, results.size());
        }

        @Test
        void returnsTopScoringDocumentsWhenLimited() {
            // doc1 appears in both (rank 0) -> highest score
            // Other docs only appear once
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of(
                    createMockHit("doc1"),
                    createMockHit("doc2"),
                    createMockHit("doc3")
            ));
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of(
                    createMockHit("doc1"),
                    createMockHit("doc4"),
                    createMockHit("doc5")
            ));

            List<RrfFusion.FusedResult> results = rrfFusion.fuse(bm25Response, knnResponse, 1);

            assertEquals(1, results.size());
            assertEquals("doc1", results.get(0).id()); // highest score (2/61)
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void bothResultsEmpty_returnsEmptyList() {
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of());
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of());

            List<RrfFusion.FusedResult> results = rrfFusion.fuse(bm25Response, knnResponse, 10);

            assertTrue(results.isEmpty());
        }

        @Test
        void onlyBm25HasResults_returnsThoseResults() {
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of(
                    createMockHit("doc1"),
                    createMockHit("doc2")
            ));
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of());

            List<RrfFusion.FusedResult> results = rrfFusion.fuse(bm25Response, knnResponse, 10);

            assertEquals(2, results.size());
            assertEquals("doc1", results.get(0).id());
            assertEquals("doc2", results.get(1).id());
        }

        @Test
        void onlyKnnHasResults_returnsThoseResults() {
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of());
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of(
                    createMockHit("doc1"),
                    createMockHit("doc2")
            ));

            List<RrfFusion.FusedResult> results = rrfFusion.fuse(bm25Response, knnResponse, 10);

            assertEquals(2, results.size());
            assertEquals("doc1", results.get(0).id());
            assertEquals("doc2", results.get(1).id());
        }

        @Test
        void noOverlap_combinesAllUniqueDocuments() {
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of(
                    createMockHit("bm25-doc1"),
                    createMockHit("bm25-doc2")
            ));
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of(
                    createMockHit("knn-doc1"),
                    createMockHit("knn-doc2")
            ));

            List<RrfFusion.FusedResult> results = rrfFusion.fuse(bm25Response, knnResponse, 10);

            assertEquals(4, results.size());

            // All rank-0 docs have same score (1/61), rank-1 docs have same score (1/62)
            // Top 2 should be the rank-0 docs (either order is fine)
            List<String> topTwoIds = results.subList(0, 2).stream()
                    .map(RrfFusion.FusedResult::id)
                    .toList();
            assertTrue(topTwoIds.contains("bm25-doc1") || topTwoIds.contains("knn-doc1"));
        }

        @Test
        void sizeZero_returnsEmptyList() {
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of(
                    createMockHit("doc1")
            ));
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of(
                    createMockHit("doc2")
            ));

            List<RrfFusion.FusedResult> results = rrfFusion.fuse(bm25Response, knnResponse, 0);

            assertTrue(results.isEmpty());
        }
    }

    @Nested
    class CustomRankConstant {

        @Test
        void customRankConstant_affectsScoreCalculation() {
            RrfFusion customFusion = new RrfFusion(10); // k = 10

            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of(
                    createMockHit("doc1")
            ));
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of());

            List<RrfFusion.FusedResult> results = customFusion.fuse(bm25Response, knnResponse, 10);

            // With k=10: rank 0 -> 1 / (10 + 0 + 1) = 1/11
            assertEquals(1.0 / 11, results.get(0).rrfScore(), 0.0001);
        }

        @Test
        void differentRankConstants_produceDifferentScores() {
            RrfFusion fusion60 = new RrfFusion(60);
            RrfFusion fusion10 = new RrfFusion(10);

            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of(
                    createMockHit("doc1")
            ));
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of());

            double score60 = fusion60.fuse(bm25Response, knnResponse, 10).get(0).rrfScore();
            double score10 = fusion10.fuse(bm25Response, knnResponse, 10).get(0).rrfScore();

            assertNotEquals(score60, score10);
            assertTrue(score10 > score60, "Lower k should produce higher scores");
        }
    }

    @Nested
    class HitDataPreservation {

        @Test
        void preservesHitFromBm25WhenDocumentOnlyInBm25() {
            Hit<JsonNode> bm25Hit = createMockHit("doc1");
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of(bm25Hit));
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of());

            List<RrfFusion.FusedResult> results = rrfFusion.fuse(bm25Response, knnResponse, 10);

            assertSame(bm25Hit, results.get(0).hit());
        }

        @Test
        void preservesHitFromKnnWhenDocumentOnlyInKnn() {
            Hit<JsonNode> knnHit = createMockHit("doc1");
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of());
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of(knnHit));

            List<RrfFusion.FusedResult> results = rrfFusion.fuse(bm25Response, knnResponse, 10);

            assertSame(knnHit, results.get(0).hit());
        }

        @Test
        void preservesFirstSeenHitWhenDocumentInBoth() {
            // When document appears in both, BM25 hit should be preserved (processed first)
            Hit<JsonNode> bm25Hit = createMockHit("doc1");
            Hit<JsonNode> knnHit = createMockHit("doc1");
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of(bm25Hit));
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of(knnHit));

            List<RrfFusion.FusedResult> results = rrfFusion.fuse(bm25Response, knnResponse, 10);

            assertSame(bm25Hit, results.get(0).hit());
        }
    }

    @Nested
    class RealWorldScenarios {

        @Test
        void typicalHybridSearch_ranksOverlappingDocumentsHigher() {
            // Simulate: BM25 finds keyword matches, kNN finds semantic matches
            // Documents appearing in both should rank highest
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of(
                    createMockHit("exact-match"),      // rank 0
                    createMockHit("keyword-only-1"),  // rank 1
                    createMockHit("keyword-only-2")   // rank 2
            ));
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of(
                    createMockHit("semantic-only-1"), // rank 0
                    createMockHit("exact-match"),     // rank 1
                    createMockHit("semantic-only-2")  // rank 2
            ));

            List<RrfFusion.FusedResult> results = rrfFusion.fuse(bm25Response, knnResponse, 10);

            // "exact-match" should be first because it appears in both result sets
            assertEquals("exact-match", results.get(0).id());

            // Its score should be sum of contributions from both lists
            double expectedScore = (1.0 / 61) + (1.0 / 62); // BM25 rank 0 + kNN rank 1
            assertEquals(expectedScore, results.get(0).rrfScore(), 0.0001);
        }

        @Test
        void pagination_consistentResultsWithDifferentSizes() {
            SearchResponse<JsonNode> bm25Response = createMockResponse(List.of(
                    createMockHit("doc1"),
                    createMockHit("doc2"),
                    createMockHit("doc3")
            ));
            SearchResponse<JsonNode> knnResponse = createMockResponse(List.of(
                    createMockHit("doc1"),
                    createMockHit("doc4"),
                    createMockHit("doc5")
            ));

            List<RrfFusion.FusedResult> resultsSize5 = rrfFusion.fuse(bm25Response, knnResponse, 5);
            List<RrfFusion.FusedResult> resultsSize3 = rrfFusion.fuse(bm25Response, knnResponse, 3);

            // First 3 results should be the same regardless of size limit
            for (int i = 0; i < 3; i++) {
                assertEquals(resultsSize5.get(i).id(), resultsSize3.get(i).id());
                assertEquals(resultsSize5.get(i).rrfScore(), resultsSize3.get(i).rrfScore(), 0.0001);
            }
        }
    }

    // Helper methods

    @SuppressWarnings("unchecked")
    private SearchResponse<JsonNode> createMockResponse(List<Hit<JsonNode>> hits) {
        SearchResponse<JsonNode> response = mock(SearchResponse.class);
        HitsMetadata<JsonNode> hitsMetadata = mock(HitsMetadata.class);
        when(hitsMetadata.hits()).thenReturn(hits);
        when(response.hits()).thenReturn(hitsMetadata);
        return response;
    }

    @SuppressWarnings("unchecked")
    private Hit<JsonNode> createMockHit(String id) {
        Hit<JsonNode> hit = mock(Hit.class);
        when(hit.id()).thenReturn(id);
        return hit;
    }
}
