package com.tanrunn.servermenu.client.navigation;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "返回 Pad"按钮的静态一致性测试（纯逻辑，不加载 AUI/Minecraft 类）。
 *
 * <p>约束（DOM 注入难以在纯 JUnit 中真机化，改用产物级静态检查）：
 * <ol>
 *   <li>固定按钮 ID/class 只由 {@code BusinessScreenNavigator} 定义：
 *       扫描 server-menu 自身 main 字节码，含该 ID 的类必须恰好一个；</li>
 *   <li>Pad 自身 HTML（pad.html）不得包含返回按钮；</li>
 *   <li>页面路径映射与 {@code MenuApp} 一一对应（见 {@link BusinessPageTest}）。</li>
 * </ol>
 * 本测试不引用导航器类（避免纯 JUnit 环境加载 AUI/Minecraft 依赖），
 * ID 字面量必须与 {@code BusinessScreenNavigator.RETURN_BUTTON_ID} 保持一致。</p>
 */
class ReturnButtonConsistencyTest {

    /** 必须与 BusinessScreenNavigator.RETURN_BUTTON_ID / RETURN_BUTTON_CLASS 一致。 */
    private static final String RETURN_BUTTON_ID = "server-menu-return-pad";
    private static final String PAD_HTML_PATH = "/assets/apricityui/apricity/servermenu/screens/pad.html";

    @Test
    void padHtmlDoesNotContainReturnButton() throws IOException {
        String html = loadPadHtml();
        assertFalse(html.contains(RETURN_BUTTON_ID), "pad.html 不应包含返回按钮 ID " + RETURN_BUTTON_ID);
        assertFalse(html.contains("server-menu-return-pad"), "pad.html 不应包含返回按钮 class");
        assertFalse(html.contains("返回 Pad"), "pad.html 不应包含返回按钮文案");
        assertFalse(html.contains("返回中…"), "pad.html 不应包含返回中文案");
    }

    @Test
    void returnButtonIdIsDefinedOnlyByNavigatorClass() throws IOException {
        // 找到包含导航器类的 main 输出目录（避免扫描测试类目录）。
        Path mainRoot = findMainClassesRoot();
        List<Path> holders = findClassFilesContaining(mainRoot, RETURN_BUTTON_ID);
        assertEquals(List.of(mainRoot.resolve(
                        "com/tanrunn/servermenu/client/navigation/BusinessScreenNavigator.class")),
                holders,
                "固定按钮 ID 必须只出现在 BusinessScreenNavigator 一个类中");
    }

    @Test
    void everyBusinessPageResolvesFromItsOwnPath() {
        for (BusinessPage page : BusinessPage.ALL) {
            assertTrue(BusinessPage.fromDocumentPath(page.documentPath()).isPresent(),
                    "页面自身路径应能解析: " + page.documentPath());
        }
    }

    @Test
    void businessPathsMatchKnownTemplatePaths() {
        // 与三个业务 Mod 的 ApricityScreen 模板路径常量保持一致（只读校验，不修改业务项目）。
        assertEquals("buildingshop/screens/building_shop.html", BusinessPage.BUILD_SHOP.documentPath());
        assertEquals("screens/market.html", BusinessPage.STOCK_MARKET.documentPath());
        assertEquals("screens/fortune.html", BusinessPage.CHINESE_ORACLE.documentPath());
    }

    // ------------------------------------------------------------------ helpers

    private static String loadPadHtml() throws IOException {
        try (InputStream in = ReturnButtonConsistencyTest.class.getResourceAsStream(PAD_HTML_PATH)) {
            assertTrue(in != null, "测试 classpath 上找不到 " + PAD_HTML_PATH);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 从测试运行时 classpath 中找出包含导航器类的目录（即 main classes 根）。 */
    private static Path findMainClassesRoot() {
        String classPath = System.getProperty("java.class.path", "");
        for (String entry : classPath.split(File.pathSeparator)) {
            Path root = Path.of(entry);
            if (Files.isDirectory(root)
                    && Files.exists(root.resolve("com/tanrunn/servermenu/client/navigation/BusinessScreenNavigator.class"))) {
                return root;
            }
        }
        throw new AssertionError("classpath 中找不到 main classes 根目录（BusinessScreenNavigator.class）");
    }

    /** 在 root 下的 com/tanrunn/servermenu 字节码中查找包含 needle 字节序列的 .class 文件。 */
    private static List<Path> findClassFilesContaining(Path root, String needle) throws IOException {
        Path base = root.resolve("com/tanrunn/servermenu");
        byte[] target = needle.getBytes(StandardCharsets.UTF_8);
        try (Stream<Path> walk = Files.walk(base)) {
            return walk
                    .filter(path -> path.toString().endsWith(".class"))
                    .filter(path -> containsBytes(readBytes(path), target))
                    .sorted()
                    .toList();
        }
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new AssertionError("读取类文件失败: " + path, e);
        }
    }

    private static boolean containsBytes(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
