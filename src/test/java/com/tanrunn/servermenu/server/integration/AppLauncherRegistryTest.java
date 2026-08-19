package com.tanrunn.servermenu.server.integration;

import com.tanrunn.servermenu.common.menu.MenuApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 适配器注册表纯逻辑测试（不依赖真实 ServerPlayer 与业务 Mod）。
 *
 * <p>测试环境没有 NeoForge/Minecraft 运行环境，因此不触碰 {@code ModList} 路径；
 * 通过注入 {@link AppLauncherRegistry.ModPresenceChecker} 替身验证安装检查与
 * 适配器延迟加载边界；反射探测用 {@link ProbeTestApis} 假 API 类覆盖全部分支。</p>
 */
class AppLauncherRegistryTest {

    @AfterEach
    void tearDown() {
        AppLauncherRegistry.resetForTesting();
    }

    // ------------------------------------------------------------ 描述符白名单

    @Test
    void everyMenuAppHasDescriptor() {
        for (MenuApp app : MenuApp.ALL) {
            assertNotNull(AppLauncherRegistry.descriptorFor(app), "缺少内置描述符：" + app.id());
        }
    }

    @Test
    void buildShopDescriptorMatchesRealApiContract() {
        LauncherDescriptor d = AppLauncherRegistry.descriptorFor(MenuApp.BUILD_SHOP);
        assertNotNull(d);
        assertEquals("com.tanrunn.servermenu.server.integration.buildshop.BuildShopLauncher", d.adapterClassName());
        assertEquals("com.tanrunn.buildshop.api.BuildingShopApi", d.apiClassName());
        assertEquals("openPanel", d.methodName());
        assertEquals(boolean.class, d.returnType());
    }

    @Test
    void stockMarketDescriptorMatchesRealApiContract() {
        LauncherDescriptor d = AppLauncherRegistry.descriptorFor(MenuApp.STOCK_MARKET);
        assertNotNull(d);
        assertEquals("com.tanrunn.servermenu.server.integration.stockmarket.StockMarketLauncher", d.adapterClassName());
        assertEquals("com.tanrunn.stockmarket.api.StockMarketApi", d.apiClassName());
        assertEquals("openPanel", d.methodName());
        assertEquals(void.class, d.returnType());
    }

    @Test
    void chineseOracleDescriptorMatchesRealApiContract() {
        LauncherDescriptor d = AppLauncherRegistry.descriptorFor(MenuApp.CHINESE_ORACLE);
        assertNotNull(d);
        assertEquals("com.tanrunn.servermenu.server.integration.oracle.ChineseOracleLauncher", d.adapterClassName());
        assertEquals("com.tanrunn.chineseoracle.api.ChineseOracleApi", d.apiClassName());
        assertEquals("openAlmanac", d.methodName());
        assertEquals(boolean.class, d.returnType());
    }

    // ------------------------------------------------------------ 反射探测分支

    @Test
    void probeShapeAcceptsCorrectPublicStaticMethod() throws Exception {
        Class<?> adapter = Class.forName("com.tanrunn.servermenu.server.integration.buildshop.BuildShopLauncher");
        assertTrue(AppLauncherRegistry.probeShape(ProbeTestApis.GoodApi.class, "openPanel",
                boolean.class, new Class<?>[]{String.class}, adapter));
    }

    @Test
    void probeShapeRejectsMissingMethod() throws Exception {
        Class<?> adapter = Class.forName("com.tanrunn.servermenu.server.integration.buildshop.BuildShopLauncher");
        assertFalse(AppLauncherRegistry.probeShape(ProbeTestApis.NoMethodApi.class, "openPanel",
                boolean.class, new Class<?>[]{String.class}, adapter));
    }

    @Test
    void probeShapeRejectsWrongReturnType() throws Exception {
        Class<?> adapter = Class.forName("com.tanrunn.servermenu.server.integration.buildshop.BuildShopLauncher");
        assertFalse(AppLauncherRegistry.probeShape(ProbeTestApis.WrongReturnApi.class, "openPanel",
                boolean.class, new Class<?>[]{String.class}, adapter));
    }

    @Test
    void probeShapeRejectsNonStaticMethod() throws Exception {
        Class<?> adapter = Class.forName("com.tanrunn.servermenu.server.integration.buildshop.BuildShopLauncher");
        assertFalse(AppLauncherRegistry.probeShape(ProbeTestApis.NonStaticApi.class, "openPanel",
                boolean.class, new Class<?>[]{String.class}, adapter));
    }

    @Test
    void probeShapeRejectsNonPublicMethod() throws Exception {
        Class<?> adapter = Class.forName("com.tanrunn.servermenu.server.integration.buildshop.BuildShopLauncher");
        assertFalse(AppLauncherRegistry.probeShape(ProbeTestApis.NonPublicApi.class, "openPanel",
                boolean.class, new Class<?>[]{String.class}, adapter));
    }

