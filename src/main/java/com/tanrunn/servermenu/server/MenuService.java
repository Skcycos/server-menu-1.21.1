package com.tanrunn.servermenu.server;

import com.tanrunn.servermenu.ServerMenuMod;
import com.tanrunn.servermenu.common.menu.MenuApp;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.AppStatus;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.MenuFeedbackPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.MenuSnapshotPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.PlayerInfoPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.TerritoryInfoPayload;
import com.tanrunn.servermenu.server.integration.AppLaunchResult;
import com.tanrunn.servermenu.server.integration.AppLauncherRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 服务端权威的 Pad 菜单服务。
 *
 * <p>安装与接入状态由 {@link AppLauncherRegistry} 检测（ModList + 适配器链接探测），
 * 绝不信任客户端上报。业务应用启动委托给适配器注册表，本类不引用任何业务 API。</p>
 *
 * <p>打开/返回 Pad 请求与应用启动请求使用独立的两个冷却
 * （{@link MenuRequestCooldowns}）：启动冷却不会阻塞紧随其后的返回 Pad 请求。</p>
 */
public final class MenuService {
    public static final MenuService INSTANCE = new MenuService();

    private static final int MAX_FEEDBACK_LENGTH = 256;

    private final MenuRequestCooldowns requestCooldowns = new MenuRequestCooldowns();

    private MenuService() {
    }

