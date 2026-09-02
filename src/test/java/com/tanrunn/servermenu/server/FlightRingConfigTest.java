package com.tanrunn.servermenu.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 飞行戒指白名单配置的纯逻辑测试。 */
class FlightRingConfigTest {
    @Test
    void matchesExactDimensionIds() {
        assertTrue(FlightRingConfig.isWorldAllowed("minecraft:overworld",
                List.of("minecraft:overworld", "minecraft:the_nether")));
        assertFalse(FlightRingConfig.isWorldAllowed("minecraft:the_end",
                List.of("minecraft:overworld", "minecraft:the_nether")));
    }

    @Test
    void emptyOrNullWhitelistDeniesFlight() {
        assertFalse(FlightRingConfig.isWorldAllowed("minecraft:overworld", List.of()));
        assertFalse(FlightRingConfig.isWorldAllowed("minecraft:overworld", null));
        assertFalse(FlightRingConfig.isWorldAllowed(null, List.of("minecraft:overworld")));
    }
}
