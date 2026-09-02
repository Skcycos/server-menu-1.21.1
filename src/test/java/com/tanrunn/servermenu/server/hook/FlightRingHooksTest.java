package com.tanrunn.servermenu.server.hook;

import com.tanrunn.servermenu.server.registry.FlightRingItem;
import com.tanrunn.servermenu.server.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 飞行戒指非 LC 修复入口的回归测试。 */
class FlightRingHooksTest {
    @Test
    void flightRingCannotUseRepairOrEnchantmentApis() {
        FlightRingItem ring = ModItems.FLIGHT_RING.get();
        ItemStack stack = mockStack(ring);

        assertFalse(ring.isRepairable(stack));
        assertFalse(ring.isValidRepairItem(stack, mockStack(Items.IRON_INGOT)));
        assertFalse(ring.isEnchantable(stack));
    }

    @Test
    void anvilRequiresNoFlightRingWithASecondInput() {
        FlightRingItem ring = ModItems.FLIGHT_RING.get();
        ItemStack flightRing = mockStack(ring);
        ItemStack secondRing = mockStack(ring);
        ItemStack iron = mockStack(Items.IRON_INGOT);

        assertTrue(FlightRingHooks.isAnvilRepairBlocked(flightRing, secondRing));
        assertTrue(FlightRingHooks.isAnvilRepairBlocked(flightRing, iron));
        assertTrue(FlightRingHooks.isAnvilRepairBlocked(iron, flightRing));
        assertFalse(FlightRingHooks.isAnvilRepairBlocked(flightRing, ItemStack.EMPTY));
    }

    private static ItemStack mockStack(Item item) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.isEmpty()).thenReturn(false);
        when(stack.getItem()).thenReturn(item);
        return stack;
    }
}
