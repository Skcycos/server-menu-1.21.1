package com.tanrunn.servermenu.client.navigation;

import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.ApricityScreen;
import com.tanrunn.servermenu.ServerMenuMod;
import com.tanrunn.servermenu.client.PlayerInfoScreen;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.OpenMenuRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端"返回 Pad"导航器（仅客户端加载，专用服务端不加载本类）。
 *
 * <p>每个客户端 tick 检查 {@link Minecraft#getScreen()}：只处理
 * {@link ApricityScreen}，通过 {@link ApricityScreen#getLinkedDocument()} 取得
 * Document，用 {@link BusinessPage#fromDocumentPath(String)} 按精确逻辑路径
 * 白名单识别建筑商店/股市/黄历/个人信息页面，并向 document.body 注入固定 ID 的
 * "返回 Pad"按钮（top layer，位于业务内容上方）。</p>
 *
 * <p>AUI 刷新会重建 DOM：本类按 Document 实例 + {@link Document#getRefreshGeneration()}
 * 变化重新注入，同一代且按钮存在时不重复绑定；页面切换、关闭或未知页面时
 * 清理全部自身引用状态。决策逻辑在纯类 {@link NavigationState} 中。</p>
 *
 * <p>点击返回按钮只发送无字段的 {@link OpenMenuRequestPayload}，不构造快照、
 * 不直接 setScreen；服务端经 {@code MenuService} 生成权威快照后，
 * {@code PadScreen.onSnapshot} 用 Minecraft.setScreen 替换当前页面，
 * 由 Minecraft 正常走旧 Screen 的 removed() 生命周期（股市的 closePanel 由
 * 其自身 removed() 发出，本类不代发关闭包）。</p>
 */
@EventBusSubscriber(modid = ServerMenuMod.MODID, value = Dist.CLIENT)
public final class BusinessScreenNavigator {
    /** 固定按钮 ID：唯一由本类定义；业务页面、个人信息页与 Pad 模板均不得包含。 */
    public static final String RETURN_BUTTON_ID = "server-menu-return-pad";
    /** 固定按钮 class。 */
    public static final String RETURN_BUTTON_CLASS = "server-menu-return-pad";

    private static final String RETURN_TEXT = "‹ 返回 Pad";
    private static final String RETURNING_TEXT = "返回中…";

    private static final NavigationState state = new NavigationState();
    private static Element buttonElement;
    private static Document buttonDocument;

    private BusinessScreenNavigator() {
        throw new AssertionError();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        tick(Minecraft.getInstance());
    }

    /** 每个客户端 tick 维护/注入返回按钮。 */
    static void tick(Minecraft minecraft) {
        Screen screen = minecraft.screen;
        ApricityScreen apricityScreen = screen instanceof ApricityScreen a ? a : null;
        Document doc = apricityScreen == null ? null : apricityScreen.getLinkedDocument();
        boolean disposed = doc != null && doc.isDisposed();
        Object page = pageFor(doc);
        long generation = doc == null ? Long.MIN_VALUE : doc.getRefreshGeneration();
        boolean buttonPresent = buttonElement != null && buttonElement.isConnected();

        NavigationState.Action action = state.observe(
                apricityScreen, doc, disposed, generation, page, buttonPresent,
                System.currentTimeMillis());
        switch (action) {
            case REINJECT -> {
                if (doc == null || disposed || page == null) {
                    // observe 已保证不会出现，防御性清理。
                    clearButton();
                } else {
                    injectButton(doc);
                }
            }
            case CLEAR -> clearButton();
            case RESTORE_RETURN -> setButtonText(RETURN_TEXT);
            case NONE, CLICK_RETURN, CLICK_IGNORED -> {
                // 点击动作由点击监听器直接执行，tick 不处理。
            }
        }
    }

    private static Object pageFor(Document doc) {
        if (doc == null) {
            return null;
        }
        BusinessPage businessPage = BusinessPage.fromDocumentPath(doc.getPath()).orElse(null);
        if (businessPage != null) {
            return businessPage;
        }
        return PlayerInfoScreen.isTemplatePath(doc.getPath()) ? PlayerInfoScreen.TEMPLATE_PATH : null;
    }

    // ------------------------------------------------------------------ DOM

    /** 在当前代 Document 上创建并绑定返回按钮；旧按钮先清理。 */
    private static void injectButton(Document doc) {
        clearButton();
        if (doc.body == null) {
            // 文档 body 尚未就绪：安全跳过；下一 tick 按钮缺失会再次尝试注入。
            return;
        }
        Element button = doc.createElement("button");
        button.setAttribute("id", RETURN_BUTTON_ID);
        button.setAttribute("class", RETURN_BUTTON_CLASS);
        button.setAttribute("type", "button");
        button.setTextContent(RETURN_TEXT);
        applyButtonStyle(button);
        button.setTopLayer(true);
        button.addEventListener("click", BusinessScreenNavigator::onReturnClick);
        doc.body.appendChild(button);
        buttonElement = button;
        buttonDocument = doc;
    }

    /** 清理按钮元素与引用；Document 已释放时不做任何 DOM 操作。 */
    private static void clearButton() {
        if (buttonElement != null) {
            if (buttonDocument != null && !buttonDocument.isDisposed() && buttonElement.isConnected()) {
                buttonElement.remove();
            }
            buttonElement = null;
            buttonDocument = null;
        }
    }

    private static void setButtonText(String text) {
        if (buttonElement != null && buttonElement.isConnected()) {
            buttonElement.setTextContent(text);
        }
    }

    /** 与 Pad 国风一致的紧凑悬浮按钮；只用 AUI 支持的 inline style 属性。 */
    private static void applyButtonStyle(Element button) {
        button.setInlineStyleProperty("position", "fixed");
        button.setInlineStyleProperty("left", "16px");
        button.setInlineStyleProperty("top", "16px");
        button.setInlineStyleProperty("padding", "8px 14px");
        button.setInlineStyleProperty("background", "linear-gradient(180deg, #fbf6ec 0%, #f0e6d2 100%)");
        button.setInlineStyleProperty("color", "#303832");
        button.setInlineStyleProperty("border", "1px solid #a84f42");
        button.setInlineStyleProperty("border-radius", "6px");
        button.setInlineStyleProperty("box-shadow", "0 2px 8px rgba(60, 40, 20, 0.28)");
        button.setInlineStyleProperty("font-size", "14px");
        button.setInlineStyleProperty("letter-spacing", "1px");
        button.setInlineStyleProperty("cursor", "pointer");
        button.setInlineStyleProperty("z-index", "9999");
        button.setInlineStyleProperty("display", "inline-block");
        button.setInlineStyleProperty("line-height", "1.4");
    }

    // ------------------------------------------------------------------ input

    /** 返回按钮点击：阻止冒泡与默认行为，防抖后发送打开 Pad 请求。 */
    private static void onReturnClick(Event event) {
        event.stopPropagation();
        event.preventDefault();
        NavigationState.Action action = state.onReturnClick(System.currentTimeMillis());
        if (action == NavigationState.Action.CLICK_RETURN) {
            setButtonText(RETURNING_TEXT);
            PacketDistributor.sendToServer(new OpenMenuRequestPayload());
        }
    }
}
