package com.tanrunn.servermenu.server.integration.summary;

import com.tanrunn.servermenu.common.menu.MenuApp;
import com.tanrunn.servermenu.server.integration.summary.buildshop.BuildShopSummaryProvider;
import com.tanrunn.servermenu.server.integration.summary.oracle.ChineseOracleSummaryProvider;
import com.tanrunn.servermenu.server.integration.summary.stockmarket.StockMarketSummaryProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static com.tanrunn.servermenu.server.integration.summary.ProbeSummaryTestApis.FakePlayer;
import static com.tanrunn.servermenu.server.integration.summary.ProbeSummaryTestApis.GoodApi;
import static com.tanrunn.servermenu.server.integration.summary.ProbeSummaryTestApis.NoMethodApi;
import static com.tanrunn.servermenu.server.integration.summary.ProbeSummaryTestApis.NonPublicApi;
import static com.tanrunn.servermenu.server.integration.summary.ProbeSummaryTestApis.NonStaticApi;
import static com.tanrunn.servermenu.server.integration.summary.ProbeSummaryTestApis.NotImplementingAdapter;
import static com.tanrunn.servermenu.server.integration.summary.ProbeSummaryTestApis.PrivateCtorOnly;
import static com.tanrunn.servermenu.server.integration.summary.ProbeSummaryTestApis.PublicCtorOnly;
import static com.tanrunn.servermenu.server.integration.summary.ProbeSummaryTestApis.WrongArityApi;
import static com.tanrunn.servermenu.server.integration.summary.ProbeSummaryTestApis.WrongParamTypeApi;
import static com.tanrunn.servermenu.server.integration.summary.ProbeSummaryTestApis.WrongReturnApi;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AppSummaryRegistry} 的纯逻辑测试：三个描述符字段、探测分支、
 * 门槛（未安装不探测）、与启动链路 connected 的隔离、调用边界降级。
 */
class AppSummaryRegistryTest {

    private static final String GOOD_RETURN_NAME =
            "com.tanrunn.servermenu.server.integration.summary.ProbeSummaryTestApis$GoodSummary";

    @BeforeEach
    @AfterEach
    void reset() {
        AppSummaryRegistry.resetForTesting();
    }

    // ------------------------------------------------------------ descriptors

    @Test
    void buildShopDescriptorFields() {
        SummaryDescriptor d = AppSummaryRegistry.descriptorFor(MenuApp.BUILD_SHOP);
        assertNotNull(d);
        assertEquals("com.tanrunn.servermenu.server.integration.summary.buildshop.BuildShopSummaryProvider",
                d.adapterClassName());
        assertEquals("com.tanrunn.buildshop.api.BuildingShopApi", d.apiClassName());
        assertEquals("summary", d.methodName());
        assertEquals("com.tanrunn.buildshop.api.BuildingShopSummary", d.returnTypeName());
    }

    @Test
    void stockMarketDescriptorFields() {
        SummaryDescriptor d = AppSummaryRegistry.descriptorFor(MenuApp.STOCK_MARKET);
        assertNotNull(d);
        assertEquals("com.tanrunn.servermenu.server.integration.summary.stockmarket.StockMarketSummaryProvider",
                d.adapterClassName());
        assertEquals("com.tanrunn.stockmarket.api.StockMarketApi", d.apiClassName());
        assertEquals("summary", d.methodName());
        assertEquals("com.tanrunn.stockmarket.api.MarketSummary", d.returnTypeName());
    }

    @Test
    void chineseOracleDescriptorFields() {
        SummaryDescriptor d = AppSummaryRegistry.descriptorFor(MenuApp.CHINESE_ORACLE);
        assertNotNull(d);
        assertEquals("com.tanrunn.servermenu.server.integration.summary.oracle.ChineseOracleSummaryProvider",
                d.adapterClassName());
        assertEquals("com.tanrunn.chineseoracle.api.ChineseOracleApi", d.apiClassName());
        assertEquals("summary", d.methodName());
        assertEquals("com.tanrunn.chineseoracle.api.AlmanacSummary", d.returnTypeName());
    }

    @Test
    void unknownAppHasNoDescriptor() {
        assertNull(AppSummaryRegistry.descriptorFor(null));
    }

    // ------------------------------------------------------------ probe shape

    @Test
    void probeShapeAcceptsCorrectSignatureWithRealAdapter() {
        // 正向分支使用真实摘要适配器（测试环境无法实现 AppSummaryProvider 接口）。
        for (Class<?> adapter : new Class<?>[]{BuildShopSummaryProvider.class,
                StockMarketSummaryProvider.class, ChineseOracleSummaryProvider.class}) {
            assertTrue(AppSummaryRegistry.probeShape(GoodApi.class, adapter,
                    "summary", GOOD_RETURN_NAME, FakePlayer.class),
                    "真实适配器应通过探测：" + adapter.getName());
        }
    }

