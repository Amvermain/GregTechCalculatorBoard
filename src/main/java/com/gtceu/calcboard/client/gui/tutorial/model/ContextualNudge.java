package com.gtceu.calcboard.client.gui.tutorial.model;

import net.minecraft.network.chat.Component;

/**
 * Encapsulates contextual mini-hint parameters.
 */
public record ContextualNudge(
    String nudgeId,
    NudgeTriggerType triggerType,
    Component message,
    String targetShortcutKey,
    String relatedChapterId
) {}
