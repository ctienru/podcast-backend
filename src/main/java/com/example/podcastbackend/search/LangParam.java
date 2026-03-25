package com.example.podcastbackend.search;

import com.example.podcastbackend.exception.InvalidLangParamException;

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
     * Throws InvalidLangParamException for unrecognised values.
     */
    public static LangParam fromString(String value) {
        if (value == null) return null;
        for (LangParam param : values()) {
            if (param.value.equals(value)) {
                return param;
            }
        }
        throw new InvalidLangParamException(
            "Invalid lang parameter: " + value + ". Allowed: zh-tw, zh-cn, en, zh-both"
        );
    }
}
