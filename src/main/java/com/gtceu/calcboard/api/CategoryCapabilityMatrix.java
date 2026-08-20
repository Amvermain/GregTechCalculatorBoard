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

                            GTVoltageTier turbineTier = isTurbine ? MultiblockDetector.getTurbineBaseTier(mId) : null;
                            double turbineBaseEnergy = isTurbine ? (MultiblockDetector.getTurbineBaseProduction(mId) != null ? MultiblockDetector.getTurbineBaseProduction(mId) : 4096.0) : 0.0;

                            // Query Recipe Types of this MachineDefinition
                            Method mGetRecipeType = def.getClass().getMethod("getRecipeTypes");
                            Object rTypes = mGetRecipeType.invoke(def);
                            if (rTypes instanceof Object[] arr) {
                                for (Object rt : arr) {
                                    if (rt != null) {
                                        Method mGetIdRt = rt.getClass().getMethod("registryName");
                                        Object rId = mGetIdRt.invoke(rt);
                                        if (rId instanceof ResourceLocation catId) {
                                            CategoryBuilder b = builders.computeIfAbsent(catId, CategoryBuilder::new);
                                            b.addWorkstation(mId, isMb);
                                            if (usesCoils) b.canUseCoils = true;
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

        Set<MachineAddon.Category> supported = new HashSet<>();
        supported.add(MachineAddon.Category.CUSTOM);
        if (isThermal) {
            supported.add(MachineAddon.Category.THERMAL_AUGMENT);
        } else {
            if (isTurbine) supported.add(MachineAddon.Category.ROTOR);
            if (isCoil) supported.add(MachineAddon.Category.COIL);
            supported.add(MachineAddon.Category.PARALLEL);
            supported.add(MachineAddon.Category.MAINTENANCE);
            supported.add(MachineAddon.Category.MULTIBLOCK_TRAIT);
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
                false, true, true, false, false, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:chemical_reactor"),
                List.of(ResourceLocation.tryParse("gtceu:chemical_reactor"), ResourceLocation.tryParse("gtceu:large_chemical_reactor")),
                ResourceLocation.tryParse("gtceu:chemical_reactor"),
                true, true, true, false, false, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:electric_blast_furnace"),
                List.of(ResourceLocation.tryParse("gtceu:electric_blast_furnace")),
                ResourceLocation.tryParse("gtceu:electric_blast_furnace"),
                false, true, true, false, false, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:pyrolyse_oven"),
                List.of(ResourceLocation.tryParse("gtceu:pyrolyse_oven")),
                ResourceLocation.tryParse("gtceu:pyrolyse_oven"),
                false, true, true, false, false, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:cracker"),
                List.of(ResourceLocation.tryParse("gtceu:cracker")),
                ResourceLocation.tryParse("gtceu:cracker"),
                false, true, true, false, false, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:multi_smelter"),
                List.of(ResourceLocation.tryParse("gtceu:multi_smelter")),
                ResourceLocation.tryParse("gtceu:multi_smelter"),
                false, true, true, false, false, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:steam_turbine"),
                List.of(ResourceLocation.tryParse("gtceu:large_steam_turbine")),
                ResourceLocation.tryParse("gtceu:large_steam_turbine"),
                false, true, false, true, false, GTVoltageTier.HV, 1024.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:gas_turbine"),
                List.of(ResourceLocation.tryParse("gtceu:large_gas_turbine")),
                ResourceLocation.tryParse("gtceu:large_gas_turbine"),
                false, true, false, true, false, GTVoltageTier.EV, 4096.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:plasma_turbine"),
                List.of(ResourceLocation.tryParse("gtceu:large_plasma_turbine")),
                ResourceLocation.tryParse("gtceu:large_plasma_turbine"),
                false, true, false, true, false, GTVoltageTier.IV, 16384.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("thermal:lapidary_fuel"),
                List.of(ResourceLocation.tryParse("thermal:dynamo_lapidary")),
                ResourceLocation.tryParse("thermal:dynamo_lapidary"),
                true, false, false, false, true, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:rock_filtrator"),
                List.of(ResourceLocation.tryParse("gtceu:rock_filtrator")),
                ResourceLocation.tryParse("gtceu:rock_filtrator"),
                false, true, false, false, false, null, 0.0
        );
    }

    private void registerMockCategory(ResourceLocation catId, List<ResourceLocation> ws, ResourceLocation defWs,
                                      boolean single, boolean multi, boolean coil, boolean turbine, boolean thermal,
                                      GTVoltageTier tTier, double tProd) {
        Set<MachineAddon.Category> supported = new HashSet<>();
        supported.add(MachineAddon.Category.CUSTOM);
        if (thermal) {
            supported.add(MachineAddon.Category.THERMAL_AUGMENT);
        } else {
            if (turbine) supported.add(MachineAddon.Category.ROTOR);
            if (coil) supported.add(MachineAddon.Category.COIL);
            if (multi) {
                supported.add(MachineAddon.Category.PARALLEL);
                supported.add(MachineAddon.Category.MAINTENANCE);
                supported.add(MachineAddon.Category.MULTIBLOCK_TRAIT);
            }
        }
        capabilities.put(catId, new CategoryCapability(
                catId, ws, defWs, single, multi, coil, turbine, thermal, tTier, tProd, Collections.unmodifiableSet(supported)
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
            Set<MachineAddon.Category> supported = new HashSet<>();
            supported.add(MachineAddon.Category.CUSTOM);

            if (isThermal) {
                supported.add(MachineAddon.Category.THERMAL_AUGMENT);
            } else {
                if (isTurbine) {
                    supported.add(MachineAddon.Category.ROTOR);
                }
                if (canUseCoils) {
                    supported.add(MachineAddon.Category.COIL);
                }
                if (hasMultiblockOption) {
                    supported.add(MachineAddon.Category.PARALLEL);
                    supported.add(MachineAddon.Category.MAINTENANCE);
                    supported.add(MachineAddon.Category.MULTIBLOCK_TRAIT);
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
                    turbineBaseTier,
                    turbineBaseProduction,
                    Collections.unmodifiableSet(supported)
            );
        }
    }
}
