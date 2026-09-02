package com.tanrunn.servermenu.server.registry;

import com.mojang.serialization.Codec;
import com.tanrunn.servermenu.ServerMenuMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** server-menu 物品使用的数据组件。 */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ServerMenuMod.MODID);

    /** 灵魂社保卡当前累计的灵魂数，持久化到卡片本身。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SOUL_COUNT =
            COMPONENTS.registerComponentType("soul_count", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.INT));

    private ModDataComponents() {
    }
}
