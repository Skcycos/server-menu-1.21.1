package com.tanrunn.servermenu.server;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 打开/返回 Pad 冷却与应用启动冷却拆分的纯逻辑测试。
 *
 * <p>关键回归场景：玩家点击 Pad 应用（launch 冷却记录）后业务页面立即打开，
 * 随后点击"返回 Pad"的 open 请求<b>不得</b>被 launch 冷却拒绝。</p>
 */
class MenuRequestCooldownsTest {

    @Test
    void openSucceedsFirstTime() {
        MenuRequestCooldowns c = new MenuRequestCooldowns();
        UUID uuid = UUID.randomUUID();
        assertTrue(c.tryAcquireOpen(uuid, 1_000));
    }

    @Test
    void launchSucceedsFirstTime() {
        MenuRequestCooldowns c = new MenuRequestCooldowns();
        UUID uuid = UUID.randomUUID();
        assertTrue(c.tryAcquireLaunch(uuid, 1_000));
    }

    @Test
    void launchDoesNotBlockOpenAtSameInstant() {
        // 回归：启动冷却不消耗打开配额。
        MenuRequestCooldowns c = new MenuRequestCooldowns();
        UUID uuid = UUID.randomUUID();
        assertTrue(c.tryAcquireLaunch(uuid, 1_000));
        assertTrue(c.tryAcquireOpen(uuid, 1_000));
    }

    @Test
    void openDoesNotBlockLaunchAtSameInstant() {
        MenuRequestCooldowns c = new MenuRequestCooldowns();
        UUID uuid = UUID.randomUUID();
        assertTrue(c.tryAcquireOpen(uuid, 1_000));
        assertTrue(c.tryAcquireLaunch(uuid, 1_000));
    }

    @Test
    void openRepeatsInsideWindowFail() {
        MenuRequestCooldowns c = new MenuRequestCooldowns();
        UUID uuid = UUID.randomUUID();
        assertTrue(c.tryAcquireOpen(uuid, 1_000));
        assertFalse(c.tryAcquireOpen(uuid, 1_200));
        assertTrue(c.tryAcquireOpen(uuid, 1_500));
    }

    @Test
    void launchRepeatsInsideWindowFail() {
        MenuRequestCooldowns c = new MenuRequestCooldowns();
        UUID uuid = UUID.randomUUID();
        assertTrue(c.tryAcquireLaunch(uuid, 1_000));
        assertFalse(c.tryAcquireLaunch(uuid, 1_200));
        assertTrue(c.tryAcquireLaunch(uuid, 1_500));
    }

    @Test
    void playerLogoutClearsBothCooldowns() {
        MenuRequestCooldowns c = new MenuRequestCooldowns();
        UUID uuid = UUID.randomUUID();
        assertTrue(c.tryAcquireOpen(uuid, 1_000));
        assertTrue(c.tryAcquireLaunch(uuid, 1_000));
        c.onPlayerLoggedOut(uuid);
        assertTrue(c.tryAcquireOpen(uuid, 1_000));
        assertTrue(c.tryAcquireLaunch(uuid, 1_000));
    }

    @Test
    void playersAreIsolatedAcrossBothCooldowns() {
        MenuRequestCooldowns c = new MenuRequestCooldowns();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(c.tryAcquireOpen(first, 1_000));
        assertTrue(c.tryAcquireLaunch(first, 1_000));
        // 第二个玩家在同一时刻各自成功（与第一个玩家互不影响）。
        assertTrue(c.tryAcquireOpen(second, 1_000));
        assertTrue(c.tryAcquireLaunch(second, 1_000));
        // 第一个玩家窗口内重复失败，不影响第二个玩家按自己的窗口恢复。
        assertFalse(c.tryAcquireOpen(first, 1_100));
        assertFalse(c.tryAcquireLaunch(first, 1_100));
        assertTrue(c.tryAcquireOpen(second, 1_500));
        assertTrue(c.tryAcquireLaunch(second, 1_500));
        // 第二个玩家恢复后又进入自己的窗口。
        assertFalse(c.tryAcquireOpen(second, 1_501));
        assertFalse(c.tryAcquireLaunch(second, 1_501));
        assertTrue(c.tryAcquireOpen(first, 1_500));
        assertTrue(c.tryAcquireLaunch(first, 1_500));
    }

    @Test
    void openAndLaunchAlternateIndependently() {
        MenuRequestCooldowns c = new MenuRequestCooldowns();
        UUID uuid = UUID.randomUUID();
        // 交替请求：两个冷却各自独立计时。
        assertTrue(c.tryAcquireLaunch(uuid, 1_000));
        assertTrue(c.tryAcquireOpen(uuid, 1_000));
        assertFalse(c.tryAcquireLaunch(uuid, 1_001));
        assertFalse(c.tryAcquireOpen(uuid, 1_001));
        assertTrue(c.tryAcquireLaunch(uuid, 1_500));
        assertTrue(c.tryAcquireOpen(uuid, 1_500));
        assertFalse(c.tryAcquireLaunch(uuid, 1_501));
        assertFalse(c.tryAcquireOpen(uuid, 1_501));
        assertTrue(c.tryAcquireLaunch(uuid, 2_000));
        assertTrue(c.tryAcquireOpen(uuid, 2_000));
    }

    @Test
    void nullUuidIsRejectedByBothCooldowns() {
        MenuRequestCooldowns c = new MenuRequestCooldowns();
        assertFalse(c.tryAcquireOpen(null, 1_000));
        assertFalse(c.tryAcquireLaunch(null, 1_000));
    }
}
