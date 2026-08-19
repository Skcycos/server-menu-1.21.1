package com.tanrunn.servermenu.client.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * "返回 Pad"导航状态机（{@link NavigationState}）的纯逻辑测试。
 *
 * <p>用普通 Object 充当 Screen/Document 的同一性占位，不依赖 AUI/Minecraft。</p>
 */
class NavigationStateTest {
    private static final Object SCREEN = new Object();
    private static final Object DOC = new Object();
    private static final long T0 = 10_000;

    // ------------------------------------------------------------ 注入决策

    @Test
    void firstObservationOfBusinessDocumentReinjects() {
        NavigationState s = new NavigationState();
        assertEquals(NavigationState.Action.REINJECT,
                s.observe(SCREEN, DOC, false, 1, BusinessPage.STOCK_MARKET, false, T0));
    }

    @Test
    void sameGenerationWithButtonPresentDoesNothing() {
        NavigationState s = new NavigationState();
        s.observe(SCREEN, DOC, false, 1, BusinessPage.STOCK_MARKET, false, T0);
        assertEquals(NavigationState.Action.NONE,
                s.observe(SCREEN, DOC, false, 1, BusinessPage.STOCK_MARKET, true, T0 + 50));
    }

    @Test
    void missingButtonReinjectsInSameGeneration() {
        NavigationState s = new NavigationState();
        s.observe(SCREEN, DOC, false, 1, BusinessPage.STOCK_MARKET, true, T0);
        assertEquals(NavigationState.Action.REINJECT,
                s.observe(SCREEN, DOC, false, 1, BusinessPage.STOCK_MARKET, false, T0 + 50));
    }

    @Test
    void generationChangeReinjects() {
        NavigationState s = new NavigationState();
        s.observe(SCREEN, DOC, false, 1, BusinessPage.STOCK_MARKET, true, T0);
        assertEquals(NavigationState.Action.REINJECT,
                s.observe(SCREEN, DOC, false, 2, BusinessPage.STOCK_MARKET, true, T0 + 50));
    }

    @Test
    void documentInstanceChangeReinjects() {
        NavigationState s = new NavigationState();
        s.observe(SCREEN, DOC, false, 1, BusinessPage.STOCK_MARKET, true, T0);
        Object doc2 = new Object();
        assertEquals(NavigationState.Action.REINJECT,
                s.observe(SCREEN, doc2, false, 1, BusinessPage.STOCK_MARKET, true, T0 + 50));
    }

    @Test
    void screenChangeToAnotherBusinessPageReinjects() {
        NavigationState s = new NavigationState();
        s.observe(SCREEN, DOC, false, 1, BusinessPage.STOCK_MARKET, true, T0);
        Object screen2 = new Object();
        assertEquals(NavigationState.Action.REINJECT,
                s.observe(screen2, DOC, false, 1, BusinessPage.BUILD_SHOP, false, T0 + 50));
    }

    @Test
    void screenChangeToUnknownPageClears() {
        NavigationState s = new NavigationState();
        s.observe(SCREEN, DOC, false, 1, BusinessPage.STOCK_MARKET, true, T0);
        Object screen2 = new Object();
        assertEquals(NavigationState.Action.CLEAR,
                s.observe(screen2, DOC, false, 1, null, false, T0 + 50));
    }

    @Test
    void screenClosedClears() {
        NavigationState s = new NavigationState();
        s.observe(SCREEN, DOC, false, 1, BusinessPage.STOCK_MARKET, true, T0);
        assertEquals(NavigationState.Action.CLEAR, s.observe(null, null, false, 0, null, false, T0 + 50));
    }

    @Test
    void disposedDocumentClears() {
        NavigationState s = new NavigationState();
        s.observe(SCREEN, DOC, false, 1, BusinessPage.STOCK_MARKET, true, T0);
        assertEquals(NavigationState.Action.CLEAR,
                s.observe(SCREEN, DOC, true, 1, BusinessPage.STOCK_MARKET, false, T0 + 50));
    }

