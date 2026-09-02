package com.tanrunn.servermenu.server.hook;

import com.tanrunn.servermenu.server.FlightRingService;
import com.tanrunn.servermenu.server.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** 飞行戒指的手动充能交互：Shift+右键只在服务端实际扣款。 */
public final class FlightRingHooks {
    private FlightRingHooks() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!isCharging(event)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player) {
            FlightRingService.charge(player, event.getHand());
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isCharging(event)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player) {
            FlightRingService.charge(player, event.getHand());
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!isCharging(event)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player) {
            FlightRingService.charge(player, event.getHand());
        }
    }

    private static boolean isCharging(PlayerInteractEvent event) {
        return event.getEntity().isCrouching()
                && event.getItemStack().is(ModItems.FLIGHT_RING.get());
    }
}
