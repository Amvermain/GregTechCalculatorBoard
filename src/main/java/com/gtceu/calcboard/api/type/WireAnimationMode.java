package com.gtceu.calcboard.api.type;

import net.minecraft.network.chat.Component;

/**
 * Visual mode for canvas wire pulse flow animations.
 */
public enum WireAnimationMode {
    RATE_MODULATED("gui.gtcalcboard.wire_anim.rate_modulated"),
    UNIFORM_LEGACY("gui.gtcalcboard.wire_anim.uniform"),
    DISABLED("gui.gtcalcboard.wire_anim.disabled");

    private final String translationKey;

    WireAnimationMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public String getDisplayName() {
        return Component.translatable(translationKey).getString();
    }

    public WireAnimationMode next() {
        WireAnimationMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
