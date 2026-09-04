package com.gtceu.calcboard.client.command;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.storage.ClientPreferenceManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class CalcBoardClientCommands {

    private CalcBoardClientCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("gtcalcboard")
                        .then(Commands.literal("open")
                                .executes(ctx -> {
                                    ClientPreferenceManager.getInstance().markWelcomeMessageSeen();
                                    Minecraft mc = Minecraft.getInstance();
                                    mc.tell(() -> mc.setScreen(new BoardScreen()));
                                    return 1;
                                })
                        )
        );
    }
}
