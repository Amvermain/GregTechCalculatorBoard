package com.gtceu.calcboard.api.type;

/**
 * Represents the Steam Processing Mode for GregTech CEu steam-era machines.
 */
public enum SteamMode {
    NONE("Electric", "§f", 1.0, 0xFFFFFFFF),
    LOW_PRESSURE("LP Steam", "§6", 2.0, 0xFFD28C38),
    HIGH_PRESSURE("HP Steam", "§7", 1.0, 0xFFAAAAAA);

    private final String displayName;
    private final String formatCode;
    private final double durationMultiplier;
    private final int color;

    SteamMode(String displayName, String formatCode, double durationMultiplier, int color) {
        this.displayName = displayName;
        this.formatCode = formatCode;
        this.durationMultiplier = durationMultiplier;
        this.color = color;
    }

    public boolean isSteam() {
        return this != NONE;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFormatCode() {
        return formatCode;
    }

    public double getDurationMultiplier() {
        return durationMultiplier;
    }

    public int getColor() {
        return color;
    }

    public String getShortName() {
        return this == HIGH_PRESSURE ? "HP" : (this == LOW_PRESSURE ? "LP" : "EL");
    }

    public static SteamMode fromString(String name) {
        if (name == null) return NONE;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}

