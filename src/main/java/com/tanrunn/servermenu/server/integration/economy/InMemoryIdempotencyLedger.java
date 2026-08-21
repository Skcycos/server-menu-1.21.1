package com.tanrunn.servermenu.server.integration.economy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 有界内存幂等账本（纯逻辑，可在单个提供者实例内共享）。
 *
 * <p><b>重启边界必须如实声明</b>：本账本只存在于内存，服务器重启后清空。
 * 在账本保留期内（默认 2048 条，LRU 淘汰最早条目），同 requestId 重放/冲突判定
 * 生效；超出保留期后，重放保护会失效——这是有界内存实现的明确边界，不宣称
 * 永久 exactly-once。LC 适配器在上面叠加金额转换与补偿校验，任何失败都不会
 * 伪装成成功。</p>
 */
public final class InMemoryIdempotencyLedger implements BankOperationCore.IdempotencyLedger {

    public static final int DEFAULT_MAX_ENTRIES = 2048;

    private final int maxEntries;
    private final LinkedHashMap<String, BankOperationCore.Record> entries = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BankOperationCore.Record> eldest) {
            return size() > maxEntries;
        }
    };

    public InMemoryIdempotencyLedger() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public InMemoryIdempotencyLedger(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
    }

    @Override
    public synchronized BankOperationCore.Record find(String playerKey, String requestId) {
        if (playerKey == null || requestId == null) {
            return null;
        }
        return entries.get(key(playerKey, requestId));
    }

    @Override
    public synchronized void remember(String playerKey, String requestId, BankOperationCore.Record record) {
        if (playerKey == null || requestId == null || record == null) {
            return;
        }
        entries.put(key(playerKey, requestId), record);
    }

    public synchronized int size() {
        return entries.size();
    }

    public int maxEntries() {
        return maxEntries;
    }

    private static String key(String playerKey, String requestId) {
        return playerKey + "|" + requestId;
    }
}
