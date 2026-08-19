package com.tanrunn.servermenu;

import com.sighs.apricityui.resource.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.io.IOException;
import java.io.InputStream;

/**
 * 客户端初始化（仅客户端加载）。
 *
 * <p>网络包的客户端处理由 common 注册引用 {@code ClientPayloadHandler}，
 * 该类只在客户端执行时才被加载，专用服务端不会触碰任何客户端类。</p>
 */
@Mod(value = ServerMenuMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ServerMenuMod.MODID, value = Dist.CLIENT)
public final class ServerMenuModClient {
    // AUI 页面中可用的内置粗体字体族名（思源黑体 Bold，子集化；与股市共用同一字体文件）
    public static final String UI_FONT = "servermenu-ui";

    public ServerMenuModClient(ModContainer container) {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ServerMenuModClient::registerUiFont);
        ServerMenuMod.LOGGER.info("CLIENT SETUP");
    }

    private static void registerUiFont() {
        try (InputStream in = Minecraft.getInstance().getResourceManager()
                .open(ResourceLocation.fromNamespaceAndPath(ServerMenuMod.MODID, "fonts/noto_sans_bold.otf"))) {
            if (Font.registerFont(UI_FONT, in)) {
                ServerMenuMod.LOGGER.info("Registered built-in UI font: {}", UI_FONT);
            } else {
                ServerMenuMod.LOGGER.warn("Failed to register built-in UI font: {}", UI_FONT);
            }
        } catch (IOException e) {
            ServerMenuMod.LOGGER.error("Failed to load built-in UI font", e);
        }
    }
}
