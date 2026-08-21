package com.tanrunn.servermenu.server.integration.lc;

/**
 * Lightman's Currency 桥接常量。
 *
 * <p>所有 LC 类名/方法名只以字符串出现于 {@link LcProbe}（反射探针），
 * 不保存任何 LC Class 字面量；只有 {@code lc} 包内的 typed 适配器直接引用 LC 类。</p>
 */
public final class LcConstants {

    public static final String LC_MOD_ID = "lightmanscurrency";

    /** Server Menu 侧统一的桥接/提供者 ID（BuildShop 与 StockMarket 共用）。 */
    public static final String PROVIDER_ID = "server_menu:lc_bank_main";
    public static final String PROVIDER_DISPLAY_NAME = "铜币";

    /** LC main 货币链。1 个桥接单位 = main 链 1 个 core value（本服务器 = 1 枚铜币）。 */
    public static final String MAIN_CHAIN = "main";

    /** LC CoinValue 在 MoneyStorage 中的唯一名（由 CoinValue.fromNumber(main,1).getUniqueName() 推导，这里为常量兜底）。 */
    public static final String MAIN_CHAIN_UNIQUE_NAME = "lightmanscurrency:coins!main";

    /** 单笔金额下限/上限（最小单位）。 */
    public static final long MIN_AMOUNT = 1L;
    public static final long MAX_AMOUNT_PER_OPERATION = 9_000_000_000_000_000_000L;

    // ---- 探针用类名（字符串，禁止 Class 字面量进入公共注册表） ----
    public static final String CLASS_BANK_API = "io.github.lightman314.lightmanscurrency.api.money.bank.BankAPI";
    public static final String CLASS_PLAYER_BANK_REFERENCE = "io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference";
    public static final String CLASS_IBANK_ACCOUNT = "io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount";
    public static final String CLASS_MONEY_STORAGE = "io.github.lightman314.lightmanscurrency.api.money.value.MoneyStorage";
    public static final String CLASS_MONEY_VALUE = "io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue";
    public static final String CLASS_COIN_VALUE = "io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue";
    public static final String CLASS_QUARANTINE_API = "io.github.lightman314.lightmanscurrency.api.misc.QuarantineAPI";
    public static final String CLASS_PAIR = "com.mojang.datafixers.util.Pair";

    /** typed 适配器自身类名（bootstrap 反射加载用）。 */
    public static final String CLASS_LC_PROVIDER = "com.tanrunn.servermenu.server.integration.lc.LcEconomyProvider";
    public static final String CLASS_BUILDSHOP_PROVIDER = "com.tanrunn.servermenu.server.integration.lc.LcBuildShopCurrencyProvider";
    public static final String CLASS_STOCKMARKET_BRIDGE = "com.tanrunn.servermenu.server.integration.lc.LcStockMarketCurrencyBridge";

    private LcConstants() {
    }
}
