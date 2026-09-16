package com.gtceu.calcboard.client.gui.tutorial.model;

import com.gtceu.calcboard.api.storage.BoardPage;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Defines execution metadata and setup hook for a single step in a tutorial track or chapter.
 */
public record TutorialChapterStepDef(
    int stepNumber,
    String titleKey,
    String descKey,
    String resultDescKey,
    Consumer<BoardPage> setupAction
) {
    public TutorialChapterStepDef(int stepNumber, String titleKey, String descKey, Consumer<BoardPage> setupAction) {
        this(stepNumber, titleKey, descKey, descKey, setupAction);
    }

    public Component getTitle() {
        return Component.translatable(titleKey);
    }

    public Component getDescription() {
        return Component.translatable(descKey);
    }

    public Component getResultDescription() {
        return Component.translatable(resultDescKey != null ? resultDescKey : descKey);
    }
}
