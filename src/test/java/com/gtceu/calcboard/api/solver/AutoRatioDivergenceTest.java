package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.api.property.NodeBadgeRegistry;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

public class AutoRatioDivergenceTest {

    @Test
    @DisplayName("RFC-032: Closed recirculation loop without sufficient supply halts runaway scaling and flags divergence")
    public void testRecirculationLoopSuppressionAndWarning() {
        FlowGraph graph = new FlowGraph();

        RecipeNode cracker = RecipeNode.create("Cracker", 20.0, 30.0, GTVoltageTier.LV);
        cracker.addAddon(new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon("gtceu:lv_energy_hatch", "LV Energy Hatch", "", ResourceLocation.tryParse("gtceu:lv_energy_hatch"), GTVoltageTier.LV, 1, false, false, false));
        cracker.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:heavy_fuel"), "Heavy Fuel", 100.0, 1.0));
        cracker.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:cracked_heavy_fuel"), "Cracked Heavy Fuel", 100.0, 1.0));
        cracker.setMachineCount(1.0);
        cracker.setBaseNode(true);
        graph.addNode(cracker);

        RecipeNode distTower = RecipeNode.create("Distillation Tower", 20.0, 30.0, GTVoltageTier.LV);
        distTower.addAddon(new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon("gtceu:lv_energy_hatch", "LV Energy Hatch", "", ResourceLocation.tryParse("gtceu:lv_energy_hatch"), GTVoltageTier.LV, 1, false, false, false));
        distTower.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:cracked_heavy_fuel"), "Cracked Heavy Fuel", 100.0, 1.0));
        distTower.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:heavy_fuel"), "Heavy Fuel", 40.0, 1.0));
        distTower.setMachineCount(1.0);
        graph.addNode(distTower);

        graph.addConnection(cracker.getId(), 0, distTower.getId(), 0);
        graph.addConnection(distTower.getId(), 0, cracker.getId(), 0);

        AutoRatioResult result = graph.autoRatioFromAnchor(cracker);

        Assertions.assertTrue(result.hasDivergence());
        Assertions.assertTrue(result.divergentNodeIds().contains(cracker.getId()));
        Assertions.assertTrue(cracker.getProperties().get(NodeProperties.DIVERGENCE_WARNING));
        Assertions.assertEquals("recirculation_loop", cracker.getProperties().get(NodeProperties.DIVERGENCE_REASON));

        Assertions.assertEquals(1.0, cracker.getMachineCount(), 0.001);
        Assertions.assertTrue(distTower.getMachineCount() < 1000.0);
    }

    @Test
    @DisplayName("RFC-032: Warning badge and divergence flags self-heal when external feed is connected")
    public void testSelfHealingWhenExternalSupplyAdded() {
        FlowGraph graph = new FlowGraph();

        RecipeNode cracker = RecipeNode.create("Cracker", 20.0, 30.0, GTVoltageTier.LV);
        cracker.addAddon(new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon("gtceu:lv_energy_hatch", "LV Energy Hatch", "", ResourceLocation.tryParse("gtceu:lv_energy_hatch"), GTVoltageTier.LV, 1, false, false, false));
        cracker.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:heavy_fuel"), "Heavy Fuel", 100.0, 1.0));
        cracker.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:cracked_heavy_fuel"), "Cracked Heavy Fuel", 100.0, 1.0));
        cracker.setMachineCount(1.0);
        cracker.setBaseNode(true);
        graph.addNode(cracker);

        RecipeNode distTower = RecipeNode.create("Distillation Tower", 20.0, 30.0, GTVoltageTier.LV);
        distTower.addAddon(new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon("gtceu:lv_energy_hatch", "LV Energy Hatch", "", ResourceLocation.tryParse("gtceu:lv_energy_hatch"), GTVoltageTier.LV, 1, false, false, false));
        distTower.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:cracked_heavy_fuel"), "Cracked Heavy Fuel", 100.0, 1.0));
        distTower.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:heavy_fuel"), "Heavy Fuel", 50.0, 1.0));
        distTower.setMachineCount(1.0);
        graph.addNode(distTower);

        graph.addConnection(cracker.getId(), 0, distTower.getId(), 0);
        graph.addConnection(distTower.getId(), 0, cracker.getId(), 0);

        AutoRatioResult firstResult = graph.autoRatioFromAnchor(cracker);
        Assertions.assertTrue(firstResult.hasDivergence());
        Assertions.assertTrue(cracker.getProperties().get(NodeProperties.DIVERGENCE_WARNING));

        RecipeNode pump = RecipeNode.create("Oil Well Pump", 20.0, 30.0, GTVoltageTier.LV);
        pump.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:heavy_fuel"), "Heavy Fuel", 50.0, 1.0));
        pump.setMachineCount(1.0);
        graph.addNode(pump);

        graph.addConnection(pump.getId(), 0, cracker.getId(), 0);

        AutoRatioResult healedResult = graph.autoRatioFromAnchor(cracker);
        Assertions.assertFalse(healedResult.hasDivergence());
        Assertions.assertFalse(cracker.getProperties().get(NodeProperties.DIVERGENCE_WARNING));
        Assertions.assertEquals("", cracker.getProperties().get(NodeProperties.DIVERGENCE_REASON));
        Assertions.assertEquals(1.0, pump.getMachineCount(), 0.001);
        Assertions.assertEquals(1.0, distTower.getMachineCount(), 0.001);
    }

