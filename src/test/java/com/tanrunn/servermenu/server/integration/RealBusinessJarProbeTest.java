package com.tanrunn.servermenu.server.integration;

import com.tanrunn.servermenu.common.menu.MenuApp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实业务 JAR 结构验证（类存在性 + 适配器结构）。
 *
 * <p>三个业务 JAR 通过 testImplementation 进入测试 classpath（由
 * {@code businessApiJars()} 保证存在）。方法签名级验证（ServerPlayer 参数）
 * 在无 Minecraft 的单测环境无法解析，由三 Mod 同装运行验证（启动摘要日志）覆盖。</p>
 */
class RealBusinessJarProbeTest {

    @Test
    void everyRealBusinessApiClassExists() throws Exception {
        for (MenuApp app : MenuApp.ALL) {
            LauncherDescriptor descriptor = AppLauncherRegistry.descriptorFor(app);
            assertNotNull(descriptor, "缺少描述符：" + app.id());
            // initialize=false：只加载不初始化，避免触发业务类依赖链接。
            Class<?> apiClass = Class.forName(descriptor.apiClassName(), false,
                    RealBusinessJarProbeTest.class.getClassLoader());
            assertNotNull(apiClass, "业务 API 类不存在：" + descriptor.apiClassName());
        }
    }

    @Test
    void everyRealAdapterMatchesDescriptorAndAppIdentity() throws Exception {
        ClassLoader loader = RealBusinessJarProbeTest.class.getClassLoader();
        for (MenuApp app : MenuApp.ALL) {
            LauncherDescriptor descriptor = AppLauncherRegistry.descriptorFor(app);
            Class<?> adapterClass = Class.forName(descriptor.adapterClassName(), false, loader);
            assertTrue(AppLauncher.class.isAssignableFrom(adapterClass),
                    "适配器未实现 AppLauncher：" + adapterClass.getName());
            assertTrue(AppLauncherRegistry.hasAccessibleNoArgConstructor(adapterClass),
                    "适配器缺少可访问无参构造器：" + adapterClass.getName());

            Object launcher = adapterClass.getDeclaredConstructor().newInstance();
            assertTrue(AppLauncherRegistry.verifyAppIdentity(launcher, app),
                    "适配器 app() 与描述符不一致：" + adapterClass.getName());
        }
    }
}
