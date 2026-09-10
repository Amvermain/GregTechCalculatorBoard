package com.gtceu.calcboard.api.bom;

import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.spi.viewer.RecipeViewerBridgeRegistry;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Catalog indexer for GTCEu Multiblock Structure blueprints.
 * Deductively extracts 3D shape blocks from GTCEu's MultiblockMachineDefinition registry
 * and falls back to EMI multiblock recipes where appropriate.
 */
public class MultiblockStructureCatalog {

    private static final Map<ResourceLocation, MultiblockStructureDef> STRUCTURES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, List<MultiblockStructureDef>> STRUCTURE_VARIANTS = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> NEGATIVE_CACHE = ConcurrentHashMap.newKeySet();
    private static volatile boolean initialized = false;
    private static volatile boolean initializing = false;

    public static void clear() {
        STRUCTURES.clear();
        STRUCTURE_VARIANTS.clear();
        NEGATIVE_CACHE.clear();
        initialized = false;
        initializing = false;
    }

    public static void invalidateTextCaches() {
        clear();
        for (IModAdapter adapter : ModAdapterRegistry.getAllLoadedAdapters()) {
            adapter.invalidateTextCaches();
        }
    }

    public static void clearStructure(ResourceLocation id) {
        if (id == null) return;
        STRUCTURES.remove(id);
        STRUCTURE_VARIANTS.remove(id);
        NEGATIVE_CACHE.remove(id);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void initialize() {
        if (initialized) return;
        synchronized (MultiblockStructureCatalog.class) {
            if (initialized || initializing) return;
            initializing = true;
            try {
                scanAdapterMultiblockStructures();
                scanEmiMultiblockInfo();
            } catch (Throwable ignored) {
            } finally {
                initialized = true;
                initializing = false;
                postMultiblocksReadyEvent();
            }
        }
    }

    private static void scanAdapterMultiblockStructures() {
        for (IModAdapter adapter : ModAdapterRegistry.getAllLoadedAdapters()) {
            try {
                adapter.scanMultiblockStructures();
            } catch (Throwable t) {
                com.gtceu.calcboard.GregTechCalcBoard.LOGGER.warn(
                        "[GTCalcBoard] [MultiblockStructureCatalog] Adapter '{}' scanMultiblockStructures failed: {}",
                        adapter.getModId(), t.getMessage()
                );
            }
        }
    }

    private static void scanEmiMultiblockInfo() {
        if (!com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) return;
        if (com.gtceu.calcboard.integration.emi.EmiLifecycleHook.isEmiRecipeBakingComplete()) {
            scanEmiMultiblockRecipes();
        } else {
            com.gtceu.calcboard.integration.emi.EmiLifecycleHook.runWhenEmiReady(MultiblockStructureCatalog::scanEmiMultiblockRecipes);
        }
    }

    private static void postMultiblocksReadyEvent() {
        try {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new com.gtceu.calcboard.api.event.CatalogLifecycleEvent.MultiblocksReady(STRUCTURES.size())
            );
        } catch (Throwable ignored) {}
    }

    public static java.util.concurrent.CompletableFuture<Void> initializeAsync() {
        if (initialized || initializing) return java.util.concurrent.CompletableFuture.completedFuture(null);
        return java.util.concurrent.CompletableFuture.runAsync(MultiblockStructureCatalog::initialize, net.minecraft.Util.backgroundExecutor());
    }

    public static void registerManualStructure(MultiblockStructureDef def) {
        if (def != null && def.controllerId() != null) {
            NEGATIVE_CACHE.remove(def.controllerId());
            STRUCTURES.put(def.controllerId(), def);
            STRUCTURE_VARIANTS.computeIfAbsent(def.controllerId(), k -> new ArrayList<>()).add(def);
        }
    }

    public static void registerStructure(MultiblockStructureDef canonicalDef, List<MultiblockStructureDef> variants) {
        if (canonicalDef == null || canonicalDef.controllerId() == null) return;
        ResourceLocation controllerId = canonicalDef.controllerId();
        NEGATIVE_CACHE.remove(controllerId);
        STRUCTURES.put(controllerId, canonicalDef);
        if (variants != null && !variants.isEmpty()) {
            STRUCTURE_VARIANTS.put(controllerId, variants);
        }
        ResourceLocation infoAlias = ResourceLocation.tryParse(controllerId.getNamespace() + ":multiblock_info/" + controllerId.getPath());
        if (infoAlias != null) {
            NEGATIVE_CACHE.remove(infoAlias);
            STRUCTURES.put(infoAlias, canonicalDef);
            if (variants != null && !variants.isEmpty()) {
                STRUCTURE_VARIANTS.put(infoAlias, variants);
            }
        }
    }

    public static MultiblockStructureDef getStructureCached(ResourceLocation id) {
        if (id == null) return null;
        MultiblockStructureDef def = STRUCTURES.get(id);
        if (def != null) return def;

        String path = id.getPath();
        if (path.contains("/")) {
            String machineName = path.substring(path.lastIndexOf('/') + 1);
            ResourceLocation stripped = ResourceLocation.tryParse(id.getNamespace() + ":" + machineName);
            if (stripped != null) {
                return STRUCTURES.get(stripped);
            }
        }
        return null;
    }

