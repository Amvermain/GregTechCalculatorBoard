package com.gtceu.calcboard.api.catalog;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.api.util.ModCompatHelper;

import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.spi.viewer.RecipeViewerBridgeRegistry;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal Registry and Query Facade for Multiblock Structures and Capabilities.
 * Mod-specific multiblock discovery is delegated to each respective IModAdapter.
 */
public class MultiblockDetector {

    private static final Set<ResourceLocation> MULTIBLOCK_RECIPE_CONTROLLERS = new HashSet<>();
    private static final Set<ResourceLocation> COIL_MULTIBLOCK_CONTROLLERS = new HashSet<>();
    private static final Set<ResourceLocation> COIL_RECIPE_CATEGORIES = new HashSet<>();
    private static final Set<ResourceLocation> TURBINE_CONTROLLERS = new HashSet<>();
    private static final Set<ResourceLocation> TURBINE_RECIPE_CATEGORIES = new HashSet<>();
    private static final Set<ResourceLocation> BATCH_MODE_CONTROLLERS = new HashSet<>();
    private static final Set<ResourceLocation> THROUGHPUT_BOOSTING_CONTROLLERS = new HashSet<>();
    private static final Set<ResourceLocation> BULK_PROCESSING_CONTROLLERS = new HashSet<>();
    private static final Set<ResourceLocation> OVERPRESSURE_CONTROLLERS = new HashSet<>();
    private static final Set<ResourceLocation> COIL_PARALLEL_CONTROLLERS = new HashSet<>();
    private static final Set<ResourceLocation> PARALLEL_HATCH_CONTROLLERS = new HashSet<>();
    private static final Set<ResourceLocation> LASER_HATCH_CONTROLLERS = new HashSet<>();
    private static final Set<ResourceLocation> STEAM_MULTIBLOCKS = new HashSet<>();
    private static final Map<ResourceLocation, Double> STEAM_MULTIBLOCK_CONSUMPTIONS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Integer> THREADING_MAX_HELIX_CAPACITY = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Integer> DEFAULT_MULTIBLOCK_PARALLELS = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;
    private static volatile boolean initializing = false;

    static {
        registerBaselineTurbines();
    }

    public static void registerMultiblock(ResourceLocation id) {
        if (id != null) {
            MULTIBLOCK_RECIPE_CONTROLLERS.add(id);
        }
    }

    public static void registerCoilMultiblock(ResourceLocation controllerId, ResourceLocation recipeCategoryId) {
        if (controllerId != null) {
            COIL_MULTIBLOCK_CONTROLLERS.add(controllerId);
            MULTIBLOCK_RECIPE_CONTROLLERS.add(controllerId);
        }
        if (recipeCategoryId != null) {
            COIL_RECIPE_CATEGORIES.add(recipeCategoryId);
        }
    }

    public static void registerTurbine(ResourceLocation controllerId, ResourceLocation recipeCategoryId, GTVoltageTier baseTier, double baseProduction) {
        if (controllerId != null) {
            IModAdapter adapter = ModAdapterRegistry.getAdapterForMod(controllerId.getNamespace());
            if (adapter != null && adapter.isCombustionEngine(controllerId)) {
                return;
            }
            TURBINE_CONTROLLERS.add(controllerId);
            MULTIBLOCK_RECIPE_CONTROLLERS.add(controllerId);
            TurbineCatalog.registerTurbineTierAndProduction(controllerId, baseTier, baseProduction);

            ResourceLocation alias = getTurbineAlias(controllerId);
            if (alias != null && !alias.equals(controllerId)) {
                TURBINE_CONTROLLERS.add(alias);
                MULTIBLOCK_RECIPE_CONTROLLERS.add(alias);
                TurbineCatalog.registerTurbineTierAndProduction(alias, baseTier, baseProduction);
            }
        }
        if (recipeCategoryId != null) {
            if (ResourceLocation.tryParse("gtceu:combustion_generator").equals(recipeCategoryId)) {
                return;
            }
            TURBINE_RECIPE_CATEGORIES.add(recipeCategoryId);
            TurbineCatalog.registerTurbineTierAndProduction(recipeCategoryId, baseTier, baseProduction);
        }
    }

    public static void registerBaselineTurbines() {
        TurbineCatalog.registerBaselineTurbines();
    }

    public static ResourceLocation getTurbineAlias(ResourceLocation id) {
        return TurbineCatalog.getTurbineAlias(id);
    }

