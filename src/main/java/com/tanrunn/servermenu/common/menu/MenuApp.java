package com.tanrunn.servermenu.common.menu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 服务器 Pad 菜单的内置应用模型（不可变）。
 *
 * <p>应用 ID 必须经过本类的严格白名单解析：客户端提交的任意 ID 一律拒绝。
 * 未来新增应用（如 lottery）时在此追加枚举常量即可，无需改动网络层。</p>
 */
public enum MenuApp {
    BUILD_SHOP("build_shop", "buildshop", "建筑商店", "建材采购与库存"),
    STOCK_MARKET("stock_market", "stockmarket", "股市", "行情、持仓与交易"),
    CHINESE_ORACLE("chinese_oracle", "chinese_oracle", "今日黄历", "今日宜忌与运势"),
    TERRITORY("territory", "openpartiesandclaims", "领地系统", "领地、队伍与区块管理");

    /** 内置应用全集（稳定顺序，快照按此顺序下发）。 */
    public static final List<MenuApp> ALL = List.of(values());

    private static final Map<String, MenuApp> BY_ID = buildIndex();

    private final String id;
    private final String requiredModId;
    private final String name;
    private final String subtitle;

    MenuApp(String id, String requiredModId, String name, String subtitle) {
        this.id = id;
        this.requiredModId = requiredModId;
        this.name = name;
        this.subtitle = subtitle;
    }

    /** 应用 ID（稳定白名单值，客户端不可提交任意内容）。 */
    public String id() {
        return id;
    }

    /** 该应用依赖的业务 Mod ID（用于服务端安装状态检测）。 */
    public String requiredModId() {
        return requiredModId;
    }

    /** 中文名。 */
    public String displayName() {
        return name;
    }

    /** 中文副标题。 */
    public String subtitle() {
        return subtitle;
    }

    /**
     * 严格白名单解析：null、空白与未知 ID 一律返回空。
     *
     * @param id 应用 ID
     * @return 匹配的内置应用，不存在时为空
     */
    public static Optional<MenuApp> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ID.get(id));
    }

    private static Map<String, MenuApp> buildIndex() {
        Map<String, MenuApp> index = new LinkedHashMap<>();
        for (MenuApp app : values()) {
            index.put(app.id, app);
        }
        return Map.copyOf(index);
    }
}
