package com.gtceu.calcboard.testutil;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.bom.MultiblockStructurePart;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SimulatedGTEnvironment {

    public static final ResourceLocation DISTILLATION_TOWER_ID = ResourceLocation.tryParse("gtceu:distillation_tower");
    public static final ResourceLocation STEAM_TURBINE_ID = ResourceLocation.tryParse("gtceu:large_steam_turbine");
    public static final ResourceLocation STEAM_TURBINE_CAT = ResourceLocation.tryParse("gtceu:steam_turbine");
    public static final ResourceLocation GAS_TURBINE_ID = ResourceLocation.tryParse("gtceu:large_gas_turbine");
    public static final ResourceLocation GAS_TURBINE_CAT = ResourceLocation.tryParse("gtceu:gas_turbine");
    public static final ResourceLocation PLASMA_TURBINE_ID = ResourceLocation.tryParse("gtceu:large_plasma_turbine");
    public static final ResourceLocation PLASMA_TURBINE_CAT = ResourceLocation.tryParse("gtceu:plasma_turbine");
    public static final ResourceLocation EBF_ID = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
    public static final ResourceLocation ROTOR_CASING_MACHINE_ID = ResourceLocation.tryParse("gtceu:rotor_casing_machine");
    public static final ResourceLocation LFD_ID = ResourceLocation.tryParse("gtceu:large_fractionating_distillery");

    private SimulatedGTEnvironment() {}

    public static void setupFullEnvironment() {
        ModAdapterRegistry.init();
        MultiblockStructureCatalog.clear();
        CategoryCapabilityMatrix.getInstance().reset();
        MultiblockDetector.reinitialize();

        TestMultiblockFixtures.initTestEnvironmentDefaults();
        registerSimulatedStructures();
        registerSimulatedCapabilities();
    }

    private static void registerSimulatedStructures() {
        MultiblockStructureCatalog.registerManualStructure(buildDistillationTowerDef());
        MultiblockStructureCatalog.registerManualStructure(buildLargeSteamTurbineDef());
        MultiblockStructureCatalog.registerManualStructure(buildLargeGasTurbineDef());
        MultiblockStructureCatalog.registerManualStructure(buildLargePlasmaTurbineDef());
        MultiblockStructureCatalog.registerManualStructure(buildEbfDef());
        MultiblockStructureCatalog.registerManualStructure(buildRotorCasingMachineDef());

        MultiblockDetector.registerMultiblock(DISTILLATION_TOWER_ID);
        MultiblockDetector.registerBatchModeMultiblock(DISTILLATION_TOWER_ID);

        MultiblockStructureCatalog.registerManualStructure(buildLfdDef());
        MultiblockDetector.registerMultiblock(LFD_ID);
        MultiblockDetector.registerParallelHatchController(LFD_ID);
    }

    private static MultiblockStructureDef buildDistillationTowerDef() {
        List<MultiblockStructurePart> parts = new ArrayList<>();
        parts.add(new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:clean_stainless_steel_casing"), "Clean Stainless Steel Casing", 48, PartCategory.CASING));
        parts.add(new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_energy_input_hatch"), "EV Energy Input Hatch", 1, PartCategory.HATCH_BUS));
        parts.add(new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:maintenance_hatch"), "Maintenance Hatch", 1, PartCategory.HATCH_BUS));
        parts.add(new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_input_hatch"), "EV Input Hatch", 1, PartCategory.HATCH_BUS));
        parts.add(new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_output_hatch"), "EV Output Hatch", 11, PartCategory.HATCH_BUS));
        parts.add(new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_output_bus"), "EV Output Bus", 1, PartCategory.HATCH_BUS));

        Set<String> abilities = Set.of("IMPORT_FLUIDS", "EXPORT_FLUIDS", "EXPORT_FLUIDS_1X", "EXPORT_ITEMS", "INPUT_ENERGY", "MAINTENANCE", "BATCH_MODE");
        Set<ResourceLocation> candidates = Set.of(
                ResourceLocation.tryParse("gtceu:clean_stainless_steel_casing"),
                ResourceLocation.tryParse("gtceu:ev_energy_input_hatch"),
                ResourceLocation.tryParse("gtceu:maintenance_hatch"),
                ResourceLocation.tryParse("gtceu:ev_input_hatch"),
                ResourceLocation.tryParse("gtceu:ev_output_hatch"),
                ResourceLocation.tryParse("gtceu:ev_output_bus")
        );

        return new MultiblockStructureDef(
                DISTILLATION_TOWER_ID,
                "Distillation Tower",
                parts,
                0, 1, 0, 1, 1, 11, 1,
                abilities,
                candidates
        );
    }

    private static MultiblockStructureDef buildLargeSteamTurbineDef() {
        List<MultiblockStructurePart> parts = new ArrayList<>();
        parts.add(new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:turbine_casing"), "Turbine Casing", 24, PartCategory.CASING));
        parts.add(new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:rotor_holder"), "Rotor Holder", 1, PartCategory.OTHER));
        parts.add(new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:hv_dynamo_hatch"), "HV Dynamo Hatch", 1, PartCategory.HATCH_BUS));
        parts.add(new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:maintenance_hatch"), "Maintenance Hatch", 1, PartCategory.HATCH_BUS));

        Set<String> abilities = Set.of("ROTOR_HOLDER", "MAINTENANCE", "OUTPUT_ENERGY");
        Set<ResourceLocation> candidates = Set.of(
                ResourceLocation.tryParse("gtceu:turbine_casing"),
                ResourceLocation.tryParse("gtceu:rotor_holder"),
                ResourceLocation.tryParse("gtceu:hv_dynamo_hatch"),
                ResourceLocation.tryParse("gtceu:maintenance_hatch")
        );

        return new MultiblockStructureDef(
                STEAM_TURBINE_ID,
                "Large Steam Turbine",
                parts,
                0, 1, 0, 0, 1, 1, 1,
                abilities,
                candidates
        );
    }

    private static MultiblockStructureDef buildLargeGasTurbineDef() {
        return new MultiblockStructureDef(
                GAS_TURBINE_ID,
                "Large Gas Turbine",
                List.of(),
                0, 1, 0, 0, 1, 1, 1,
                Set.of("ROTOR_HOLDER", "MAINTENANCE", "OUTPUT_ENERGY"),
                Set.of()
        );
    }

    private static MultiblockStructureDef buildLargePlasmaTurbineDef() {
        return new MultiblockStructureDef(
                PLASMA_TURBINE_ID,
                "Large Plasma Turbine",
                List.of(),
                0, 1, 0, 0, 1, 1, 1,
                Set.of("ROTOR_HOLDER", "MAINTENANCE", "OUTPUT_ENERGY"),
                Set.of()
        );
    }

    private static MultiblockStructureDef buildEbfDef() {
        return new MultiblockStructureDef(
                EBF_ID,
                "Electric Blast Furnace",
                List.of(),
                16, 2, 1, 1, 1, 1, 1,
                Set.of("HEATING_COILS", "IMPORT_ITEMS", "EXPORT_ITEMS", "IMPORT_FLUIDS", "EXPORT_FLUIDS", "INPUT_ENERGY", "MAINTENANCE"),
                Set.of()
        );
    }

    private static MultiblockStructureDef buildRotorCasingMachineDef() {
        return new MultiblockStructureDef(
                ROTOR_CASING_MACHINE_ID,
                "Rotor Casing Machine",
                List.of(),
                0, 1, 1, 1, 0, 0, 1,
                Set.of("ROTOR_HOLDER", "INPUT_ENERGY", "MAINTENANCE"),
                Set.of()
        );
    }

    private static MultiblockStructureDef buildLfdDef() {
        Set<String> abilities = Set.of("IMPORT_FLUIDS", "EXPORT_FLUIDS", "INPUT_ENERGY", "MAINTENANCE", "BATCH_MODE", "PARALLEL_HATCH");
        return new MultiblockStructureDef(
                LFD_ID,
                "Large Fractionating Distillery",
                List.of(),
                0, 1, 1, 1, 1, 1, 1,
                abilities,
                Set.of()
        );
    }

    private static void registerSimulatedCapabilities() {
        CategoryCapabilityMatrix matrix = CategoryCapabilityMatrix.getInstance();
        matrix.registerMockCategory(
                DISTILLATION_TOWER_ID,
                List.of(DISTILLATION_TOWER_ID, LFD_ID),
                DISTILLATION_TOWER_ID,
                false, true, false, false, false, false, false, null, null, null, 0.0
        );
        matrix.registerMockCategory(
                STEAM_TURBINE_CAT,
                List.of(
                        ResourceLocation.tryParse("gtceu:lv_steam_turbine"),
                        ResourceLocation.tryParse("gtceu:mv_steam_turbine"),
                        ResourceLocation.tryParse("gtceu:hv_steam_turbine"),
                        STEAM_TURBINE_ID
                ),
                STEAM_TURBINE_ID,
                false, true, false, true, false, false, false, null, null, GTVoltageTier.HV, 1024.0
        );
        matrix.registerMockCategory(
                GAS_TURBINE_CAT,
                List.of(
                        ResourceLocation.tryParse("gtceu:mv_gas_turbine"),
                        ResourceLocation.tryParse("gtceu:hv_gas_turbine"),
                        ResourceLocation.tryParse("gtceu:ev_gas_turbine"),
                        GAS_TURBINE_ID
                ),
                GAS_TURBINE_ID,
                false, true, false, true, false, false, false, null, null, GTVoltageTier.EV, 4096.0
        );
        matrix.registerMockCategory(
                PLASMA_TURBINE_CAT,
                List.of(
                        ResourceLocation.tryParse("gtceu:iv_plasma_turbine"),
                        ResourceLocation.tryParse("gtceu:luv_plasma_turbine"),
                        ResourceLocation.tryParse("gtceu:zpm_plasma_turbine"),
                        PLASMA_TURBINE_ID
                ),
                PLASMA_TURBINE_ID,
                false, true, false, true, false, false, false, null, null, GTVoltageTier.IV, 16384.0
        );
    }

    public static RecipeNode createDistillationTowerNode() {
        RecipeNode node = RecipeNode.create(DISTILLATION_TOWER_ID, "Distillation Tower (Crude Oil)", 320.0, 64.0, GTVoltageTier.MV);
        node.setRecipeCategoryId(DISTILLATION_TOWER_ID);
        node.setMultiblock(true);
        node.setGenerator(false);
        node.setEnergyType(EnergyType.ELECTRIC_EU);
        return node;
    }

    public static RecipeNode createLargeSteamTurbineNode() {
        RecipeNode node = RecipeNode.create(STEAM_TURBINE_ID, "Large Steam Turbine", 20.0, 1024.0, GTVoltageTier.HV);
        node.setRecipeCategoryId(STEAM_TURBINE_CAT);
        node.setMultiblock(true);
        node.setGenerator(true);
        node.setEnergyType(EnergyType.ELECTRIC_EU);
        return node;
    }

    public static RecipeNode createRotorCasingMachineNode() {
        RecipeNode node = RecipeNode.create(ROTOR_CASING_MACHINE_ID, "Rotor Casing Machine", 100.0, 128.0, GTVoltageTier.MV);
        node.setRecipeCategoryId(ROTOR_CASING_MACHINE_ID);
        node.setMultiblock(true);
        node.setGenerator(false);
        node.setEnergyType(EnergyType.ELECTRIC_EU);
        return node;
    }
}
