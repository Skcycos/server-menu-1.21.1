package com.tanrunn.servermenu.server.integration.curios;

import com.tanrunn.servermenu.server.FlightRingService;
import com.tanrunn.servermenu.server.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** Curios 9.x 的飞行戒指能力适配器。 */
final class CuriosFlightRing implements ICurioItem {
    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!slotContext.cosmetic() && slotContext.entity() instanceof ServerPlayer player) {
            FlightRingService.tick(player, slotContext.identifier(), slotContext.index(), stack);
        }
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack previousStack, ItemStack stack) {
        if (!slotContext.cosmetic() && slotContext.entity() instanceof ServerPlayer player) {
            FlightRingService.tick(player, slotContext.identifier(), slotContext.index(), stack);
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        // Curios 9.x compares the complete ItemStack on every tick.  Changing the
        // vanilla durability component therefore emits an unequip/equip pair even
        // though the same ring is still in the slot.  Only clear flight when the
        // replacement is actually empty or a different item.
        if (!slotContext.cosmetic() && shouldClearFlightState(newStack)
                && slotContext.entity() instanceof ServerPlayer player) {
            FlightRingService.unequip(player, slotContext.identifier(), slotContext.index());
        }
    }

    static boolean shouldClearFlightState(ItemStack newStack) {
        return newStack == null || newStack.isEmpty()
                || !newStack.is(ModItems.FLIGHT_RING.get());
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return !slotContext.cosmetic();
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext slotContext, ItemStack stack) {
        return new ICurio.SoundInfo(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GOLD.value(), 1.0f, 1.0f);
    }
}
