package com.gtceu.calcboard.api.spi.viewer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry and coordinator for active {@link IRecipeViewerBridge} implementations.
 */
public final class RecipeViewerBridgeRegistry {

    private static final List<IRecipeViewerBridge> BRIDGES = new CopyOnWriteArrayList<>();
    private static final Map<String, IRecipeViewerBridge> BRIDGE_MAP = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    private RecipeViewerBridgeRegistry() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        // Discover EMI bridge
        try {
            Class<?> emiBridge = Class.forName("com.gtceu.calcboard.integration.emi.EmiRecipeViewerBridge");
            Method m = emiBridge.getMethod("getInstance");
            Object inst = m.invoke(null);
            if (inst instanceof IRecipeViewerBridge bridge) {
                register(bridge);
            }
        } catch (Throwable ignored) {}

        // Discover JEI bridge
        try {
            Class<?> jeiBridge = Class.forName("com.gtceu.calcboard.integration.jei.JeiRecipeViewerBridge");
            Method m = jeiBridge.getMethod("getInstance");
            Object inst = m.invoke(null);
            if (inst instanceof IRecipeViewerBridge bridge) {
                register(bridge);
            }
        } catch (Throwable ignored) {}
    }

    public static synchronized void register(IRecipeViewerBridge bridge) {
        if (bridge == null) return;
        BRIDGES.removeIf(b -> b.getViewerId().equals(bridge.getViewerId()));
        BRIDGES.add(bridge);
        BRIDGE_MAP.put(bridge.getViewerId(), bridge);
    }

    public static List<IRecipeViewerBridge> getActiveBridges() {
        init();
        List<IRecipeViewerBridge> active = new ArrayList<>();
        for (IRecipeViewerBridge b : BRIDGES) {
            if (b.isAvailable()) {
                active.add(b);
            }
        }
        return Collections.unmodifiableList(active);
    }

    public static IRecipeViewerBridge getActiveBridge() {
        init();
        for (IRecipeViewerBridge b : BRIDGES) {
            if (b.isAvailable()) {
                return b;
            }
        }
        return null;
    }

    public static IRecipeViewerBridge getBridge(String viewerId) {
        init();
        return BRIDGE_MAP.get(viewerId);
    }

    public static boolean isAnyRecipeBakingComplete() {
        init();
        for (IRecipeViewerBridge b : BRIDGES) {
            if (b.isAvailable() && b.isRecipeBakingComplete()) {
                return true;
            }
        }
        return false;
    }

    public static void invalidateAllTextCaches() {
        for (IRecipeViewerBridge b : getActiveBridges()) {
            b.invalidateTextCaches();
        }
    }
}