    public static MultiblockStructureDef getStructure(ResourceLocation id) {
        if (!initialized && !initializing) {
            initializeAsync();
        }
        if (id == null) return null;
        MultiblockStructureDef def = getStructureCached(id);
        if (def != null) return def;
        if (NEGATIVE_CACHE.contains(id)) return null;

        MultiblockStructureDef scanned = scanStructureFromAdapters(id);
        if (scanned != null) {
            STRUCTURES.put(id, scanned);
            return scanned;
        }
        NEGATIVE_CACHE.add(id);
        return null;
    }

    private static MultiblockStructureDef scanStructureFromAdapters(ResourceLocation id) {
        for (IModAdapter adapter : ModAdapterRegistry.getAllLoadedAdapters()) {
            try {
                MultiblockStructureDef scanned = adapter.scanMultiblockStructure(id);
                if (scanned != null) return scanned;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Finds the best-fitting multiblock shape variant that satisfies the required port counts.
     */
    public static MultiblockStructureDef getMatchingStructure(ResourceLocation id, int requiredFluidOut, int requiredItemOut, int requiredFluidIn, int requiredItemIn) {
        if (!initialized && !initializing) {
            initializeAsync();
        }
        if (id == null) return null;
        List<MultiblockStructureDef> variants = STRUCTURE_VARIANTS.get(id);
        if (variants == null || variants.isEmpty()) {
            String path = id.getPath();
            if (path.contains("/")) {
                String machineName = path.substring(path.lastIndexOf('/') + 1);
                ResourceLocation stripped = ResourceLocation.tryParse(id.getNamespace() + ":" + machineName);
                if (stripped != null) {
                    variants = STRUCTURE_VARIANTS.get(stripped);
                }
            }
        }

        if (variants == null || variants.isEmpty()) {
            return getStructure(id);
        }

        MultiblockStructureDef bestFit = null;
        for (MultiblockStructureDef def : variants) {
            if (def.outputHatchSlotCount() >= requiredFluidOut
                    && def.outputBusSlotCount() >= requiredItemOut
                    && def.inputHatchSlotCount() >= requiredFluidIn
                    && def.inputBusSlotCount() >= requiredItemIn) {
                bestFit = def;
                break;
            }
        }

        if (bestFit == null) {
            bestFit = variants.get(variants.size() - 1);
        }

        return bestFit;
    }

    private static void scanEmiMultiblockRecipes() {
        RecipeViewerBridgeRegistry.getActiveBridges().forEach(b -> b.discoverMultiblockStructures(def -> {
            if (def != null && def.controllerId() != null) {
                MultiblockStructureDef existing = STRUCTURES.get(def.controllerId());
                STRUCTURES.put(def.controllerId(), mergeStructureDefs(existing, def));
            }
        }));
    }

    private static MultiblockStructureDef mergeStructureDefs(MultiblockStructureDef existing, MultiblockStructureDef emiDef) {
        if (existing == null) return emiDef;
        Set<String> mergedAbilities = new HashSet<>(existing.allowedAbilities());
        mergedAbilities.addAll(emiDef.allowedAbilities());
        Set<ResourceLocation> mergedCandidates = new HashSet<>(existing.candidateBlocks());
        mergedCandidates.addAll(emiDef.candidateBlocks());
        List<MultiblockStructurePart> parts = existing.parts().size() >= emiDef.parts().size() ? existing.parts() : emiDef.parts();
        return new MultiblockStructureDef(
                existing.controllerId(),
                existing.controllerName(),
                parts,
                Math.max(existing.coilSlotCount(), emiDef.coilSlotCount()),
                Math.max(existing.energyHatchSlotCount(), emiDef.energyHatchSlotCount()),
                Math.max(existing.inputBusSlotCount(), emiDef.inputBusSlotCount()),
                Math.max(existing.outputBusSlotCount(), emiDef.outputBusSlotCount()),
                Math.max(existing.inputHatchSlotCount(), emiDef.inputHatchSlotCount()),
                Math.max(existing.outputHatchSlotCount(), emiDef.outputHatchSlotCount()),
                Math.max(existing.maintenanceSlotCount(), emiDef.maintenanceSlotCount()),
                Collections.unmodifiableSet(mergedAbilities),
                Collections.unmodifiableSet(mergedCandidates)
        );
    }

    public static class StructureSlotCounts {
        public int coilSlots = 0;
        public int energyHatchSlots = 0;
        public int inputBusSlots = 0;
        public int outputBusSlots = 0;
        public int inputHatchSlots = 0;
        public int outputHatchSlots = 0;
        public int maintenanceSlots = 0;
    }

    public static PartCategory classifyPart(ResourceLocation itemId) {
        if (itemId == null) return PartCategory.OTHER;
        return ModAdapterRegistry.classifyBOMPart(itemId);
    }

    public static String formatMachineName(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    public static Map<ResourceLocation, MultiblockStructureDef> getAllStructures() {
        if (!initialized || STRUCTURES.isEmpty()) {
            initialize();
        }
        return Collections.unmodifiableMap(STRUCTURES);
    }
}



