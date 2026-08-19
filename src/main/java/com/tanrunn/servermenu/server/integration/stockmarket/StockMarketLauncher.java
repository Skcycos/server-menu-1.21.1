package com.tanrunn.servermenu.server.integration.stockmarket;

import com.tanrunn.servermenu.ServerMenuMod;
import com.tanrunn.servermenu.common.menu.MenuApp;
import com.tanrunn.servermenu.server.integration.AppLaunchResult;
import com.tanrunn.servermenu.server.integration.AppLauncher;
import com.tanrunn.stockmarket.api.StockMarketApi;
import net.minecraft.server.level.ServerPlayer;

/**
 * 股市适配器：只调用 {@link StockMarketApi#openPanel(ServerPlayer)}。
 *
 * <p>股市 API 要求服务端主线程（本类由 {@code MenuService} 在主线程统一调用）；
 * 其预期异常为 {@link IllegalArgumentException}（null 玩家）与
 * {@link IllegalStateException}（非主线程/服务未启动），在此捕获并转换为
 * 面向玩家的安全提示，不泄露内部消息。</p>
 */
public final class StockMarketLauncher implements AppLauncher {

    @Override
    public MenuApp app() {
        return MenuApp.STOCK_MARKET;
    }

    @Override
    public AppLaunchResult launch(ServerPlayer player) {
        try {
            StockMarketApi.openPanel(player);
            return AppLaunchResult.ok();
        } catch (IllegalArgumentException | IllegalStateException e) {
            ServerMenuMod.LOGGER.error("[ServerMenu] stockmarket openPanel rejected for {}: {}",
                    player.getGameProfile().getName(), e.toString());
            return AppLaunchResult.failure("股市当前不可用，请稍后再试。");
        } catch (RuntimeException e) {
            ServerMenuMod.LOGGER.error("[ServerMenu] stockmarket launch failed for {}",
                    player.getGameProfile().getName(), e);
            return AppLaunchResult.failure("股市打开失败，请稍后再试。");
        }
    }
}
