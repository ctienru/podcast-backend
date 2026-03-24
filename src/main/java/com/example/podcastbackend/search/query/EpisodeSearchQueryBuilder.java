package com.example.podcastbackend.search.query;

import com.example.podcastbackend.request.EpisodeSearchRequest;
import com.example.podcastbackend.search.LangParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.TemplateFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

@Component
public class EpisodeSearchQueryBuilder {

    private final Map<LangParam, Mustache> templates;
    private final LangParam defaultLang;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EpisodeSearchQueryBuilder(
            @Value("${search.episode.template.zh-tw.path:podcast-spec/es/search_episodes_zh_tw/query.template.mustache}") String zhTwPath,
            @Value("${search.episode.template.zh-cn.path:podcast-spec/es/search_episodes_zh_cn/query.template.mustache}") String zhCnPath,
            @Value("${search.episode.template.en.path:podcast-spec/es/search_episodes_en/query.template.mustache}") String enPath,
            @Value("${search.default-lang:en}") String defaultLangStr
    ) throws IOException {
        this.templates = new EnumMap<>(LangParam.class);
        this.templates.put(LangParam.ZH_TW, loadTemplate(zhTwPath, "zh-tw"));
        this.templates.put(LangParam.ZH_CN, loadTemplate(zhCnPath, "zh-cn"));
        this.templates.put(LangParam.EN, loadTemplate(enPath, "en"));
        LangParam parsed = LangParam.fromString(defaultLangStr);
        this.defaultLang = (parsed != null && parsed != LangParam.ZH_BOTH) ? parsed : LangParam.ZH_TW;
    }

    private Mustache loadTemplate(String path, String name) throws IOException {
        var mf = new DefaultMustacheFactory();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path);
        if (inputStream == null) {
            throw new IOException("Template not found in classpath: " + path);
        }
        return mf.compile(new InputStreamReader(inputStream, StandardCharsets.UTF_8), name);
    }

    /**
     * Selects the language-specific template.
     * null and zh-both fall back to defaultLang (from search.default-lang config).
     */
    private Mustache selectTemplate(String lang) {
        LangParam param = LangParam.fromString(lang);
        if (param == null || param == LangParam.ZH_BOTH) {
            return templates.get(defaultLang);
        }
        return templates.get(param);
    }

    /** BM25 query for standard search. */
    public String buildBm25Query(EpisodeSearchRequest request) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("query", request.getQ());
        ctx.put("from", request.from());
        ctx.put("size", request.getSize());
        if (request.sortByDate()) {
            ctx.put("sort_by_date", true);
        }
        return render(selectTemplate(request.getLang()), ctx);
    }

    /** KNN-only query for semantic search. */
    public String buildKnnQuery(EpisodeSearchRequest request, float[] queryVector) {
        Map<String, Object> ctx = buildKnnContext(queryVector, request.getSize());
        ctx.put("from", request.from());
        ctx.put("mode_hybrid", true);
        ctx.put("mode_knn", true);
        return render(selectTemplate(request.getLang()), ctx);
    }

    /** BM25 query with larger window size for RRF fusion. */
    public String buildBm25QueryForHybrid(EpisodeSearchRequest request, int windowSize) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("query", request.getQ());
        ctx.put("from", 0);
        ctx.put("size", windowSize);
        return render(selectTemplate(request.getLang()), ctx);
    }

    /** KNN-only query with larger window size for RRF fusion. */
    public String buildKnnQueryForHybrid(String lang, float[] queryVector, int windowSize) {
        Map<String, Object> ctx = buildKnnContext(queryVector, windowSize);
        ctx.put("from", 0);
        ctx.put("mode_hybrid", true);
        ctx.put("mode_knn", true);
        return render(selectTemplate(lang), ctx);
    }

    /** Exact phrase match query. */
    public String buildExactQuery(EpisodeSearchRequest request) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("query", request.getQ());
        ctx.put("from", request.from());
        ctx.put("size", request.getSize());
        ctx.put("mode_exact", true);
        return render(selectTemplate(request.getLang()), ctx);
    }

    private Map<String, Object> buildKnnContext(float[] queryVector, int size) {
        Map<String, Object> ctx = new HashMap<>();
        try {
            ctx.put("queryVector", objectMapper.writeValueAsString(queryVector));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize query vector", e);
        }
        ctx.put("toJson", (TemplateFunction) input -> input);
        ctx.put("size", size);
        return ctx;
    }

    private String render(Mustache template, Map<String, Object> ctx) {
        StringWriter writer = new StringWriter();
        template.execute(writer, ctx);
        return writer.toString();
    }
}
