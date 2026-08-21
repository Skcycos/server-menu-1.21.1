package com.tanrunn.servermenu.api.economy;

/**
 * 经济提供者的账户余额快照（公共 API，不引用任何业务 Mod / LC 类）。
 *
 * @param available  提供者当前是否可用（LC 已安装且探针通过）
 * @param quarantined 玩家当前所在维度是否被隔离（true 时交易必须拒绝）
 * @param minorUnits 余额（最小单位）
 * @param chain      货币链标识（LC 为 "main"）
 * @param providerId 提供者 id
 */
public record EconomyBalance(
        boolean available,
        boolean quarantined,
        long minorUnits,
        String chain,
        String providerId) {

    public static EconomyBalance unavailable(String providerId, String chain) {
        return new EconomyBalance(false, false, 0L, chain, providerId);
    }

    public static EconomyBalance of(boolean available, boolean quarantined, long minorUnits,
                                    String chain, String providerId) {
        return new EconomyBalance(available, quarantined, minorUnits, chain, providerId);
    }
}
