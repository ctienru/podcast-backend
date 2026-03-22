package com.example.podcastbackend.search;

import com.example.podcastbackend.exception.InvalidLangParamException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LangParamTest {

    @Test
    void fromString_zhTw_returnsZH_TW() {
        assertEquals(LangParam.ZH_TW, LangParam.fromString("zh-tw"));
    }

    @Test
    void fromString_zhCn_returnsZH_CN() {
        assertEquals(LangParam.ZH_CN, LangParam.fromString("zh-cn"));
    }

    @Test
    void fromString_en_returnsEN() {
        assertEquals(LangParam.EN, LangParam.fromString("en"));
    }

    @Test
    void fromString_zhBoth_returnsZH_BOTH() {
        assertEquals(LangParam.ZH_BOTH, LangParam.fromString("zh-both"));
    }

    @Test
    void fromString_null_returnsNull() {
        assertNull(LangParam.fromString(null));
    }

    @Test
    void fromString_invalid_throwsInvalidLangParamException() {
        assertThrows(InvalidLangParamException.class, () -> LangParam.fromString("fr"));
    }

    @Test
    void getValue_returnsCorrectString() {
        assertEquals("zh-tw", LangParam.ZH_TW.getValue());
        assertEquals("zh-cn", LangParam.ZH_CN.getValue());
        assertEquals("en", LangParam.EN.getValue());
        assertEquals("zh-both", LangParam.ZH_BOTH.getValue());
    }
}
