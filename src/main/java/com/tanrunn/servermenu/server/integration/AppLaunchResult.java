package com.tanrunn.servermenu.server.integration;

/**
 * 应用启动结果（不可变）。
 *
 * <p>成功时不携带用户消息（业务 S2C 包随后自然打开对应页面，Pad 状态栏保持不变）；
 * 失败时 userMessage 为面向玩家的安全提示（不含内部异常文本），error 表示是否按错误样式显示。</p>
 */
public record AppLaunchResult(boolean success, String userMessage, boolean error) {

    /** 启动成功：客户端 Pad 将被业务页面自然替换。 */
    public static AppLaunchResult ok() {
        return new AppLaunchResult(true, "", false);
    }

    /** 启动失败：返回面向玩家的安全提示。 */
    public static AppLaunchResult failure(String userMessage) {
        return new AppLaunchResult(false, userMessage == null ? "" : userMessage, true);
    }
}