    public static void registerBatchModeMultiblock(ResourceLocation controllerId) {
        if (controllerId != null) {
            BATCH_MODE_CONTROLLERS.add(controllerId);
            MULTIBLOCK_RECIPE_CONTROLLERS.add(controllerId);
        }
    }

    public static void registerBatchModeController(ResourceLocation controllerId) {
        registerBatchModeMultiblock(controllerId);
    }

    public static boolean supportsBatchMode(ResourceLocation controllerId) {
        return supportsBatchMode(controllerId, null);
    }

    public static void registerThroughputBoostingMultiblock(ResourceLocation controllerId) {
        if (controllerId != null) {
            THROUGHPUT_BOOSTING_CONTROLLERS.add(controllerId);
            MULTIBLOCK_RECIPE_CONTROLLERS.add(controllerId);
        }
    }

    public static boolean supportsThroughputBoosting(ResourceLocation controllerId) {
        if (controllerId == null) return false;
        ensureInitialized();
        if (THROUGHPUT_BOOSTING_CONTROLLERS.contains(controllerId)) return true;
        var defStruct = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(controllerId);
        if (defStruct != null && defStruct.supportsAbility("THROUGHPUT_BOOSTING")) {
            registerThroughputBoostingMultiblock(controllerId);
            return true;
        }
        return false;
    }

    public static void registerBulkProcessingMultiblock(ResourceLocation controllerId) {
        if (controllerId != null) {
            BULK_PROCESSING_CONTROLLERS.add(controllerId);
            MULTIBLOCK_RECIPE_CONTROLLERS.add(controllerId);
        }
    }

    public static boolean supportsBulkProcessing(ResourceLocation controllerId) {
        if (controllerId == null) return false;
        ensureInitialized();
        if (BULK_PROCESSING_CONTROLLERS.contains(controllerId)) return true;
        var defStruct = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(controllerId);
        if (defStruct != null && defStruct.supportsAbility("BULK_PROCESSING")) {
            registerBulkProcessingMultiblock(controllerId);
            return true;
        }
        return false;
    }

    public static void registerOverpressureMultiblock(ResourceLocation controllerId) {
        if (controllerId != null) {
            OVERPRESSURE_CONTROLLERS.add(controllerId);
        }
    }

    public static boolean supportsOverpressure(ResourceLocation controllerId) {
        if (controllerId == null) return false;
        ensureInitialized();
        if (OVERPRESSURE_CONTROLLERS.contains(controllerId)) return true;
        var defStruct = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(controllerId);
        if (defStruct != null && defStruct.supportsAbility("OVERPRESSURE")) {
            registerOverpressureMultiblock(controllerId);
            return true;
        }
        return false;
    }

    public static void registerCoilParallelMultiblock(ResourceLocation controllerId) {
        if (controllerId != null) {
            COIL_PARALLEL_CONTROLLERS.add(controllerId);
            MULTIBLOCK_RECIPE_CONTROLLERS.add(controllerId);
        }
    }

    public static boolean isCoilParallelMultiblock(ResourceLocation controllerId) {
        if (controllerId == null) return false;
        ensureInitialized();
        if (COIL_PARALLEL_CONTROLLERS.contains(controllerId)) return true;
        var defStruct = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(controllerId);
        if (defStruct != null && defStruct.supportsAbility("COIL_PARALLEL")) {
            registerCoilParallelMultiblock(controllerId);
            return true;
        }
        return false;
    }

