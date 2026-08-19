package com.tanrunn.servermenu.server.integration.summary;

import com.tanrunn.servermenu.common.menu.MenuApp;

/**
 * 摘要探测测试用的假 API 类（纯逻辑，不依赖 Minecraft/业务 Mod）。
 *
 * <p>用 {@link FakePlayer} 充当 ServerPlayer 的参数类型占位；
 * {@link GoodSummary} 充当业务摘要 record 占位。测试环境没有 Minecraft 类，
 * 无法在测试源码中实现 {@link AppSummaryProvider}（其签名引用 ServerPlayer），
 * 因此正向探测分支使用真实摘要适配器类，接口/构造器分支分别用
 * 假适配器与 {@code hasAccessibleNoArgConstructor} 覆盖。</p>
 */
final class ProbeSummaryTestApis {

    private ProbeSummaryTestApis() {
    }

    /** 参数类型占位（对应 ServerPlayer）。 */
    static final class FakePlayer {
    }

    /** 返回类型占位（对应业务摘要 record）。 */
    record GoodSummary(String text) {
    }

    /** 形状正确：公开静态、单参数、返回 GoodSummary。 */
    static final class GoodApi {
        public static GoodSummary summary(FakePlayer ignored) {
            return new GoodSummary("ok");
        }
    }

    /** 方法名不存在。 */
    static final class NoMethodApi {
        public static GoodSummary other(FakePlayer ignored) {
            return new GoodSummary("x");
        }
    }

    /** 方法非 public。 */
    static final class NonPublicApi {
        static GoodSummary summary(FakePlayer ignored) {
            return new GoodSummary("x");
        }
    }

    /** 方法非 static。 */
    static final class NonStaticApi {
        public GoodSummary summary(FakePlayer ignored) {
            return new GoodSummary("x");
        }
    }

    /** 参数数量错误。 */
    static final class WrongArityApi {
        public static GoodSummary summary(FakePlayer first, FakePlayer second) {
            return new GoodSummary("x");
        }
    }

    /** 参数类型错误。 */
    static final class WrongParamTypeApi {
        public static GoodSummary summary(String ignored) {
            return new GoodSummary("x");
        }
    }

    /** 返回类型错误（String 而非 GoodSummary）。 */
    static final class WrongReturnApi {
        public static String summary(FakePlayer ignored) {
            return "x";
        }
    }

    /** 未实现 AppSummaryProvider 的普通类。 */
    static final class NotImplementingAdapter {
    }

    /** 无公开构造器的普通类（构造器分支用）。 */
    static final class PrivateCtorOnly {
        private PrivateCtorOnly() {
        }
    }

    /** 有公开构造器的普通类（构造器分支用；public 使默认构造器公开）。 */
    public static final class PublicCtorOnly {
    }
}
