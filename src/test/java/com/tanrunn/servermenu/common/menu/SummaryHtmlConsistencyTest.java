package com.tanrunn.servermenu.common.menu;

import com.tanrunn.servermenu.client.navigation.BusinessPage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pad HTML 摘要区的静态一致性测试（纯逻辑）。
 *
 * <p>约束：三个 MenuApp 均有 3 个摘要元素 ID；所有摘要 ID 唯一；HTML 中不存在
 * 未知 appId 的摘要 ID；原“返回 Pad”按钮 ID 不与摘要区冲突；Pad 自身路径
 * 仍不会被 {@link BusinessPage} 识别为业务页。</p>
 */
class SummaryHtmlConsistencyTest {

    private static final String PAD_HTML_PATH = "/assets/apricityui/apricity/servermenu/screens/pad.html";
    private static final String RETURN_BUTTON_ID = "server-menu-return-pad";
    private static final Pattern SUMMARY_ID = Pattern.compile("id=\"app-summary-([a-z_]+)-([0-9])\"");

    @Test
    void everyMenuAppHasThreeSummaryElementIds() throws IOException {
        String html = loadPadHtml();
        for (MenuApp app : MenuApp.ALL) {
            for (int i = 0; i < 3; i++) {
                assertTrue(html.contains("id=\"app-summary-" + app.id() + "-" + i + "\""),
                        "pad.html 缺少摘要元素 id=\"app-summary-" + app.id() + "-" + i + "\"");
            }
        }
    }

    @Test
    void everySummaryIdIsUniqueAndWhitelisted() throws IOException {
        String html = loadPadHtml();
        Matcher matcher = SUMMARY_ID.matcher(html);
        int total = 0;
        while (matcher.find()) {
            total++;
            String appId = matcher.group(1);
            String index = matcher.group(2);
            // 未知 appId 一律拒绝（Java 端也由 MenuApp 白名单约束后才拼接 ID）。
            assertTrue(MenuApp.fromId(appId).isPresent(), "pad.html 含未知 appId 的摘要 ID：" + appId);
            assertTrue("0".equals(index) || "1".equals(index) || "2".equals(index));
            // 每个 ID 恰好出现一次。
            assertEquals(1, countOccurrences(html, "id=\"app-summary-" + appId + "-" + index + "\""),
                    "摘要 ID 重复：" + appId + "-" + index);
        }
        // 三个应用 × 三行 = 9 个摘要 ID。
        assertEquals(MenuApp.ALL.size() * 3, total, "摘要元素 ID 总数应为 9");
    }

    @Test
    void returnPadButtonIdDoesNotConflictWithSummaryArea() throws IOException {
        String html = loadPadHtml();
        assertFalse(html.contains("id=\"" + RETURN_BUTTON_ID + "\""),
                "pad.html 不应包含返回 Pad 按钮 ID（由导航器运行时注入）");
        assertFalse(html.contains("app-summary-" + RETURN_BUTTON_ID));
    }

    @Test
    void padItselfIsNotABusinessPage() {
        assertTrue(BusinessPage.fromDocumentPath(BusinessPage.PAD_DOCUMENT_PATH).isEmpty(),
                "Pad 自身路径不得被识别为业务页面");
    }

    // ------------------------------------------------------------------ helpers

    private static String loadPadHtml() throws IOException {
        try (InputStream in = SummaryHtmlConsistencyTest.class.getResourceAsStream(PAD_HTML_PATH)) {
            assertTrue(in != null, "测试 classpath 上找不到 " + PAD_HTML_PATH);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int countOccurrences(String html, String token) {
        int count = 0;
        int index = 0;
        while ((index = html.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
