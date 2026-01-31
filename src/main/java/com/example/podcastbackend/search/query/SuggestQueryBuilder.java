package com.example.podcastbackend.search.query;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class SuggestQueryBuilder {

    private final Mustache showsMustache;
    private final Mustache episodesMustache;

    public SuggestQueryBuilder() throws IOException {
        MustacheFactory mf = new DefaultMustacheFactory();

        // Load shows suggest template
        InputStream showsStream = getClass().getClassLoader()
                .getResourceAsStream("podcast-spec/es/suggest/shows.query.template.mustache");
        if (showsStream == null) {
            throw new IOException("Shows suggest template not found in classpath");
        }
        Reader showsReader = new InputStreamReader(showsStream, StandardCharsets.UTF_8);
        this.showsMustache = mf.compile(showsReader, "suggest_shows");

        // Load episodes suggest template
        InputStream episodesStream = getClass().getClassLoader()
                .getResourceAsStream("podcast-spec/es/suggest/episodes.query.template.mustache");
        if (episodesStream == null) {
            throw new IOException("Episodes suggest template not found in classpath");
        }
        Reader episodesReader = new InputStreamReader(episodesStream, StandardCharsets.UTF_8);
        this.episodesMustache = mf.compile(episodesReader, "suggest_episodes");
    }

    public String buildShowSuggestQuery(String query, int limit) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("query", query);
        ctx.put("limit", limit);

        StringWriter writer = new StringWriter();
        showsMustache.execute(writer, ctx);
        return writer.toString();
    }

    public String buildEpisodeSuggestQuery(String query, int limit) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("query", query);
        ctx.put("limit", limit);

        StringWriter writer = new StringWriter();
        episodesMustache.execute(writer, ctx);
        return writer.toString();
    }
}
