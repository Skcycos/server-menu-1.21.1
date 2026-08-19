package com.tanrunn.servermenu.server.integration.oracle;

import com.tanrunn.chineseoracle.api.ChineseOracleApi;
import com.tanrunn.servermenu.ServerMenuMod;
import com.tanrunn.servermenu.common.menu.MenuApp;
import com.tanrunn.servermenu.server.integration.AppLaunchResult;
import com.tanrunn.servermenu.server.integration.AppLauncher;
import net.minecraft.server.level.ServerPlayer;

/**
 * 今日黄历适配器：只调用 {@link ChineseOracleApi#openAlmanac(ServerPlayer)}。
 *
 * <p>保持业务 API 自身的 AUI/聊天降级语义，不直接调用 FortuneService 或 FortuneNetwork。</p>
 */
public final class ChineseOracleLauncher implements AppLauncher {

    @Override
    public MenuApp app() {
        return MenuApp.CHINESE_ORACLE;
    }

    @Override
    public AppLaunchResult launch(ServerPlayer player) {
        try {
            boolean opened = ChineseOracleApi.openAlmanac(player);
            return opened
                    ? AppLaunchResult.ok()
                    : AppLaunchResult.failure("今日黄历当前不可用，请稍后再试。");
        } catch (RuntimeException e) {
            ServerMenuMod.LOGGER.error("[ServerMenu] chinese_oracle launch failed for {}",
                    player.getGameProfile().getName(), e);
            return AppLaunchResult.failure("今日黄历打开失败，请稍后再试。");
        }
    }
}
