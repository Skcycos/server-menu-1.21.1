package com.tanrunn.servermenu.server;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.math.BigInteger;

/** 领地购买商店的服务端配置。金额单位与 server-menu 的 LC main 铜币桥接单位一致。 */
public final class TerritoryShopConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.LongValue CLAIM_PRICE = BUILDER
            .comment("首次购买一个 OAPC 领地上限所需的 LC main 铜币数量")
            .defineInRange("claimPrice", 1024L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.IntValue PRICE_INCREASE_PERCENT = BUILDER
            .comment("每购买一次后，下一次价格相对上一次价格增加的百分比")
            .defineInRange("priceIncreasePercent", 50, 0, 100_000);

    public static final ModConfigSpec.IntValue MAX_PURCHASES = BUILDER
            .comment("单个玩家最多通过领地商店购买的领地上限次数")
            .defineInRange("maxPurchases", 99, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * 根据已经购买的次数计算下一次价格；第 0 次购买使用基础价格。
     * 价格采用向上取整，避免自定义百分比过小时价格长期不增长，并在溢出时封顶为 Long.MAX_VALUE。
     */
    public static long priceForPurchaseCount(int purchasedClaims) {
        return calculatePrice(CLAIM_PRICE.get(), PRICE_INCREASE_PERCENT.get(), purchasedClaims);
    }

    static long calculatePrice(long basePrice, int increasePercent, int purchasedClaims) {
        basePrice = Math.max(0L, basePrice);
        int increases = Math.max(0, purchasedClaims);
        long percent = Math.max(0L, increasePercent);
        if (increases == 0 || percent == 0 || basePrice == Long.MAX_VALUE) {
            return basePrice;
        }

        BigInteger current = BigInteger.valueOf(basePrice);
        BigInteger multiplier = BigInteger.valueOf(100L + percent);
        BigInteger denominator = BigInteger.valueOf(100L);
        BigInteger roundUp = denominator.subtract(BigInteger.ONE);
        BigInteger maximum = BigInteger.valueOf(Long.MAX_VALUE);
        for (int i = 0; i < increases; i++) {
            current = current.multiply(multiplier).add(roundUp).divide(denominator);
            if (current.compareTo(maximum) >= 0) {
                return Long.MAX_VALUE;
            }
        }
        return current.longValue();
    }

    private TerritoryShopConfig() {
    }
}
