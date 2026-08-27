package com.tanrunn.servermenu.client.integration.territory;

import com.tanrunn.servermenu.ServerMenuMod;
import net.minecraft.client.Minecraft;
import xaero.pac.client.api.OpenPACClientAPI;

/** OAPC 客户端入口：调用其公开 API 打开领地与队伍主界面。 */
public final class TerritoryClientLauncher {
    private TerritoryClientLauncher() {
    }

    public static void open() {
        try {
            OpenPACClientAPI.get().openMainMenuScreen(null, null);
        } catch (LinkageError | RuntimeException e) {
            ServerMenuMod.LOGGER.error("[ServerMenu] failed to open OAPC client screen", e);
            Minecraft.getInstance().setScreen(null);
        }
    }
}
