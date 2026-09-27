package com.gtceu.calcboard.api.model;

import java.util.Objects;

/**
 * Lightweight immutable fingerprint of the currently loaded recipe dataset.
 * Used to detect whether recipe indexing can be completely skipped between world/server transitions.
 */
public record RecipeFingerprint(String viewerId, int recipeCount, long contentHash) {

    public static final RecipeFingerprint EMPTY = new RecipeFingerprint("", 0, 0L);

    public boolean isEmpty() {
        return recipeCount <= 0 || viewerId == null || viewerId.isEmpty();
    }

    public boolean matches(RecipeFingerprint other) {
        if (other == null || this.isEmpty() || other.isEmpty()) {
            return false;
        }
        return this.recipeCount == other.recipeCount
                && this.contentHash == other.contentHash
                && Objects.equals(this.viewerId, other.viewerId);
    }
}