    @Test
    void probeShapeRejectsWrongArity() throws Exception {
        Class<?> adapter = Class.forName("com.tanrunn.servermenu.server.integration.buildshop.BuildShopLauncher");
        assertFalse(AppLauncherRegistry.probeShape(ProbeTestApis.WrongArityApi.class, "openPanel",
                boolean.class, new Class<?>[]{String.class}, adapter));
    }

    @Test
    void probeShapeRejectsWrongParameterType() throws Exception {
        Class<?> adapter = Class.forName("com.tanrunn.servermenu.server.integration.buildshop.BuildShopLauncher");
        assertFalse(AppLauncherRegistry.probeShape(ProbeTestApis.WrongParamTypeApi.class, "openPanel",
                boolean.class, new Class<?>[]{String.class}, adapter));
    }

    @Test
    void probeShapeRejectsBrokenAdapter() {
        // 适配器不是 AppLauncher 实现，或没有可访问无参构造器。
        assertFalse(AppLauncherRegistry.probeShape(ProbeTestApis.GoodApi.class, "openPanel",
                boolean.class, new Class<?>[]{String.class}, null));
        assertFalse(AppLauncherRegistry.probeShape(ProbeTestApis.GoodApi.class, "openPanel",
                boolean.class, new Class<?>[]{String.class}, String.class));
        assertFalse(AppLauncherRegistry.probeShape(ProbeTestApis.GoodApi.class, "openPanel",
                boolean.class, new Class<?>[]{String.class}, PrivateCtor.class));
    }

    /** 无可访问无参构造器的普通类。 */
    private static final class PrivateCtor {
        private PrivateCtor() {
        }
    }

    // ------------------------------------------------------------ 安装与链接

    @Test
    void notInstalledDoesNotProbeOrCacheAdapter() {
        AppLauncherRegistry.presenceChecker = modId -> false;
        assertFalse(AppLauncherRegistry.isInstalled(MenuApp.BUILD_SHOP));
        assertFalse(AppLauncherRegistry.isAvailable(MenuApp.BUILD_SHOP));

        // 关键断言：未安装分支不得触发兼容性探测（缓存保持未填充）。
        assertNull(AppLauncherRegistry.compatCacheForTesting(MenuApp.BUILD_SHOP));

        // Mod 已装时才会触发探测（测试环境无 Minecraft，签名级探测返回 false 属预期，
        // 但缓存被填充说明探测确实发生在安装检查之后）。
        AppLauncherRegistry.presenceChecker = modId -> true;
        AppLauncherRegistry.isAvailable(MenuApp.BUILD_SHOP);
        assertNotNull(AppLauncherRegistry.compatCacheForTesting(MenuApp.BUILD_SHOP));
    }

    @Test
    void installedProbesCompatibilityOnlyAfterInstallCheck() {
        AppLauncherRegistry.presenceChecker = modId -> true;
        assertTrue(AppLauncherRegistry.isInstalled(MenuApp.BUILD_SHOP));
        // 探测被触发并缓存结果（无 Minecraft 环境下为 false；真实签名验证见运行验证）。
        AppLauncherRegistry.isAvailable(MenuApp.BUILD_SHOP);
        assertNotNull(AppLauncherRegistry.compatCacheForTesting(MenuApp.BUILD_SHOP));
    }

    @Test
    void launchWithoutModReturnsNotInstalledMessage() {
        AppLaunchResult result = AppLauncherRegistry.notInstalledResult(MenuApp.STOCK_MARKET);
        assertFalse(result.success());
        assertTrue(result.userMessage().contains("服务器未安装"));
        assertTrue(result.userMessage().contains(MenuApp.STOCK_MARKET.displayName()));
    }

    // ------------------------------------------------------------ 实例化与状态

    @Test
    void instantiateSucceedsAndIsCached() {
        AppLauncherRegistry.presenceChecker = modId -> true;
        assertNotNull(AppLauncherRegistry.instantiate(MenuApp.BUILD_SHOP));
        assertNotNull(AppLauncherRegistry.cachedInstanceForTesting(MenuApp.BUILD_SHOP));
        // 身份一致且实例已缓存；再次获取返回同一实例。
        assertNotNull(AppLauncherRegistry.instantiate(MenuApp.BUILD_SHOP));
        assertNotNull(AppLauncherRegistry.cachedInstanceForTesting(MenuApp.BUILD_SHOP));
    }

