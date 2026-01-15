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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Component
public class ShowSearchQueryBuilder {

    private final Mustache mustache;

    public ShowSearchQueryBuilder(
            @Value("${search.show.template.path}") String templatePath
    ) throws IOException {

        MustacheFactory mf = new DefaultMustacheFactory();
        Reader reader = Files.newBufferedReader(Path.of(templatePath), StandardCharsets.UTF_8);
        this.mustache = mf.compile(reader, "search_shows");
    }

    public String build(ShowSearchRequest request) {

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("query", request.getQ());
        ctx.put("from", 0);
        ctx.put("size", request.getSize());

        if (request.getLanguage() != null && !request.getLanguage().isEmpty()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                String languagesJson = mapper.writeValueAsString(request.getLanguage());
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