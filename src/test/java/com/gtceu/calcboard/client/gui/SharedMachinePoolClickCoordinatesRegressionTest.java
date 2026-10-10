package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.PoolViewMode;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.interaction.EmbeddedPanelInteractionHandler;
import com.gtceu.calcboard.client.gui.layout.SubCardLayoutBounds;
import com.gtceu.calcboard.client.gui.layout.SubCardLayoutCalculator;
import com.gtceu.calcboard.client.gui.render.EmbeddedPanelRenderer;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MinecraftBootstrapExtension.class)
public class SharedMachinePoolClickCoordinatesRegressionTest {

    @Test
    @DisplayName("Verify that clicking the bottom-right border of sub-card 1 does not mistakenly trigger Blue Topaz port")
    public void testSubCardBottomBorderDoesNotTriggerPortHit() {
        FlowGraph graph = new FlowGraph();

        CanvasGroupFrame frame = new CanvasGroupFrame("sp_cutter_frame", "Large Cutter Pool", CanvasGroupFrame.COLOR_CYAN, 100, 100, 360, 260);
        frame.setSharedMachineFrame(true);
        frame.setTargetPoolCapacity(1.0);
        graph.addFrame(frame);

        // Sub-card 1: Large Cutter with Water input and Blue Topaz + Black Dust outputs
        RecipeNode sub1 = RecipeNode.create("Large Cutter [LCt]", 100, 20, GTVoltageTier.HV);
        sub1.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0));
        sub1.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:blue_topaz_ore"), "Blue Topaz Ore", 1.0));
        sub1.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:black_dust"), "Black Dust", 1.0));
        sub1.setMachineCount(0.07);
        graph.addNode(sub1);
        frame.addRecipeInline(sub1, graph);

        // Sub-card 2: Large Cutter second recipe
        RecipeNode sub2 = RecipeNode.create("Large Cutter [LCt]", 100, 20, GTVoltageTier.HV);
        sub2.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:red_garnet_ore"), "Red Garnet Ore", 1.0));
        sub2.setMachineCount(0.09);
        graph.addNode(sub2);
        frame.addRecipeInline(sub2, graph);

        frame.relayoutEmbeddedCards(graph);

        SubCardLayoutBounds bounds1 = SubCardLayoutCalculator.compute(frame, sub1);
        Assertions.assertEquals(1, bounds1.inputPorts().size());
        Assertions.assertEquals(2, bounds1.outputPorts().size());

        // 1. Port 0 (Blue Topaz) detection at its exact hit box
        var blueTopazPort = bounds1.findPort(false, 0);
        Assertions.assertNotNull(blueTopazPort);
        var hitTopaz = EmbeddedPanelRenderer.findHoveredEmbeddedPort(graph, blueTopazPort.anchorX(), blueTopazPort.anchorY());
        Assertions.assertNotNull(hitTopaz);
        Assertions.assertFalse(hitTopaz.isInput());
        Assertions.assertEquals(0, hitTopaz.portIndex());
        Assertions.assertEquals("Blue Topaz Ore", hitTopaz.stack().getDisplayName());

        // 2. Port 1 (Black Dust) detection at its exact hit box
        var blackDustPort = bounds1.findPort(false, 1);
        Assertions.assertNotNull(blackDustPort);
        var hitDust = EmbeddedPanelRenderer.findHoveredEmbeddedPort(graph, blackDustPort.anchorX(), blackDustPort.anchorY());
        Assertions.assertNotNull(hitDust);
        Assertions.assertFalse(hitDust.isInput());
        Assertions.assertEquals(1, hitDust.portIndex());
        Assertions.assertEquals("Black Dust", hitDust.stack().getDisplayName());

        // 3. Regression verification: Click at bottom-right border of sub-card 1 (the red circle in user screenshot)
        double card1X = sub1.getPosX();
        double card1Y = sub1.getPosY();
        double card1W = sub1.getCardWidth();
        double card1H = sub1.getCardHeight();

        // The red circle is at X ~ (cardX + cardW - 15), Y ~ (cardY + cardH - 3)
        double redCircleX = card1X + card1W - 15.0;
        double redCircleY = card1Y + card1H - 3.0;

        var hitAtRedCircle = EmbeddedPanelRenderer.findHoveredEmbeddedPort(graph, redCircleX, redCircleY);
        Assertions.assertNull(hitAtRedCircle, "Clicking sub-card bottom-right border must NOT detect any port!");

        // 4. Also verify that standard NodeWidget bounds are completely suppressed
        NodeWidget widget1 = new NodeWidget(sub1, null);
        Assertions.assertFalse(widget1.isPointInside(redCircleX, redCircleY));
        Assertions.assertEquals(-1, widget1.getHoveredOutputPortIndex(redCircleX, redCircleY));
    }

    @Test
    @DisplayName("Verify that transitionToEmbeddedPanel synchronizes containedNodeIds properly")
    public void testTransitionToEmbeddedPanelSyncsContainedNodeIds() {
        FlowGraph graph = new FlowGraph();

        RecipeNode n1 = RecipeNode.create("Cutter 1", 100, 20, GTVoltageTier.HV);
        n1.setPos(120, 140);
        n1.setCardWidth(180);
        n1.setCardHeight(100);
        graph.addNode(n1);

        CanvasGroupFrame frame = new CanvasGroupFrame("test_frame", "Shared Pool", CanvasGroupFrame.COLOR_CYAN, 100, 100, 360, 260);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        // Frame was in EXPANDED_FRAME mode, transition to EMBEDDED_PANEL
        frame.setViewMode(PoolViewMode.EMBEDDED_PANEL, graph);

        Assertions.assertTrue(frame.containsNode(n1.getId()));
        Assertions.assertTrue(graph.isNodeInEmbeddedPanel(n1.getId()));
        Assertions.assertTrue(graph.isNodeInFoldedOrEmbeddedFrame(n1.getId()));
    }
}
