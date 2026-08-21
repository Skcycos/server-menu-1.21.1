package com.tanrunn.servermenu.server.integration.economy;

import com.tanrunn.servermenu.api.economy.EconomyInputs;
import com.tanrunn.servermenu.api.economy.EconomyTransactionResult;
import com.tanrunn.servermenu.api.economy.EconomyTransactionStatus;

/**
 * 纯逻辑经济交易执行器（无 Minecraft / LC 依赖，可完整单测）。
 *
 * <p>{@link AccountHandle} 是玩家资金账户的最小抽象：由 LC 适配器针对每个玩家
 * 实现（负责 LC 账户解析、MoneyValue 精确转换、BankAPI 调用），交易时序、余额
 * 判定、部分扣款补偿、幂等重放与冲突判定全部在本类完成并可用替身单测。</p>
 *
 * <p>不变量：
 * <ul>
 *   <li>先校验入参（amount&gt;0、长度限制），非法直接拒绝；</li>
 *   <li>不可用 / 被隔离维度直接拒绝（fail closed）；</li>
 *   <li>交易前读取余额并确认足额（扣款）；</li>
 *   <li>扣款必须是精确金额：实际扣款 != 请求金额一律视为部分扣款并立即补偿；</li>
 *   <li>补偿失败返回 {@link EconomyTransactionStatus#COMPENSATION_FAILED}
 *       （配合外层 critical/error 日志与人工审计错误）；</li>
 *   <li>同一 requestId 重放返回原结果；同一 requestId 不同方向或不同金额被拒绝；</li>
 *   <li>余额运算使用 {@link Math#addExact}/{@link Math#subtractExact} 防溢出。</li>
 * </ul></p>
 */
public final class BankOperationCore {

    private BankOperationCore() {
    }

    /** 玩家资金账户抽象（LC 适配器实现；测试用替身）。 */
    public interface AccountHandle {
        boolean isAvailable();

        boolean isQuarantined();

        long balanceMinorUnits();

        /**
         * 尝试精确扣款 {@code amountMinorUnits}。
         * SUCCESS/actual==amount：足额扣款；PARTIAL/actual&lt;amount：部分扣款；
         * FAILED：硬失败（转换失败 / 提供者错误）。
         */
        Outcome withdraw(long amountMinorUnits);

        /** 尝试精确入账；PARTIAL 表示实际入账少于请求（防御性补偿用）。 */
        Outcome deposit(long amountMinorUnits);
    }

    /** 账户操作结果分类。 */
    public enum ResultKind {
        SUCCESS, PARTIAL, FAILED
    }

    public record Outcome(ResultKind result, long actualMinorUnits, EconomyTransactionStatus failStatus) {
        public static Outcome success(long actualMinorUnits) {
            return new Outcome(ResultKind.SUCCESS, actualMinorUnits, null);
        }

        public static Outcome partial(long actualMinorUnits) {
            return new Outcome(ResultKind.PARTIAL, actualMinorUnits, null);
        }

        public static Outcome failed(EconomyTransactionStatus status) {
            return new Outcome(ResultKind.FAILED, 0, status);
        }

        boolean fullSuccess(long amount) {
            return result == ResultKind.SUCCESS && actualMinorUnits == amount;
        }
    }

    /** 幂等账本（提供者持有；测试/LC 适配器可注入替身）。 */
    public interface IdempotencyLedger {
        Record find(String playerKey, String requestId);

        void remember(String playerKey, String requestId, Record record);
    }

    public enum Operation {
        WITHDRAW, DEPOSIT,
        /**
         * 该 requestId 已被“冲正”（如 BuildShop 退款后写入的墓碑）。
         * 同一 requestId 的任何后续操作都视为 REQUEST_CONFLICT，防止
         * “已退款交易再次扣款”造成免费发货。
         */
        REVERSED
    }

    public record Record(Operation operation, long amountMinorUnits, EconomyTransactionResult result) {
    }

    /** 构造“已冲正”墓碑记录（供 BuildShop 退款写入，阻止该 requestId 复用）。 */
    public static Record reversedRecord(String providerId, String requestId) {
        EconomyTransactionResult conflict = EconomyTransactionResult.failure(
                EconomyTransactionStatus.REQUEST_CONFLICT, "该请求已被冲正", providerId, requestId, 0, 0);
        return new Record(Operation.REVERSED, 0, conflict);
    }

