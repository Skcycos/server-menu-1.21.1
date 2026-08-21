package com.tanrunn.servermenu.api.economy;

/**
 * 经济交易结果（公共 API，不引用任何业务 Mod / LC 类）。
 *
 * <p>{@code message} 永远是<b>固定文案</b>：不包含 source/reason/requestId 等
 * 用户输入，避免把无界或敏感内容写进日志或 UI。{@code processedMinorUnits} 是
 * 该操作实际处理的最小单位金额（部分扣款场景下为已扣金额），
 * {@code balanceMinorUnits} 是操作完成后的余额。</p>
 *
 * @param success              是否成功
 * @param status               状态（见 {@link EconomyTransactionStatus}）
 * @param processedMinorUnits  实际处理金额
 * @param balanceMinorUnits    操作后余额
 * @param message              固定安全文案（禁止包含敏感/无界用户输入）
 * @param providerId           提供者 id
 * @param requestId            幂等键
 * @param duplicate            是否为已处理请求的重放（幂等重复）
 */
public record EconomyTransactionResult(
        boolean success,
        EconomyTransactionStatus status,
        long processedMinorUnits,
        long balanceMinorUnits,
        String message,
        String providerId,
        String requestId,
        boolean duplicate) {

    public static EconomyTransactionResult success(String providerId, String requestId,
                                                   long processedMinorUnits, long balanceMinorUnits) {
        return new EconomyTransactionResult(true, EconomyTransactionStatus.SUCCESS,
                processedMinorUnits, balanceMinorUnits, "操作成功", providerId, requestId, false);
    }

    public static EconomyTransactionResult failure(EconomyTransactionStatus status,
                                                   String message, String providerId, String requestId,
                                                   long processedMinorUnits, long balanceMinorUnits) {
        return new EconomyTransactionResult(false, status, processedMinorUnits,
                balanceMinorUnits, message, providerId, requestId, false);
    }

    /** 重放已处理请求时返回：保留原结果，但标记 duplicate。 */
    public EconomyTransactionResult asDuplicate() {
        if (duplicate) {
            return this;
        }
        return new EconomyTransactionResult(success, status, processedMinorUnits, balanceMinorUnits,
                message, providerId, requestId, true);
    }
}