    public static boolean isCoilParallelMultiblock(RecipeNode node) {
        if (node == null) return false;
        if (node.getMachineIcon() != null && isCoilParallelMultiblock(node.getMachineIcon())) return true;
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && isCoilParallelMultiblock(ws)) return true;
        }
        return false;
    }

    private static final Set<ResourceLocation> STEAM_ORE_FACTORIES = Set.of(
            ResourceLocation.tryParse("gtceu:steam_ore_factory"),
            ResourceLocation.tryParse("gtceu:bronze_steam_ore_factory"),
            ResourceLocation.tryParse("gtceu:steel_steam_ore_factory")
    );

    public static boolean isSteamOreFactory(RecipeNode node) {
        if (node == null) return false;
        if (node.getMachineIcon() != null && STEAM_ORE_FACTORIES.contains(node.getMachineIcon())) return true;
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && STEAM_ORE_FACTORIES.contains(ws)) return true;
        }
        return false;
    }

    public static void registerParallelHatchMultiblock(ResourceLocation controllerId) {
        if (controllerId != null) {
            PARALLEL_HATCH_CONTROLLERS.add(controllerId);
            MULTIBLOCK_RECIPE_CONTROLLERS.add(controllerId);
        }
    }

    public static void registerParallelHatchController(ResourceLocation controllerId) {
        registerParallelHatchMultiblock(controllerId);
    }

    public static void registerLaserHatchMultiblock(ResourceLocation controllerId) {
        if (controllerId != null) {
            LASER_HATCH_CONTROLLERS.add(controllerId);
            MULTIBLOCK_RECIPE_CONTROLLERS.add(controllerId);
        }
    }

    public static void registerLaserHatchController(ResourceLocation controllerId) {
        registerLaserHatchMultiblock(controllerId);
    }

    public static void registerSteamMultiblock(ResourceLocation controllerId, int defaultParallel) {
        registerSteamMultiblock(controllerId, defaultParallel, 64.0);
    }

    public static void registerSteamMultiblock(ResourceLocation controllerId, int defaultParallel, double steamDrainRate) {
        if (controllerId != null) {
            STEAM_MULTIBLOCKS.add(controllerId);
            MULTIBLOCK_RECIPE_CONTROLLERS.add(controllerId);
            DEFAULT_MULTIBLOCK_PARALLELS.put(controllerId, defaultParallel);
            if (steamDrainRate > 0) {
                STEAM_MULTIBLOCK_CONSUMPTIONS.put(controllerId, steamDrainRate);
            }
        }
    }

    public static void registerThreadingMultiblock(ResourceLocation controllerId, int maxHelixCount) {
        if (controllerId != null && maxHelixCount > 0) {
            THREADING_MAX_HELIX_CAPACITY.put(controllerId, maxHelixCount);
            MULTIBLOCK_RECIPE_CONTROLLERS.add(controllerId);
        }
    }

    public static void registerDefaultParallel(ResourceLocation controllerId, int defaultParallel) {
        if (controllerId != null && defaultParallel > 1) {
            DEFAULT_MULTIBLOCK_PARALLELS.put(controllerId, defaultParallel);
        }
    }

    public static boolean isSteamMultiblock(ResourceLocation id) {
        if (id == null) return false;
        return STEAM_MULTIBLOCKS.contains(id);
    }

    public static double getSteamConsumption(ResourceLocation id, SteamMode mode) {
        if (id == null) return 32.0;
        Double customRate = STEAM_MULTIBLOCK_CONSUMPTIONS.get(id);
        if (customRate != null && customRate > 0) {
            return (mode == SteamMode.LOW_PRESSURE) ? customRate / 2.0 : customRate;
        }
        return (mode == SteamMode.LOW_PRESSURE) ? 32.0 : 64.0;
    }

    public static double getSteamMultiblockConsumption(ResourceLocation id, SteamMode mode) {
        return getSteamConsumption(id, mode);
    }

    public static void initialize() {
        initialize(null);
    }

    public static java.util.concurrent.CompletableFuture<Void> initializeAsync() {
        if (initialized || initializing) return java.util.concurrent.CompletableFuture.completedFuture(null);
        return java.util.concurrent.CompletableFuture.runAsync(MultiblockDetector::initialize, net.minecraft.Util.backgroundExecutor());
    }

    public static void initialize(Object rmObj) {
        if (initialized || initializing) return;
        synchronized (MultiblockDetector.class) {
            if (initialized || initializing) return;
            initializing = true;
            try {
                initializeStructureCatalog();
                scanEmiMultiblockRecipes(rmObj);
                scanAdapterMultiblocks(rmObj);
            } finally {
                initialized = true;
                initializing = false;
            }
        }
    }

    private static void initializeStructureCatalog() {
        try {
            com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.initialize();
        } catch (Throwable ignored) {}
    }

    private static void scanAdapterMultiblocks(Object rmObj) {
        for (IModAdapter adapter : ModAdapterRegistry.getAllLoadedAdapters()) {
            try {
                adapter.scanMultiblocks(rmObj);
            } catch (Throwable t) {
                com.gtceu.calcboard.GregTechCalcBoard.LOGGER.warn(
                        "[GTCalcBoard] [MultiblockDetector] Adapter '{}' scanMultiblocks failed: {}",
                        adapter.getModId(), t.getMessage()
                );
            }
        }
    }

    private static void scanEmiMultiblockRecipes(Object rmObj) {
        RecipeViewerBridgeRegistry.getActiveBridges().forEach(b -> b.discoverMultiblockControllers(MULTIBLOCK_RECIPE_CONTROLLERS::add));
    }

    public static void reinitialize() {
        reinitialize(null);
    }

    public static void reinitialize(Object rmObj) {
        initialized = false;
        MULTIBLOCK_RECIPE_CONTROLLERS.clear();
        COIL_MULTIBLOCK_CONTROLLERS.clear();
        COIL_RECIPE_CATEGORIES.clear();
        TURBINE_CONTROLLERS.clear();
        TURBINE_RECIPE_CATEGORIES.clear();
        TurbineCatalog.clear();
        DEFAULT_MULTIBLOCK_PARALLELS.clear();
        BATCH_MODE_CONTROLLERS.clear();
        THROUGHPUT_BOOSTING_CONTROLLERS.clear();
        BULK_PROCESSING_CONTROLLERS.clear();
        OVERPRESSURE_CONTROLLERS.clear();
        PARALLEL_HATCH_CONTROLLERS.clear();
        LASER_HATCH_CONTROLLERS.clear();
        STEAM_MULTIBLOCKS.clear();
        STEAM_MULTIBLOCK_CONSUMPTIONS.clear();
        THREADING_MAX_HELIX_CAPACITY.clear();
        initialize(rmObj);
    }

    private static void ensureInitialized() {
        if (!initialized || MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) {
            initialize();
        }
    }

    public static boolean supportsLaserHatch(ResourceLocation machineIcon, List<ResourceLocation> availableWorkstations) {
        ensureInitialized();
        if (machineIcon != null && LASER_HATCH_CONTROLLERS.contains(machineIcon)) {
            return true;
        }
        if (availableWorkstations == null) return false;
        for (ResourceLocation ws : availableWorkstations) {
            if (ws != null && LASER_HATCH_CONTROLLERS.contains(ws)) {
                return true;
            }
        }
        return false;
    }

    public static boolean supportsTurbineRotor(ResourceLocation machineIcon, List<ResourceLocation> availableWorkstations) {
        if (machineIcon != null) {
            IModAdapter adapter = ModAdapterRegistry.getAdapterForMod(machineIcon.getNamespace());
            if (adapter != null && adapter.isCombustionEngine(machineIcon)) {
                return false;
            }
        }
        if (isTurbineMachine(machineIcon)) return true;
        if (availableWorkstations == null) return false;
        for (ResourceLocation ws : availableWorkstations) {
            if (ws != null) {
                IModAdapter adapter = ModAdapterRegistry.getAdapterForMod(ws.getNamespace());
                if (adapter != null && adapter.isCombustionEngine(ws)) {
                    continue;
                }
                if (isTurbineMachine(ws)) return true;
            }
        }
        return false;
    }

    public static boolean supportsParallelHatch(ResourceLocation controllerId) {
        return supportsParallelHatch(controllerId, null, null);
    }

    public static boolean supportsParallelHatch(ResourceLocation machineIcon, List<ResourceLocation> availableWorkstations) {
        return supportsParallelHatch(machineIcon, availableWorkstations, null);
    }

    public static boolean supportsParallelHatch(ResourceLocation machineIcon, List<ResourceLocation> availableWorkstations, ResourceLocation categoryId) {
        ensureInitialized();
        if (machineIcon != null) {
            return checkParallelHatch(machineIcon);
        }
        if (availableWorkstations != null) {
            for (ResourceLocation ws : availableWorkstations) {
                if (checkParallelHatch(ws)) return true;
            }
        }
        return checkParallelHatchCategory(categoryId);
    }

    private static boolean checkParallelHatch(ResourceLocation id) {
        if (id == null || isTurbineMachine(id)) return false;
        if (PARALLEL_HATCH_CONTROLLERS.contains(id)) return true;
        var defStruct = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(id);
        if (defStruct != null && defStruct.supportsAbility("PARALLEL_HATCH")) {
            registerParallelHatchController(id);
            return true;
        }
        return false;
    }

    private static boolean checkParallelHatchCategory(ResourceLocation categoryId) {
        if (categoryId == null || isTurbineRecipeCategory(categoryId)) return false;
        if (PARALLEL_HATCH_CONTROLLERS.contains(categoryId)) return true;
        var defStruct = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(categoryId);
        if (defStruct != null && defStruct.supportsAbility("PARALLEL_HATCH")) {
            registerParallelHatchController(categoryId);
            return true;
        }
        return false;
    }

    public static boolean supportsBatchMode(ResourceLocation machineIcon, List<ResourceLocation> availableWorkstations) {
        ensureInitialized();
        if (machineIcon != null) {
            return checkBatchMode(machineIcon);
        }
        if (availableWorkstations != null) {
            for (ResourceLocation ws : availableWorkstations) {
                if (checkBatchMode(ws)) return true;
            }
        }
        return false;
    }

    private static boolean checkBatchMode(ResourceLocation id) {
        if (id == null) return false;
        if (BATCH_MODE_CONTROLLERS.contains(id)) return true;
        var defStruct = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(id);
        if (defStruct != null && defStruct.supportsAbility("BATCH_MODE")) {
            registerBatchModeMultiblock(id);
            return true;
        }
        return false;
    }

    public static int getDefaultParallel(ResourceLocation id) {
        if (id == null) return 1;
        if (!initialized && !initializing) {
            initialize();
        }
        return DEFAULT_MULTIBLOCK_PARALLELS.getOrDefault(id, 1);
    }

    public static int getDefaultParallel(RecipeNode node) {
        if (node == null) return 1;
        if (node.getMachineIcon() != null) {
            return getDefaultParallel(node.getMachineIcon());
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            int p = getDefaultParallel(ws);
            if (p > 1) return p;
        }
        return 1;
    }

    public static GTVoltageTier getTurbineBaseTier(RecipeNode node) {
        return TurbineCatalog.getTurbineBaseTier(node);
    }

    public static double getTurbineBaseProduction(RecipeNode node) {
        return TurbineCatalog.getTurbineBaseProduction(node);
    }

    public static boolean requiresMinimumBaseTier(ResourceLocation turbineId) {
        return TurbineCatalog.requiresMinimumBaseTier(turbineId);
    }

    public static GTVoltageTier getTurbineBaseTier(ResourceLocation id) {
        ensureInitialized();
        return TurbineCatalog.getTurbineBaseTier(id);
    }

    public static Double getTurbineBaseProduction(ResourceLocation id) {
        ensureInitialized();
        return TurbineCatalog.getTurbineBaseProduction(id);
    }

    public static boolean isMultiblock(ResourceLocation workstationId) {
        if (workstationId == null) return false;
        if (!initialized && !initializing) {
            initialize();
        }
        if (MULTIBLOCK_RECIPE_CONTROLLERS.contains(workstationId)) return true;
        if (COIL_MULTIBLOCK_CONTROLLERS.contains(workstationId)) return true;
        if (PARALLEL_HATCH_CONTROLLERS.contains(workstationId)) return true;
        if (THROUGHPUT_BOOSTING_CONTROLLERS.contains(workstationId)) return true;
        if (BATCH_MODE_CONTROLLERS.contains(workstationId)) return true;
        if (STEAM_MULTIBLOCKS.contains(workstationId)) return true;
        if (THREADING_MAX_HELIX_CAPACITY.containsKey(workstationId)) return true;
        if (MultiblockStructureCatalog.getStructure(workstationId) != null) return true;
        IModAdapter adapter = ModAdapterRegistry.getAdapterForMod(workstationId.getNamespace());
        if (adapter != null && adapter.isMultiblock(workstationId)) return true;
        String path = workstationId.getPath().toLowerCase(Locale.ROOT);
        return path.endsWith("fusion_reactor") || path.startsWith("auxiliary_fusion") || path.startsWith("auxiliary_booster");
    }

    public static boolean isCoilMultiblock(ResourceLocation workstationId) {
        if (workstationId == null) return false;
        if (!initialized && !initializing) {
            initialize();
        }
        if (COIL_MULTIBLOCK_CONTROLLERS.contains(workstationId)) return true;

        var defStruct = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(workstationId);
        if (defStruct != null && defStruct.supportsAbility("HEATING_COILS") && defStruct.coilSlotCount() > 0) {
            COIL_MULTIBLOCK_CONTROLLERS.add(workstationId);
            return true;
        }

        IModAdapter adapter = ModAdapterRegistry.getAdapterForMod(workstationId.getNamespace());
        if (adapter != null && adapter.isCoilMultiblock(workstationId)) {
            COIL_MULTIBLOCK_CONTROLLERS.add(workstationId);
            return true;
        }

        return false;
    }

    public static void registerCoilCategory(ResourceLocation categoryId) {
        if (categoryId != null) {
            COIL_RECIPE_CATEGORIES.add(categoryId);
        }
    }

    public static void registerTurbineCategory(ResourceLocation categoryId) {
        if (categoryId != null) {
            TURBINE_RECIPE_CATEGORIES.add(categoryId);
        }
    }

    public static boolean isCoilRecipeCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        if (!initialized && !initializing) {
            initialize();
        }
        return COIL_RECIPE_CATEGORIES.contains(categoryId);
    }

    public static boolean isTurbineMachine(ResourceLocation workstationId) {
        if (workstationId == null) return false;
        IModAdapter adapter = ModAdapterRegistry.getAdapterForMod(workstationId.getNamespace());
        if (adapter != null && adapter.isCombustionEngine(workstationId)) {
            return false;
        }
        if (!initialized && !initializing) {
            initialize();
        }
        if (TURBINE_CONTROLLERS.contains(workstationId)) return true;

        ResourceLocation alias = getTurbineAlias(workstationId);
        if (alias != null && TURBINE_CONTROLLERS.contains(alias)) {
            registerTurbine(workstationId, null, null, 0.0);
            return true;
        }

        var def = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(workstationId);
        if (def == null && alias != null) {
            def = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(alias);
        }
        if (def != null && def.supportsAbility("ROTOR_HOLDER")) {
            if (!isCoilMultiblock(workstationId)) {
                if (adapter != null && adapter.hasTurbineSignature(workstationId, alias)) {
                    registerTurbine(workstationId, null, null, 0.0);
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isTurbineRecipeCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        if (ResourceLocation.tryParse("gtceu:combustion_generator").equals(categoryId)) {
            return false;
        }
        if (!initialized && !initializing) {
            initialize();
        }
        return TURBINE_RECIPE_CATEGORIES.contains(categoryId);
    }

    public static Set<ResourceLocation> getAllCoilControllers() {
        return java.util.Collections.unmodifiableSet(COIL_MULTIBLOCK_CONTROLLERS);
    }

    public static Set<ResourceLocation> getAllCoilCategories() {
        return java.util.Collections.unmodifiableSet(COIL_RECIPE_CATEGORIES);
    }

    public static Set<ResourceLocation> getAllTurbineControllers() {
        return java.util.Collections.unmodifiableSet(TURBINE_CONTROLLERS);
    }

    public static Set<ResourceLocation> getAllTurbineCategories() {
        return java.util.Collections.unmodifiableSet(TURBINE_RECIPE_CATEGORIES);
    }

    public static Set<ResourceLocation> getAllMultiblockControllers() {
        return java.util.Collections.unmodifiableSet(MULTIBLOCK_RECIPE_CONTROLLERS);
    }

    public static int getMaxHelixCount(ResourceLocation id) {
        if (id == null) return 0;
        if (!initialized && !initializing) {
            initialize();
        }
        return THREADING_MAX_HELIX_CAPACITY.getOrDefault(id, 0);
    }

    public static int getMaxHelixCount(RecipeNode node) {
        if (node == null) return 0;
        if (!initialized && !initializing) {
            initialize();
        }
        if (node.getMachineIcon() != null) {
            return THREADING_MAX_HELIX_CAPACITY.getOrDefault(node.getMachineIcon(), 0);
        }
        return 0;
    }

    public static boolean isTurbine(ResourceLocation workstationId) {
        return isTurbineMachine(workstationId);
    }

    public static boolean isThreadingMultiblock(ResourceLocation id) {
        return getMaxHelixCount(id) > 0;
    }

    public static boolean inspectAndRegisterMachine(ResourceLocation id, Object def, ResourceLocation recipeCategoryId) {
        return MultiblockMachineInspector.inspectAndRegisterMachine(id, def, recipeCategoryId);
    }

    public static ResourceLocation extractRecipeTypeId(Object rt) {
        return MultiblockMachineInspector.extractRecipeTypeId(rt);
    }

    public static Iterable<?> getRegistryIterable(Object registry) {
        return MultiblockMachineInspector.getRegistryIterable(registry);
    }
}




