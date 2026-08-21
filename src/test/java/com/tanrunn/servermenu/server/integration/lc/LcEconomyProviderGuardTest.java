package com.tanrunn.servermenu.server.integration.lc;

import com.mojang.authlib.GameProfile;
import com.tanrunn.servermenu.api.economy.EconomyTransactionResult;
import com.tanrunn.servermenu.api.economy.EconomyTransactionStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link LcEconomyProvider} 的 MC 相关守卫（非服务端线程 / LC 账户不可用时的
 * fail closed）。LC 转账户与补偿时序由 {@link BankOperationCoreTest} 覆盖。
 */
class LcEconomyProviderGuardTest {

    private final LcEconomyProvider provider = new LcEconomyProvider();

    private static ServerPlayer playerWithServerThread(boolean isServerThread) throws Exception {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getGameProfile()).thenReturn(new GameProfile(UUID.randomUUID(), "tester"));
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.isSameThread()).thenReturn(isServerThread);
        Field serverField = ServerPlayer.class.getField("server");
        serverField.setAccessible(true);
        serverField.set(player, server);
        return player;
    }

    @Test
    void wrongThreadReturnsWrongThreadStatus() throws Exception {
        ServerPlayer player = playerWithServerThread(false);
        EconomyTransactionResult r = provider.withdrawMinorUnits(
                player, 100, "test", "reason", "wg");
        assertEquals(EconomyTransactionStatus.WRONG_THREAD, r.status());
        assertFalse(r.success());
    }

    @Test
    void wrongThreadGuardsDepositToo() throws Exception {
        ServerPlayer player = playerWithServerThread(false);
        EconomyTransactionResult r = provider.depositMinorUnits(
                player, 100, "test", "reason", "wg2");
        assertEquals(EconomyTransactionStatus.WRONG_THREAD, r.status());
    }

    @Test
    void wrongThreadPrecedesAccountResolution() throws Exception {
        // 非服务端线程时不应触碰 LC 账户解析（mock 玩家 level 为 null 也不应 NPE）。
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getGameProfile()).thenReturn(new GameProfile(UUID.randomUUID(), "ghost"));
        EconomyTransactionResult r = provider.withdrawMinorUnits(
                player, 100, "test", "reason", "wg3");
        assertEquals(EconomyTransactionStatus.WRONG_THREAD, r.status());
    }
}
