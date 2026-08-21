package com.tanrunn.servermenu.server.integration.lc;

import io.github.lightman314.lightmanscurrency.api.misc.QuarantineAPI;
import io.github.lightman314.lightmanscurrency.api.money.bank.BankAPI;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyStorage;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;
import com.mojang.datafixers.util.Pair;
import com.tanrunn.servermenu.api.economy.EconomyTransactionStatus;
import com.tanrunn.servermenu.server.integration.economy.BankOperationCore;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * 针对单个玩家 LC 个人 ATM 银行账户的 {@link BankOperationCore.AccountHandle} 实现。
 *
 * <p>契约（对应任务要求，逐条落实）：
 * <ul>
 *   <li>只使用 {@link io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference#of}
 *       解析<b>玩家自己的个人账户</b>（绝不选择管理员/团队/其他玩家账户）；</li>
 *   <li>只使用 {@code main} 货币链；{@link CoinValue#fromNumber(String, long)} 转换后必须精确
 *       （{@code getCoreValue() == amount} 且非空），否则返回
 *       {@link EconomyTransactionStatus#CONVERSION_FAILED}；</li>
 *   <li>{@code BankWithdrawFromServer} 返回的 {@code Boolean} 不能证明足额扣款：以实际
 *       返回 {@code MoneyValue.getCoreValue()} 为准，不足即部分扣款交由
 *       {@link BankOperationCore} 补偿；</li>
 *   <li>每次交易前读取余额（{@code MoneyStorage.valueOf(uniqueName).getCoreValue()}）；</li>
 *   <li>{@link QuarantineAPI#IsDimensionQuarantined} 被隔离维度返回 quarantined=true；</li>
 *   <li>所有 LC 调用包裹 try/catch（含 LinkageError），异常不向服务端传播。</li>
 * </ul></p>
 */
final class LcAccountHandle implements BankOperationCore.AccountHandle {

    private static final Logger LOGGER = LoggerFactory.getLogger(LcAccountHandle.class);

    private final ServerPlayer player;
    private final IBankAccount account;
    private final String uniqueName;

    LcAccountHandle(@Nonnull ServerPlayer player, @Nonnull IBankAccount account, @Nonnull String uniqueName) {
        this.player = player;
        this.account = account;
        this.uniqueName = uniqueName;
    }

    @Override
    public boolean isAvailable() {
        return account != null;
    }

    @Override
    public boolean isQuarantined() {
        try {
            return QuarantineAPI.IsDimensionQuarantined(player);
        } catch (LinkageError | RuntimeException e) {
            // 隔离查询失败按“已隔离”处理（fail closed）。
            return true;
        }
    }

    @Override
    public long balanceMinorUnits() {
        try {
            MoneyStorage storage = account.getMoneyStorage();
            return storage.valueOf(uniqueName).getCoreValue();
        } catch (LinkageError | RuntimeException e) {
            return 0;
        }
    }

    @Override
    public BankOperationCore.Outcome withdraw(long amountMinorUnits) {
        try {
            MoneyValue request = CoinValue.fromNumber(LcConstants.MAIN_CHAIN, amountMinorUnits);
            if (request == null || request.isEmpty() || request.getCoreValue() != amountMinorUnits) {
                return BankOperationCore.Outcome.failed(EconomyTransactionStatus.CONVERSION_FAILED);
            }
            Pair<Boolean, MoneyValue> result = BankAPI.getApi()
                    .BankWithdrawFromServer(account, request, false);
            MoneyValue takenValue = result == null ? null : result.getSecond();
            long taken = takenValue == null ? 0 : takenValue.getCoreValue();
            if (taken == amountMinorUnits) {
                return BankOperationCore.Outcome.success(amountMinorUnits);
            }
            if (taken >= 0 && taken < amountMinorUnits) {
                // 部分扣款：交由核心立即补偿已扣金额。
                return BankOperationCore.Outcome.partial(taken);
            }
            return BankOperationCore.Outcome.failed(EconomyTransactionStatus.PROVIDER_ERROR);
        } catch (LinkageError | RuntimeException e) {
            LOGGER.error("[ServerMenu][LC] bank withdraw error: player={} amount={}",
                    player.getUUID(), amountMinorUnits, e);
            return BankOperationCore.Outcome.failed(EconomyTransactionStatus.PROVIDER_ERROR);
        }
    }

    @Override
    public BankOperationCore.Outcome deposit(long amountMinorUnits) {
        try {
            MoneyValue value = CoinValue.fromNumber(LcConstants.MAIN_CHAIN, amountMinorUnits);
            if (value == null || value.isEmpty() || value.getCoreValue() != amountMinorUnits) {
                return BankOperationCore.Outcome.failed(EconomyTransactionStatus.CONVERSION_FAILED);
            }
            boolean ok = BankAPI.getApi().BankDepositFromServer(account, value, false);
            if (!ok) {
                return BankOperationCore.Outcome.failed(EconomyTransactionStatus.PROVIDER_ERROR);
            }
            return BankOperationCore.Outcome.success(amountMinorUnits);
        } catch (LinkageError | RuntimeException e) {
            LOGGER.error("[ServerMenu][LC] bank deposit error: player={} amount={}",
                    player.getUUID(), amountMinorUnits, e);
            return BankOperationCore.Outcome.failed(EconomyTransactionStatus.PROVIDER_ERROR);
        }
    }
}
