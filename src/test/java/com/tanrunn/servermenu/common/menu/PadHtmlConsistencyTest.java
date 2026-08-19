package com.tanrunn.servermenu.common.menu;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pad HTML 与 {@link MenuApp} 白名单的静态一致性测试。
 *
 * <p>读取 main resources 中的 pad.html（位于测试运行时 classpath），确保：
 * 每个内置应用都有对应的卡片 data-app 与状态元素 id；HTML 中出现的
 * data-app / 状态元素 id 全部在白名单内且不重复。</p>
 */
class PadHtmlConsistencyTest {

    private static final String PAD_HTML_PATH = "/assets/apricityui/apricity/servermenu/screens/pad.html";

    @Test
    void everyMenuAppHasCardAndStateElement() throws IOException {
        String html = loadPadHtml();
        for (MenuApp app : MenuApp.ALL) {
            assertTrue(html.contains("data-app=\"" + app.id() + "\""),
                    "pad.html 缺少卡片 data-app=\"" + app.id() + "\"");
            assertTrue(html.contains("id=\"app-state-" + app.id() + "\""),
                    "pad.html 缺少状态元素 id=\"app-state-" + app.id() + "\"");
        }
    }

    @Test
    void everyDataAppInHtmlIsWhitelistedAndUnique() throws IOException {
        String html = loadPadHtml();
        Pattern pattern = Pattern.compile("data-app=\"([a-z_]+)\"");
        Matcher matcher = pattern.matcher(html);
        boolean foundAny = false;
        while (matcher.find()) {
            foundAny = true;
            String appId = matcher.group(1);
            assertTrue(MenuApp.fromId(appId).isPresent(), "pad.html 含未知 data-app=\"" + appId + "\"");
            assertCountIsOne(html, "data-app=\"" + appId + "\"");
        }
        assertTrue(foundAny, "pad.html 中未找到任何 data-app 卡片");
    }

    @Test
    void everyStateElementIdInHtmlIsWhitelistedAndUnique() throws IOException {
        String html = loadPadHtml();
        Pattern pattern = Pattern.compile("id=\"app-state-([a-z_]+)\"");
        Matcher matcher = pattern.matcher(html);
        boolean foundAny = false;
        while (matcher.find()) {
            foundAny = true;
            String appId = matcher.group(1);
            assertTrue(MenuApp.fromId(appId).isPresent(), "pad.html 含未知状态元素 id=\"app-state-" + appId + "\"");
            assertCountIsOne(html, "id=\"app-state-" + appId + "\"");
        }
        assertTrue(foundAny, "pad.html 中未找到任何 app-state 状态元素");
        // 状态元素数量与内置应用数量一致。
        assertTrue(countOccurrences(html, "id=\"app-state-") == MenuApp.ALL.size(),
                "app-state 元素数量与内置应用数量不一致");
    }

    @Test
    void everyStateElementTextMatchesWhitelist() throws IOException {
        String html = loadPadHtml();
        Pattern pattern = Pattern.compile("class=\"app-state [a-z]+\">([^<]*)</div>");
        Matcher matcher = pattern.matcher(html);
        boolean foundAny = false;
        while (matcher.find()) {
            foundAny = true;
            String text = matcher.group(1);
            assertTrue("未安装".equals(text) || "待接入".equals(text) || "已接入".equals(text),
                    "pad.html 状态元素含未知文案：" + text);
        }
        assertTrue(foundAny, "pad.html 中未找到任何 app-state 文案");
    }

    private static String loadPadHtml() throws IOException {
        try (InputStream in = PadHtmlConsistencyTest.class.getResourceAsStream(PAD_HTML_PATH)) {
            assertTrue(in != null, "测试 classpath 上找不到 " + PAD_HTML_PATH);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertCountIsOne(String html, String token) {
        assertTrue(countOccurrences(html, token) == 1, "HTML 中 " + token + " 出现次数不为 1");
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
