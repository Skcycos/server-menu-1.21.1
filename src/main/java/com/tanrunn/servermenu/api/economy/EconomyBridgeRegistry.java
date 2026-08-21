package com.tanrunn.servermenu.api.economy;

/**
 * 经济桥接注册表（公共 API，不引用任何业务 Mod / LC 类）。
 *
 * <p>提供者由 LC 适配器在确认安装后注册；未注册 / 未安装时一律 fail closed：
 * 查询返回 unavailable，交易返回 {@link EconomyTransactionStatus#UNAVAILABLE}，
 * 绝不散布异常。<b>第三方 provider 的 RuntimeException / LinkageError（如可选依赖缺失）
 * 在本层全部转换为不可用/provider error，不让普通业务请求崩溃；但 OOM /
 * StackOverflowError 等真正 JVM 致命错误（{@link VirtualMachineError}）不吞。</b></p>
 *
 * <p>线程：本类自身不做线程断言，但所有交易方法应当只在服务端主线程调用；
 * 实际线程守卫由具体实现再校验一次（错误线程返回 WRONG_THREAD）。</p>
 */
public final class EconomyBridgeRegistry {

    private static final java.util.Map<String, EconomyProvider> PROVIDERS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private EconomyBridgeRegistry() {
    }

    // ---------------------------------------------------------------- registration

