package com.gtceu.calcboard.api.spi;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.extension.IModExtension;
import com.gtceu.calcboard.api.spi.extension.IMultiblockBOMProvider;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pure domain Service Provider Interface registry and router for mod compatibility adapters.
 * Isolated from concrete compatibility implementations via bootstrap discovery.
 */
public class ModAdapterRegistry {

    private static final List<IModAdapter> ADAPTERS = new CopyOnWriteArrayList<>();
    private static final Map<String, IModAdapter> ADAPTER_MAP = new ConcurrentHashMap<>();
    private static IModAdapter fallbackAdapter = new DefaultFallbackAdapter();
    private static boolean initialized = false;

    static {
        init();
    }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        // Register default fallback
        register(fallbackAdapter);

        // Discovers built-in compatibility adapters without static coupling to compat
        try {
            Class<?> bootstrap = Class.forName("com.gtceu.calcboard.compat.ModAdapterBootstrap");
            Method m = bootstrap.getMethod("registerBuiltinAdapters");
            m.invoke(null);
        } catch (Throwable ignored) {}

        // Fire extension event for third-party adapters & KubeJS
        try {
            var event = new com.gtceu.calcboard.api.event.ModAdapterRegisterEvent();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
            for (IModAdapter customAdapter : event.getRegisteredAdapters()) {
                register(customAdapter);
            }
        } catch (Throwable ignored) {}
    }

    public static synchronized void reset() {
        ADAPTERS.clear();
        ADAPTER_MAP.clear();
        initialized = false;
    }

    public static synchronized void register(IModAdapter adapter) {
        if (adapter == null) return;
        ADAPTERS.removeIf(a -> a.getModId().equals(adapter.getModId()));
        ADAPTERS.add(adapter);
        ADAPTERS.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        ADAPTER_MAP.put(adapter.getModId(), adapter);
        try {
            adapter.initialize();
        } catch (Throwable ignored) {}
    }

    public static List<IModAdapter> getAllLoadedAdapters() {
        init();
        List<IModAdapter> loaded = new ArrayList<>();
        for (IModAdapter a : ADAPTERS) {
            if (a.isLoaded()) {
                loaded.add(a);
            }
        }
        return Collections.unmodifiableList(loaded);
    }

    public static List<IModAdapter> getAdapters() {
        return getAllLoadedAdapters();
    }

    public static IModAdapter getFallbackAdapter() {
        return fallbackAdapter;
    }

    public static void setFallbackAdapter(IModAdapter fallback) {
        if (fallback != null) {
            fallbackAdapter = fallback;
        }
    }

    public static IModAdapter getAdapterForModId(String modId) {
        init();
        return ADAPTER_MAP.getOrDefault(modId, fallbackAdapter);
    }

    public static IModAdapter getAdapterForMod(String modId) {
        return getAdapterForModId(modId);
    }

    public static boolean isCategorySupported(ResourceLocation categoryId) {
        init();
        if (categoryId == null) return false;
        for (IModAdapter a : ADAPTERS) {
            if (a.isGenericFallback()) continue;
            if (a.isLoaded() && a.handlesCategory(categoryId)) return true;
        }
        return "minecraft".equals(categoryId.getNamespace());
    }

    public static boolean isRecipeSupported(String modId, ResourceLocation categoryId) {
        return isCategorySupported(categoryId);
    }

    public static PartCategory classifyBOMPart(ResourceLocation itemId) {
        init();
        for (IModAdapter a : ADAPTERS) {
            if (a.isLoaded() && a.hasExtension(IMultiblockBOMProvider.class)) {
                PartCategory cat = a.classifyBOMPart(itemId);
                if (cat != null && cat != PartCategory.OTHER) return cat;
            }
        }
        return PartCategory.OTHER;
    }

    public static IModAdapter getAdapterForCategory(ResourceLocation categoryId) {
        init();
        if (categoryId == null) return fallbackAdapter;

        for (IModAdapter a : ADAPTERS) {
            if (a.isLoaded() && a.handlesCategory(categoryId)) {
                return a;
            }
        }
        return fallbackAdapter;
    }

    public static IModAdapter getAdapterForNode(RecipeNode node) {
        if (node == null) return fallbackAdapter;
        IModAdapter cached = node.getCachedModAdapter();
        if (cached != null) return cached;
        init();

        for (IModAdapter a : ADAPTERS) {
            if (a.isLoaded() && a.handlesNode(node)) {
                node.setCachedModAdapter(a);
                return a;
            }
        }
        node.setCachedModAdapter(fallbackAdapter);
        return fallbackAdapter;
    }

    public static <T extends IModExtension> Optional<T> findExtension(RecipeNode node, Class<T> extensionClass) {
        IModAdapter adapter = getAdapterForNode(node);
        return adapter.getExtension(extensionClass);
    }

    public static <T extends IModExtension> Optional<T> getExtensionForMod(String modId, Class<T> extensionClass) {
        IModAdapter adapter = getAdapterForModId(modId);
        return adapter.getExtension(extensionClass);
    }

    public static <T extends IModExtension> List<T> getAllExtensions(Class<T> extensionClass) {
        init();
        List<T> result = new ArrayList<>();
        for (IModAdapter adapter : ADAPTERS) {
            if (adapter.isLoaded()) {
                adapter.getExtension(extensionClass).ifPresent(result::add);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static void accumulateStructureSlots(ResourceLocation itemId, PartCategory category, int amount, MultiblockStructureCatalog.StructureSlotCounts slots) {
        init();
        for (IModAdapter a : ADAPTERS) {
            if (a.isLoaded() && a.hasExtension(IMultiblockBOMProvider.class)) {
                a.accumulateStructureSlots(itemId, category, amount, slots);
            }
        }
    }

    private static class DefaultFallbackAdapter implements IModAdapter {
        @Override
        public String getModId() {
            return "calcboard_fallback";
        }

        @Override
        public int getPriority() {
            return -1000;
        }

        @Override
        public boolean isGenericFallback() {
            return true;
        }

        @Override
        public boolean isLoaded() {
            return true;
        }

        @Override
        public boolean handlesCategory(ResourceLocation categoryId) {
            return false;
        }

        @Override
        public boolean handlesNode(RecipeNode node) {
            return true;
        }
    }
}
