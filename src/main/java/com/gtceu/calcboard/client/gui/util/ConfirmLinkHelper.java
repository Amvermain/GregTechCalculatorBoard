package com.gtceu.calcboard.client.gui.util;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;

import java.net.URI;

/**
 * Utility helper to safely prompt a confirmation screen before opening external URIs in the platform browser.
 */
public final class ConfirmLinkHelper {

    private ConfirmLinkHelper() {}

    /**
     * Prompts the player with a confirmation dialog before opening an external URL.
     *
     * @param parent the parent screen to return to after confirmation or cancellation
     * @param url the external URL to open
     */
    public static void openExternalLink(Screen parent, String url) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        Screen returnScreen = parent != null ? parent : mc.screen;
        mc.setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) {
                Util.getPlatform().openUri(URI.create(url));
            }
            mc.setScreen(returnScreen);
        }, url, true));
    }
}
