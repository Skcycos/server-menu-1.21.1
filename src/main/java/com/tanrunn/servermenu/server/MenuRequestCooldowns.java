package com.tanrunn.servermenu.server;

import java.util.UUID;

/**
 * Pad 菜单请求冷却协调（纯逻辑，无 Minecraft 依赖，可单测）。
 *
 * <p>打开/返回 Pad 请求与业务应用启动请求使用<b>两个独立冷却</b>：
 * 玩家点击 Pad 应用后业务页面立即打开，若在启动冷却窗口内点击"返回 Pad"，
 * 打开请求不应被启动冷却拒绝。两个冷却默认窗口均为 500ms。</p>
 */
public final class MenuRequestCooldowns {
    /** 打开/返回 Pad 请求的最小间隔（毫秒）。 */
    public static final long OPEN_COOLDOWN_MS = 500;
    /** 应用启动请求的最小间隔（毫秒）。 */
    public static final long LAUNCH_COOLDOWN_MS = 500;

    private final RequestCooldown openCooldown;
    private final RequestCooldown launchCooldown;

    public MenuRequestCooldowns() {
        this(OPEN_COOLDOWN_MS, LAUNCH_COOLDOWN_MS);
    }

    /** 测试用：自定义窗口。 */
    MenuRequestCooldowns(long openWindowMs, long launchWindowMs) {
        this.openCooldown = new RequestCooldown(openWindowMs);
        this.launchCooldown = new RequestCooldown(launchWindowMs);
    }

    /** 尝试获取一次打开/返回 Pad 请求配额。 */
    public boolean tryAcquireOpen(UUID uuid, long nowMs) {
        return openCooldown.tryAcquire(uuid, nowMs);
    }

    /** 尝试获取一次应用启动请求配额。 */
    public boolean tryAcquireLaunch(UUID uuid, long nowMs) {
        return launchCooldown.tryAcquire(uuid, nowMs);
    }

    /** 玩家退出：清理两个冷却，避免 UUID 无限保留。 */
    public void onPlayerLoggedOut(UUID uuid) {
        if (uuid == null) {
            return;
        }
        openCooldown.remove(uuid);
        launchCooldown.remove(uuid);
    }
}
