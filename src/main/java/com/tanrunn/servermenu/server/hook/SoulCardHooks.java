package com.tanrunn.servermenu.server.hook;

import com.tanrunn.servermenu.server.SoulCardService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** 灵魂社保卡击杀计数与 ATM 交互事件。 */
public final class SoulCardHooks {
    private static final String ATM_PATH = "atm";

    private SoulCardHooks() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        SoulCardService.onLivingDeath(event);
    }

    @SubscribeEvent
    public static void onRightClickAtm(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isCrouching()
                || !isAtm(event.getLevel(), event.getPos())
                || !SoulCardService.hasConvertibleCard(event.getItemStack())) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof ServerPlayer player) {
            SoulCardService.exchangeAtAtm(player, player.serverLevel(), event.getPos(), event.getHand());
        }
    }

    private static boolean isAtm(Level level, net.minecraft.core.BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return blockId != null
                && "lightmanscurrency".equals(blockId.getNamespace())
                && ATM_PATH.equals(blockId.getPath());
    }
}
