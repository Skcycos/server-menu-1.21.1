package com.tanrunn.servermenu.server.integration.lc;

import com.tanrunn.buildshop.api.PaymentResult;
import com.tanrunn.buildshop.api.ShopCurrencyProvider;
import com.tanrunn.servermenu.api.economy.EconomyOperationIds;
import com.tanrunn.servermenu.api.economy.EconomyProvider;
import com.tanrunn.servermenu.api.economy.EconomyTransactionResult;
import com.tanrunn.servermenu.api.economy.EconomyTransactionStatus;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BuildShop 货币提供者（ID: {@code server_menu:lc_bank_main}，显示名 “铜币”）。
 *
 * <p>在 Server Menu 内实现并通过 {@code BuildingShopApi.registerCurrencyProvider} 注册，
 * 不改动 BuildShop 核心。金额单位：1 桥接单位 = 1 LC main core value = 1 枚铜币。</p>
 *
 * <p><b>幂等键隔离（本轮修复）</b>：BuildShop 传入的 {@code requestId} 是其完整幂等键
 * {@code productId|requestId|mode|quantity}（可能超过 64 字符）。本提供者用<b>完整原始
 * key</b> 在本地有界账本查重；传给 LC provider 前用 {@link EconomyOperationIds} 生成
 * 固定长度 opId（扣款 {@code bs:wd:}、退款 {@code bs:rf:}），因此：
 * <ul>
 *   <li>同一完整 key 重放得到同一 opId → LC 幂等重放不重复扣款；</li>
 *   <li>不同完整 key（即使共享前 61 字符）哈希不同 → 不碰撞；</li>
 *   <li>BuildShop 键与 StockMarket 键属于不同命名空间，互不重放。</li>
 * </ul></p>
 *
 * <p><b>退款金额校验</b>：必须找到原始成功扣款记录且退款金额与之一致才允许退款；
 * 校验失败直接失败且<b>不调用 LC</b>。重复相同退款幂等；退款后不同金额的请求失败。</p>
 *
 * <p>账本只存在于内存（LRU，{@value #MAX_ENTRIES} 条上限）：重启/淘汰后重放保护失效，
 * 这是明确的重启边界。退款成功后把原扣款 opId 在 LC provider 幂等账本标记为“已冲正”，
 * 即使本账本被淘汰，LC 侧也会拒绝再次扣款（避免免费发货）。</p>
 */
public final class LcBuildShopCurrencyProvider implements ShopCurrencyProvider {

    static final int MAX_ENTRIES = 2048;

    /** BuildShop 扣款/退款使用的 source（独立业务命名空间）。 */
    static final String BS_SOURCE = "buildshop";

    private final EconomyProvider provider;
    private final java.util.function.BiConsumer<ServerPlayer, String> reversalTombstone;
    private final Map<String, Entry> ledger = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    /** 生产路径构造：直接绑定 LC typed 提供者（含退款后写“已冲正”墓碑）。 */
    public LcBuildShopCurrencyProvider(LcEconomyProvider provider) {
        this(provider, provider == null ? null : provider::rememberReversal);
    }

    /** 测试/内部分离路径：注入任意 EconomyProvider 与可选的冲正墓碑回调。 */
    LcBuildShopCurrencyProvider(EconomyProvider provider,
                                java.util.function.BiConsumer<ServerPlayer, String> reversalTombstone) {
        this.provider = provider;
        this.reversalTombstone = reversalTombstone;
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
    public long balance(ServerPlayer player) {
        if (player == null || !provider.isAvailable()) {
            return 0;
        }
        try {
            return provider.balance(player).minorUnits();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Override
    public boolean canWithdraw(ServerPlayer player, long amount) {
        return provider.isAvailable() && amount >= 0 && balance(player) >= amount;
    }

    @Override
    public PaymentResult withdraw(ServerPlayer player, long amount, String reason, String requestId) {
        if (amount < 0) {
            return PaymentResult.fail("buildshop.payment.negative");
        }
        if (amount == 0 || amount > LcConstants.MAX_AMOUNT_PER_OPERATION) {
            return PaymentResult.fail("buildshop.payment.too_large");
        }
        if (player == null || !provider.isAvailable()) {
            return PaymentResult.fail("buildshop.payment.unknown_currency");
        }
        if (requestId == null || requestId.isBlank()) {
            return PaymentResult.fail("buildshop.payment.unknown_currency");
        }
        String key = key(player, requestId);
        // 本地用完整原始 key 查重（可能 >64，仅内存键）。
        synchronized (ledger) {
            Entry existing = ledger.get(key);
            if (existing != null) {
                if (existing.refunded) {
                    // 已退款：拒绝再次扣款，避免免费发货。
                    return PaymentResult.fail("buildshop.payment.withdraw_partial");
                }
                if (existing.amount == amount) {
                    // 相同完整 key + 相同金额：重放首次成功，不重复扣款。
                    return PaymentResult.ok();
                }
                return PaymentResult.fail("buildshop.payment.withdraw_partial");
            }
        }
        if (balance(player) < amount) {
            return PaymentResult.fail("buildshop.payment.insufficient_balance");
        }

        // 传入 LC 的是内部 opId（bs:wd: 域，稳定哈希），绝不外传原始 requestId。
        String opId = EconomyOperationIds.generate(EconomyOperationIds.BS_WITHDRAW,
                LcConstants.PROVIDER_ID, BS_SOURCE, "withdraw", requestId, "");
        EconomyTransactionResult result = provider.withdrawMinorUnits(
                player, amount, LcConstants.PROVIDER_ID, "shop_purchase", opId);
        if (!result.success()) {
            return PaymentResult.fail(mapWithdrawFailure(result.status()));
        }
        synchronized (ledger) {
            ledger.put(key, new Entry(amount, false));
        }
        return PaymentResult.ok();
    }

    @Override
    public PaymentResult refund(ServerPlayer player, long amount, String reason, String requestId) {
        if (amount < 0) {
            return PaymentResult.fail("buildshop.payment.negative");
        }
        if (player == null || !provider.isAvailable()) {
            return PaymentResult.fail("buildshop.payment.unknown_currency");
        }
        if (requestId == null || requestId.isBlank()) {
            return PaymentResult.fail("buildshop.payment.refund_failed");
        }
        String key = key(player, requestId);
        String opIdWithdraw = EconomyOperationIds.generate(EconomyOperationIds.BS_WITHDRAW,
                LcConstants.PROVIDER_ID, BS_SOURCE, "withdraw", requestId, "");

        // 一、找到了原始成功扣款记录且金额完全一致，否则直接失败（不调用 LC）。
        synchronized (ledger) {
            Entry existing = ledger.get(key);
            if (existing == null) {
                // 未成功扣款的交易不允许退款。
                return PaymentResult.fail("buildshop.payment.refund_failed");
            }
            if (existing.amount != amount) {
                // 退款金额与原始扣款不一致（含“已退款后不同金额”的情况）：直接失败。
                return PaymentResult.fail("buildshop.payment.refund_failed");
            }
            if (existing.refunded) {
                // 已退款且金额一致：幂等返回成功，不重复退款。
                return PaymentResult.ok();
            }
        }

        // 二、校验通过后才调用 LC（域隔离的 bs:rf: opId，与扣款 opId 不冲突）。
        String opIdRefund = EconomyOperationIds.generate(EconomyOperationIds.BS_REFUND,
                LcConstants.PROVIDER_ID, BS_SOURCE, "refund", requestId, "");
        EconomyTransactionResult result = provider.depositMinorUnits(
                player, amount, LcConstants.PROVIDER_ID, "shop_rollback", opIdRefund);
        if (!result.success()) {
            return PaymentResult.fail("buildshop.payment.refund_failed");
        }
        synchronized (ledger) {
            Entry existing = ledger.get(key);
            if (existing == null || existing.refunded || existing.amount != amount) {
                // 并发/竞态兜底：不重复标记。
                return PaymentResult.ok();
            }
            ledger.put(key, new Entry(existing.amount, true));
        }
        // 三、把“已冲正”墓碑写在扣款 opId 上（LC 账本拒绝再以该 opId 扣款，防免费发货）。
        if (reversalTombstone != null) {
            reversalTombstone.accept(player, opIdWithdraw);
        }
        return PaymentResult.ok();
    }

    @Override
    public String format(long amount) {
        return provider.formatMinorUnits(amount);
    }

    // ---------------------------------------------------------------- internals

    private static String key(ServerPlayer player, String requestId) {
        return player.getUUID() + ":" + requestId;
    }

    /** 只使用 BuildShop 现有 payment 文案键（不改动 BuildShop 资源）。 */
    private static String mapWithdrawFailure(EconomyTransactionStatus status) {
        return switch (status) {
            case INSUFFICIENT_FUNDS -> "buildshop.payment.insufficient_balance";
            case COMPENSATION_FAILED -> "buildshop.payment.refund_failed"; // “请联系管理员”
            case INVALID_AMOUNT -> "buildshop.payment.too_large";
            default -> "buildshop.payment.withdraw_partial";
        };
    }

    /** 提供给测试读取账本容量与内容。 */
    int ledgerSize() {
        synchronized (ledger) {
            return ledger.size();
        }
    }

    static final class Entry {
        final long amount;
        boolean refunded;

        Entry(long amount, boolean refunded) {
            this.amount = amount;
            this.refunded = refunded;
        }
    }
}
