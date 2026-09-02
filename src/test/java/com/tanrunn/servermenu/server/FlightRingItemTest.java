package com.tanrunn.servermenu.server;

import com.tanrunn.servermenu.server.registry.FlightRingItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 飞行戒指原生耐久语义测试。 */
class FlightRingItemTest {
    @Test
    void oneDurabilityPointRepresentsOneChargeUnit() {
        assertEquals(3_600, FlightRingItem.MAX_DURABILITY);
        assertEquals(3_600, FlightRingService.remainingDurability(3_600, 0));
        assertEquals(3_599, FlightRingService.remainingDurability(3_600, 1));
        assertEquals(0, FlightRingService.damageAfterCharge(0));
        assertEquals(4, FlightRingService.damageAfterFlight(3_600, 3));
        assertEquals(3_600, FlightRingService.damageAfterFlight(3_600, 3_600));
    }
}
