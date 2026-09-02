package com.tanrunn.servermenu.server;

import com.tanrunn.servermenu.server.registry.SoulSocialSecurityCardItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SoulCardServiceTest {
    @Test
    void soulCountIsCappedAtOneThousand() {
        assertEquals(1, SoulSocialSecurityCardItem.nextSoulCount(0));
        assertEquals(1_000, SoulSocialSecurityCardItem.nextSoulCount(999));
        assertEquals(1_000, SoulSocialSecurityCardItem.nextSoulCount(1_000));
        assertEquals(1, SoulSocialSecurityCardItem.nextSoulCount(-50));
        assertEquals(1_000, SoulSocialSecurityCardItem.clampSouls(50_000));
    }

    @Test
    void oneSoulConvertsToOneCopper() {
        assertEquals(0, SoulCardService.conversionAmount(0));
        assertEquals(1, SoulCardService.conversionAmount(1));
        assertEquals(1_000, SoulCardService.conversionAmount(1_000));
        assertEquals(1_000, SoulCardService.conversionAmount(Integer.MAX_VALUE));
    }

    @Test
    void fountainEmitsTheWholeAmountOverTenSeconds() {
        long emitted = 0;
        for (int second = 1; second <= 10; second++) {
            emitted += SoulCardService.copperForSecond(1_000, second);
            assertEquals(100, SoulCardService.copperForSecond(1_000, second));
        }
        assertEquals(1_000, emitted);
    }

    @Test
    void fountainRemainderIsNotLost() {
        long emitted = 0;
        for (int second = 1; second <= 10; second++) {
            emitted += SoulCardService.copperForSecond(7, second);
        }
        assertEquals(7, emitted);
    }
}
