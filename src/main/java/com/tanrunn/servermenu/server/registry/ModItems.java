package com.tanrunn.servermenu.server.registry;

import com.tanrunn.servermenu.ServerMenuMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * server-menu 物品：服务器服务中心终端（tablet）。
 *
 * <p>右键行为由 {@link com.tanrunn.servermenu.server.hook.TabletHooks}
 * 在服务端处理。</p>
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ServerMenuMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ServerMenuMod.MODID);

    public static final DeferredItem<Item> TABLET = ITEMS.registerSimpleItem("tablet", new Item.Properties().stacksTo(1));
    public static final DeferredItem<FlightRingItem> FLIGHT_RING = ITEMS.register("flight_ring",
            () -> new FlightRingItem(new Item.Properties()));
    public static final DeferredItem<SoulSocialSecurityCardItem> SOUL_SOCIAL_SECURITY_CARD = ITEMS.register(
            "soul_social_security_card",
            () -> new SoulSocialSecurityCardItem(new Item.Properties()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.server_menu"))
                    .icon(() -> new ItemStack(TABLET.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(TABLET.get());
                        output.accept(FLIGHT_RING.get());
                        output.accept(SOUL_SOCIAL_SECURITY_CARD.get());
                    })
                    .build());

    private ModItems() {
    }
}
