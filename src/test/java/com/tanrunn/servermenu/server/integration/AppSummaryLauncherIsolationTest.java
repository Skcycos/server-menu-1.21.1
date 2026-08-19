package com.tanrunn.servermenu.server.integration;

import com.tanrunn.servermenu.common.menu.MenuApp;
import com.tanrunn.servermenu.server.integration.summary.AppCardSummary;
import com.tanrunn.servermenu.server.integration.summary.SummaryTestHooks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 摘要能力与启动链路（connected/launcher instance）的隔离测试。
 *
 * <p>本类位于 {@code server.integration} 包，可直接访问
 * {@link AppLauncherRegistry} 的包内测试钩子；摘要侧通过 src/test 的
 * {@link SummaryTestHooks} 驱动，不扩大任何生产类的公开 API。</p>
 *
 * <p>结论：摘要 LinkageError 只降级摘要能力；摘要 RuntimeException 不永久关闭
 * 摘要；摘要失败不会调用启动兼容性降级；原 connected/launcher instance 不受影响。</p>
 */
class AppSummaryLauncherIsolationTest {

    @BeforeEach
    @AfterEach
    void reset() {
        AppLauncherRegistry.resetForTesting();
        SummaryTestHooks.reset();
    }

    @Test
    void linkageErrorDegradesOnlySummaryCapability() {
        // 启动链路：兼容缓存置 true 并实例化适配器。
        AppLauncherRegistry.presenceChecker = modId -> true;
        AppLauncherRegistry.markCompatibilityAvailableForTesting(MenuApp.BUILD_SHOP);
        AppLauncher launcher = AppLauncherRegistry.instantiate(MenuApp.BUILD_SHOP);
        assertNotNull(launcher);
        assertTrue(AppLauncherRegistry.isCompatible(MenuApp.BUILD_SHOP));
        SummaryTestHooks.markCompatible(MenuApp.BUILD_SHOP);

        // 摘要调用期间 LinkageError：仅摘要能力降级。
        AppCardSummary result = SummaryTestHooks.guarded(MenuApp.BUILD_SHOP, () -> {
            throw new LinkageError("simulated");
        });
        assertTrue(result.isEmpty());
        assertEquals(Boolean.FALSE, SummaryTestHooks.compatCache(MenuApp.BUILD_SHOP));
        assertNull(SummaryTestHooks.provider(MenuApp.BUILD_SHOP));

        // 启动链路不受影响：兼容缓存保持 true、实例仍在、connected 语义不变。
        assertEquals(Boolean.TRUE, AppLauncherRegistry.compatCacheForTesting(MenuApp.BUILD_SHOP));
        assertNotNull(AppLauncherRegistry.cachedInstanceForTesting(MenuApp.BUILD_SHOP));
        assertTrue(AppLauncherRegistry.isAvailable(MenuApp.BUILD_SHOP));
    }

    @Test
    void runtimeExceptionDoesNotPermanentlyCloseSummaryNorTouchLauncher() {
        AppLauncherRegistry.presenceChecker = modId -> true;
        AppLauncherRegistry.markCompatibilityAvailableForTesting(MenuApp.BUILD_SHOP);
        SummaryTestHooks.markCompatible(MenuApp.BUILD_SHOP);

        AppCardSummary first = SummaryTestHooks.guarded(MenuApp.BUILD_SHOP, () -> {
            throw new IllegalStateException("player issue");
        });
        assertTrue(first.isEmpty());
        // 摘要缓存保持 true：仍允许重试。
        assertEquals(Boolean.TRUE, SummaryTestHooks.compatCache(MenuApp.BUILD_SHOP));
        // 重试可成功。
        assertEquals("恢复", SummaryTestHooks.guarded(MenuApp.BUILD_SHOP,
                () -> new AppCardSummary(java.util.List.of("恢复"))).lines().get(0));
        // 启动链路不受影响。
        assertEquals(Boolean.TRUE, AppLauncherRegistry.compatCacheForTesting(MenuApp.BUILD_SHOP));
    }

    @Test
    void summaryProbeNeverTouchesLauncherCompatibilityState() {
        AppLauncherRegistry.presenceChecker = modId -> true;
        AppLauncherRegistry.markCompatibilityAvailableForTesting(MenuApp.BUILD_SHOP);
        AppLauncher launcher = AppLauncherRegistry.instantiate(MenuApp.BUILD_SHOP);
        assertNotNull(launcher);
        assertNull(SummaryTestHooks.compatCache(MenuApp.BUILD_SHOP));

        // 摘要兼容探测（真实描述符路径）成功写入摘要缓存。
        assertTrue(SummaryTestHooks.probeCompatible(MenuApp.BUILD_SHOP));
        assertEquals(Boolean.TRUE, SummaryTestHooks.compatCache(MenuApp.BUILD_SHOP));

        // 启动链路缓存与实例完全未被摘要探测触碰。
        assertEquals(Boolean.TRUE, AppLauncherRegistry.compatCacheForTesting(MenuApp.BUILD_SHOP));
        assertNotNull(AppLauncherRegistry.cachedInstanceForTesting(MenuApp.BUILD_SHOP));
    }
}
