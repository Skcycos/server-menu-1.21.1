package com.tanrunn.servermenu.server.integration.summary;

import com.tanrunn.servermenu.common.menu.MenuApp;
import com.tanrunn.servermenu.server.integration.AppLauncherRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 摘要适配器注册表（公共路径，不引用任何业务 Mod 的 API 类）。
 *
 * <p>每个内置应用对应一个硬编码 {@link SummaryDescriptor}（适配器类名、API 类名、
 * 方法名、返回类型全限定名字符串），描述符中不保存业务 Class 字面量。探测顺序：
 * MenuApp 白名单 → ModList 安装检查 → 启动链路 connected → 描述符反射探测 →
 * 适配器实例化与身份校验。摘要兼容性使用<b>自己的缓存</b>，与启动链路的
 * COMPAT_CACHE/INSTANCES 完全隔离：摘要 API 缺失或摘要失败只会让摘要区显示
 * “摘要暂不可用”，绝不改变 connected、绝不影响应用启动。</p>
 */
public final class AppSummaryRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppSummaryRegistry.class);

    /** 硬编码白名单：MenuApp.id → 摘要描述符（禁止任意客户端输入参与）。 */
    private static final Map<String, SummaryDescriptor> DESCRIPTORS = new LinkedHashMap<>();

    static {
        DESCRIPTORS.put(MenuApp.BUILD_SHOP.id(), new SummaryDescriptor(
                "com.tanrunn.servermenu.server.integration.summary.buildshop.BuildShopSummaryProvider",
                "com.tanrunn.buildshop.api.BuildingShopApi",
                "summary",
                "com.tanrunn.buildshop.api.BuildingShopSummary"));
        DESCRIPTORS.put(MenuApp.STOCK_MARKET.id(), new SummaryDescriptor(
                "com.tanrunn.servermenu.server.integration.summary.stockmarket.StockMarketSummaryProvider",
                "com.tanrunn.stockmarket.api.StockMarketApi",
                "summary",
                "com.tanrunn.stockmarket.api.MarketSummary"));
        DESCRIPTORS.put(MenuApp.CHINESE_ORACLE.id(), new SummaryDescriptor(
                "com.tanrunn.servermenu.server.integration.summary.oracle.ChineseOracleSummaryProvider",
                "com.tanrunn.chineseoracle.api.ChineseOracleApi",
                "summary",
                "com.tanrunn.chineseoracle.api.AlmanacSummary"));
        DESCRIPTORS.put(MenuApp.TERRITORY.id(), new SummaryDescriptor(
                "com.tanrunn.servermenu.server.integration.summary.territory.TerritorySummaryProvider",
                "com.tanrunn.servermenu.server.integration.summary.territory.TerritorySummaryApi",
                "summary",
                "com.tanrunn.servermenu.server.integration.summary.AppCardSummary"));
    }

    /** 摘要兼容性探测结果缓存（appId → 可用）；与启动链路 COMPAT_CACHE 完全隔离。 */
    private static final Map<String, Boolean> SUMMARY_COMPAT_CACHE = new ConcurrentHashMap<>();

    /** 已成功实例化且通过身份校验的无状态摘要适配器（按 appId）。 */
    private static final Map<String, AppSummaryProvider> PROVIDERS = new ConcurrentHashMap<>();

    /** 玩家相关摘要异常的完整日志防刷（每 app 每次服务器生命周期至多一次）。 */
    private static final Map<String, Boolean> LOGGED_PLAYER_ERRORS = new ConcurrentHashMap<>();

    /** 安装检测器：默认走 AppLauncherRegistry；测试可注入替身。 */
    static volatile Predicate<MenuApp> installedChecker = AppLauncherRegistry::isInstalled;
    /** 启动链路 connected 检测器：默认走 AppLauncherRegistry；测试可注入替身。 */
    static volatile Predicate<MenuApp> connectedChecker = AppLauncherRegistry::isAvailable;

    private AppSummaryRegistry() {
    }

    // ---------------------------------------------------------------- API

    /**
     * 生成应用摘要（必须在服务端主线程调用）。
     *
     * <p>顺序：白名单 → 安装 → 启动链路 connected → 摘要兼容探测 → 实例化 → 调用。
     * 任何一步失败都返回空摘要；摘要失败只降级为“摘要暂不可用”，
     * 不调用 AppLauncherRegistry.markCompatibilityFailed、不改变 connected、
     * 不阻止应用启动。</p>
     */
    public static AppCardSummary summary(MenuApp app, ServerPlayer player) {
        if (app == null || player == null) {
            return AppCardSummary.empty();
        }
        if (!shouldAttempt(app)) {
            return AppCardSummary.empty();
        }
        if (!isSummaryCompatible(app.id())) {
            return AppCardSummary.empty();
        }
        AppSummaryProvider provider = instantiate(app);
        if (provider == null) {
            return AppCardSummary.empty();
        }
        return guardedSummary(app, () -> provider.summary(player));
    }

    /** 服务器启动后记录一次摘要兼容性摘要（三 Mod 同装/故障排查的日志证据）。 */
    public static void logCompatibilitySummary() {
        for (MenuApp app : MenuApp.ALL) {
            boolean installed = AppLauncherRegistry.isInstalled(app);
            boolean connected = installed && AppLauncherRegistry.isAvailable(app);
            boolean summaryOk = connected && isSummaryCompatible(app.id());
            LOGGER.info("[ServerMenu] app {} installed={} connected={} summary={}",
                    app.id(), installed, connected, summaryOk);
        }
    }

    // ---------------------------------------------------------------- gating

    /** 是否值得尝试生成摘要：白名单 + 已安装 + 启动链路 connected（纯逻辑）。 */
    static boolean shouldAttempt(MenuApp app) {
        if (app == null || descriptorFor(app) == null) {
            return false;
        }
        try {
            return installedChecker.test(app) && connectedChecker.test(app);
        } catch (RuntimeException | LinkageError e) {
            // 检测器异常：按“不尝试”处理，不影响启动链路。
            return false;
        }
    }

    // ---------------------------------------------------------------- probe

    /** 摘要兼容性探测（按描述符 + 固定参数类型 ServerPlayer），结果按 appId 缓存。 */
    private static boolean isSummaryCompatible(String appId) {
        Boolean cached = SUMMARY_COMPAT_CACHE.get(appId);
        if (cached != null) {
            return cached;
        }
        SummaryDescriptor descriptor = DESCRIPTORS.get(appId);
        if (descriptor == null) {
            return false;
        }
        boolean ok = probeDescriptor(descriptor, AppSummaryRegistry.class.getClassLoader());
        SUMMARY_COMPAT_CACHE.put(appId, ok);
        if (!ok) {
            LOGGER.error("[ServerMenu] summary probe failed for app={} requiredMod={} api={}#{}",
                    appId,
                    MenuApp.fromId(appId).map(MenuApp::requiredModId).orElse("?"),
                    descriptor.apiClassName(), descriptor.methodName());
        }
        return ok;
    }

    /**
     * 按描述符探测：API 类存在、公开静态 summary(ServerPlayer) 存在、
     * 返回类型全限定名精确匹配、适配器类存在且实现 AppSummaryProvider
     * 且有可访问无参构造器。只加载不初始化，不调用业务 API。
     */
    static boolean probeDescriptor(SummaryDescriptor descriptor, ClassLoader loader) {
        if (descriptor == null || loader == null) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName(descriptor.apiClassName(), false, loader);
            Class<?> adapterClass = Class.forName(descriptor.adapterClassName(), false, loader);
            return probeShape(apiClass, adapterClass, descriptor.methodName(),
                    descriptor.returnTypeName(), ServerPlayer.class);
        } catch (ClassNotFoundException | LinkageError | RuntimeException e) {
            // RuntimeException 覆盖 SecurityException / TypeNotPresentException 等。
            return false;
        }
    }

    /**
     * 纯反射探测（无 Minecraft 依赖，可单测）：公开静态方法存在、返回类型
     * 全限定名精确匹配、参数类型精确匹配、适配器实现 {@link AppSummaryProvider}
     * 且有可访问无参构造器。
     */
    static boolean probeShape(Class<?> apiClass, Class<?> adapterClass, String methodName,
                              String returnTypeName, Class<?> parameterType) {
        Method method = findPublicStaticMethod(apiClass, methodName);
        if (method == null || returnTypeName == null
                || !returnTypeName.equals(method.getReturnType().getName())) {
            return false;
        }
        if (!parameterTypesMatch(method, new Class<?>[]{parameterType})) {
            return false;
        }
        if (adapterClass == null || !AppSummaryProvider.class.isAssignableFrom(adapterClass)) {
            return false;
        }
        return hasAccessibleNoArgConstructor(adapterClass);
    }

    /** 按名字查找公开静态方法（不按参数查询，避免提前解析参数类型）。 */
    static Method findPublicStaticMethod(Class<?> apiClass, String methodName) {
        if (apiClass == null || methodName == null) {
            return null;
        }
        try {
            for (Method method : apiClass.getMethods()) {
                if (method.getName().equals(methodName)
                        && Modifier.isPublic(method.getModifiers())
                        && Modifier.isStatic(method.getModifiers())) {
                    return method;
                }
            }
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
        return null;
    }

    /** 参数数量与类型精确匹配（含 null 防御）。 */
    static boolean parameterTypesMatch(Method method, Class<?>[] expected) {
        if (method == null || expected == null) {
            return false;
        }
        try {
            Class<?>[] actual = method.getParameterTypes();
            if (actual.length != expected.length) {
                return false;
            }
            for (int i = 0; i < actual.length; i++) {
                if (actual[i] != expected[i]) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException | LinkageError e) {
            // 参数类型无法解析（如依赖缺失）：视为不匹配。
            return false;
        }
    }

    /** 是否存在可访问的无参构造器。 */
    static boolean hasAccessibleNoArgConstructor(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        try {
            return Modifier.isPublic(clazz.getDeclaredConstructor().getModifiers());
        } catch (NoSuchMethodException | SecurityException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- call

    /**
     * 统一调用边界（纯逻辑，可单测）：
     * LinkageError → 本生命周期内仅把摘要能力降级为 false（不影响启动链路）；
     * 玩家相关 RuntimeException → 返回空摘要且不影响兼容缓存与 connected，
     * 完整日志每 app 只记一次，后续打开 Pad 仍允许重试。
     */
    static AppCardSummary guardedSummary(MenuApp app, Supplier<AppCardSummary> call) {
        try {
            AppCardSummary result = call.get();
            return result == null ? AppCardSummary.empty() : result;
        } catch (LinkageError e) {
            degrade(app);
            LOGGER.error("[ServerMenu] summary linkage failed for app={} requiredMod={}",
                    app == null ? "?" : app.id(),
                    app == null ? "?" : app.requiredModId(), e);
            return AppCardSummary.empty();
        } catch (RuntimeException e) {
            if (app != null && LOGGED_PLAYER_ERRORS.putIfAbsent(app.id(), Boolean.TRUE) == null) {
                LOGGER.error("[ServerMenu] summary generation failed for app={} requiredMod={}",
                        app.id(), app.requiredModId(), e);
            }
            return AppCardSummary.empty();
        }
    }

    // ---------------------------------------------------------------- instantiate

    /** 实例化摘要适配器；失败（链接/反射/类型/配置错误）时仅降级摘要能力并记录一次日志。 */
    static AppSummaryProvider instantiate(MenuApp app) {
        if (app == null) {
            return null;
        }
        String appId = app.id();
        AppSummaryProvider cached = PROVIDERS.get(appId);
        if (cached != null) {
            return cached;
        }
        SummaryDescriptor descriptor = DESCRIPTORS.get(appId);
        if (descriptor == null) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName(descriptor.adapterClassName(), false,
                    AppSummaryRegistry.class.getClassLoader());
            AppSummaryProvider provider = (AppSummaryProvider) clazz.getDeclaredConstructor().newInstance();
            if (!verifyProviderIdentity(provider, app)) {
                degrade(app);
                LOGGER.error("[ServerMenu] summary adapter identity mismatch for app={} class={}",
                        appId, descriptor.adapterClassName());
                return null;
            }
            PROVIDERS.put(appId, provider);
            return provider;
        } catch (LinkageError | ReflectiveOperationException | RuntimeException e) {
            degrade(app);
            LOGGER.error("[ServerMenu] cannot instantiate summary adapter for app={} class={}",
                    appId, descriptor.adapterClassName(), e);
            return null;
        }
    }

    /** 摘要适配器身份校验：app() 必须与请求的白名单应用一致（纯逻辑）。 */
    static boolean verifyProviderIdentity(AppSummaryProvider provider, MenuApp expected) {
        return provider != null && provider.app() == expected;
    }

    /** 仅把摘要能力降级：摘要缓存置 false 并清除已实例化适配器；不动启动链路。 */
    static void degrade(MenuApp app) {
        if (app == null) {
            return;
        }
        SUMMARY_COMPAT_CACHE.put(app.id(), false);
        PROVIDERS.remove(app.id());
    }

    // ---------------------------------------------------------------- testing hooks

    /** 白名单描述符查询（测试与内部共用；未知应用返回 null）。 */
    static SummaryDescriptor descriptorFor(MenuApp app) {
        return app == null ? null : DESCRIPTORS.get(app.id());
    }

    /** 测试专用：当前摘要兼容缓存值（null 表示从未探测）。 */
    static Boolean summaryCompatForTesting(MenuApp app) {
        return app == null ? null : SUMMARY_COMPAT_CACHE.get(app.id());
    }

    /** 测试专用：触发一次摘要兼容探测（写缓存）。 */
    static boolean isSummaryCompatible(MenuApp app) {
        return app != null && isSummaryCompatible(app.id());
    }

    /** 测试专用：把摘要兼容缓存显式置为 true（模拟探测成功后的状态）。 */
    static void markSummaryCompatibleForTesting(MenuApp app) {
        if (app != null) {
            SUMMARY_COMPAT_CACHE.put(app.id(), true);
        }
    }

    /** 测试专用：当前已实例化摘要适配器。 */
    static AppSummaryProvider providerForTesting(MenuApp app) {
        return app == null ? null : PROVIDERS.get(app.id());
    }

    /** 测试专用：恢复默认检测器并清空全部缓存。 */
    static void resetForTesting() {
        installedChecker = AppLauncherRegistry::isInstalled;
        connectedChecker = AppLauncherRegistry::isAvailable;
        SUMMARY_COMPAT_CACHE.clear();
        PROVIDERS.clear();
        LOGGED_PLAYER_ERRORS.clear();
    }
}
