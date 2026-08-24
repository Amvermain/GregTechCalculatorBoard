package com.gtceu.calcboard.api.property;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Immutable UI model for a declarative node card badge.
 *
 * @param text The badge label to display (e.g. "[⚛ Mk1]").
 * @param textColor The ARGB font color.
 * @param bgColor The ARGB background fill color (or 0 for transparent).
 * @param outlineColor The ARGB border outline color.
 * @param tooltipLines Optional tooltip lines shown when hovering over this badge.
 * @param isWarning True if this badge represents a critical operational warning/requirement failure.
 * @param onClick Optional action triggered when clicking this badge.
 */
public record NodeBadge(
        String text,
        int textColor,
        int bgColor,
        int outlineColor,
        List<Component> tooltipLines,
        boolean isWarning,
        Runnable onClick
) {
    public NodeBadge(String text, int textColor, int bgColor, int outlineColor, List<Component> tooltipLines, boolean isWarning) {
        this(text, textColor, bgColor, outlineColor, tooltipLines, isWarning, null);
    }

    public NodeBadge(String text, int textColor, int bgColor, int outlineColor, List<Component> tooltipLines) {
        this(text, textColor, bgColor, outlineColor, tooltipLines, false, null);
    }

    public NodeBadge(String text, int textColor, int outlineColor) {
        this(text, textColor, 0xAA000000, outlineColor, List.of(), false, null);
    }
}
