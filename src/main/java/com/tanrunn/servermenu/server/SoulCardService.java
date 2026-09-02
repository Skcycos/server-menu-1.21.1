package com.tanrunn.servermenu.server;

import com.tanrunn.servermenu.ServerMenuMod;
import com.tanrunn.servermenu.server.registry.ModItems;
import com.tanrunn.servermenu.server.registry.SoulSocialSecurityCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.lang.reflect.Method;

/** 灵魂社保卡的服务端权威逻辑。 */
public final class SoulCardService {
    private static final String FOUNTAIN_CLASS =
            "com.tanrunn.servermenu.server.integration.lc.LcSoulCardFountainAnimation";

    private SoulCardService() {
    }

    /** 敌对生物死亡时，只给实际由玩家造成的击杀计数。 */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event == null || !(event.getEntity() instanceof Enemy)
                || event.getEntity().level().isClientSide()) {
            return;
        }

        ServerPlayer killer = resolvePlayerKiller(event);
        if (killer == null) {
            return;
        }

        ItemStack card = findCarriedCard(killer);
        if (card.isEmpty()) {
            return;
        }

        int before = SoulSocialSecurityCardItem.souls(card);
        int after = SoulSocialSecurityCardItem.addSoul(card);
        if (after == before) {
            return;
        }
        markInventoryChanged(killer);
        killer.displayClientMessage(Component.translatable(
                "message.server_menu.soul_card.gained", after,
                SoulSocialSecurityCardItem.MAX_SOULS), true);
    }

    /** 是否存在一张身上携带且有灵魂可兑换的卡片。 */
    public static boolean hasConvertibleCard(Player player) {
        ItemStack card = findCarriedCard(player);
        return !card.isEmpty() && SoulSocialSecurityCardItem.souls(card) > 0;
    }

    /** 在 ATM 交互处将第一张直接携带的卡片兑换为 LC main 链铜币。 */
    public static void exchangeAtAtm(ServerPlayer player, ServerLevel level, BlockPos atmPos) {
        if (player == null || level == null || atmPos == null) {
            return;
        }

        ItemStack card = findCarriedCard(player);
        int souls = SoulSocialSecurityCardItem.souls(card);
        if (card.isEmpty() || souls <= 0) {
            return;
        }

        long copper = conversionAmount(souls);
        if (copper <= 0) {
            player.displayClientMessage(Component.translatable(
                    "message.server_menu.soul_card.exchange_failed"), true);
            return;
        }

        if (!startFountainAnimation(player, level, atmPos, copper, card.copyWithCount(1))) {
            player.displayClientMessage(Component.translatable(
                    "message.server_menu.soul_card.exchange_failed"), true);
            return;
        }

        // 喷泉动画已成功排入服务端队列后清除卡片数据；同一 tick 的后续点击会看到 0。
        SoulSocialSecurityCardItem.clearSouls(card);
        markInventoryChanged(player);
        player.displayClientMessage(Component.translatable(
                "message.server_menu.soul_card.exchanged", souls, copper), true);
    }

    static ItemStack findCarriedCard(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(ModItems.SOUL_SOCIAL_SECURITY_CARD.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    static long conversionAmount(int souls) {
        return SoulSocialSecurityCardItem.clampSouls(souls);
    }

    public static long copperForSecond(long totalCopper, int second) {
        if (totalCopper <= 0 || second < 1 || second > 10) {
            return 0;
        }
        long current = totalCopper * second / 10;
        long previous = totalCopper * (second - 1L) / 10;
        return current - previous;
    }

    private static ServerPlayer resolvePlayerKiller(LivingDeathEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        Entity owner = event.getSource().getEntity();
        ServerPlayer directPlayer = asServerPlayer(direct);
        if (directPlayer != null) {
            return directPlayer;
        }
        ServerPlayer ownerPlayer = asServerPlayer(owner);
        if (ownerPlayer != null) {
            return ownerPlayer;
        }
        if (direct instanceof Projectile projectile) {
            ServerPlayer projectileOwner = asServerPlayer(projectile.getOwner());
            if (projectileOwner != null) {
                return projectileOwner;
            }
        }
        if (owner instanceof Projectile projectile) {
            return asServerPlayer(projectile.getOwner());
        }
        return null;
    }

    private static ServerPlayer asServerPlayer(Entity entity) {
        return entity instanceof ServerPlayer player ? player : null;
    }

    private static void markInventoryChanged(ServerPlayer player) {
        player.getInventory().setChanged();
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
    }

    private static boolean startFountainAnimation(ServerPlayer player, ServerLevel level,
                                                  BlockPos atmPos, long copper, ItemStack displayItem) {
        try {
            Class<?> animationClass = Class.forName(FOUNTAIN_CLASS, false,
                    SoulCardService.class.getClassLoader());
            Method start = animationClass.getMethod("start", ServerPlayer.class,
                    ServerLevel.class, BlockPos.class, long.class, ItemStack.class);
            return Boolean.TRUE.equals(start.invoke(null, player, level, atmPos, copper, displayItem));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            ServerMenuMod.LOGGER.warn("[ServerMenu] LC soul-card fountain animation unavailable", exception);
            return false;
        }
    }
}
