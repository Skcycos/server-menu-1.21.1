package com.tanrunn.servermenu.server.integration.lc;

import com.tanrunn.servermenu.api.economy.EconomyBridgeRegistry;
import com.tanrunn.servermenu.api.economy.EconomyProvider;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LC 经济桥接装配入口：确认 LC 已安装 → 探针 → 反射实例化 typed 适配器 →
 * 注册 {@link EconomyBridgeRegistry}；若同时安装了 BuildShop/StockMarket，再把
 * 对应适配器注册进它们的公开注册表。
 *
 * <p>全程 fail closed：LC 未安装 / 探针失败 / 反射异常都不让服务器崩溃，
 * 只记日志并让相关能力保持不可用。业务 Mod 注册失败只影响该业务 Mod 侧桥接，
 * 不影响已注册的 LC provider。</p>
 */
public final class LcEconomyBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(LcEconomyBootstrap.class);

    /** Mod 安装检测器：默认走 ModList；测试可注入替身。 */
    static volatile ModPresenceChecker presenceChecker = LcEconomyBootstrap::defaultPresenceCheck;
    private static volatile boolean probed;
    private static volatile boolean probeOk;
    private static volatile boolean bootstrapped;
    private static volatile EconomyProvider registeredProvider;

    private LcEconomyBootstrap() {
    }

    @FunctionalInterface
    public interface ModPresenceChecker {
        boolean isLoaded(String modId);
    }

    // ---------------------------------------------------------------- API

    /** 服务器启动完成时调用（服务端主线程）。失败不会抛出。 */
    public static void bootstrap() {
        bootstrap(LcEconomyBootstrap.class.getClassLoader());
    }

    static void bootstrap(ClassLoader loader) {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        if (loader == null) {
            return;
        }
        if (!presenceChecker.isLoaded(LcConstants.LC_MOD_ID)) {
            LOGGER.info("[ServerMenu] lightmanscurrency not installed; LC economy bridge stays unavailable");
            return;
        }
        if (!probe(loader)) {
            LOGGER.error("[ServerMenu] LC API probe failed; LC economy bridge stays unavailable "
                    + "(expected LC {} API shape)", "2.3.0.5");
            return;
        }
        EconomyProvider provider = instantiateProvider(loader);
        if (provider == null) {
            return;
        }
        EconomyBridgeRegistry.register(provider);
        registeredProvider = provider;
        LOGGER.info("[ServerMenu] LC economy bridge registered as providerId={} chain={}",
                provider.providerId(), provider.currencyChain());

        registerSoulCardFountain(loader);
        registerBuildShop(loader, provider);
        registerStockMarket(loader, provider);
    }

    // ---------------------------------------------------------------- steps

    /** 探针（结果缓存；失败只记一次，本生命周期内不可用）。 */
    static boolean probe(ClassLoader loader) {
        if (probed) {
            return probeOk;
        }
        probed = true;
        probeOk = LcProbe.probe(loader);
        return probeOk;
    }

    private static EconomyProvider instantiateProvider(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(LcConstants.CLASS_LC_PROVIDER, false, loader);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (!(instance instanceof EconomyProvider provider)) {
                LOGGER.error("[ServerMenu] LC provider adapter does not implement EconomyProvider");
                return null;
            }
            return provider;
        } catch (LinkageError | ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("[ServerMenu] cannot instantiate LC provider adapter", e);
            return null;
        }
    }

    private static void registerBuildShop(ClassLoader loader, EconomyProvider provider) {
        if (!presenceChecker.isLoaded("buildshop")) {
            return;
        }
        if (com.tanrunn.buildshop.api.BuildingShopApi.currencyProvider(LcConstants.PROVIDER_ID).isPresent()) {
            LOGGER.info("[ServerMenu] BuildShop LC currency provider already registered");
            return;
        }
        try {
            Class<?> clazz = Class.forName(LcConstants.CLASS_BUILDSHOP_PROVIDER, false, loader);
            Object instance = clazz.getDeclaredConstructor(
                    Class.forName(LcConstants.CLASS_LC_PROVIDER, false, loader)).newInstance(provider);
            if (instance instanceof com.tanrunn.buildshop.api.ShopCurrencyProvider shopProvider) {
                com.tanrunn.buildshop.api.BuildingShopApi.registerCurrencyProvider(shopProvider);
                LOGGER.info("[ServerMenu] registered BuildShop currency provider {}", shopProvider.id());
            }
        } catch (LinkageError | ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("[ServerMenu] cannot register BuildShop LC currency provider", e);
        }
    }

    private static void registerStockMarket(ClassLoader loader, EconomyProvider provider) {
        if (!presenceChecker.isLoaded("stockmarket")) {
            return;
        }
        if (com.tanrunn.stockmarket.api.StockMarketApi.currencyBridge(LcConstants.PROVIDER_ID).isPresent()) {
            LOGGER.info("[ServerMenu] StockMarket LC currency bridge already registered");
            return;
        }
        try {
            Class<?> clazz = Class.forName(LcConstants.CLASS_STOCKMARKET_BRIDGE, false, loader);
            Object instance = clazz.getDeclaredConstructor(
                    Class.forName(LcConstants.CLASS_LC_PROVIDER, false, loader)).newInstance(provider);
            if (instance instanceof com.tanrunn.stockmarket.api.CurrencyBridge bridge) {
                com.tanrunn.stockmarket.api.StockMarketApi.registerCurrencyBridge(bridge);
                LOGGER.info("[ServerMenu] registered StockMarket currency bridge {}", bridge.id());
            }
        } catch (LinkageError | ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("[ServerMenu] cannot register StockMarket LC currency bridge", e);
        }
    }

    private static void registerSoulCardFountain(ClassLoader loader) {
        try {
            Class<?> animationClass = Class.forName(
                    "com.tanrunn.servermenu.server.integration.lc.LcSoulCardFountainAnimation",
                    false, loader);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(animationClass);
            LOGGER.info("[ServerMenu] registered LC soul-card fountain animation");
        } catch (LinkageError | ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("[ServerMenu] cannot register LC soul-card fountain animation", e);
        }
    }

    private static boolean defaultPresenceCheck(String modId) {
        try {
            return ModList.get().isLoaded(modId);
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- testing hooks

    public static EconomyProvider registeredProviderForTesting() {
        return registeredProvider;
    }

    public static boolean probeOkForTesting() {
        return probeOk;
    }

    public static void resetForTesting() {
        presenceChecker = LcEconomyBootstrap::defaultPresenceCheck;
        probed = false;
        probeOk = false;
        bootstrapped = false;
        registeredProvider = null;
    }
}
