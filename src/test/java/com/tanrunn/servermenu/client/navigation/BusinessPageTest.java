package com.tanrunn.servermenu.client.navigation;

import com.tanrunn.servermenu.common.menu.MenuApp;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务页面路径白名单的纯逻辑测试。
 */
class BusinessPageTest {

    @Test
    void exactPathsResolveToCorrectApps() {
        assertSame(MenuApp.BUILD_SHOP,
                BusinessPage.fromDocumentPath("buildingshop/screens/building_shop.html").orElseThrow().app());
        assertSame(MenuApp.STOCK_MARKET,
                BusinessPage.fromDocumentPath("screens/market.html").orElseThrow().app());
        assertSame(MenuApp.CHINESE_ORACLE,
                BusinessPage.fromDocumentPath("screens/fortune.html").orElseThrow().app());
    }

    @Test
    void nullAndBlankPathsResolveEmpty() {
        assertTrue(BusinessPage.fromDocumentPath(null).isEmpty());
        assertTrue(BusinessPage.fromDocumentPath("").isEmpty());
        assertTrue(BusinessPage.fromDocumentPath("   ").isEmpty());
        assertTrue(BusinessPage.fromDocumentPath("\t\n").isEmpty());
    }

    @Test
    void padPathResolvesEmpty() {
        assertTrue(BusinessPage.fromDocumentPath(BusinessPage.PAD_DOCUMENT_PATH).isEmpty());
        assertTrue(BusinessPage.fromDocumentPath("servermenu/screens/pad.html").isEmpty());
    }

    @Test
    void similarPathsResolveEmpty() {
        assertTrue(BusinessPage.fromDocumentPath("x/screens/market.html").isEmpty());
        assertTrue(BusinessPage.fromDocumentPath("screens/market.html.bak").isEmpty());
        assertTrue(BusinessPage.fromDocumentPath("buildingshop/screens/building_shop.html/extra").isEmpty());
        assertTrue(BusinessPage.fromDocumentPath("screens/fortune.HTML").isEmpty());
        assertTrue(BusinessPage.fromDocumentPath("/screens/market.html").isEmpty());
        assertTrue(BusinessPage.fromDocumentPath("screens/market.html ").isEmpty());
        assertTrue(BusinessPage.fromDocumentPath("screens/market").isEmpty());
        assertTrue(BusinessPage.fromDocumentPath("Screens/market.html").isEmpty());
    }

    @Test
    void unknownPathsResolveEmpty() {
        assertTrue(BusinessPage.fromDocumentPath("screens/unknown.html").isEmpty());
        assertTrue(BusinessPage.fromDocumentPath("lottery/screens/lottery.html").isEmpty());
        assertTrue(BusinessPage.fromDocumentPath("buildshop/screens/building_shop.html").isEmpty());
        assertTrue(BusinessPage.fromDocumentPath("buildingshop/screens/market.html").isEmpty());
    }

    @Test
    void threePathsAreUniqueAndNonBlank() {
        Set<String> paths = new HashSet<>();
        Set<MenuApp> apps = new HashSet<>();
        for (BusinessPage page : BusinessPage.ALL) {
            assertNotNull(page.app());
            assertFalse(page.documentPath().isBlank(), "路径不能为空白: " + page);
            assertTrue(paths.add(page.documentPath()), "路径重复: " + page.documentPath());
            assertTrue(apps.add(page.app()), "MenuApp 重复: " + page.app());
        }
        assertEquals(3, paths.size());
        assertEquals(3, apps.size());
    }

    @Test
    void everyBusinessPageMatchesMenuAppWhitelist() {
        for (BusinessPage page : BusinessPage.ALL) {
            assertSame(page.app(), MenuApp.fromId(page.app().id()).orElseThrow());
        }
    }

    @Test
    void everyMenuAppHasExactlyOneBusinessPage() {
        // 反向一一对应：每个内置 MenuApp 恰好映射一个业务页面。
        for (MenuApp app : MenuApp.ALL) {
            long count = BusinessPage.ALL.stream().filter(page -> page.app() == app).count();
            assertEquals(1, count, "MenuApp " + app.id() + " 应恰好对应一个业务页面");
        }
    }

    @Test
    void pathMappingIsOneToOneWithMenuApp() {
        // 同一路径不得映射到两个不同页面（by 构造即保证），
        // 同时断言每个页面的 documentPath 与 app.id 白名单均稳定。
        for (BusinessPage page : BusinessPage.ALL) {
            assertEquals(page, BusinessPage.fromDocumentPath(page.documentPath()).orElseThrow());
        }
    }
}
