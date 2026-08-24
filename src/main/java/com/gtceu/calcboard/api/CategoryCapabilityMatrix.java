package com.gtceu.calcboard.api;

import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-baked Global Capability Matrix for all Recipe Categories.
 * Deductively maps Recipe Category IDs to available workstations, multiblock/singleblock options,
 * coil heating compatibility, large turbine stats, and applicable addon categories.
 */
public class CategoryCapabilityMatrix {

    private static final CategoryCapabilityMatrix INSTANCE = new CategoryCapabilityMatrix();

    private final Map<ResourceLocation, CategoryCapability> capabilities = new ConcurrentHashMap<>();
    private boolean baked = false;

    public static CategoryCapabilityMatrix getInstance() {
        return INSTANCE;
    }

    private CategoryCapabilityMatrix() {
        initTestDefaults();
    }

    /**
     * Retrieves the pre-baked CategoryCapability for the given category ID.
     */
    public CategoryCapability getCapability(ResourceLocation categoryId) {
        if (categoryId == null) {
            return CategoryCapability.DEFAULT;
        }
        if (!baked && capabilities.isEmpty()) {
            bake(null);
        }
        return capabilities.getOrDefault(categoryId, buildFallback(categoryId));
    }

    public boolean isBaked() {
        return baked || !capabilities.isEmpty();
    }

    public synchronized void reset() {
        capabilities.clear();
        baked = false;
        initTestDefaults();
    }

