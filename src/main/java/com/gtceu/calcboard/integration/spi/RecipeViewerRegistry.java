package com.gtceu.calcboard.integration.spi;

import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.integration.vanilla.VanillaRecipeViewerAdapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Registry for managing and selecting the active IRecipeViewerAdapter based on priority and mod availability.
 * Adapters for optional mods (EMI, JEI) are lazily loaded via reflection to prevent NoClassDefFoundError.
 */
public final class RecipeViewerRegistry {

    private static final List<IRecipeViewerAdapter> ADAPTERS = new ArrayList<>();
    private static final IRecipeViewerAdapter FALLBACK_ADAPTER = new VanillaRecipeViewerAdapter();
    private static volatile boolean initialized = false;

    private RecipeViewerRegistry() {}

    public static synchronized void init() {
        if (initialized) return;
        ADAPTERS.clear();

        if (ModCompatHelper.isEmiLoaded()) {
            try {
                Class<?> emiAdapterClass = Class.forName("com.gtceu.calcboard.integration.emi.EmiRecipeViewerAdapter");
                IRecipeViewerAdapter emiAdapter = (IRecipeViewerAdapter) emiAdapterClass.getConstructor().newInstance();
                register(emiAdapter);
            } catch (Throwable t) {
                com.gtceu.calcboard.GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] Failed to instantiate EmiRecipeViewerAdapter: {}", t.getMessage());
            }
        }

        if (ModCompatHelper.isJeiLoaded()) {
            try {
                Class<?> jeiAdapterClass = Class.forName("com.gtceu.calcboard.integration.jei.JeiRecipeViewerAdapter");
                IRecipeViewerAdapter jeiAdapter = (IRecipeViewerAdapter) jeiAdapterClass.getConstructor().newInstance();
                register(jeiAdapter);
            } catch (Throwable t) {
                com.gtceu.calcboard.GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] Failed to instantiate JeiRecipeViewerAdapter: {}", t.getMessage());
            }
        }

        register(FALLBACK_ADAPTER);
        ADAPTERS.sort(Comparator.comparingInt(IRecipeViewerAdapter::getPriority).reversed());
        initialized = true;
    }

    public static synchronized void register(IRecipeViewerAdapter adapter) {
        if (adapter == null) return;
        ADAPTERS.removeIf(a -> a.getViewerId().equals(adapter.getViewerId()));
        ADAPTERS.add(adapter);
        ADAPTERS.sort(Comparator.comparingInt(IRecipeViewerAdapter::getPriority).reversed());
    }

    public static synchronized void unregister(String viewerId) {
        if (viewerId == null) return;
        ADAPTERS.removeIf(a -> a.getViewerId().equals(viewerId));
    }

    public static synchronized void reset() {
        initialized = false;
        ADAPTERS.clear();
        init();
    }

    public static IRecipeViewerAdapter getActiveAdapter() {
        if (!initialized) {
            init();
        }
        for (IRecipeViewerAdapter adapter : ADAPTERS) {
            try {
                if (adapter.isAvailable()) {
                    return adapter;
                }
            } catch (Throwable ignored) {}
        }
        return FALLBACK_ADAPTER;
    }

    public static List<IRecipeViewerAdapter> getAllAdapters() {
        if (!initialized) {
            init();
        }
        return List.copyOf(ADAPTERS);
    }

    public static boolean isAnySearchFocused() {
        if (!initialized) {
            init();
        }
        for (IRecipeViewerAdapter adapter : ADAPTERS) {
            try {
                if (adapter.isAvailable() && adapter.isSearchFieldFocused()) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static boolean tryAddHoveredRecipeToBoard(net.minecraft.client.gui.screens.Screen screen, double mouseX, double mouseY) {
        if (!initialized) {
            init();
        }
        for (IRecipeViewerAdapter adapter : ADAPTERS) {
            try {
                if (adapter.isAvailable() && adapter.tryAddHoveredRecipeToBoard(screen, mouseX, mouseY)) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }
}

