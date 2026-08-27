package com.tanrunn.servermenu.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 领地商店默认阶梯价格测试。 */
class TerritoryShopConfigTest {
    @Test
    void defaultPriceStartsAt1024AndIncreasesBy50Percent() {
        assertEquals(1024L, TerritoryShopConfig.calculatePrice(1024L, 50, 0));
        assertEquals(1536L, TerritoryShopConfig.calculatePrice(1024L, 50, 1));
        assertEquals(2304L, TerritoryShopConfig.calculatePrice(1024L, 50, 2));
    }

    @Test
    void negativePurchaseCountUsesBasePrice() {
        assertEquals(1024L, TerritoryShopConfig.calculatePrice(1024L, 50, -1));
    }
}
