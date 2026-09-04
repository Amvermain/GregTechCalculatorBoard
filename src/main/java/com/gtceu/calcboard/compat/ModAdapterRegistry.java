package com.gtceu.calcboard.compat;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.create.CreateModAdapter;
import com.gtceu.calcboard.compat.createnewage.CreateNewAgeModAdapter;
import com.gtceu.calcboard.compat.greate.GreateModAdapter;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.start.StarTModAdapter;
import com.gtceu.calcboard.compat.systeams.SysteamsModAdapter;
import com.gtceu.calcboard.compat.thermal.ThermalModAdapter;
import com.gtceu.calcboard.compat.vanilla.VanillaModAdapter;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central registry and router for mod compatibility adapters.
 */
public class ModAdapterRegistry {

    private static final List<IModAdapter> ADAPTERS = new CopyOnWriteArrayList<>();
    private static final Map<String, IModAdapter> ADAPTER_MAP = new ConcurrentHashMap<>();
    private static final IModAdapter FALLBACK_ADAPTER = new VanillaModAdapter();
    private static boolean initialized = false;

    static {
        init();
    }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        // Register built-in adapters
        register(new SysteamsModAdapter());      // Priority 110 (intercepts Systeams boilers & steam dynamos)
        register(new StarTModAdapter());         // Priority 105 (intercepts Star Technology GCC, SPT/NPT, Threading)
        register(new CreateNewAgeModAdapter());  // Priority 105 (intercepts Create: New Age motors & generator coils)
        register(new ThermalModAdapter());       // Priority 100
        register(new GTCEuModAdapter());         // Priority 100
        register(new GreateModAdapter());        // Priority 95 (intercepts Greate tiered kinetic machinery)
        register(new CreateModAdapter());        // Priority 90
        register(FALLBACK_ADAPTER);               // Priority 0

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

    public static IModAdapter getAdapterForModId(String modId) {
        init();
        return ADAPTER_MAP.getOrDefault(modId, FALLBACK_ADAPTER);
    }

    public static IModAdapter getAdapterForCategory(ResourceLocation categoryId) {
        init();
        if (categoryId == null) return FALLBACK_ADAPTER;

        for (IModAdapter a : ADAPTERS) {
            if (a.isLoaded() && a.handlesCategory(categoryId)) {
                return a;
            }
        }
        return FALLBACK_ADAPTER;
    }

    public static IModAdapter getAdapterForNode(RecipeNode node) {
        init();
        if (node == null) return FALLBACK_ADAPTER;

        for (IModAdapter a : ADAPTERS) {
            if (a.isLoaded() && a.handlesNode(node)) {
                return a;
            }
        }
        return FALLBACK_ADAPTER;
    }

    public static IModAdapter getFallbackAdapter() {
        return FALLBACK_ADAPTER;
    }

    public static boolean isCategorySupported(ResourceLocation categoryId) {
        init();
        if (categoryId == null) return false;
        if ("gtcalcboard".equals(categoryId.getNamespace()) && "kinetic_generation".equals(categoryId.getPath())) {
            return true;
        }

        for (IModAdapter a : ADAPTERS) {
            if (a.isGenericFallback()) continue;
            if (a.isLoaded() && a.handlesCategory(categoryId)) {
                return true;
            }
        }
        return FALLBACK_ADAPTER.handlesCategory(categoryId);
    }

    public static boolean isRecipeSupported(String modId, ResourceLocation categoryId) {
        if (categoryId != null && isCategorySupported(categoryId)) {
            return true;
        }
        if (modId == null || modId.isEmpty()) return false;
        IModAdapter adapter = getAdapterForModId(modId);
        return adapter != null && !adapter.isGenericFallback() && adapter.isLoaded();
    }

    public static com.gtceu.calcboard.api.bom.PartCategory classifyBOMPart(ResourceLocation itemId) {
        init();
        if (itemId == null) return com.gtceu.calcboard.api.bom.PartCategory.OTHER;
        for (IModAdapter a : ADAPTERS) {
            if (a.isLoaded()) {
                com.gtceu.calcboard.api.bom.PartCategory cat = a.classifyBOMPart(itemId);
                if (cat != null) {
                    return cat;
                }
            }
        }
        return com.gtceu.calcboard.api.bom.PartCategory.OTHER;
    }

    public static void accumulateStructureSlots(
            ResourceLocation itemId,
            com.gtceu.calcboard.api.bom.PartCategory category,
            int amount,
            com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.StructureSlotCounts slots
    ) {
        init();
        if (itemId == null || slots == null) return;
        for (IModAdapter a : ADAPTERS) {
            if (a.isLoaded()) {
                a.accumulateStructureSlots(itemId, category, amount, slots);
            }
        }
    }
}

