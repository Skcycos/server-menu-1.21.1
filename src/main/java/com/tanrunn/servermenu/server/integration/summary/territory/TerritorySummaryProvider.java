package com.tanrunn.servermenu.server.integration.summary.territory;

import com.tanrunn.servermenu.common.menu.MenuApp;
import com.tanrunn.servermenu.server.integration.summary.AppCardSummary;
import com.tanrunn.servermenu.server.integration.summary.AppSummaryProvider;
import net.minecraft.server.level.ServerPlayer;

/** 领地系统卡片摘要：保留 OAPC 数据与权限的单一事实来源。 */
public final class TerritorySummaryProvider implements AppSummaryProvider {
    @Override
    public MenuApp app() {
        return MenuApp.TERRITORY;
    }

    @Override
    public AppCardSummary summary(ServerPlayer player) {
        return TerritorySummaryApi.summary(player);
    }
}
