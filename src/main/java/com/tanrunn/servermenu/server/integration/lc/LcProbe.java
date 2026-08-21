package com.tanrunn.servermenu.server.integration.lc;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * LC API 兼容性探针（纯反射，不引用任何 LC Class 字面量）。
 *
 * <p>只按 {@link LcConstants} 中的类名/方法名/返回类型<b>字符串</b>校验 LC 2.3.0.5
 * 的实际签名（与 AppLauncherRegistry 的描述符风格一致）。任何缺失/签名变化都会
 * 让探针返回 false → bootstrap fail closed（LC 未安装或版本不兼容时整桥不可用）。</p>
 *
 * <p>{@code probe} 可传入任意 {@link ClassLoader}：在未安装 LC 的纯净环境返回 false，
 * 在测试中把服务器安装的 LC JAR 放进 classpath 时返回 true（真实 API 签名验证）。</p>
 */
public final class LcProbe {

    private LcProbe() {
    }

    /**
     * 校验 LC API 形状是否与适配器期望一致。
     *
     * @return true 表示 LC 类与关键方法签名全部匹配
     */
    public static boolean probe(ClassLoader loader) {
        if (loader == null) {
            return false;
        }
        try {
            Class<?> bankApi = Class.forName(LcConstants.CLASS_BANK_API, false, loader);
            if (!hasStaticMethod(bankApi, "getApi", "io.github.lightman314.lightmanscurrency.api.money.bank.BankAPI", new String[0])) {
                return false;
            }
            if (!hasInstanceMethod(bankApi, "BankDepositFromServer", "boolean",
                    new String[]{LcConstants.CLASS_IBANK_ACCOUNT, LcConstants.CLASS_MONEY_VALUE, "boolean"})) {
                return false;
            }
            if (!hasInstanceMethod(bankApi, "BankWithdrawFromServer", LcConstants.CLASS_PAIR,
                    new String[]{LcConstants.CLASS_IBANK_ACCOUNT, LcConstants.CLASS_MONEY_VALUE, "boolean"})) {
                return false;
            }

            Class<?> playerRef = Class.forName(LcConstants.CLASS_PLAYER_BANK_REFERENCE, false, loader);
            if (!hasStaticMethod(playerRef, "of", "io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference",
                    new String[]{"net.minecraft.world.entity.player.Player"})) {
                return false;
            }

            Class<?> iBank = Class.forName(LcConstants.CLASS_IBANK_ACCOUNT, false, loader);
            if (!hasInstanceMethod(iBank, "getMoneyStorage", LcConstants.CLASS_MONEY_STORAGE, new String[0])) {
                return false;
            }
            if (!hasInstanceMethod(iBank, "depositMoney", "void", new String[]{LcConstants.CLASS_MONEY_VALUE})) {
                return false;
            }
            if (!hasInstanceMethod(iBank, "withdrawMoney", LcConstants.CLASS_MONEY_VALUE,
                    new String[]{LcConstants.CLASS_MONEY_VALUE})) {
                return false;
            }

            Class<?> moneyStorage = Class.forName(LcConstants.CLASS_MONEY_STORAGE, false, loader);
            if (!hasInstanceMethod(moneyStorage, "valueOf", LcConstants.CLASS_MONEY_VALUE,
                    new String[]{"java.lang.String"})) {
                return false;
            }

            Class<?> moneyValue = Class.forName(LcConstants.CLASS_MONEY_VALUE, false, loader);
            if (!hasInstanceMethod(moneyValue, "getCoreValue", "long", new String[0])) {
                return false;
            }

            Class<?> coinValue = Class.forName(LcConstants.CLASS_COIN_VALUE, false, loader);
            if (!hasStaticMethod(coinValue, "fromNumber", LcConstants.CLASS_MONEY_VALUE,
                    new String[]{"java.lang.String", "long"})) {
                return false;
            }

            Class<?> quarantine = Class.forName(LcConstants.CLASS_QUARANTINE_API, false, loader);
            if (!hasStaticMethod(quarantine, "IsDimensionQuarantined", "boolean",
                    new String[]{"net.minecraft.world.entity.Entity"})) {
                return false;
            }
            return true;
        } catch (ClassNotFoundException | LinkageError | RuntimeException e) {
            // RuntimeException 覆盖 SecurityException / TypeNotPresentException 等。
            return false;
        }
    }

    /** 查找公开方法（静态与否由参数决定）并校验名字/返回类型/参数类型。 */
    static boolean hasMethod(Class<?> clazz, String methodName, boolean isStatic,
                             String returnTypeName, String[] parameterTypeNames) {
        if (clazz == null || methodName == null || returnTypeName == null) {
            return false;
        }
        boolean found = false;
        for (Method candidate : clazz.getMethods()) {
            if (!candidate.getName().equals(methodName)) {
                continue;
            }
            if (Modifier.isStatic(candidate.getModifiers()) != isStatic) {
                continue;
            }
            if (!returnTypeName.equals(candidate.getReturnType().getName())) {
                continue;
            }
            Class<?>[] params = candidate.getParameterTypes();
            if (params.length != parameterTypeNames.length) {
                continue;
            }
            boolean match = true;
            for (int i = 0; i < params.length; i++) {
                if (!params[i].getName().equals(parameterTypeNames[i])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                found = true;
                break;
            }
        }
        return found;
    }

    static boolean hasStaticMethod(Class<?> clazz, String name, String returnTypeName, String[] params) {
        return hasMethod(clazz, name, true, returnTypeName, params);
    }

    static boolean hasInstanceMethod(Class<?> clazz, String name, String returnTypeName, String[] params) {
        return hasMethod(clazz, name, false, returnTypeName, params);
    }
}
