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
                        .then(Commands.literal("web")
                                .executes(ctx -> {
                                    if (com.gtceu.calcboard.config.CalcBoardClientConfig.ENABLE_LOCAL_WEB_SERVER != null
                                            && !com.gtceu.calcboard.config.CalcBoardClientConfig.ENABLE_LOCAL_WEB_SERVER.get()) {
                                        Minecraft mc = Minecraft.getInstance();
                                        if (mc.player != null) {
                                            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e⚠ ").append(net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.web.disabled_hint")));
                                        }
                                        return 0;
                                    }
                                    var daemon = com.gtceu.calcboard.client.web.LocalWebServerDaemon.getInstance();
                                    if (!daemon.isRunning()) {
                                        daemon.start();
                                    }
                                    if (!daemon.isRunning()) {
                                        Minecraft mc = Minecraft.getInstance();
                                        if (mc.player != null) {
                                            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c⚠ ").append(net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.web.bind_failed")));
                                        }
                                        return 0;
                                    }
                                    com.gtceu.calcboard.client.web.WebSyncEventBus.publishCurrentBoard();
                                    String url = daemon.getUrl();
                                    Minecraft mc = Minecraft.getInstance();
                                    mc.tell(() -> {
                                        try {
                                            net.minecraft.Util.getPlatform().openUri(java.net.URI.create(url));
                                        } catch (Throwable ignored) {}
                                        try {
                                            mc.keyboardHandler.setClipboard(url);
                                        } catch (Throwable ignored) {}
                                        if (mc.player != null) {
                                            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.web.opened", url));
                                        }
                                    });
                                    return 1;
                                })
                        )
        );
    }
}
