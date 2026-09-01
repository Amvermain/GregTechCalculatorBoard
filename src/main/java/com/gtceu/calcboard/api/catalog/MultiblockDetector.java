package com.gtceu.calcboard.api.catalog;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.api.util.ModCompatHelper;

import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
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
    private static final Set<ResourceLocation> PARALLEL_HATCH_CONTROLLERS = new HashSet<>();
    private static final Set<ResourceLocation> LASER_HATCH_CONTROLLERS = new HashSet<>();
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
            if (baseTier != null) TURBINE_BASE_TIERS.put(recipeCategoryId, baseTier);
            if (baseProduction > 0) TURBINE_BASE_PRODUCTIONS.put(recipeCategoryId, baseProduction);
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
                initTestEnvironmentDefaults();
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
        if (!com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
            return;
        }
        try {
            EmiMultiblockScanner.scan(rmObj);
        } catch (Throwable t) {
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] Error scanning EMI multiblock_info recipes: {}", t.getMessage());
        }
    }

    private static class EmiMultiblockScanner {
        private static void scan(Object rmObj) {
            Object resolvedManager = resolveEmiRecipeManager(rmObj);
            if (resolvedManager == null) return;

            if (resolvedManager instanceof dev.emi.emi.api.recipe.EmiRecipeManager emiManager) {
                scanEmiManagerCategories(emiManager);
                return;
            }

            scanGenericRecipeCollection(resolvedManager);
        }

        private static Object resolveEmiRecipeManager(Object rmObj) {
            if (rmObj != null) return rmObj;
            if (!com.gtceu.calcboard.integration.emi.EmiLifecycleHook.isEmiRecipeBakingComplete()) return null;
            try {
                return dev.emi.emi.api.EmiApi.getRecipeManager();
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static void scanEmiManagerCategories(dev.emi.emi.api.recipe.EmiRecipeManager emiManager) {
            if (emiManager.getCategories() == null) return;
            for (dev.emi.emi.api.recipe.EmiRecipeCategory cat : emiManager.getCategories()) {
                if (cat == null || cat.getId() == null) continue;
                if (!isMultiblockCategory(cat.getId().getPath())) continue;

                List<dev.emi.emi.api.recipe.EmiRecipe> mbRecipes = emiManager.getRecipes(cat);
                if (mbRecipes != null) {
                    mbRecipes.forEach(EmiMultiblockScanner::processEmiMultiblockRecipe);
                }
            }
        }

        private static void scanGenericRecipeCollection(Object rmObj) {
            Iterable<?> recipes = extractRecipeIterable(rmObj);
            if (recipes == null) return;

            for (Object recipeObj : recipes) {
                if (recipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe recipe) {
                    processRecipeIfMultiblock(recipe);
                }
            }
        }

        private static Iterable<?> extractRecipeIterable(Object rmObj) {
            if (rmObj instanceof Iterable<?> it) return it;
            try {
                Method mGetRecipes = rmObj.getClass().getMethod("getRecipes");
                Object res = mGetRecipes.invoke(rmObj);
                if (res instanceof Iterable<?> it) return it;
            } catch (Throwable ignored) {}
            return null;
        }

        private static void processRecipeIfMultiblock(dev.emi.emi.api.recipe.EmiRecipe recipe) {
            if (recipe.getCategory() == null || recipe.getCategory().getId() == null) return;
            if (isMultiblockCategory(recipe.getCategory().getId().getPath())) {
                processEmiMultiblockRecipe(recipe);
            }
        }

        private static boolean isMultiblockCategory(String path) {
            return path.equals("multiblock_info") || path.contains("multiblock");
        }

        private static void processEmiMultiblockRecipe(dev.emi.emi.api.recipe.EmiRecipe recipe) {
            if (recipe == null) return;
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

            if (controllerId == null && recipe.getOutputs() != null) {
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

    public static void initTestEnvironmentDefaults() {
        initTurbineDefaults();
        initCoilDefaults();
        initStandardMultiblockDefaults();
        initSteamMultiblockDefaults();
        initThreadingMultiblockDefaults();
    }

    private static void initTurbineDefaults() {
        ResourceLocation lst1 = ResourceLocation.tryParse("gtceu:large_steam_turbine");
        ResourceLocation lst2 = ResourceLocation.tryParse("gtceu:steam_large_turbine");
        ResourceLocation lgt1 = ResourceLocation.tryParse("gtceu:large_gas_turbine");
        ResourceLocation lgt2 = ResourceLocation.tryParse("gtceu:gas_large_turbine");
        ResourceLocation lpt1 = ResourceLocation.tryParse("gtceu:large_plasma_turbine");
        ResourceLocation lpt2 = ResourceLocation.tryParse("gtceu:plasma_large_turbine");
        ResourceLocation spt = ResourceLocation.tryParse("gtceu:supreme_plasma_turbine");
        ResourceLocation sptStart = ResourceLocation.tryParse("start_core:supreme_plasma_turbine");
        ResourceLocation npt = ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine");
        ResourceLocation nptStart = ResourceLocation.tryParse("start_core:nyinsane_plasma_turbine");
        ResourceLocation plasmaGen = ResourceLocation.tryParse("gtceu:plasma_generator");

        ResourceLocation st = ResourceLocation.tryParse("gtceu:steam_turbine");
        ResourceLocation gt = ResourceLocation.tryParse("gtceu:gas_turbine");
        ResourceLocation pt = ResourceLocation.tryParse("gtceu:plasma_turbine");

        registerTurbine(lst1, st, GTVoltageTier.HV, 1024.0);
        registerTurbine(lst2, st, GTVoltageTier.HV, 1024.0);
        registerTurbine(null, ResourceLocation.tryParse("gtceu:steam_turbine_superheated"), GTVoltageTier.HV, 1024.0);
        registerTurbine(lgt1, gt, GTVoltageTier.EV, 4096.0);
        registerTurbine(lgt2, gt, GTVoltageTier.EV, 4096.0);
        registerTurbine(lpt1, pt, GTVoltageTier.IV, 16384.0);
        registerTurbine(lpt2, pt, GTVoltageTier.IV, 16384.0);
        registerTurbine(null, plasmaGen, GTVoltageTier.IV, 16384.0);
        registerTurbine(spt, null, GTVoltageTier.IV, 98304.0);
        registerTurbine(sptStart, null, GTVoltageTier.IV, 98304.0);
        registerTurbine(npt, null, GTVoltageTier.IV, 196608.0);
        registerTurbine(nptStart, null, GTVoltageTier.IV, 196608.0);

        registerLaserHatchController(spt);
        registerLaserHatchController(sptStart);
        registerLaserHatchController(npt);
        registerLaserHatchController(nptStart);
        registerDefaultParallel(spt, 6);
        registerDefaultParallel(sptStart, 6);
        registerDefaultParallel(npt, 12);
        registerDefaultParallel(nptStart, 12);
    }

    private static void initCoilDefaults() {
        ResourceLocation lcr = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        ResourceLocation ecr = ResourceLocation.tryParse("gtceu:extreme_chemical_reactor");
        ResourceLocation icr = ResourceLocation.tryParse("gtceu:incomprehensible_chemical_reactor");
        ResourceLocation cr = ResourceLocation.tryParse("gtceu:chemical_reactor");
        ResourceLocation ebf = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        ResourceLocation pyrolyse = ResourceLocation.tryParse("gtceu:pyrolyse_oven");
        ResourceLocation cracker = ResourceLocation.tryParse("gtceu:cracker");
        ResourceLocation multiSmelter = ResourceLocation.tryParse("gtceu:multi_smelter");

        registerCoilMultiblock(lcr, cr);
        registerCoilMultiblock(ecr, cr);
        registerCoilMultiblock(icr, cr);
        registerCoilMultiblock(ebf, ebf);
        registerCoilMultiblock(pyrolyse, pyrolyse);
        registerCoilMultiblock(cracker, cracker);
        registerCoilMultiblock(multiSmelter, multiSmelter);

        ResourceLocation rhf = ResourceLocation.tryParse("gtceu:mega_blast_furnace");
        ResourceLocation rhfAlt = ResourceLocation.tryParse("gtceu:rotary_hearth_furnace");
        ResourceLocation rhfStart = ResourceLocation.tryParse("start_core:rotary_hearth_furnace");
        ResourceLocation hif = ResourceLocation.tryParse("gtceu:hardened_industrial_furnace");
        ResourceLocation hifStart = ResourceLocation.tryParse("start_core:hardened_industrial_furnace");
        ResourceLocation chef = ResourceLocation.tryParse("gtceu:catalytic_hellfire_energized_furnace");
        ResourceLocation chefStart = ResourceLocation.tryParse("start_core:catalytic_hellfire_energized_furnace");

        registerCoilMultiblock(rhf, ebf);
        registerCoilMultiblock(rhfAlt, ebf);
        registerCoilMultiblock(rhfStart, ebf);
        registerCoilMultiblock(hif, ebf);
        registerCoilMultiblock(hifStart, ebf);
        registerCoilMultiblock(chef, ebf);
        registerCoilMultiblock(chefStart, ebf);
    }

    private static void initStandardMultiblockDefaults() {
        ResourceLocation lcr = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        ResourceLocation ecr = ResourceLocation.tryParse("gtceu:extreme_chemical_reactor");
        ResourceLocation icr = ResourceLocation.tryParse("gtceu:incomprehensible_chemical_reactor");
        ResourceLocation ebf = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        ResourceLocation rhf = ResourceLocation.tryParse("gtceu:mega_blast_furnace");
        ResourceLocation rhfAlt = ResourceLocation.tryParse("gtceu:rotary_hearth_furnace");
        ResourceLocation rhfStart = ResourceLocation.tryParse("start_core:rotary_hearth_furnace");
        ResourceLocation hif = ResourceLocation.tryParse("gtceu:hardened_industrial_furnace");
        ResourceLocation hifStart = ResourceLocation.tryParse("start_core:hardened_industrial_furnace");
        ResourceLocation chef = ResourceLocation.tryParse("gtceu:catalytic_hellfire_energized_furnace");
        ResourceLocation chefStart = ResourceLocation.tryParse("start_core:catalytic_hellfire_energized_furnace");
        ResourceLocation pyrolyse = ResourceLocation.tryParse("gtceu:pyrolyse_oven");
        ResourceLocation cracker = ResourceLocation.tryParse("gtceu:cracker");
        ResourceLocation multiSmelter = ResourceLocation.tryParse("gtceu:multi_smelter");

        registerMultiblock(lcr);
        registerMultiblock(ecr);
        registerMultiblock(icr);
        registerMultiblock(ebf);
        registerMultiblock(rhf);
        registerMultiblock(rhfAlt);
        registerMultiblock(rhfStart);
        registerMultiblock(hif);
        registerMultiblock(hifStart);
        registerMultiblock(chef);
        registerMultiblock(chefStart);
        registerMultiblock(pyrolyse);
        registerMultiblock(cracker);
        registerMultiblock(multiSmelter);
        registerMultiblock(ResourceLocation.tryParse("gtceu:large_macerator"));
        registerParallelHatchController(lcr);
        registerParallelHatchController(ecr);
        registerParallelHatchController(icr);
        registerParallelHatchController(rhf);
        registerParallelHatchController(rhfAlt);
        registerParallelHatchController(rhfStart);
        registerParallelHatchController(hif);
        registerParallelHatchController(hifStart);
        registerParallelHatchController(chef);
        registerParallelHatchController(chefStart);
        registerParallelHatchController(ResourceLocation.tryParse("gtceu:processing_array"));
        registerParallelHatchController(ResourceLocation.tryParse("start_core:star_forge"));
        registerParallelHatchController(ResourceLocation.tryParse("start_core:supreme_assembly_line"));
        registerBatchModeMultiblock(lcr);
        registerBatchModeMultiblock(ecr);
        registerBatchModeMultiblock(icr);
    }

    private static void initSteamMultiblockDefaults() {
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

    private static void initThreadingMultiblockDefaults() {
        registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:fermenting_arboreal_rejuvenation_monstrosity"), 8);
        registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:multithreaded_component_synthesis_forge"), 24);
        registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:aqueous_transformation_processing_center"), 8);
        registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:ascendant_engraving_matrix"), 9);
        registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:byteforce_unified_incomparable_logistics_depot"), 12);
        registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:electro_magnetic_material_ripper"), 10);
        registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:gravitational_compression_chamber"), 12);
        registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:material_annihilation_array"), 8);
        registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:molecular_inducing_xanadu"), 8);
        registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:subatomic_particle_lattice_isolation_terminal"), 12);
        registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:superior_particulate_isolation_nexus"), 8);
        registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:yielding_excression_advanced_seperation_transformator"), 8);
        registerThreadingMultiblock(ResourceLocation.tryParse("start:threading_processing_plant"), 8);
        registerThreadingMultiblock(ResourceLocation.tryParse("start_core:threading_processing_plant"), 8);

        for (String idStr : new String[]{
                "gtceu:fermenting_arboreal_rejuvenation_monstrosity",
                "gtceu:multithreaded_component_synthesis_forge",
                "gtceu:aqueous_transformation_processing_center",
                "gtceu:ascendant_engraving_matrix",
                "gtceu:byteforce_unified_incomparable_logistics_depot",
                "gtceu:electro_magnetic_material_ripper",
                "gtceu:gravitational_compression_chamber",
                "gtceu:material_annihilation_array",
                "gtceu:molecular_inducing_xanadu",
                "gtceu:subatomic_particle_lattice_isolation_terminal",
                "gtceu:superior_particulate_isolation_nexus",
                "gtceu:yielding_excression_advanced_seperation_transformator"
        }) {
            ResourceLocation rl = ResourceLocation.tryParse(idStr);
            registerMultiblock(rl);
            registerParallelHatchController(rl);
            registerBatchModeController(rl);
        }
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
        LASER_HATCH_CONTROLLERS.clear();
        STEAM_MULTIBLOCKS.clear();
        STEAM_MULTIBLOCK_CONSUMPTIONS.clear();
        THREADING_MAX_HELIX_CAPACITY.clear();
        initTestEnvironmentDefaults();
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
        if (isTurbineMachine(machineIcon)) return true;
        if (availableWorkstations == null) return false;
        for (ResourceLocation ws : availableWorkstations) {
            if (ws != null && isTurbineMachine(ws)) return true;
        }
        return false;
    }

    public static boolean supportsParallelHatch(ResourceLocation machineIcon, List<ResourceLocation> availableWorkstations) {
        return supportsParallelHatch(machineIcon, availableWorkstations, null);
    }

    public static boolean supportsParallelHatch(ResourceLocation machineIcon, List<ResourceLocation> availableWorkstations, ResourceLocation categoryId) {
        ensureInitialized();
        if (checkParallelHatch(machineIcon)) return true;
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
        if (machineIcon != null && BATCH_MODE_CONTROLLERS.contains(machineIcon)) {
            return true;
        }
        if (availableWorkstations == null) return false;
        for (ResourceLocation ws : availableWorkstations) {
            if (ws != null && BATCH_MODE_CONTROLLERS.contains(ws)) {
                return true;
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
            return getDefaultParallel(node.getMachineIcon());
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            int p = getDefaultParallel(ws);
            if (p > 1) return p;
        }
        return 1;
    }

    public static GTVoltageTier getTurbineBaseTier(RecipeNode node) {
        if (node == null) return GTVoltageTier.HV;
        if (node.getMachineIcon() != null) {
            GTVoltageTier t = TURBINE_BASE_TIERS.get(node.getMachineIcon());
            if (t != null) return t;
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null) {
                GTVoltageTier t = TURBINE_BASE_TIERS.get(ws);
                if (t != null) return t;
            }
        }
        if (node.getRecipeCategoryId() != null) {
            GTVoltageTier t = TURBINE_BASE_TIERS.get(node.getRecipeCategoryId());
            if (t != null) return t;
        }
        return GTVoltageTier.HV;
    }

    public static boolean requiresMinimumBaseTier(ResourceLocation turbineId) {
        if (turbineId == null) return false;
        GTVoltageTier baseTier = getTurbineBaseTier(turbineId);
        return baseTier != null && baseTier.ordinal() >= GTVoltageTier.IV.ordinal();
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
        if (MULTIBLOCK_RECIPE_CONTROLLERS.contains(workstationId)) return true;
        String path = workstationId.getPath().toLowerCase(Locale.ROOT);
        return path.contains("fusion_reactor") || path.contains("auxiliary_fusion") || path.contains("auxiliary_booster");
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
        if (id == null) return false;
        if (def == null) {
            return registerFallbackFromCatalog(id);
        }

        Class<?> cls = def.getClass();
        Class<?> mCls = extractMachineClass(cls, def);
        boolean isMb = isMultiblockDefinition(cls, def, mCls, id);

        if (isMb) {
            registerMultiblock(id);
            detectAndRegisterCoilMultiblock(id, mCls, recipeCategoryId);
            detectAndRegisterParallelAndBatch(id, def, cls);
            detectAndRegisterLaserHatch(id);
            detectAndRegisterThreading(id, def, cls, mCls);
        }

        return isMb;
    }

    private static final Class<?> COIL_WORKABLE_CLS;
    private static final Class<?> THREADING_CAPABLE_CLS;
    private static final Class<?> GT_MODIFIERS_CLS;
    private static final Class<?> GT_REGISTRIES_CLS;
    private static final Field RECIPE_TYPES_FIELD;

    static {
        ClassLoader cl = MultiblockDetector.class.getClassLoader();
        Class<?> coilCls = null;
        try {
            coilCls = Class.forName("com.gregtechceu.gtceu.common.machine.multiblock.electric.CoilWorkableElectricMultiblockMachine", false, cl);
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        COIL_WORKABLE_CLS = coilCls;

        Class<?> threadCls = null;
        try {
            threadCls = Class.forName("com.startechnology.start_core.machine.threading.StarTThreadingCapableMachine", false, cl);
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        THREADING_CAPABLE_CLS = threadCls;

        Class<?> modCls = null;
        try {
            modCls = Class.forName("com.gregtechceu.gtceu.api.recipe.modifier.GTRecipeModifiers", false, cl);
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        GT_MODIFIERS_CLS = modCls;

        Class<?> gtRegs = null;
        Field rtField = null;
        try {
            gtRegs = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries", false, cl);
            rtField = gtRegs.getField("RECIPE_TYPES");
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        GT_REGISTRIES_CLS = gtRegs;
        RECIPE_TYPES_FIELD = rtField;
    }

    private static boolean registerFallbackFromCatalog(ResourceLocation id) {
        if (!isMultiblock(id) && MultiblockStructureCatalog.getStructure(id) == null) {
            return false;
        }
        registerMultiblock(id);
        var defStruct = MultiblockStructureCatalog.getStructure(id);
        if (defStruct != null && defStruct.supportsAbility("PARALLEL_HATCH")) {
            registerParallelHatchController(id);
        }
        return true;
    }

    private static Class<?> extractMachineClass(Class<?> cls, Object def) {
        try {
            Method mGetMachineClass = cls.getMethod("getMachineClass");
            mGetMachineClass.setAccessible(true);
            return (Class<?>) mGetMachineClass.invoke(def);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean isMultiblockDefinition(Class<?> cls, Object def, Class<?> mCls, ResourceLocation id) {
        String clsName = cls.getName().toLowerCase(Locale.ROOT);
        String simpleName = cls.getSimpleName().toLowerCase(Locale.ROOT);
        if (simpleName.contains("multiblock") || clsName.contains("multiblock")) {
            return true;
        }

        try {
            Method mIsMb = cls.getMethod("isMultiblock");
            mIsMb.setAccessible(true);
            Object res = mIsMb.invoke(def);
            if (res instanceof Boolean b && b) return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        if (mCls != null) {
            String mClsName = mCls.getName().toLowerCase(Locale.ROOT);
            if (mClsName.contains("multiblock") || mClsName.contains("controller")) {
                return true;
            }
        }

        return MultiblockStructureCatalog.getStructure(id) != null;
    }

    private static void detectAndRegisterCoilMultiblock(ResourceLocation id, Class<?> mCls, ResourceLocation recipeCategoryId) {
        if (isCoilMachineClass(mCls) || isCoilFromCatalog(id) || isCoilFromCategory(recipeCategoryId)) {
            registerCoilMultiblock(id, recipeCategoryId);
        }
    }

    private static boolean isCoilMachineClass(Class<?> mCls) {
        if (mCls == null || COIL_WORKABLE_CLS == null) return false;
        return COIL_WORKABLE_CLS.isAssignableFrom(mCls);
    }

    private static boolean isCoilFromCatalog(ResourceLocation id) {
        var defStruct = MultiblockStructureCatalog.getStructure(id);
        return defStruct != null && (defStruct.coilSlotCount() > 0 || defStruct.supportsAbility("HEATING_COILS"));
    }

    private static boolean isCoilFromCategory(ResourceLocation recipeCategoryId) {
        if (recipeCategoryId == null) return false;
        CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(recipeCategoryId);
        return cap != null && cap.canUseCoils();
    }

    private static void detectAndRegisterParallelAndBatch(ResourceLocation id, Object def, Class<?> cls) {
        if (isTurbineMachine(id)) return;

        boolean supportsParallel = hasParallelModifier(cls, def) || hasParallelFromCatalog(id);
        boolean supportsBatch = hasBatchModifier(cls, def);

        if (supportsParallel) registerParallelHatchController(id);
        if (supportsBatch) registerBatchModeController(id);
    }

    private static boolean hasParallelModifier(Class<?> cls, Object def) {
        return hasRecipeModifier(cls, def, "PARALLEL_HATCH");
    }

    private static boolean hasBatchModifier(Class<?> cls, Object def) {
        return hasRecipeModifier(cls, def, "BATCH_MODE");
    }

    private static boolean hasRecipeModifier(Class<?> cls, Object def, String targetName) {
        for (Method m : cls.getMethods()) {
            if (m.getParameterCount() != 0 || !isRecipeModifierGetter(m.getName())) continue;
            try {
                m.setAccessible(true);
                Object modifiers = m.invoke(def);
                if (modifiers != null && containsRecipeModifier(modifiers, targetName)) {
                    return true;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        return false;
    }

    private static boolean isRecipeModifierGetter(String name) {
        return name.equals("getRecipeModifiers") || name.equals("getRecipeModifier") || name.equals("recipeModifiers");
    }

    private static boolean hasParallelFromCatalog(ResourceLocation id) {
        var defStruct = MultiblockStructureCatalog.getStructure(id);
        return defStruct != null && defStruct.supportsAbility("PARALLEL_HATCH");
    }

    private static void detectAndRegisterLaserHatch(ResourceLocation id) {
        var defStruct = MultiblockStructureCatalog.getStructure(id);
        if (defStruct != null && (defStruct.supportsAbility("INPUT_LASER") || defStruct.supportsAbility("LASER_TARGET_HATCH") || defStruct.supportsAbility("LASER_SOURCE_HATCH"))) {
            registerLaserHatchController(id);
        }
    }

    private static void detectAndRegisterThreading(ResourceLocation id, Object def, Class<?> cls, Class<?> mCls) {
        boolean isThreading = isThreadingMachineClass(mCls) || hasThreadingModifier(cls, def) || isThreadingFromCatalog(id);
        if (!isThreading) return;

        int detectedHelixCount = detectHelixCountFromCatalog(id);
        registerThreadingMultiblock(id, detectedHelixCount > 0 ? detectedHelixCount : 8);
    }

    private static boolean isThreadingMachineClass(Class<?> mCls) {
        if (mCls == null) return false;
        if (THREADING_CAPABLE_CLS != null && THREADING_CAPABLE_CLS.isAssignableFrom(mCls)) {
            return true;
        }
        String mClsName = mCls.getName().toLowerCase(Locale.ROOT);
        return mClsName.contains("threadingcapable") || mClsName.contains("startthreading");
    }

    private static boolean hasThreadingModifier(Class<?> cls, Object def) {
        try {
            Method mGetModifiers = cls.getMethod("getRecipeModifiers");
            mGetModifiers.setAccessible(true);
            Object modifiers = mGetModifiers.invoke(def);
            if (modifiers != null) {
                String modStr = modifiers.toString().toLowerCase(Locale.ROOT);
                return modStr.contains("threading_machine") || modStr.contains("startrecipemodifiers") || modStr.contains("threading");
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return false;
    }

    private static boolean isThreadingFromCatalog(ResourceLocation id) {
        var defStruct = MultiblockStructureCatalog.getStructure(id);
        if (defStruct == null) return false;

        if (defStruct.supportsAbility("THREADING") || defStruct.supportsAbility("THREADING_HELIX")) {
            return true;
        }
        return defStruct.candidateBlocks().stream().anyMatch(b ->
                b.getPath().contains("threading_controller") || b.getPath().contains("thread_helix") || b.getPath().contains("threading_helix"));
    }

    private static int detectHelixCountFromCatalog(ResourceLocation id) {
        var defStruct = MultiblockStructureCatalog.getStructure(id);
        if (defStruct == null) return 0;

        int count = 0;
        for (var part : defStruct.parts()) {
            if (part != null && part.itemId() != null && isHelixPartPath(part.itemId().getPath())) {
                count = Math.max(count, part.amount());
            }
        }
        return count;
    }

    private static boolean isHelixPartPath(String path) {
        return path.contains("thread_helix") || path.contains("threading_helix")
                || path.contains("supreme_helix") || path.contains("overdrive_helix")
                || path.contains("coprocessor_helix") || path.contains("weaver_helix");
    }

    public static ResourceLocation extractRecipeTypeId(Object rt) {
        if (rt == null) return null;
        if (rt instanceof ResourceLocation rl) return rl;

        ResourceLocation loc = extractFromForgeRecipeTypes(rt);
        if (loc != null) return loc;

        ResourceLocation refLoc = extractRecipeTypeIdViaReflection(rt);
        if (refLoc != null) return refLoc;

        String str = rt.toString();
        if (str != null && str.contains(":")) {
            return ResourceLocation.tryParse(str);
        }
        return null;
    }

    private static ResourceLocation extractFromForgeRecipeTypes(Object rt) {
        if (rt instanceof net.minecraft.world.item.crafting.RecipeType<?> rType && net.minecraftforge.registries.ForgeRegistries.RECIPE_TYPES != null) {
            ResourceLocation loc = net.minecraftforge.registries.ForgeRegistries.RECIPE_TYPES.getKey(rType);
            if (loc != null && !loc.getPath().equals("air")) return loc;
        }
        return null;
    }

    private static ResourceLocation extractRecipeTypeIdViaReflection(Object rt) {
        ResourceLocation fromField = extractRegistryNameFromField(rt);
        if (fromField != null) return fromField;

        ResourceLocation fromMethod = extractRegistryNameFromMethod(rt);
        if (fromMethod != null) return fromMethod;

        return extractRegistryNameFromGTRegistry(rt);
    }

    private static ResourceLocation extractRegistryNameFromField(Object rt) {
        try {
            Field f = rt.getClass().getField("registryName");
            f.setAccessible(true);
            Object val = f.get(rt);
            if (val instanceof ResourceLocation rl) return rl;
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return null;
    }

    private static ResourceLocation extractRegistryNameFromMethod(Object rt) {
        for (String mName : new String[]{"getRegistryName", "getId"}) {
            try {
                Method m = rt.getClass().getMethod(mName);
                m.setAccessible(true);
                Object idVal = m.invoke(rt);
                if (idVal instanceof ResourceLocation rl) return rl;
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        return null;
    }

    private static ResourceLocation extractRegistryNameFromGTRegistry(Object rt) {
        if (RECIPE_TYPES_FIELD == null) return null;
        try {
            Object recipeTypesReg = RECIPE_TYPES_FIELD.get(null);
            if (recipeTypesReg != null) {
                Method mGetKey = recipeTypesReg.getClass().getMethod("getKey", Object.class);
                mGetKey.setAccessible(true);
                Object k = mGetKey.invoke(recipeTypesReg, rt);
                if (k instanceof ResourceLocation rl) return rl;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return null;
    }

    public static Iterable<?> getRegistryIterable(Object registry) {
        if (registry == null) return null;
        if (registry instanceof Iterable<?> iterable) {
            return iterable;
        }
        try {
            Method valuesMethod = registry.getClass().getMethod("values");
            valuesMethod.setAccessible(true);
            Object result = valuesMethod.invoke(registry);
            if (result instanceof Iterable<?> iterable) {
                return iterable;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return null;
    }

    private static boolean containsRecipeModifier(Object modifiersObj, String targetName) {
        if (modifiersObj == null || targetName == null || GT_MODIFIERS_CLS == null) return false;
        try {
            Field f = GT_MODIFIERS_CLS.getField(targetName);
            f.setAccessible(true);
            Object targetModifier = f.get(null);
            if (targetModifier != null) {
                return containsModifierObject(modifiersObj, targetModifier);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return false;
    }

    private static boolean containsModifierObject(Object obj, Object target) {
        if (obj == null || target == null) return false;
        if (obj == target || obj.equals(target)) return true;

        if (obj instanceof Object[] arr) {
            for (Object item : arr) {
                if (containsModifierObject(item, target)) return true;
            }
            return false;
        }

        if (obj instanceof Iterable<?> it) {
            for (Object item : it) {
                if (containsModifierObject(item, target)) return true;
            }
            return false;
        }

        return inspectModifierFields(obj, target);
    }

    private static boolean inspectModifierFields(Object obj, Object target) {
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val != null && val != obj && isMatchingModifierValue(val, target)) {
                        return true;
                    }
                } catch (ReflectiveOperationException | LinkageError ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    private static boolean isMatchingModifierValue(Object val, Object target) {
        if (val == target || val.equals(target)) return true;
        if (val instanceof Object[] || val instanceof Iterable<?>) {
            return containsModifierObject(val, target);
        }
        return false;
    }
}




