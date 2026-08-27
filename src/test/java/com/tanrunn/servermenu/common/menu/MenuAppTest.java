package com.tanrunn.servermenu.common.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuAppTest {

    @Test
    void parsesAllBuiltInIds() {
        assertSame(MenuApp.BUILD_SHOP, MenuApp.fromId("build_shop").orElseThrow());
        assertSame(MenuApp.STOCK_MARKET, MenuApp.fromId("stock_market").orElseThrow());
        assertSame(MenuApp.CHINESE_ORACLE, MenuApp.fromId("chinese_oracle").orElseThrow());
        assertSame(MenuApp.TERRITORY, MenuApp.fromId("territory").orElseThrow());
    }

    @Test
    void requiredModIdsMatchBusinessMods() {
        assertEquals("buildshop", MenuApp.BUILD_SHOP.requiredModId());
        assertEquals("stockmarket", MenuApp.STOCK_MARKET.requiredModId());
        assertEquals("chinese_oracle", MenuApp.CHINESE_ORACLE.requiredModId());
        assertEquals("openpartiesandclaims", MenuApp.TERRITORY.requiredModId());
    }

    @Test
    void builtInMetadataIsStable() {
        assertEquals("建筑商店", MenuApp.BUILD_SHOP.displayName());
        assertEquals("建材采购与库存", MenuApp.BUILD_SHOP.subtitle());
        assertEquals("股市", MenuApp.STOCK_MARKET.displayName());
        assertEquals("今日黄历", MenuApp.CHINESE_ORACLE.displayName());
        assertEquals("领地、队伍与区块管理", MenuApp.TERRITORY.subtitle());
    }

    @Test
    void rejectsUnknownAndBlankIds() {
        assertTrue(MenuApp.fromId("lottery").isEmpty());
        assertTrue(MenuApp.fromId("build_shop_extra").isEmpty());
        assertTrue(MenuApp.fromId("").isEmpty());
        assertTrue(MenuApp.fromId("   ").isEmpty());
        assertTrue(MenuApp.fromId(null).isEmpty());
        assertTrue(MenuApp.fromId("BUILD_SHOP").isEmpty());
    }

    @Test
    void allEnumValuesAreIndexed() {
        assertEquals(MenuApp.values().length, MenuApp.ALL.size());
        for (MenuApp app : MenuApp.values()) {
            assertTrue(MenuApp.fromId(app.id()).isPresent());
            assertFalse(app.id().isBlank());
            assertFalse(app.requiredModId().isBlank());
        }
    }
}
