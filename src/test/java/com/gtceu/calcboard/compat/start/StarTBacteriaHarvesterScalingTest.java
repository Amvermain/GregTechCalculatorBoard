package com.gtceu.calcboard.compat.start;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.ProjectedPort;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.compat.start.projection.StarTPortProjector;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StarTBacteriaHarvesterScalingTest {

    private static final ResourceLocation BHH_RECIPE_TYPE = ResourceLocation.tryParse("gtceu:bacterial_hydrocarbon_harvester");
    private static final ResourceLocation BHH_MACHINE_ICON = ResourceLocation.tryParse("start_core:bacterial_hydrocarbon_harvester");
    private static final ResourceLocation SUGAR_ID = ResourceLocation.tryParse("minecraft:sugar");
    private static final ResourceLocation BIOMASS_ID = ResourceLocation.tryParse("gtceu:biomass");
    private static final ResourceLocation DISTILLED_WATER_ID = ResourceLocation.tryParse("gtceu:distilled_water");
    private static final ResourceLocation BACTERIA_ITEM_ID = ResourceLocation.tryParse("start_core:bacteria_bituminimonas_combustilis");

    private static final ResourceLocation TOLUENE_ID = ResourceLocation.tryParse("gtceu:toluene");
    private static final ResourceLocation BUTADIENE_ID = ResourceLocation.tryParse("gtceu:butadiene");
    private static final ResourceLocation PROPENE_ID = ResourceLocation.tryParse("gtceu:propene");
    private static final ResourceLocation SUPERFLUID_ID = ResourceLocation.tryParse("start_core:bituminimonas");
    private static final ResourceLocation BACTERIA_FLUID_ID = ResourceLocation.tryParse("gtceu:bacteria");

    @BeforeEach
    public void setup() {
        ModAdapterRegistry.init();
    }

    private RecipeNode createHarvesterNode() {
        RecipeNode node = RecipeNode.create("bhh_test", 160.0, 65536.0, com.gtceu.calcboard.api.type.GTVoltageTier.ZPM);
        node.setMachineIcon(BHH_MACHINE_ICON);
        node.setRecipeCategoryId(BHH_RECIPE_TYPE);

        List<IngredientStack> inputs = List.of(
                IngredientStack.item(BACTERIA_ITEM_ID, "Bacteria Colony", 1),
                IngredientStack.item(SUGAR_ID, "Sugar", 32),
                IngredientStack.fluid(BIOMASS_ID, "Biomass", 3200),
                IngredientStack.fluid(DISTILLED_WATER_ID, "Distilled Water", 1000)
        );

        List<IngredientStack> outputs = List.of(
                IngredientStack.fluid(TOLUENE_ID, "Toluene", 7500),
                IngredientStack.fluid(BUTADIENE_ID, "Butadiene", 3750),
                IngredientStack.fluid(PROPENE_ID, "Propene", 1250),
                IngredientStack.fluid(SUPERFLUID_ID, "Superfluid", 5000),
                IngredientStack.fluid(BACTERIA_FLUID_ID, "Bacteria Liquid", 2500)
        );

        for (IngredientStack in : inputs) {
            node.addInput(in.copy());
        }
        for (IngredientStack out : outputs) {
            node.addOutput(out.copy());
        }

        com.gtceu.calcboard.api.model.RecipeSpec spec = new com.gtceu.calcboard.api.model.RecipeSpec(
                "harvest_recipe", BHH_RECIPE_TYPE, 160.0, 65536.0, inputs, outputs
        );
        node.setBaseSpec(spec);
        node.syncProjectedPorts();
        return node;
    }

    @Test
    public void testHarvesterDetection() {
        RecipeNode node = createHarvesterNode();
        assertTrue(StarTPortProjector.isBacterialHarvester(node));

        RecipeNode normalNode = RecipeNode.create("normal", 100.0, 30.0, com.gtceu.calcboard.api.type.GTVoltageTier.LV);
        normalNode.setMachineIcon(ResourceLocation.tryParse("gtceu:centrifuge"));
        normalNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:centrifuge"));
        assertFalse(StarTPortProjector.isBacterialHarvester(normalNode));
    }

    @Test
    public void testDefaultStatsScaling() {
        RecipeNode node = createHarvesterNode();

        assertEquals(5, StarTBacterialProperties.getProduction(node));
        assertEquals(5, StarTBacterialProperties.getMetabolism(node));

        List<ProjectedPort> inputs = node.getProjectedInputs();
        assertEquals(4, inputs.size());
        assertEquals(1.0, inputs.get(0).stack().getAmount(), 0.001); // Bacteria 1
        assertEquals(32.0, inputs.get(1).stack().getAmount(), 0.001); // Sugar 32 (2^5)
        assertEquals(3200.0, inputs.get(2).stack().getAmount(), 0.001); // Biomass 3200 (100 * 2^5)
        assertEquals(1000.0, inputs.get(3).stack().getAmount(), 0.001); // Distilled water 1000

        List<ProjectedPort> outputs = node.getProjectedOutputs();
        assertEquals(5, outputs.size());
        assertEquals(7500.0, outputs.get(0).stack().getAmount(), 0.001); // Toluene (1500 * 5)
        assertEquals(3750.0, outputs.get(1).stack().getAmount(), 0.001); // Butadiene (750 * 5)
        assertEquals(1250.0, outputs.get(2).stack().getAmount(), 0.001); // Propene (250 * 5)
        assertEquals(5000.0, outputs.get(3).stack().getAmount(), 0.001); // Superfluid (1000 * 5)
        assertEquals(2500.0, outputs.get(4).stack().getAmount(), 0.001); // Bacteria (500 * 5)
    }

    @Test
    public void testPerfectStatsScaling() {
        RecipeNode node = createHarvesterNode();

        StarTBacterialProperties.setProduction(node, 5);
        StarTBacterialProperties.setMetabolism(node, 1);
        node.syncProjectedPorts();

        List<ProjectedPort> inputs = node.getProjectedInputs();
        assertEquals(1.0, inputs.get(0).stack().getAmount(), 0.001); // Bacteria 1
        assertEquals(2.0, inputs.get(1).stack().getAmount(), 0.001); // Sugar 2 (2^1 = 1/16 of 32)
        assertEquals(200.0, inputs.get(2).stack().getAmount(), 0.001); // Biomass 200 (100 * 2^1 = 1/16 of 3200)
        assertEquals(1000.0, inputs.get(3).stack().getAmount(), 0.001); // Distilled water 1000

        List<ProjectedPort> outputs = node.getProjectedOutputs();
        assertEquals(7500.0, outputs.get(0).stack().getAmount(), 0.001);
        assertEquals(3750.0, outputs.get(1).stack().getAmount(), 0.001);
        assertEquals(1250.0, outputs.get(2).stack().getAmount(), 0.001);
        assertEquals(5000.0, outputs.get(3).stack().getAmount(), 0.001);
        assertEquals(2500.0, outputs.get(4).stack().getAmount(), 0.001);
    }

    @Test
    public void testIntermediateStatsScaling() {
        RecipeNode node = createHarvesterNode();

        StarTBacterialProperties.setProduction(node, 3);
        StarTBacterialProperties.setMetabolism(node, 2);
        node.syncProjectedPorts();

        List<ProjectedPort> inputs = node.getProjectedInputs();
        assertEquals(4.0, inputs.get(1).stack().getAmount(), 0.001); // Sugar 4 (2^2)
        assertEquals(400.0, inputs.get(2).stack().getAmount(), 0.001); // Biomass 400 (100 * 2^2)

        List<ProjectedPort> outputs = node.getProjectedOutputs();
        assertEquals(4500.0, outputs.get(0).stack().getAmount(), 0.001); // 1500 * 3
        assertEquals(2250.0, outputs.get(1).stack().getAmount(), 0.001); // 750 * 3
        assertEquals(750.0, outputs.get(2).stack().getAmount(), 0.001);  // 250 * 3
        assertEquals(3000.0, outputs.get(3).stack().getAmount(), 0.001); // 1000 * 3
        assertEquals(1500.0, outputs.get(4).stack().getAmount(), 0.001); // 500 * 3
    }

    @Test
    public void testClampingAndDefense() {
        RecipeNode node = createHarvesterNode();

        StarTBacterialProperties.setProduction(node, -10);
        StarTBacterialProperties.setMetabolism(node, 99);
        node.syncProjectedPorts();

        assertEquals(1, StarTBacterialProperties.getProduction(node));
        assertEquals(5, StarTBacterialProperties.getMetabolism(node));

        List<ProjectedPort> inputs = node.getProjectedInputs();
        assertEquals(32.0, inputs.get(1).stack().getAmount(), 0.001); // Clamped to 5 -> 32

        List<ProjectedPort> outputs = node.getProjectedOutputs();
        assertEquals(1500.0, outputs.get(0).stack().getAmount(), 0.001); // Clamped to 1 -> 1500
    }

    @Test
    public void testCopyPreservesStats() {
        RecipeNode node = createHarvesterNode();
        StarTBacterialProperties.setProduction(node, 4);
        StarTBacterialProperties.setMetabolism(node, 2);
        node.syncProjectedPorts();

        RecipeNode copied = node.copy();
        assertEquals(4, StarTBacterialProperties.getProduction(copied));
        assertEquals(2, StarTBacterialProperties.getMetabolism(copied));

        List<ProjectedPort> copiedInputs = copied.getProjectedInputs();
        assertEquals(4.0, copiedInputs.get(1).stack().getAmount(), 0.001);
        assertEquals(400.0, copiedInputs.get(2).stack().getAmount(), 0.001);

        List<ProjectedPort> copiedOutputs = copied.getProjectedOutputs();
        assertEquals(6000.0, copiedOutputs.get(0).stack().getAmount(), 0.001);
    }
}
