package com.example.podcastbackend.search.fusion;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

/**
 * Reciprocal Rank Fusion (RRF) implementation for combining search results.
 *
 * RRF score formula: score(d) = Σ 1 / (k + rank_i)
 * where k is the rank constant (default: 60)
 *
 * Reference: "Reciprocal Rank Fusion outperforms Condorcet and individual Rank Learning Methods"
 * (Cormack, Clarke, Büttcher, 2009)
 */
public class RrfFusion {

    private static final int DEFAULT_RANK_CONSTANT = 60;

    private final int rankConstant;

    public RrfFusion() {
        this(DEFAULT_RANK_CONSTANT);
    }

    public RrfFusion(int rankConstant) {
        this.rankConstant = rankConstant;
    }

    /**
     * Fuse two search results using RRF.
     *
     * @param bm25Response Results from BM25 search
     * @param knnResponse Results from kNN search
     * @param size Number of results to return
     * @return Fused results ordered by RRF score
     */
    public List<FusedResult> fuse(
            SearchResponse<JsonNode> bm25Response,
            SearchResponse<JsonNode> knnResponse,
            int size
    ) {
        Map<String, FusedResult> resultMap = new HashMap<>();
        Map<String, Double> rrfScores = new HashMap<>();

        // Process BM25 results
        List<Hit<JsonNode>> bm25Hits = bm25Response.hits().hits();
        for (int rank = 0; rank < bm25Hits.size(); rank++) {
            Hit<JsonNode> hit = bm25Hits.get(rank);
            String id = hit.id();
            double contribution = 1.0 / (rankConstant + rank + 1);

            rrfScores.merge(id, contribution, (a, b) -> a + b);
            resultMap.putIfAbsent(id, new FusedResult(id, hit));
        }

        // Process kNN results
        List<Hit<JsonNode>> knnHits = knnResponse.hits().hits();
        for (int rank = 0; rank < knnHits.size(); rank++) {
            Hit<JsonNode> hit = knnHits.get(rank);
            String id = hit.id();
            double contribution = 1.0 / (rankConstant + rank + 1);

            rrfScores.merge(id, contribution, (a, b) -> a + b);
            resultMap.putIfAbsent(id, new FusedResult(id, hit));
        }

        // Sort by RRF score and return top results
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(size)
                .map(entry -> {
                    FusedResult result = resultMap.get(entry.getKey());
                    return new FusedResult(result.id(), result.hit(), entry.getValue());
                })
                .toList();
    }

    /**
     * Result after RRF fusion with the original hit data.
     */
    public record FusedResult(
            String id,
            Hit<JsonNode> hit,
            double rrfScore
    ) {
        public FusedResult(String id, Hit<JsonNode> hit) {
            this(id, hit, 0.0);
        }
    }
}
