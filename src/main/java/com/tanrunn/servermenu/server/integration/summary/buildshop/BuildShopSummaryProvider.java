package com.tanrunn.servermenu.server.integration.summary.buildshop;

import com.tanrunn.buildshop.api.BuildingShopApi;
import com.tanrunn.buildshop.api.BuildingShopSummary;
import com.tanrunn.servermenu.common.menu.MenuApp;
import com.tanrunn.servermenu.server.integration.summary.AppCardSummary;
import com.tanrunn.servermenu.server.integration.summary.AppSummaryProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 建筑商店摘要适配器：只调用 {@link BuildingShopApi#summary(ServerPlayer)}。
 *
 * <p>不读取 Config、ShopServer、货币提供者或商品目录；不再把
 * defaultCurrencyName 拼到已格式化的余额后面。旧版业务 Mod 缺少
 * summary API 时本类不会被加载（由注册表描述符探测把关）。</p>
 */
public final class BuildShopSummaryProvider implements AppSummaryProvider {

    @Override
    public MenuApp app() {
        return MenuApp.BUILD_SHOP;
    }

    @Override
    public AppCardSummary summary(ServerPlayer player) {
        BuildingShopSummary data = BuildingShopApi.summary(player);
        List<String> lines = new ArrayList<>(2);
        lines.add((data.shopEnabled() ? "营业中 · " : "暂停营业 · ")
                + data.enabledProductCount() + " 件商品");
        String balance = data.formattedDefaultBalance();
        lines.add(balance == null || balance.isBlank() ? "余额暂不可用" : "余额 " + balance);
        return new AppCardSummary(lines);
    }
}
