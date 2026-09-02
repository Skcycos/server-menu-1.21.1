package com.tanrunn.servermenu.server.registry;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;

/** 使用原生耐久值表示充能的飞行戒指。 */
public final class FlightRingItem extends Item {
    /** 1 点耐久 = 2 秒实际创造飞行；总容量约 2 小时。 */
    public static final int MAX_DURABILITY = 3_600;

    public FlightRingItem(Properties properties) {
        super(properties.durability(MAX_DURABILITY).setNoRepair().rarity(Rarity.RARE));
    }

    /** 飞行戒指的耐久只能由 LC 充能，不能通过原版或其他通用修复入口恢复。 */
    @Override
    public boolean isRepairable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return false;
    }

    /** 不允许通过附魔台、附魔书或铁砧给戒指添加任何附魔。 */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return false;
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