    /**
     * 注册提供者（重复 ID 覆盖；仅内部适配器/bootstrap 使用）。
     *
     * providerId() 只读取一次、校验一次、使用一次；若该元数据方法抛出 RuntimeException /
     * LinkageError，转换为受控 IllegalArgumentException（不递归进入错误日志）。
     */
    public static void register(EconomyProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("economy provider must not be null");
        }
        String id = safeId(provider);
        if (id == null || id.isBlank() || "?".equals(id)) {
            // safeId 已把元数据异常归一为 "?"：这里按注册失败处理（受控、不递归）。
            throw new IllegalArgumentException("economy provider id must not be blank or unreadable");
        }
        PROVIDERS.put(id, provider);
    }

    /** 注销提供者（供测试与卸载路径使用）。 */
    public static void unregister(String providerId) {
        if (providerId != null) {
            PROVIDERS.remove(providerId);
        }
    }

    public static java.util.Optional<EconomyProvider> provider(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(PROVIDERS.get(providerId));
    }

    /** 提供者是否注册且当前可用（LC 已安装 + 探针通过；第三方 provider 抛异常按不可用）。 */
    public static boolean isAvailable(String providerId) {
        EconomyProvider p = provider(providerId).orElse(null);
        return p != null && safeAvailable(p);
    }

    public static java.util.Set<String> ids() {
        return java.util.Set.copyOf(PROVIDERS.keySet());
    }

    public static int size() {
        return PROVIDERS.size();
    }

    // ---------------------------------------------------------------- operations

    /** 查询余额（fail closed：未注册/不可用返回 unavailable 快照；provider 异常转不可用）。 */
    public static EconomyBalance balance(String providerId, net.minecraft.server.level.ServerPlayer player) {
        if (player == null) {
            return EconomyBalance.unavailable(safeId(providerId), "");
        }
        EconomyProvider p = provider(providerId).orElse(null);
        if (p == null || !safeAvailable(p)) {
            return EconomyBalance.unavailable(safeId(providerId), safeChain(p));
        }
        try {
            return p.balance(player);
        } catch (VirtualMachineError e) {
            throw e; // OOM / StackOverflowError 等真正 JVM 致命错误不得吞。
        } catch (LinkageError | RuntimeException e) {
            logProviderBoundary(safeId(p), player, e);
            return EconomyBalance.unavailable(safeId(p), safeChain(p));
        }
    }

    /** 扣款（fail closed：未注册/不可用返回 UNAVAILABLE，不抛异常）。 */
    public static EconomyTransactionResult withdrawMinorUnits(String providerId,
                                                              net.minecraft.server.level.ServerPlayer player,
                                                              long amountMinorUnits,
                                                              String source, String reason, String requestId) {
        if (player == null) {
            return EconomyTransactionResult.failure(EconomyTransactionStatus.UNAVAILABLE,
                    "银行桥接当前不可用", safeId(providerId), requestId, 0, 0);
        }
        EconomyProvider p = provider(providerId).orElse(null);
        if (p == null || !safeAvailable(p)) {
            return EconomyTransactionResult.failure(EconomyTransactionStatus.UNAVAILABLE,
                    "银行桥接当前不可用", safeId(providerId), requestId, 0, 0);
        }
        return guard(p, () -> p.withdrawMinorUnits(player, amountMinorUnits,
                source, reason, requestId), player, requestId);
    }

    /** 入账（fail closed：未注册/不可用返回 UNAVAILABLE，不抛异常）。 */
    public static EconomyTransactionResult depositMinorUnits(String providerId,
                                                              net.minecraft.server.level.ServerPlayer player,
                                                              long amountMinorUnits,
                                                              String source, String reason, String requestId) {
        if (player == null) {
            return EconomyTransactionResult.failure(EconomyTransactionStatus.UNAVAILABLE,
                    "银行桥接当前不可用", safeId(providerId), requestId, 0, 0);
        }
        EconomyProvider p = provider(providerId).orElse(null);
        if (p == null || !safeAvailable(p)) {
            return EconomyTransactionResult.failure(EconomyTransactionStatus.UNAVAILABLE,
                    "银行桥接当前不可用", safeId(providerId), requestId, 0, 0);
        }
        return guard(p, () -> p.depositMinorUnits(player, amountMinorUnits,
                source, reason, requestId), player, requestId);
    }

    /** 格式化（fail closed：未注册/不可用返回原始数字字符串；provider 异常转原始数值）。 */
    public static String format(String providerId, long amountMinorUnits) {
        EconomyProvider p = provider(providerId).orElse(null);
        if (p == null || !safeAvailable(p)) {
            return String.valueOf(amountMinorUnits);
        }
        try {
            return p.formatMinorUnits(amountMinorUnits);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (LinkageError | RuntimeException e) {
            logProviderBoundary(safeId(p), null, e);
            return String.valueOf(amountMinorUnits);
        }
    }

    // ---------------------------------------------------------------- internals

    /** 安全读取 isAvailable：LinkageError/RuntimeException → unavailable，JVM 致命错误不吞。 */
    private static boolean safeAvailable(EconomyProvider p) {
        if (p == null) {
            return false;
        }
        try {
            return p.isAvailable();
        } catch (VirtualMachineError e) {
            throw e;
        } catch (LinkageError | RuntimeException e) {
            logProviderBoundary(safeId(p), null, e);
            return false;
        }
    }

    /**
     * 统一交易调用边界：异常一律转换为 PROVIDER_ERROR。OOM / StackOverflowError 等
     * JVM 致命错误（VirtualMachineError）不得吞；可选依赖缺失导致的 LinkageError
     * 转换为 provider error 而绝不让普通业务请求崩溃。
     */
    private static EconomyTransactionResult guard(EconomyProvider p, java.util.function.Supplier<EconomyTransactionResult> call,
                                                  net.minecraft.server.level.ServerPlayer player, String requestId) {
        try {
            return call.get();
        } catch (VirtualMachineError e) {
            throw e;
        } catch (LinkageError | RuntimeException e) {
            logProviderBoundary(safeId(p), player, e);
            return EconomyTransactionResult.failure(EconomyTransactionStatus.PROVIDER_ERROR,
                    "银行操作失败，请稍后再试", safeId(p), requestId, 0, safeBalance(p, player));
        }
    }

    /**
     * 只记录受限字段（providerId、玩家名、异常类型与截断消息），不记录无界输入。
     * providerIdText 必须预先解析为字符串：日志函数自身绝不调用任何 provider 方法，
     * 避免「错误日志再次触发 provider 元数据异常」的递归。
     */
    private static void logProviderBoundary(String providerIdText, net.minecraft.server.level.ServerPlayer player,
                                            Throwable t) {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EconomyBridgeRegistry.class);
        logger.error("[ServerMenu] economy provider boundary error: providerId={} player={} error={} detail={}",
                providerIdText == null ? "?" : providerIdText,
                player == null || player.getGameProfile() == null ? "?" : player.getGameProfile().getName(),
                t == null ? "?" : t.getClass().getName(),
                t == null || t.getMessage() == null ? "" : truncate(t.getMessage(), 120));
    }

    private static String truncate(String value, int max) {
        return value == null ? "" : (value.length() <= max ? value : value.substring(0, max));
    }

    /**
     * 安全读取 providerId：自身捕获 RuntimeException / LinkageError（可选依赖缺失），
     * 返回 "?"；VirtualMachineError / ThreadDeath 不得吞。绝不让元数据读取成为二次异常源。
     */
    private static String safeId(EconomyProvider p) {
        if (p == null) {
            return "?";
        }
        try {
            String id = p.providerId();
            return id == null || id.isBlank() ? "?" : id;
        } catch (VirtualMachineError e) {
            throw e;
        } catch (LinkageError | RuntimeException e) {
            return "?";
        }
    }

    private static String safeId(String providerId) {
        return providerId == null || providerId.isBlank() ? "?" : providerId;
    }

    /** 安全读取货币链：捕获 RuntimeException / LinkageError 返回 ""（不吞 VM 致命错误）。 */
    private static String safeChain(EconomyProvider p) {
        if (p == null) {
            return "";
        }
        try {
            String chain = p.currencyChain();
            return chain == null ? "" : chain;
        } catch (VirtualMachineError e) {
            throw e;
        } catch (LinkageError | RuntimeException e) {
            return "";
        }
    }

    private static long safeBalance(EconomyProvider p, net.minecraft.server.level.ServerPlayer player) {
        if (p == null || player == null || !safeAvailable(p)) {
            return 0;
        }
        try {
            return p.balance(player).minorUnits();
        } catch (VirtualMachineError e) {
            throw e;
        } catch (LinkageError | RuntimeException ignored) {
            // 只把可选依赖缺失 / 常规 provider 异常转为 0；
            // ThreadDeath / AssertionError / 其它 Error 原样传播，绝不吞。
            return 0;
        }
    }

    // ---------------------------------------------------------------- testing hooks

    /** 测试专用：清空全部注册。 */
    static void resetForTesting() {
        PROVIDERS.clear();
    }
}
