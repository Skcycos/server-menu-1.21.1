package com.tanrunn.servermenu.server.integration.summary;

import com.tanrunn.servermenu.common.menu.MenuApp;
import net.minecraft.server.level.ServerPlayer;

/**
 * 业务摘要适配器接口（server-menu 自有，不依赖任何业务 Mod 的公开 API）。
 *
 * <p>每个具体实现只依赖对应业务 Mod 的公开 {@code *Api} 类，且只在服务端确认
 * 对应 Mod 已安装、启动链路 connected 且摘要兼容性探测通过后才被加载并实例化
 * （见 {@link AppSummaryRegistry}）。</p>
 */
public interface AppSummaryProvider {

    /** 该适配器负责的内置应用（白名单）。 */
    MenuApp app();

    /**
     * 生成应用摘要（必须在服务端主线程调用）。
     *
     * @param player 目标玩家
     * @return 摘要；实现不得返回 null（注册表边界会把 null 视为空摘要），
     *         玩家相关 RuntimeException 由注册表边界降级为空摘要
     */
    AppCardSummary summary(ServerPlayer player);
}
