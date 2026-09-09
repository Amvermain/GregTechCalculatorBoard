package com.gtceu.calcboard.api.spi.extension;

import com.gtceu.calcboard.api.model.RecipeNode;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Provider interface for interactive booster controls, catalysts, and oxidizer cycles.
 */
public interface IBoosterProvider extends IModExtension {

    default boolean supportsBoosterControl(RecipeNode node) {
        return false;
    }

    default Component getBoosterDisplayComponent(RecipeNode node) {
        return null;
    }

    default void cycleBooster(RecipeNode node, int direction) {
    }

    default void syncBoosterInputs(RecipeNode node) {
    }

    default int getBoosterBackgroundColor(RecipeNode node, boolean isHovered) {
        return isHovered ? 0xFF2A303C : 0xFF1E222D;
    }

    default int getBoosterBorderColor(RecipeNode node, boolean isHovered) {
        return isHovered ? 0xFF6B7B96 : 0xFF353C4D;
    }

    default int getBoosterTextColor(RecipeNode node, boolean isHovered) {
        return 0xFFFFFFFF;
    }

    default void buildBoosterTooltip(RecipeNode node, List<Component> tooltip) {
    }
}
