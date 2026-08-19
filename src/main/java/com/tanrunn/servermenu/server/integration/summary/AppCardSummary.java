package com.tanrunn.servermenu.server.integration.summary;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用卡片摘要（server-menu 自己的不可变 DTO）。
 *
 * <p>约束：最多 {@link #MAX_LINES} 行；每行最多 {@link #MAX_LINE_LENGTH} 个
 * Java 字符；null 行剔除；空行剔除；CR/LF/TAB 等控制字符规范化为空格；
 * 超长行截断。集合使用 {@link List#copyOf} 防御性复制。
 * 本类型不保存任何业务 record、AUI、客户端或网络对象。</p>
 */
public record AppCardSummary(List<String> lines) {

    /** 摘要最大行数（与网络层 AppStatus.MAX_SUMMARY_LINES 保持一致）。 */
    public static final int MAX_LINES = 3;
    /** 每行最大 Java 字符数（与网络层 AppStatus.MAX_SUMMARY_LINE_LENGTH 保持一致）。 */
    public static final int MAX_LINE_LENGTH = 96;

    public AppCardSummary {
        List<String> cleaned = new ArrayList<>(lines == null ? 0 : Math.min(lines.size(), MAX_LINES));
        if (lines != null) {
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                String normalized = normalizeLine(line);
                if (normalized.isEmpty()) {
                    continue;
                }
                cleaned.add(normalized);
                if (cleaned.size() >= MAX_LINES) {
                    break;
                }
            }
        }
        lines = List.copyOf(cleaned);
    }

    /** 空摘要。 */
    public static AppCardSummary empty() {
        return new AppCardSummary(List.of());
    }

    /** 是否没有任何行。 */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * 把控制字符（CR/LF/TAB 等）规范化为空格并截断到 {@link #MAX_LINE_LENGTH}。
     * 纯逻辑，可单测。
     */
    static String normalizeLine(String line) {
        if (line == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            sb.append(c < 0x20 || c == 0x7F ? ' ' : c);
        }
        String result = sb.toString();
        return result.length() > MAX_LINE_LENGTH ? result.substring(0, MAX_LINE_LENGTH) : result;
    }
}
