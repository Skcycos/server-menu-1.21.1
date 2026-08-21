package com.tanrunn.servermenu.api.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** {@link EconomyInputs} 校验（amount 0/负数/长度/溢出安全）。 */
class EconomyInputsTest {

    @Test
    void validInputsPass() {
        assertNull(EconomyInputs.validate(100, "buildshop", "购买商品", "req-1"));
    }

    @Test
    void zeroAmountRejected() {
        assertEquals(EconomyTransactionStatus.INVALID_AMOUNT,
                EconomyInputs.validate(0, "s", "r", "id"));
    }

    @Test
    void negativeAmountRejected() {
        assertEquals(EconomyTransactionStatus.INVALID_AMOUNT,
                EconomyInputs.validate(-1, "s", "r", "id"));
    }

    @Test
    void hugeAmountRejected() {
        assertEquals(EconomyTransactionStatus.INVALID_AMOUNT,
                EconomyInputs.validate(EconomyInputs.MAX_AMOUNT_MINOR_UNITS + 1, "s", "r", "id"));
    }

    @Test
    void sourceBlankOrTooLongRejected() {
        assertEquals(EconomyTransactionStatus.INVALID_REQUEST,
                EconomyInputs.validate(100, "  ", "r", "id"));
        assertEquals(EconomyTransactionStatus.INVALID_REQUEST,
                EconomyInputs.validate(100, "a".repeat(EconomyInputs.MAX_SOURCE_LENGTH + 1), "r", "id"));
    }

    @Test
    void reasonTooLongRejected() {
        assertEquals(EconomyTransactionStatus.INVALID_REQUEST,
                EconomyInputs.validate(100, "s", "a".repeat(EconomyInputs.MAX_REASON_LENGTH + 1), "id"));
    }

    @Test
    void requestIdBlankOrTooLongRejected() {
        assertEquals(EconomyTransactionStatus.INVALID_REQUEST,
                EconomyInputs.validate(100, "s", "r", ""));
        assertEquals(EconomyTransactionStatus.INVALID_REQUEST,
                EconomyInputs.validate(100, "s", "r", null));
        assertEquals(EconomyTransactionStatus.INVALID_REQUEST,
                EconomyInputs.validate(100, "s", "r", "a".repeat(EconomyInputs.MAX_REQUEST_ID_LENGTH + 1)));
    }

    @Test
    void safeAddHandlesOverflow() {
        long[] out = {0};
        org.junit.jupiter.api.Assertions.assertTrue(EconomyInputs.tryAdd(10, 5, out));
        assertEquals(15, out[0]);
        org.junit.jupiter.api.Assertions.assertFalse(EconomyInputs.tryAdd(Long.MAX_VALUE, 1, out));
    }

    @Test
    void safeSubtractRejectsNegativeResultAndOverflow() {
        long[] out = {0};
        org.junit.jupiter.api.Assertions.assertTrue(EconomyInputs.trySubtractNonNegative(10, 5, out));
        assertEquals(5, out[0]);
        org.junit.jupiter.api.Assertions.assertFalse(EconomyInputs.trySubtractNonNegative(5, 10, out));
        org.junit.jupiter.api.Assertions.assertFalse(EconomyInputs.trySubtractNonNegative(0, Long.MIN_VALUE, out));
    }
}
