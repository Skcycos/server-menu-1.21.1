package com.tanrunn.servermenu.server;

import com.tanrunn.servermenu.api.economy.EconomyBridgeRegistry;
import com.tanrunn.servermenu.api.economy.EconomyOperationIds;
import com.tanrunn.servermenu.api.economy.EconomyTransactionResult;
import com.tanrunn.servermenu.api.economy.EconomyTransactionStatus;
import com.tanrunn.servermenu.server.integration.lc.LcConstants;
import com.tanrunn.servermenu.server.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 飞行戒指的服务端权威逻辑：ATM 只负责手动充能，飞行 tick 只消耗本地耐久。 */
public final class FlightRingService {
    private static final String SOURCE = "server_menu:flight_ring";
    private static final String REASON = "flight_ring_charge";
    private static final int TICKS_PER_DURABILITY = 40;

    private static final Map<UUID, PlayerState> STATES = new HashMap<>();

    private FlightRingService() {
    }

    /** Shift+右键手持戒指时调用；成功扣款后才补满当前戒指的原生耐久。 */
    public static void charge(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ModItems.FLIGHT_RING.get())) {
            return;
        }
        charge(player, stack);
    }

    static void charge(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()
                || !stack.is(ModItems.FLIGHT_RING.get())) {
            return;
        }
        if (stack.getDamageValue() <= 0) {
            player.displayClientMessage(Component.translatable(
                    "message.server_menu.flight_ring.full"), true);
            return;
        }

        long cost = chargeCost(stack.getMaxDamage(), stack.getDamageValue(),
                FlightRingConfig.CHARGE_COST_COPPER.get());
        if (cost <= 0) {
            player.displayClientMessage(Component.translatable(
                    "message.server_menu.flight_ring.charge_failed"), true);
            return;
        }
        String requestSeed = UUID.randomUUID().toString();
        String requestId = EconomyOperationIds.generate(
                EconomyOperationIds.FR_CHARGE,
                LcConstants.PROVIDER_ID,
                SOURCE,
                "charge_durability",
                requestSeed,
                "");
        EconomyTransactionResult result = EconomyBridgeRegistry.withdrawMinorUnits(
                LcConstants.PROVIDER_ID,
                player,
                cost,
                SOURCE,
                REASON,
                requestId);
        if (!result.success() || result.processedMinorUnits() != cost) {
            player.displayClientMessage(chargeFailure(result.status()), true);
            return;
        }

        // 交易成功后才改变物品，且一次补满当前戒指全部缺少的耐久。
        stack.setDamageValue(0);
        player.getInventory().setChanged();
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
        player.displayClientMessage(Component.translatable(
                "message.server_menu.flight_ring.charged",
                remainingDurability(stack),
                stack.getMaxDamage(),
                cost), true);
    }

    /** 每个实际装备的戒指每 tick 调用；不访问 ATM。 */
    public static void tick(ServerPlayer player, String identifier, int index, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()
                || !stack.is(ModItems.FLIGHT_RING.get())) {
            return;
        }

        PlayerState state = STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
        String slotKey = slotKey(identifier, index);
        boolean eligible = !player.isCreative()
                && !player.isSpectator()
                && FlightRingConfig.isWorldAllowed(player.level())
                && hasRemainingDurability(stack);
        if (!eligible) {
            state.activeSlots.remove(slotKey);
            state.flightTicks.remove(slotKey);
            reconcileAbilities(player, state);
            return;
        }

        state.activeSlots.add(slotKey);
        grantMayFly(player, state);
        if (player.getAbilities().flying && !player.onGround()) {
            int elapsed = state.flightTicks.getOrDefault(slotKey, 0) + 1;
            if (elapsed >= TICKS_PER_DURABILITY) {
                elapsed -= TICKS_PER_DURABILITY;
                consumeDurability(player, stack, state, slotKey);
            }
            state.flightTicks.put(slotKey, elapsed);
        }
    }

    public static void unequip(ServerPlayer player, String identifier, int index) {
        if (player == null) {
            return;
        }
        PlayerState state = STATES.get(player.getUUID());
        if (state == null) {
            return;
        }
        String slotKey = slotKey(identifier, index);
        state.activeSlots.remove(slotKey);
        state.flightTicks.remove(slotKey);
        reconcileAbilities(player, state);
        removeEmptyState(player.getUUID(), state);
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        if (player != null) {
            STATES.remove(player.getUUID());
        }
    }

    static int remainingDurability(ItemStack stack) {
        return remainingDurability(stack.getMaxDamage(), stack.getDamageValue());
    }

    static int remainingDurability(int maxDamage, int damage) {
        return Math.max(0, maxDamage - damage);
    }

    static long chargeCost(int maxDamage, int damage, long costPerDurability) {
        int missingDurability = Math.max(0, Math.min(maxDamage, damage));
        if (missingDurability == 0 || costPerDurability <= 0
                || missingDurability > Long.MAX_VALUE / costPerDurability) {
            return missingDurability == 0 ? 0 : -1;
        }
        return missingDurability * costPerDurability;
    }

    static int damageAfterFlight(int maxDamage, int damage) {
        return Math.min(maxDamage, damage + 1);
    }

    private static void consumeDurability(ServerPlayer player, ItemStack stack, PlayerState state, String slotKey) {
        if (!hasRemainingDurability(stack)) {
            return;
        }
        stack.setDamageValue(damageAfterFlight(stack.getMaxDamage(), stack.getDamageValue()));
        if (stack.getDamageValue() >= stack.getMaxDamage()) {
            state.activeSlots.remove(slotKey);
            state.flightTicks.remove(slotKey);
            player.displayClientMessage(Component.translatable(
                    "message.server_menu.flight_ring.empty"), true);
            reconcileAbilities(player, state);
        }
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
    }

    private static void grantMayFly(ServerPlayer player, PlayerState state) {
        if (!player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            state.grantedMayFly = true;
            player.onUpdateAbilities();
        }
    }

    private static void reconcileAbilities(ServerPlayer player, PlayerState state) {
        if (!state.activeSlots.isEmpty() || !state.grantedMayFly
                || player.isCreative() || player.isSpectator()) {
            return;
        }
        // 只撤销本服务授予的 mayfly，避免破坏创造/旁观或其他来源的飞行权限。
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        state.grantedMayFly = false;
        player.onUpdateAbilities();
    }

    private static boolean hasRemainingDurability(ItemStack stack) {
        return stack.getDamageValue() < stack.getMaxDamage();
    }

    private static String slotKey(String identifier, int index) {
        return (identifier == null ? "?" : identifier) + ":" + index;
    }

    private static void removeEmptyState(UUID playerId, PlayerState state) {
        if (state.activeSlots.isEmpty() && state.flightTicks.isEmpty() && !state.grantedMayFly) {
            STATES.remove(playerId);
        }
    }

    private static Component chargeFailure(EconomyTransactionStatus status) {
        return switch (status) {
            case INSUFFICIENT_FUNDS -> Component.translatable(
                    "message.server_menu.flight_ring.insufficient_funds");
            case QUARANTINED -> Component.translatable(
                    "message.server_menu.flight_ring.quarantined");
            case UNAVAILABLE -> Component.translatable(
                    "message.server_menu.flight_ring.unavailable");
            default -> Component.translatable(
                    "message.server_menu.flight_ring.charge_failed");
        };
    }

    private static final class PlayerState {
        private final Set<String> activeSlots = new HashSet<>();
        private final Map<String, Integer> flightTicks = new HashMap<>();
        private boolean grantedMayFly;
    }
}
