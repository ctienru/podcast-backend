package com.example.podcastbackend.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.StringReader;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Creates the {@code query-logs} and {@code click-logs} Elasticsearch indices
 * on startup if they do not already exist.
 *
 * Failures are caught and logged as warnings — they must not prevent the
 * application from starting (e.g. when ES is unavailable during local tests).
 */
@Component
public class ElasticsearchInitializer {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchInitializer.class);

    private static final String QUERY_LOGS_MAPPING = """
            {
              "mappings": {
                "properties": {
                  "request_id":       { "type": "keyword" },
                  "timestamp":        { "type": "date" },
                  "query":            { "type": "text" },
                  "query_lang":       { "type": "keyword" },
                  "selected_lang":    { "type": "keyword" },
                  "mode":             { "type": "keyword" },
                  "target_index":     { "type": "keyword" },
                  "is_cross_lang":    { "type": "boolean" },
                  "result_count":     { "type": "integer" },
                  "result_ids":       { "type": "keyword" },
                  "result_languages": { "type": "keyword" },
                  "page":             { "type": "integer" },
                  "latency_ms":       { "type": "integer" },
                  "was_degraded":     { "type": "boolean" },
                  "degradation_reason": { "type": "keyword" }
                }
              }
            }
            """;

    private static final String CLICK_LOGS_MAPPING = """
            {
              "mappings": {
                "properties": {
                  "request_id":         { "type": "keyword" },
                  "timestamp":          { "type": "date" },
                  "query":              { "type": "text" },
                  "selected_lang":      { "type": "keyword" },
                  "clicked_episode_id": { "type": "keyword" },
                  "clicked_rank":       { "type": "integer" },
                  "clicked_language":   { "type": "keyword" },
                  "time_to_click_sec":  { "type": "integer" }
                }
              }
            }
            """;

    private final ElasticsearchClient esClient;

    public ElasticsearchInitializer(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    @PostConstruct
    public void init() {
        createIndexIfAbsent("query-logs", QUERY_LOGS_MAPPING);
        createIndexIfAbsent("click-logs", CLICK_LOGS_MAPPING);
    }

    private void createIndexIfAbsent(String indexName, String mappingJson) {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(indexName)).value();
            if (!exists) {
                esClient.indices().create(c -> c
                        .index(indexName)
                        .withJson(new StringReader(mappingJson))
                );
                log.info("index_created", kv("index", indexName));
            } else {
                log.debug("index_already_exists", kv("index", indexName));
            }
        } catch (Exception e) {
            log.warn("index_init_failed",
                    kv("index", indexName),
                    kv("error", e.getMessage()));
        }
    }
}
