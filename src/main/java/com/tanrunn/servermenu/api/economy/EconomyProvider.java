package com.tanrunn.servermenu.api.economy;

import net.minecraft.server.level.ServerPlayer;

/**
 * 经济提供者（公共 API，不引用任何业务 Mod / LC 类）。
 *
 * <p>实现方（如 LC 适配器）负责把“最小单位”解释成具体货币：
 * 本桥接契约中 1 个桥接单位 = LC {@code main} 链 1 个 core value（当前服务器配置下
 * 为 1 枚铜币）。所有金额使用 long 最小单位。</p>
 *
 * <p>线程与安全契约：交易方法只允许在服务端主线程调用；实现必须自行校验
 * {@code player.level().getServer().isSameThread()}。任何异常都不得向调用方传播
 * —— 一律转换成本模块的状态码并返回失败结果（fail closed）。</p>
 */
public interface EconomyProvider {

    /** 提供者稳定 ID（如 server_menu:lc_bank_main）。 */
    String providerId();

    /** 展示名称（如 “铜币”）。 */
    String displayName();

    /** 货币链标识（LC 为 "main"）。 */
    String currencyChain();

    /** 是否可用：依赖已安装 + 探针通过 + 运行时就绪。 */
    boolean isAvailable();

    /** 查询余额（必须在服务端主线程调用）。 */
    EconomyBalance balance(ServerPlayer player);

    /**
     * 从玩家个人账户扣款。
     *
     * @param amountMinorUnits 最小单位金额（必须大于 0）
     * @param source           业务来源（审计用，长度受限）
     * @param reason           用途说明（审计用，长度受限）
     * @param requestId        幂等键（同一 requestId 重放不重复扣款）
     */
    EconomyTransactionResult withdrawMinorUnits(ServerPlayer player, long amountMinorUnits,
                                                String source, String reason, String requestId);

    /**
     * 给玩家个人账户入账。
     *
     * @param amountMinorUnits 最小单位金额（必须大于 0）
     * @param source           业务来源（审计用，长度受限）
     * @param reason           用途说明（审计用，长度受限）
     * @param requestId        幂等键（同一 requestId 重放不重复入账）
     */
    EconomyTransactionResult depositMinorUnits(ServerPlayer player, long amountMinorUnits,
                                               String source, String reason, String requestId);

    /** 格式化最小单位金额（展示用，如 "1,234 铜币"）。 */
    String formatMinorUnits(long amountMinorUnits);
}
