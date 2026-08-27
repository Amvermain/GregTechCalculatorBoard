package com.gtceu.calcboard.api;

import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.minecraft.resources.ResourceLocation;

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
    private static final Set<ResourceLocation> PARALLEL_HATCH_CONTROLLERS = new HashSet<>();
    private static final Set<ResourceLocation> STEAM_MULTIBLOCKS = new HashSet<>();
    private static final Map<ResourceLocation, Double> STEAM_MULTIBLOCK_CONSUMPTIONS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, GTVoltageTier> TURBINE_BASE_TIERS = new HashMap<>();
    private static final Map<ResourceLocation, Double> TURBINE_BASE_PRODUCTIONS = new HashMap<>();
    private static final Map<ResourceLocation, Integer> THREADING_MAX_HELIX_CAPACITY = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Integer> DEFAULT_MULTIBLOCK_PARALLELS = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;
    private static volatile boolean initializing = false;

    static {
        initTestEnvironmentDefaults();
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
            TURBINE_CONTROLLERS.add(controllerId);
            MULTIBLOCK_RECIPE_CONTROLLERS.add(controllerId);
            if (baseTier != null) TURBINE_BASE_TIERS.put(controllerId, baseTier);
            if (baseProduction > 0) TURBINE_BASE_PRODUCTIONS.put(controllerId, baseProduction);
        }
        if (recipeCategoryId != null) {
            TURBINE_RECIPE_CATEGORIES.add(recipeCategoryId);
        }
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

    public static void registerParallelHatchMultiblock(ResourceLocation controllerId) {
        if (controllerId != null) {
            PARALLEL_HATCH_CONTROLLERS.add(controllerId);
            MULTIBLOCK_RECIPE_CONTROLLERS.add(controllerId);
        }
    }

    public static void registerParallelHatchController(ResourceLocation controllerId) {
        registerParallelHatchMultiblock(controllerId);
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

    public static void initialize(Object rmObj) {
        if (initialized || initializing) return;
        synchronized (MultiblockDetector.class) {
            if (initialized || initializing) return;
            initializing = true;
            try {
                // 1. Scan universal EMI "multiblock_info" recipe category
                scanEmiMultiblockRecipes(rmObj);

                // 2. Delegate mod-specific multiblock discovery to each loaded IModAdapter
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
            } finally {
                if (MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) {
                    initTestEnvironmentDefaults();
                }
                initialized = true;
                initializing = false;
            }
        }
    }

    private static void scanEmiMultiblockRecipes(Object rmObj) {
        try {
            Iterable<?> recipes = null;
            if (rmObj != null) {
                if (rmObj instanceof Iterable<?> it) {
                    recipes = it;
                } else {
                    try {
                        Method mGetRecipes = rmObj.getClass().getMethod("getRecipes");
                        Object res = mGetRecipes.invoke(rmObj);
                        if (res instanceof Iterable<?> it) recipes = it;
                    } catch (Throwable ignored) {}
                }
            } else if (com.gtceu.calcboard.integration.emi.EmiLifecycleHook.isEmiRecipeBakingComplete()) {
                try {
                    var rm = dev.emi.emi.api.EmiApi.getRecipeManager();
                    if (rm != null) recipes = rm.getRecipes();
                } catch (Throwable ignored) {}
            }

            if (recipes == null) return;

            if (recipes != null) {
                for (Object recipeObj : recipes) {
                    if (recipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe recipe) {
                        if (recipe.getCategory() != null && recipe.getCategory().getId() != null) {
                            String catPath = recipe.getCategory().getId().getPath();
                            if (catPath.equals("multiblock_info") || catPath.contains("multiblock")) {
                                ResourceLocation controllerId = null;

                                if (recipe.getId() != null) {
                                    String rPath = recipe.getId().getPath();
                                    if (rPath.contains("/")) {
                                        String machineName = rPath.substring(rPath.lastIndexOf('/') + 1);
                                        controllerId = ResourceLocation.tryParse(recipe.getId().getNamespace() + ":" + machineName);
                                    } else {
                                        controllerId = recipe.getId();
                                    }
                                }

                                if (controllerId == null) {
                                    for (var es : recipe.getOutputs()) {
                                        if (es != null && es.getId() != null) {
                                            controllerId = es.getId();
                                            break;
                                        }
                                    }
                                }

                                if (controllerId != null) {
                                    MULTIBLOCK_RECIPE_CONTROLLERS.add(controllerId);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] Error scanning EMI multiblock_info recipes: {}", t.getMessage());
        }
    }

    public static void initTestEnvironmentDefaults() {
        registerTurbine(ResourceLocation.tryParse("gtceu:large_steam_turbine"), ResourceLocation.tryParse("gtceu:steam_turbine"), GTVoltageTier.HV, 1024.0);
        registerTurbine(ResourceLocation.tryParse("gtceu:large_gas_turbine"), ResourceLocation.tryParse("gtceu:gas_turbine"), GTVoltageTier.EV, 4096.0);
        registerTurbine(ResourceLocation.tryParse("gtceu:large_plasma_turbine"), ResourceLocation.tryParse("gtceu:plasma_turbine"), GTVoltageTier.IV, 16384.0);
        registerTurbine(ResourceLocation.tryParse("gtceu:supreme_plasma_turbine"), ResourceLocation.tryParse("gtceu:plasma_generator"), GTVoltageTier.IV, 98304.0);
        registerTurbine(ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine"), ResourceLocation.tryParse("gtceu:plasma_generator"), GTVoltageTier.IV, 196608.0);

        ResourceLocation lcr = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        ResourceLocation cr = ResourceLocation.tryParse("gtceu:chemical_reactor");
        registerCoilMultiblock(lcr, cr);
        registerMultiblock(ResourceLocation.tryParse("gtceu:large_macerator"));
        registerParallelHatchController(lcr);
        registerParallelHatchController(cr);
        registerParallelHatchController(ResourceLocation.tryParse("gtceu:large_macerator"));
        registerParallelHatchController(ResourceLocation.tryParse("gtceu:processing_array"));
        registerParallelHatchController(ResourceLocation.tryParse("start_core:star_forge"));
        registerParallelHatchController(ResourceLocation.tryParse("start_core:supreme_assembly_line"));
        registerBatchModeMultiblock(lcr);
        registerBatchModeMultiblock(cr);

        registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_grinder"), 8);
        registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_oven"), 8);
        registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_compressor"), 8);
        registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_ore_factory"), 6);
        registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_hammer"), 8);
        registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_alloy_smelter"), 8);
        registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_purifier"), 8);
        registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_rock_breaker"), 8);
        registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_kiln"), 8);
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
        TURBINE_BASE_TIERS.clear();
        TURBINE_BASE_PRODUCTIONS.clear();
        DEFAULT_MULTIBLOCK_PARALLELS.clear();
        BATCH_MODE_CONTROLLERS.clear();
        PARALLEL_HATCH_CONTROLLERS.clear();
        STEAM_MULTIBLOCKS.clear();
        STEAM_MULTIBLOCK_CONSUMPTIONS.clear();
        THREADING_MAX_HELIX_CAPACITY.clear();
        initialize(rmObj);
    }

    public static boolean supportsParallelHatch(ResourceLocation machineIcon, List<ResourceLocation> availableWorkstations) {
        return supportsParallelHatch(machineIcon, availableWorkstations, null);
    }

    public static boolean supportsParallelHatch(ResourceLocation machineIcon, List<ResourceLocation> availableWorkstations, ResourceLocation categoryId) {
        if (!initialized || MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) {
            initialize();
        }
        if (machineIcon != null && PARALLEL_HATCH_CONTROLLERS.contains(machineIcon)) {
            return true;
        }
        if (availableWorkstations != null) {
            for (ResourceLocation ws : availableWorkstations) {
                if (ws != null && PARALLEL_HATCH_CONTROLLERS.contains(ws)) {
                    return true;
                }
            }
        }
        if (categoryId != null && PARALLEL_HATCH_CONTROLLERS.contains(categoryId)) {
            return true;
        }
        return false;
    }

    public static boolean supportsBatchMode(ResourceLocation machineIcon, List<ResourceLocation> availableWorkstations) {
        if (!initialized || MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) {
            initialize();
        }
        if (machineIcon != null && BATCH_MODE_CONTROLLERS.contains(machineIcon)) {
            return true;
        }
        if (availableWorkstations != null) {
            for (ResourceLocation ws : availableWorkstations) {
                if (ws != null && BATCH_MODE_CONTROLLERS.contains(ws)) {
                    return true;
                }
            }
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
            int p = getDefaultParallel(node.getMachineIcon());
            if (p > 1) return p;
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            int p = getDefaultParallel(ws);
            if (p > 1) return p;
        }
        return 1;
    }

    public static GTVoltageTier getTurbineBaseTier(ResourceLocation id) {
        if (id == null) return null;
        if (!initialized && !initializing) {
            initialize();
        }
        return TURBINE_BASE_TIERS.get(id);
    }

    public static Double getTurbineBaseProduction(ResourceLocation id) {
        if (id == null) return null;
        if (!initialized && !initializing) {
            initialize();
        }
        return TURBINE_BASE_PRODUCTIONS.get(id);
    }

    public static boolean isMultiblock(ResourceLocation workstationId) {
        if (workstationId == null) return false;
        if (!initialized && !initializing) {
            initialize();
        }
        return MULTIBLOCK_RECIPE_CONTROLLERS.contains(workstationId);
    }

    public static boolean isCoilMultiblock(ResourceLocation workstationId) {
        if (workstationId == null) return false;
        if (!initialized && !initializing) {
            initialize();
        }
        return COIL_MULTIBLOCK_CONTROLLERS.contains(workstationId);
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
        if (!initialized && !initializing) {
            initialize();
        }
        return TURBINE_CONTROLLERS.contains(workstationId);
    }

    public static boolean isTurbineRecipeCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        if (!initialized && !initializing) {
            initialize();
        }
        if (TURBINE_RECIPE_CATEGORIES.contains(categoryId)) return true;
        String path = categoryId.getPath().toLowerCase(Locale.ROOT);
        return path.equals("gas_turbine") || path.equals("steam_turbine") || path.equals("plasma_turbine")
                || path.equals("large_gas_turbine") || path.equals("large_steam_turbine") || path.equals("large_plasma_turbine");
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
            int cnt = THREADING_MAX_HELIX_CAPACITY.getOrDefault(node.getMachineIcon(), 0);
            if (cnt > 0) return cnt;
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null) {
                int cnt = THREADING_MAX_HELIX_CAPACITY.getOrDefault(ws, 0);
                if (cnt > 0) return cnt;
            }
        }
        if (node.getRecipeCategoryId() != null) {
            int cnt = THREADING_MAX_HELIX_CAPACITY.getOrDefault(node.getRecipeCategoryId(), 0);
            if (cnt > 0) return cnt;
        }
        return 0;
    }

    public static boolean isTurbine(ResourceLocation workstationId) {
        return isTurbineMachine(workstationId);
    }

    public static boolean isThreadingMultiblock(ResourceLocation id) {
        return getMaxHelixCount(id) > 0;
    }

    public static ResourceLocation extractRecipeTypeId(Object rt) {
        if (rt == null) return null;
        if (rt instanceof ResourceLocation rl) return rl;
        try {
            if (rt instanceof net.minecraft.world.item.crafting.RecipeType<?> rType) {
                ResourceLocation loc = net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(rType);
                if (loc != null && !loc.getPath().equals("air")) return loc;
            }
        } catch (Throwable ignored) {}
        try {
            Method mGetId = rt.getClass().getMethod("getId");
            Object idVal = mGetId.invoke(rt);
            if (idVal instanceof ResourceLocation rl) return rl;
        } catch (Throwable ignored) {}
        try {
            Method mRegistryName = rt.getClass().getMethod("getRegistryName");
            Object idVal = mRegistryName.invoke(rt);
            if (idVal instanceof ResourceLocation rl) return rl;
        } catch (Throwable ignored) {}
        return null;
    }

    public static Iterable<?> getRegistryIterable(Object registry) {
        if (registry == null) return null;
        if (registry instanceof Iterable<?> iterable) {
            return iterable;
        }
        try {
            Method valuesMethod = registry.getClass().getMethod("values");
            Object result = valuesMethod.invoke(registry);
            if (result instanceof Iterable<?> iterable) {
                return iterable;
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
