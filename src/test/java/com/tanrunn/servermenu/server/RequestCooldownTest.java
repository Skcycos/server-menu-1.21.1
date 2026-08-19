package com.tanrunn.servermenu.server;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestCooldownTest {

    @Test
    void acceptsFirstRequest() {
        RequestCooldown cooldown = new RequestCooldown(500);
        UUID uuid = UUID.randomUUID();
        assertTrue(cooldown.tryAcquire(uuid, 1_000));
    }

    @Test
    void rejectsRequestsInsideWindow() {
        RequestCooldown cooldown = new RequestCooldown(500);
        UUID uuid = UUID.randomUUID();
        assertTrue(cooldown.tryAcquire(uuid, 1_000));
        assertFalse(cooldown.tryAcquire(uuid, 1_001));
        assertFalse(cooldown.tryAcquire(uuid, 1_499));
    }

    @Test
    void acceptsWhenWindowElapses() {
        RequestCooldown cooldown = new RequestCooldown(500);
        UUID uuid = UUID.randomUUID();
        assertTrue(cooldown.tryAcquire(uuid, 1_000));
        assertTrue(cooldown.tryAcquire(uuid, 1_500));
    }

    @Test
    void removeAllowsImmediateReacquire() {
        RequestCooldown cooldown = new RequestCooldown(500);
        UUID uuid = UUID.randomUUID();
        assertTrue(cooldown.tryAcquire(uuid, 1_000));
        cooldown.remove(uuid);
        assertTrue(cooldown.tryAcquire(uuid, 1_000));
    }

    @Test
    void clearResetsAllState() {
        RequestCooldown cooldown = new RequestCooldown(500);
        UUID uuid = UUID.randomUUID();
        assertTrue(cooldown.tryAcquire(uuid, 1_000));
        assertFalse(cooldown.tryAcquire(uuid, 1_001));
        cooldown.clear();
        assertTrue(cooldown.tryAcquire(uuid, 1_002));
    }

    @Test
    void rejectsNullUuid() {
        RequestCooldown cooldown = new RequestCooldown(500);
        assertFalse(cooldown.tryAcquire(null, 1_000));
    }

    @Test
    void playersAreIsolated() {
        RequestCooldown cooldown = new RequestCooldown(500);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(cooldown.tryAcquire(first, 1_000));
        assertTrue(cooldown.tryAcquire(second, 1_000));
        assertFalse(cooldown.tryAcquire(first, 1_001));
        // 两个玩家互不影响：second 在窗口结束后可再次获取。
        assertTrue(cooldown.tryAcquire(second, 1_500));
        assertFalse(cooldown.tryAcquire(second, 1_501));
        assertTrue(cooldown.tryAcquire(first, 1_500));
    }

    @Test
    void zeroWindowAcceptsEveryRequest() {
        RequestCooldown cooldown = new RequestCooldown(0);
        UUID uuid = UUID.randomUUID();
        assertTrue(cooldown.tryAcquire(uuid, 1_000));
        assertTrue(cooldown.tryAcquire(uuid, 1_000));
    }
}
