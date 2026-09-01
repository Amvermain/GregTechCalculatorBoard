package com.gtceu.calcboard.server.command;

import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2COpenBoardPacket;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class CalcBoardServerCommands {

    private CalcBoardServerCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("gtcalcboard")
                        .then(Commands.literal("open")
                                .executes(ctx -> {
                                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                                        NetworkHandler.sendToPlayer(player, new S2COpenBoardPacket());
                                    }
                                    return 1;
                                })
                        )
        );
    }
}
