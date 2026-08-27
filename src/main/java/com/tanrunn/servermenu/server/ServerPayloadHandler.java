package com.tanrunn.servermenu.server;

import com.tanrunn.servermenu.common.network.ServerMenuNetwork.LaunchAppRequestPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.OpenMenuRequestPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.OpenPlayerInfoRequestPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.OpenOapcRequestPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.PurchaseTerritoryRequestPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.RefreshTerritoryRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端网络包处理：统一转到 {@link MenuService}，在服务端主线程执行。
 */
public final class ServerPayloadHandler {
    private ServerPayloadHandler() {
    }

    public static void handleOpenMenu(OpenMenuRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MenuService.INSTANCE.handleOpenRequest(player);
            }
        });
    }

    public static void handleOpenPlayerInfo(OpenPlayerInfoRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MenuService.INSTANCE.handleOpenPlayerInfoRequest(player);
            }
        });
    }

    public static void handleLaunchApp(LaunchAppRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MenuService.INSTANCE.handleLaunch(player, payload.appId());
            }
        });
    }

    public static void handleRefreshTerritory(RefreshTerritoryRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MenuService.INSTANCE.handleRefreshTerritory(player);
            }
        });
    }

    public static void handlePurchaseTerritory(PurchaseTerritoryRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MenuService.INSTANCE.handlePurchaseTerritory(player);
            }
        });
    }

    public static void handleOpenOapc(OpenOapcRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MenuService.INSTANCE.handleOpenOapc(player);
            }
        });
    }
}
