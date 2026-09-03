package com.tanrunn.servermenu.server.integration.curios;

import com.tanrunn.servermenu.server.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Curios 耐久变化不会被误判为戒指卸下的回归测试。 */
class CuriosFlightRingTest {
    @Test
    void durabilityChangeKeepsFlightState() {
        ItemStack sameRing = ModItems.FLIGHT_RING.get().getDefaultInstance();
        sameRing.setDamageValue(1);

        assertFalse(CuriosFlightRing.shouldClearFlightState(sameRing));
    }

    @Test
    void emptyOrDifferentReplacementClearsFlightState() {
        assertTrue(CuriosFlightRing.shouldClearFlightState(ItemStack.EMPTY));
        assertTrue(CuriosFlightRing.shouldClearFlightState(new ItemStack(Items.IRON_INGOT)));
    }
}
