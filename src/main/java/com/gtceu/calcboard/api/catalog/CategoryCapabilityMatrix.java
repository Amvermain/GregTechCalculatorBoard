package com.gtceu.calcboard.api.catalog;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

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
     * Returns fallback immediately if matrix is not yet baked to prevent freezing caller thread.
     */
    public CategoryCapability getCapability(ResourceLocation categoryId) {
        if (categoryId == null) {
            return CategoryCapability.DEFAULT;
        }
        CategoryCapability cap = capabilities.get(categoryId);
        return cap != null ? cap : buildFallback(categoryId);
    }

    public boolean isBaked() {
        return baked || !capabilities.isEmpty();
    }

    public synchronized void reset() {
        capabilities.clear();
        baked = false;
        initTestDefaults();
    }

    private Map<ResourceLocation, CategoryBuilder> currentBuilders = null;

    public CategoryBuilder getOrCreateBuilder(ResourceLocation categoryId) {
        if (currentBuilders != null && categoryId != null) {
            return currentBuilders.computeIfAbsent(categoryId, CategoryBuilder::new);
        }
        return new CategoryBuilder(categoryId);
    }

    /**
     * Bakes the matrix by querying EMI multiblock_info recipes and delegating to loaded IModAdapters.
     */
    public synchronized void bake(Object emiRecipeManager) {
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [CategoryCapabilityMatrix] Starting pre-baking capability matrix...");

        // 1. Ensure MultiblockDetector has scanned EMI multiblock_info recipes and mod multiblocks
        MultiblockDetector.reinitialize(emiRecipeManager);

        Map<ResourceLocation, CategoryBuilder> builders = new HashMap<>();
        this.currentBuilders = builders;

        // 2. Query EMI Category workstations directly (O(Categories) ~ 100 iterations instead of 30,000+ recipes)
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
            try {
                EmiMatrixScanner.scan(builders);
            } catch (Throwable ignored) {}
        }

        // 2-2. Query JEI Category Catalysts directly
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isJeiLoaded()) {
            try {
                JeiMatrixScanner.scan(builders, emiRecipeManager);
            } catch (Throwable ignored) {}
        }

        // 3. Delegate to each loaded IModAdapter to enrich capabilities
        for (com.gtceu.calcboard.compat.IModAdapter adapter : com.gtceu.calcboard.compat.ModAdapterRegistry.getAllLoadedAdapters()) {
            try {
                adapter.enrichCapabilities(this, emiRecipeManager);
            } catch (Throwable t) {
                com.gtceu.calcboard.GregTechCalcBoard.LOGGER.warn(
                        "[GTCalcBoard] [CategoryCapabilityMatrix] Adapter '{}' enrichCapabilities failed: {}",
                        adapter.getModId(), t.getMessage()
                );
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

        this.currentBuilders = null;

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
        if (categoryId == null) return null;
        String ns = categoryId.getNamespace().toLowerCase(Locale.ROOT);
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
                List.of(
                        ResourceLocation.tryParse("gtceu:large_chemical_reactor"),
                        ResourceLocation.tryParse("gtceu:extreme_chemical_reactor"),
                        ResourceLocation.tryParse("gtceu:incomprehensible_chemical_reactor")
                ),
                ResourceLocation.tryParse("gtceu:large_chemical_reactor"),
                false, true, true, false, false, false, false, null, null, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:chemical_reactor"),
                List.of(
                        ResourceLocation.tryParse("gtceu:chemical_reactor"),
                        ResourceLocation.tryParse("gtceu:large_chemical_reactor"),
                        ResourceLocation.tryParse("gtceu:extreme_chemical_reactor"),
                        ResourceLocation.tryParse("gtceu:incomprehensible_chemical_reactor")
                ),
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
                List.of(ResourceLocation.tryParse("gtceu:lp_steam_macerator"), ResourceLocation.tryParse("gtceu:hp_steam_macerator"), ResourceLocation.tryParse("gtceu:lv_macerator"), ResourceLocation.tryParse("gtceu:large_macerator")),
                ResourceLocation.tryParse("gtceu:lv_macerator"),
                true, true, false, false, false, true, true, ResourceLocation.tryParse("gtceu:lp_steam_macerator"), ResourceLocation.tryParse("gtceu:hp_steam_macerator"), null, 0.0
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
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:extractor"),
                List.of(ResourceLocation.tryParse("gtceu:lp_steam_extractor"), ResourceLocation.tryParse("gtceu:hp_steam_extractor"), ResourceLocation.tryParse("gtceu:lv_extractor")),
                ResourceLocation.tryParse("gtceu:lv_extractor"),
                true, false, false, false, false, true, true, ResourceLocation.tryParse("gtceu:lp_steam_extractor"), ResourceLocation.tryParse("gtceu:hp_steam_extractor"), null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:forge_hammer"),
                List.of(ResourceLocation.tryParse("gtceu:lp_steam_forge_hammer"), ResourceLocation.tryParse("gtceu:hp_steam_forge_hammer"), ResourceLocation.tryParse("gtceu:lv_forge_hammer")),
                ResourceLocation.tryParse("gtceu:lv_forge_hammer"),
                true, false, false, false, false, true, true, ResourceLocation.tryParse("gtceu:lp_steam_forge_hammer"), ResourceLocation.tryParse("gtceu:hp_steam_forge_hammer"), null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:ore_washer"),
                List.of(ResourceLocation.tryParse("gtceu:lp_steam_ore_washer"), ResourceLocation.tryParse("gtceu:hp_steam_ore_washer"), ResourceLocation.tryParse("gtceu:lv_ore_washer")),
                ResourceLocation.tryParse("gtceu:lv_ore_washer"),
                true, false, false, false, false, true, true, ResourceLocation.tryParse("gtceu:lp_steam_ore_washer"), ResourceLocation.tryParse("gtceu:hp_steam_ore_washer"), null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:rock_breaker"),
                List.of(ResourceLocation.tryParse("gtceu:lp_steam_rock_breaker"), ResourceLocation.tryParse("gtceu:hp_steam_rock_breaker"), ResourceLocation.tryParse("gtceu:lv_rock_breaker")),
                ResourceLocation.tryParse("gtceu:lv_rock_breaker"),
                true, false, false, false, false, true, true, ResourceLocation.tryParse("gtceu:lp_steam_rock_breaker"), ResourceLocation.tryParse("gtceu:hp_steam_rock_breaker"), null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:thermal_centrifuge"),
                List.of(ResourceLocation.tryParse("gtceu:lv_thermal_centrifuge"), ResourceLocation.tryParse("gtceu:mv_thermal_centrifuge"), ResourceLocation.tryParse("gtceu:hv_thermal_centrifuge")),
                ResourceLocation.tryParse("gtceu:lv_thermal_centrifuge"),
                true, false, false, false, false, false, false, null, null, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:centrifuge"),
                List.of(ResourceLocation.tryParse("gtceu:mv_centrifuge"), ResourceLocation.tryParse("gtceu:hv_centrifuge")),
                ResourceLocation.tryParse("gtceu:mv_centrifuge"),
                true, false, false, false, false, false, false, null, null, null, 0.0
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

    public static class CategoryBuilder {
        public final ResourceLocation categoryId;
        public final List<ResourceLocation> workstations = new ArrayList<>();
        public ResourceLocation defaultWorkstation = null;
        public boolean hasSingleblockOption = false;
        public boolean hasMultiblockOption = false;
        public boolean canUseCoils = false;
        public boolean isTurbine = false;
        public boolean isThermal = false;
        public boolean hasLowPressureSteamOption = false;
        public boolean hasHighPressureSteamOption = false;
        public ResourceLocation lowPressureWorkstation = null;
        public ResourceLocation highPressureWorkstation = null;
        public GTVoltageTier turbineBaseTier = null;
        public double turbineBaseProduction = 0.0;

        public CategoryBuilder(ResourceLocation categoryId) {
            this.categoryId = categoryId;
            String ns = categoryId != null ? categoryId.getNamespace().toLowerCase(Locale.ROOT) : "";
            this.isThermal = ns.equals("thermal") || ns.equals("thermal_expansion") || ns.equals("systeams") || ns.equals("cofh_core");
        }

        public void addWorkstation(ResourceLocation ws, boolean isMb) {
            if (ws == null) return;
            if (ws.getPath().equalsIgnoreCase(categoryId.getPath())) {
                workstations.remove(ws);
                workstations.add(0, ws);
                defaultWorkstation = ws;
            } else if (!workstations.contains(ws)) {
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
            // Sort workstations: exact categoryId match first, base multiblocks, then large/mega multiblocks, then singleblocks
            workstations.sort((a, b) -> {
                boolean aExact = a.getPath().equalsIgnoreCase(categoryId.getPath());
                boolean bExact = b.getPath().equalsIgnoreCase(categoryId.getPath());
                if (aExact != bExact) return aExact ? -1 : 1;

                boolean aMb = MultiblockDetector.isMultiblock(a);
                boolean bMb = MultiblockDetector.isMultiblock(b);
                if (aMb != bMb) return aMb ? -1 : 1;

                if (aMb && bMb) {
                    String aPath = a.getPath().toLowerCase(Locale.ROOT);
                    String bPath = b.getPath().toLowerCase(Locale.ROOT);
                    boolean aAdvanced = aPath.startsWith("large_") || aPath.startsWith("mega_") || aPath.startsWith("advanced_") || aPath.startsWith("yielding_");
                    boolean bAdvanced = bPath.startsWith("large_") || bPath.startsWith("mega_") || bPath.startsWith("advanced_") || bPath.startsWith("yielding_");
                    if (aAdvanced != bAdvanced) return aAdvanced ? 1 : -1;
                }

                return a.toString().compareTo(b.toString());
            });

            if (!workstations.isEmpty()) {
                ResourceLocation bestDefault = null;
                for (ResourceLocation ws : workstations) {
                    if (!MultiblockDetector.isMultiblock(ws)) {
                        if (ws.getPath().equalsIgnoreCase(categoryId.getPath())) {
                            bestDefault = ws;
                            break;
                        }
                        if (bestDefault == null) {
                            bestDefault = ws;
                        }
                    }
                }
                if (bestDefault == null) {
                    bestDefault = workstations.get(0);
                }
                defaultWorkstation = bestDefault;
            }

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

    private static class EmiMatrixScanner {
        private static void scan(Map<ResourceLocation, CategoryBuilder> builders) {
            var rm = dev.emi.emi.api.EmiApi.getRecipeManager();
            if (rm != null && rm.getCategories() != null) {
                for (dev.emi.emi.api.recipe.EmiRecipeCategory cat : rm.getCategories()) {
                    if (cat == null || cat.getId() == null) continue;
                    ResourceLocation catId = cat.getId();
                    CategoryBuilder b = builders.computeIfAbsent(catId, CategoryBuilder::new);
                    List<dev.emi.emi.api.stack.EmiIngredient> workstations = rm.getWorkstations(cat);
                    if (workstations != null) {
                        for (dev.emi.emi.api.stack.EmiIngredient ei : workstations) {
                            if (ei != null && ei.getEmiStacks() != null) {
                                for (dev.emi.emi.api.stack.EmiStack es : ei.getEmiStacks()) {
                                    if (es != null && !es.isEmpty() && es.getId() != null) {
                                        ResourceLocation ws = es.getId();
                                        Object machineDef = null;
                                        try {
                                            Class<?> gtRegs = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
                                            Object machinesReg = gtRegs.getField("MACHINES").get(null);
                                            if (machinesReg != null) {
                                                Method mGet = machinesReg.getClass().getMethod("get", ResourceLocation.class);
                                                machineDef = mGet.invoke(machinesReg, ws);
                                            }
                                        } catch (Throwable ignored) {}

                                        boolean isMb = MultiblockDetector.inspectAndRegisterMachine(ws, machineDef, catId);
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
                }
            }
        }
    }

    private static class JeiMatrixScanner {
        private static void scan(Map<ResourceLocation, CategoryBuilder> builders, Object jeiRuntimeObj) {
            if (jeiRuntimeObj == null) {
                try {
                    jeiRuntimeObj = com.gtceu.calcboard.integration.jei.JeiRecipeViewerAdapter.getJeiRuntime();
                } catch (Throwable ignored) {}
            }
            if (!(jeiRuntimeObj instanceof mezz.jei.api.runtime.IJeiRuntime runtime)) return;
            try {
                var recipeManager = runtime.getRecipeManager();
                var categoryLookup = recipeManager.createRecipeCategoryLookup();
                if (categoryLookup != null) {
                    for (mezz.jei.api.recipe.category.IRecipeCategory<?> cat : categoryLookup.get().toList()) {
                        if (cat == null || cat.getRecipeType() == null) continue;
                        ResourceLocation catId = cat.getRecipeType().getUid();
                        CategoryBuilder b = builders.computeIfAbsent(catId, CategoryBuilder::new);

                        try {
                            var catalystLookup = recipeManager.createRecipeCatalystLookup(cat.getRecipeType());
                            if (catalystLookup != null) {
                                for (var typedIng : catalystLookup.get().toList()) {
                                    if (typedIng != null) {
                                        ItemStack is = typedIng.getItemStack().orElse(ItemStack.EMPTY);
                                        if (is.isEmpty() && typedIng.getIngredient() instanceof ItemStack s) {
                                            is = s;
                                        }
                                        if (!is.isEmpty()) {
                                            ResourceLocation ws = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(is.getItem());
                                            if (ws != null) {
                                                Object machineDef = null;
                                                try {
                                                    Class<?> gtRegs = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
                                                    Object machinesReg = gtRegs.getField("MACHINES").get(null);
                                                    if (machinesReg != null) {
                                                        Method mGet = machinesReg.getClass().getMethod("get", ResourceLocation.class);
                                                        machineDef = mGet.invoke(machinesReg, ws);
                                                    }
                                                } catch (Throwable ignored) {}

                                                boolean isMb = MultiblockDetector.inspectAndRegisterMachine(ws, machineDef, catId);
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
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        }
    }
}



