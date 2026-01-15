package com.example.podcastbackend.search.query;

import com.example.podcastbackend.request.EpisodeSearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Component
public class EpisodeSearchQueryBuilder {

    private final Mustache mustache;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EpisodeSearchQueryBuilder(
            @Value("${search.episode.template.path}") String templatePath
    ) throws Exception {

        var mf = new DefaultMustacheFactory();
        var reader = Files.newBufferedReader(Path.of(templatePath), StandardCharsets.UTF_8);
        this.mustache = mf.compile(reader, "search_episodes");
    }

    public String build(EpisodeSearchRequest request) {
        Map<String, Object> ctx = new HashMap<>();

        ctx.put("query", request.getQ());
        ctx.put("from", request.from());
        ctx.put("size", request.getSize());

        if (request.sortByDate()) {
            ctx.put("sort_by_date", true);
        }

        if (request.getLanguage() != null && !request.getLanguage().isEmpty()) {
            try {
                ctx.put(
                        "languagesJson",
                        objectMapper.writeValueAsString(request.getLanguage())
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        StringWriter writer = new StringWriter();
        mustache.execute(writer, ctx);
        return writer.toString();
    }
}