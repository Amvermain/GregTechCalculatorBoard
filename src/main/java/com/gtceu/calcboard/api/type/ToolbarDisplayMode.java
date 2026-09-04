package com.gtceu.calcboard.api.type;

/**
 * Display modes for the top toolbar widget.
 * Controls whether buttons render in full text, compact icon-only mode, or automatically adapt.
 */
public enum ToolbarDisplayMode {
    AUTO("gui.gtcalcboard.toolbar.auto"),
    COMPACT("gui.gtcalcboard.toolbar.compact"),
    FULL("gui.gtcalcboard.toolbar.full");

    private final String translationKey;

    ToolbarDisplayMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public String getDisplayName() {
        return net.minecraft.network.chat.Component.translatable(translationKey).getString();
    }

    public ToolbarDisplayMode next() {
        ToolbarDisplayMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
