package com.tanrunn.servermenu.server.hook;

import com.tanrunn.servermenu.ServerMenuMod;
import com.tanrunn.servermenu.server.MenuService;
import com.tanrunn.servermenu.server.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Pad 物品交互：玩家手持右键时由服务端统一打开菜单。
 *
 * <p>NeoForge 1.21.1 中客户端对任意物品的右键都会发送使用包，
 * 服务端处理时触发本事件（仅服务端实例的实体是 ServerPlayer）。</p>
 */
public final class TabletHooks {
    private TabletHooks() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!event.getItemStack().is(ModItems.TABLET.get())) {
            return;
        }
        MenuService.INSTANCE.openMenu(player);
    }
}