    @Test
    void probeShapeRejectsMissingMethod() {
        assertFalse(AppSummaryRegistry.probeShape(NoMethodApi.class, BuildShopSummaryProvider.class,
                "summary", GOOD_RETURN_NAME, FakePlayer.class));
    }

    @Test
    void probeShapeRejectsNonPublicMethod() {
        assertFalse(AppSummaryRegistry.probeShape(NonPublicApi.class, BuildShopSummaryProvider.class,
                "summary", GOOD_RETURN_NAME, FakePlayer.class));
    }

    @Test
    void probeShapeRejectsNonStaticMethod() {
        assertFalse(AppSummaryRegistry.probeShape(NonStaticApi.class, BuildShopSummaryProvider.class,
                "summary", GOOD_RETURN_NAME, FakePlayer.class));
    }

    @Test
    void probeShapeRejectsWrongArity() {
        assertFalse(AppSummaryRegistry.probeShape(WrongArityApi.class, BuildShopSummaryProvider.class,
                "summary", GOOD_RETURN_NAME, FakePlayer.class));
    }

    @Test
    void probeShapeRejectsWrongParameterType() {
        assertFalse(AppSummaryRegistry.probeShape(WrongParamTypeApi.class, BuildShopSummaryProvider.class,
                "summary", GOOD_RETURN_NAME, FakePlayer.class));
    }

    @Test
    void probeShapeRejectsWrongReturnTypeName() {
        assertFalse(AppSummaryRegistry.probeShape(GoodApi.class, BuildShopSummaryProvider.class,
                "summary", "com.example.WrongSummary", FakePlayer.class));
        assertFalse(AppSummaryRegistry.probeShape(WrongReturnApi.class, BuildShopSummaryProvider.class,
                "summary", GOOD_RETURN_NAME, FakePlayer.class));
    }

    @Test
    void probeShapeRejectsAdapterNotImplementingInterface() {
        assertFalse(AppSummaryRegistry.probeShape(GoodApi.class, NotImplementingAdapter.class,
                "summary", GOOD_RETURN_NAME, FakePlayer.class));
    }

    @Test
    void constructorAccessibilityIsChecked() {
        // 构造器分支：接口实现由真实适配器在正向分支覆盖，这里单独验证构造器检查逻辑。
        assertTrue(AppSummaryRegistry.hasAccessibleNoArgConstructor(PublicCtorOnly.class));
        assertFalse(AppSummaryRegistry.hasAccessibleNoArgConstructor(PrivateCtorOnly.class));
        assertFalse(AppSummaryRegistry.hasAccessibleNoArgConstructor(null));
    }

    @Test
    void summaryMethodLookupRequiresPublicStatic() {
        assertNotNull(AppSummaryRegistry.findPublicStaticMethod(GoodApi.class, "summary"));
        assertNull(AppSummaryRegistry.findPublicStaticMethod(NonPublicApi.class, "summary"));
        assertNull(AppSummaryRegistry.findPublicStaticMethod(NonStaticApi.class, "summary"));
    }

    // ------------------------------------------------------------ identity

    @Test
    void providerIdentityMustMatchRequestedApp() {
        // 真实适配器实例：app() 与自身白名单一致。
        assertTrue(AppSummaryRegistry.verifyProviderIdentity(
                new BuildShopSummaryProvider(), MenuApp.BUILD_SHOP));
        // 身份不匹配：真实适配器与其它应用配对。
        assertFalse(AppSummaryRegistry.verifyProviderIdentity(
                new BuildShopSummaryProvider(), MenuApp.STOCK_MARKET));
        assertFalse(AppSummaryRegistry.verifyProviderIdentity(null, MenuApp.BUILD_SHOP));
    }

    // ------------------------------------------------------------ gating

    @Test
    void shouldAttemptRejectsNullAndUnknown() {
        assertFalse(AppSummaryRegistry.shouldAttempt(null));
    }

    @Test
    void notInstalledDoesNotProbe() {
        AppSummaryRegistry.installedChecker = app -> false;
        AppSummaryRegistry.connectedChecker = app -> true;
        assertFalse(AppSummaryRegistry.shouldAttempt(MenuApp.BUILD_SHOP));
        // 未安装：不进行类加载探测，缓存保持未写入。
        assertNull(AppSummaryRegistry.summaryCompatForTesting(MenuApp.BUILD_SHOP));
    }

    @Test
    void notConnectedDoesNotProbe() {
        AppSummaryRegistry.installedChecker = app -> true;
        AppSummaryRegistry.connectedChecker = app -> false;
        assertFalse(AppSummaryRegistry.shouldAttempt(MenuApp.BUILD_SHOP));
        assertNull(AppSummaryRegistry.summaryCompatForTesting(MenuApp.BUILD_SHOP));
    }

