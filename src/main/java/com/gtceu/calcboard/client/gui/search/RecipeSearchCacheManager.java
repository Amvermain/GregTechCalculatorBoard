package com.gtceu.calcboard.client.gui.search;

import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.SearchableRecipe;
import com.gtceu.calcboard.client.gui.widget.FavoritesDockWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the static global recipe cache, background indexing lifecycles,
 * and recipe favorite tracking.
 */
public final class RecipeSearchCacheManager {

    public record RecipeLoadingProgress(int currentPhase, int totalPhases, String phaseKey, String detail) {}

    private static final List<SearchableRecipe> GLOBAL_RECIPES = Collections.synchronizedList(new ArrayList<>());
    private static final List<Runnable> ON_COMPLETE_CALLBACKS = Collections.synchronizedList(new ArrayList<>());
    private static final List<Runnable> FAVORITES_LISTENERS = new CopyOnWriteArrayList<>();

    private static volatile boolean GLOBAL_CACHED = false;
    private static volatile boolean IS_CACHING = false;
    private static volatile long GLOBAL_VERSION = 0;
    private static volatile RecipeLoadingProgress CACHING_PROGRESS = new RecipeLoadingProgress(1, 4, "gui.gtcalcboard.loading_recipe_phase.1", "");

    private RecipeSearchCacheManager() {}

    public static void registerFavoritesListener(Runnable listener) {
        if (listener != null) {
            FAVORITES_LISTENERS.add(listener);
        }
    }

    public static void unregisterFavoritesListener(Runnable listener) {
        FAVORITES_LISTENERS.remove(listener);
    }

    public static void notifyFavoritesChanged() {
        FavoritesDockWidget.clearCache();
        for (Runnable r : FAVORITES_LISTENERS) {
            try {
                r.run();
            } catch (Throwable ignored) {}
        }
    }

    public static Set<ResourceLocation> getFavoriteRecipeIds() {
        try {
            return com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter().getFavoriteRecipeIds();
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    public static boolean isRecipeFavorite(Object recipe) {
        if (recipe == null) return false;
        try {
            return com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter().isFavorite(recipe);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void toggleFavoriteRecipe(Object recipe) {
        if (recipe == null) return;
        try {
            com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter().toggleFavorite(recipe);
        } catch (Throwable ignored) {}
    }

    public static void clearGlobalCache() {
        GLOBAL_RECIPES.clear();
        ON_COMPLETE_CALLBACKS.clear();
        GLOBAL_CACHED = false;
        IS_CACHING = false;
        GLOBAL_VERSION++;
    }

    public static void invalidateCache() {
        clearGlobalCache();
    }

    public static boolean isGlobalCached() {
        return GLOBAL_CACHED;
    }

    public static long getGlobalVersion() {
        return GLOBAL_VERSION;
    }

    public static int getCachedRecipeCount() {
        return GLOBAL_RECIPES.size();
    }

    public static List<SearchableRecipe> getGlobalRecipes() {
        synchronized (GLOBAL_RECIPES) {
            return new ArrayList<>(GLOBAL_RECIPES);
        }
    }

    public static RecipeLoadingProgress getCachingProgress() {
        return CACHING_PROGRESS;
    }

    public static boolean isCaching() {
        return IS_CACHING;
    }

    public static void ensureGlobalRecipesCachedAsync(Runnable onComplete) {
        if (GLOBAL_CACHED) {
            if (onComplete != null) onComplete.run();
            return;
        }
        if (onComplete != null) {
            ON_COMPLETE_CALLBACKS.add(onComplete);
        }
        if (IS_CACHING) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level == null) {
            return;
        }

        var adapter = com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter();
        if (!adapter.isRecipeBakingComplete()) {
            adapter.runWhenReady(() -> ensureGlobalRecipesCachedAsync(null));
            return;
        }

        IS_CACHING = true;
        CACHING_PROGRESS = new RecipeLoadingProgress(1, 4, "gui.gtcalcboard.loading_recipe_phase.1", "Connecting to " + adapter.getViewerId().toUpperCase(Locale.ROOT) + " Recipe Manager");

        CompletableFuture.runAsync(() -> {
            try {
                long startNanos = System.nanoTime();
                List<SearchableRecipe> rawList = adapter.collectSearchableRecipes();

                CACHING_PROGRESS = new RecipeLoadingProgress(2, 4, "gui.gtcalcboard.loading_recipe_phase.2", rawList.size() + " Recipes");
                List<SearchableRecipe> tempList = new ArrayList<>(rawList);
                if (ModCompatHelper.isCreateLoaded() || ModCompatHelper.isCreateNewAgeLoaded() || ModCompatHelper.isCreateAdditionsLoaded()) {
                    tempList.addAll(com.gtceu.calcboard.compat.create.CreateModAdapter.getVirtualKineticSearchRecipes());
                }

                synchronized (GLOBAL_RECIPES) {
                    GLOBAL_RECIPES.clear();
                    GLOBAL_RECIPES.addAll(tempList);
                    GLOBAL_CACHED = true;
                    GLOBAL_VERSION++;
                }

                CACHING_PROGRESS = new RecipeLoadingProgress(3, 4, "gui.gtcalcboard.loading_recipe_phase.3", "Baking Machine Capabilities Matrix");
                RecipeFilterDialog.updateDiscoveredCategories(RecipeSearchEngine.discoverCategories(tempList));
                try {
                    com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix.getInstance().bake(null);
                } catch (Throwable ignored) {}
                CACHING_PROGRESS = new RecipeLoadingProgress(4, 4, "gui.gtcalcboard.loading_recipe_phase.3", "Completed");

                long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                        "[GTCalcBoard] [RecipeSearch] Indexed {} {} recipes in {}ms in background.",
                        tempList.size(), adapter.getViewerId().toUpperCase(Locale.ROOT), elapsedMs
                );

                try {
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                            new com.gtceu.calcboard.api.event.CatalogLifecycleEvent.RecipesReady(tempList.size(), elapsedMs)
                    );
                } catch (Throwable ignored) {}

                List<Runnable> callbacks;
                synchronized (ON_COMPLETE_CALLBACKS) {
                    callbacks = new ArrayList<>(ON_COMPLETE_CALLBACKS);
                    ON_COMPLETE_CALLBACKS.clear();
                }
                for (Runnable cb : callbacks) {
                    Minecraft.getInstance().execute(cb);
                }
            } catch (Throwable t) {
                IS_CACHING = false;
            } finally {
                IS_CACHING = false;
            }
        });
    }
}
