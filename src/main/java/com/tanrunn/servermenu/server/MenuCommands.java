package com.tanrunn.servermenu.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * /servermenu 与 /pad 命令：与 Pad 物品复用同一个 {@link MenuService#openMenu}。
 */
public final class MenuCommands {
    private MenuCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var node = dispatcher.register(Commands.literal("servermenu")
                .requires(source -> source.getPlayer() != null)
                .executes(ctx -> open(ctx)));
        dispatcher.register(Commands.literal("pad").redirect(node));
    }

    private static int open(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return MenuService.INSTANCE.openMenu(player) ? 1 : 0;
    }
}
