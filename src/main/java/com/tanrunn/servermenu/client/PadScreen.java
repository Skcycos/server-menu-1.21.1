package com.tanrunn.servermenu.client;

import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.ApricityScreen;
import com.tanrunn.servermenu.ServerMenuMod;
import com.tanrunn.servermenu.common.menu.MenuApp;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.AppStatus;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.LaunchAppRequestPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.MenuFeedbackPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.MenuSnapshotPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.OpenPlayerInfoRequestPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pad 主界面（AUI ApricityScreen）。
 *
 * <p>数据由服务端 {@link MenuSnapshotPayload} 注入；卡片点击发送
 * {@link LaunchAppRequestPayload}，状态栏由 {@link MenuFeedbackPayload} 更新。
 * 所有 DOM 操作在客户端线程执行，文本一律用 setTextContent（不拼接用户可控内容）。</p>
 *
 * <p>AUI 刷新（END/autoReload）会重建 DOM：本类在 {@link #tick()} 中检测
 * {@link Document#getRefreshGeneration()} 变化并重新绑定，同一代只绑定一次。</p>
 */
public final class PadScreen extends ApricityScreen {
    public static final String TEMPLATE_PATH = "servermenu/screens/pad.html";
    private static final String OPEN_PLAYER_INFO_ACTION = "open_player_info";

    private static final String WELCOME_MESSAGE = "欢迎使用服务器服务中心，请选择要进入的应用。";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    private static PadScreen active;

    private List<AppStatus> apps = List.of();
    private Document boundDocument;
    private long boundGeneration = Long.MIN_VALUE;
    private String lastFeedback;
    private boolean feedbackError;

    private PadScreen(List<AppStatus> snapshot) {
        super(TEMPLATE_PATH);
        this.apps = new ArrayList<>(snapshot);
    }

    /** 服务端快照到达：首次打开页面，已打开则只更新数据并重绘。 */
    public static void onSnapshot(MenuSnapshotPayload payload) {
        if (payload == null) {
            return;
        }
        if (active != null) {
            active.applySnapshot(payload.apps());
            return;
        }
        active = new PadScreen(payload.apps());
        Minecraft.getInstance().setScreen(active.setPauseGame(false).setShowDefaultBackground(false));
    }

    /** 服务端反馈到达：更新状态栏；页面未打开时忽略。 */
    public static void onFeedback(MenuFeedbackPayload payload) {
        if (payload == null || active == null) {
            return;
        }
        active.applyFeedback(payload.message(), payload.error());
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void init() {
        super.init();
        Document doc = getLinkedDocument();
        if (doc == null) {
            ServerMenuMod.LOGGER.error("[Pad] template missing: {}", TEMPLATE_PATH);
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

    // ------------------------------------------------------------------ data

    private void applySnapshot(List<AppStatus> snapshot) {
        apps = new ArrayList<>(snapshot);
        Document doc = boundDocument;
        if (doc != null && !doc.isDisposed()) {
            renderAll(doc);
        }
    }

    private void applyFeedback(String message, boolean error) {
        lastFeedback = message == null ? "" : message;
        feedbackError = error;
        Document doc = boundDocument;
        if (doc != null && !doc.isDisposed()) {
            renderStatusBar(doc);
        }
    }

    // ------------------------------------------------------------------ binding

    /**
     * 绑定当前代 Document 的监听器并重绘；同一代重复调用直接返回。
     */
    private void bindDocument(Document doc) {
        long generation = doc.getRefreshGeneration();
        if (boundDocument == doc && boundGeneration == generation) {
            return;
        }
        boundDocument = doc;
        boundGeneration = generation;

        Element cards = doc.getElementById("app-cards");
        if (cards == null) {
            ServerMenuMod.LOGGER.error("[Pad] required element #app-cards missing in template {}", TEMPLATE_PATH);
            return;
        }
        cards.addEventListener("click", event -> onCardClick(event));
        renderAll(doc);
    }

    private void renderAll(Document doc) {
        renderHeader(doc);
        renderCards(doc);
        renderStatusBar(doc);
    }

    private void renderHeader(Document doc) {
        setText(doc, "pad-date", LocalDate.now().format(DATE_FORMAT));
        if (Minecraft.getInstance().player != null) {
            setText(doc, "pad-player", Minecraft.getInstance().player.getGameProfile().getName());
        }
    }

    /** 根据 installed/connected 更新卡片降级样式与状态标签。 */
    private void renderCards(Document doc) {
        Element cards = doc.getElementById("app-cards");
        if (cards == null) {
            return;
        }
        for (Element card : new ArrayList<>(cards.getChildren())) {
            String appId = card.getAttribute("data-app");
            if (appId == null || appId.isBlank()) {
                continue;
            }
            // 只处理白名单应用 ID：状态元素 ID 由 id + 白名单 ID 拼接，拒绝任意 ID。
            if (MenuApp.fromId(appId).isEmpty()) {
                continue;
            }
            AppStatus status = statusFor(appId);
            if (status == null) {
                continue;
            }
            String stateClass;
            String stateText;
            if (!status.installed()) {
                stateClass = "missing";
                stateText = "未安装";
            } else if (!status.connected()) {
                stateClass = "pending";
                stateText = "待接入";
            } else {
                stateClass = "connected";
                stateText = "已接入";
            }
            card.setAttribute("class", "app-card " + stateClass);
            Element state = doc.getElementById("app-state-" + appId);
            if (state != null) {
                state.setAttribute("class", "app-state " + stateClass);
                state.setTextContent(stateText);
            }
            renderSummary(doc, appId, status);
        }
    }

    /**
     * 渲染卡片摘要区（最多 3 行，ID 由白名单 appId 拼接，杜绝任意 ID）：
     * 未安装 → “服务器未安装”；已安装未接入 → “版本暂未接入”；
     * 已接入但摘要为空 → “摘要暂不可用”；已接入且有摘要 → 逐行显示。
     * 未使用的行隐藏，不留空白高度。只用 setTextContent，不用 innerHTML。
     */
    private void renderSummary(Document doc, String appId, AppStatus status) {
        for (int i = 0; i < AppStatus.MAX_SUMMARY_LINES; i++) {
            Element line = doc.getElementById("app-summary-" + appId + "-" + i);
            if (line == null) {
                continue;
            }
            String text = summaryText(status, i);
            if (text == null) {
                line.setAttribute("class", "app-summary hidden");
                line.setTextContent("");
            } else {
                line.setAttribute("class", i == 0 ? "app-summary lead" : "app-summary");
                line.setTextContent(text);
            }
        }
    }

    /** 第 index 行应显示的摘要文本；null 表示该行隐藏。 */
    private String summaryText(AppStatus status, int index) {
        if (!status.installed()) {
            return index == 0 ? "服务器未安装" : null;
        }
        if (!status.connected()) {
            return index == 0 ? "版本暂未接入" : null;
        }
        List<String> summaryLines = status.summaryLines();
        if (summaryLines == null || summaryLines.isEmpty()) {
            return index == 0 ? "摘要暂不可用" : null;
        }
        return index < summaryLines.size() ? summaryLines.get(index) : null;
    }

    private void renderStatusBar(Document doc) {
        Element status = doc.getElementById("status-bar");
        if (status == null) {
            return;
        }
        if (lastFeedback == null) {
            status.setTextContent(WELCOME_MESSAGE);
            status.setAttribute("class", "status-bar info");
        } else {
            status.setTextContent(lastFeedback);
            status.setAttribute("class", "status-bar " + (feedbackError ? "error" : "info"));
        }
    }

    private AppStatus statusFor(String appId) {
        for (AppStatus status : apps) {
            if (appId.equals(status.appId())) {
                return status;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    private void onCardClick(Event event) {
        Element target = event.target instanceof Element element ? element : null;
        if (target == null) {
            return;
        }
        Element actionCard = walkUp(target, "data-action");
        if (actionCard != null && OPEN_PLAYER_INFO_ACTION.equals(actionCard.getAttribute("data-action"))) {
            PacketDistributor.sendToServer(new OpenPlayerInfoRequestPayload());
            return;
        }
        Element card = walkUp(target, "data-app");
        if (card == null) {
            return;
        }
        String appId = card.getAttribute("data-app");
        if (appId == null || appId.isBlank()) {
            return;
        }
        PacketDistributor.sendToServer(new LaunchAppRequestPayload(appId));
    }

    private Element walkUp(Element start, String attribute) {
        Element current = start;
        while (current != null) {
            String value = current.getAttribute(attribute);
            if (value != null && !value.isBlank()) {
                return current;
            }
            current = current.parentElement;
        }
        return null;
    }

    private static void setText(Element element, String text) {
        element.setTextContent(text);
    }

    private static void setText(Document doc, String id, String text) {
        Element element = doc.getElementById(id);
        if (element != null) {
            element.setTextContent(text);
        }
    }
}
