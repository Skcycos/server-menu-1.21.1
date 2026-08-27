package com.tanrunn.servermenu.server.integration.territory;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.query.QueryOptions;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.server.player.permission.api.UsedPermissionNodes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * LuckPerms 与 OAPC 数值权限的桥接。
 *
 * <p>OAPC 的 LuckPerms 实现读取的是元数据值，而不是简单 true/false 权限节点；
 * 因此购买后写入 {@code xaero.pac_max_claims=<数字>}，正是 OAPC
 * {@link UsedPermissionNodes#MAX_PLAYER_CLAIMS} 使用的节点。</p>
 */
public final class LuckPermsTerritoryBridge {
    /** server-menu 自己记录购买次数的元数据键，用于限制商店购买次数。 */
    public static final String PURCHASES_META_KEY = "server_menu.territory_purchases";

    private LuckPermsTerritoryBridge() {
    }

    public static boolean isAvailable() {
        try {
            LuckPermsProvider.get();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static User user(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        try {
            LuckPerms api = LuckPermsProvider.get();
            var manager = api.getUserManager();
            User user = manager.getUser(player.getUUID());
            if (user != null) {
                return user;
            }
            return manager.loadUser(player.getUUID()).join();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static int purchases(User user) {
        if (user == null) {
            return 0;
        }
        try {
            CachedMetaData metadata = user.getCachedData()
                    .getMetaData(QueryOptions.defaultContextualOptions());
            return metadata.getMetaValue(PURCHASES_META_KEY, Integer::parseInt)
                    .orElse(0);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** 写入一次购买后的次数与 OAPC 领地上限，并持久化 LuckPerms User。 */
    public static boolean grantClaim(User user, int newClaimLimit, int newPurchases) {
        if (user == null || newClaimLimit < 0 || newPurchases < 0) {
            return false;
        }
        NodeMap data = user.data();
        List<Node> oldPurchaseNodes = nodesWithMetaKey(data, PURCHASES_META_KEY);
        String permissionNode = UsedPermissionNodes.MAX_PLAYER_CLAIMS.getNodeString();
        if (permissionNode == null || permissionNode.isBlank()) {
            return false;
        }
        List<Node> oldClaimNodes = nodesWithMetaKey(data, permissionNode);
        try {
            removeAll(data, oldPurchaseNodes);
            removeAll(data, oldClaimNodes);
            data.add(MetaNode.builder(PURCHASES_META_KEY, String.valueOf(newPurchases)).build());
            data.add(MetaNode.builder(permissionNode, String.valueOf(newClaimLimit)).build());
            LuckPermsProvider.get().getUserManager().saveUser(user).join();
            return true;
        } catch (RuntimeException e) {
            removeAll(data, nodesWithMetaKey(data, PURCHASES_META_KEY));
            removeAll(data, nodesWithMetaKey(data, permissionNode));
            restore(data, oldPurchaseNodes);
            restore(data, oldClaimNodes);
            return false;
        }
    }

    private static List<Node> nodesWithMetaKey(NodeMap data, String key) {
        List<Node> result = new ArrayList<>();
        if (data == null || key == null) {
            return result;
        }
        for (Node node : data.toCollection()) {
            if (node instanceof MetaNode meta && key.equals(meta.getMetaKey())) {
                result.add(node);
            }
        }
        return result;
    }

    private static void removeAll(NodeMap data, Collection<Node> nodes) {
        for (Node node : nodes) {
            data.remove(node);
        }
    }

    private static void restore(NodeMap data, Collection<Node> nodes) {
        for (Node node : nodes) {
            data.add(node);
        }
    }
}
