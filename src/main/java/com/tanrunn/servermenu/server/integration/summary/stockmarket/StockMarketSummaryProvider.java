package com.tanrunn.servermenu.server.integration.summary.stockmarket;

import com.tanrunn.servermenu.common.menu.MenuApp;
import com.tanrunn.servermenu.server.integration.summary.AppCardSummary;
import com.tanrunn.servermenu.server.integration.summary.AppSummaryProvider;
import com.tanrunn.servermenu.server.integration.summary.MoneyFormat;
import com.tanrunn.stockmarket.api.MarketSummary;
import com.tanrunn.stockmarket.api.StockMarketApi;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 股市摘要适配器：只调用 {@link StockMarketApi#summary(ServerPlayer)}。
 *
 * <p>不再调用 account()、不访问 MarketService；金额单位为分，由
 * {@link MoneyFormat} 统一转换（今日盈亏正数显式带 +）。旧版业务 Mod 缺少
 * summary API 时本类不会被加载。</p>
 */
public final class StockMarketSummaryProvider implements AppSummaryProvider {

    @Override
    public MenuApp app() {
        return MenuApp.STOCK_MARKET;
    }

    @Override
    public AppCardSummary summary(ServerPlayer player) {
        MarketSummary data = StockMarketApi.summary(player);
        List<String> lines = new ArrayList<>(2);
        lines.add("总资产 " + MoneyFormat.amount(data.totalValueCents())
                + " · 现金 " + MoneyFormat.amount(data.cashCents()));
        lines.add("今日 " + MoneyFormat.pnl(data.dailyPnlCents())
                + " · 持仓 " + data.holdingKinds()
                + " · 委托 " + data.openOrderCount());
        return new AppCardSummary(lines);
    }
}
