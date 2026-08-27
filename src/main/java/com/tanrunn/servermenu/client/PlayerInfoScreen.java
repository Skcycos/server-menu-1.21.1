package com.tanrunn.servermenu.client;

import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.ApricityScreen;
import com.tanrunn.servermenu.ServerMenuMod;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.OpenPlayerInfoRequestPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.PlayerInfoPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * 个人信息页：只展示服务端下发的玩家状态与原版统计快照。
 *
 * <p>AUI 刷新会重建 Document，因此监听器按 Document 实例与刷新代数重新绑定，
 * 与建筑商店等业务页面保持相同的生命周期处理方式。</p>
 */
public final class PlayerInfoScreen extends ApricityScreen {
    public static final String TEMPLATE_PATH = "servermenu/screens/player_info.html";

    private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance(Locale.ROOT);
    private static PlayerInfoScreen active;

    private PlayerInfoPayload snapshot;
    private Document boundDocument;
    private long boundGeneration = Long.MIN_VALUE;

    private PlayerInfoScreen(PlayerInfoPayload snapshot) {
        super(TEMPLATE_PATH);
        this.snapshot = snapshot;
    }

    /** 服务端快照到达：首次打开页面，已打开则只更新数据。 */
    public static void onSnapshot(PlayerInfoPayload payload) {
        if (payload == null) {
            return;
        }
        if (active != null) {
            active.applySnapshot(payload);
            return;
        }
        active = new PlayerInfoScreen(payload);
        Minecraft.getInstance().setScreen(active.setPauseGame(false).setShowDefaultBackground(false));
    }

    /** 供返回导航器精确识别当前页面。 */
    public static boolean isTemplatePath(String path) {
        return TEMPLATE_PATH.equals(path);
    }

    @Override
    protected void init() {
        super.init();
        Document doc = getLinkedDocument();
        if (doc == null) {
            ServerMenuMod.LOGGER.error("[PlayerInfo] template missing: {}", TEMPLATE_PATH);
            return;
        }
        bindDocument(doc);
    }

    @Override
    public void tick() {
        super.tick();
        Document doc = getLinkedDocument();
        if (doc != null && (boundDocument != doc || boundGeneration != doc.getRefreshGeneration())) {
            bindDocument(doc);
        }
    }

    @Override
    public void removed() {
        super.removed();
        boundDocument = null;
        active = null;
    }

    private void applySnapshot(PlayerInfoPayload payload) {
        snapshot = payload;
        Document doc = boundDocument;
        if (doc != null && !doc.isDisposed()) {
            renderAll(doc);
        }
    }

    private void bindDocument(Document doc) {
        long generation = doc.getRefreshGeneration();
        if (boundDocument == doc && boundGeneration == generation) {
            return;
        }
        boundDocument = doc;
        boundGeneration = generation;

        Element refresh = doc.getElementById("player-info-refresh");
        if (refresh != null) {
            refresh.addEventListener("click", this::onRefreshClick);
        }
        renderAll(doc);
    }

    private void renderAll(Document doc) {
        if (snapshot == null) {
            return;
        }
        setText(doc, "player-name", snapshot.playerName());
        setText(doc, "player-uuid", snapshot.uuid());
        setText(doc, "player-dimension", dimensionName(snapshot.dimension()));
        setText(doc, "player-position", snapshot.positionX() + ", "
                + snapshot.positionY() + ", " + snapshot.positionZ());
        setText(doc, "stat-balance", balanceText(snapshot));
        setText(doc, "stat-balance-note", balanceNote(snapshot));

        setText(doc, "stat-level", integer(snapshot.level()));
        setText(doc, "stat-experience", integer(snapshot.totalExperience())
                + " · " + snapshot.experienceProgressPermille() / 10 + "%");
        setText(doc, "stat-health", decimal(snapshot.healthTenths())
                + " / " + decimal(snapshot.maxHealthTenths()));
        setText(doc, "stat-hunger", integer(snapshot.foodLevel())
                + " · 饱和 " + decimal(snapshot.saturationTenths()));

        setText(doc, "stat-play-time", playTime(snapshot.playTimeTicks()));
        setText(doc, "stat-world-days", integer(snapshot.worldDays()) + " 天");
        setText(doc, "stat-deaths", integer(snapshot.deaths()));
        setText(doc, "stat-mob-kills", integer(snapshot.mobKills()));
        setText(doc, "stat-player-kills", integer(snapshot.playerKills()));
        setText(doc, "stat-jumps", integer(snapshot.jumps()));
        setText(doc, "stat-walk-distance", distance(snapshot.walkDistanceCm()));
        setText(doc, "stat-blocks-mined", integer(snapshot.blocksMined()));
        setText(doc, "stat-total-experience", integer(snapshot.totalExperience()));
    }

    private void onRefreshClick(Event event) {
        event.stopPropagation();
        event.preventDefault();
        PacketDistributor.sendToServer(new OpenPlayerInfoRequestPayload());
    }

    private static void setText(Document doc, String id, String text) {
        Element element = doc.getElementById(id);
        if (element != null) {
            element.setTextContent(text == null ? "—" : text);
        }
    }

    private static String integer(long value) {
        return INTEGER_FORMAT.format(Math.max(0L, value));
    }

    private static String decimal(int tenths) {
        int safe = Math.max(0, tenths);
        return (safe / 10) + "." + (safe % 10);
    }

    private static String balanceText(PlayerInfoPayload snapshot) {
        if (!snapshot.balanceAvailable()) {
            return "暂不可用";
        }
        String currency = snapshot.balanceCurrency();
        if (currency == null || currency.isBlank()) {
            currency = "铜币";
        }
        return integer(snapshot.balanceMinorUnits()) + " " + currency;
    }

    private static String balanceNote(PlayerInfoPayload snapshot) {
        if (!snapshot.balanceAvailable()) {
            return "LC 银行桥接未就绪";
        }
        return snapshot.balanceQuarantined()
                ? "Lightman's Currency · 当前维度交易隔离"
                : "Lightman's Currency · main 货币链";
    }

    private static String dimensionName(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return "未知维度";
        }
        return switch (dimension) {
            case "minecraft:overworld" -> "主世界";
            case "minecraft:the_nether" -> "下界";
            case "minecraft:the_end" -> "末地";
            default -> dimension;
        };
    }

    private static String playTime(long ticks) {
        long totalSeconds = Math.max(0L, ticks) / 20L;
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (days > 0) {
            return days + "天 " + String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static String distance(long centimeters) {
        long safe = Math.max(0L, centimeters);
        if (safe < 100L) {
            return integer(safe) + " cm";
        }
        return String.format(Locale.ROOT, "%.2f m", safe / 100.0D);
    }
}
