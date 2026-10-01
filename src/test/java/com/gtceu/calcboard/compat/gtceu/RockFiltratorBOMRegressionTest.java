package com.gtceu.calcboard.compat.gtceu;

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

public class RockFiltratorBOMRegressionTest {

    private static final ResourceLocation RF_ID = ResourceLocation.tryParse("gtceu:rock_filtrator");
    private static final ResourceLocation SOLID_CASING = ResourceLocation.tryParse("gtceu:solid_machine_casing");
    private static final ResourceLocation MV_BUS = ResourceLocation.tryParse("gtceu:mv_output_bus");
    private static final ResourceLocation LV_BUS = ResourceLocation.tryParse("gtceu:lv_output_bus");

    @BeforeEach
    void setUp() {
        SimulatedGTEnvironment.setupFullEnvironment();

        List<MultiblockStructurePart> parts = List.of(
                new MultiblockStructurePart(RF_ID, "Rock Filtrator", 1, PartCategory.CONTROLLER),
                new MultiblockStructurePart(SOLID_CASING, "Solid Machine Casing", 12, PartCategory.CASING),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:tempered_glass"), "Tempered Glass", 8, PartCategory.CASING),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:steel_firebox_casing"), "Steel Firebox Casing", 7, PartCategory.CASING),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:cupronickel_coil_block"), "Cupronickel Coil", 1, PartCategory.COIL),
                new MultiblockStructurePart(LV_BUS, "LV Output Bus", 2, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_energy_input_hatch"), "LV Energy Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_input_bus"), "LV Input Bus", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_input_hatch"), "LV Input Hatch", 1, PartCategory.HATCH_BUS)
        );

        MultiblockStructureDef rfDef = new MultiblockStructureDef(
                RF_ID,
                "Rock Filtrator",
                parts,
                1, 1, 1, 2, 1, 0, 0,
                java.util.Set.of("INPUT_ENERGY", "IMPORT_ITEMS", "EXPORT_ITEMS", "IMPORT_FLUIDS"),
                java.util.Set.of(SOLID_CASING)
        );
        com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.registerManualStructure(rfDef);
    }

    @Test
    @DisplayName("Rock Filtrator producing 6 items at MV tier should require only 1 MV Output Bus (9 slots) and restore 1 Casing")
    void testRockFiltratorMVRequiresSingleOutputBus() {
        RecipeNode node = RecipeNode.create("Rock Filtrator (Sand)", 48.0, 60.0, GTVoltageTier.MV);
        node.setMultiblock(true);
        node.setMachineIcon(RF_ID);
        node.setEnergyType(EnergyType.ELECTRIC_EU);
        node.setTargetTier(GTVoltageTier.MV);

        node.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:sand"), "Sand", 1.0));
        node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0));

        for (int i = 0; i < 6; i++) {
            node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:gem_" + i), "Gem " + i, 1.0));
        }

        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);

        MultiblockBOMSummary.BOMItemEntry busEntry = summary.aggregatedItems().stream()
                .filter(e -> e.itemId().equals(MV_BUS))
                .findFirst().orElse(null);
        Assertions.assertNotNull(busEntry, "MV Output Bus must be present in BOM");
        Assertions.assertEquals(1, busEntry.totalAmount(), "6 items fit in 1 MV Output Bus (9 slots)");

        MultiblockBOMSummary.BOMItemEntry casingEntry = summary.aggregatedItems().stream()
                .filter(e -> e.itemId().equals(SOLID_CASING))
                .findFirst().orElse(null);
        Assertions.assertNotNull(casingEntry, "Solid Machine Casing must be present in BOM");
        Assertions.assertEquals(13, casingEntry.totalAmount(), "1 reduced output bus should restore 1 casing (12 + 1 = 13)");

        int totalBlocks = summary.aggregatedItems().stream().mapToInt(MultiblockBOMSummary.BOMItemEntry::totalAmount).sum();
        Assertions.assertEquals(34, totalBlocks, "Total structure block count must be preserved");
    }

    @Test
    @DisplayName("Rock Filtrator producing 6 items at LV tier should require 2 LV Output Buses (4 slots each)")
    void testRockFiltratorLVRequiresTwoOutputBuses() {
        RecipeNode node = RecipeNode.create("Rock Filtrator (Sand LV)", 48.0, 30.0, GTVoltageTier.LV);
        node.setMultiblock(true);
        node.setMachineIcon(RF_ID);
        node.setEnergyType(EnergyType.ELECTRIC_EU);
        node.setTargetTier(GTVoltageTier.LV);

        node.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:sand"), "Sand", 1.0));
        for (int i = 0; i < 6; i++) {
            node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:gem_" + i), "Gem " + i, 1.0));
        }

        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);

        MultiblockBOMSummary.BOMItemEntry busEntry = summary.aggregatedItems().stream()
                .filter(e -> e.itemId().equals(LV_BUS))
                .findFirst().orElse(null);
        Assertions.assertNotNull(busEntry);
        Assertions.assertEquals(2, busEntry.totalAmount(), "6 items require 2 LV Output Buses (4 slots each = 8 slots)");

        MultiblockBOMSummary.BOMItemEntry casingEntry = summary.aggregatedItems().stream()
                .filter(e -> e.itemId().equals(SOLID_CASING))
                .findFirst().orElse(null);
        Assertions.assertNotNull(casingEntry);
        Assertions.assertEquals(12, casingEntry.totalAmount(), "Casing count remains 12 when 2 buses are used");
    }
}
