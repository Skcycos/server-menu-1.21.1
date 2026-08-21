package com.tanrunn.servermenu.server.integration.lc;

import com.mojang.authlib.GameProfile;
import com.tanrunn.buildshop.api.PaymentResult;
import com.tanrunn.servermenu.api.economy.EconomyBalance;
import com.tanrunn.servermenu.api.economy.EconomyOperationIds;
import com.tanrunn.servermenu.api.economy.EconomyProvider;
import com.tanrunn.servermenu.api.economy.EconomyTransactionResult;
import com.tanrunn.servermenu.api.economy.EconomyTransactionStatus;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link LcBuildShopCurrencyProvider}（server_menu:lc_bank_main）语义测试：
 * 本轮回修复重点——完整原始 key（可 &gt;64）本地查重、传 LC 前转 bs: 域 opId、
 * 退款金额校验、域名隔离防跨业务碰撞、幂等/退款/免费发货防护。
 */
class LcBuildShopCurrencyProviderTest {

    private static final String IDEM_KEY = "cobblestone|req-1|BULK|64";
    /** 超过 EconomyInputs 64 上限的合法 BuildShop 幂等键（stone_brick_stairs + UUID + BULK + 四位数量）。 */
    private static final String LONG_KEY =
            "stone_brick_stairs|6f9619ff-8b86-d011-b42d-00cf4fc964ff|BULK|1000..tail-beyond-64-characters";

    private final FakeEconomy economy = new FakeEconomy();
    private final List<String> reversals = new ArrayList<>();
    private final LcBuildShopCurrencyProvider provider = new LcBuildShopCurrencyProvider(
            economy, (player, requestId) -> reversals.add(requestId));
    private final ServerPlayer player = player();

