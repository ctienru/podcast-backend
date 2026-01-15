package com.example.podcastbackend.search.client;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.io.StringReader;

@Component
public class ElasticsearchSearchClient {

    private final ElasticsearchClient client;

    public ElasticsearchSearchClient(ElasticsearchClient client) {
        this.client = client;
    }

    public SearchResponse<JsonNode> search(String index, String queryJson) {
        try {
            return client.search(s -> s
                            .index(index)
                            .withJson(new StringReader(queryJson)),
                    JsonNode.class
            );
        } catch (Exception e) {
            throw new RuntimeException("Elasticsearch search failed", e);
        }
    }
}