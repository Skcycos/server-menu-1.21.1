package com.tanrunn.servermenu.api.economy;

/**
 * 输入校验与安全常量（公共 API，纯逻辑，可单测）。
 *
 * <p>安全要求：amount 必须大于 0；requestId/source/reason 必须限制长度
 * （避免把无界或敏感用户输入写进日志/账本）。所有校验在这里集中定义，
 * 保证 LC 适配器、BuildShop 适配器等所有实现行为一致。</p>
 */
public final class EconomyInputs {

    /** requestId 最大长度（网络层/回调层同步限制）。 */
    public static final int MAX_REQUEST_ID_LENGTH = 64;
    /** source 最大长度（审计流水用）。 */
    public static final int MAX_SOURCE_LENGTH = 32;
    /** reason 最大长度（审计流水用）。 */
    public static final int MAX_REASON_LENGTH = 128;
    /** 单笔交易金额上限（最小单位）。服务器最大余额与资产都远小于该值。 */
    public static final long MAX_AMOUNT_MINOR_UNITS = 9_000_000_000_000_000_000L; // 9e18

    private EconomyInputs() {
    }

    /**
     * 校验一笔交易的入参。
     *
     * @return 不合法时返回对应状态（INVALID_AMOUNT / INVALID_REQUEST），合法返回 null
     */
    public static EconomyTransactionStatus validate(long amountMinorUnits, String source,
                                                    String reason, String requestId) {
        if (amountMinorUnits <= 0 || amountMinorUnits > MAX_AMOUNT_MINOR_UNITS) {
            return EconomyTransactionStatus.INVALID_AMOUNT;
        }
        if (source == null || source.isBlank() || source.length() > MAX_SOURCE_LENGTH) {
            return EconomyTransactionStatus.INVALID_REQUEST;
        }
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            return EconomyTransactionStatus.INVALID_REQUEST;
        }
        if (requestId == null || requestId.isBlank() || requestId.length() > MAX_REQUEST_ID_LENGTH) {
            return EconomyTransactionStatus.INVALID_REQUEST;
        }
        return null;
    }

    /** requestId 是否合法（独立查询用）。 */
    public static boolean validRequestId(String requestId) {
        return requestId != null && !requestId.isBlank() && requestId.length() <= MAX_REQUEST_ID_LENGTH;
    }

    /** 溢出安全的加（余额运算）；溢出返回 false，结果不写盘。 */
    public static boolean tryAdd(long a, long b, long[] out) {
        try {
            out[0] = Math.addExact(a, b);
            return true;
        } catch (ArithmeticException e) {
            return false;
        }
    }

    /** 溢出安全的减；减去后不得为负（扣款不能把余额扣成负数）。 */
    public static boolean trySubtractNonNegative(long balance, long amount, long[] out) {
        if (amount < 0) {
            return false;
        }
        try {
            long next = Math.subtractExact(balance, amount);
            if (next < 0) {
                return false;
            }
            out[0] = next;
            return true;
        } catch (ArithmeticException e) {
            return false;
        }
    }
}
