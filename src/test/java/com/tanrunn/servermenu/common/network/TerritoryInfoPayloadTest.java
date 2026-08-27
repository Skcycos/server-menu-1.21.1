package com.tanrunn.servermenu.common.network;

import com.tanrunn.servermenu.common.network.ServerMenuNetwork.TerritoryInfoPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 领地服务页快照的字节级编解码与边界测试。 */
class TerritoryInfoPayloadTest {
    @Test
    void roundTripsAllFields() {
        TerritoryInfoPayload payload = new TerritoryInfoPayload(
                true, true, true, 3, 8, 7, 92, 1000L, 12345L,
                "铜币", "xaero.pac_max_claims", "购买成功", false);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        TerritoryInfoPayload.STREAM_CODEC.encode(buf, payload);
        assertEquals(payload, TerritoryInfoPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void constructorClampsNumericValuesAndBoundsStrings() {
        TerritoryInfoPayload payload = new TerritoryInfoPayload(
                false, false, false, -1, -2, -3, -4, -5L, -6L,
                "x".repeat(100), "y".repeat(200), "z".repeat(300), true);
        assertEquals(0, payload.claimsHeld());
        assertEquals(0, payload.claimLimit());
        assertEquals(0, payload.purchasedClaims());
        assertEquals(0, payload.maxPurchasable());
        assertEquals(0L, payload.claimPrice());
        assertEquals(0L, payload.balanceMinorUnits());
        assertEquals(TerritoryInfoPayload.MAX_CURRENCY_LENGTH, payload.currency().length());
        assertEquals(TerritoryInfoPayload.MAX_PERMISSION_NODE_LENGTH, payload.permissionNode().length());
        assertEquals(TerritoryInfoPayload.MAX_MESSAGE_LENGTH, payload.message().length());
    }
}
