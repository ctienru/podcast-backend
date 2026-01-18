package com.example.podcastbackend.search.query;

import com.example.podcastbackend.request.ShowSearchRequest;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class ShowSearchQueryBuilder {

    private final Mustache mustache;
    private final ObjectMapper objectMapper;

    public ShowSearchQueryBuilder(
            @Value("${search.show.template.path}") String templatePath,
            ObjectMapper objectMapper
    ) throws IOException {
        this.objectMapper = objectMapper;

        MustacheFactory mf = new DefaultMustacheFactory();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(templatePath);
        if (inputStream == null) {
            throw new IOException("Template not found in classpath: " + templatePath);
        }
        Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        this.mustache = mf.compile(reader, "search_shows");
    }

    public String build(ShowSearchRequest request) {

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("query", request.getQ());

        // 計算分頁的 from 值
        int page = request.getPage() != null ? request.getPage() : 1;
        int size = request.getSize() != null ? request.getSize() : 10;
        int from = (page - 1) * size;

        ctx.put("from", from);
        ctx.put("size", size);

        if (request.getLanguage() != null && !request.getLanguage().isEmpty()) {
            try {
                String languagesJson = objectMapper.writeValueAsString(request.getLanguage());
                ctx.put("languagesJson", languagesJson);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize languages", e);
            }
        }

        StringWriter writer = new StringWriter();
        mustache.execute(writer, ctx);
        return writer.toString();
    }
}