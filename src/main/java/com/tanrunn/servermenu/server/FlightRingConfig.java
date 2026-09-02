package com.tanrunn.servermenu.server;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** 飞行戒指的服务端配置。金额单位为 LC main 链铜币。 */
public final class FlightRingConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.LongValue CHARGE_COST_COPPER = BUILDER
            .comment("Shift+右键补满当前飞行戒指时，每缺少1点耐久所需的LC main链铜币数量")
            .defineInRange("chargeCostCopper", 1L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_WORLDS = BUILDER
            .comment("允许飞行戒指工作的世界/维度ID；例如 minecraft:overworld。空列表表示全部禁止")
            .defineListAllowEmpty("allowedWorlds", List.of("minecraft:overworld"),
                    value -> value instanceof String text && ResourceLocation.tryParse(text) != null);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean isWorldAllowed(Level level) {
        if (level == null) {
            return false;
        }
        String dimensionId = level.dimension().location().toString();
        return isWorldAllowed(dimensionId, ALLOWED_WORLDS.get());
    }

    static boolean isWorldAllowed(String dimensionId, List<? extends String> worlds) {
        return dimensionId != null && worlds != null && worlds.stream().anyMatch(dimensionId::equals);
    }

    private FlightRingConfig() {
    }
}