    @Test
    @DisplayName("RFC-032: Declarative badge registry provides amber warning badge with interactive tooltip")
    public void testDeclarativeWarningBadgePresentationAndClick() {
        RecipeNode node = RecipeNode.create("LoopNode", 20.0, 30.0, GTVoltageTier.LV);
        node.getProperties().set(NodeProperties.DIVERGENCE_WARNING, true);
        node.getProperties().set(NodeProperties.DIVERGENCE_REASON, "recirculation_loop");

        List<NodeBadge> badges = NodeBadgeRegistry.getBadgesForNode(node);
        Assertions.assertFalse(badges.isEmpty());

        NodeBadge warningBadge = badges.stream()
                .filter(NodeBadge::isWarning)
                .findFirst()
                .orElse(null);

        Assertions.assertNotNull(warningBadge);
        Assertions.assertNotNull(warningBadge.tooltipLines());
        Assertions.assertEquals(5, warningBadge.tooltipLines().size());
        Assertions.assertNotNull(warningBadge.onClick());

        warningBadge.onClick().run();
        Assertions.assertTrue(node.isBaseNode());
        Assertions.assertFalse(node.getProperties().get(NodeProperties.DIVERGENCE_WARNING));
    }

    @Test
    @DisplayName("RFC-032: Pre-solve static loop detection identifies unfed deficit loops before calculation")
    public void testPreSolveDeficitLoopDetectionBeforeIteration() {
        FlowGraph graph = new FlowGraph();

        RecipeNode cracker = RecipeNode.create("Cracker", 20.0, 30.0, GTVoltageTier.LV);
        cracker.addAddon(new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon("gtceu:lv_energy_hatch", "LV Energy Hatch", "", ResourceLocation.tryParse("gtceu:lv_energy_hatch"), GTVoltageTier.LV, 1, false, false, false));
        cracker.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:heavy_fuel"), "Heavy Fuel", 100.0, 1.0));
        cracker.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:cracked_heavy_fuel"), "Cracked Heavy Fuel", 100.0, 1.0));
        cracker.setMachineCount(1.0);
        cracker.setBaseNode(true);
        graph.addNode(cracker);

        RecipeNode distTower = RecipeNode.create("Distillation Tower", 20.0, 30.0, GTVoltageTier.LV);
        distTower.addAddon(new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon("gtceu:lv_energy_hatch", "LV Energy Hatch", "", ResourceLocation.tryParse("gtceu:lv_energy_hatch"), GTVoltageTier.LV, 1, false, false, false));
        distTower.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:cracked_heavy_fuel"), "Cracked Heavy Fuel", 100.0, 1.0));
        distTower.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:heavy_fuel"), "Heavy Fuel", 40.0, 1.0));
        distTower.setMachineCount(1.0);
        graph.addNode(distTower);

        graph.addConnection(cracker.getId(), 0, distTower.getId(), 0);
        graph.addConnection(distTower.getId(), 0, cracker.getId(), 0);

        java.util.Set<String> preSolveDeficits = graph.findUnfedDeficitLoopNodeIds();
        Assertions.assertFalse(preSolveDeficits.isEmpty(), "Unfed deficit loop must be detected statically prior to calculation");
        Assertions.assertTrue(preSolveDeficits.contains(cracker.getId()));
        Assertions.assertTrue(preSolveDeficits.contains(distTower.getId()));

        RecipeNode pump = RecipeNode.create("Oil Well Pump", 20.0, 30.0, GTVoltageTier.LV);
        pump.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:heavy_fuel"), "Heavy Fuel", 60.0, 1.0));
        pump.setMachineCount(1.0);
        graph.addNode(pump);

        graph.addConnection(pump.getId(), 0, cracker.getId(), 0);

        java.util.Set<String> healedDeficits = graph.findUnfedDeficitLoopNodeIds();
        Assertions.assertTrue(healedDeficits.isEmpty(), "Connecting external feed must clear pre-solve unfed loop warning");
    }
}
