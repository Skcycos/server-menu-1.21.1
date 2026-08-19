package com.tanrunn.servermenu.server;

import com.tanrunn.servermenu.common.network.ServerMenuNetwork.LaunchAppRequestPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.OpenMenuRequestPayload;
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

    public static void handleLaunchApp(LaunchAppRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MenuService.INSTANCE.handleLaunch(player, payload.appId());
            }
        });
    }
}
