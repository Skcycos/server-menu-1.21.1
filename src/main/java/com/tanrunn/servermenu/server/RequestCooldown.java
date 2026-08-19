package com.tanrunn.servermenu.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 按玩家 UUID 的请求冷却（纯逻辑，无 Minecraft 依赖）。
 *
 * <p>同一 UUID 在窗口期内的重复请求被拒绝；窗口过后接受并刷新时间。
 * 时钟由调用方以毫秒时间戳传入，便于测试。</p>
 */
public final class RequestCooldown {
    private final long windowMs;
    private final Map<UUID, Long> lastAccepted = new HashMap<>();

    public RequestCooldown(long windowMs) {
        this.windowMs = Math.max(0, windowMs);
    }

    /**
     * 尝试获取一次请求配额。
     *
     * @param uuid 玩家 UUID
     * @param nowMs 当前毫秒时间戳
     * @return true 表示请求被接受（并记录时间）；冷却期内返回 false
     */
    public boolean tryAcquire(UUID uuid, long nowMs) {
        if (uuid == null) {
            return false;
        }
        Long last = lastAccepted.get(uuid);
        if (last != null && nowMs - last < windowMs) {
            return false;
        }
        lastAccepted.put(uuid, nowMs);
        return true;
    }

    /** 玩家退出时清理，避免 UUID 无限保留。 */
    public void remove(UUID uuid) {
        lastAccepted.remove(uuid);
    }

    /** 清空全部状态（服务器停止）。 */
    public void clear() {
        lastAccepted.clear();
    }
}