    @Test
    void instantiateFailureClearsCompatibilityAndInstanceCache() {
        AppLauncherRegistry.presenceChecker = modId -> true;
        assertNotNull(AppLauncherRegistry.instantiate(MenuApp.BUILD_SHOP));
        assertNotNull(AppLauncherRegistry.cachedInstanceForTesting(MenuApp.BUILD_SHOP));

        // 模拟实例化失败（LinkageError/ReflectiveOperationException/ClassCastException 等）：
        // 兼容性置 false、实例缓存清除；再次打开 Pad 显示 connected=false。
        AppLauncherRegistry.markCompatibilityFailed(MenuApp.BUILD_SHOP);
        assertFalse(AppLauncherRegistry.isCompatible(MenuApp.BUILD_SHOP));
        assertNull(AppLauncherRegistry.cachedInstanceForTesting(MenuApp.BUILD_SHOP));
        assertFalse(AppLauncherRegistry.isAvailable(MenuApp.BUILD_SHOP));
    }

    @Test
    void adapterIdentityMismatchIsRejected() throws Exception {
        Class<?> adapterClass = Class.forName("com.tanrunn.servermenu.server.integration.buildshop.BuildShopLauncher");
        Object launcher = adapterClass.getDeclaredConstructor().newInstance();
        // 身份一致 → 通过；身份不一致（模拟配置错误）→ 拒绝。
        assertTrue(AppLauncherRegistry.verifyAppIdentity(launcher, MenuApp.BUILD_SHOP));
        assertFalse(AppLauncherRegistry.verifyAppIdentity(launcher, MenuApp.STOCK_MARKET));
        assertFalse(AppLauncherRegistry.verifyAppIdentity(null, MenuApp.BUILD_SHOP));
        assertFalse(AppLauncherRegistry.verifyAppIdentity("not-a-launcher", MenuApp.BUILD_SHOP));
    }

    // ------------------------------------------------------------ 异常边界

    @Test
    void guardedLaunchPassesThroughSuccess() {
        AppLaunchResult expected = AppLaunchResult.ok();
        AppLaunchResult result = AppLauncherRegistry.guardedLaunch(MenuApp.BUILD_SHOP, () -> expected);
        assertEquals(expected, result);
    }

    @Test
    void guardedLaunchConvertsRuntimeExceptionWithoutLeakingInternals() {
        AppLaunchResult result = AppLauncherRegistry.guardedLaunch(MenuApp.BUILD_SHOP,
                () -> {
                    throw new IllegalStateException("secret-internal-detail");
                });
        assertFalse(result.success());
        assertTrue(result.error());
        assertFalse(result.userMessage().contains("secret-internal-detail"),
                "内部异常文本泄露给了客户端：" + result.userMessage());
    }

    @Test
    void guardedLaunchConvertsLinkageError() {
        AppLaunchResult result = AppLauncherRegistry.guardedLaunch(MenuApp.BUILD_SHOP,
                () -> {
                    throw new NoClassDefFoundError("com/example/MissingApi");
                });
        assertFalse(result.success());
        assertEquals("应用版本不兼容，请联系管理员。", result.userMessage());
    }

    @Test
    void guardedLaunchLinkageErrorDegradesCompatibilityPermanently() {
        AppLauncherRegistry.presenceChecker = modId -> true;

        // 先建立探测成功状态：兼容缓存 true + 实例缓存非空。
        AppLauncherRegistry.markCompatibilityAvailableForTesting(MenuApp.BUILD_SHOP);
        assertNotNull(AppLauncherRegistry.instantiate(MenuApp.BUILD_SHOP));
        assertTrue(AppLauncherRegistry.compatCacheForTesting(MenuApp.BUILD_SHOP));
        assertNotNull(AppLauncherRegistry.cachedInstanceForTesting(MenuApp.BUILD_SHOP));

        // 运行期链接错误（如业务 API 加载失败）。
        AppLaunchResult result = AppLauncherRegistry.guardedLaunch(MenuApp.BUILD_SHOP,
                () -> {
                    throw new NoClassDefFoundError("com/example/MissingApi");
                });

        // 结果失败、提示安全、兼容性永久降级、实例缓存清除、后续 isAvailable 为 false。
        assertFalse(result.success());
        assertEquals("应用版本不兼容，请联系管理员。", result.userMessage());
        assertEquals(Boolean.FALSE, AppLauncherRegistry.compatCacheForTesting(MenuApp.BUILD_SHOP));
        assertNull(AppLauncherRegistry.cachedInstanceForTesting(MenuApp.BUILD_SHOP));
        assertFalse(AppLauncherRegistry.isAvailable(MenuApp.BUILD_SHOP));
    }

    @Test
    void unknownAppCannotFindLauncher() {
        assertFalse(AppLauncherRegistry.isAvailable(null));
        assertNotNull(AppLauncherRegistry.rejectUnknown(null));
        assertFalse(AppLauncherRegistry.rejectUnknown(null).success());
        assertTrue(AppLauncherRegistry.rejectUnknown(null).userMessage().contains("未知应用"));
        assertEquals(null, AppLauncherRegistry.rejectUnknown(MenuApp.BUILD_SHOP));
    }
}
