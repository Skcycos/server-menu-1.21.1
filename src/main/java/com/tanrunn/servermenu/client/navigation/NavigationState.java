package com.tanrunn.servermenu.client.navigation;

/**
 * "返回 Pad"导航状态机（纯逻辑，无 Minecraft/AUI 依赖，可单测）。
 *
 * <p>跟踪当前 Screen/Document 实例（同一性比较）、AUI refreshGeneration、
 * 识别出的 {@link BusinessPage}、最近返回请求时间与"返回中"等待状态。
 * 每 tick 由 {@link BusinessScreenNavigator} 喂入当前观察值并执行返回的动作；
 * 点击与超时恢复也由本状态机裁决，DOM 与网络效果由导航器执行。</p>
 */
final class NavigationState {
    /** 返回请求的本地防抖窗口（毫秒），防止连点刷包。 */
    static final long CLICK_DEBOUNCE_MS = 600;
    /** 点击后仍停留在同一业务页面的恢复窗口（毫秒）；超时后恢复按钮文案并允许重试。 */
    static final long RETURNING_TIMEOUT_MS = 1_000;

    /** 状态机裁决出的动作。 */
    enum Action {
        /** 同一代且按钮存在：不做任何事。 */
        NONE,
        /** 需要（重新）注入返回按钮（新页面/新代/按钮缺失）。 */
        REINJECT,
        /** 页面关闭、未知页面或 Document 已失效：清理全部跟踪状态与按钮。 */
        CLEAR,
        /** 返回请求超时且仍停留在同一业务页面：恢复按钮文案，允许重试。 */
        RESTORE_RETURN,
        /** 点击被接受：发送 OpenMenuRequestPayload 并显示"返回中…"。 */
        CLICK_RETURN,
        /** 点击被忽略（防抖窗口内，或仍处于"返回中"等待）。 */
        CLICK_IGNORED
    }

    private Object screen;
    private Object document;
    private long generation = Long.MIN_VALUE;
    private BusinessPage page;
    private boolean returning;
    private long returningSince;
    private long lastReturnRequestAt;

    /**
     * 每 tick 观察一次。
     *
     * @param screen 当前 Screen（非 ApricityScreen 时为 null）
     * @param document 当前 Document（未就绪时为 null）
     * @param documentDisposed Document 已被释放
     * @param generation Document.getRefreshGeneration()
     * @param page 由 documentPath 识别出的业务页面（未知页面为 null）
     * @param buttonPresent 当前注入的按钮元素是否仍连接在 DOM 中
     * @param nowMs 当前毫秒时间戳
     */
    Action observe(Object screen, Object document, boolean documentDisposed,
                   long generation, BusinessPage page, boolean buttonPresent, long nowMs) {
        if (screen != this.screen || document != this.document) {
            reset();
            if (document == null || documentDisposed || page == null) {
                return Action.CLEAR;
            }
            this.screen = screen;
            this.document = document;
            this.generation = generation;
            this.page = page;
            return Action.REINJECT;
        }
        if (document == null || documentDisposed || page == null) {
            reset();
            return Action.CLEAR;
        }
        if (page != this.page || generation != this.generation || !buttonPresent) {
            this.page = page;
            this.generation = generation;
            return Action.REINJECT;
        }
        if (returning && nowMs - returningSince >= RETURNING_TIMEOUT_MS) {
            returning = false;
            returningSince = 0;
            return Action.RESTORE_RETURN;
        }
        return Action.NONE;
    }

    /** 返回按钮点击裁决：防抖 + "返回中"等待/恢复。 */
    Action onReturnClick(long nowMs) {
        if (returning) {
            if (nowMs - returningSince >= RETURNING_TIMEOUT_MS) {
                // 等待超时：视作旧请求已失败，恢复后按新点击处理。
                returning = false;
                returningSince = 0;
            } else {
                return Action.CLICK_IGNORED;
            }
        }
        if (nowMs - lastReturnRequestAt < CLICK_DEBOUNCE_MS) {
            return Action.CLICK_IGNORED;
        }
        lastReturnRequestAt = nowMs;
        returning = true;
        returningSince = nowMs;
        return Action.CLICK_RETURN;
    }

    /** 清空全部跟踪状态（页面切换、关闭、Document 失效时）。 */
    void reset() {
        screen = null;
        document = null;
        generation = Long.MIN_VALUE;
        page = null;
        returning = false;
        returningSince = 0;
        lastReturnRequestAt = 0;
    }
}
