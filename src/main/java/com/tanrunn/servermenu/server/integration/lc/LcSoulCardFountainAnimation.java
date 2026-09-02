package com.tanrunn.servermenu.server.integration.lc;

import com.tanrunn.servermenu.common.network.ServerMenuNetwork.SoulCardAnimationPayload;
import com.tanrunn.servermenu.server.SoulCardService;
import io.github.lightman314.lightmanscurrency.common.core.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** LC 专属的铜币喷泉动画；只在 LC 桥接成功后由反射注册。 */
public final class LcSoulCardFountainAnimation {
    private static final int ANIMATION_SECONDS = 10;
    private static final int TICKS_PER_SECOND = 20;
    private static final Map<MinecraftServer, List<Animation>> ACTIVE = new IdentityHashMap<>();

    private LcSoulCardFountainAnimation() {
    }

    public static boolean start(ServerPlayer player, ServerLevel level, BlockPos atmPos,
                                long copperAmount, ItemStack displayItem) {
        if (player == null || level == null || atmPos == null || copperAmount <= 0
                || displayItem == null || displayItem.isEmpty() || player.serverLevel() != level) {
            return false;
        }

        ItemStack copperPrototype = new ItemStack(ModItems.COIN_COPPER.get());
        if (copperPrototype.isEmpty()) {
            return false;
        }

        double x = atmPos.getX() + 0.5D;
        double y = atmPos.getY() + 1.25D;
        double z = atmPos.getZ() + 0.5D;

        // 复用原版 GameRenderer 的物品激活动画，但传递实际兑换的社保卡，
        // 避免实体事件 35 在客户端把物品硬编码为不死图腾。
        PacketDistributor.sendToPlayer(player,
                new SoulCardAnimationPayload(displayItem.copyWithCount(1)));
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z,
                30, 0.45D, 0.45D, 0.45D, 0.2D);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z,
                16, 0.2D, 0.15D, 0.2D, 0.08D);
        level.playSound(null, atmPos, SoundEvents.TOTEM_USE,
                SoundSource.BLOCKS, 0.8F, 1.0F);

        ACTIVE.computeIfAbsent(level.getServer(), ignored -> new ArrayList<>())
                .add(new Animation(level, atmPos.immutable(), copperAmount, copperPrototype));
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        List<Animation> animations = ACTIVE.get(event.getServer());
        if (animations == null) {
            return;
        }

        Iterator<Animation> iterator = animations.iterator();
        while (iterator.hasNext()) {
            Animation animation = iterator.next();
            if (animation.tick()) {
                iterator.remove();
            }
        }
        if (animations.isEmpty()) {
            ACTIVE.remove(event.getServer());
        }
    }

    private static final class Animation {
        private final ServerLevel level;
        private final BlockPos atmPos;
        private final long totalCopper;
        private final ItemStack copperPrototype;
        private int ticks;

        private Animation(ServerLevel level, BlockPos atmPos, long totalCopper, ItemStack copperPrototype) {
            this.level = level;
            this.atmPos = atmPos;
            this.totalCopper = totalCopper;
            this.copperPrototype = copperPrototype;
        }

        private boolean tick() {
            ticks++;
            if (ticks % TICKS_PER_SECOND != 0) {
                return false;
            }

            int second = ticks / TICKS_PER_SECOND;
            long copperThisSecond = SoulCardService.copperForSecond(totalCopper, second);
            emit(copperThisSecond, second);
            return second >= ANIMATION_SECONDS;
        }

        private void emit(long copperThisSecond, int second) {
            double x = atmPos.getX() + 0.5D;
            double y = atmPos.getY() + 1.15D;
            double z = atmPos.getZ() + 0.5D;
            spawnCopperEntities(copperThisSecond, x, y, z, second);
            level.sendParticles(ParticleTypes.END_ROD, x, y, z,
                    4, 0.25D, 0.2D, 0.25D, 0.05D);
            level.playSound(null, atmPos, SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS, 0.45F, 0.85F + second * 0.02F);
        }

        private void spawnCopperEntities(long amount, double x, double y, double z, int second) {
            RandomSource random = level.getRandom();
            for (long coin = 0; coin < amount; coin++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double spawnX = x + (random.nextDouble() - 0.5D) * 0.18D;
                double spawnY = y + random.nextDouble() * 0.12D;
                double spawnZ = z + (random.nextDouble() - 0.5D) * 0.18D;
                ItemEntity entity = new ItemEntity(level, spawnX, spawnY, spawnZ,
                        new ItemStack(copperPrototype.getItem()));
                double horizontalSpeed = 0.10D + random.nextDouble() * 0.28D;
                entity.setDeltaMovement(Math.cos(angle) * horizontalSpeed,
                        0.58D + random.nextDouble() * 0.24D,
                        Math.sin(angle) * horizontalSpeed);
                entity.setNoPickUpDelay();
                level.addFreshEntity(entity);
            }
        }
    }

}
