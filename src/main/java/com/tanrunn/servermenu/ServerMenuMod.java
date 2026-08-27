package com.tanrunn.servermenu;

import com.mojang.logging.LogUtils;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork;
import com.tanrunn.servermenu.server.MenuCommands;
import com.tanrunn.servermenu.server.MenuService;
import com.tanrunn.servermenu.server.hook.TabletHooks;
import com.tanrunn.servermenu.server.integration.AppLauncherRegistry;
import com.tanrunn.servermenu.server.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

/**
 * server-menu 主入口（服务器 Pad 菜单）。
 *
 * <p>common/server 代码不引用任何客户端或 AUI 类；客户端界面与网络处理
 * 只在 {@link com.tanrunn.servermenu.client} 包中。</p>
 */
@Mod(ServerMenuMod.MODID)
public class ServerMenuMod {
    public static final String MODID = "server_menu";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ServerMenuMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

        ModItems.ITEMS.register(modEventBus);
        ModItems.TABS.register(modEventBus);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                com.tanrunn.servermenu.server.TerritoryShopConfig.SPEC,
                "server-menu-territory.toml");

        // ServerMenuMod 自身没有 @SubscribeEvent 方法，不能注册到 EVENT_BUS；
        // 命令与退出清理由 @EventBusSubscriber 的 ServerEvents 负责。
        NeoForge.EVENT_BUS.register(TabletHooks.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("{} initialized", MODID);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ServerMenuNetwork.CHANNEL)
                .versioned("1")
                .optional();
        ServerMenuNetwork.register(registrar);
        LOGGER.info("Registered {} network payloads", ServerMenuNetwork.class.getSimpleName());
    }

    @EventBusSubscriber(modid = ServerMenuMod.MODID)
    public static class ServerEvents {

        @SubscribeEvent
        public static void onCommands(RegisterCommandsEvent event) {
            MenuCommands.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent event) {
            // 预热兼容性探测并记录一次摘要（installed/connected 日志证据）。
            AppLauncherRegistry.logCompatibilitySummary();
            // 摘要能力探测与日志（与启动链路完全隔离；未接入时不做类加载探测）。
            com.tanrunn.servermenu.server.integration.summary.AppSummaryRegistry.logCompatibilitySummary();
            // LC 经济桥接（可选依赖）：未安装 / 探针失败时保持不可用，不崩溃。
            com.tanrunn.servermenu.server.integration.lc.LcEconomyBootstrap.bootstrap();
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                MenuService.INSTANCE.onPlayerLoggedOut(player.getUUID());
            }
        }
    }
}
