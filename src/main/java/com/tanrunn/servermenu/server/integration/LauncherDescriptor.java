package com.tanrunn.servermenu.server.integration;

/**
 * 内置应用的兼容性描述（不可变）。
 *
 * <p>只保存类名与方法名字符串，不保存任何业务 API 的 Class 字面量；
 * {@code returnType} 只使用 {@code boolean.class}/{@code void.class} 等安全字面量。</p>
 *
 * @param adapterClassName 适配器类全限定名（server-menu 自身）
 * @param apiClassName     业务公开 API 类全限定名
 * @param methodName       业务 API 公开静态方法名
 * @param returnType       期望的返回类型（精确匹配）
 */
record LauncherDescriptor(
        String adapterClassName,
        String apiClassName,
        String methodName,
        Class<?> returnType
) {
}
