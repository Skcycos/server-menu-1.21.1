package com.tanrunn.servermenu.server.integration.buildshop;

import com.tanrunn.buildshop.api.BuildingShopApi;
import com.tanrunn.servermenu.ServerMenuMod;
import com.tanrunn.servermenu.common.menu.MenuApp;
import com.tanrunn.servermenu.server.integration.AppLaunchResult;
import com.tanrunn.servermenu.server.integration.AppLauncher;
import net.minecraft.server.level.ServerPlayer;

/**
 * 建筑商店适配器：只调用 {@link BuildingShopApi#openPanel(ServerPlayer)}。
 *
 * <p>不直接发送 OpenShopPayload，不绕过业务 Mod 的商店开关与管理员权限语义。</p>
 */
public final class BuildShopLauncher implements AppLauncher {

    @Override
    public MenuApp app() {
        return MenuApp.BUILD_SHOP;
    }

    @Override
    public AppLaunchResult launch(ServerPlayer player) {
        try {
            boolean opened = BuildingShopApi.openPanel(player);
            return opened
                    ? AppLaunchResult.ok()
                    : AppLaunchResult.failure("建筑商店当前不可用，可能已关闭或客户端未就绪。");
        } catch (RuntimeException e) {
            ServerMenuMod.LOGGER.error("[ServerMenu] buildshop launch failed for {}",
                    player.getGameProfile().getName(), e);
            return AppLaunchResult.failure("建筑商店打开失败，请稍后再试。");
        }
    }
}
