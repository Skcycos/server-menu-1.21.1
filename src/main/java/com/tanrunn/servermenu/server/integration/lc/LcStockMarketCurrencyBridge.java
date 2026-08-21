package com.tanrunn.servermenu.server.integration.lc;

import com.tanrunn.servermenu.api.economy.EconomyTransactionResult;
import com.tanrunn.servermenu.api.economy.EconomyTransactionStatus;
import com.tanrunn.stockmarket.api.BridgeResult;
import com.tanrunn.stockmarket.api.BridgeStatusCode;
import com.tanrunn.stockmarket.api.CurrencyBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * StockMarket {@link CurrencyBridge} 的 LC 实现（compileOnly，只在 LC + StockMarket
 * 均已安装时由 bootstrap 注册）。桥接金额单位：1 铜币 = 1 LC {@code main} core value。
 *
 * <p>只把玩家主动的入金/出金接到 LC 个人 ATM 账户；证券资产买入/卖出/限价单/分红/
 * 股价/市值等日常逻辑完全在 StockMarket 内，不会调用本桥。所有操作在服务端主线程，
 * 结果精确映射 {@link EconomyTransactionStatus} → {@link BridgeStatusCode}。
 * 传入的 {@code requestId} 是内部 opId（由 StockMarket 的 {@code OperationIds}
 * 生成），副作用落在 LC provider 的幂等账本上。</p>
 */
public final class LcStockMarketCurrencyBridge implements CurrencyBridge {

    private final LcEconomyProvider provider;

    public LcStockMarketCurrencyBridge(LcEconomyProvider provider) {
        this.provider = provider;
    }

    @Override
    public String id() {
        return LcConstants.PROVIDER_ID;
    }

    @Override
    public String displayName() {
        return LcConstants.PROVIDER_DISPLAY_NAME;
    }

    @Override
    public boolean isAvailable() {
        return provider != null && provider.isAvailable();
    }

    @Override
    public long balanceCopper(UUID playerId) {
        ServerPlayer player = resolve(playerId);
        if (player == null || provider == null || !provider.isAvailable()) {
            return 0;
        }
        try {
            return Math.max(0, provider.balance(player).minorUnits());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Override
    public BridgeResult withdraw(UUID playerId, long copper, String source, String reason, String requestId) {
        ServerPlayer player = resolve(playerId);
        if (player == null || provider == null || !provider.isAvailable()) {
            return BridgeResult.fail(BridgeStatusCode.UNAVAILABLE, "银行桥接不可用");
        }
        return map(provider.withdrawMinorUnits(player, copper, source, reason, requestId));
    }

    @Override
    public BridgeResult deposit(UUID playerId, long copper, String source, String reason, String requestId) {
        ServerPlayer player = resolve(playerId);
        if (player == null || provider == null || !provider.isAvailable()) {
            return BridgeResult.fail(BridgeStatusCode.UNAVAILABLE, "银行桥接不可用");
        }
        return map(provider.depositMinorUnits(player, copper, source, reason, requestId));
    }

    private static ServerPlayer resolve(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(playerId);
    }

    private static BridgeResult map(EconomyTransactionResult result) {
        if (result == null) {
            return BridgeResult.fail(BridgeStatusCode.PROVIDER_ERROR, "银行返回空结果");
        }
        if (result.success() && result.status() == EconomyTransactionStatus.SUCCESS) {
            long actual = Math.max(0, result.processedMinorUnits());
            return BridgeResult.ok(actual);
        }
        return switch (result.status()) {
            case UNAVAILABLE -> BridgeResult.fail(BridgeStatusCode.UNAVAILABLE, result.message());
            case WRONG_THREAD -> BridgeResult.fail(BridgeStatusCode.WRONG_THREAD, result.message());
            case QUARANTINED -> BridgeResult.fail(BridgeStatusCode.QUARANTINED, result.message());
            case INVALID_AMOUNT -> BridgeResult.fail(BridgeStatusCode.INVALID_AMOUNT, result.message());
            case INSUFFICIENT_FUNDS -> BridgeResult.fail(BridgeStatusCode.INSUFFICIENT_FUNDS, result.message());
            case CONVERSION_FAILED -> BridgeResult.fail(BridgeStatusCode.CONVERSION_FAILED, result.message());
            case PROVIDER_ERROR -> BridgeResult.fail(BridgeStatusCode.PROVIDER_ERROR, result.message());
            case PARTIAL_OPERATION -> new BridgeResult(false,
                    Math.max(0, result.processedMinorUnits()), BridgeStatusCode.PARTIAL_OPERATION, result.message());
            case COMPENSATION_FAILED -> new BridgeResult(false,
                    Math.max(0, result.processedMinorUnits()), BridgeStatusCode.COMPENSATION_FAILED, result.message());
            case INVALID_REQUEST -> BridgeResult.fail(BridgeStatusCode.INVALID_REQUEST, result.message());
            case REQUEST_CONFLICT -> BridgeResult.fail(BridgeStatusCode.REQUEST_CONFLICT, result.message());
            case AMOUNT_OVERFLOW -> BridgeResult.fail(BridgeStatusCode.AMOUNT_OVERFLOW, result.message());
            default -> BridgeResult.fail(BridgeStatusCode.PROVIDER_ERROR, result.message());
        };
    }
}
