package com.gtceu.calcboard.api.catalog;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.spi.viewer.RecipeViewerBridgeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CategoryCapabilityMatrix {

    private static final CategoryCapabilityMatrix INSTANCE = new CategoryCapabilityMatrix();

    private static final Class<?> GT_REGISTRIES_CLS;
    private static final Field MACHINES_FIELD;
    private static final Method GET_MACHINE_METHOD;

    static {
        ClassLoader cl = CategoryCapabilityMatrix.class.getClassLoader();
        Class<?> gtRegs = null;
        Field mField = null;
        Method getM = null;
        try {
            gtRegs = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries", false, cl);
            mField = gtRegs.getField("MACHINES");
            if (mField != null) {
                getM = mField.getType().getMethod("get", ResourceLocation.class);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}

        GT_REGISTRIES_CLS = gtRegs;
        MACHINES_FIELD = mField;
        GET_MACHINE_METHOD = getM;
    }

    private final Map<ResourceLocation, CategoryCapability> capabilities = new ConcurrentHashMap<>();
    private boolean baked = false;

    public static CategoryCapabilityMatrix getInstance() {
        return INSTANCE;
    }

    private CategoryCapabilityMatrix() {
        initTestDefaults();
    }

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

    public synchronized void bake(Object emiRecipeManager) {
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [CategoryCapabilityMatrix] Starting pre-baking capability matrix...");

        MultiblockDetector.reinitialize(emiRecipeManager);

        Map<ResourceLocation, CategoryBuilder> builders = new HashMap<>();
        this.currentBuilders = builders;

        RecipeViewerBridgeRegistry.getActiveBridges().forEach(bridge -> {
            bridge.discoverCategoryWorkstations((catId, ws) -> {
                CategoryBuilder b = builders.computeIfAbsent(catId, CategoryBuilder::new);
                processScannedWorkstation(ws, b, catId);
            });
        });

        enrichCapabilitiesFromAdapters(emiRecipeManager);
        enrichCoilCategories(builders);
        enrichTurbineCategories(builders);

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

    private void enrichCapabilitiesFromAdapters(Object emiRecipeManager) {
        for (IModAdapter adapter : ModAdapterRegistry.getAllLoadedAdapters()) {
            try {
                adapter.enrichCapabilities(this, emiRecipeManager);
            } catch (Exception e) {
                com.gtceu.calcboard.GregTechCalcBoard.LOGGER.warn(
                        "[GTCalcBoard] [CategoryCapabilityMatrix] Adapter '{}' enrichCapabilities failed: {}",
                        adapter.getModId(), e.getMessage()
                );
            }
        }
    }

    private void enrichCoilCategories(Map<ResourceLocation, CategoryBuilder> builders) {
        for (ResourceLocation catId : MultiblockDetector.getAllCoilCategories()) {
            CategoryBuilder b = builders.computeIfAbsent(catId, CategoryBuilder::new);
            b.canUseCoils = true;
            b.hasMultiblockOption = true;
        }
    }

    private void enrichTurbineCategories(Map<ResourceLocation, CategoryBuilder> builders) {
        for (ResourceLocation catId : MultiblockDetector.getAllTurbineCategories()) {
            CategoryBuilder b = builders.computeIfAbsent(catId, CategoryBuilder::new);
            b.isTurbine = true;
            b.hasMultiblockOption = true;
            GTVoltageTier t = MultiblockDetector.getTurbineBaseTier(catId);
            if (t != null) b.turbineBaseTier = t;
            Double prod = MultiblockDetector.getTurbineBaseProduction(catId);
            if (prod != null && prod > 0) b.turbineBaseProduction = prod;
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
            supported.add(AddonCategory.ENERGY_HATCH);
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
                ResourceLocation.tryParse("gtceu:alloy_blast_smelter"),
                List.of(
                        ResourceLocation.tryParse("gtceu:alloy_blast_smelter"),
                        ResourceLocation.tryParse("gtceu:super_abs"),
                        ResourceLocation.tryParse("gtceu:mega_abs"),
                        ResourceLocation.tryParse("gtceu:ultimate_abs")
                ),
                ResourceLocation.tryParse("gtceu:alloy_blast_smelter"),
                false, true, true, false, false, false, false, null, null, null, 0.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:steam_turbine"),
                List.of(
                        ResourceLocation.tryParse("gtceu:lv_steam_turbine"),
                        ResourceLocation.tryParse("gtceu:mv_steam_turbine"),
                        ResourceLocation.tryParse("gtceu:hv_steam_turbine"),
                        ResourceLocation.tryParse("gtceu:steam_large_turbine"),
                        ResourceLocation.tryParse("gtceu:large_steam_turbine")
                ),
                ResourceLocation.tryParse("gtceu:steam_large_turbine"),
                true, true, false, true, false, false, false, null, null, GTVoltageTier.HV, 1024.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:steam_turbine_fuels"),
                List.of(
                        ResourceLocation.tryParse("gtceu:lv_steam_turbine"),
                        ResourceLocation.tryParse("gtceu:mv_steam_turbine"),
                        ResourceLocation.tryParse("gtceu:hv_steam_turbine"),
                        ResourceLocation.tryParse("gtceu:steam_large_turbine"),
                        ResourceLocation.tryParse("gtceu:large_steam_turbine")
                ),
                ResourceLocation.tryParse("gtceu:steam_large_turbine"),
                true, true, false, true, false, false, false, null, null, GTVoltageTier.HV, 1024.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:gas_turbine"),
                List.of(
                        ResourceLocation.tryParse("gtceu:lv_gas_turbine"),
                        ResourceLocation.tryParse("gtceu:mv_gas_turbine"),
                        ResourceLocation.tryParse("gtceu:hv_gas_turbine"),
                        ResourceLocation.tryParse("gtceu:gas_large_turbine"),
                        ResourceLocation.tryParse("gtceu:large_gas_turbine")
                ),
                ResourceLocation.tryParse("gtceu:gas_large_turbine"),
                true, true, false, true, false, false, false, null, null, GTVoltageTier.EV, 4096.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:gas_turbine_fuels"),
                List.of(
                        ResourceLocation.tryParse("gtceu:lv_gas_turbine"),
                        ResourceLocation.tryParse("gtceu:mv_gas_turbine"),
                        ResourceLocation.tryParse("gtceu:hv_gas_turbine"),
                        ResourceLocation.tryParse("gtceu:gas_large_turbine"),
                        ResourceLocation.tryParse("gtceu:large_gas_turbine")
                ),
                ResourceLocation.tryParse("gtceu:gas_large_turbine"),
                true, true, false, true, false, false, false, null, null, GTVoltageTier.EV, 4096.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:combustion_generator"),
                List.of(
                        ResourceLocation.tryParse("gtceu:lv_combustion"),
                        ResourceLocation.tryParse("gtceu:mv_combustion"),
                        ResourceLocation.tryParse("gtceu:hv_combustion"),
                        ResourceLocation.tryParse("gtceu:large_combustion_engine"),
                        ResourceLocation.tryParse("gtceu:extreme_combustion_engine")
                ),
                ResourceLocation.tryParse("gtceu:large_combustion_engine"),
                true, true, false, false, false, false, false, null, null, GTVoltageTier.EV, 4096.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:combustion_generator_fuels"),
                List.of(
                        ResourceLocation.tryParse("gtceu:lv_combustion"),
                        ResourceLocation.tryParse("gtceu:mv_combustion"),
                        ResourceLocation.tryParse("gtceu:hv_combustion"),
                        ResourceLocation.tryParse("gtceu:large_combustion_engine"),
                        ResourceLocation.tryParse("gtceu:extreme_combustion_engine")
                ),
                ResourceLocation.tryParse("gtceu:large_combustion_engine"),
                true, true, false, false, false, false, false, null, null, GTVoltageTier.EV, 4096.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:plasma_turbine"),
                List.of(
                        ResourceLocation.tryParse("gtceu:plasma_large_turbine"),
                        ResourceLocation.tryParse("gtceu:large_plasma_turbine"),
                        ResourceLocation.tryParse("gtceu:supreme_plasma_turbine"),
                        ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine")
                ),
                ResourceLocation.tryParse("gtceu:plasma_large_turbine"),
                false, true, false, true, false, false, false, null, null, GTVoltageTier.IV, 16384.0
        );
        registerMockCategory(
                ResourceLocation.tryParse("gtceu:plasma_generator_fuels"),
                List.of(
                        ResourceLocation.tryParse("gtceu:plasma_large_turbine"),
                        ResourceLocation.tryParse("gtceu:large_plasma_turbine"),
                        ResourceLocation.tryParse("gtceu:supreme_plasma_turbine"),
                        ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine")
                ),
                ResourceLocation.tryParse("gtceu:plasma_large_turbine"),
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

    public void registerMockCategory(ResourceLocation catId, List<ResourceLocation> ws, ResourceLocation defWs,
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

    private static Object queryMachineDefinition(ResourceLocation ws) {
        if (MACHINES_FIELD == null || GET_MACHINE_METHOD == null || ws == null) return null;
        try {
            Object machinesReg = MACHINES_FIELD.get(null);
            if (machinesReg != null) {
                GET_MACHINE_METHOD.setAccessible(true);
                return GET_MACHINE_METHOD.invoke(machinesReg, ws);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {}
        return null;
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
            sortWorkstations();
            resolveDefaultWorkstation();

            Set<AddonCategory> supported = new HashSet<>();
            supported.add(AddonCategory.CUSTOM);

            if (isThermal) {
                supported.add(AddonCategory.THERMAL_AUGMENT);
            } else {
                if (isTurbine) supported.add(AddonCategory.ROTOR);
                if (canUseCoils) supported.add(AddonCategory.COIL);
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

        private void sortWorkstations() {
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
        }

        private void resolveDefaultWorkstation() {
            if (workstations.isEmpty()) return;
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
    }
    private static void processScannedWorkstation(ResourceLocation ws, CategoryBuilder b, ResourceLocation catId) {
        Object machineDef = queryMachineDefinition(ws);
        boolean isMb = MultiblockDetector.inspectAndRegisterMachine(ws, machineDef, catId);
        b.addWorkstation(ws, isMb);
        if (MultiblockDetector.isCoilMultiblock(ws)) {
            b.canUseCoils = true;
            MultiblockDetector.registerCoilCategory(catId);
        }
        if (MultiblockDetector.isTurbineMachine(ws) && MultiblockDetector.isTurbineRecipeCategory(catId)) {
            b.isTurbine = true;
        }
    }
}



