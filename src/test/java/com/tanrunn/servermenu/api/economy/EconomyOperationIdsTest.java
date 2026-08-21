package com.tanrunn.servermenu.api.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EconomyOperationIds} 幂等键规范测试。
 *
 * <p>覆盖：长度 ≤64、确定性、业务域隔离（BuildShop vs StockMarket vs 普通 API/补偿）、
 * source/操作类型/方向区分、完整原始 requestId（共享前 61 字符不碰撞）、
 * 「原始 requestId = 其它补偿 ID」不碰撞。</p>
 */
class EconomyOperationIdsTest {

    private static final String PROVIDER = "server_menu:lc_bank_main";

    @Test
    void idsAreFixedLengthAndUnderLimit() {
        for (String domain : new String[]{
                EconomyOperationIds.BS_WITHDRAW, EconomyOperationIds.BS_REFUND,
                EconomyOperationIds.SM_BANK_DEBIT, EconomyOperationIds.SM_BANK_CREDIT,
                EconomyOperationIds.SM_SECURITIES_DEBIT, EconomyOperationIds.SM_SECURITIES_CREDIT,
                EconomyOperationIds.SM_ROLLBACK}) {
            String id = EconomyOperationIds.generate(domain, PROVIDER, "s", "t", "req-1", "d");
            assertTrue(id.length() <= EconomyOperationIds.MAX_LENGTH,
                    domain + " 长度超限: " + id + " (" + id.length() + ")");
        }
    }

    @Test
    void sameInputIsDeterministic() {
        String a = EconomyOperationIds.generate(EconomyOperationIds.BS_WITHDRAW, PROVIDER, "buildshop",
                "withdraw", "req-99", "");
        String b = EconomyOperationIds.generate(EconomyOperationIds.BS_WITHDRAW, PROVIDER, "buildshop",
                "withdraw", "req-99", "");
        assertEquals(49, a.length()); // 6 字符前缀 + 43 字符 base64url(SHA-256)
        assertEquals(a, b);
    }

    @Test
    void businessDomainsAreIsolated() {
        String buildShop = EconomyOperationIds.generate(EconomyOperationIds.BS_WITHDRAW, PROVIDER,
                "buildshop", "withdraw", "shared", "");
        String stockMarket = EconomyOperationIds.generate(EconomyOperationIds.SM_BANK_DEBIT, PROVIDER,
                "server_menu_lc_bank", "bank_debit", "shared", "DEPOSIT_TO_SECURITIES");
        assertNotEquals(buildShop, stockMarket);
        assertTrue(buildShop.startsWith("bs:wd:"));
        assertTrue(stockMarket.startsWith("sm:bd:"));
    }

    @Test
    void differentSourcesSameRequestIdDoNotCollide() {
        String a = EconomyOperationIds.generate(EconomyOperationIds.SM_BANK_DEBIT, PROVIDER, "buildshop",
                "withdraw", "same", "d");
        String b = EconomyOperationIds.generate(EconomyOperationIds.SM_BANK_DEBIT, PROVIDER, "server_menu_lc_bank",
                "withdraw", "same", "d");
        assertNotEquals(a, b);
    }

    @Test
    void differentOperationTypesDoNotCollide() {
        String wd = EconomyOperationIds.generate(EconomyOperationIds.SM_BANK_DEBIT, PROVIDER, "s",
                "bank_debit", "same", "d");
        String sc = EconomyOperationIds.generate(EconomyOperationIds.SM_SECURITIES_CREDIT, PROVIDER, "s",
                "sec_credit", "same", "d");
        assertNotEquals(wd, sc);
    }

    @Test
    void requestIdsSharingFirst61CharsDoNotCollide() {
        String base = EconomyOperationIds.generate(EconomyOperationIds.SM_BANK_CREDIT, PROVIDER, "s",
                "credit", "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789AAAAAAAAAA", "d");
        String other = EconomyOperationIds.generate(EconomyOperationIds.SM_BANK_CREDIT, PROVIDER, "s",
                "credit", "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789BBBBBBBBBB", "d");
        assertNotEquals(base, other);
    }

    @Test
    void rawRequestIdEqualToOtherCompensationIdDoesNotCollide() {
        // raw = "rb:x" 的补偿键 不会 与 raw = "x" 的补偿键相同，更不会与正常操作相同。
        String compOfX = EconomyOperationIds.generate(EconomyOperationIds.SM_ROLLBACK, PROVIDER, "s",
                "rollback", "x", "d");
        String compOfRbX = EconomyOperationIds.generate(EconomyOperationIds.SM_ROLLBACK, PROVIDER, "s",
                "rollback", "rb:x", "d");
        assertNotEquals(compOfX, compOfRbX);
        String normalOfRbX = EconomyOperationIds.generate(EconomyOperationIds.SM_BANK_DEBIT, PROVIDER, "s",
                "bank_debit", "rb:x", "d");
        assertNotEquals(compOfRbX, normalOfRbX);
    }

    @Test
    void buildShopRefundDoesNotCollideWithStockMarketRollback() {
        String bsRefund = EconomyOperationIds.generate(EconomyOperationIds.BS_REFUND, PROVIDER,
                "buildshop", "refund", "same", "");
        String smRollback = EconomyOperationIds.generate(EconomyOperationIds.SM_ROLLBACK, PROVIDER,
                "server_menu_lc_bank", "rollback_securities", "same", "WITHDRAW_TO_BANK");
        assertNotEquals(bsRefund, smRollback);
    }

    @Test
    void directionDistinguishesDepositAndWithdrawForSameRequestId() {
        String deposit = EconomyOperationIds.generate(EconomyOperationIds.SM_BANK_DEBIT, PROVIDER, "s",
                "bank_debit", "dup", "DEPOSIT_TO_SECURITIES");
        String withdraw = EconomyOperationIds.generate(EconomyOperationIds.SM_BANK_DEBIT, PROVIDER, "s",
                "bank_debit", "dup", "WITHDRAW_TO_BANK");
        assertNotEquals(deposit, withdraw);
    }
}
