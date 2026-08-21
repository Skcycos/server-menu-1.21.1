package com.tanrunn.servermenu.server.integration.economy;

import com.tanrunn.servermenu.api.economy.EconomyTransactionResult;
import com.tanrunn.servermenu.api.economy.EconomyTransactionStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BankOperationCore} 的隔离测试（fake {@link BankOperationCore.AccountHandle}）。
 *
 * <p>覆盖任务清单里 LC bridge 的全部行为：LC 未安装（unavailable）、非服务端线程
 * （WRONG_THREAD 由 typed 适配器外层守卫，这里测核心不依赖线程）、amount=0/负数、
 * long 溢出、MoneyValue 转换不精确（CONVERSION_FAILED）、余额不足、被隔离维度、
 * LC 返回部分扣款、部分扣款补偿成功/失败、存款失败、重复 requestId、
 * 相同 requestId 不同金额/方向（REQUEST_CONFLICT）。</p>
 */
class BankOperationCoreTest {

    private static final String PROVIDER = "server_menu:lc_bank_main";
    private static final String PLAYER = "player-uuid-1";

    private final FakeAccount account = new FakeAccount();
    private final BankOperationCore.IdempotencyLedger ledger = new InMemoryIdempotencyLedger();

    // ---------------------------------------------------------------- 不可用 / 隔离

    @Test
    void unavailableFailsClosed() {
        account.available = false;
        EconomyTransactionResult r = BankOperationCore.withdraw(PROVIDER, PLAYER, 100,
                "test", "reason", "r1", account, ledger);
        assertEquals(EconomyTransactionStatus.UNAVAILABLE, r.status());
        assertFalse(r.success());
    }

    @Test
    void quarantinedDimensionRejected() {
        account.quarantined = true;
        EconomyTransactionResult r = BankOperationCore.withdraw(PROVIDER, PLAYER, 100,
                "test", "reason", "r2", account, ledger);
        assertEquals(EconomyTransactionStatus.QUARANTINED, r.status());
        assertEquals(0, account.withdrawCalls);
    }

    // ---------------------------------------------------------------- 金额校验

    @Test
    void amountZeroRejected() {
        EconomyTransactionResult r = BankOperationCore.withdraw(PROVIDER, PLAYER, 0,
                "test", "reason", "r3", account, ledger);
        assertEquals(EconomyTransactionStatus.INVALID_AMOUNT, r.status());
    }

    @Test
    void amountNegativeRejected() {
        EconomyTransactionResult r = BankOperationCore.deposit(PROVIDER, PLAYER, -5,
                "test", "reason", "r4", account, ledger);
        assertEquals(EconomyTransactionStatus.INVALID_AMOUNT, r.status());
    }

    @Test
    void requestIdTooLongRejected() {
        EconomyTransactionResult r = BankOperationCore.withdraw(PROVIDER, PLAYER, 100,
                "test", "reason", "a".repeat(65), account, ledger);
        assertEquals(EconomyTransactionStatus.INVALID_REQUEST, r.status());
    }

    @Test
    void depositOverflowRejected() {
        account.balance = Long.MAX_VALUE - 1;
        EconomyTransactionResult r = BankOperationCore.deposit(PROVIDER, PLAYER, 1000,
                "test", "reason", "r5", account, ledger);
        assertEquals(EconomyTransactionStatus.AMOUNT_OVERFLOW, r.status());
        assertEquals(0, account.depositCalls); // 溢出在真正入账前判定
    }

    // ---------------------------------------------------------------- 转换失败

    @Test
    void conversionFailureMapped() {
        account.withdrawOutcome = BankOperationCore.Outcome.failed(EconomyTransactionStatus.CONVERSION_FAILED);
        account.balance = 10_000;
        EconomyTransactionResult r = BankOperationCore.withdraw(PROVIDER, PLAYER, 100,
                "test", "reason", "r7", account, ledger);
        assertEquals(EconomyTransactionStatus.CONVERSION_FAILED, r.status());
        assertFalse(r.success());
    }

    // ---------------------------------------------------------------- 余额不足

    @Test
    void insufficientFundsWhenBalanceBelowAmount() {
        account.balance = 50;
        EconomyTransactionResult r = BankOperationCore.withdraw(PROVIDER, PLAYER, 100,
                "test", "reason", "r8", account, ledger);
        assertEquals(EconomyTransactionStatus.INSUFFICIENT_FUNDS, r.status());
        assertEquals(0, account.withdrawCalls); // 余额预查，不真正扣
    }

    // ---------------------------------------------------------------- 部分扣款与补偿

    @Test
    void partialWithdrawCompensatedReturnsPartialOperation() {
        account.balance = 10_000;
        account.withdrawOutcome = BankOperationCore.Outcome.partial(40); // 只扣到 40
        EconomyTransactionResult r = BankOperationCore.withdraw(PROVIDER, PLAYER, 100,
                "test", "reason", "r9", account, ledger);
        assertEquals(EconomyTransactionStatus.PARTIAL_OPERATION, r.status());
        assertFalse(r.success()); // 不得当作成功
        assertEquals(40, r.processedMinorUnits());
        assertEquals(1, account.depositCalls); // 已补偿 40
        assertFalse(r.duplicate());
        // 幂等账本不记录“成功”（避免重放被视为成功）
        assertEquals(null, ledger.find(PLAYER, "r9"));
    }

    @Test
    void partialWithdrawCompensationFailedReturnsCompensationFailed() {
        account.balance = 10_000;
        account.withdrawOutcome = BankOperationCore.Outcome.partial(40);
        account.depositFails = true;
        EconomyTransactionResult r = BankOperationCore.withdraw(PROVIDER, PLAYER, 100,
                "test", "reason", "r10", account, ledger);
        assertEquals(EconomyTransactionStatus.COMPENSATION_FAILED, r.status());
        assertEquals(40, r.processedMinorUnits());
        assertFalse(r.success());
    }

