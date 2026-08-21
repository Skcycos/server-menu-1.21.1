package com.tanrunn.servermenu.server.integration.lc;

import com.tanrunn.servermenu.api.economy.EconomyBalance;
import com.tanrunn.servermenu.api.economy.EconomyProvider;
import com.tanrunn.servermenu.api.economy.EconomyTransactionResult;
import com.tanrunn.servermenu.api.economy.EconomyTransactionStatus;
import com.tanrunn.servermenu.server.integration.economy.BankOperationCore;
import com.tanrunn.servermenu.server.integration.economy.InMemoryIdempotencyLedger;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * LC 经济桥接的 typed 适配器（引用 LC 类，compileOnly）。
 *
 * <p>只在 {@link LcEconomyBootstrap} 确认 LC 已安装且 {@link LcProbe} 通过后通过反射
 * 实例化并注册进 {@link com.tanrunn.servermenu.api.economy.EconomyBridgeRegistry}。
 * 本类绝不进入 server-menu 的发布 JAR 之外依赖 LC 类（compileOnly）。</p>
 *
 * <p>所有交易通过 {@link BankOperationCore} 执行：余额预查、精确转换校验、部分扣款补偿、
 * 幂等重放，全部由纯逻辑核心保证；本类只做线程守卫、LC 账户解析与异常日志。</p>
 */
public final class LcEconomyProvider implements EconomyProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LcEconomyProvider.class);

    private final BankOperationCore.IdempotencyLedger ledger = new InMemoryIdempotencyLedger();
    private volatile String uniqueNameCache;

    @Override
    public String providerId() {
        return LcConstants.PROVIDER_ID;
    }

    @Override
    public String displayName() {
        return LcConstants.PROVIDER_DISPLAY_NAME;
    }

    @Override
    public String currencyChain() {
        return LcConstants.MAIN_CHAIN;
    }

    /** 只在本类被确认可加载（LC 已装 + 探针通过）后实例化，因此这里恒返回 true。 */
    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public EconomyBalance balance(ServerPlayer player) {
        if (player == null) {
            return EconomyBalance.unavailable(providerId(), currencyChain());
        }
        if (!onServerThread(player)) {
            return EconomyBalance.unavailable(providerId(), currencyChain());
        }
        LcAccountHandle handle = resolve(player);
        if (handle == null || !handle.isAvailable()) {
            return EconomyBalance.unavailable(providerId(), currencyChain());
        }
        return EconomyBalance.of(true, handle.isQuarantined(), handle.balanceMinorUnits(),
                currencyChain(), providerId());
    }

    @Override
    public EconomyTransactionResult withdrawMinorUnits(ServerPlayer player, long amountMinorUnits,
                                                       String source, String reason, String requestId) {
        if (!onServerThread(player)) {
            return EconomyTransactionResult.failure(EconomyTransactionStatus.WRONG_THREAD,
                    "必须在服务端主线程操作", providerId(), requestId, 0, 0);
        }
        LcAccountHandle handle = resolve(player);
        if (handle == null) {
            return EconomyTransactionResult.failure(EconomyTransactionStatus.UNAVAILABLE,
                    "银行账户不可用", providerId(), requestId, 0, 0);
        }
        EconomyTransactionResult result = BankOperationCore.withdraw(
                providerId(), player.getUUID().toString(), amountMinorUnits,
                source, reason, requestId, handle, ledger);
        logAudit(result, player, amountMinorUnits, requestId);
        return result;
    }

    @Override
    public EconomyTransactionResult depositMinorUnits(ServerPlayer player, long amountMinorUnits,
                                                      String source, String reason, String requestId) {
        if (!onServerThread(player)) {
            return EconomyTransactionResult.failure(EconomyTransactionStatus.WRONG_THREAD,
                    "必须在服务端主线程操作", providerId(), requestId, 0, 0);
        }
        LcAccountHandle handle = resolve(player);
        if (handle == null) {
            return EconomyTransactionResult.failure(EconomyTransactionStatus.UNAVAILABLE,
                    "银行账户不可用", providerId(), requestId, 0, 0);
        }
        EconomyTransactionResult result = BankOperationCore.deposit(
                providerId(), player.getUUID().toString(), amountMinorUnits,
                source, reason, requestId, handle, ledger);
        logAudit(result, player, amountMinorUnits, requestId);
        return result;
    }

    @Override
    public String formatMinorUnits(long amountMinorUnits) {
        return String.format("%,d", amountMinorUnits);
    }

    /** BuildShop 退款后把原 idemKey 标记为“已冲正”，阻止该 requestId 被再次扣款。 */
    void rememberReversal(ServerPlayer player, String requestId) {
        if (player == null || requestId == null) {
            return;
        }
        ledger.remember(player.getUUID().toString(), requestId,
                BankOperationCore.reversedRecord(providerId(), requestId));
    }

    /** 供同包的 BuildShop / StockMarket 适配器复用的幂等账本（不对外暴露）。 */
    BankOperationCore.IdempotencyLedger idempotency() {
        return ledger;
    }

    // ---------------------------------------------------------------- internals

    @Nullable
    private LcAccountHandle resolve(ServerPlayer player) {
        try {
            BankReference reference = PlayerBankReference.of(player);
            if (reference == null) {
                return null;
            }
            IBankAccount account = reference.get();
            if (account == null) {
                return null;
            }
            return new LcAccountHandle(player, account, uniqueName());
        } catch (LinkageError | RuntimeException e) {
            LOGGER.error("[ServerMenu][LC] cannot resolve personal bank account: player={}",
                    player.getGameProfile().getName(), e);
            return null;
        }
    }

    /** main 链 CoinValue 的唯一名；LC 链数据未就绪时回退到验证过的常量。 */
    private String uniqueName() {
        String cached = uniqueNameCache;
        if (cached != null) {
            return cached;
        }
        String resolved = null;
        try {
            MoneyValue probe = CoinValue.fromNumber(LcConstants.MAIN_CHAIN, 1);
            if (probe != null && !probe.isEmpty()) {
                resolved = probe.getUniqueName();
            }
        } catch (LinkageError | RuntimeException ignored) {
            // 链数据未就绪：下面回退常量。
        }
        if (resolved == null || resolved.isBlank()) {
            resolved = LcConstants.MAIN_CHAIN_UNIQUE_NAME;
        }
        uniqueNameCache = resolved;
        return resolved;
    }

    private static boolean onServerThread(ServerPlayer player) {
        return player != null && player.server != null && player.server.isSameThread();
    }

    /** CONTEXT：审计与人工排查日志（仅固定/受限字段，不含无界输入）。 */
    private static void logAudit(EconomyTransactionResult result, ServerPlayer player,
                                 long amountMinorUnits, String requestId) {
        if (result.status() == EconomyTransactionStatus.COMPENSATION_FAILED) {
            LOGGER.error("[ServerMenu][LC] COMPENSATION_FAILED: player={} requestId={} amount={} status={}",
                    player.getGameProfile().getName(), requestId, amountMinorUnits, result.status());
        } else if (result.status() == EconomyTransactionStatus.PROVIDER_ERROR
                || result.status() == EconomyTransactionStatus.CONVERSION_FAILED) {
            LOGGER.warn("[ServerMenu][LC] provider failure: player={} requestId={} amount={} status={}",
                    player.getGameProfile().getName(), requestId, amountMinorUnits, result.status());
        }
    }
}
