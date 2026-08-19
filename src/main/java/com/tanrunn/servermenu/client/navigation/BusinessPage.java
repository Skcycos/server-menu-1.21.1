package com.tanrunn.servermenu.client.navigation;

import com.tanrunn.servermenu.common.menu.MenuApp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 客户端业务页面白名单（纯逻辑，无 Minecraft/AUI 依赖，可单测）。
 *
 * <p>每个常量由 {@link MenuApp} 与 AUI Document 的<b>精确逻辑路径</b>标识——
 * 即业务 Mod 构造 {@code ApricityScreen} 时传入的模板路径，也是
 * {@code Document.getPath()} 的返回值。</p>
 *
 * <p>{@link #fromDocumentPath(String)} 只做整串相等匹配：
 * null、空白、Pad 自身路径、相似路径与未知路径一律返回空。
 * 客户端页面路径只用于决定是否显示"返回 Pad"按钮，不授予任何业务权限。</p>
 */
public enum BusinessPage {
    BUILD_SHOP(MenuApp.BUILD_SHOP, "buildingshop/screens/building_shop.html"),
    STOCK_MARKET(MenuApp.STOCK_MARKET, "screens/market.html"),
    CHINESE_ORACLE(MenuApp.CHINESE_ORACLE, "screens/fortune.html");

    /** Pad 自身模板路径；明确不属于业务页面（与未知路径一样返回空）。 */
    public static final String PAD_DOCUMENT_PATH = "servermenu/screens/pad.html";

    /** 内置业务页面全集（稳定顺序）。 */
    public static final List<BusinessPage> ALL = List.of(values());

    private static final Map<String, BusinessPage> BY_PATH = buildIndex();

    private final MenuApp app;
    private final String documentPath;

    BusinessPage(MenuApp app, String documentPath) {
        this.app = app;
        this.documentPath = documentPath;
    }

    /** 对应的 Pad 菜单应用。 */
    public MenuApp app() {
        return app;
    }

    /** AUI Document 精确逻辑路径（与 {@code Document.getPath()} 整串相等比较）。 */
    public String documentPath() {
        return documentPath;
    }

    /**
     * 严格白名单解析：null、空白、Pad 路径、相似路径与未知路径均返回空。
     * 只做整串相等匹配，不允许 contains、endsWith 或任何模糊匹配。
     *
     * @param path AUI {@code Document.getPath()} 返回值
     * @return 命中的业务页面，未命中时为空
     */
    public static Optional<BusinessPage> fromDocumentPath(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_PATH.get(path));
    }

    private static Map<String, BusinessPage> buildIndex() {
        Map<String, BusinessPage> index = new LinkedHashMap<>();
        for (BusinessPage page : values()) {
            index.put(page.documentPath, page);
        }
        return Map.copyOf(index);
    }
}
