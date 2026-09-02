package com.tanrunn.servermenu.server.integration.curios;

import com.tanrunn.servermenu.server.registry.ModItems;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;

/** 仅在 Curios 存在时加载的 typed 注册桥。 */
public final class CuriosFlightRingRegistration {
    private CuriosFlightRingRegistration() {
    }

    public static void register() {
        Item ring = ModItems.FLIGHT_RING.get();
        CuriosApi.registerCurio(ring, new CuriosFlightRing());
    }
}
