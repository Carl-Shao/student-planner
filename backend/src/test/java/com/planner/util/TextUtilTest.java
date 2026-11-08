package com.planner.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TextUtilTest {
    @Test
    void bigramsIgnorePunctuationAndSpaces() {
        assertEquals(Set.of("你好", "好世", "世界"), TextUtil.bigrams("你好，世界"));
    }

    @Test
    void shortOrNullInput() {
        assertTrue(TextUtil.bigrams(null).isEmpty());
        assertEquals(Set.of("a"), TextUtil.bigrams("a"));
    }

    @Test
    void jaccard() {
        assertEquals(1.0, TextUtil.jaccard(TextUtil.bigrams("每天阅读半小时"), TextUtil.bigrams("每天阅读半小时")), 1e-9);
        assertEquals(0.0, TextUtil.jaccard(TextUtil.bigrams("数学"), TextUtil.bigrams("英语")), 1e-9);
        assertEquals(0.0, TextUtil.jaccard(Set.of(), Set.of("ab")), 1e-9);
        double near = TextUtil.jaccard(TextUtil.bigrams("每天坚持阅读三十分钟"), TextUtil.bigrams("每天坚持阅读半小时"));
        assertTrue(near > 0.3 && near < 1.0);
    }
}