    /**
     * 打开玩家 Pad 菜单：下发服务端权威的快照。
     *
     * @return true 表示已接受并下发快照；player 为 null、离线或网络未就绪时返回 false
     */
    public boolean openMenu(ServerPlayer player) {
        if (player == null || player.server == null) {
            return false;
        }
        if (!player.server.isSameThread()) {
            MinecraftServer server = player.server;
            server.execute(() -> openMenu(player));
            return true;
        }
        if (!isReachable(player, MenuSnapshotPayload.TYPE)) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new MenuSnapshotPayload(buildAppStatuses(player)));
        return true;
    }

    /** C2S 打开菜单请求：打开冷却 + 统一打开流程。 */
    public void handleOpenRequest(ServerPlayer player) {
        runOnServerThread(player, () -> {
            if (!requestCooldowns.tryAcquireOpen(player.getUUID(), System.currentTimeMillis())) {
                sendFeedback(player, "操作太频繁，请稍后再试。", true);
                return;
            }
            openMenu(player);
        });
    }

    /** C2S 个人信息页请求：沿用打开冷却并下发服务端权威统计快照。 */
    public void handleOpenPlayerInfoRequest(ServerPlayer player) {
        runOnServerThread(player, () -> {
            if (!requestCooldowns.tryAcquireOpen(player.getUUID(), System.currentTimeMillis())) {
                sendFeedback(player, "操作太频繁，请稍后再试。", true);
                return;
            }
            if (!isReachable(player, PlayerInfoPayload.TYPE)) {
                return;
            }
            PacketDistributor.sendToPlayer(player, PlayerInfoService.snapshot(player));
        });
    }

    /**
     * C2S 启动应用请求：启动冷却 → 白名单解析 → 安装检查 → 适配器启动。
     * 成功时业务 S2C 包会自然打开对应页面，不发送额外反馈。
     */
    public void handleLaunch(ServerPlayer player, String rawAppId) {
        runOnServerThread(player, () -> {
            if (!requestCooldowns.tryAcquireLaunch(player.getUUID(), System.currentTimeMillis())) {
                sendFeedback(player, "操作太频繁，请稍后再试。", true);
                return;
            }
            MenuApp app = MenuApp.fromId(rawAppId).orElse(null);
            if (app == null) {
                sendFeedback(player, "未知应用，请求已被拒绝。", true);
                return;
            }
            if (app == MenuApp.TERRITORY) {
                openTerritoryPage(player, "", false);
                return;
            }
            AppLaunchResult result = AppLauncherRegistry.launch(app, player);
            if (result.success()) {
                // 业务页面随后由业务 Mod 的 S2C 包打开，Pad 自然被替换。
                return;
            }
            sendFeedback(player, result.userMessage(), result.error());
        });
    }

    public void handleRefreshTerritory(ServerPlayer player) {
        runOnServerThread(player, () -> {
            if (!requestCooldowns.tryAcquireOpen(player.getUUID(), System.currentTimeMillis())) {
                return;
            }
            openTerritoryPage(player, "", false);
        });
    }

    public void handlePurchaseTerritory(ServerPlayer player) {
        runOnServerThread(player, () -> {
            if (!requestCooldowns.tryAcquireLaunch(player.getUUID(), System.currentTimeMillis())) {
                return;
            }
            TerritoryService.PurchaseResult result = TerritoryService.purchase(player);
            openTerritoryPage(player, result.message(), result.error());
        });
    }

    public void handleOpenOapc(ServerPlayer player) {
        runOnServerThread(player, () -> {
            if (!requestCooldowns.tryAcquireLaunch(player.getUUID(), System.currentTimeMillis())) {
                return;
            }
            AppLaunchResult result = AppLauncherRegistry.launch(MenuApp.TERRITORY, player);
            if (!result.success()) {
                sendFeedback(player, result.userMessage(), true);
            }
        });
    }

    private void openTerritoryPage(ServerPlayer player, String message, boolean error) {
        if (!isReachable(player, TerritoryInfoPayload.TYPE)) {
            return;
        }
        TerritoryInfoPayload base = TerritoryService.snapshot(player);
        TerritoryInfoPayload payload = message == null || message.isBlank()
                ? base
                : new TerritoryInfoPayload(base.oapcAvailable(), base.luckPermsAvailable(),
                base.economyAvailable(), base.claimsHeld(), base.claimLimit(),
                base.purchasedClaims(), base.maxPurchasable(), base.claimPrice(),
                base.balanceMinorUnits(), base.currency(), base.permissionNode(), message, error);
        PacketDistributor.sendToPlayer(player, payload);
    }

    /** 玩家退出：清理两个冷却状态。 */
    public void onPlayerLoggedOut(UUID uuid) {
        requestCooldowns.onPlayerLoggedOut(uuid);
    }

    /** 非服务端主线程调用时延迟到主线程执行；返回 true 表示已在主线程执行完毕。 */
    private boolean runOnServerThread(ServerPlayer player, Runnable action) {
        if (player == null || player.server == null) {
            return false;
        }
        if (!player.server.isSameThread()) {
            MinecraftServer server = player.server;
            server.execute(() -> runOnServerThread(player, action));
            return false;
        }
        action.run();
        return true;
    }

    /**
     * 构建快照（必须位于服务端主线程，由 {@link #openMenu} 保证）。
     *
     * <p>installed/connected 仍由 {@link AppLauncherRegistry} 计算；只有
     * connected=true 时才尝试 {@code AppSummaryRegistry.summary} 生成摘要，
     * 未安装、未接入或摘要失败时 summaryLines 为空。一个应用摘要失败
     * 不阻止其余应用快照生成；摘要随快照一次下发，无额外往返。</p>
     */
    private List<AppStatus> buildAppStatuses(ServerPlayer player) {
        List<AppStatus> apps = new ArrayList<>(MenuApp.ALL.size());
        for (MenuApp app : MenuApp.ALL) {
            boolean installed = AppLauncherRegistry.isInstalled(app);
            // connected：已安装且 server-menu 内置适配器且 API 链接可用。
            boolean connected = installed && AppLauncherRegistry.isAvailable(app);
            List<String> summaryLines = connected
                    ? com.tanrunn.servermenu.server.integration.summary.AppSummaryRegistry.summary(app, player).lines()
                    : List.of();
            apps.add(new AppStatus(app.id(), installed, connected, summaryLines));
        }
        return apps;
    }

    private void sendFeedback(ServerPlayer player, String message, boolean error) {
        if (player == null) {
            return;
        }
        String safe = message == null ? "" : message;
        if (safe.length() > MAX_FEEDBACK_LENGTH) {
            safe = safe.substring(0, MAX_FEEDBACK_LENGTH);
        }
        if (!isReachable(player, MenuFeedbackPayload.TYPE)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new MenuFeedbackPayload(safe, error));
    }

    /** 网络连接有效且已注册本 Mod 频道时才发包。 */
    private static boolean isReachable(ServerPlayer player, CustomPacketPayload.Type<?> payloadType) {
        return player != null && player.connection != null
                && player.connection.getConnection() != null
                && player.connection.getConnection().isConnected()
                && NetworkRegistry.hasChannel(player.connection, payloadType.id());
    }
}
