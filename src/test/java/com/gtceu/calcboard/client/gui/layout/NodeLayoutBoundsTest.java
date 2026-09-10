package com.gtceu.calcboard.client.gui.layout;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.ModuleInputPin;
import com.gtceu.calcboard.api.model.ModuleOutputPin;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.HarmonizedRatioOptimizer;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.canvas.CanvasWireRenderer;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class NodeLayoutBoundsTest {

    private RecipeNode createSampleMachineNode(double x, double y) {
        RecipeNode node = new RecipeNode("test_machine", "Chemical Reactor", 20.0, 30.0, GTVoltageTier.LV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:chemical_reactor"));
        node.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0));
        node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:gold_ingot"), "Gold Ingot", 1.0));
        node.setPosX(x);
        node.setPosY(y);
        node.setEnergyType(EnergyType.ELECTRIC_EU);
        return node;
    }

    @Test
    public void testSlimModeGhostHitboxPrevention() {
        RecipeNode node = createSampleMachineNode(0, 0);
        NodeLayoutBounds bounds = NodeLayoutCalculator.compute(node, true, 20, false);

        Assertions.assertFalse(bounds.hasRow2Controls());
        Assertions.assertTrue(bounds.getTierBtnBounds().isEmpty());
        Assertions.assertTrue(bounds.getSecondaryBtnBounds().isEmpty());
        Assertions.assertTrue(bounds.getConfigBtnBounds().isEmpty());

        Assertions.assertEquals(46, bounds.getContentStartY());

        var inputPort = bounds.findPort(true, 0);
        Assertions.assertNotNull(inputPort);

        double testX = 10.0;
        double testY = 46.0;

        Assertions.assertEquals(0, bounds.getHoveredInputPortIndex(testX, testY));
        Assertions.assertFalse(bounds.isTierButtonHovered(testX, testY));
    }

    @Test
    public void testNormalModeControlsAndPortSeparation() {
        RecipeNode node = createSampleMachineNode(0, 0);
        NodeLayoutBounds bounds = NodeLayoutCalculator.compute(node, false, 20, false);

        Assertions.assertTrue(bounds.hasRow2Controls());
        Assertions.assertFalse(bounds.getTierBtnBounds().isEmpty());
        Assertions.assertEquals(80, bounds.getContentStartY());

        double row2X = 10.0;
        double row2Y = 46.0;

        Assertions.assertTrue(bounds.isTierButtonHovered(row2X, row2Y));
        Assertions.assertEquals(-1, bounds.getHoveredInputPortIndex(row2X, row2Y));

        double portY = 80.0;
        Assertions.assertEquals(0, bounds.getHoveredInputPortIndex(10.0, portY));
        Assertions.assertFalse(bounds.isTierButtonHovered(10.0, portY));
    }

    @Test
    public void testRerouteNodeLayoutBounds() {
        RecipeNode reroute = RecipeNode.createReroute(100.0, 100.0);
        NodeLayoutBounds bounds = NodeLayoutCalculator.compute(reroute, false, 20, false);

        Assertions.assertEquals(32, bounds.getCardBounds().width());
        Assertions.assertEquals(32, bounds.getCardBounds().height());
        Assertions.assertFalse(bounds.hasRow2Controls());
        Assertions.assertEquals(1, bounds.getInputPorts().size());
        Assertions.assertEquals(1, bounds.getOutputPorts().size());

        var inPort = bounds.getInputPorts().get(0);
        var outPort = bounds.getOutputPorts().get(0);

        Assertions.assertEquals(100.0f, inPort.anchorX());
        Assertions.assertEquals(116.0f, inPort.anchorY());
        Assertions.assertEquals(132.0f, outPort.anchorX());
        Assertions.assertEquals(116.0f, outPort.anchorY());

        // Reroute body is draggable header, but port hitboxes are not
        Assertions.assertTrue(bounds.isHeaderHovered(116.0, 116.0));
        Assertions.assertFalse(bounds.isHeaderHovered(inPort.hitBox().x() + 2, inPort.hitBox().y() + 2));
        Assertions.assertFalse(bounds.isHeaderHovered(outPort.hitBox().x() + 2, outPort.hitBox().y() + 2));
    }

    @Test
    public void testRerouteNodeTargetBatchBadgeExcludesHeaderHover() {
        RecipeNode reroute = RecipeNode.createReroute(100.0, 100.0);
        reroute.setTargetBatchAmount(64.0);
        NodeLayoutBounds bounds = NodeLayoutCalculator.compute(reroute, false, 20, false);

        Assertions.assertFalse(bounds.getTargetBatchBadgeBounds().isEmpty());
        double badgeX = bounds.getTargetBatchBadgeBounds().x() + 2;
        double badgeY = bounds.getTargetBatchBadgeBounds().y() + 2;

        Assertions.assertTrue(bounds.isTargetBatchBadgeHovered(badgeX, badgeY));
        Assertions.assertFalse(bounds.isHeaderHovered(badgeX, badgeY));
    }

    @Test
    public void testPortAnchorAndClampedHitbox() {
        RecipeNode node = createSampleMachineNode(50.0, 100.0);
        NodeLayoutBounds bounds = NodeLayoutCalculator.compute(node, true, 20, false);

        var inPort = bounds.findPort(true, 0);
        var outPort = bounds.findPort(false, 0);
        Assertions.assertNotNull(inPort);
        Assertions.assertNotNull(outPort);

        Assertions.assertEquals(56.0f, inPort.anchorX());
        Assertions.assertEquals(100.0f + 46.0f + 8.0f, inPort.anchorY());

        Assertions.assertEquals(50.0f + node.getCardWidth() - 6.0f, outPort.anchorX());
        Assertions.assertEquals(100.0f + 46.0f + 8.0f, outPort.anchorY());

        Assertions.assertEquals(22, inPort.hitBox().height());
        Assertions.assertEquals(22, outPort.hitBox().height());
    }

    @Test
    public void testBoundaryPinInputLayoutBounds() {
        IngredientStack stack = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:water"), "Water", 1000);
        ModuleInputPin inPin = new ModuleInputPin("in_pin_1", "Water Feed", stack);
        inPin.setPos(100.0, 100.0);

        NodeLayoutBounds bounds = NodeLayoutCalculator.compute(inPin, false, 20, false);
        Assertions.assertEquals(32, bounds.getCardBounds().width());
        Assertions.assertEquals(32, bounds.getCardBounds().height());
        Assertions.assertEquals(0, bounds.getInputPorts().size());
        Assertions.assertEquals(1, bounds.getOutputPorts().size());

        var outPort = bounds.getOutputPorts().get(0);
        Assertions.assertEquals(132.0f, outPort.anchorX());
        Assertions.assertEquals(116.0f, outPort.anchorY());

        Assertions.assertTrue(bounds.isCloseButtonHovered(104.0, 104.0));
        Assertions.assertFalse(bounds.isHeaderHovered(104.0, 104.0));
        Assertions.assertTrue(bounds.isHeaderHovered(116.0, 116.0));
        Assertions.assertFalse(bounds.isHeaderHovered(132.0, 116.0));

        inPin.setFlipped(true);
        NodeLayoutBounds flippedBounds = NodeLayoutCalculator.compute(inPin, false, 20, false);
        var flippedOutPort = flippedBounds.getOutputPorts().get(0);
        Assertions.assertEquals(100.0f, flippedOutPort.anchorX());
        Assertions.assertEquals(116.0f, flippedOutPort.anchorY());
        Assertions.assertTrue(flippedBounds.isCloseButtonHovered(123.0, 104.0));
    }

    @Test
    public void testBoundaryPinOutputLayoutBounds() {
        IngredientStack stack = IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 10);
        ModuleOutputPin outPin = new ModuleOutputPin("out_pin_1", "Iron Collector", stack);
        outPin.setPos(200.0, 200.0);

        NodeLayoutBounds bounds = NodeLayoutCalculator.compute(outPin, false, 20, false);
        Assertions.assertEquals(32, bounds.getCardBounds().width());
        Assertions.assertEquals(32, bounds.getCardBounds().height());
        Assertions.assertEquals(1, bounds.getInputPorts().size());
        Assertions.assertEquals(0, bounds.getOutputPorts().size());

        var inPort = bounds.getInputPorts().get(0);
        Assertions.assertEquals(200.0f, inPort.anchorX());
        Assertions.assertEquals(216.0f, inPort.anchorY());

        Assertions.assertTrue(bounds.isCloseButtonHovered(223.0, 204.0));
        Assertions.assertFalse(bounds.isHeaderHovered(223.0, 204.0));
        Assertions.assertTrue(bounds.isHeaderHovered(216.0, 216.0));
        Assertions.assertFalse(bounds.isHeaderHovered(200.0, 216.0));

        outPin.setFlipped(true);
        NodeLayoutBounds flippedBounds = NodeLayoutCalculator.compute(outPin, false, 20, false);
        var flippedInPort = flippedBounds.getInputPorts().get(0);
        Assertions.assertEquals(232.0f, flippedInPort.anchorX());
        Assertions.assertEquals(216.0f, flippedInPort.anchorY());
        Assertions.assertTrue(flippedBounds.isCloseButtonHovered(204.0, 204.0));
    }

    @Test
    public void testBoundaryPinWireEndpointsFallback() {
        FlowGraph graph = new FlowGraph();
        IngredientStack stack = IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 10);
        ModuleInputPin inPin = new ModuleInputPin("in_pin_1", "Iron Source", stack);
        inPin.setPos(50.0, 100.0);
        ModuleOutputPin outPin = new ModuleOutputPin("out_pin_1", "Iron Sink", stack);
        outPin.setPos(300.0, 100.0);

        graph.addNode(inPin);
        graph.addNode(outPin);
        graph.addConnection(inPin.getId(), 0, outPin.getId(), 0);
        FlowGraph.ConnectionEdge edge = graph.getConnections().get(0);

        CanvasWireRenderer.ResolvedWireEndpoints pts = CanvasWireRenderer.resolveWireEndpoints(graph, null, edge);
        Assertions.assertNotNull(pts);
        Assertions.assertEquals(82.0f, pts.x1());
        Assertions.assertEquals(116.0f, pts.y1());
        Assertions.assertEquals(300.0f, pts.x2());
        Assertions.assertEquals(116.0f, pts.y2());

        inPin.setFlipped(true);
        outPin.setFlipped(true);
        CanvasWireRenderer.ResolvedWireEndpoints flippedPts = CanvasWireRenderer.resolveWireEndpoints(graph, null, edge);
        Assertions.assertNotNull(flippedPts);
        Assertions.assertEquals(50.0f, flippedPts.x1());
        Assertions.assertEquals(116.0f, flippedPts.y1());
        Assertions.assertEquals(332.0f, flippedPts.x2());
        Assertions.assertEquals(116.0f, flippedPts.y2());
    }

    @Test
    public void testHarmonizedRatioOptimizerExcludesBoundaryPins() {
        FlowGraph graph = new FlowGraph();
        IngredientStack stack = IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 10);
        ModuleInputPin inPin = new ModuleInputPin("in_pin_1", "Iron Source", stack);
        RecipeNode machine = createSampleMachineNode(150.0, 100.0);
        ModuleOutputPin outPin = new ModuleOutputPin("out_pin_1", "Gold Sink", stack);

        graph.addNode(inPin);
        graph.addNode(machine);
        graph.addNode(outPin);

        double consumerMatch = HarmonizedRatioOptimizer.calculateConsumerMatchCount(graph, machine, 0, outPin, 0);
        Assertions.assertEquals(1.0, consumerMatch, 0.0001);

        double producerMatch = HarmonizedRatioOptimizer.calculateProducerMatchCount(graph, inPin, 0, machine, 0);
        Assertions.assertEquals(1.0, producerMatch, 0.0001);
    }
}