    // ---------------------------------------------------------------- entry points

    public static EconomyTransactionResult withdraw(String providerId, String playerKey,
                                                    long amountMinorUnits, String source, String reason,
                                                    String requestId, AccountHandle account, IdempotencyLedger ledger) {
        return execute(Operation.WITHDRAW, providerId, playerKey, amountMinorUnits,
                source, reason, requestId, account, ledger);
    }

    public static EconomyTransactionResult deposit(String providerId, String playerKey,
                                                   long amountMinorUnits, String source, String reason,
                                                   String requestId, AccountHandle account, IdempotencyLedger ledger) {
        return execute(Operation.DEPOSIT, providerId, playerKey, amountMinorUnits,
                source, reason, requestId, account, ledger);
    }

    // ---------------------------------------------------------------- internals

    private static EconomyTransactionResult execute(Operation op, String providerId, String playerKey,
                                                    long amountMinorUnits, String source, String reason,
                                                    String requestId, AccountHandle account, IdempotencyLedger ledger) {
        String pid = providerId == null ? "?" : providerId;
        EconomyTransactionStatus invalid = EconomyInputs.validate(amountMinorUnits, source, reason, requestId);
        if (invalid != null) {
            return EconomyTransactionResult.failure(invalid, fixedMessage(invalid), pid, requestId, 0, 0);
        }
        if (account == null || !account.isAvailable()) {
            return EconomyTransactionResult.failure(EconomyTransactionStatus.UNAVAILABLE,
                    "银行桥接当前不可用", pid, requestId, 0, 0);
        }
        if (account.isQuarantined()) {
            return EconomyTransactionResult.failure(EconomyTransactionStatus.QUARANTINED,
                    "当前维度禁止银行交易", pid, requestId, 0, account.balanceMinorUnits());
        }

        // 幂等重放 / 冲突判定
        Record existing = ledger == null ? null : ledger.find(playerKey, requestId);
        if (existing != null) {
            if (existing.operation() == Operation.REVERSED) {
                return EconomyTransactionResult.failure(EconomyTransactionStatus.REQUEST_CONFLICT,
                        "该请求已被冲正", pid, requestId, 0, account.balanceMinorUnits());
            }
            if (existing.operation() == op && existing.amountMinorUnits() == amountMinorUnits) {
                return replay(existing.result(), account.balanceMinorUnits());
            }
            return EconomyTransactionResult.failure(EconomyTransactionStatus.REQUEST_CONFLICT,
                    "该请求已用于另一笔方向或金额", pid, requestId, 0, account.balanceMinorUnits());
        }

        if (op == Operation.WITHDRAW) {
            return doWithdraw(pid, playerKey, amountMinorUnits, requestId, account, ledger);
        }
        return doDeposit(pid, playerKey, amountMinorUnits, requestId, account, ledger);
    }

    private static EconomyTransactionResult doWithdraw(String providerId, String playerKey,
                                                       long amountMinorUnits, String requestId,
                                                       AccountHandle account, IdempotencyLedger ledger) {
        long balance = account.balanceMinorUnits();
        if (balance < amountMinorUnits) {
            return EconomyTransactionResult.failure(EconomyTransactionStatus.INSUFFICIENT_FUNDS,
                    "银行余额不足", providerId, requestId, 0, balance);
        }
        Outcome outcome = account.withdraw(amountMinorUnits);
        if (outcome.result() == ResultKind.FAILED) {
            EconomyTransactionStatus status = outcome.failStatus() == null
                    ? EconomyTransactionStatus.PROVIDER_ERROR : outcome.failStatus();
            return EconomyTransactionResult.failure(status, fixedMessage(status),
                    providerId, requestId, 0, account.balanceMinorUnits());
        }
        long actual = outcome.actualMinorUnits();
        if (!outcome.fullSuccess(amountMinorUnits)) {
            // 部分扣款：立即补偿已扣金额，补偿失败返回 COMPENSATION_FAILED。
            if (actual > 0) {
                Outcome compensation = account.deposit(actual);
                if (compensation.result() != ResultKind.SUCCESS) {
                    return EconomyTransactionResult.failure(EconomyTransactionStatus.COMPENSATION_FAILED,
                            "部分扣款且补偿失败，请联系管理员审计", providerId, requestId,
                            actual, account.balanceMinorUnits());
                }
            }
            return EconomyTransactionResult.failure(EconomyTransactionStatus.PARTIAL_OPERATION,
                    "部分扣款已全额补偿，本次操作未完成", providerId, requestId,
                    actual, account.balanceMinorUnits());
        }
        long[] next = {0};
        if (!EconomyInputs.trySubtractNonNegative(balance, amountMinorUnits, next)) {
            return EconomyTransactionResult.failure(EconomyTransactionStatus.AMOUNT_OVERFLOW,
                    "银行余额超出可用范围", providerId, requestId, actual, account.balanceMinorUnits());
        }
        // 余额以操作后的账户真实值为准（避免预读与操作之间的微小漂移造成误导）。
        EconomyTransactionResult ok = EconomyTransactionResult.success(
                providerId, requestId, amountMinorUnits, account.balanceMinorUnits());
        if (ledger != null) {
            ledger.remember(playerKey, requestId,
                    new Record(Operation.WITHDRAW, amountMinorUnits, ok));
        }
        return ok;
    }

