package com.tanrunn.servermenu.server.integration.summary.territory;

import com.tanrunn.servermenu.common.menu.MenuApp;
import com.tanrunn.servermenu.server.integration.summary.AppCardSummary;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** server-menu 自有的领地卡片摘要 API，避免读取 OAPC 内部实现细节。 */
public final class TerritorySummaryApi {
    private TerritorySummaryApi() {
    }

    public static AppCardSummary summary(ServerPlayer player) {
        return new AppCardSummary(List.of(MenuApp.TERRITORY.subtitle(), "由 Open Parties and Claims 提供"));
    }
}
