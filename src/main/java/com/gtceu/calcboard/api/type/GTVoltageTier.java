package com.gtceu.calcboard.api.type;

/**
 * GregTech voltage tiers (ULV to MAX) with exact EU/t voltage ratings, badge colors, and text formatting codes.
 */
public enum GTVoltageTier {
    ULV("ULV", 8L, 0xFF8C8C8C, "§7"),
    LV("LV", 32L, 0xFFFFFFFF, "§f"),
    MV("MV", 128L, 0xFF55FFFF, "§b"),
    HV("HV", 512L, 0xFFFFAA00, "§6"),
    EV("EV", 2048L, 0xFFAA00AA, "§5"),
    IV("IV", 8192L, 0xFF0000AA, "§1"),
    LuV("LuV", 32768L, 0xFFFF55FF, "§d"),
    ZPM("ZPM", 131072L, 0xFFFF5555, "§c"),
    UV("UV", 524288L, 0xFF55FF55, "§a"),
    UHV("UHV", 2097152L, 0xFF00AA00, "§2"),
    UEV("UEV", 8388608L, 0xFF00AAAA, "§3"),
    UIV("UIV", 33554432L, 0xFFAA0000, "§4"),
    UXV("UXV", 134217728L, 0xFFFFD700, "§e"),
    OpV("OpV", 536870912L, 0xFF40E0D0, "§3"),
    MAX("MAX", 2147483647L, 0xFFFF0055, "§c");

    private final String name;
    private final long voltage;
    private final int color;
    private final String formatCode;

    GTVoltageTier(String name, long voltage, int color, String formatCode) {
        this.name = name;
        this.voltage = voltage;
        this.color = color;
        this.formatCode = formatCode;
    }

    public String getName() {
        return name;
    }

    public long getVoltage() {
        return voltage;
    }

    public int getColor() {
        return color;
    }

    public String getFormatCode() {
        return formatCode;
    }

    public static GTVoltageTier getTierForVoltage(long voltage) {
        if (voltage <= 8) return ULV;
        for (int i = 1; i < values().length; i++) {
            if (voltage <= values()[i].voltage) {
                return values()[i];
            }
        }
        return MAX;
    }

    public static GTVoltageTier getMaxTierProvided(long totalEUtCapacity) {
        GTVoltageTier tier = ULV;
        for (GTVoltageTier t : values()) {
            if (totalEUtCapacity >= t.voltage) {
                tier = t;
            } else {
                break;
            }
        }
        return tier;
    }

    public static GTVoltageTier getByIndex(int index) {
        if (index < 0) return ULV;
        if (index >= values().length) return MAX;
        return values()[index];
    }

    public static GTVoltageTier fromVoltage(long voltage) {
        return getTierForVoltage(voltage);
    }

    public static GTVoltageTier fromName(String name) {
        if (name == null || name.isEmpty()) return null;
        for (GTVoltageTier t : values()) {
            if (t.name().equalsIgnoreCase(name) || t.getName().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }
}

