package com.example.podcastbackend.log;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.podcastbackend.request.ClickLogRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Writes click-log entries to the {@code click-logs} Elasticsearch index
 * asynchronously. Failures are silent-dropped (logged + metric).
 */
@Service
public class ClickLogService {

    private static final Logger log = LoggerFactory.getLogger(ClickLogService.class);
    private static final String INDEX = "click-logs";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ElasticsearchClient esClient;
    private final MeterRegistry meterRegistry;

    public ClickLogService(ElasticsearchClient esClient, MeterRegistry meterRegistry) {
        this.esClient = esClient;
        this.meterRegistry = meterRegistry;
    }

    @Async("logTaskExecutor")
    public void logClick(ClickLogRequest request) {
        try {
            String documentJson = toJson(request);
            esClient.index(i -> i
                    .index(INDEX)
                    .withJson(new StringReader(documentJson))
            );
        } catch (Exception e) {
            log.warn("click_log_failed",
                    kv("request_id", request.getRequestId()),
                    kv("error", e.getMessage()));
            meterRegistry.counter("click_log.write.failure").increment();
        }
    }

    private String toJson(ClickLogRequest request) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("request_id", request.getRequestId());
        doc.put("timestamp", request.getTimestamp());
        doc.put("query", request.getQuery());
        doc.put("selected_lang", request.getSelectedLang());
        doc.put("clicked_episode_id", request.getClickedEpisodeId());
        doc.put("clicked_rank", request.getClickedRank());
        doc.put("clicked_language", request.getClickedLanguage());
        doc.put("time_to_click_sec", request.getTimeToClickSec());
        try {
            return MAPPER.writeValueAsString(doc);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize ClickLogRequest", e);
        }
    }
}
