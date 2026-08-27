package com.tanrunn.servermenu.common.network;

import com.tanrunn.servermenu.common.network.ServerMenuNetwork.PlayerInfoPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 个人信息快照的字节级编解码与构造边界测试。 */
class PlayerInfoPayloadTest {

    @Test
    void roundTripsAllFields() {
        PlayerInfoPayload payload = new PlayerInfoPayload(
                "建筑师", "00000000-0000-0000-0000-000000000001", "minecraft:overworld",
                42, 123_456, 875, 195, 200, 20, 35,
                true, false, 98_765L, "铜币",
                987_654L, 18L, 2, 340, 7, 1_234,
                456_789L, 12_345L, -12, 68, 301);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        PlayerInfoPayload.STREAM_CODEC.encode(buf, payload);

        assertEquals(payload, PlayerInfoPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void constructorClampsDisplayOnlyValues() {
        PlayerInfoPayload payload = new PlayerInfoPayload(
                "x".repeat(100), null, null,
                -1, -2, 2_000, -3, -4, -5, -6,
                true, true, -7L, null,
                -8L, -9L, -10, -11, -12, -13, -14L, -15L, 1, 2, 3);

        assertEquals(PlayerInfoPayload.MAX_PLAYER_NAME_LENGTH, payload.playerName().length());
        assertEquals("", payload.uuid());
        assertEquals("", payload.dimension());
        assertEquals(0, payload.level());
        assertEquals(0, payload.totalExperience());
        assertEquals(PlayerInfoPayload.MAX_EXPERIENCE_PROGRESS_PERMILLE,
                payload.experienceProgressPermille());
        assertEquals(0L, payload.balanceMinorUnits());
        assertEquals("", payload.balanceCurrency());
        assertEquals(0L, payload.playTimeTicks());
        assertEquals(0L, payload.blocksMined());
    }
}
