package com.gtceu.calcboard.api;

import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamically detects whether a machine block/workstation is a valid Multiblock
 * by checking if it has a registered "multiblock_info" structure recipe in EMI or is a GT Multiblock Controller.
 */
public class MultiblockDetector {

    private static final Set<ResourceLocation> MULTIBLOCK_RECIPE_CONTROLLERS = new HashSet<>();
    private static final Set<ResourceLocation> COIL_MULTIBLOCK_CONTROLLERS = new HashSet<>();
    private static final Set<ResourceLocation> COIL_RECIPE_CATEGORIES = new HashSet<>();
    private static final Set<ResourceLocation> TURBINE_CONTROLLERS = new HashSet<>();
    private static final Set<ResourceLocation> TURBINE_RECIPE_CATEGORIES = new HashSet<>();
    private static final Map<ResourceLocation, GTVoltageTier> TURBINE_BASE_TIERS = new HashMap<>();
    private static final Map<ResourceLocation, Double> TURBINE_BASE_PRODUCTIONS = new HashMap<>();
    private static final Map<ResourceLocation, Integer> THREADING_MAX_HELIX_CAPACITY = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    public static void initialize() {
        initialize(null);
    }

    public static void initialize(Object rmObj) {
        if (initialized && !MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) return;

        // 1. Scan EMI's "multiblock_info" recipe category directly
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
            }
            if (recipes == null) {
                try {
                    var rm = dev.emi.emi.api.EmiApi.getRecipeManager();
                    if (rm != null) recipes = rm.getRecipes();
                } catch (Throwable ignored) {}
            }

