package com.gtceu.calcboard.api.event;

import net.minecraftforge.eventbus.ListenerList;
import net.minecraftforge.eventbus.api.Event;

/**
 * Base Forge event for catalog lifecycle transitions (Recipes, Addons, Multiblock structures).
 */
public abstract class CatalogLifecycleEvent extends Event {

    private static ListenerList LISTENER_LIST = new ListenerList();

    public CatalogLifecycleEvent() {
    }

    @Override
    public ListenerList getListenerList() {
        return LISTENER_LIST;
    }

    public static void clearListeners() {
        LISTENER_LIST = new ListenerList();
        RecipesReady.LISTENER_LIST = new ListenerList(LISTENER_LIST);
        AddonsReady.LISTENER_LIST = new ListenerList(LISTENER_LIST);
        MultiblocksReady.LISTENER_LIST = new ListenerList(LISTENER_LIST);
    }

    /**
     * Fired when global recipe search indexing and capability matrices are fully baked.
     */
    public static class RecipesReady extends CatalogLifecycleEvent {
        private static ListenerList LISTENER_LIST = new ListenerList(CatalogLifecycleEvent.LISTENER_LIST);
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

        @Override
        public ListenerList getListenerList() {
            return LISTENER_LIST;
        }
    }

    /**
     * Fired when machine hardware addons (hatches, coils, rotors, augments) are crawled and cached.
     */
    public static class AddonsReady extends CatalogLifecycleEvent {
        private static ListenerList LISTENER_LIST = new ListenerList(CatalogLifecycleEvent.LISTENER_LIST);
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

        @Override
        public ListenerList getListenerList() {
            return LISTENER_LIST;
        }
    }

    /**
     * Fired when GTCEu 3D multiblock structures and EMI fallback blueprints are fully indexed.
     */
    public static class MultiblocksReady extends CatalogLifecycleEvent {
        private static ListenerList LISTENER_LIST = new ListenerList(CatalogLifecycleEvent.LISTENER_LIST);
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

        @Override
        public ListenerList getListenerList() {
            return LISTENER_LIST;
        }
    }
}
