package com.tanrunn.servermenu.server.integration;

import com.tanrunn.servermenu.common.menu.MenuApp;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务应用启动器注册表（公共路径，不引用任何业务 Mod 的 API 类）。
 *
 * <p>每个内置应用对应一个硬编码 {@link LauncherDescriptor}（适配器类名、API 类名、
 * 方法名、返回类型），描述符中不保存业务 API 的 Class 字面量。只有在
 * {@link ModList#isLoaded} 确认对应 Mod 已安装后，才会对描述符做兼容性探测
 * （反射验证 API 方法签名与适配器结构），探测结果按 appId 缓存，失败只记一次日志；
 * 玩家相关的启动结果不缓存。</p>
 */
public final class AppLauncherRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppLauncherRegistry.class);

    /** 业务 API 方法的唯一参数类型（三个业务 API 均为 ServerPlayer）。 */
    // 注意：ServerPlayer.class 字面量不能在静态初始化中使用（测试/无依赖环境会解析失败），
    // 只在 probeDescriptor 方法体内惰性解析。

    /** 硬编码白名单：MenuApp.id → 兼容性描述符（禁止任意客户端输入参与）。 */
    private static final Map<String, LauncherDescriptor> DESCRIPTORS = new LinkedHashMap<>();

    static {
        DESCRIPTORS.put(MenuApp.BUILD_SHOP.id(), new LauncherDescriptor(
                "com.tanrunn.servermenu.server.integration.buildshop.BuildShopLauncher",
                "com.tanrunn.buildshop.api.BuildingShopApi",
                "openPanel", boolean.class));
        DESCRIPTORS.put(MenuApp.STOCK_MARKET.id(), new LauncherDescriptor(
                "com.tanrunn.servermenu.server.integration.stockmarket.StockMarketLauncher",
                "com.tanrunn.stockmarket.api.StockMarketApi",
                "openPanel", void.class));
        DESCRIPTORS.put(MenuApp.CHINESE_ORACLE.id(), new LauncherDescriptor(
                "com.tanrunn.servermenu.server.integration.oracle.ChineseOracleLauncher",
                "com.tanrunn.chineseoracle.api.ChineseOracleApi",
                "openAlmanac", boolean.class));
        DESCRIPTORS.put(MenuApp.TERRITORY.id(), new LauncherDescriptor(
                "com.tanrunn.servermenu.server.integration.territory.TerritoryLauncher",
                "com.tanrunn.servermenu.server.integration.territory.TerritoryServerBridge",
                "isAvailable", boolean.class));
    }

    /** 兼容性探测结果缓存（appId → 可用）。探测失败只记一次日志。 */
    private static final Map<String, Boolean> COMPAT_CACHE = new ConcurrentHashMap<>();

    /** 已成功实例化且通过身份校验的无状态适配器（按 appId）。 */
    private static final Map<String, AppLauncher> INSTANCES = new ConcurrentHashMap<>();

    /** Mod 安装检测器：默认走 ModList；测试可注入替身。 */
    static volatile ModPresenceChecker presenceChecker = AppLauncherRegistry::defaultPresenceCheck;

    private AppLauncherRegistry() {
    }

    // ---------------------------------------------------------------- API

    /** 目标 Mod 是否已安装（服务端权威）。 */
    public static boolean isInstalled(MenuApp app) {
        if (app == null) {
            return false;
        }
        try {
            return presenceChecker.isLoaded(app.requiredModId());
        } catch (RuntimeException e) {
            LOGGER.warn("[ServerMenu] ModList unavailable while probing {}: {}",
                    app.requiredModId(), e.toString());
            return false;
        }
    }

    /**
     * 应用是否可接入：Mod 已安装 + 内置描述符 + 兼容性探测成功。
     *
     * <p>只做类加载与反射签名检查，不初始化业务服务、不调用业务 API、不打开界面。</p>
     */
    public static boolean isAvailable(MenuApp app) {
        if (app == null || descriptorFor(app) == null) {
            return false;
        }
        if (!isInstalled(app)) {
            return false;
        }
        return isCompatible(app.id());
    }

    /**
     * 启动应用（必须在服务端主线程调用）。
     *
     * <p>顺序：白名单 → 安装检查 → 兼容性探测 → 实例化 → 调用。
     * 第 3/4 步失败统一返回“应用版本不兼容”，不等到实际业务调用才发现已知签名错误；
     * 调用期间的 LinkageError 仍保留兜底捕获。</p>
     */
    public static AppLaunchResult launch(MenuApp app, ServerPlayer player) {
        AppLaunchResult rejected = rejectUnknown(app);
        if (rejected != null) {
            return rejected;
        }
        if (!isInstalled(app)) {
            return notInstalledResult(app);
        }
        if (!isCompatible(app.id())) {
            return AppLaunchResult.failure("应用版本不兼容，请联系管理员。");
        }
        return guardedLaunch(app, () -> {
            AppLauncher launcher = instantiate(app);
            if (launcher == null) {
                return AppLaunchResult.failure("应用版本不兼容，请联系管理员。");
            }
            return launcher.launch(player);
        });
    }

    /** 服务器启动后记录一次应用兼容性摘要（三 Mod 同装/故障排查的日志证据）。 */
    public static void logCompatibilitySummary() {
        for (MenuApp app : MenuApp.ALL) {
            boolean installed = isInstalled(app);
            boolean connected = installed && isAvailable(app);
            LOGGER.info("[ServerMenu] app {} installed={} connected={}", app.id(), installed, connected);
        }
    }

    // ---------------------------------------------------------------- internals

    /** 白名单解析失败 / 无内置描述符时的拒绝结果（纯逻辑）。 */
    static AppLaunchResult rejectUnknown(MenuApp app) {
        if (app == null || descriptorFor(app) == null) {
            return AppLaunchResult.failure("未知应用，请求已被拒绝。");
        }
        return null;
    }

    /** 未安装时的失败提示（纯逻辑）。 */
    static AppLaunchResult notInstalledResult(MenuApp app) {
        return AppLaunchResult.failure("服务器未安装「" + app.displayName() + "」，无法打开。");
    }

    /** 兼容性探测（按描述符 + 固定参数类型 ServerPlayer），结果按 appId 缓存。 */
    private static boolean isCompatible(String appId) {
        Boolean cached = COMPAT_CACHE.get(appId);
        if (cached != null) {
            return cached;
        }
        LauncherDescriptor descriptor = DESCRIPTORS.get(appId);
        if (descriptor == null) {
            return false;
        }
        boolean ok = probeDescriptor(descriptor, AppLauncherRegistry.class.getClassLoader());
        COMPAT_CACHE.put(appId, ok);
        if (!ok) {
            LOGGER.error("[ServerMenu] compatibility probe failed for app={} requiredMod={} api={}#{}",
                    appId,
                    MenuApp.fromId(appId).map(MenuApp::requiredModId).orElse("?"),
                    descriptor.apiClassName(), descriptor.methodName());
        }
        return ok;
    }

    /**
     * 按描述符探测：API 类存在、公开静态方法存在、返回类型精确匹配、
     * 参数为 ServerPlayer、适配器类存在且实现 AppLauncher 且有可访问无参构造器。
     * 只加载不初始化，不调用业务 API。
     */
    static boolean probeDescriptor(LauncherDescriptor descriptor, ClassLoader loader) {
        try {
            Class<?> apiClass = Class.forName(descriptor.apiClassName(), false, loader);
            Class<?> adapterClass = Class.forName(descriptor.adapterClassName(), false, loader);
            return probeShape(apiClass, descriptor.methodName(), descriptor.returnType(),
                    new Class<?>[]{ServerPlayer.class}, adapterClass);
        } catch (ClassNotFoundException | LinkageError | RuntimeException e) {
            // RuntimeException 覆盖 SecurityException / TypeNotPresentException 等。
            return false;
        }
    }

    /**
     * 纯反射探测（无 Minecraft 依赖，可单测）：
     * 公开静态方法存在、返回类型精确匹配、参数类型精确匹配、适配器实现
     * {@link AppLauncher} 且有可访问无参构造器。
     */
    static boolean probeShape(Class<?> apiClass, String methodName, Class<?> returnType,
                              Class<?>[] parameterTypes, Class<?> adapterClass) {
        Method method = findPublicStaticMethod(apiClass, methodName);
        if (method == null || method.getReturnType() != returnType) {
            return false;
        }
        if (!parameterTypesMatch(method, parameterTypes)) {
            return false;
        }
        if (adapterClass == null || !AppLauncher.class.isAssignableFrom(adapterClass)) {
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

    /**
     * 统一兼容性降级：把该应用的兼容性缓存置为 false 并清除已实例化适配器。
     * 调用后本次服务器生命周期内 connected 恒为 false，点击返回版本不兼容提示。
     */
    static void markCompatibilityFailed(MenuApp app) {
        if (app == null) {
            return;
        }
        COMPAT_CACHE.put(app.id(), false);
        INSTANCES.remove(app.id());
    }

    /**
     * 实例化适配器；失败（链接/反射/类型/配置错误）时把兼容性缓存置 false、
     * 移除实例缓存，并记录一次日志。实例化后校验 {@code app()} 与请求一致。
     */
    private static AppLauncher instantiateInternal(MenuApp app) {
        String appId = app.id();
        AppLauncher cached = INSTANCES.get(appId);
        if (cached != null) {
            return cached;
        }
        LauncherDescriptor descriptor = DESCRIPTORS.get(appId);
        if (descriptor == null) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName(descriptor.adapterClassName(), false,
                    AppLauncherRegistry.class.getClassLoader());
            AppLauncher launcher = (AppLauncher) clazz.getDeclaredConstructor().newInstance();
            if (!verifyAppIdentity(launcher, app)) {
                markCompatibilityFailed(app);
                LOGGER.error("[ServerMenu] adapter identity mismatch for app={} class={}",
                        appId, descriptor.adapterClassName());
                return null;
            }
            INSTANCES.put(appId, launcher);
            return launcher;
        } catch (LinkageError | ReflectiveOperationException | RuntimeException e) {
            markCompatibilityFailed(app);
            LOGGER.error("[ServerMenu] cannot instantiate adapter for app={} class={}",
                    appId, descriptor.adapterClassName(), e);
            return null;
        }
    }

    /** 适配器身份校验：app() 必须与请求的白名单应用一致（纯逻辑）。 */
    static boolean verifyAppIdentity(Object launcher, MenuApp expected) {
        return launcher instanceof AppLauncher typed && typed.app() == expected;
    }

    /** 统一调用边界：LinkageError / 意外 RuntimeException 在此转换为安全提示。 */
    static AppLaunchResult guardedLaunch(MenuApp app, LauncherCall call) {
        try {
            return call.invoke();
        } catch (LinkageError e) {
            LOGGER.error("[ServerMenu] adapter linkage failed for app={} requiredMod={}",
                    app == null ? "?" : app.id(),
                    app == null ? "?" : app.requiredModId(), e);
            // 运行期链接错误：本服务器生命周期内永久降级为不可用并清除实例。
            markCompatibilityFailed(app);
            return AppLaunchResult.failure("应用版本不兼容，请联系管理员。");
        } catch (RuntimeException e) {
            LOGGER.error("[ServerMenu] unexpected error launching app={}",
                    app == null ? "?" : app.id(), e);
            return AppLaunchResult.failure("应用打开失败，请稍后再试。");
        }
    }

    private static boolean defaultPresenceCheck(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /** 白名单描述符查询（测试与内部共用；未知应用返回 null）。 */
    static LauncherDescriptor descriptorFor(MenuApp app) {
        return app == null ? null : DESCRIPTORS.get(app.id());
    }

    // ---------------------------------------------------------------- testing hooks

    /** 测试专用：包装实例化入口。 */
    static AppLauncher instantiate(MenuApp app) {
        return instantiateInternal(app);
    }

    /** 测试专用：读取当前实例缓存。 */
    static AppLauncher cachedInstanceForTesting(MenuApp app) {
        return app == null ? null : INSTANCES.get(app.id());
    }

    /** 测试专用：当前兼容性缓存值。 */
    static boolean isCompatible(MenuApp app) {
        return app != null && isCompatible(app.id());
    }

    /** 测试专用：读取当前兼容性缓存值（null 表示从未探测）。 */
    static Boolean compatCacheForTesting(MenuApp app) {
        return app == null ? null : COMPAT_CACHE.get(app.id());
    }

    /** 测试专用：把兼容性缓存显式置为 true（模拟探测成功后的状态）。 */
    static void markCompatibilityAvailableForTesting(MenuApp app) {
        if (app != null) {
            COMPAT_CACHE.put(app.id(), true);
        }
    }

    // ---------------------------------------------------------------- hooks

    /** 测试替身：未安装时不加载适配器。 */
    @FunctionalInterface
    public interface ModPresenceChecker {
        boolean isLoaded(String modId);
    }

    @FunctionalInterface
    interface LauncherCall {
        AppLaunchResult invoke();
    }

    /** 测试专用：恢复默认检测器并清空缓存。 */
    static void resetForTesting() {
        presenceChecker = AppLauncherRegistry::defaultPresenceCheck;
        COMPAT_CACHE.clear();
        INSTANCES.clear();
    }
}
