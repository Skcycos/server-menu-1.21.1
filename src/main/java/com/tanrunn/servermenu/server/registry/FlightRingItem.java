package com.tanrunn.servermenu.server.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** 使用原生耐久值表示充能的飞行戒指。 */
public final class FlightRingItem extends Item {
    /** 1 点耐久 = 2 秒实际创造飞行；总容量约 2 小时。 */
    public static final int MAX_DURABILITY = 3_600;

    public FlightRingItem(Properties properties) {
        super(properties.durability(MAX_DURABILITY).rarity(Rarity.RARE));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.server_menu.flight_ring.tooltip"));
        tooltip.add(Component.translatable("item.server_menu.flight_ring.flight"));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
