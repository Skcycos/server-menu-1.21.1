package com.tanrunn.servermenu.server.integration.summary;

import java.math.BigDecimal;

/**
 * 金额格式化（纯逻辑，可单测）。
 *
 * <p>金额单位为分，统一转换为两位小数字符串；不使用共享静态 DecimalFormat；
 * 通过 {@link BigDecimal#valueOf(long, int)} 正确处理 Long.MIN_VALUE
 * （不做 Math.abs(long)，避免溢出）。</p>
 */
public final class MoneyFormat {
    private MoneyFormat() {
        throw new AssertionError();
    }

    /** 分 → 两位小数字符串，不显式加号（12345 → "123.45"，-12345 → "-123.45"，0 → "0.00"）。 */
    public static String amount(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    /** 分 → 两位小数字符串；正数显式带 "+"（今日盈亏用）。 */
    public static String pnl(long cents) {
        return cents > 0 ? "+" + amount(cents) : amount(cents);
    }
}
