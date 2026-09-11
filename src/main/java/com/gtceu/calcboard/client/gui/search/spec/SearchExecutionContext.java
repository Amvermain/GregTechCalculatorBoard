package com.gtceu.calcboard.client.gui.search.spec;

import com.gtceu.calcboard.client.gui.dialog.RecipeSearchDialog.ContextualWireTarget;
import com.gtceu.calcboard.client.gui.search.RecipeFilterConfig;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Set;

/**
 * Immutable execution context encapsulating state and filter parameters for recipe search.
 */
public record SearchExecutionContext(
        ContextualWireTarget contextualWireTarget,
        boolean showFavoritesOnly,
        Set<ResourceLocation> favoriteRecipeIds,
        RecipeFilterConfig filterConfig,
        boolean isTutorialActive
) {
    public SearchExecutionContext {
        if (favoriteRecipeIds == null) {
            favoriteRecipeIds = Collections.emptySet();
        }
    }

    public boolean hasWireContext() {
        return contextualWireTarget != null && contextualWireTarget.sourceStack != null;
    }
}