    @Test
    void unknownPageClearsAndStaysCleared() {
        NavigationState s = new NavigationState();
        assertEquals(NavigationState.Action.CLEAR, s.observe(SCREEN, DOC, false, 1, null, false, T0));
        // 非业务页面连续 tick 保持 CLEAR，绝不注入。
        assertEquals(NavigationState.Action.CLEAR, s.observe(SCREEN, DOC, false, 1, null, false, T0 + 50));
        assertEquals(NavigationState.Action.CLEAR, s.observe(SCREEN, DOC, false, 7, null, false, T0 + 100));
    }

    @Test
    void padPathIsNeverTreatedAsBusiness() {
        NavigationState s = new NavigationState();
        assertEquals(NavigationState.Action.CLEAR,
                s.observe(SCREEN, DOC, false, 1,
                        BusinessPage.fromDocumentPath(BusinessPage.PAD_DOCUMENT_PATH).orElse(null),
                        false, T0));
    }

    @Test
    void pageSwitchBetweenBusinessPagesReinjects() {
        NavigationState s = new NavigationState();
        s.observe(SCREEN, DOC, false, 1, BusinessPage.STOCK_MARKET, true, T0);
        Object doc2 = new Object();
        assertEquals(NavigationState.Action.REINJECT,
                s.observe(SCREEN, doc2, false, 1, BusinessPage.BUILD_SHOP, false, T0 + 50));
    }

    // ------------------------------------------------------------ 点击状态机

    @Test
    void clickAcceptedOnceAndIgnoredWithinDebounce() {
        NavigationState s = new NavigationState();
        s.observe(SCREEN, DOC, false, 1, BusinessPage.STOCK_MARKET, true, T0);
        assertEquals(NavigationState.Action.CLICK_RETURN, s.onReturnClick(T0 + 100));
        assertEquals(NavigationState.Action.CLICK_IGNORED, s.onReturnClick(T0 + 200));
        // "返回中"未超时：即使已过防抖窗口也忽略。
        assertEquals(NavigationState.Action.CLICK_IGNORED, s.onReturnClick(T0 + 900));
    }

    @Test
    void returningTimeoutRestoresAndAllowsRetry() {
        NavigationState s = new NavigationState();
        s.observe(SCREEN, DOC, false, 1, BusinessPage.STOCK_MARKET, true, T0);
        assertEquals(NavigationState.Action.CLICK_RETURN, s.onReturnClick(T0 + 100));
        // tick 检测到等待超时：恢复按钮文案并允许重试。
        assertEquals(NavigationState.Action.RESTORE_RETURN,
                s.observe(SCREEN, DOC, false, 1, BusinessPage.STOCK_MARKET, true, T0 + 1_200));
        assertEquals(NavigationState.Action.CLICK_RETURN, s.onReturnClick(T0 + 1_300));
    }

    @Test
    void clickAfterTimeoutRestoresAndRetriesImmediately() {
        NavigationState s = new NavigationState();
        s.observe(SCREEN, DOC, false, 1, BusinessPage.STOCK_MARKET, true, T0);
        assertEquals(NavigationState.Action.CLICK_RETURN, s.onReturnClick(T0 + 100));
        // 点击时已超时：先恢复旧等待，再按新点击处理（防抖也已过期）。
        assertEquals(NavigationState.Action.CLICK_RETURN, s.onReturnClick(T0 + 1_200));
    }

    @Test
    void screenReplacementClearsReturningState() {
        NavigationState s = new NavigationState();
        s.observe(SCREEN, DOC, false, 1, BusinessPage.STOCK_MARKET, true, T0);
        assertEquals(NavigationState.Action.CLICK_RETURN, s.onReturnClick(T0 + 100));
        // 服务端快照到达、Pad 替换业务页面：清理全部状态。
        Object padScreen = new Object();
        Object padDoc = new Object();
        assertEquals(NavigationState.Action.CLEAR,
                s.observe(padScreen, padDoc, false, 1, null, false, T0 + 200));
        // 再次进入业务页面后点击视为全新请求（防抖与等待均已清空）。
        Object screen2 = new Object();
        s.observe(screen2, DOC, false, 1, BusinessPage.STOCK_MARKET, false, T0 + 300);
        assertEquals(NavigationState.Action.CLICK_RETURN, s.onReturnClick(T0 + 300));
    }
}
