package com.tanrunn.servermenu.server.integration.summary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MoneyFormat} 纯逻辑测试：正数、负数、零、正盈亏显式 +、
 * Long.MAX_VALUE 与 Long.MIN_VALUE（不做 Math.abs，避免溢出）。
 */
class MoneyFormatTest {

    @Test
    void positiveAmount() {
        assertEquals("123.45", MoneyFormat.amount(12_345));
        assertEquals("0.01", MoneyFormat.amount(1));
    }

    @Test
    void negativeAmount() {
        assertEquals("-123.45", MoneyFormat.amount(-12_345));
    }

    @Test
    void zeroAmount() {
        assertEquals("0.00", MoneyFormat.amount(0));
    }

    @Test
    void positivePnlHasExplicitPlus() {
        assertEquals("+123.45", MoneyFormat.pnl(12_345));
        assertEquals("+0.01", MoneyFormat.pnl(1));
    }

    @Test
    void negativeAndZeroPnlHaveNoPlus() {
        assertEquals("-123.45", MoneyFormat.pnl(-12_345));
        assertEquals("0.00", MoneyFormat.pnl(0));
    }

    @Test
    void longMaxValue() {
        assertEquals("92233720368547758.07", MoneyFormat.amount(Long.MAX_VALUE));
    }

    @Test
    void longMinValueDoesNotOverflow() {
        // Math.abs(Long.MIN_VALUE) 会溢出为负数；这里必须直接按 Long.MIN_VALUE 格式化。
        assertEquals("-92233720368547758.08", MoneyFormat.amount(Long.MIN_VALUE));
        assertEquals("-92233720368547758.08", MoneyFormat.pnl(Long.MIN_VALUE));
    }
}
