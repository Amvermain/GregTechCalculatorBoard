package com.gtceu.calcboard.client.gui.tutorial.model;

import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.client.gui.tutorial.TutorialStep;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Definition contract for modular topic-based academy chapters.
 */
public interface ITutorialChapter {
    String getChapterId();

    Component getTitle();

    Component getDescription();

    ResourceLocation getIconTexture();

    List<TutorialStep> getSteps();

    void onChapterEnter(BoardPage tutorialPage);
}
