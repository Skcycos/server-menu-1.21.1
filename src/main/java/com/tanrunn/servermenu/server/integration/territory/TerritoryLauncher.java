package com.tanrunn.servermenu.server.integration.territory;

import com.tanrunn.servermenu.ServerMenuMod;
import com.tanrunn.servermenu.common.menu.MenuApp;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.OpenTerritoryPayload;
import com.tanrunn.servermenu.server.integration.AppLaunchResult;
import com.tanrunn.servermenu.server.integration.AppLauncher;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import xaero.pac.common.server.api.OpenPACServerAPI;

/**
 * Open Parties and Claims 适配器：验证 OAPC 服务端 API 后通知客户端打开其主界面。
 *
 * <p>领地与队伍的实际权限、数据同步和操作仍由 OAPC 自己处理；server-menu 只负责
 * 统一入口，不复制领地逻辑，也不直接发送 OAPC 内部包。</p>
 */
public final class TerritoryLauncher implements AppLauncher {

    @Override
    public MenuApp app() {
        return MenuApp.TERRITORY;
    }

    @Override
    public AppLaunchResult launch(ServerPlayer player) {
        try {
            // 先触碰官方服务端 API，确认当前服务端确实完成了 OAPC 初始化。
            if (OpenPACServerAPI.get(player.server) == null) {
                return AppLaunchResult.failure("领地系统当前不可用，请稍后再试。");
            }
            PacketDistributor.sendToPlayer(player, new OpenTerritoryPayload());
            return AppLaunchResult.ok();
        } catch (RuntimeException e) {
            ServerMenuMod.LOGGER.error("[ServerMenu] territory launch failed for {}",
                    player.getGameProfile().getName(), e);
            return AppLaunchResult.failure("领地系统打开失败，请稍后再试。");
        }
    }
}
