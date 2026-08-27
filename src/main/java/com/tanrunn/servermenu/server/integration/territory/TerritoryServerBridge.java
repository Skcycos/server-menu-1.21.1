package com.tanrunn.servermenu.server.integration.territory;

import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.server.api.OpenPACServerAPI;

/**
 * OAPC 服务端 API 的兼容性探针。
 *
 * <p>该类只在 {@code openpartiesandclaims} 已加载后，才会由启动器注册表反射加载。
 * 通过官方服务端 API 获取实例，避免只按 Mod ID 判断而把错误版本标记为可用。</p>
 */
public final class TerritoryServerBridge {
    private TerritoryServerBridge() {
    }

    public static boolean isAvailable(ServerPlayer player) {
        if (player == null || player.server == null) {
            return false;
        }
        try {
            return OpenPACServerAPI.get(player.server) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
