package com.tanrunn.servermenu.server.integration.summary;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 极简 .class 文件方法表解析器（纯 Java，无第三方依赖）。
 *
 * <p>用于在无 Minecraft 的纯 JUnit 环境验证真实业务 JAR 中 summary 方法的
 * 存在性、访问标志与完整描述符（参数与返回类型的精确签名）。</p>
 */
final class ClassFileMethodScanner {

    private ClassFileMethodScanner() {
    }

    /** 方法信息（access 为 Class 文件 access_flags）。 */
    record MethodInfo(int access, String name, String descriptor) {
        boolean isPublic() {
            return (access & 0x0001) != 0;
        }

        boolean isStatic() {
            return (access & 0x0008) != 0;
        }
    }

    /**
     * 解析 classpath 上某个类的完整方法表。
     *
     * @param className 二进制名（如 com.tanrunn.buildshop.api.BuildingShopApi）
     */
    static List<MethodInfo> methodsOf(String className) throws IOException {
        String resource = className.replace('.', '/') + ".class";
        try (InputStream in = ClassFileMethodScanner.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("classpath 上找不到 " + resource);
            }
            return parse(in);
        }
    }

    static List<MethodInfo> parse(InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(in);
        int magic = data.readInt();
        if (magic != 0xCAFEBABE) {
            throw new IOException("不是合法的 class 文件（magic 不符）");
        }
        data.readUnsignedShort(); // minor version
        data.readUnsignedShort(); // major version

        int cpCount = data.readUnsignedShort();
        String[] utf8 = new String[cpCount];
        for (int i = 1; i < cpCount; i++) {
            int tag = data.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = data.readUTF();
                case 3, 4 -> data.skipBytes(4);
                case 5, 6 -> {
                    data.skipBytes(8);
                    i++; // long/double 占两个槽位
                }
                case 7, 8, 16, 19, 20 -> data.skipBytes(2);
                case 9, 10, 11, 12, 17, 18 -> data.skipBytes(4);
                case 15 -> data.skipBytes(3);
                default -> throw new IOException("未知常量池标签 " + tag);
            }
        }

        data.readUnsignedShort(); // access_flags
        data.readUnsignedShort(); // this_class
        data.readUnsignedShort(); // super_class
        int interfaceCount = data.readUnsignedShort();
        data.skipBytes(interfaceCount * 2);

        int fieldCount = data.readUnsignedShort();
        for (int i = 0; i < fieldCount; i++) {
            data.readUnsignedShort(); // access_flags
            data.readUnsignedShort(); // name_index
            data.readUnsignedShort(); // descriptor_index
            skipAttributes(data);
        }

        int methodCount = data.readUnsignedShort();
        List<MethodInfo> methods = new ArrayList<>(methodCount);
        for (int i = 0; i < methodCount; i++) {
            int access = data.readUnsignedShort();
            int nameIndex = data.readUnsignedShort();
            int descriptorIndex = data.readUnsignedShort();
            methods.add(new MethodInfo(access, utf8[nameIndex], utf8[descriptorIndex]));
            skipAttributes(data);
        }
        return methods;
    }

    private static void skipAttributes(DataInputStream data) throws IOException {
        int count = data.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            data.readUnsignedShort(); // attribute_name_index
            int length = data.readInt();
            data.skipBytes(length);
        }
    }
}
