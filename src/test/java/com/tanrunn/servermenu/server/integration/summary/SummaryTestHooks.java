package com.tanrunn.servermenu.server.integration.summary;

import com.tanrunn.servermenu.common.menu.MenuApp;

import java.util.function.Supplier;

/**
 * 摘要注册表测试桥（仅位于 src/test，绝不进入发布 JAR）。
 *
 * <p>把 {@link AppSummaryRegistry} 的包内测试钩子暴露给
 * {@code com.tanrunn.servermenu.server.integration} 包的启动隔离测试，
 * 使该测试无需扩大任何生产类的公开 API。</p>
 */
public final class SummaryTestHooks {
    private SummaryTestHooks() {
        throw new AssertionError();
    }

    public static void reset() {
        AppSummaryRegistry.resetForTesting();
    }

    public static void markCompatible(MenuApp app) {
        AppSummaryRegistry.markSummaryCompatibleForTesting(app);
    }

    public static Boolean compatCache(MenuApp app) {
        return AppSummaryRegistry.summaryCompatForTesting(app);
    }

    public static AppSummaryProvider provider(MenuApp app) {
        return AppSummaryRegistry.providerForTesting(app);
    }

    public static void degrade(MenuApp app) {
        AppSummaryRegistry.degrade(app);
    }

    public static AppCardSummary guarded(MenuApp app, Supplier<AppCardSummary> call) {
        return AppSummaryRegistry.guardedSummary(app, call);
    }

    /** 触发一次摘要兼容探测（写摘要缓存）。 */
    public static boolean probeCompatible(MenuApp app) {
        return AppSummaryRegistry.isSummaryCompatible(app);
    }
}
