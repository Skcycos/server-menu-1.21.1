package com.tanrunn.servermenu.server.hook;

import com.tanrunn.servermenu.server.FlightRingService;
import com.tanrunn.servermenu.server.registry.FlightRingItem;
import com.tanrunn.servermenu.server.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
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

    /**
     * 原版铁砧会单独允许两件同类可损坏物品合并，即使物品没有可用的修复材料。
     * 飞行戒指只能通过 LC 充能，因此禁止它参与任何带右侧输入的铁砧操作。
     * 左侧单独放戒指仍可改名；改名不会改变耐久。
     */
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (isAnvilRepairBlocked(event.getLeft(), event.getRight())) {
            event.setCanceled(true);
        }
    }

    static boolean isAnvilRepairBlocked(ItemStack left, ItemStack right) {
        return !right.isEmpty()
                && (isFlightRing(left) || isFlightRing(right));
    }

    private static boolean isFlightRing(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof FlightRingItem;
    }

    private static boolean isCharging(PlayerInteractEvent event) {
        return event.getEntity().isCrouching()
                && event.getItemStack().is(ModItems.FLIGHT_RING.get());
    }
}
