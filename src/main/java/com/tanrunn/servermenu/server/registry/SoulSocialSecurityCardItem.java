package com.tanrunn.servermenu.server.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** 灵魂社保卡：灵魂数量保存在每一张实体卡片上。 */
public final class SoulSocialSecurityCardItem extends Item {
    public static final int MAX_SOULS = 1_000;

    public SoulSocialSecurityCardItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static int souls(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Integer value = stack.get(ModDataComponents.SOUL_COUNT.get());
        return value == null ? 0 : clampSouls(value);
    }

    public static int clampSouls(int value) {
        return Math.max(0, Math.min(MAX_SOULS, value));
    }

    public static int addSoul(ItemStack stack) {
        int before = souls(stack);
        int after = nextSoulCount(before);
        if (stack != null && !stack.isEmpty() && after != before) {
            stack.set(ModDataComponents.SOUL_COUNT.get(), after);
        }
        return after;
    }

    public static int nextSoulCount(int current) {
        return Math.min(MAX_SOULS, clampSouls(current) + 1);
    }

    public static void clearSouls(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            stack.remove(ModDataComponents.SOUL_COUNT.get());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(
                "item.server_menu.soul_social_security_card.souls",
                souls(stack), MAX_SOULS));
        tooltipComponents.add(Component.translatable(
                "item.server_menu.soul_social_security_card.carry_rule"));
        tooltipComponents.add(Component.translatable(
                "item.server_menu.soul_social_security_card.exchange_rule"));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return souls(stack) > 0 || super.isFoil(stack);
    }
}
