package com.tanrunn.servermenu.common.network;

import com.tanrunn.servermenu.common.network.ServerMenuNetwork.AppStatus;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.MenuSnapshotPayload;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AppStatus / MenuSnapshotPayload 的真实字节级 codec 测试。
 *
 * <p>使用真实 {@link FriendlyByteBuf}（包装 Netty {@link Unpooled} 缓冲）做
 * 编码/解码往返，覆盖：0/1/3 行摘要、多应用完整往返、中文与金额格式化字符串、
 * 非法 summary 行数与非法 apps 数量的严格拒绝（{@link DecoderException}）、
 * 合法多应用不产生字段错位、解码结果不可变。</p>
 */
class AppStatusCodecTest {

    // ------------------------------------------------------------ 正常往返

    @Test
    void singleAppStatusRoundTripsWithZeroLines() {
        AppStatus status = new AppStatus("build_shop", true, true, List.of());
        FriendlyByteBuf buf = buffer();
        status.write(buf);
        AppStatus decoded = AppStatus.read(buf);
        assertEquals(status, decoded);
        assertTrue(decoded.summaryLines().isEmpty());
    }

    @Test
    void singleAppStatusRoundTripsWithOneLine() {
        AppStatus status = new AppStatus("stock_market", true, true, List.of("总资产 123.45 · 现金 67.00"));
        FriendlyByteBuf buf = buffer();
        status.write(buf);
        assertEquals(status, AppStatus.read(buf));
    }

    @Test
    void singleAppStatusRoundTripsWithThreeLines() {
        AppStatus status = new AppStatus("chinese_oracle", true, true,
                List.of("吉 · 午时 吉", "宜 祈福、出行 等5项", "忌 无"));
        FriendlyByteBuf buf = buffer();
        status.write(buf);
        assertEquals(status, AppStatus.read(buf));
    }

    @Test
    void chineseTextAndMoneyFormattingRoundTrip() {
        // 中文、Long.MIN_VALUE 格式化结果等普通字符串必须原样往返。
        AppStatus status = new AppStatus("stock_market", true, true,
                List.of("今日 -92233720368547758.08 · 持仓 2", "余额 1,234.50 金币"));
        FriendlyByteBuf buf = buffer();
        status.write(buf);
        AppStatus decoded = AppStatus.read(buf);
        assertEquals(List.of("今日 -92233720368547758.08 · 持仓 2", "余额 1,234.50 金币"),
                decoded.summaryLines());
    }

    @Test
    void fullMenuSnapshotRoundTripsWithThreeApps() {
        List<AppStatus> apps = List.of(
                new AppStatus("build_shop", true, true, List.of("营业中 · 12 件商品", "余额 500 金币")),
                new AppStatus("stock_market", true, true, List.of("总资产 12345.67 · 现金 500.00",
                        "今日 +12.34 · 持仓 3 · 委托 2")),
                new AppStatus("chinese_oracle", false, false, List.of()));
        MenuSnapshotPayload payload = new MenuSnapshotPayload(apps);

        FriendlyByteBuf buf = buffer();
        MenuSnapshotPayload.STREAM_CODEC.encode(buf, payload);
        MenuSnapshotPayload decoded = MenuSnapshotPayload.STREAM_CODEC.decode(buf);

        assertEquals(payload.apps(), decoded.apps());
        // 合法多应用往返后字段不错位：逐应用逐一比对。
        for (int i = 0; i < apps.size(); i++) {
            assertEquals(apps.get(i), decoded.apps().get(i));
        }
    }

    // ------------------------------------------------------------ 非法长度严格拒绝

    @Test
    void summaryLineCountFourIsRejected() {
        FriendlyByteBuf buf = buffer();
        buf.writeUtf("build_shop", 64);
        buf.writeBoolean(true);
        buf.writeBoolean(true);
        buf.writeVarInt(4); // 非法：合法范围 0..3
        assertThrows(DecoderException.class, () -> AppStatus.read(buf));
    }

    @Test
    void negativeSummaryLineCountIsRejected() {
        FriendlyByteBuf buf = buffer();
        buf.writeUtf("build_shop", 64);
        buf.writeBoolean(true);
        buf.writeBoolean(true);
        buf.writeVarInt(-1); // 非法：负数
        assertThrows(DecoderException.class, () -> AppStatus.read(buf));
    }

    @Test
    void appsCountAboveMaxIsRejected() {
        FriendlyByteBuf buf = buffer();
        buf.writeVarInt(ServerMenuNetwork.MAX_APPS + 1); // 非法：MAX_APPS + 1
        assertThrows(DecoderException.class, () -> MenuSnapshotPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void negativeAppsCountIsRejected() {
        FriendlyByteBuf buf = buffer();
        buf.writeVarInt(-1); // 非法：负数
        assertThrows(DecoderException.class, () -> MenuSnapshotPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void maxAppsPayloadRoundTrips() {
        // 合法上限边界：MAX_APPS 个应用仍可正常往返。
        List<AppStatus> apps = new ArrayList<>();
        for (int i = 0; i < ServerMenuNetwork.MAX_APPS; i++) {
            apps.add(new AppStatus("app-" + i, true, false, List.of("行-" + i)));
        }
        MenuSnapshotPayload payload = new MenuSnapshotPayload(apps);
        FriendlyByteBuf buf = buffer();
        MenuSnapshotPayload.STREAM_CODEC.encode(buf, payload);
        assertEquals(payload.apps(), MenuSnapshotPayload.STREAM_CODEC.decode(buf).apps());
    }

    // ------------------------------------------------------------ 解码结果不可变

    @Test
    void decodedAppsAndSummaryLinesAreImmutable() {
        FriendlyByteBuf buf = buffer();
        MenuSnapshotPayload.STREAM_CODEC.encode(buf, new MenuSnapshotPayload(List.of(
                new AppStatus("build_shop", true, true, List.of("a", "b")))));
        MenuSnapshotPayload decoded = MenuSnapshotPayload.STREAM_CODEC.decode(buf);

        assertThrows(UnsupportedOperationException.class, () -> decoded.apps().add(
                new AppStatus("x", true, true, List.of())));
        assertThrows(UnsupportedOperationException.class,
                () -> decoded.apps().get(0).summaryLines().add("x"));
    }

    // ------------------------------------------------------------ 构造边界

    @Test
    void constructorStillEnforcesLineAndLengthBounds() {
        AppStatus status = new AppStatus("a", true, true, List.of("1", "2", "3", "4"));
        assertEquals(3, status.summaryLines().size());
        AppStatus longLine = new AppStatus("a", true, true, List.of("长".repeat(200)));
        assertEquals(AppStatus.MAX_SUMMARY_LINE_LENGTH, longLine.summaryLines().get(0).length());
    }

    @Test
    void summaryBoundariesMatchServerSideDto() {
        assertEquals(com.tanrunn.servermenu.server.integration.summary.AppCardSummary.MAX_LINES,
                AppStatus.MAX_SUMMARY_LINES);
        assertEquals(com.tanrunn.servermenu.server.integration.summary.AppCardSummary.MAX_LINE_LENGTH,
                AppStatus.MAX_SUMMARY_LINE_LENGTH);
    }

    // ------------------------------------------------------------ helpers

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }
}
