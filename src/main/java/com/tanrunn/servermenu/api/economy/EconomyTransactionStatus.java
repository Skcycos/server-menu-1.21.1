package com.tanrunn.servermenu.api.economy;

/**
 * 经济交易结果状态（公共 API，不引用任何业务 Mod / LC 类）。
 *
 * <p>交易返回不能只是 boolean：至少区分下面这些可审计的状态。失败的补偿语义：
 * 任何“部分扣款后已全额补偿”都不得当作成功；补偿失败必须返回
 * {@link #COMPENSATION_FAILED} 并配合 critical/error 日志供人工审计。</p>
 */
public enum EconomyTransactionStatus {
    /** 交易成功。 */
    SUCCESS,
    /** LC/经济提供者不存在、未安装或不可用（fail closed）。 */
    UNAVAILABLE,
    /** 不在服务器主线程调用。 */
    WRONG_THREAD,
    /** 玩家所在维度被 LC 隔离名单隔离，交易被拒绝。 */
    QUARANTINED,
    /** 金额不合法（≤0 或超出允许上限）。 */
    INVALID_AMOUNT,
    /** 余额不足。 */
    INSUFFICIENT_FUNDS,
    /** 金额无法精确转换为基础货币（如 LC 链未加载/换算不精确）。 */
    CONVERSION_FAILED,
    /** 提供者内部错误（异常已被捕获，不向服务器传播）。 */
    PROVIDER_ERROR,
    /** 发生了部分扣款；已尝试全额补偿（补偿后净额为零，但不是成功）。 */
    PARTIAL_OPERATION,
    /** 部分扣款/中途失败后的补偿也失败了，需要人工审计。 */
    COMPENSATION_FAILED,
    /** requestId 长度或格式不合法。 */
    INVALID_REQUEST,
    /** 同一 requestId 已被用于不同方向或不同金额的操作（拒绝，不做重放）。 */
    REQUEST_CONFLICT,
    /** 余额运算出现 long 溢出。 */
    AMOUNT_OVERFLOW;

    /** 该状态是否适合直接展示给玩家（固定中文文案，不包含任何用户输入）。 */
    public boolean isTerminalFailure() {
        return this != SUCCESS;
    }
}
