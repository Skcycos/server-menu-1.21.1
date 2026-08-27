package com.tanrunn.servermenu.client.network;

import com.tanrunn.servermenu.client.PadScreen;
import com.tanrunn.servermenu.client.PlayerInfoScreen;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.MenuFeedbackPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.MenuSnapshotPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.OpenTerritoryPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.PlayerInfoPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.TerritoryInfoPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端网络包处理：所有页面操作通过 {@code enqueueWork} 切到客户端主线程。
 *
 * <p>本类只在客户端被加载（common 注册处的方法引用在专用服务端不会被解析执行）。</p>
 */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void handleSnapshot(MenuSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PadScreen.onSnapshot(payload));
    }

    public static void handlePlayerInfo(PlayerInfoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PlayerInfoScreen.onSnapshot(payload));
    }

    public static void handleOpenTerritory(OpenTerritoryPayload payload, IPayloadContext context) {
        context.enqueueWork(com.tanrunn.servermenu.client.integration.territory.TerritoryClientLauncher::open);
    }

    public static void handleTerritoryInfo(TerritoryInfoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.tanrunn.servermenu.client.TerritoryScreen.onSnapshot(payload));
    }

    public static void handleFeedback(MenuFeedbackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PadScreen.onFeedback(payload));
    }
}
