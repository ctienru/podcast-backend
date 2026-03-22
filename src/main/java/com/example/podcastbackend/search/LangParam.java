package com.example.podcastbackend.search;

public enum LangParam {
    ZH_TW("zh-tw"),
    ZH_CN("zh-cn"),
    EN("en"),
    ZH_BOTH("zh-both");

    private final String value;

    LangParam(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Converts a string to LangParam.
     * Returns null for null input.
     * Throws IllegalArgumentException for unrecognised values
     * (will be replaced with InvalidLangParamException in Batch 3).
     */
    public static LangParam fromString(String value) {
        if (value == null) return null;
        for (LangParam param : values()) {
            if (param.value.equals(value)) {
                return param;
            }
        }
        throw new IllegalArgumentException(
            "Invalid lang parameter: " + value + ". Allowed: zh-tw, zh-cn, en, zh-both"
        );
    }
}