    private static EconomyTransactionResult doDeposit(String providerId, String playerKey,
                                                      long amountMinorUnits, String requestId,
                                                      AccountHandle account, IdempotencyLedger ledger) {
        long balance = account.balanceMinorUnits();
        long[] next = {0};
        if (!EconomyInputs.tryAdd(balance, amountMinorUnits, next)) {
            return EconomyTransactionResult.failure(EconomyTransactionStatus.AMOUNT_OVERFLOW,
                    "银行余额超出可用范围", providerId, requestId, 0, balance);
        }
        Outcome outcome = account.deposit(amountMinorUnits);
        if (outcome.result() == ResultKind.FAILED) {
            EconomyTransactionStatus status = outcome.failStatus() == null
                    ? EconomyTransactionStatus.PROVIDER_ERROR : outcome.failStatus();
            return EconomyTransactionResult.failure(status, fixedMessage(status),
                    providerId, requestId, 0, account.balanceMinorUnits());
        }
        if (!outcome.fullSuccess(amountMinorUnits)) {
            // 防御性处理部分入账：补偿已入账金额。
            long actual = outcome.actualMinorUnits();
            if (actual > 0) {
                Outcome compensation = account.withdraw(actual);
                if (compensation.result() != ResultKind.SUCCESS) {
                    return EconomyTransactionResult.failure(EconomyTransactionStatus.COMPENSATION_FAILED,
                            "部分入账且补偿失败，请联系管理员审计", providerId, requestId,
                            actual, account.balanceMinorUnits());
                }
            }
            return EconomyTransactionResult.failure(EconomyTransactionStatus.PARTIAL_OPERATION,
                    "部分入账已全额冲回，本次操作未完成", providerId, requestId,
                    actual, account.balanceMinorUnits());
        }
        // 余额以操作后的账户真实值为准。
        EconomyTransactionResult ok = EconomyTransactionResult.success(
                providerId, requestId, amountMinorUnits, account.balanceMinorUnits());
        if (ledger != null) {
            ledger.remember(playerKey, requestId,
                    new Record(Operation.DEPOSIT, amountMinorUnits, ok));
        }
        return ok;
    }

    /** 重放：保留原结果状态与处理金额，余额刷新为当前值。 */
    private static EconomyTransactionResult replay(EconomyTransactionResult stored, long freshBalance) {
        if (stored == null) {
            return EconomyTransactionResult.failure(EconomyTransactionStatus.REQUEST_CONFLICT,
                    "该请求已被处理过", "?", "", 0, freshBalance);
        }
        return new EconomyTransactionResult(stored.success(), stored.status(),
                stored.processedMinorUnits(), freshBalance, stored.message(),
                stored.providerId(), stored.requestId(), true);
    }

    private static String fixedMessage(EconomyTransactionStatus status) {
        return switch (status) {
            case INVALID_AMOUNT -> "金额不合法";
            case INVALID_REQUEST -> "请求参数不合法";
            case CONVERSION_FAILED -> "金额转换失败";
            case PROVIDER_ERROR -> "银行操作失败，请稍后再试";
            case AMOUNT_OVERFLOW -> "金额超出可用范围";
            default -> "银行操作失败";
        };
    }
}
