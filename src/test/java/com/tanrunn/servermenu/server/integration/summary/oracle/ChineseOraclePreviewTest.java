package com.tanrunn.servermenu.server.integration.summary.oracle;

import com.tanrunn.servermenu.server.integration.summary.AppCardSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChineseOracleSummaryProvider#preview} 纯逻辑测试：
 * 空列表、一项、两项、三项以上、空白项过滤，以及超长名称最终被
 * {@link AppCardSummary} 行长度上限约束。
 */
class ChineseOraclePreviewTest {

    @Test
    void emptyListShowsNone() {
        assertEquals("无", ChineseOracleSummaryProvider.preview(List.of()));
        assertEquals("无", ChineseOracleSummaryProvider.preview(null));
    }

    @Test
    void singleItem() {
        assertEquals("宜一", ChineseOracleSummaryProvider.preview(List.of("宜一")));
    }

    @Test
    void twoItems() {
        assertEquals("宜一、宜二", ChineseOracleSummaryProvider.preview(List.of("宜一", "宜二")));
    }

    @Test
    void moreThanTwoItemsAppendCount() {
        assertEquals("宜一、宜二 等3项", ChineseOracleSummaryProvider.preview(List.of("宜一", "宜二", "宜三")));
        assertEquals("甲、乙 等5项", ChineseOracleSummaryProvider.preview(List.of("甲", "乙", "丙", "丁", "戊")));
    }

    @Test
    void blankNamesAreFiltered() {
        assertEquals("宜一、宜二", ChineseOracleSummaryProvider.preview(
                java.util.Arrays.asList("宜一", "", "  ", null, "宜二")));
        // 全空白 → 无
        assertEquals("无", ChineseOracleSummaryProvider.preview(java.util.Arrays.asList("", null, "  ")));
        // 空白过滤后仍超过 2 项：追加计数按过滤后数量
        assertEquals("甲、乙 等3项", ChineseOracleSummaryProvider.preview(
                java.util.Arrays.asList("甲", "", null, "乙", "丙")));
    }

    @Test
    void longNamesAreEventuallyCappedByLineLimit() {
        String longName = "事项".repeat(60); // 120 字符
        String preview = ChineseOracleSummaryProvider.preview(List.of(longName));
        AppCardSummary summary = new AppCardSummary(List.of("宜 " + preview));
        assertEquals(1, summary.lines().size());
        assertEquals(AppCardSummary.MAX_LINE_LENGTH, summary.lines().get(0).length());
        assertTrue(summary.lines().get(0).startsWith("宜 "));
    }
}
