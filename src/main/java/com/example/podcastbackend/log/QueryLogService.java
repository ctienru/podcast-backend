package com.example.podcastbackend.log;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.StringReader;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Writes query-log entries to the {@code query-logs} Elasticsearch index
 * asynchronously. Failures are silent-dropped (logged + metric) so they
 * never block the search response.
 */
@Service
public class QueryLogService {

    private static final Logger log = LoggerFactory.getLogger(QueryLogService.class);
    private static final String INDEX = "query-logs";

    private final ElasticsearchClient esClient;
    private final MeterRegistry meterRegistry;

    public QueryLogService(ElasticsearchClient esClient, MeterRegistry meterRegistry) {
        this.esClient = esClient;
        this.meterRegistry = meterRegistry;
    }

    @Async("logTaskExecutor")
    public void logQuery(QueryLogEntry entry) {
        try {
            esClient.index(i -> i
                    .index(INDEX)
                    .withJson(new StringReader(entry.toJson()))
            );
        } catch (Exception e) {
            log.warn("query_log_failed",
                    kv("request_id", entry.requestId()),
                    kv("error", e.getMessage()));
            meterRegistry.counter("query_log.write.failure").increment();
        }
    }
}
