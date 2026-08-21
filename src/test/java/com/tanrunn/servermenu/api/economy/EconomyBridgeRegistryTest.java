package com.tanrunn.servermenu.api.economy;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link EconomyBridgeRegistry} 的 fail closed 语义测试。
 */
class EconomyBridgeRegistryTest {

    private static final String ID = "server_menu:lc_bank_main";

    @AfterEach
    void cleanUp() {
        EconomyBridgeRegistry.resetForTesting();
    }

    private static ServerPlayer player() {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getGameProfile()).thenReturn(new GameProfile(UUID.randomUUID(), "tester"));
        return player;
    }

    @Test
    void unregisteredProviderIsUnavailable() {
        assertEquals(0, EconomyBridgeRegistry.size());
        assertFalse(EconomyBridgeRegistry.isAvailable(ID));
        EconomyBalance balance = EconomyBridgeRegistry.balance(ID, player());
        assertFalse(balance.available());
        // 未注册 → 交易 UNAVAILABLE（fail closed）
        EconomyTransactionResult r = EconomyBridgeRegistry.withdrawMinorUnits(
                ID, player(), 100, "test", "reason", "r1");
        assertEquals(EconomyTransactionStatus.UNAVAILABLE, r.status());
    }

    @Test
    void nullPlayerFailsClosedEvenRegistered() {
        EconomyBridgeRegistry.register(new FakeProvider(ID, true, 10_000, null));
        EconomyTransactionResult r = EconomyBridgeRegistry.withdrawMinorUnits(ID, null, 100,
                "test", "reason", "r2");
        assertEquals(EconomyTransactionStatus.UNAVAILABLE, r.status());
    }

    @Test
    void registeredButUnavailableProviderFailsClosed() {
        EconomyBridgeRegistry.register(new FakeProvider(ID, false, 10_000, null));
        assertFalse(EconomyBridgeRegistry.isAvailable(ID));
        EconomyTransactionResult r = EconomyBridgeRegistry.withdrawMinorUnits(
                ID, player(), 100, "test", "reason", "r3");
        assertEquals(EconomyTransactionStatus.UNAVAILABLE, r.status());
    }

    @Test
    void registeredAvailableProviderExecutesAndReportsBalance() {
        EconomyBridgeRegistry.register(new FakeProvider(ID, true, 10_000, null));
        assertTrue(EconomyBridgeRegistry.isAvailable(ID));
        assertEquals(1, EconomyBridgeRegistry.size());
        assertTrue(EconomyBridgeRegistry.ids().contains(ID));

        EconomyBalance balance = EconomyBridgeRegistry.balance(ID, player());
        assertTrue(balance.available());
        assertEquals(10_000, balance.minorUnits());
        assertEquals("main", balance.chain());

        EconomyTransactionResult r = EconomyBridgeRegistry.withdrawMinorUnits(
                ID, player(), 100, "test", "reason", "r4");
        assertTrue(r.success());
        assertEquals(9_900, r.balanceMinorUnits());

        EconomyTransactionResult d = EconomyBridgeRegistry.depositMinorUnits(
                ID, player(), 50, "test", "reason", "r5");
        assertTrue(d.success());
        assertEquals(9_950, d.balanceMinorUnits());
    }

    @Test
    void providerExceptionConvertedToProviderError() {
        FakeProvider throwing = new FakeProvider(ID, true, 10_000, new IllegalStateException("boom"));
        EconomyBridgeRegistry.register(throwing);
        EconomyTransactionResult r = EconomyBridgeRegistry.withdrawMinorUnits(
                ID, player(), 100, "test", "reason", "r6");
        assertEquals(EconomyTransactionStatus.PROVIDER_ERROR, r.status());
        assertFalse(r.success());
    }

    // ---- 第三方 provider 抛异常时的 fail-closed 边界（本轮审查修复） ----

    @Test
    void isAvailableThrowingProviderTreatsAsUnavailable() {
        EconomyBridgeRegistry.register(FakeProvider.unavailableOnProbe(ID));
        assertFalse(EconomyBridgeRegistry.isAvailable(ID));
        assertFalse(EconomyBridgeRegistry.balance(ID, player()).available());
        EconomyTransactionResult r = EconomyBridgeRegistry.withdrawMinorUnits(
                ID, player(), 100, "test", "reason", "r7");
        assertEquals(EconomyTransactionStatus.UNAVAILABLE, r.status());
    }

    @Test
    void balanceThrowingProviderFailsClosed() {
        EconomyBridgeRegistry.register(FakeProvider.throwingOnBalance(ID, new RuntimeException("balance-boom")));
        EconomyBalance balance = EconomyBridgeRegistry.balance(ID, player());
        assertFalse(balance.available());
        assertEquals(0, balance.minorUnits());
    }

    @Test
    void formatThrowingProviderFallsBackToPlainNumber() {
        EconomyBridgeRegistry.register(FakeProvider.throwingOnFormat(ID, new RuntimeException("format-boom")));
        assertEquals("1234", EconomyBridgeRegistry.format(ID, 1234));
    }

    @Test
    void linkageErrorFromProviderConvertedToProviderErrorNotCrashed() {
        EconomyBridgeRegistry.register(FakeProvider.throwingOnCall(ID, new NoClassDefFoundError("io/example/Missing")));
        EconomyTransactionResult r = EconomyBridgeRegistry.withdrawMinorUnits(
                ID, player(), 100, "test", "reason", "r8");
        assertEquals(EconomyTransactionStatus.PROVIDER_ERROR, r.status());
    }

    @Test
    void outOfMemoryFromProviderIsNotSwallowed() {
        // 真正 JVM 致命错误必须继续抛出（不吞）。
        EconomyBridgeRegistry.register(FakeProvider.throwingOnCall(ID, new OutOfMemoryError("simulated")));
        org.junit.jupiter.api.Assertions.assertThrows(OutOfMemoryError.class, () ->
                EconomyBridgeRegistry.withdrawMinorUnits(ID, player(), 100, "test", "reason", "r9"));
    }

    // ---- 第三轮：provider 元数据方法异常（providerId / currencyChain）不得二次抛出 ----

    @Test
    void registerWithThrowingProviderIdThrowsControlledIllegalArgument() {
        EconomyProvider boom = FakeProvider.throwingMetaId(ID, new RuntimeException("id-boom"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> EconomyBridgeRegistry.register(boom));
    }

    @Test
    void registerWithLinkageErrorProviderIdThrowsControlledIllegalArgument() {
        EconomyProvider boom = FakeProvider.throwingMetaId(ID, new NoClassDefFoundError("io/example/Missing"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> EconomyBridgeRegistry.register(boom));
    }

    @Test
    void currencyChainThrowsFailsClosedOnBalanceRead() {
        // 不可用 provider：balance 走不可用快照，会读取 currencyChain；读取抛异常 → 依旧不可用、不抛。
        EconomyProvider p = FakeProvider.throwingMetaChain(ID, false);
        EconomyBridgeRegistry.register(p);
        assertFalse(EconomyBridgeRegistry.isAvailable(ID));
        EconomyBalance balance = org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> EconomyBridgeRegistry.balance(ID, player()));
        assertFalse(balance.available());
    }

    @Test
    void providerIdThrowsAfterRegistrationIsSafeInReadPaths() {
        FakeProvider flaky = FakeProvider.flakyMetaId(ID);
        EconomyBridgeRegistry.register(flaky);
        assertTrue(EconomyBridgeRegistry.isAvailable(ID));
        flaky.armMetaIdFailure();          // providerId 之后抛异常
        flaky.armBalanceFailure(new IllegalStateException("balance-boom")); // balance 也抛
        // 读路径（unavailable 快照/日志）会读取 providerId；不得再次抛出。
        EconomyBalance balance = org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> EconomyBridgeRegistry.balance(ID, player()));
        assertFalse(balance.available());
    }

    // ---- 第三/四轮：safeBalance 的错误边界（只捕 RuntimeException/LinkageError） ----

    private FakeProvider transactionFailureWithBalanceThrowing(Throwable balanceThrow) {
        FakeProvider p = FakeProvider.throwingOnCall(ID, new IllegalStateException("tx-boom"));
        p.setBalanceFailure(balanceThrow);
        return p;
    }

    @Test
    void safeBalanceThrowingRuntimeExceptionFailsClosed() {
        EconomyBridgeRegistry.register(transactionFailureWithBalanceThrowing(new IllegalStateException("b-boom")));
        EconomyTransactionResult r = org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> EconomyBridgeRegistry.withdrawMinorUnits(ID, player(), 100, "test", "reason", "sb1"));
        assertEquals(EconomyTransactionStatus.PROVIDER_ERROR, r.status());
    }

    @Test
    void safeBalanceThrowingLinkageErrorFailsClosed() {
        EconomyBridgeRegistry.register(transactionFailureWithBalanceThrowing(new NoClassDefFoundError("io/x")));
        EconomyTransactionResult r = org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> EconomyBridgeRegistry.withdrawMinorUnits(ID, player(), 100, "test", "reason", "sb2"));
        assertEquals(EconomyTransactionStatus.PROVIDER_ERROR, r.status());
    }

    @Test
    void safeBalanceThrowingOutOfMemoryPropagates() {
        EconomyBridgeRegistry.register(transactionFailureWithBalanceThrowing(new OutOfMemoryError("simulated")));
        org.junit.jupiter.api.Assertions.assertThrows(OutOfMemoryError.class, () ->
                EconomyBridgeRegistry.withdrawMinorUnits(ID, player(), 100, "test", "reason", "sb3"));
    }

    @Test
    void safeBalanceThrowingThreadDeathPropagates() {
        EconomyBridgeRegistry.register(transactionFailureWithBalanceThrowing(new ThreadDeath()));
        org.junit.jupiter.api.Assertions.assertThrows(ThreadDeath.class, () ->
                EconomyBridgeRegistry.withdrawMinorUnits(ID, player(), 100, "test", "reason", "sb4"));
    }

    @Test
    void safeBalanceThrowingAssertionErrorPropagates() {
        EconomyBridgeRegistry.register(transactionFailureWithBalanceThrowing(new AssertionError("assert")));
        org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, () ->
                EconomyBridgeRegistry.withdrawMinorUnits(ID, player(), 100, "test", "reason", "sb5"));
    }

    @Test
    void unregisterRemovesProvider() {
        EconomyBridgeRegistry.register(new FakeProvider(ID, true, 10_000, null));
        EconomyBridgeRegistry.unregister(ID);
        assertFalse(EconomyBridgeRegistry.isAvailable(ID));
        assertEquals(0, EconomyBridgeRegistry.size());
    }

    @Test
    void formatFallsBackWithoutProvider() {
        assertEquals("1234", EconomyBridgeRegistry.format(ID, 1234));
        EconomyBridgeRegistry.register(new FakeProvider(ID, true, 0, null));
        assertEquals("1,234 铜币", EconomyBridgeRegistry.format(ID, 1234));
    }

    /** 可注入余额/异常行为的 fake 提供者。 */
    static final class FakeProvider implements EconomyProvider {
        private final String id;
        private boolean available;
        private long balance;
        private Throwable probeFailure;
        private Throwable balanceFailure;
        private Throwable callFailure;
        private Throwable formatFailure;
        private boolean metaIdArmed;
        private boolean metaChainThrows;

        FakeProvider(String id, boolean available, long balance, RuntimeException whenThrowing) {
            this.id = id;
            this.available = available;
            this.balance = balance;
            this.callFailure = whenThrowing;
            this.metaChainThrows = false;
        }

        static FakeProvider unavailableOnProbe(String id) {
            FakeProvider p = new FakeProvider(id, true, 0, null);
            p.probeFailure = new IllegalStateException("probe-boom");
            return p;
        }

        static FakeProvider throwingOnBalance(String id, RuntimeException failure) {
            FakeProvider p = new FakeProvider(id, true, 0, null);
            p.balanceFailure = failure;
            return p;
        }

        static FakeProvider throwingOnFormat(String id, RuntimeException failure) {
            FakeProvider p = new FakeProvider(id, true, 0, null);
            p.formatFailure = failure;
            return p;
        }

        static FakeProvider throwingOnCall(String id, Throwable failure) {
            FakeProvider p = new FakeProvider(id, true, 0, null);
            p.callFailure = failure;
            return p;
        }

        static FakeProvider throwingMetaId(String id, Throwable failure) {
            FakeProvider p = flakyMetaId(id);
            p.metaArmed(failure);
            return p;
        }

        static FakeProvider throwingMetaChain(String id, boolean available) {
            FakeProvider p = new FakeProvider(id, available, 0, null);
            p.chainThrows();
            return p;
        }

        static FakeProvider flakyMetaId(String id) {
            return new FakeProvider(id, true, 0, null);
        }

        FakeProvider chainThrows() {
            this.metaChainThrows = true;
            return this;
        }

        void armMetaIdFailure() {
            this.metaIdArmed = true;
        }

        void armBalanceFailure(RuntimeException failure) {
            this.balanceFailure = failure;
        }

        void setBalanceFailure(Throwable failure) {
            this.balanceFailure = failure;
        }

        private void metaArmed(Throwable failure) {
            this.metaIdArmed = true;
            // reuse field for message detail
            this.callFailure = failure;
        }

        @Override
        public String providerId() {
            if (metaIdArmed) {
                // 重新抛出调用者给的那个异常（或构造一个新的）
                rethrow(callFailure != null
                        ? callFailure : new IllegalStateException("meta-id-boom"));
            }
            return id;
        }

        @Override
        public String displayName() { return "测试账户"; }

        @Override
        public String currencyChain() {
            if (metaChainThrows) {
                rethrow(new IllegalStateException("meta-chain-boom"));
            }
            return "main";
        }

        @Override
        public boolean isAvailable() {
            if (probeFailure != null) {
                rethrow(probeFailure);
            }
            return available;
        }

        @Override
        public EconomyBalance balance(ServerPlayer player) {
            if (balanceFailure != null) {
                rethrow(balanceFailure);
            }
            return EconomyBalance.of(available, false, balance, "main", id);
        }

        @Override
        public EconomyTransactionResult withdrawMinorUnits(ServerPlayer player, long amount,
                                                           String source, String reason, String requestId) {
            if (callFailure != null) {
                rethrow(callFailure);
            }
            balance -= amount;
            return EconomyTransactionResult.success(id, requestId, amount, balance);
        }

        @Override
        public EconomyTransactionResult depositMinorUnits(ServerPlayer player, long amount,
                                                          String source, String reason, String requestId) {
            if (callFailure != null) {
                rethrow(callFailure);
            }
            balance += amount;
            return EconomyTransactionResult.success(id, requestId, amount, balance);
        }

        @Override
        public String formatMinorUnits(long amount) {
            if (formatFailure != null) {
                rethrow(formatFailure);
            }
            return String.format("%,d", amount) + " 铜币";
        }

        /** RuntimeException/Error 原样抛出；受检异常包装后抛。 */
        private static void rethrow(Throwable t) {
            if (t instanceof RuntimeException re) {
                throw re;
            }
            if (t instanceof Error er) {
                throw er;
            }
            throw new RuntimeException(t);
        }
    }
}
