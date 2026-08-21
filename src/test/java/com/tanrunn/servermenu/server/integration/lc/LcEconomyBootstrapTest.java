package com.tanrunn.servermenu.server.integration.lc;

import com.tanrunn.servermenu.api.economy.EconomyBridgeRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LcEconomyBootstrap} 装配测试（注入 presence checker，走真实反射装配）。
 *
 * <p>LC JAR / business JAR 都在测试 classpath 上，因此可以验证：LC 未安装 → 不注册；
 * LC 已装 → 注册 provider；LC + BuildShop + StockMarket 全装 → 三处全部注册。</p>
 */
class LcEconomyBootstrapTest {

    @AfterEach
    void cleanUp() {
        EconomyBridgeRegistry.unregister(LcConstants.PROVIDER_ID);
        LcEconomyBootstrap.resetForTesting();
    }

    @Test
    void lcNotInstalledLeavesBridgeUnavailable() {
        LcEconomyBootstrap.presenceChecker = modId -> false;
        LcEconomyBootstrap.bootstrap();
        assertNull(LcEconomyBootstrap.registeredProviderForTesting());
        assertTrue(EconomyBridgeRegistry.provider(LcConstants.PROVIDER_ID).isEmpty());
    }

    @Test
    void lcInstalledAloneRegistersProvider() {
        LcEconomyBootstrap.presenceChecker = modId -> LcConstants.LC_MOD_ID.equals(modId);
        LcEconomyBootstrap.bootstrap();
        assertNotNull(LcEconomyBootstrap.registeredProviderForTesting());
        assertTrue(EconomyBridgeRegistry.isAvailable(LcConstants.PROVIDER_ID));
        assertEquals("main", EconomyBridgeRegistry.provider(LcConstants.PROVIDER_ID)
                .orElseThrow().currencyChain());
    }

    @Test
    void lcPlusBusinessModsRegistersAllThree() {
        LcEconomyBootstrap.presenceChecker = modId -> true;
        LcEconomyBootstrap.bootstrap();
        assertNotNull(LcEconomyBootstrap.registeredProviderForTesting());
        assertTrue(EconomyBridgeRegistry.isAvailable(LcConstants.PROVIDER_ID));
        // BuildShop provider 已注册
        assertTrue(com.tanrunn.buildshop.api.BuildingShopApi.currencyProvider(LcConstants.PROVIDER_ID).isPresent());
        // StockMarket bridge 已注册
        assertTrue(com.tanrunn.stockmarket.api.StockMarketApi.currencyBridge(LcConstants.PROVIDER_ID).isPresent());
    }

    @Test
    void repeatedBootstrapIsIdempotent() {
        LcEconomyBootstrap.presenceChecker = modId -> modId.equals(LcConstants.LC_MOD_ID) || modId.equals("stockmarket");
        LcEconomyBootstrap.bootstrap();
        LcEconomyBootstrap.bootstrap(); // bootstrapped 标志阻止重复装配
        assertNotNull(LcEconomyBootstrap.registeredProviderForTesting());
        assertTrue(com.tanrunn.stockmarket.api.StockMarketApi.currencyBridge(LcConstants.PROVIDER_ID).isPresent());
    }
}
