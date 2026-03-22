package com.example.podcastbackend.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndexRouterTest {

    private IndexRouter router;

    @BeforeEach
    void setUp() {
        router = new IndexRouter(
                "episodes-zh-tw",
                "episodes-zh-cn",
                "episodes-en",
                "zh-tw"
        );
    }

    // --- resolveIndex ---

    @Test
    void resolveIndex_zhTw_returnsZhTwIndex() {
        assertEquals("episodes-zh-tw", router.resolveIndex("zh-tw"));
    }

    @Test
    void resolveIndex_zhCn_returnsZhCnIndex() {
        assertEquals("episodes-zh-cn", router.resolveIndex("zh-cn"));
    }

    @Test
    void resolveIndex_en_returnsEnIndex() {
        assertEquals("episodes-en", router.resolveIndex("en"));
    }

    @Test
    void resolveIndex_null_usesDefaultLang() {
        // defaultLang = "zh-tw"
        assertEquals("episodes-zh-tw", router.resolveIndex(null));
    }

    @Test
    void resolveIndex_zhBoth_throws() {
        assertThrows(IllegalArgumentException.class, () -> router.resolveIndex("zh-both"));
    }

    // --- resolveIndices ---

    @Test
    void resolveIndices_zhBoth_returnsBothIndices() {
        List<String> indices = router.resolveIndices("zh-both");
        assertEquals(2, indices.size());
        assertTrue(indices.contains("episodes-zh-tw"));
        assertTrue(indices.contains("episodes-zh-cn"));
    }

    @Test
    void resolveIndices_zhTw_returnsSingleElement() {
        assertEquals(List.of("episodes-zh-tw"), router.resolveIndices("zh-tw"));
    }

    @Test
    void resolveIndices_en_returnsSingleElement() {
        assertEquals(List.of("episodes-en"), router.resolveIndices("en"));
    }

    // --- isCrossIndex ---

    @Test
    void isCrossIndex_zhBoth_returnsTrue() {
        assertTrue(router.isCrossIndex("zh-both"));
    }

    @Test
    void isCrossIndex_zhTw_returnsFalse() {
        assertFalse(router.isCrossIndex("zh-tw"));
    }

    @Test
    void isCrossIndex_en_returnsFalse() {
        assertFalse(router.isCrossIndex("en"));
    }
}
