package com.tanrunn.servermenu.client;

import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.ApricityScreen;
import com.tanrunn.servermenu.ServerMenuMod;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.OpenMenuRequestPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.OpenOapcRequestPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.PurchaseTerritoryRequestPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.RefreshTerritoryRequestPayload;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.TerritoryInfoPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.text.NumberFormat;
import java.util.Locale;

/** OAPC 领地服务页：展示服务端权威数据，并提供购买与打开 OAPC 两个入口。 */
public final class TerritoryScreen extends ApricityScreen {
    public static final String TEMPLATE_PATH = "servermenu/screens/territory.html";

    private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance(Locale.ROOT);
    private static TerritoryScreen active;

    private TerritoryInfoPayload snapshot;
    private Document boundDocument;
    private long boundGeneration = Long.MIN_VALUE;

    private TerritoryScreen(TerritoryInfoPayload snapshot) {
        super(TEMPLATE_PATH);
        this.snapshot = snapshot;
    }

    public static void onSnapshot(TerritoryInfoPayload payload) {
        if (payload == null) {
            return;
        }
        if (active != null) {
            active.snapshot = payload;
            if (active.boundDocument != null && !active.boundDocument.isDisposed()) {
                active.renderAll(active.boundDocument);
            }
            return;
        }
        active = new TerritoryScreen(payload);
        Minecraft.getInstance().setScreen(active.setPauseGame(false).setShowDefaultBackground(false));
    }

    @Override
    protected void init() {
        super.init();
        Document doc = getLinkedDocument();
        if (doc == null) {
            ServerMenuMod.LOGGER.error("[Territory] template missing: {}", TEMPLATE_PATH);
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

    private void bindDocument(Document doc) {
        long generation = doc.getRefreshGeneration();
        if (boundDocument == doc && boundGeneration == generation) {
            return;
        }
        boundDocument = doc;
        boundGeneration = generation;
        Element root = doc.getElementById("territory-root");
        if (root != null) {
            root.addEventListener("click", this::onActionClick);
        }
        renderAll(doc);
    }

    private void renderAll(Document doc) {
        if (snapshot == null) {
            return;
        }
        setText(doc, "territory-claim-count", integer(snapshot.claimsHeld()));
        setText(doc, "territory-claim-limit", integer(snapshot.claimLimit()));
        setText(doc, "territory-purchased", integer(snapshot.purchasedClaims()));
        setText(doc, "territory-remaining", integer(snapshot.maxPurchasable()));
        setText(doc, "territory-price", integer(snapshot.claimPrice()) + " LC "
                + (snapshot.currency().isBlank() ? "金币" : snapshot.currency()));
        setText(doc, "territory-balance", snapshot.economyAvailable()
                ? integer(snapshot.balanceMinorUnits()) + " " + snapshot.currency() : "暂不可用");
        setText(doc, "territory-node", snapshot.oapcAvailable()
                ? snapshot.permissionNode() : "OAPC 未就绪");
        setText(doc, "territory-status", statusText(snapshot));
        Element status = doc.getElementById("territory-status");
        if (status != null) {
            status.setAttribute("class", "status-bar " + (snapshot.messageError() ? "error" : "info"));
        }
    }

    private void onActionClick(Event event) {
        event.stopPropagation();
        event.preventDefault();
        Element target = event.target instanceof Element element ? element : null;
        if (target == null) {
            return;
        }
        Element action = walkUp(target, "data-action");
        if (action == null) {
            return;
        }
        switch (action.getAttribute("data-action")) {
            case "refresh" -> PacketDistributor.sendToServer(new RefreshTerritoryRequestPayload());
            case "purchase" -> PacketDistributor.sendToServer(new PurchaseTerritoryRequestPayload());
            case "open_oapc" -> PacketDistributor.sendToServer(new OpenOapcRequestPayload());
            case "back" -> {
                active = null;
                Minecraft.getInstance().setScreen(null);
                PacketDistributor.sendToServer(new OpenMenuRequestPayload());
            }
            default -> {
            }
        }
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

    private static String statusText(TerritoryInfoPayload data) {
        if (data.message() != null && !data.message().isBlank()) {
            return data.message();
        }
        if (!data.oapcAvailable()) {
            return "OAPC 领地系统未安装或未就绪。";
        }
        if (!data.luckPermsAvailable()) {
            return "需要 LuckPerms 才能购买领地上限。";
        }
        if (!data.economyAvailable()) {
            return "需要 LC 银行才能购买领地上限。";
        }
        return "数据来自服务器；购买后 OAPC 上限会同步更新。";
    }

    private static void setText(Document doc, String id, String value) {
        Element element = doc.getElementById(id);
        if (element != null) {
            element.setTextContent(value == null ? "—" : value);
        }
    }

    private static String integer(long value) {
        return INTEGER_FORMAT.format(Math.max(0L, value));
    }
}