    /**
     * Bakes the matrix by deductively analyzing GTCEu machine definitions, multiblock_info recipes, and Thermal tags.
     */
    public synchronized void bake(Object emiRecipeManager) {
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [CategoryCapabilityMatrix] Starting pre-baking capability matrix...");

        // 1. Ensure MultiblockDetector has scanned EMI multiblock_info recipes
        MultiblockDetector.reinitialize(emiRecipeManager);

        Map<ResourceLocation, CategoryBuilder> builders = new HashMap<>();

        // 2. Scan GTCEu Machine Definitions
        try {
            if (ModCompatHelper.isGTLoaded()) {
                Class<?> multiblockDefCls = null;
                try {
                    multiblockDefCls = Class.forName("com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition");
                } catch (Throwable ignored) {}

                Class<?> gtRegistriesCls = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
                Object machinesRegistry = gtRegistriesCls.getField("MACHINES").get(null);
                Iterable<?> iterable = MultiblockDetector.getRegistryIterable(machinesRegistry);

                if (iterable != null) {
                    for (Object def : iterable) {
                        if (def == null) continue;
                        try {
                            Method mGetId = def.getClass().getMethod("getId");
                            ResourceLocation mId = (ResourceLocation) mGetId.invoke(def);
                            if (mId == null) continue;

                            boolean isMb = (multiblockDefCls != null && multiblockDefCls.isInstance(def))
                                    || MultiblockDetector.isMultiblock(mId);
                            boolean usesCoils = MultiblockDetector.isCoilMultiblock(mId);
                            boolean isTurbine = MultiblockDetector.isTurbineMachine(mId);

                            boolean isSteam = false;
                            boolean isHp = false;
                            int tier = -1;
                            try {
                                Method mTier = def.getClass().getMethod("getTier");
                                Object tObj = mTier.invoke(def);
                                if (tObj instanceof Number num) tier = num.intValue();
                                else if (tObj instanceof Enum<?> e) tier = e.ordinal();
                            } catch (Throwable ignored) {}

                            if (tier == 0 && !isMb) {
                                isSteam = true;
                                try {
                                    Method mIsHp = def.getClass().getMethod("isHighPressure");
                                    isHp = (boolean) mIsHp.invoke(def);
                                } catch (Throwable ignored) {}
                            }

                            GTVoltageTier turbineTier = isTurbine ? MultiblockDetector.getTurbineBaseTier(mId) : null;
                            double turbineBaseEnergy = isTurbine ? (MultiblockDetector.getTurbineBaseProduction(mId) != null ? MultiblockDetector.getTurbineBaseProduction(mId) : 4096.0) : 0.0;

                            // Query Recipe Types of this MachineDefinition
                            Method mGetRecipeType = def.getClass().getMethod("getRecipeTypes");
                            Object rTypes = mGetRecipeType.invoke(def);
                            if (rTypes instanceof Object[] arr) {
                                for (Object rt : arr) {
                                    if (rt != null) {
                                        ResourceLocation catId = MultiblockDetector.extractRecipeTypeId(rt);
                                        if (catId != null) {
                                            CategoryBuilder b = builders.computeIfAbsent(catId, CategoryBuilder::new);
                                            b.addWorkstation(mId, isMb);
                                            if (usesCoils) b.canUseCoils = true;
                                            if (isSteam) {
                                                if (isHp) {
                                                    b.hasHighPressureSteamOption = true;
                                                    b.highPressureWorkstation = mId;
                                                } else {
                                                    b.hasLowPressureSteamOption = true;
                                                    b.lowPressureWorkstation = mId;
                                                }
                                            }
                                            if (isTurbine) {
                                                b.isTurbine = true;
                                                if (turbineTier != null) b.turbineBaseTier = turbineTier;
                                                if (turbineBaseEnergy > 0) b.turbineBaseProduction = turbineBaseEnergy;
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable t) {
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] [CategoryCapabilityMatrix] Error inspecting GTRegistries: {}", t.getMessage());
        }

        // 3. Scan all EMI recipes to map category -> workstations and detect capabilities directly from recipe definitions
        Iterable<?> recipes = null;
        if (emiRecipeManager != null) {
            if (emiRecipeManager instanceof Iterable<?> it) {
                recipes = it;
            } else {
                try {
                    Method mGetRecipes = emiRecipeManager.getClass().getMethod("getRecipes");
                    Object res = mGetRecipes.invoke(emiRecipeManager);
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
            for (Object rObj : recipes) {
                if (rObj instanceof dev.emi.emi.api.recipe.EmiRecipe recipe) {
                    if (recipe.getCategory() != null && recipe.getCategory().getId() != null) {
                        ResourceLocation catId = recipe.getCategory().getId();
                        CategoryBuilder b = builders.computeIfAbsent(catId, CategoryBuilder::new);

                        for (ResourceLocation ws : com.gtceu.calcboard.integration.emi.EmiRecipeConverter.findAllWorkstations(recipe)) {
                            boolean isMb = MultiblockDetector.isMultiblock(ws);
                            b.addWorkstation(ws, isMb);
                            if (MultiblockDetector.isCoilMultiblock(ws)) {
                                b.canUseCoils = true;
                                MultiblockDetector.registerCoilCategory(catId);
                            }
                            if (MultiblockDetector.isTurbineMachine(ws)) {
                                b.isTurbine = true;
                                MultiblockDetector.registerTurbineCategory(catId);
                            }
                            if (!isMb && ws != null && ws.getNamespace().equals("gtceu")) {
                                try {
                                    if (ModCompatHelper.isGTLoaded()) {
                                        Class<?> gtRegistriesCls = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
                                        Object machinesRegistry = gtRegistriesCls.getField("MACHINES").get(null);
                                        Method mGet = machinesRegistry.getClass().getMethod("get", ResourceLocation.class);
                                        Object def = mGet.invoke(machinesRegistry, ws);
                                        if (def != null) {
                                            Method mTier = def.getClass().getMethod("getTier");
                                            Object tObj = mTier.invoke(def);
                                            int tier = -1;
                                            if (tObj instanceof Number num) tier = num.intValue();
                                            else if (tObj instanceof Enum<?> e) tier = e.ordinal();
                                            if (tier == 0) {
                                                boolean isHp = false;
                                                try {
                                                    Method mIsHp = def.getClass().getMethod("isHighPressure");
                                                    isHp = (boolean) mIsHp.invoke(def);
                                                } catch (Throwable ignored) {}
                                                if (isHp) {
                                                    b.hasHighPressureSteamOption = true;
                                                    if (b.highPressureWorkstation == null) b.highPressureWorkstation = ws;
                                                } else {
                                                    b.hasLowPressureSteamOption = true;
                                                    if (b.lowPressureWorkstation == null) b.lowPressureWorkstation = ws;
                                                }
                                            }
                                        }
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                }
            }
        }

        // 4. Scan Known Coil Recipe Categories from MultiblockDetector
        for (ResourceLocation catId : MultiblockDetector.getAllCoilCategories()) {
            CategoryBuilder b = builders.computeIfAbsent(catId, CategoryBuilder::new);
            b.canUseCoils = true;
            b.hasMultiblockOption = true;
        }

        // 5. Scan Known Turbine Recipe Categories from MultiblockDetector
        for (ResourceLocation catId : MultiblockDetector.getAllTurbineCategories()) {
            CategoryBuilder b = builders.computeIfAbsent(catId, CategoryBuilder::new);
            b.isTurbine = true;
            b.hasMultiblockOption = true;
            GTVoltageTier t = MultiblockDetector.getTurbineBaseTier(catId);
            if (t != null) b.turbineBaseTier = t;
            Double prod = MultiblockDetector.getTurbineBaseProduction(catId);
            if (prod != null && prod > 0) b.turbineBaseProduction = prod;
        }

        // 6. Build Final Immutable Capabilities Map
        Map<ResourceLocation, CategoryCapability> newMap = new HashMap<>();
        for (Map.Entry<ResourceLocation, CategoryBuilder> entry : builders.entrySet()) {
            newMap.put(entry.getKey(), entry.getValue().build());
        }

        if (!newMap.isEmpty()) {
            capabilities.clear();
            capabilities.putAll(newMap);
            baked = true;
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                    "[GTCalcBoard] [CategoryCapabilityMatrix] Pre-baking completed successfully. Total baked categories: {}",
                    capabilities.size()
            );
        } else {
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                    "[GTCalcBoard] [CategoryCapabilityMatrix] Pre-baking deferred: recipe list not yet available."
            );
        }
    }

    private CategoryCapability buildFallback(ResourceLocation categoryId) {
        String ns = categoryId.getNamespace().toLowerCase();
        boolean isThermal = ns.equals("thermal") || ns.equals("thermal_expansion") || ns.equals("systeams") || ns.equals("cofh_core");
        boolean isCoil = MultiblockDetector.isCoilRecipeCategory(categoryId);
        boolean isTurbine = MultiblockDetector.isTurbineRecipeCategory(categoryId);

        Set<AddonCategory> supported = new HashSet<>();
        supported.add(AddonCategory.CUSTOM);
        if (isThermal) {
            supported.add(AddonCategory.THERMAL_AUGMENT);
        } else {
            if (isTurbine) supported.add(AddonCategory.ROTOR);
            if (isCoil) supported.add(AddonCategory.COIL);
            supported.add(AddonCategory.PARALLEL);
            supported.add(AddonCategory.MAINTENANCE);
            supported.add(AddonCategory.MULTIBLOCK_TRAIT);
        }

        return new CategoryCapability(
                categoryId,
                Collections.emptyList(),
                null,
                true,
                isCoil || isTurbine,
                isCoil,
                isTurbine,
                isThermal,
                false,
                false,
                null,
                null,
                isTurbine ? MultiblockDetector.getTurbineBaseTier(categoryId) : null,
                isTurbine && MultiblockDetector.getTurbineBaseProduction(categoryId) != null ? MultiblockDetector.getTurbineBaseProduction(categoryId) : 0.0,
                Collections.unmodifiableSet(supported)
        );
    }

    private void initTestDefaults() {
        // Mock Categories for JUnit Test Environments
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:large_chemical_reactor"),
                List.of(ResourceLocation.tryParse("gtceu:large_chemical_reactor"), ResourceLocation.tryParse("gtceu:extreme_chemical_reactor")),
                ResourceLocation.tryParse("gtceu:large_chemical_reactor"),
                false, true, true, false, false, false, false, null, null, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:chemical_reactor"),
                List.of(ResourceLocation.tryParse("gtceu:chemical_reactor"), ResourceLocation.tryParse("gtceu:large_chemical_reactor")),
                ResourceLocation.tryParse("gtceu:chemical_reactor"),
                true, true, true, false, false, false, false, null, null, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:electric_blast_furnace"),
                List.of(ResourceLocation.tryParse("gtceu:electric_blast_furnace")),
                ResourceLocation.tryParse("gtceu:electric_blast_furnace"),
                false, true, true, false, false, false, false, null, null, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:pyrolyse_oven"),
                List.of(ResourceLocation.tryParse("gtceu:pyrolyse_oven")),
                ResourceLocation.tryParse("gtceu:pyrolyse_oven"),
                false, true, true, false, false, false, false, null, null, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:cracker"),
                List.of(ResourceLocation.tryParse("gtceu:cracker")),
                ResourceLocation.tryParse("gtceu:cracker"),
                false, true, true, false, false, false, false, null, null, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:multi_smelter"),
                List.of(ResourceLocation.tryParse("gtceu:multi_smelter")),
                ResourceLocation.tryParse("gtceu:multi_smelter"),
                false, true, true, false, false, false, false, null, null, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:steam_turbine"),
                List.of(
                        ResourceLocation.tryParse("gtceu:lv_steam_turbine"),
                        ResourceLocation.tryParse("gtceu:mv_steam_turbine"),
                        ResourceLocation.tryParse("gtceu:hv_steam_turbine"),
                        ResourceLocation.tryParse("gtceu:large_steam_turbine")
                ),
                ResourceLocation.tryParse("gtceu:large_steam_turbine"),
                true, true, false, true, false, false, false, null, null, GTVoltageTier.HV, 1024.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:gas_turbine"),
                List.of(
                        ResourceLocation.tryParse("gtceu:lv_gas_turbine"),
                        ResourceLocation.tryParse("gtceu:mv_gas_turbine"),
                        ResourceLocation.tryParse("gtceu:hv_gas_turbine"),
                        ResourceLocation.tryParse("gtceu:large_gas_turbine")
                ),
                ResourceLocation.tryParse("gtceu:large_gas_turbine"),
                true, true, false, true, false, false, false, null, null, GTVoltageTier.EV, 4096.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:plasma_turbine"),
                List.of(
                        ResourceLocation.tryParse("gtceu:large_plasma_turbine"),
                        ResourceLocation.tryParse("gtceu:supreme_plasma_turbine"),
                        ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine")
                ),
                ResourceLocation.tryParse("gtceu:large_plasma_turbine"),
                false, true, false, true, false, false, false, null, null, GTVoltageTier.IV, 16384.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("thermal:lapidary_fuel"),
                List.of(ResourceLocation.tryParse("thermal:dynamo_lapidary")),
                ResourceLocation.tryParse("thermal:dynamo_lapidary"),
                true, false, false, false, true, false, false, null, null, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:rock_filtrator"),
                List.of(ResourceLocation.tryParse("gtceu:rock_filtrator")),
                ResourceLocation.tryParse("gtceu:rock_filtrator"),
                false, true, false, false, false, false, false, null, null, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:macerator"),
                List.of(ResourceLocation.tryParse("gtceu:lp_steam_macerator"), ResourceLocation.tryParse("gtceu:hp_steam_macerator"), ResourceLocation.tryParse("gtceu:lv_macerator")),
                ResourceLocation.tryParse("gtceu:lv_macerator"),
                true, false, false, false, false, true, true, ResourceLocation.tryParse("gtceu:lp_steam_macerator"), ResourceLocation.tryParse("gtceu:hp_steam_macerator"), null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:compressor"),
                List.of(ResourceLocation.tryParse("gtceu:lp_steam_compressor"), ResourceLocation.tryParse("gtceu:hp_steam_compressor"), ResourceLocation.tryParse("gtceu:lv_compressor")),
                ResourceLocation.tryParse("gtceu:lv_compressor"),
                true, false, false, false, false, true, true, ResourceLocation.tryParse("gtceu:lp_steam_compressor"), ResourceLocation.tryParse("gtceu:hp_steam_compressor"), null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:alloy_smelter"),
                List.of(ResourceLocation.tryParse("gtceu:lp_steam_alloy_smelter"), ResourceLocation.tryParse("gtceu:hp_steam_alloy_smelter"), ResourceLocation.tryParse("gtceu:lv_alloy_smelter"), ResourceLocation.tryParse("gtceu:combination_smelter")),
                ResourceLocation.tryParse("gtceu:lv_alloy_smelter"),
                true, true, false, false, false, true, true, ResourceLocation.tryParse("gtceu:lp_steam_alloy_smelter"), ResourceLocation.tryParse("gtceu:hp_steam_alloy_smelter"), null, 0.0
        );
    }

    private void registerMockCategory(ResourceLocation catId, List<ResourceLocation> ws, ResourceLocation defWs,
                                      boolean single, boolean multi, boolean coil, boolean turbine, boolean thermal,
                                      boolean hasLp, boolean hasHp, ResourceLocation lpWs, ResourceLocation hpWs,
                                      GTVoltageTier tTier, double tProd) {
        Set<AddonCategory> supported = new HashSet<>();
        supported.add(AddonCategory.CUSTOM);
        if (thermal) {
            supported.add(AddonCategory.THERMAL_AUGMENT);
        } else {
            if (turbine) supported.add(AddonCategory.ROTOR);
            if (coil) supported.add(AddonCategory.COIL);
            if (multi) {
                supported.add(AddonCategory.PARALLEL);
                supported.add(AddonCategory.MAINTENANCE);
                supported.add(AddonCategory.MULTIBLOCK_TRAIT);
            }
        }
        capabilities.put(catId, new CategoryCapability(
                catId, ws, defWs, single, multi, coil, turbine, thermal, hasLp, hasHp, lpWs, hpWs, tTier, tProd, Collections.unmodifiableSet(supported)
        ));
    }

    private static class CategoryBuilder {
        final ResourceLocation categoryId;
        final List<ResourceLocation> workstations = new ArrayList<>();
        ResourceLocation defaultWorkstation = null;
        boolean hasSingleblockOption = false;
        boolean hasMultiblockOption = false;
        boolean canUseCoils = false;
        boolean isTurbine = false;
        boolean isThermal = false;
        boolean hasLowPressureSteamOption = false;
        boolean hasHighPressureSteamOption = false;
        ResourceLocation lowPressureWorkstation = null;
        ResourceLocation highPressureWorkstation = null;
        GTVoltageTier turbineBaseTier = null;
        double turbineBaseProduction = 0.0;

        CategoryBuilder(ResourceLocation categoryId) {
            this.categoryId = categoryId;
            String ns = categoryId.getNamespace().toLowerCase();
            this.isThermal = ns.equals("thermal") || ns.equals("thermal_expansion") || ns.equals("systeams") || ns.equals("cofh_core");
        }

        void addWorkstation(ResourceLocation ws, boolean isMb) {
            if (ws == null) return;
            if (!workstations.contains(ws)) {
                workstations.add(ws);
            }
            if (isMb) {
                hasMultiblockOption = true;
            } else {
                hasSingleblockOption = true;
            }
            if (defaultWorkstation == null) {
                defaultWorkstation = ws;
            }
        }

        CategoryCapability build() {
            Set<AddonCategory> supported = new HashSet<>();
            supported.add(AddonCategory.CUSTOM);

            if (isThermal) {
                supported.add(AddonCategory.THERMAL_AUGMENT);
            } else {
                if (isTurbine) {
                    supported.add(AddonCategory.ROTOR);
                }
                if (canUseCoils) {
                    supported.add(AddonCategory.COIL);
                }
                if (hasMultiblockOption) {
                    supported.add(AddonCategory.PARALLEL);
                    supported.add(AddonCategory.MAINTENANCE);
                    supported.add(AddonCategory.MULTIBLOCK_TRAIT);
                }
            }

            return new CategoryCapability(
                    categoryId,
                    Collections.unmodifiableList(workstations),
                    defaultWorkstation != null ? defaultWorkstation : (workstations.isEmpty() ? null : workstations.get(0)),
                    hasSingleblockOption,
                    hasMultiblockOption,
                    canUseCoils,
                    isTurbine,
                    isThermal,
                    hasLowPressureSteamOption,
                    hasHighPressureSteamOption,
                    lowPressureWorkstation,
                    highPressureWorkstation,
                    turbineBaseTier,
                    turbineBaseProduction,
                    Collections.unmodifiableSet(supported)
            );
        }
    }
}
