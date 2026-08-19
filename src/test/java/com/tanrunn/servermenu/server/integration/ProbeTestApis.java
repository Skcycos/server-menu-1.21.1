package com.tanrunn.servermenu.server.integration;

/**
 * 兼容性探测测试用的假 API 类（纯逻辑，不依赖 Minecraft/业务 Mod）。
 */
final class ProbeTestApis {

    private ProbeTestApis() {
    }

    /** 形状正确：公开静态、返回 boolean、单参数。 */
    static final class GoodApi {
        public static boolean openPanel(String ignored) {
            return true;
        }
    }

    /** 方法名不存在。 */
    static final class NoMethodApi {
        public static boolean other(String ignored) {
            return true;
        }
    }

    /** 返回类型错误（String 而非 boolean）。 */
    static final class WrongReturnApi {
        public static String openPanel(String ignored) {
            return "";
        }
    }

    /** 方法非 static。 */
    static final class NonStaticApi {
        public boolean openPanel(String ignored) {
            return true;
        }
    }

    /** 方法非 public。 */
    static final class NonPublicApi {
        static boolean openPanel(String ignored) {
            return true;
        }
    }

    /** 参数数量不符。 */
    static final class WrongArityApi {
        public static boolean openPanel(String first, String second) {
            return true;
        }
    }

    /** 参数类型不符。 */
    static final class WrongParamTypeApi {
        public static boolean openPanel(Integer ignored) {
            return true;
        }
    }
}
