package com.tanrunn.servermenu.server.integration.lc;

import com.tanrunn.servermenu.server.SoulCardService;
import io.github.lightman314.lightmanscurrency.common.core.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

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

    public static void start(ServerLevel level, BlockPos atmPos, long copperAmount) {
        if (level == null || atmPos == null || copperAmount <= 0) {
            return;
        }

        double x = atmPos.getX() + 0.5D;
        double y = atmPos.getY() + 1.25D;
        double z = atmPos.getZ() + 0.5D;
        level.playSound(null, atmPos, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 0.8F, 1.0F);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z,
                50, 0.45D, 0.45D, 0.45D, 0.2D);

        ACTIVE.computeIfAbsent(level.getServer(), ignored -> new ArrayList<>())
                .add(new Animation(level, atmPos.immutable(), copperAmount));
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
        private int ticks;

        private Animation(ServerLevel level, BlockPos atmPos, long totalCopper) {
            this.level = level;
            this.atmPos = atmPos;
            this.totalCopper = totalCopper;
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
            ItemParticleOption copperParticle = new ItemParticleOption(
                    ParticleTypes.ITEM, new ItemStack(ModItems.COIN_COPPER.get()));
            int count = Math.toIntExact(copperThisSecond);
            if (count > 0) {
                level.sendParticles(copperParticle, x, y, z, count,
                        0.35D, 0.15D, 0.35D, 0.18D);
            }
            level.sendParticles(ParticleTypes.END_ROD, x, y, z,
                    4, 0.25D, 0.2D, 0.25D, 0.05D);
            level.playSound(null, atmPos, SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS, 0.45F, 0.85F + second * 0.02F);
        }
    }

}