    @Test
    void realDescriptorProbeSucceedsAndWritesOnlySummaryCache() {
        // unitTest 环境（Minecraft + 业务 JAR 均在测试 classpath）：真实描述符探测应成功。
        // BuildShop 与其他业务一样提供真实 summary API，验证摘要探测成功。
        // 启动链路缓存是否被触碰由 AppSummaryLauncherIsolationTest（server.integration 包）验证。
        boolean probed = AppSummaryRegistry.isSummaryCompatible(MenuApp.STOCK_MARKET);
        assertTrue(probed);
        assertEquals(Boolean.TRUE, AppSummaryRegistry.summaryCompatForTesting(MenuApp.STOCK_MARKET));
    }

    // ------------------------------------------------------------ guarded call

    @Test
    void guardedSummaryReturnsEmptyForNullResult() {
        AppCardSummary result = AppSummaryRegistry.guardedSummary(MenuApp.BUILD_SHOP, () -> null);
        assertTrue(result.isEmpty());
    }

    @Test
    void guardedSummaryPassesThroughValidResult() {
        AppCardSummary summary = new AppCardSummary(java.util.List.of("一行"));
        assertEquals(summary, AppSummaryRegistry.guardedSummary(MenuApp.BUILD_SHOP, () -> summary));
    }

    @Test
    void linkageErrorOnlyDegradesSummaryCache() {
        AppSummaryRegistry.markSummaryCompatibleForTesting(MenuApp.BUILD_SHOP);
        AppCardSummary result = AppSummaryRegistry.guardedSummary(MenuApp.BUILD_SHOP, () -> {
            throw new LinkageError("simulated");
        });
        assertTrue(result.isEmpty());
        // 摘要能力降级为 false；启动链路不受影响由 AppSummaryLauncherIsolationTest 验证。
        assertEquals(Boolean.FALSE, AppSummaryRegistry.summaryCompatForTesting(MenuApp.BUILD_SHOP));
        assertNull(AppSummaryRegistry.providerForTesting(MenuApp.BUILD_SHOP));
    }

    @Test
    void runtimeExceptionReturnsEmptyWithoutClosingSummaryCapability() {
        AppSummaryRegistry.markSummaryCompatibleForTesting(MenuApp.BUILD_SHOP);
        AppCardSummary first = AppSummaryRegistry.guardedSummary(MenuApp.BUILD_SHOP, () -> {
            throw new IllegalStateException("player issue");
        });
        assertTrue(first.isEmpty());
        // 兼容缓存保持 true：后续仍允许重试。
        assertEquals(Boolean.TRUE, AppSummaryRegistry.summaryCompatForTesting(MenuApp.BUILD_SHOP));
        // 重试路径仍可成功。
        AppCardSummary second = AppSummaryRegistry.guardedSummary(MenuApp.BUILD_SHOP,
                () -> new AppCardSummary(java.util.List.of("恢复")));
        assertEquals("恢复", second.lines().get(0));
    }

    // ------------------------------------------------------------ real adapters

    @Test
    void realAdaptersInstantiateWithMatchingIdentity() {
        for (MenuApp app : MenuApp.ALL) {
            AppSummaryProvider provider = AppSummaryRegistry.instantiate(app);
            assertNotNull(provider, "摘要适配器实例化失败：" + app.id());
            assertEquals(app, provider.app(), "摘要适配器 app() 与请求不一致：" + app.id());
            assertTrue(Modifier.isPublic(provider.getClass().getModifiers()));
        }
    }

    @Test
    void realAdapterClassesImplementProviderWithPublicConstructor() throws Exception {
        for (MenuApp app : MenuApp.ALL) {
            SummaryDescriptor d = AppSummaryRegistry.descriptorFor(app);
            Class<?> adapterClass = Class.forName(d.adapterClassName(), false, getClass().getClassLoader());
            assertTrue(AppSummaryProvider.class.isAssignableFrom(adapterClass));
            assertTrue(AppSummaryRegistry.hasAccessibleNoArgConstructor(adapterClass));
        }
    }

    @Test
    void summaryCompatibilityCacheIsSeparateFromLauncherState() {
        AppSummaryRegistry.markSummaryCompatibleForTesting(MenuApp.BUILD_SHOP);
        assertEquals(Boolean.TRUE, AppSummaryRegistry.summaryCompatForTesting(MenuApp.BUILD_SHOP));
        // 摘要 PROVIDERS 缓存独立维护；启动链路状态隔离由 AppSummaryLauncherIsolationTest 验证。
        assertNull(AppSummaryRegistry.providerForTesting(MenuApp.BUILD_SHOP));
    }
}
