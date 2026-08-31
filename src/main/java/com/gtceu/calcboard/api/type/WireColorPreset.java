package com.gtceu.calcboard.api.type;

import net.minecraft.network.chat.Component;

/**
 * Color preset palette for customizable canvas wires and connection themes.
 * Provides ARGB color codes and localization keys.
 */
public enum WireColorPreset {
    CYAN("cyan", 0xFF00E5FF, "gui.gtcalcboard.color.cyan"),
    GREEN("green", 0xFF00E676, "gui.gtcalcboard.color.green"),
    GOLD("gold", 0xFFFFD700, "gui.gtcalcboard.color.gold"),
    VIOLET("violet", 0xFFB388FF, "gui.gtcalcboard.color.violet"),
    ORANGE("orange", 0xFFFFAA00, "gui.gtcalcboard.color.orange"),
    ICE_BLUE("ice_blue", 0xFF80D8FF, "gui.gtcalcboard.color.ice_blue"),
    CRIMSON("crimson", 0xFFFF5252, "gui.gtcalcboard.color.crimson"),
    WHITE("white", 0xFFE0E0E0, "gui.gtcalcboard.color.white");

    private final String id;
    private final int argb;
    private final String translationKey;

    WireColorPreset(String id, int argb, String translationKey) {
        this.id = id;
        this.argb = argb;
        this.translationKey = translationKey;
    }

    public String getId() {
        return id;
    }

    public int getArgb() {
        return argb;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }

    public static WireColorPreset fromId(String id, WireColorPreset fallback) {
        if (id == null) return fallback;
        for (WireColorPreset p : values()) {
            if (p.id.equalsIgnoreCase(id) || p.name().equalsIgnoreCase(id)) {
                return p;
            }
        }
        return fallback;
    }
}
