package com.tanrunn.servermenu.server;

import com.tanrunn.servermenu.api.economy.EconomyBalance;
import com.tanrunn.servermenu.api.economy.EconomyBridgeRegistry;
import com.tanrunn.servermenu.api.economy.EconomyTransactionResult;
import com.tanrunn.servermenu.common.network.ServerMenuNetwork.TerritoryInfoPayload;
import com.tanrunn.servermenu.server.integration.lc.LcConstants;
import com.tanrunn.servermenu.server.integration.territory.LuckPermsTerritoryBridge;
import net.minecraft.server.level.ServerPlayer;
import net.luckperms.api.model.user.User;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;

/** OAPC 领地服务页的数据与购买业务。所有方法都在服务端主线程执行。 */
public final class TerritoryService {
    private static final String PURCHASE_SOURCE = "server_menu_territory";

    private TerritoryService() {
    }

    public static TerritoryInfoPayload snapshot(ServerPlayer player) {
        if (player == null || player.server == null) {
            return unavailable("玩家状态不可用，请稍后再试。", true);
        }

        boolean oapcAvailable = false;
        int claimsHeld = 0;
        int claimLimit = 0;
        String permissionNode = "";
        try {
            IServerClaimsManagerAPI claims = OpenPACServerAPI.get(player.server).getServerClaimsManager();
            var info = claims.getPlayerInfo(player.getUUID());
            claimsHeld = info == null ? 0 : Math.max(0, info.getClaimCount());
            claimLimit = Math.max(0, claims.getPlayerFullClaimLimit(player));
            permissionNode = xaero.pac.common.server.player.permission.api.UsedPermissionNodes
                    .MAX_PLAYER_CLAIMS.getNodeString();
            oapcAvailable = true;
        } catch (LinkageError | RuntimeException ignored) {
            // 可选 OAPC 或当前服务端数据未就绪时，页面保持可打开但不允许购买。
        }

        boolean luckPermsAvailable = LuckPermsTerritoryBridge.isAvailable();
        User user = luckPermsAvailable ? LuckPermsTerritoryBridge.user(player) : null;
        int purchased = LuckPermsTerritoryBridge.purchases(user);
        int remaining = Math.max(0, TerritoryShopConfig.MAX_PURCHASES.get() - purchased);
        EconomyBalance balance = EconomyBridgeRegistry.balance(LcConstants.PROVIDER_ID, player);

        String message = "";
        boolean error = false;
        if (!oapcAvailable) {
            message = "OAPC 领地系统未就绪。";
            error = true;
        } else if (!luckPermsAvailable || user == null) {
            message = "LuckPerms 未就绪，暂时不能购买领地上限。";
            error = true;
        } else if (!balance.available()) {
            message = "LC 银行未就绪，暂时不能购买领地上限。";
            error = true;
        }
        return new TerritoryInfoPayload(oapcAvailable, luckPermsAvailable, balance.available(),
                claimsHeld, claimLimit, purchased, remaining,
                TerritoryShopConfig.priceForPurchaseCount(purchased), balance.minorUnits(),
                balance.available() ? LcConstants.PROVIDER_DISPLAY_NAME : "", permissionNode,
                message, error);
    }

    /** 扣款成功后写入 LuckPerms；失败时由调用方负责退款。 */
    public static PurchaseResult purchase(ServerPlayer player) {
        if (player == null || player.server == null) {
            return PurchaseResult.failure("玩家状态不可用，请稍后再试。");
        }
        TerritoryInfoPayload before = snapshot(player);
        if (!before.oapcAvailable()) {
            return PurchaseResult.failure("OAPC 领地系统未就绪，无法购买。");
        }
        if (!before.luckPermsAvailable()) {
            return PurchaseResult.failure("LuckPerms 未就绪，无法同步领地上限。");
        }
        if (!before.economyAvailable()) {
            return PurchaseResult.failure("LC 银行未就绪，无法扣款。");
        }
        if (before.maxPurchasable() <= 0) {
            return PurchaseResult.failure("已达到领地商店的购买上限。");
        }

        long price = before.claimPrice();
        String requestId = "territory:" + player.getUUID() + ":" + System.nanoTime();
        EconomyTransactionResult payment = EconomyBridgeRegistry.withdrawMinorUnits(
                LcConstants.PROVIDER_ID, player, price, PURCHASE_SOURCE,
                "购买一个 OAPC 领地上限", requestId);
        if (!payment.success()) {
            return PurchaseResult.failure(paymentMessage(payment));
        }

        User user = LuckPermsTerritoryBridge.user(player);
        int newLimit = before.claimLimit() >= Integer.MAX_VALUE
                ? Integer.MAX_VALUE : before.claimLimit() + 1;
        boolean granted = LuckPermsTerritoryBridge.grantClaim(user, newLimit, before.purchasedClaims() + 1);
        if (!granted) {
            EconomyTransactionResult refund = EconomyBridgeRegistry.depositMinorUnits(
                    LcConstants.PROVIDER_ID, player, payment.processedMinorUnits(),
                    PURCHASE_SOURCE, "领地上限写入失败退款", requestId + ":refund");
            if (!refund.success()) {
                return PurchaseResult.failure("领地上限写入失败，退款也未完成，请联系管理员处理。");
            }
            return PurchaseResult.failure("领地上限写入失败，已退回 LC 金币。");
        }
        return PurchaseResult.success("购买成功，领地上限已增加 1。");
    }

    private static String paymentMessage(EconomyTransactionResult result) {
        return switch (result.status()) {
            case INSUFFICIENT_FUNDS -> "LC 余额不足，无法购买。";
            case QUARANTINED -> "当前维度不允许使用 LC 银行。";
            case WRONG_THREAD -> "服务器正在处理其他操作，请稍后再试。";
            default -> "LC 扣款失败，购买未完成。";
        };
    }

    private static TerritoryInfoPayload unavailable(String message, boolean error) {
        return new TerritoryInfoPayload(false, false, false, 0, 0, 0, 0,
                0, 0, "", "", message, error);
    }

    public record PurchaseResult(boolean success, String message, boolean error) {
        static PurchaseResult success(String message) {
            return new PurchaseResult(true, message, false);
        }

        static PurchaseResult failure(String message) {
            return new PurchaseResult(false, message, true);
        }
    }
}
