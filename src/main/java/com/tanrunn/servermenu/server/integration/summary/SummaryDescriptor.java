package com.tanrunn.servermenu.server.integration.summary;

/**
 * 摘要能力描述（不可变）。
 *
 * <p>只保存类名/方法名/返回类型<b>全限定名字符串</b>，不保存任何业务 API 或
 * 摘要 record 的 Class 字面量；公共注册表（{@link AppSummaryRegistry}）只持有
 * 本描述符，业务类字面量只允许出现在三个独立摘要适配器中。</p>
 *
 * @param adapterClassName 摘要适配器类全限定名（server-menu 自身）
 * @param apiClassName     业务公开 API 类全限定名
 * @param methodName       业务 API 公开静态方法名（summary）
 * @param returnTypeName   期望返回类型的全限定名字符串（精确匹配）
 */
public record SummaryDescriptor(
        String adapterClassName,
        String apiClassName,
        String methodName,
        String returnTypeName) {
}
