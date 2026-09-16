package com.gtceu.calcboard.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SoundInstance;

/**
 * Centralized defensive helpers for client-side Minecraft resources,
 * ensuring safe execution across window resizing, focus shifts, and headless environments.
 */
public final class ClientSafetyHelper {

    private ClientSafetyHelper() {}

    public static boolean isShiftDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        try {
            return Screen.hasShiftDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isControlDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        try {
            return Screen.hasControlDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static long getWindowHandleSafely() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return 0L;
        try {
            return mc.getWindow().getWindow();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static void playSoundSafely(SoundInstance sound) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null || sound == null) return;
        mc.getSoundManager().play(sound);
    }

    public static int getStringWidth(Font font, String text) {
        if (text == null || text.isEmpty()) return 0;
        if (font != null) {
            try {
                return font.width(text);
            } catch (Throwable ignored) {
                return text.length() * 6;
            }
        }
        return text.length() * 6;
    }
}
