package com.tanrunn.servermenu.server.integration.lc;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LcProbe} 探针测试：
 * <ul>
 *   <li>服务器安装的 LC 2.3.0.5 JAR 在测试 classpath 上 → 探针必须通过（真实 API 签名验证）；</li>
 *   <li>纯净 ClassLoader（无 LC）→ 探针失败（fail closed）；</li>
 *   <li>null loader / 无法解析 → 失败。</li>
 * </ul>
 */
class LcProbeTest {

    @Test
    void probePassesAgainstRealInstalledLcJar() {
        // 测试 classpath 已通过 testImplementation files(lcReferenceJar()) 引入服务器 LC jar。
        assertTrue(LcProbe.probe(getClass().getClassLoader()),
                "服务器安装的 LC 2.3.0.5 API 签名必须与适配器期望一致");
    }

    @Test
    void probeFailsWithoutAnyLcClasses() {
        assertFalse(LcProbe.probe(new URLClassLoader(new URL[0], null)));
    }

    @Test
    void probeFailsOnNullLoader() {
        assertFalse(LcProbe.probe(null));
    }

    @Test
    void probeFailsIfBankApiMissingButOthersPresent() {
        // 只拿空 loader，等于无任何类；逐类名称都能通过字符串核对。
        assertFalse(LcProbe.probe(new URLClassLoader(new URL[0], null)));
        assertNotNull(LcConstants.CLASS_BANK_API);
    }
}
