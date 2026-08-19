package com.tanrunn.servermenu.server.integration;

import com.tanrunn.servermenu.common.menu.MenuApp;
import net.minecraft.server.level.ServerPlayer;

/**
 * 业务应用启动适配器接口。
 *
 * <p>本接口不依赖任何业务 Mod 的公开 API；每个具体实现只依赖对应业务 Mod 的公开
 * {@code *Api} 类。具体实现仅在服务端确认对应 Mod 已安装且兼容性探测通过后才被
 * 加载并实例化（见 {@link AppLauncherRegistry}）。</p>
 */
public interface AppLauncher {

    /** 该适配器负责的内置应用（白名单）。 */
    MenuApp app();

    /**
     * 启动应用（必须在服务端主线程调用）。
     *
     * @param player 目标玩家
     * @return 启动结果；实现不应抛出 {@link LinkageError} 之外的未检查异常，
     *         统一由 {@link AppLauncherRegistry} 边界转换
     */
    AppLaunchResult launch(ServerPlayer player);
}
