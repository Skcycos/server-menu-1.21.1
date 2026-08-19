package com.tanrunn.servermenu.server.integration.summary.oracle;

import com.tanrunn.chineseoracle.api.AlmanacSummary;
import com.tanrunn.chineseoracle.api.ChineseOracleApi;
import com.tanrunn.servermenu.common.menu.MenuApp;
import com.tanrunn.servermenu.server.integration.summary.AppCardSummary;
import com.tanrunn.servermenu.server.integration.summary.AppSummaryProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 今日黄历摘要适配器：只调用 {@link ChineseOracleApi#summary(ServerPlayer)}。
 *
 * <p>不访问 FortuneService、FortuneRegistry 或 FortuneDisplay；展示内容为
 * 业务 API 返回的展示就绪名称。所有文本仍受 {@link AppCardSummary} 的行数与
 * 长度上限约束。旧版业务 Mod 缺少 summary API 时本类不会被加载。</p>
 */
public final class ChineseOracleSummaryProvider implements AppSummaryProvider {

    /** 宜/忌预览最多展示的项数。 */
    static final int PREVIEW_MAX = 2;

    @Override
    public MenuApp app() {
        return MenuApp.CHINESE_ORACLE;
    }

    @Override
    public AppCardSummary summary(ServerPlayer player) {
        AlmanacSummary data = ChineseOracleApi.summary(player);
        List<String> lines = new ArrayList<>(3);
        lines.add(data.tierName() + " · " + data.shichen()
                + (data.shichenAuspicious() ? " 吉" : " 凶"));
        lines.add("宜 " + preview(data.yiNames()));
        lines.add("忌 " + preview(data.jiNames()));
        return new AppCardSummary(lines);
    }

    /**
     * 宜/忌预览（纯逻辑，可单测）：空列表显示 "无"；最多展示前
     * {@link #PREVIEW_MAX} 项，用 "、" 连接；超过 2 项时追加 " 等N项"；
     * 空白名称忽略。
     */
    static String preview(List<String> names) {
        List<String> cleaned = new ArrayList<>(names == null ? 0 : names.size());
        if (names != null) {
            for (String name : names) {
                if (name != null && !name.isBlank()) {
                    cleaned.add(name);
                }
            }
        }
        if (cleaned.isEmpty()) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(cleaned.size(), PREVIEW_MAX);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append(cleaned.get(i));
        }
        if (cleaned.size() > PREVIEW_MAX) {
            sb.append(" 等").append(cleaned.size()).append("项");
        }
        return sb.toString();
    }
}
