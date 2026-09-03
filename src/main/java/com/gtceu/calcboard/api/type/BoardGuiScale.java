package com.gtceu.calcboard.api.type;

/**
 * Dedicated GUI Scale override options for the Calculator Board screen.
 * Allows decoupling the Board Screen resolution from Minecraft's global game GUI scale.
 */
public enum BoardGuiScale {
    AUTO(0, "gui.gtcalcboard.scale.auto"),
    SCALE_1X(1, "gui.gtcalcboard.scale.1x"),
    SCALE_2X(2, "gui.gtcalcboard.scale.2x"),
    SCALE_3X(3, "gui.gtcalcboard.scale.3x"),
    SCALE_4X(4, "gui.gtcalcboard.scale.4x");

    private final int scaleFactor;
    private final String translationKey;

    BoardGuiScale(int scaleFactor, String translationKey) {
        this.scaleFactor = scaleFactor;
        this.translationKey = translationKey;
    }

    public int getScaleFactor() {
        return scaleFactor;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public String getDisplayName() {
        return net.minecraft.network.chat.Component.translatable(translationKey).getString();
    }

    public BoardGuiScale next() {
        BoardGuiScale[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public int resolveEffectiveScale(int gameGuiScale) {
        return resolveEffectiveScale(gameGuiScale, 1920, 1080);
    }

    public int resolveEffectiveScale(int gameGuiScale, int windowPhysW, int windowPhysH) {
        if (this.scaleFactor > 0) {
            return this.scaleFactor;
        }
        if (gameGuiScale <= 2) {
            return Math.max(1, gameGuiScale);
        }
        if (windowPhysW >= 1920 && windowPhysH >= 1080) {
            return 2;
        }
        if (windowPhysW < 1280 || windowPhysH < 720) {
            return 2;
        }
        return Math.max(1, gameGuiScale - 1);
    }
}
