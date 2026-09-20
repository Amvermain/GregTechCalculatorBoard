package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.solver.MassBalanceSolver;
import com.gtceu.calcboard.api.storage.NodeClipboard;
import com.gtceu.calcboard.api.storage.RecipeNodeSerializer;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecipeNodeManualOverrideTest {

    @Test
    @DisplayName("Manual duration and power overrides update node parameters and flag manual state")
    void testManualDurationAndPowerOverride() {
        RecipeNode node = RecipeNode.create("Centrifuge", 100.0, 30.0, GTVoltageTier.LV);
        Assertions.assertFalse(node.isManualOverride());
        Assertions.assertFalse(node.hasOriginalRecipeSpec());

        node.overrideDurationTicks(40.0);
        Assertions.assertTrue(node.isManualOverride());
        Assertions.assertTrue(node.hasOriginalRecipeSpec());
        Assertions.assertEquals(40.0, node.getBaseDurationTicks(), 0.001);

        node.overrideBaseEUt(128.0, false);
        Assertions.assertEquals(128.0, node.getBaseEUt(), 0.001);
        Assertions.assertFalse(node.isGenerator());
        Assertions.assertEquals(GTVoltageTier.MV, node.getRecipeTier());
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, node.getEnergyType());

        node.overrideBaseEUt(512.0, true);
        Assertions.assertEquals(512.0, node.getBaseEUt(), 0.001);
        Assertions.assertTrue(node.isGenerator());
        Assertions.assertEquals(GTVoltageTier.HV, node.getRecipeTier());
    }

    @Test
    @DisplayName("Ingredient quantity override propagates to inputs, outputs, and projected ports")
    void testIngredientAmountOverrideAndPortSync() {
        RecipeNode node = RecipeNode.create("Chemical Reactor", 20.0, 30.0, GTVoltageTier.LV);
        IngredientStack inStack = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:water"), "Water", 1000.0);
        IngredientStack outStack = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 500.0);
        node.addInput(inStack);
        node.addOutput(outStack);

        node.ensurePortsProjected();
        Assertions.assertEquals(1000.0, node.getInputs().get(0).getAmount(), 0.001);
        Assertions.assertEquals(500.0, node.getOutputs().get(0).getAmount(), 0.001);

        node.overrideInputAmount(0, 2500.0);
        Assertions.assertTrue(node.isManualOverride());
        Assertions.assertEquals(2500.0, node.getInputs().get(0).getAmount(), 0.001);
        Assertions.assertEquals(2500.0, node.getProjectedInputs().get(0).stack().getAmount(), 0.001);

        node.overrideOutputAmount(0, 1250.0);
        Assertions.assertEquals(1250.0, node.getOutputs().get(0).getAmount(), 0.001);
        Assertions.assertEquals(1250.0, node.getProjectedOutputs().get(0).stack().getAmount(), 0.001);
    }

    @Test
    @DisplayName("Reset to original recipe cleanly restores initial duration, power, and ingredient amounts")
    void testResetToOriginalRecipe() {
        RecipeNode node = RecipeNode.create("Mixer", 100.0, 30.0, GTVoltageTier.LV);
        node.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 2.0));
        node.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_nugget"), "Iron Nugget", 18.0));

        node.overrideDurationTicks(20.0);
        node.overrideBaseEUt(480.0, false);
        node.overrideInputAmount(0, 5.0);
        node.overrideOutputAmount(0, 45.0);

        Assertions.assertTrue(node.isManualOverride());
        Assertions.assertEquals(20.0, node.getBaseDurationTicks(), 0.001);
        Assertions.assertEquals(480.0, node.getBaseEUt(), 0.001);
        Assertions.assertEquals(5.0, node.getInputs().get(0).getAmount(), 0.001);
        Assertions.assertEquals(45.0, node.getOutputs().get(0).getAmount(), 0.001);

        boolean resetSuccess = node.resetToOriginalRecipe();
        Assertions.assertTrue(resetSuccess);
        Assertions.assertFalse(node.isManualOverride());
        Assertions.assertFalse(node.hasOriginalRecipeSpec());
        Assertions.assertEquals(100.0, node.getBaseDurationTicks(), 0.001);
        Assertions.assertEquals(30.0, node.getBaseEUt(), 0.001);
        Assertions.assertEquals(2.0, node.getInputs().get(0).getAmount(), 0.001);
        Assertions.assertEquals(18.0, node.getOutputs().get(0).getAmount(), 0.001);
        Assertions.assertEquals(2.0, node.getProjectedInputs().get(0).stack().getAmount(), 0.001);
        Assertions.assertEquals(18.0, node.getProjectedOutputs().get(0).stack().getAmount(), 0.001);
    }

    @Test
    @DisplayName("MassBalanceSolver automatically respects overridden duration and ingredient rates")
    void testMassBalanceSolverWithOverriddenNode() {
        FlowGraph graph = new FlowGraph();

        RecipeNode producer = RecipeNode.create("Producer", 20.0, 30.0, GTVoltageTier.LV);
        producer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 1000.0));

        RecipeNode consumer = RecipeNode.create("Consumer", 20.0, 30.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 1000.0));

        graph.addNode(producer);
        graph.addNode(consumer);
        graph.addConnection(new FlowGraph.ConnectionEdge(producer.getId(), 0, consumer.getId(), 0));

        Map<String, Double> initialResult = MassBalanceSolver.solve(graph, consumer, 1.0);
        Assertions.assertEquals(1.0, initialResult.get(producer.getId()), 0.001);

        producer.overrideOutputAmount(0, 2000.0);
        Map<String, Double> overriddenAmountResult = MassBalanceSolver.solve(graph, consumer, 1.0);
        Assertions.assertEquals(0.5, overriddenAmountResult.get(producer.getId()), 0.001);

        consumer.overrideDurationTicks(10.0);
        Map<String, Double> overriddenDurationResult = MassBalanceSolver.solve(graph, consumer, 1.0);
        Assertions.assertEquals(1.0, overriddenDurationResult.get(producer.getId()), 0.001);
    }

    @Test
    @DisplayName("NBT serialization and deserialization preserves manual overrides and allows clean reset")
    void testNbtSerializationPreservesOverrideAndOriginalSpec() {
        RecipeNode original = RecipeNode.create("Arc Furnace", 80.0, 60.0, GTVoltageTier.LV);
        original.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper", 3.0));
        original.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:annealed_copper_ingot"), "Annealed Copper", 3.0));

        original.overrideDurationTicks(40.0);
        original.overrideBaseEUt(120.0, false);
        original.overrideInputAmount(0, 6.0);

        CompoundTag serialized = RecipeNodeSerializer.serialize(original);
        RecipeNode deserialized = RecipeNodeSerializer.deserialize(serialized);

        Assertions.assertNotNull(deserialized);
        Assertions.assertTrue(deserialized.isManualOverride());
        Assertions.assertTrue(deserialized.hasOriginalRecipeSpec());
        Assertions.assertEquals(40.0, deserialized.getBaseDurationTicks(), 0.001);
        Assertions.assertEquals(120.0, deserialized.getBaseEUt(), 0.001);
        Assertions.assertEquals(6.0, deserialized.getInputs().get(0).getAmount(), 0.001);

        boolean reset = deserialized.resetToOriginalRecipe();
        Assertions.assertTrue(reset);
        Assertions.assertFalse(deserialized.isManualOverride());
        Assertions.assertEquals(80.0, deserialized.getBaseDurationTicks(), 0.001);
        Assertions.assertEquals(60.0, deserialized.getBaseEUt(), 0.001);
        Assertions.assertEquals(3.0, deserialized.getInputs().get(0).getAmount(), 0.001);
    }

    @Test
    @DisplayName("Node clipboard copy and paste preserves manual overrides and original spec")
    void testNodeClipboardCopyAndPastePreservesOverride() {
        FlowGraph sourceGraph = new FlowGraph();
        RecipeNode node = RecipeNode.create("Electrolyzer", 150.0, 90.0, GTVoltageTier.MV);
        node.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:water"), "Water", 1000.0));
        node.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:hydrogen"), "Hydrogen", 2000.0));
        sourceGraph.addNode(node);

        node.overrideDurationTicks(50.0);
        node.overrideOutputAmount(0, 3000.0);

        NodeClipboard.getInstance().copy(sourceGraph, Set.of(node.getId()));
        FlowGraph targetGraph = new FlowGraph();
        NodeClipboard.PasteResult pasteResult = NodeClipboard.getInstance().paste(targetGraph, 100.0, 100.0);

        Assertions.assertEquals(1, pasteResult.nodes().size());
        RecipeNode pastedNode = pasteResult.nodes().get(0);
        Assertions.assertTrue(pastedNode.isManualOverride());
        Assertions.assertTrue(pastedNode.hasOriginalRecipeSpec());
        Assertions.assertEquals(50.0, pastedNode.getBaseDurationTicks(), 0.001);
        Assertions.assertEquals(3000.0, pastedNode.getOutputs().get(0).getAmount(), 0.001);

        Assertions.assertTrue(pastedNode.resetToOriginalRecipe());
        Assertions.assertEquals(150.0, pastedNode.getBaseDurationTicks(), 0.001);
        Assertions.assertEquals(2000.0, pastedNode.getOutputs().get(0).getAmount(), 0.001);
    }

    @Test
    @DisplayName("Boundary clamping and invalid indices are handled safely without exceptions")
    void testBoundaryClampingAndInvalidIndices() {
        RecipeNode node = RecipeNode.create("Bender", 40.0, 24.0, GTVoltageTier.LV);
        node.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 1.0));

        node.overrideDurationTicks(-10.0);
        Assertions.assertEquals(1.0, node.getBaseDurationTicks(), 0.001);

        node.overrideInputAmount(0, -5.0);
        Assertions.assertEquals(0.0001, node.getInputs().get(0).getAmount(), 0.00001);

        node.overrideInputAmount(-1, 10.0);
        node.overrideInputAmount(999, 10.0);
        node.overrideOutputAmount(-1, 10.0);
        node.overrideOutputAmount(999, 10.0);

        RecipeNode freshNode = RecipeNode.create("Furnace", 20.0, 10.0, GTVoltageTier.LV);
        Assertions.assertFalse(freshNode.resetToOriginalRecipe());
    }

    @Test
    @DisplayName("Resetting to original recipe cleanly restores generator state, energy type, and voltage tier")
    void testResetRestoresGeneratorStateEnergyTypeAndTier() {
        RecipeNode genNode = RecipeNode.create("Diesel Generator", 20.0, 128.0, GTVoltageTier.MV);
        genNode.setGenerator(true);
        genNode.setEnergyType(EnergyType.ELECTRIC_EU);
        genNode.setRecipeTier(GTVoltageTier.MV);

        genNode.overrideBaseEUt(0.0, false);
        Assertions.assertTrue(genNode.isManualOverride());
        Assertions.assertFalse(genNode.isGenerator());
        Assertions.assertEquals(EnergyType.NONE, genNode.getEnergyType());

        boolean reset = genNode.resetToOriginalRecipe();
        Assertions.assertTrue(reset);
        Assertions.assertFalse(genNode.isManualOverride());
        Assertions.assertTrue(genNode.isGenerator());
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, genNode.getEnergyType());
        Assertions.assertEquals(GTVoltageTier.MV, genNode.getRecipeTier());
        Assertions.assertEquals(128.0, genNode.getBaseEUt(), 0.001);
    }

    @Test
    @DisplayName("Auxiliary projected ports cannot be corrupted by manual override and core ports map correctly")
    void testAuxiliaryPortMappingAndProtection() {
        RecipeNode engine = RecipeNode.create("Combustion Engine", 72.0, 160.0, GTVoltageTier.MV);
        engine.setMachineIcon(com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.LARGE_COMBUSTION_ENGINE);
        engine.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:combustion_generator"));
        engine.setEnergyType(EnergyType.ELECTRIC_EU);
        engine.setGenerator(true);
        engine.setMultiblock(true);

        ResourceLocation fuelId = ResourceLocation.tryParse("gtceu:rocket_fuel");
        IngredientStack fuel = IngredientStack.fluid(fuelId, "Rocket Fuel", 1000.0);
        RecipeSpec spec = new RecipeSpec("lce_fuel", engine.getRecipeCategoryId(), 72.0, 160.0, List.of(fuel), List.of());
        engine.setBaseSpec(spec);
        engine.syncProjectedPorts();

        engine.getProperties().set(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.OXYGEN_BOOST, true);
        engine.syncProjectedPorts();

        Assertions.assertEquals(2, engine.getInputs().size());
        Assertions.assertFalse(engine.isAuxiliaryInputPort(0));
        Assertions.assertTrue(engine.isAuxiliaryInputPort(1));

        engine.overrideInputAmount(1, 99999.0);
        Assertions.assertEquals(1000.0, engine.getInputs().get(0).getAmount(), 0.001);

        engine.overrideInputAmount(0, 2500.0);
        Assertions.assertTrue(engine.isManualOverride());
        Assertions.assertEquals(2500.0, engine.getInputs().get(0).getAmount(), 0.001);
        Assertions.assertEquals(2500.0, engine.getBaseSpec().baseInputs().get(0).getAmount(), 0.001);
    }

    @Test
    @DisplayName("Blueprint export and import preserves manual recipe overrides and allows reset")
    void testBlueprintExportAndImportWithManualOverride() {
        FlowGraph graph = new FlowGraph();
        RecipeNode node = RecipeNode.create("Generic Assembler", 60.0, 32.0, GTVoltageTier.LV);
        node.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 4.0));
        node.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_block"), "Iron Block", 1.0));
        graph.addNode(node);

        node.overrideDurationTicks(15.0);
        node.overrideBaseEUt(128.0, false);
        node.overrideInputAmount(0, 9.0);

        String code = com.gtceu.calcboard.api.storage.BlueprintCodec.exportToString(
                graph,
                "Custom Overridden Factory",
                "Testing manual override blueprint persistence",
                "Tester",
                0.0,
                0.0,
                1.0
        );
        Assertions.assertNotNull(code);

        com.gtceu.calcboard.api.storage.BlueprintPackage pkg = com.gtceu.calcboard.api.storage.BlueprintCodec.importPackageFromString(code);
        Assertions.assertNotNull(pkg);
        FlowGraph importedGraph = pkg.getGraph();
        Assertions.assertNotNull(importedGraph);
        Assertions.assertEquals(1, importedGraph.getNodes().size());

        RecipeNode importedNode = importedGraph.getNodes().get(0);
        Assertions.assertTrue(importedNode.isManualOverride());
        Assertions.assertTrue(importedNode.hasOriginalRecipeSpec());
        Assertions.assertEquals(15.0, importedNode.getBaseDurationTicks(), 0.001);
        Assertions.assertEquals(128.0, importedNode.getBaseEUt(), 0.001);
        Assertions.assertEquals(9.0, importedNode.getInputs().get(0).getAmount(), 0.001);

        Assertions.assertTrue(importedNode.resetToOriginalRecipe());
        Assertions.assertFalse(importedNode.isManualOverride());
        Assertions.assertEquals(60.0, importedNode.getBaseDurationTicks(), 0.001);
        Assertions.assertEquals(32.0, importedNode.getBaseEUt(), 0.001);
        Assertions.assertEquals(4.0, importedNode.getInputs().get(0).getAmount(), 0.001);
    }
}