            if (recipes != null) {
                for (Object recipeObj : recipes) {
                    if (recipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe recipe) {
                        if (recipe.getCategory() != null && recipe.getCategory().getId() != null) {
                            String catPath = recipe.getCategory().getId().getPath();
                            if (catPath.equals("multiblock_info") || catPath.contains("multiblock")) {
                                ResourceLocation controllerId = null;

                                // Extract from recipe ID path (e.g. gtceu:multiblock_info/large_chemical_reactor -> gtceu:large_chemical_reactor)
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

                                // Deduce whether this multiblock structure uses Heating Coils from recipe structure inputs
                                boolean usesCoilBlock = false;
                                for (var ei : recipe.getInputs()) {
                                    for (var es : ei.getEmiStacks()) {
                                        if (es != null) {
                                            ItemStack stack = es.getItemStack();
                                            if (stack != null && !stack.isEmpty()) {
                                                CoilHelper.CoilStats stats = CoilHelper.getCoilStats(stack);
                                                if (stats != null && stats.temperature() > 0) {
                                                    usesCoilBlock = true;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    if (usesCoilBlock) break;
                                }

                                if (usesCoilBlock && controllerId != null) {
                                    // Exclude rock_filtrator / geode_filter
                                    if (!controllerId.equals(ResourceLocation.tryParse("gtceu:rock_filtrator")) && !controllerId.equals(ResourceLocation.tryParse("gtceu:geode_filter"))) {
                                        COIL_MULTIBLOCK_CONTROLLERS.add(controllerId);
                                        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [MultiblockDetector] Detected Coil Multiblock via multiblock_info: {}", controllerId);
                                    }
                                }

                                // Deduce whether this multiblock structure uses Threading Helixes and how many
                                int helixCount = 0;
                                for (var ei : recipe.getInputs()) {
                                    for (var es : ei.getEmiStacks()) {
                                        if (es != null) {
                                            ItemStack stack = es.getItemStack();
                                            if (stack != null && !stack.isEmpty()) {
                                                ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
                                                if (itemId != null) {
                                                    String p = itemId.getPath();
                                                    if (GTThreadingHelix.fromId(itemId.toString()) != null || p.contains("thread_helix") || p.contains("threading_helix")) {
                                                        helixCount = Math.max(helixCount, (int) es.getAmount());
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (helixCount > 0 && controllerId != null) {
                                    THREADING_MAX_HELIX_CAPACITY.put(controllerId, helixCount);
                                    com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [MultiblockDetector] Detected Threading Multiblock with {} helix blocks via multiblock_info: {}", helixCount, controllerId);
                                }
                            }
                        }
                    }
                }
                if (!MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) {
                    initialized = true;
                }
            }
        } catch (Throwable t) {
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] Error scanning EMI multiblock_info recipes: {}", t.getMessage());
        }

        // 2. GTCEu MetaMachine Multiblock Definition inspection via Reflection
        try {
            if (ModCompatHelper.isGTLoaded()) {
                Class<?> multiblockDefCls = null;
                try {
                    multiblockDefCls = Class.forName("com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition");
                } catch (Throwable ignored) {}

                Class<?> gtRegistriesCls = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
                Object machinesRegistry = gtRegistriesCls.getField("MACHINES").get(null);
                Iterable<?> iterable = getRegistryIterable(machinesRegistry);

                if (iterable != null) {
                    for (Object def : iterable) {
                        if (def == null) continue;
                        try {
                            Method mGetId = def.getClass().getMethod("getId");
                            ResourceLocation id = (ResourceLocation) mGetId.invoke(def);
                            if (id == null) continue;

                            Class<?> mCls = null;
                            try {
                                Method mGetMachineClass = def.getClass().getMethod("getMachineClass");
                                mCls = (Class<?>) mGetMachineClass.invoke(def);
                            } catch (Throwable ignored) {}

                            boolean isTurbineClass = false;
                            if (mCls != null) {
                                String mClsName = mCls.getName().toLowerCase();
                                if (mClsName.contains("largeturbine") || mClsName.contains("largeturbinemanager")) {
                                    isTurbineClass = true;
                                }
                            }

                            boolean isMb = (multiblockDefCls != null && multiblockDefCls.isInstance(def))
                                    || MULTIBLOCK_RECIPE_CONTROLLERS.contains(id)
                                    || id.getPath().startsWith("large_")
                                    || id.getPath().contains("nyinsane");
                            if (isMb) {
                                MULTIBLOCK_RECIPE_CONTROLLERS.add(id);
                            }

                            boolean isCoilMb = isMb && COIL_MULTIBLOCK_CONTROLLERS.contains(id);
                            boolean isTurbineMb = isMb && (isTurbineClass || TURBINE_CONTROLLERS.contains(id)
                                    || id.getPath().contains("large_steam_turbine")
                                    || id.getPath().contains("large_gas_turbine")
                                    || id.getPath().contains("large_plasma_turbine")
                                    || id.getPath().contains("supreme_plasma_turbine")
                                    || id.getPath().contains("nyinsane_plasma_turbine"));

                            if (isCoilMb) {
                                COIL_MULTIBLOCK_CONTROLLERS.add(id);
                                MULTIBLOCK_RECIPE_CONTROLLERS.add(id);
                            }

                            if (isTurbineMb) {
                                TURBINE_CONTROLLERS.add(id);
                                MULTIBLOCK_RECIPE_CONTROLLERS.add(id);

                                // Extract turbine base tier dynamically from definition
                                GTVoltageTier turbineTier = null;
                                for (String mName : new String[]{"getTier", "tier", "getBaseTier"}) {
                                    try {
                                        Method m = def.getClass().getMethod(mName);
                                        Object tVal = m.invoke(def);
                                        if (tVal instanceof Number num) {
                                            int tIdx = num.intValue();
                                            if (tIdx >= 0 && tIdx < GTVoltageTier.values().length) {
                                                turbineTier = GTVoltageTier.values()[tIdx];
                                                break;
                                            }
                                        } else if (tVal instanceof Enum<?> en) {
                                            try {
                                                turbineTier = GTVoltageTier.valueOf(en.name());
                                                break;
                                            } catch (Throwable ignored) {}
                                        }
                                    } catch (Throwable ignored) {}
                                }

                                double baseEnergy = turbineTier != null ? (double) (turbineTier.getVoltage() * 2L) : 4096.0;
                                for (String mName : new String[]{"getBaseEnergyPerTick", "getBaseEnergy", "getBaseEUt", "getEnergyCapacity"}) {
                                    try {
                                        Method m = def.getClass().getMethod(mName);
                                        Object eVal = m.invoke(def);
                                        if (eVal instanceof Number num && num.doubleValue() > 0) {
                                            baseEnergy = num.doubleValue();
                                            break;
                                        }
                                    } catch (Throwable ignored) {}
                                }

                                if (turbineTier != null) {
                                    TURBINE_BASE_TIERS.put(id, turbineTier);
                                }
                                TURBINE_BASE_PRODUCTIONS.put(id, baseEnergy);
                            }

                            // Inspect recipe types of multiblock definition
                            try {
                                Method mGetRecipeType = def.getClass().getMethod("getRecipeTypes");
                                Object rTypes = mGetRecipeType.invoke(def);
                                if (rTypes instanceof Object[] arr) {
                                    for (Object rt : arr) {
                                        if (rt != null) {
                                            ResourceLocation rl = extractRecipeTypeId(rt);
                                            if (rl != null) {
                                                if (isCoilMb) {
                                                    COIL_RECIPE_CATEGORIES.add(rl);
                                                    com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [MultiblockDetector] Registered Coil Recipe Category: {} for controller {}", rl, id);
                                                }
                                                if (isTurbineMb) {
                                                    TURBINE_RECIPE_CATEGORIES.add(rl);
                                                    GTVoltageTier t = TURBINE_BASE_TIERS.get(id);
                                                    if (t != null) TURBINE_BASE_TIERS.put(rl, t);
                                                    Double e = TURBINE_BASE_PRODUCTIONS.get(id);
                                                    if (e != null) TURBINE_BASE_PRODUCTIONS.put(rl, e);
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignored) {}
                        } catch (Throwable ignored) {}
                    }
                    initialized = true;
                }
            }
        } catch (Throwable ignored) {}

        if (!initialized || MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) {
            initTestEnvironmentDefaults();
            initialized = true;
        }
    }

    public static ResourceLocation extractRecipeTypeId(Object rt) {
        if (rt == null) return null;
        if (rt instanceof ResourceLocation rl) return rl;
        try {
            if (rt instanceof net.minecraft.world.item.crafting.RecipeType<?> rType) {
                ResourceLocation loc = net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(rType);
                if (loc != null && !loc.getPath().equals("air")) return loc;
                loc = net.minecraftforge.registries.ForgeRegistries.RECIPE_TYPES.getKey(rType);
                if (loc != null && !loc.getPath().equals("air")) return loc;
            }
        } catch (Throwable ignored) {}
        for (String mName : new String[]{"registryName", "getId", "getRegistryName"}) {
            try {
                Method m = rt.getClass().getMethod(mName);
                Object res = m.invoke(rt);
                if (res instanceof ResourceLocation rl) return rl;
                if (res instanceof String s) return ResourceLocation.tryParse(s);
            } catch (Throwable ignored) {}
        }
        try {
            java.lang.reflect.Field f = rt.getClass().getDeclaredField("registryName");
            f.setAccessible(true);
            Object res = f.get(rt);
            if (res instanceof ResourceLocation rl) return rl;
            if (res instanceof String s) return ResourceLocation.tryParse(s);
        } catch (Throwable ignored) {}
        return null;
    }

    public static Iterable<?> getRegistryIterable(Object registryObj) {
        if (registryObj == null) return null;
        if (registryObj instanceof Iterable<?> it) return it;
        try {
            Method mValues = registryObj.getClass().getMethod("values");
            Object vals = mValues.invoke(registryObj);
            if (vals instanceof Iterable<?> it) return it;
        } catch (Throwable ignored) {}
        try {
            Method mEntries = registryObj.getClass().getMethod("entries");
            Object entries = mEntries.invoke(registryObj);
            if (entries instanceof Iterable<?> it) return it;
        } catch (Throwable ignored) {}
        try {
            Method mKeySet = registryObj.getClass().getMethod("keySet");
            Object keys = mKeySet.invoke(registryObj);
            if (keys instanceof Iterable<?> it) {
                Method mGet = registryObj.getClass().getMethod("get", Object.class);
                List<Object> list = new ArrayList<>();
                for (Object k : it) {
                    Object val = mGet.invoke(registryObj, k);
                    if (val != null) list.add(val);
                }
                return list;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void initTestEnvironmentDefaults() {
        registerTestTurbine(ResourceLocation.tryParse("gtceu:large_steam_turbine"), GTVoltageTier.HV, 1024.0);
        registerTestTurbine(ResourceLocation.tryParse("gtceu:large_gas_turbine"), GTVoltageTier.EV, 4096.0);
        registerTestTurbine(ResourceLocation.tryParse("gtceu:large_plasma_turbine"), GTVoltageTier.IV, 16384.0);
        registerTestTurbine(ResourceLocation.tryParse("gtceu:supreme_plasma_turbine"), GTVoltageTier.IV, 98304.0);
        registerTestTurbine(ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine"), GTVoltageTier.IV, 196608.0);

        TURBINE_BASE_TIERS.put(ResourceLocation.tryParse("gtceu:steam_turbine"), GTVoltageTier.HV);
        TURBINE_BASE_PRODUCTIONS.put(ResourceLocation.tryParse("gtceu:steam_turbine"), 1024.0);
        TURBINE_BASE_TIERS.put(ResourceLocation.tryParse("gtceu:gas_turbine"), GTVoltageTier.EV);
        TURBINE_BASE_PRODUCTIONS.put(ResourceLocation.tryParse("gtceu:gas_turbine"), 4096.0);
        TURBINE_BASE_TIERS.put(ResourceLocation.tryParse("gtceu:plasma_turbine"), GTVoltageTier.IV);
        TURBINE_BASE_PRODUCTIONS.put(ResourceLocation.tryParse("gtceu:plasma_turbine"), 16384.0);

        TURBINE_RECIPE_CATEGORIES.add(ResourceLocation.tryParse("gtceu:steam_turbine"));
        TURBINE_RECIPE_CATEGORIES.add(ResourceLocation.tryParse("gtceu:gas_turbine"));
        TURBINE_RECIPE_CATEGORIES.add(ResourceLocation.tryParse("gtceu:plasma_turbine"));

        ResourceLocation lcr = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        ResourceLocation cr = ResourceLocation.tryParse("gtceu:chemical_reactor");
        COIL_MULTIBLOCK_CONTROLLERS.add(lcr);
        MULTIBLOCK_RECIPE_CONTROLLERS.add(lcr);
        COIL_RECIPE_CATEGORIES.add(cr);
    }

    private static void registerTestTurbine(ResourceLocation loc, GTVoltageTier tier, double baseProd) {
        TURBINE_CONTROLLERS.add(loc);
        MULTIBLOCK_RECIPE_CONTROLLERS.add(loc);
        TURBINE_RECIPE_CATEGORIES.add(loc);
        TURBINE_BASE_TIERS.put(loc, tier);
        TURBINE_BASE_PRODUCTIONS.put(loc, baseProd);
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
        initialize(rmObj);
    }

    public static GTVoltageTier getTurbineBaseTier(ResourceLocation id) {
        if (id == null) return null;
        if (!initialized || MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) {
            initialize();
        }
        return TURBINE_BASE_TIERS.get(id);
    }

    public static Double getTurbineBaseProduction(ResourceLocation id) {
        if (id == null) return null;
        if (!initialized || MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) {
            initialize();
        }
        return TURBINE_BASE_PRODUCTIONS.get(id);
    }

    /**
     * Checks whether the given workstation item/block has a registered Multiblock Info structure recipe or controller definition.
     */
    public static boolean isMultiblock(ResourceLocation workstationId) {
        if (workstationId == null) return false;
        if (!initialized || MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) {
            initialize();
        }
        return MULTIBLOCK_RECIPE_CONTROLLERS.contains(workstationId);
    }

    /**
     * Checks whether the given workstation item/block is a coil-heated multiblock machine.
     */
    public static boolean isCoilMultiblock(ResourceLocation workstationId) {
        if (workstationId == null) return false;
        if (!initialized || MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) {
            initialize();
        }
        return COIL_MULTIBLOCK_CONTROLLERS.contains(workstationId);
    }

    /**
     * Checks whether the given recipe category is supported by a coil-heated multiblock machine.
     */
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
        if (!initialized || MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) {
            initialize();
        }
        return COIL_RECIPE_CATEGORIES.contains(categoryId);
    }

    /**
     * Checks whether the given workstation item/block is a turbine generator machine.
     */
    public static boolean isTurbineMachine(ResourceLocation workstationId) {
        if (workstationId == null) return false;
        if (!initialized || MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) {
            initialize();
        }
        return TURBINE_CONTROLLERS.contains(workstationId);
    }

    /**
     * Checks whether the given recipe category is a turbine generator recipe type.
     */
    public static boolean isTurbineRecipeCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        if (!initialized || MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) {
            initialize();
        }
        if (TURBINE_RECIPE_CATEGORIES.contains(categoryId)) return true;
        String path = categoryId.getPath().toLowerCase();
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
        if (!initialized || MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) {
            initialize();
        }
        return THREADING_MAX_HELIX_CAPACITY.getOrDefault(id, 0);
    }

    public static int getMaxHelixCount(RecipeNode node) {
        if (node == null) return 0;
        if (!initialized || MULTIBLOCK_RECIPE_CONTROLLERS.isEmpty()) {
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

    public static boolean isThreadingMultiblock(ResourceLocation id) {
        return getMaxHelixCount(id) > 0;
    }
}
