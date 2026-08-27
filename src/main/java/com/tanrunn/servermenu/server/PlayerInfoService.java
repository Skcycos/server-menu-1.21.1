package com.tanrunn.servermenu.server;

import com.tanrunn.servermenu.api.economy.EconomyBalance;
import com.tanrunn.servermenu.api.economy.EconomyBridgeRegistry;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.PlayerInfoPayload;
import com.tanrunn.servermenu.server.integration.lc.LcConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.block.Block;

/**
 * 个人信息页的服务端数据源。
 *
 * <p>所有数值都从服务端在线玩家对象或原版统计系统读取，客户端只负责展示快照，
 * 不接受客户端上传的等级、位置或统计数据。</p>
 */
public final class PlayerInfoService {

    private PlayerInfoService() {
    }

    /** 在服务端主线程构建一个个人信息快照。 */
    public static PlayerInfoPayload snapshot(ServerPlayer player) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }

        ServerStatsCounter stats = player.getStats();
        EconomyBalance bankBalance = EconomyBridgeRegistry.balance(LcConstants.PROVIDER_ID, player);
        long playTimeTicks = customStat(stats, Stats.PLAY_TIME);
        long worldDays = Math.max(0L, player.serverLevel().getDayTime() / 24_000L);
        var position = player.blockPosition();

        return new PlayerInfoPayload(
                player.getGameProfile().getName(),
                player.getUUID().toString(),
                player.serverLevel().dimension().location().toString(),
                player.experienceLevel,
                player.totalExperience,
                Math.round(player.experienceProgress * PlayerInfoPayload.MAX_EXPERIENCE_PROGRESS_PERMILLE),
                Math.round(Math.max(0.0F, player.getHealth()) * 10.0F),
                Math.round(Math.max(0.0F, player.getMaxHealth()) * 10.0F),
                player.getFoodData().getFoodLevel(),
                Math.round(Math.max(0.0F, player.getFoodData().getSaturationLevel()) * 10.0F),
                bankBalance != null && bankBalance.available(),
                bankBalance != null && bankBalance.available() && bankBalance.quarantined(),
                bankBalance == null ? 0L : bankBalance.minorUnits(),
                bankBalance != null && bankBalance.available() ? LcConstants.PROVIDER_DISPLAY_NAME : "",
                playTimeTicks,
                worldDays,
                customStat(stats, Stats.DEATHS),
                customStat(stats, Stats.MOB_KILLS),
                customStat(stats, Stats.PLAYER_KILLS),
                customStat(stats, Stats.JUMP),
                customStat(stats, Stats.WALK_ONE_CM),
                blocksMined(stats),
                position.getX(),
                position.getY(),
                position.getZ());
    }

    private static int customStat(ServerStatsCounter stats, net.minecraft.resources.ResourceLocation id) {
        return Math.max(0, stats.getValue(Stats.CUSTOM, id));
    }

    /** 原版没有直接提供“挖掘方块总数”，这里按所有方块统计项求和。 */
    private static long blocksMined(ServerStatsCounter stats) {
        long total = 0L;
        for (Block block : BuiltInRegistries.BLOCK) {
            total += Math.max(0, stats.getValue(Stats.BLOCK_MINED, block));
        }
        return total;
    }
}
