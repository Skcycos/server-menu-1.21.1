package com.tanrunn.servermenu.server.integration.summary;

import com.tanrunn.servermenu.common.menu.MenuApp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实业务 JAR 摘要 API 结构验证。
 *
 * <p>三个业务 JAR 通过 testImplementation 进入测试 classpath（由
 * {@code businessApiJars()} 保证存在）。纯 JUnit 环境没有 Minecraft 类，
 * {@code Class.getMethods()} 会因无法解析 ServerPlayer 参数类型而失败，
 * 因此这里直接解析 .class 文件的常量池与方法表（{@link ClassFileMethodScanner}），
 * 校验 summary 方法存在、public static、参数与返回类型的<b>完整描述符</b>
 * 精确匹配描述符。</p>
 */
class RealBusinessJarSummaryProbeTest {

    @Test
    void everyRealBusinessApiHasPublicStaticSummaryWithExactDescriptor() throws Exception {
        for (MenuApp app : MenuApp.ALL) {
            SummaryDescriptor descriptor = AppSummaryRegistry.descriptorFor(app);
            assertNotNull(descriptor, "缺少摘要描述符：" + app.id());

            String expectedDescriptor = "(Lnet/minecraft/server/level/ServerPlayer;)L"
                    + descriptor.returnTypeName().replace('.', '/') + ";";

            ClassFileMethodScanner.MethodInfo method = ClassFileMethodScanner.methodsOf(
                    descriptor.apiClassName()).stream()
                    .filter(m -> "summary".equals(m.name()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(method, "业务 API 缺少 summary 方法：" + descriptor.apiClassName());
            assertTrue(method.isPublic(), "summary 必须 public：" + descriptor.apiClassName());
            assertTrue(method.isStatic(), "summary 必须 static：" + descriptor.apiClassName());
            assertEquals(expectedDescriptor, method.descriptor(),
                    "summary 方法描述符不匹配：" + descriptor.apiClassName());
        }
    }

    @Test
    void everyRealSummaryAdapterMatchesDescriptorAndAppIdentity() throws Exception {
        ClassLoader loader = RealBusinessJarSummaryProbeTest.class.getClassLoader();
        for (MenuApp app : MenuApp.ALL) {
            SummaryDescriptor descriptor = AppSummaryRegistry.descriptorFor(app);
            Class<?> adapterClass = Class.forName(descriptor.adapterClassName(), false, loader);
            assertTrue(AppSummaryProvider.class.isAssignableFrom(adapterClass),
                    "摘要适配器未实现 AppSummaryProvider：" + adapterClass.getName());
            assertTrue(AppSummaryRegistry.hasAccessibleNoArgConstructor(adapterClass),
                    "摘要适配器缺少公开无参构造器：" + adapterClass.getName());

            AppSummaryProvider provider = (AppSummaryProvider) adapterClass.getDeclaredConstructor().newInstance();
            assertEquals(app, provider.app(), "摘要适配器 app() 与白名单不一致：" + adapterClass.getName());
        }
    }
}
