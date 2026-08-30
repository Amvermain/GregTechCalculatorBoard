package com.gtceu.calcboard.api.event;

import net.neoforged.bus.api.Event;

/**
 * Base NeoForge event for catalog lifecycle transitions (Recipes, Addons, Multiblock structures).
 */
public abstract class CatalogLifecycleEvent extends Event {

    public CatalogLifecycleEvent() {
    }

    public static void clearListeners() {
        // No-op in NeoForge event system
    }

    /**
     * Fired when global recipe search indexing and capability matrices are fully baked.
     */
    public static class RecipesReady extends CatalogLifecycleEvent {
        private final int recipeCount;
        private final long elapsedMs;

        public RecipesReady() {
            this(0, 0);
        }

        public RecipesReady(int recipeCount, long elapsedMs) {
            this.recipeCount = recipeCount;
            this.elapsedMs = elapsedMs;
        }

        public int getRecipeCount() {
            return recipeCount;
        }

        public long getElapsedMs() {
            return elapsedMs;
        }
    }

    /**
     * Fired when machine hardware addons (hatches, coils, rotors, augments) are crawled and cached.
     */
    public static class AddonsReady extends CatalogLifecycleEvent {
        private final int addonCount;

        public AddonsReady() {
            this(0);
        }

        public AddonsReady(int addonCount) {
            this.addonCount = addonCount;
        }

        public int getAddonCount() {
            return addonCount;
        }
    }

    /**
     * Fired when GTCEu 3D multiblock structures and EMI fallback blueprints are fully indexed.
     */
    public static class MultiblocksReady extends CatalogLifecycleEvent {
        private final int structureCount;

        public MultiblocksReady() {
            this(0);
        }

        public MultiblocksReady(int structureCount) {
            this.structureCount = structureCount;
        }

        public int getStructureCount() {
            return structureCount;
        }
    }
}