    @Test
    void fullWithdrawSuccessWhenExact() {
        account.balance = 10_000;
        EconomyTransactionResult r = BankOperationCore.withdraw(PROVIDER, PLAYER, 100,
                "test", "reason", "r11", account, ledger);
        assertEquals(EconomyTransactionStatus.SUCCESS, r.status());
        assertTrue(r.success());
        assertEquals(9_900, r.balanceMinorUnits());
        assertEquals(100, r.processedMinorUnits());
    }

    // ---------------------------------------------------------------- 存款失败

    @Test
    void depositHardFailureMappedFromAdapter() {
        account.balance = 10_000;
        account.depositOutcome = BankOperationCore.Outcome.failed(EconomyTransactionStatus.PROVIDER_ERROR);
        EconomyTransactionResult r = BankOperationCore.deposit(PROVIDER, PLAYER, 100,
                "test", "reason", "r12", account, ledger);
        assertEquals(EconomyTransactionStatus.PROVIDER_ERROR, r.status());
        assertFalse(r.success());
    }

    @Test
    void depositSuccess() {
        account.balance = 10_000;
        EconomyTransactionResult r = BankOperationCore.deposit(PROVIDER, PLAYER, 100,
                "test", "reason", "r13", account, ledger);
        assertTrue(r.success());
        assertEquals(10_100, r.balanceMinorUnits());
    }

    // ---------------------------------------------------------------- 幂等重放 / 冲突

    @Test
    void duplicateRequestIdReplaysOriginalSuccess() {
        account.balance = 10_000;
        EconomyTransactionResult first = BankOperationCore.withdraw(PROVIDER, PLAYER, 100,
                "test", "reason", "dup1", account, ledger);
        EconomyTransactionResult second = BankOperationCore.withdraw(PROVIDER, PLAYER, 100,
                "test", "reason", "dup1", account, ledger);
        assertTrue(first.success());
        assertTrue(second.success());
        assertTrue(second.duplicate());
        assertEquals(1, account.withdrawCalls); // 不重复扣款
    }

    @Test
    void sameRequestIdDifferentAmountRejected() {
        account.balance = 100_000;
        BankOperationCore.withdraw(PROVIDER, PLAYER, 100, "test", "reason", "conf1", account, ledger);
        EconomyTransactionResult r = BankOperationCore.withdraw(PROVIDER, PLAYER, 200,
                "test", "reason", "conf1", account, ledger);
        assertEquals(EconomyTransactionStatus.REQUEST_CONFLICT, r.status());
        assertEquals(1, account.withdrawCalls);
    }

    @Test
    void sameRequestIdDifferentDirectionRejected() {
        account.balance = 100_000;
        BankOperationCore.withdraw(PROVIDER, PLAYER, 100, "test", "reason", "conf2", account, ledger);
        EconomyTransactionResult r = BankOperationCore.deposit(PROVIDER, PLAYER, 100,
                "test", "reason", "conf2", account, ledger);
        assertEquals(EconomyTransactionStatus.REQUEST_CONFLICT, r.status());
        assertEquals(0, account.depositCalls);
    }

    @Test
    void reversedTombstoneRejectsAnyReuse() {
        ledger.remember(PLAYER, "rev1", BankOperationCore.reversedRecord(PROVIDER, "rev1"));
        EconomyTransactionResult r = BankOperationCore.withdraw(PROVIDER, PLAYER, 100,
                "test", "reason", "rev1", account, ledger);
        assertEquals(EconomyTransactionStatus.REQUEST_CONFLICT, r.status());
        EconomyTransactionResult d = BankOperationCore.deposit(PROVIDER, PLAYER, 100,
                "test", "reason", "rev1", account, ledger);
        assertEquals(EconomyTransactionStatus.REQUEST_CONFLICT, d.status());
        assertEquals(0, account.withdrawCalls);
        assertEquals(0, account.depositCalls);
    }

    // ---------------------------------------------------------------- helpers

    static final class FakeAccount implements BankOperationCore.AccountHandle {
        boolean available = true;
        boolean quarantined;
        long balance = 10_000;
        BankOperationCore.Outcome withdrawOutcome;
        BankOperationCore.Outcome depositOutcome;
        boolean depositFails;
        int withdrawCalls;
        int depositCalls;

        @Override
        public boolean isAvailable() { return available; }

        @Override
        public boolean isQuarantined() { return quarantined; }

        @Override
        public long balanceMinorUnits() { return balance; }

        @Override
        public BankOperationCore.Outcome withdraw(long amountMinorUnits) {
            if (withdrawOutcome != null) {
                if (withdrawOutcome.result() != BankOperationCore.ResultKind.FAILED) {
                    balance -= withdrawOutcome.actualMinorUnits();
                }
                withdrawCalls++;
                return withdrawOutcome;
            }
            withdrawCalls++;
            balance -= amountMinorUnits;
            return BankOperationCore.Outcome.success(amountMinorUnits);
        }

        @Override
        public BankOperationCore.Outcome deposit(long amountMinorUnits) {
            if (depositOutcome != null) {
                if (depositOutcome.result() != BankOperationCore.ResultKind.FAILED) {
                    balance += depositOutcome.actualMinorUnits();
                }
                depositCalls++;
                return depositOutcome;
            }
            if (depositFails) {
                depositCalls++;
                return BankOperationCore.Outcome.failed(EconomyTransactionStatus.PROVIDER_ERROR);
            }
            depositCalls++;
            balance += amountMinorUnits;
            return BankOperationCore.Outcome.success(amountMinorUnits);
        }
    }
}
