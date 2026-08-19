package com.tanrunn.servermenu.common.network;

import com.tanrunn.servermenu.ServerMenuMod;
import com.tanrunn.servermenu.client.network.ClientPayloadHandler;
import com.tanrunn.servermenu.server.ServerPayloadHandler;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/**
 * server-menu 网络协议定义与注册。
 *
 * <p>所有字符串字段都有长度上限；payload 与 handler 不含任何客户端类
 * （客户端 handler 类只在客户端执行时才被加载）。解码侧对非法集合长度
 * （summary 行数、apps 数量）一律立即抛 {@link DecoderException}，
 * 由连接层丢弃该包，绝不按 0 处理或按任意网络输入循环。</p>
 */
public final class ServerMenuNetwork {
    public static final String CHANNEL = "main";

    private static final int MAX_APP_ID_LENGTH = 64;
    /** 快照最大应用数（包内可见，供同包 codec 测试使用）。 */
    static final int MAX_APPS = 16;
    private static final int MAX_FEEDBACK_LENGTH = 256;

    private ServerMenuNetwork() {
        throw new AssertionError();
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(MenuSnapshotPayload.TYPE, MenuSnapshotPayload.STREAM_CODEC,
                ClientPayloadHandler::handleSnapshot);
        registrar.playToClient(MenuFeedbackPayload.TYPE, MenuFeedbackPayload.STREAM_CODEC,
                ClientPayloadHandler::handleFeedback);
        registrar.playToServer(OpenMenuRequestPayload.TYPE, OpenMenuRequestPayload.STREAM_CODEC,
                ServerPayloadHandler::handleOpenMenu);
        registrar.playToServer(LaunchAppRequestPayload.TYPE, LaunchAppRequestPayload.STREAM_CODEC,
                ServerPayloadHandler::handleLaunchApp);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ServerMenuMod.MODID, path);
    }

    // ---------------------------------------------------------------- payloads

    /** 单个应用的快照状态；installed 由服务端用 ModList 检测，不信任客户端。 */
    public record AppStatus(String appId, boolean installed, boolean connected,
                            List<String> summaryLines) {
        /** 摘要最大行数与每行最大长度（与服务端 AppCardSummary 保持一致）。 */
        public static final int MAX_SUMMARY_LINES = 3;
        public static final int MAX_SUMMARY_LINE_LENGTH = 96;

        public AppStatus {
            appId = appId == null ? "" : appId;
            summaryLines = sanitizeSummaryLines(summaryLines);
        }

        void write(FriendlyByteBuf buf) {
            buf.writeUtf(appId, MAX_APP_ID_LENGTH);
            buf.writeBoolean(installed);
            buf.writeBoolean(connected);
            buf.writeVarInt(summaryLines.size());
            for (String line : summaryLines) {
                buf.writeUtf(line, MAX_SUMMARY_LINE_LENGTH);
            }
        }

        static AppStatus read(FriendlyByteBuf buf) {
            String appId = buf.readUtf(MAX_APP_ID_LENGTH);
            boolean installed = buf.readBoolean();
            boolean connected = buf.readBoolean();
            int declared = buf.readVarInt();
            // 严格拒绝非法集合长度：合法范围只能是 0..MAX_SUMMARY_LINES。
            // 越界立即抛解码异常（连接层丢弃该包），不消费 declared 行、
            // 不按 0 处理、不允许按任意网络输入执行超长循环。
            if (declared < 0 || declared > MAX_SUMMARY_LINES) {
                throw new DecoderException("Invalid summary line count " + declared
                        + " (expected 0.." + MAX_SUMMARY_LINES + ")");
            }
            List<String> lines = new ArrayList<>(declared);
            for (int i = 0; i < declared; i++) {
                lines.add(buf.readUtf(MAX_SUMMARY_LINE_LENGTH));
            }
            return new AppStatus(appId, installed, connected, lines);
        }

        /** 构造时保证边界：最多 MAX_SUMMARY_LINES 行、每行最多 MAX_SUMMARY_LINE_LENGTH 字符。 */
        private static List<String> sanitizeSummaryLines(List<String> raw) {
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<String> cleaned = new ArrayList<>(Math.min(raw.size(), MAX_SUMMARY_LINES));
            for (String line : raw) {
                if (cleaned.size() >= MAX_SUMMARY_LINES) {
                    break;
                }
                String safe = line == null ? "" : line;
                if (safe.length() > MAX_SUMMARY_LINE_LENGTH) {
                    safe = safe.substring(0, MAX_SUMMARY_LINE_LENGTH);
                }
                cleaned.add(safe);
            }
            return List.copyOf(cleaned);
        }
    }

    /** 客户端 → 服务端：请求打开菜单（为未来按键入口预留）。 */
    public record OpenMenuRequestPayload() implements CustomPacketPayload {
        public static final Type<OpenMenuRequestPayload> TYPE = new Type<>(id("open_menu_request"));
        public static final StreamCodec<FriendlyByteBuf, OpenMenuRequestPayload> STREAM_CODEC =
                StreamCodec.ofMember((buf, payload) -> {
                }, buf -> new OpenMenuRequestPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 服务端 → 客户端：菜单快照（应用安装/接入状态与摘要）。 */
    public record MenuSnapshotPayload(List<AppStatus> apps) implements CustomPacketPayload {
        public static final Type<MenuSnapshotPayload> TYPE = new Type<>(id("menu_snapshot"));
        public static final StreamCodec<FriendlyByteBuf, MenuSnapshotPayload> STREAM_CODEC =
                StreamCodec.ofMember(MenuSnapshotPayload::write, MenuSnapshotPayload::read);

        public MenuSnapshotPayload {
            // 防御性复制：apps 列表外部不可修改。
            apps = apps == null ? List.of() : List.copyOf(apps);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(apps.size());
            for (AppStatus status : apps) {
                status.write(buf);
            }
        }

        private static MenuSnapshotPayload read(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            // 严格拒绝非法应用数量：越界立即抛解码异常（连接层丢弃该包），
            // 禁止把 size 重置为 0 后返回空快照——那会留下 payload 剩余字节。
            if (size < 0 || size > MAX_APPS) {
                throw new DecoderException("Invalid app count " + size
                        + " (expected 0.." + MAX_APPS + ")");
            }
            List<AppStatus> apps = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                apps.add(AppStatus.read(buf));
            }
            return new MenuSnapshotPayload(apps);
        }
    }

    /** 客户端 → 服务端：点击应用卡片请求启动。 */
    public record LaunchAppRequestPayload(String appId) implements CustomPacketPayload {
        public static final Type<LaunchAppRequestPayload> TYPE = new Type<>(id("launch_app_request"));
        public static final StreamCodec<FriendlyByteBuf, LaunchAppRequestPayload> STREAM_CODEC =
                StreamCodec.ofMember(LaunchAppRequestPayload::write, LaunchAppRequestPayload::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeUtf(appId == null ? "" : appId, MAX_APP_ID_LENGTH);
        }

        private static LaunchAppRequestPayload read(FriendlyByteBuf buf) {
            return new LaunchAppRequestPayload(buf.readUtf(MAX_APP_ID_LENGTH));
        }
    }

    /** 服务端 → 客户端：Pad 状态栏反馈（纯文本，服务端生成）。 */
    public record MenuFeedbackPayload(String message, boolean error) implements CustomPacketPayload {
        public static final Type<MenuFeedbackPayload> TYPE = new Type<>(id("menu_feedback"));
        public static final StreamCodec<FriendlyByteBuf, MenuFeedbackPayload> STREAM_CODEC =
                StreamCodec.ofMember(MenuFeedbackPayload::write, MenuFeedbackPayload::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeUtf(message == null ? "" : message, MAX_FEEDBACK_LENGTH);
            buf.writeBoolean(error);
        }

        private static MenuFeedbackPayload read(FriendlyByteBuf buf) {
            return new MenuFeedbackPayload(buf.readUtf(MAX_FEEDBACK_LENGTH), buf.readBoolean());
        }
    }
}
