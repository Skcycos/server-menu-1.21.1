package com.tanrunn.servermenu.server.integration.summary;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AppCardSummary} 纯逻辑测试：集合防御性复制、最多 3 行、每行最多
 * 96 字符、null 与控制换行清理、empty()。
 */
class AppCardSummaryTest {

    @Test
    void linesAreDefensivelyCopiedAndImmutable() {
        List<String> source = new ArrayList<>(List.of("一行", "二行"));
        AppCardSummary summary = new AppCardSummary(source);
        source.add("外部修改");
        assertEquals(2, summary.lines().size());
        assertThrows(UnsupportedOperationException.class, () -> summary.lines().add("x"));
    }

    @Test
    void atMostThreeLines() {
        AppCardSummary summary = new AppCardSummary(List.of("1", "2", "3", "4", "5"));
        assertEquals(3, summary.lines().size());
        assertEquals(List.of("1", "2", "3"), summary.lines());
    }

    @Test
    void eachLineAtMostNinetySixChars() {
        String longLine = "长".repeat(200);
        AppCardSummary summary = new AppCardSummary(List.of(longLine));
        assertEquals(1, summary.lines().size());
        assertEquals(96, summary.lines().get(0).length());
    }

    @Test
    void nullLinesAreDropped() {
        AppCardSummary summary = new AppCardSummary(java.util.Arrays.asList("a", null, "b"));
        assertEquals(List.of("a", "b"), summary.lines());
    }

    @Test
    void emptyLinesAreDropped() {
        AppCardSummary summary = new AppCardSummary(List.of("a", "", "b"));
        assertEquals(List.of("a", "b"), summary.lines());
    }

    @Test
    void controlCharactersAreNormalizedToSpaces() {
        AppCardSummary summary = new AppCardSummary(List.of("甲\r\n乙\t丙\u0001丁"));
        assertEquals("甲  乙 丙 丁", summary.lines().get(0));
    }

    @Test
    void nullListYieldsEmpty() {
        AppCardSummary summary = new AppCardSummary(null);
        assertTrue(summary.isEmpty());
        assertEquals(0, summary.lines().size());
    }

    @Test
    void emptyFactoryIsEmpty() {
        assertTrue(AppCardSummary.empty().isEmpty());
        assertEquals(0, AppCardSummary.empty().lines().size());
    }
}
