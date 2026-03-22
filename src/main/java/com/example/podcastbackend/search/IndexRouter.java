package com.example.podcastbackend.search;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IndexRouter {

    private final String zhTwIndex;
    private final String zhCnIndex;
    private final String enIndex;
    private final String defaultLang;

    public IndexRouter(
            @Value("${elasticsearch.indices.episodes.zh-tw:episodes-zh-tw}") String zhTwIndex,
            @Value("${elasticsearch.indices.episodes.zh-cn:episodes-zh-cn}") String zhCnIndex,
            @Value("${elasticsearch.indices.episodes.en:episodes-en}") String enIndex,
            @Value("${search.default-lang:zh-tw}") String defaultLang
    ) {
        this.zhTwIndex = zhTwIndex;
        this.zhCnIndex = zhCnIndex;
        this.enIndex = enIndex;
        this.defaultLang = defaultLang;
    }

    /**
     * Resolves a single index alias for the given lang.
     * Throws for zh-both — use resolveIndices() instead.
     */
    public String resolveIndex(String lang) {
        return switch (LangParam.fromString(lang != null ? lang : defaultLang)) {
            case ZH_TW -> zhTwIndex;
            case ZH_CN -> zhCnIndex;
            case EN -> enIndex;
            case ZH_BOTH -> throw new IllegalArgumentException(
                "zh-both requires multi-index resolution, use resolveIndices()");
        };
    }

    /**
     * Resolves one or two index aliases for the given lang.
     * zh-both returns both zh-tw and zh-cn indices.
     */
    public List<String> resolveIndices(String lang) {
        if (LangParam.ZH_BOTH == LangParam.fromString(lang)) {
            return List.of(zhTwIndex, zhCnIndex);
        }
        return List.of(resolveIndex(lang));
    }

    public boolean isCrossIndex(String lang) {
        return LangParam.ZH_BOTH == LangParam.fromString(lang);
    }
}
