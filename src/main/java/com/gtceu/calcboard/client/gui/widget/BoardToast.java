package com.gtceu.calcboard.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Global on-screen toast notification renderer for calculator board actions.
 * Renders brightly and clearly on top of all UI layers with smooth fade animation.
 */
public final class BoardToast {

    private static Component currentMessage = null;
    private static long showTimestamp = 0;
    private static final long DURATION_MS = 2200;
    private static final long FADE_MS = 250;

    private BoardToast() {}

    /**
     * Shows a toast message on the calculator board HUD.
     */
    public static void show(Component message) {
        if (message == null) return;
        currentMessage = message;
        showTimestamp = System.currentTimeMillis();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) {
            mc.player.displayClientMessage(message, true);
        }
    }

    public static void show(String translationKey, Object... args) {
        show(Component.translatable(translationKey, args));
    }

    /**
     * Renders the active toast at the top-center of the screen.
     */
    public static void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        if (currentMessage == null) return;

        long elapsed = System.currentTimeMillis() - showTimestamp;
        if (elapsed >= DURATION_MS) {
            currentMessage = null;
            return;
        }

        float alpha = 1.0f;
        if (elapsed < FADE_MS) {
            alpha = (float) elapsed / FADE_MS;
        } else if (elapsed > DURATION_MS - FADE_MS) {
            alpha = (float) (DURATION_MS - elapsed) / FADE_MS;
        }
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));
        int alphaInt = (int) (alpha * 255);
        if (alphaInt <= 4) return;

        int msgW = font.width(currentMessage);
        int padX = 14;
        int boxW = msgW + padX * 2;
        int boxH = 22;
        int boxX = (screenWidth - boxW) / 2;
        int boxY = screenHeight - 48; // Bottom-center: avoids top menu bar & side overlays

        graphics.pose().pushPose();

        // 1. Drop shadow & background card
        int bgAlpha = Math.min(0xF2, (int) (0xF2 * alpha));
        int bgColor = (bgAlpha << 24) | 0x181C26;
        int borderColor = (alphaInt << 24) | 0x4E5D7A;
        int shadowColor = (Math.min(0x80, (int) (0x80 * alpha)) << 24);

        graphics.fill(boxX + 2, boxY + 2, boxX + boxW + 2, boxY + boxH + 2, shadowColor);
        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, bgColor);
        graphics.renderOutline(boxX, boxY, boxW, boxH, borderColor);

        // 2. High-contrast text
        int textColor = (alphaInt << 24) | 0xFFFFFF;
        graphics.drawCenteredString(font, currentMessage, boxX + boxW / 2, boxY + (boxH - 8) / 2, textColor);

        graphics.pose().popPose();
    }
}