    private static ServerPlayer player() {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getGameProfile()).thenReturn(new GameProfile(UUID.randomUUID(), "builder"));
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        return player;
    }

    // ---------------------------------------------------------------- 正常购买扣款

    @Test
    void normalPurchaseWithdrawDebitsBankOnce() {
        economy.available = true;
        economy.balance = 5000;
        assertEquals(5000, provider.balance(player));
        PaymentResult result = provider.withdraw(player, 100, "shop_purchase", IDEM_KEY);
        assertTrue(result.success());
        assertEquals(1, economy.withdrawRequests);
        assertEquals(4900, provider.balance(player));
    }

    @Test
    void withdrawPassesBsWithdrawOpIdToLcNotRawKey() {
        economy.available = true;
        economy.balance = 5000;
        provider.withdraw(player, 100, "shop_purchase", IDEM_KEY);
        // 原始 key 绝不能作为 LC 资金操作幂等键：LC 收到的是 bs:wd: 命名空间 opId。
        assertTrue(economy.lastWithdrawRequestId.startsWith(EconomyOperationIds.BS_WITHDRAW));
        assertNotEquals(IDEM_KEY, economy.lastWithdrawRequestId);
        assertTrue(economy.lastWithdrawRequestId.length() <= EconomyOperationIds.MAX_LENGTH);
    }

    @Test
    void canWithdrawUsesBankBalance() {
        economy.available = true;
        economy.balance = 100;
        assertTrue(provider.canWithdraw(player, 100));
        assertFalse(provider.canWithdraw(player, 101));
    }

    // ---------------------------------------------------------------- 幂等 / 冲突

    @Test
    void duplicateWithdrawSameKeySameAmountDoesNotDoubleCharge() {
        economy.available = true;
        economy.balance = 5000;
        assertTrue(provider.withdraw(player, 100, "shop_purchase", IDEM_KEY).success());
        PaymentResult second = provider.withdraw(player, 100, "shop_purchase", IDEM_KEY);
        assertTrue(second.success()); // 重放返回原始成功
        assertEquals(1, economy.withdrawRequests); // 不重复扣款
    }

    @Test
    void sameKeyDifferentAmountRejected() {
        economy.available = true;
        economy.balance = 50_000;
        provider.withdraw(player, 100, "shop_purchase", IDEM_KEY);
        PaymentResult second = provider.withdraw(player, 200, "shop_purchase", IDEM_KEY);
        assertFalse(second.success());
        assertEquals(1, economy.withdrawRequests);
    }

    @Test
    void withdrawWhenBankInsufficientFails() {
        economy.available = true;
        economy.balance = 50;
        PaymentResult result = provider.withdraw(player, 100, "shop_purchase", IDEM_KEY);
        assertFalse(result.success());
        assertEquals("buildshop.payment.insufficient_balance", result.messageKey());
        assertEquals(0, economy.withdrawRequests);
    }

    // ---------------------------------------------------------------- 退款金额校验（本轮修复）

    @Test
    void refundWithSameAmountSucceedsWithBsRefundOpId() {
        economy.available = true;
        economy.balance = 5000;
        provider.withdraw(player, 100, "shop_purchase", IDEM_KEY);
        PaymentResult refund = provider.refund(player, 100, "shop_rollback", IDEM_KEY);
        assertTrue(refund.success());
        assertEquals(1, economy.depositRequests);
        assertTrue(economy.lastDepositRequestId.startsWith(EconomyOperationIds.BS_REFUND));
        assertNotEquals(IDEM_KEY, economy.lastDepositRequestId);
        assertEquals(5000, provider.balance(player)); // 净值不变
        // 墓碑写在「扣款 opId」上（用以拒绝后续再扣，防免费发货）。
        assertEquals(1, reversals.size());
        assertTrue(reversals.get(0).startsWith(EconomyOperationIds.BS_WITHDRAW));
    }

    @Test
    void refundWithLargerAmountFailsWithoutTouchingLc() {
        economy.available = true;
        economy.balance = 5000;
        provider.withdraw(player, 100, "shop_purchase", IDEM_KEY);
        PaymentResult refund = provider.refund(player, 200, "shop_rollback", IDEM_KEY);
        assertFalse(refund.success());
        assertEquals(0, economy.depositRequests); // 校验失败且未调用 LC deposit
        assertEquals(4900, provider.balance(player)); // 原扣款保留
    }

    @Test
    void refundWithSmallerAmountFailsWithoutTouchingLc() {
        economy.available = true;
        economy.balance = 5000;
        provider.withdraw(player, 100, "shop_purchase", IDEM_KEY);
        PaymentResult refund = provider.refund(player, 50, "shop_rollback", IDEM_KEY);
        assertFalse(refund.success());
        assertEquals(0, economy.depositRequests);
        assertEquals(4900, provider.balance(player));
    }

    @Test
    void duplicateRefundSameAmountDoesNotDoubleRefund() {
        economy.available = true;
        economy.balance = 5000;
        provider.withdraw(player, 100, "shop_purchase", IDEM_KEY);
        assertTrue(provider.refund(player, 100, "shop_rollback", IDEM_KEY).success());
        PaymentResult second = provider.refund(player, 100, "shop_rollback", IDEM_KEY);
        assertTrue(second.success()); // 幂等
        assertEquals(1, economy.depositRequests); // 不重复退款
    }

    @Test
    void refundAfterRefundedWithDifferentAmountFails() {
        economy.available = true;
        economy.balance = 5000;
        provider.withdraw(player, 100, "shop_purchase", IDEM_KEY);
        provider.refund(player, 100, "shop_rollback", IDEM_KEY);
        PaymentResult different = provider.refund(player, 200, "shop_rollback", IDEM_KEY);
        assertFalse(different.success());
        assertEquals(1, economy.depositRequests); // 未再入账
    }

    @Test
    void refundWithoutSuccessfulWithdrawRejected() {
        economy.available = true;
        economy.balance = 5000;
        PaymentResult refund = provider.refund(player, 100, "shop_rollback", IDEM_KEY);
        assertFalse(refund.success());
        assertEquals(0, economy.depositRequests);
    }

    @Test
    void withdrawAfterRefundRejectedToPreventFreeGoods() {
        economy.available = true;
        economy.balance = 5000;
        provider.withdraw(player, 100, "shop_purchase", IDEM_KEY);
        provider.refund(player, 100, "shop_rollback", IDEM_KEY);
        PaymentResult again = provider.withdraw(player, 100, "shop_purchase", IDEM_KEY);
        assertFalse(again.success()); // 已退款再扣款必须拒绝
        assertEquals(1, economy.withdrawRequests);
        assertEquals(5000, provider.balance(player));
    }

    // ---------------------------------------------------------------- 超过 64 的合法幂等键（本轮修复）

    @Test
    void over64CharLegalKeyCompletesWithdrawAndRefund() {
        assertTrue(LONG_KEY.length() > 64);
        economy.available = true;
        economy.balance = 50_000;
        PaymentResult withdraw = provider.withdraw(player, 100, "shop_purchase", LONG_KEY);
        assertTrue(withdraw.success());
        assertEquals(1, economy.withdrawRequests);
        assertTrue(economy.lastWithdrawRequestId.length() <= EconomyOperationIds.MAX_LENGTH);

        PaymentResult refund = provider.refund(player, 100, "shop_rollback", LONG_KEY);
        assertTrue(refund.success());
        assertEquals(1, economy.depositRequests);
        assertEquals(50_000, provider.balance(player));
    }

    @Test
    void over64KeysSharingPrefixDoNotCollide() {
        // 只共享前 61 字符、尾部不同的 BuildShop key 必须走不同的 LC opId。
        String keyA = "oak_planks|11111111-2222-3333-4444-555555555555|BULK|0010|AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        String keyB = "oak_planks|11111111-2222-3333-4444-555555555555|BULK|0010|BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
        String opA = EconomyOperationIds.generate(EconomyOperationIds.BS_WITHDRAW,
                provider.id(), LcBuildShopCurrencyProvider.BS_SOURCE, "withdraw", keyA, "");
        String opB = EconomyOperationIds.generate(EconomyOperationIds.BS_WITHDRAW,
                provider.id(), LcBuildShopCurrencyProvider.BS_SOURCE, "withdraw", keyB, "");
        assertNotEquals(opA, opB);
    }

    // ---------------------------------------------------------------- 跨业务命名空间（本轮修复）

    @Test
    void buildShopDebitCannotBeReplayedAsStockMarketDebit() {
        // BuildShop 扣款成功后，StockMarket 用相同原始 requestId + 相同金额，
        // 由于命名空间不同（bs:wd: vs sm:bd:），LC 侧 opId 不同 → 必须产生新的真实扣款，不重放商店结果。
        economy.available = true;
        economy.balance = 50_000;
        provider.withdraw(player, 100, "shop_purchase", IDEM_KEY);
        assertEquals(1, economy.withdrawRequests);

        String stockMarketOpId = EconomyOperationIds.generate(EconomyOperationIds.SM_BANK_DEBIT,
                provider.id(), "server_menu_lc_bank", "bank_debit", IDEM_KEY, "DEPOSIT_TO_SECURITIES");
        assertNotEquals(economy.lastWithdrawRequestId, stockMarketOpId);
        assertNotEquals(economy.lastWithdrawRequestId, EconomyOperationIds.generate(
                EconomyOperationIds.SM_BANK_DEBIT, provider.id(), "server_menu_lc_bank",
                "bank_debit", IDEM_KEY, "DEPOSIT_TO_SECURITIES"));
    }

    @Test
    void stockMarketRequestIdCannotCollideWithBuildShopRefund() {
        // StockMarket 出金的补偿 opId（sm:rb:）与 BuildShop 退款 opId（bs:rf:）不同。
        String bsRefund = EconomyOperationIds.generate(EconomyOperationIds.BS_REFUND,
                provider.id(), "buildshop", "refund", IDEM_KEY, "");
        String smRollback = EconomyOperationIds.generate(EconomyOperationIds.SM_ROLLBACK,
                provider.id(), "server_menu_lc_bank", "rollback_securities", IDEM_KEY, "WITHDRAW_TO_BANK");
        assertNotEquals(bsRefund, smRollback);
    }

    // ---------------------------------------------------------------- 不可用

    @Test
    void providerUnavailableFailsClosedForAllOperations() {
        economy.available = false;
        assertEquals(0, provider.balance(player));
        assertFalse(provider.canWithdraw(player, 1));
        assertFalse(provider.withdraw(player, 100, "shop_purchase", IDEM_KEY).success());
        assertFalse(provider.refund(player, 100, "shop_rollback", IDEM_KEY).success());
        assertEquals(0, economy.withdrawRequests);
    }

    @Test
    void negativeAmountRejected() {
        economy.available = true;
        assertFalse(provider.withdraw(player, -1, "shop_purchase", IDEM_KEY).success());
    }

    @Test
    void zeroAmountRejected() {
        economy.available = true;
        assertFalse(provider.withdraw(player, 0, "shop_purchase", IDEM_KEY).success());
    }

    // ---------------------------------------------------------------- fake

    static final class FakeEconomy implements EconomyProvider {
        boolean available = true;
        long balance = 5000;
        int withdrawRequests;
        int depositRequests;
        String lastWithdrawRequestId;
        String lastDepositRequestId;

        @Override public String providerId() { return "server_menu:lc_bank_main"; }
        @Override public String displayName() { return "LC 银行账户"; }
        @Override public String currencyChain() { return "main"; }
        @Override public boolean isAvailable() { return available; }

        @Override
        public EconomyBalance balance(ServerPlayer player) {
            return EconomyBalance.of(available, false, balance, "main", providerId());
        }

        @Override
        public EconomyTransactionResult withdrawMinorUnits(ServerPlayer player, long amount,
                                                           String source, String reason, String requestId) {
            if (!available) {
                return EconomyTransactionResult.failure(EconomyTransactionStatus.UNAVAILABLE,
                        "不可用", providerId(), requestId, 0, balance);
            }
            if (balance < amount) {
                return EconomyTransactionResult.failure(EconomyTransactionStatus.INSUFFICIENT_FUNDS,
                        "余额不足", providerId(), requestId, 0, balance);
            }
            withdrawRequests++;
            lastWithdrawRequestId = requestId;
            balance -= amount;
            return EconomyTransactionResult.success(providerId(), requestId, amount, balance);
        }

        @Override
        public EconomyTransactionResult depositMinorUnits(ServerPlayer player, long amount,
                                                          String source, String reason, String requestId) {
            if (!available) {
                return EconomyTransactionResult.failure(EconomyTransactionStatus.UNAVAILABLE,
                        "不可用", providerId(), requestId, 0, balance);
            }
            depositRequests++;
            lastDepositRequestId = requestId;
            balance += amount;
            return EconomyTransactionResult.success(providerId(), requestId, amount, balance);
        }

        @Override
        public String formatMinorUnits(long amount) {
            return String.format("%,d", amount) + " 铜币";
        }
    }
}
