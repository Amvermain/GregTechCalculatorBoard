package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.bom.BOMHatchTierMode;
import com.gtceu.calcboard.api.bom.MultiblockBOMCalculator;
import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.bom.MultiblockStructurePart;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.testutil.SimulatedGTEnvironment;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class BOMHatchTierModeTest {

    private static final ResourceLocation CONTROLLER_ID = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
    private static final ResourceLocation INERT_CASING = ResourceLocation.tryParse("gtceu:inert_machine_casing");

    private static final ResourceLocation LV_INPUT_BUS = ResourceLocation.tryParse("gtceu:lv_input_bus");
    private static final ResourceLocation MV_INPUT_BUS = ResourceLocation.tryParse("gtceu:mv_input_bus");
    private static final ResourceLocation HV_INPUT_BUS = ResourceLocation.tryParse("gtceu:hv_input_bus");

    private static final ResourceLocation LV_OUTPUT_BUS = ResourceLocation.tryParse("gtceu:lv_output_bus");
    private static final ResourceLocation MV_OUTPUT_BUS = ResourceLocation.tryParse("gtceu:mv_output_bus");
    private static final ResourceLocation HV_OUTPUT_BUS = ResourceLocation.tryParse("gtceu:hv_output_bus");

    private static final ResourceLocation LV_INPUT_HATCH = ResourceLocation.tryParse("gtceu:lv_input_hatch");
    private static final ResourceLocation MV_INPUT_HATCH = ResourceLocation.tryParse("gtceu:mv_input_hatch");
    private static final ResourceLocation HV_INPUT_HATCH = ResourceLocation.tryParse("gtceu:hv_input_hatch");

    private static final ResourceLocation HV_ENERGY_HATCH = ResourceLocation.tryParse("gtceu:hv_energy_input_hatch");

    @BeforeEach
    void setUp() {
        SimulatedGTEnvironment.setupFullEnvironment();

        List<MultiblockStructurePart> parts = List.of(
                new MultiblockStructurePart(CONTROLLER_ID, "Large Chemical Reactor", 1, PartCategory.CONTROLLER),
                new MultiblockStructurePart(INERT_CASING, "Inert Machine Casing", 20, PartCategory.CASING),
                new MultiblockStructurePart(HV_ENERGY_HATCH, "HV Energy Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(HV_INPUT_BUS, "HV Input Bus", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(HV_OUTPUT_BUS, "HV Output Bus", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(HV_INPUT_HATCH, "HV Input Hatch", 1, PartCategory.HATCH_BUS)
        );

        MultiblockStructureDef def = new MultiblockStructureDef(
                CONTROLLER_ID,
                "Large Chemical Reactor",
                parts,
                1, 1, 1, 1, 1, 0, 0,
                Set.of("INPUT_ENERGY", "IMPORT_ITEMS", "EXPORT_ITEMS", "IMPORT_FLUIDS"),
                Set.of(INERT_CASING)
        );
        com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.registerManualStructure(def);
    }

    private RecipeNode createHVReactorNode(int itemCount, int fluidCount) {
        RecipeNode node = RecipeNode.create("HV LCR Recipe", 480.0, 100.0, GTVoltageTier.HV);
        node.setMultiblock(true);
        node.setMachineIcon(CONTROLLER_ID);
        node.setEnergyType(EnergyType.ELECTRIC_EU);
        node.setTargetTier(GTVoltageTier.HV);

        for (int i = 0; i < itemCount; i++) {
            node.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:item_" + i), "Item " + i, 1.0));
            node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:product_" + i), "Product " + i, 1.0));
        }
        for (int i = 0; i < fluidCount; i++) {
            node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:fluid_" + i), "Fluid " + i, 1000.0));
        }
        return node;
    }

    @Test
    @DisplayName("MATCH_MACHINE mode keeps HV tier buses and hatches for HV machine")
    void testMatchMachineMode() {
        RecipeNode node = createHVReactorNode(2, 1);
        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false, BOMHatchTierMode.MATCH_MACHINE);

        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(HV_INPUT_BUS)));
        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(HV_OUTPUT_BUS)));
        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(HV_INPUT_HATCH)));
        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(HV_ENERGY_HATCH)));
    }

    @Test
    @DisplayName("AUTO_MINIMUM mode scales buses and hatches down to LV when 2 items and 1 fluid are used")
    void testAutoMinimumModeSmallRecipe() {
        RecipeNode node = createHVReactorNode(2, 1);
        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false, BOMHatchTierMode.AUTO_MINIMUM);

        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(LV_INPUT_BUS)));
        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(LV_OUTPUT_BUS)));
        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(LV_INPUT_HATCH)));
        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(HV_ENERGY_HATCH)), "Energy hatch stays at HV");
    }

    @Test
    @DisplayName("AUTO_MINIMUM mode scales buses up to MV when 6 items are used")
    void testAutoMinimumModeMediumRecipe() {
        RecipeNode node = createHVReactorNode(6, 1);
        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false, BOMHatchTierMode.AUTO_MINIMUM);

        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(MV_INPUT_BUS)));
        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(MV_OUTPUT_BUS)));
        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(LV_INPUT_HATCH)), "Fluid hatch stays at LV minimum");
    }

    @Test
    @DisplayName("FORCE_LV mode forces all buses and hatches to LV")
    void testForceLVMode() {
        RecipeNode node = createHVReactorNode(6, 1);
        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false, BOMHatchTierMode.FORCE_LV);

        var inBus = summary.aggregatedItems().stream().filter(e -> e.itemId().equals(LV_INPUT_BUS)).findFirst();
        Assertions.assertTrue(inBus.isPresent());
        Assertions.assertEquals(2, inBus.get().totalAmount(), "6 items need 2 LV buses (4 slots each)");

        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(LV_INPUT_HATCH)));
    }

    @Test
    @DisplayName("FORCE_MV mode forces all buses and hatches to MV")
    void testForceMVMode() {
        RecipeNode node = createHVReactorNode(2, 1);
        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false, BOMHatchTierMode.FORCE_MV);

        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(MV_INPUT_BUS)));
        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(MV_OUTPUT_BUS)));
        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(MV_INPUT_HATCH)));
    }

    @Test
    @DisplayName("FORCE_HV mode forces all buses and hatches to HV")
    void testForceHVMode() {
        RecipeNode node = createHVReactorNode(2, 1);
        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false, BOMHatchTierMode.FORCE_HV);

        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(HV_INPUT_BUS)));
        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(HV_OUTPUT_BUS)));
        Assertions.assertTrue(summary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(HV_INPUT_HATCH)));
    }
}
