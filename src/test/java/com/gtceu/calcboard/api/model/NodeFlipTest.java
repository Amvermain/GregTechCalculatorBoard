package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NodeFlipTest {

    @Test
    public void testRecipeNodeFlipStateAndSerialization() {
        RecipeNode node = RecipeNode.create("Macerator", 20.0, 32.0, GTVoltageTier.LV);
        Assertions.assertFalse(node.isFlipped());

        node.setFlipped(true);
        Assertions.assertTrue(node.isFlipped());

        node.toggleFlipped();
        Assertions.assertFalse(node.isFlipped());

        node.setFlipped(true);
        CompoundTag tag = node.serializeNBT();
        Assertions.assertTrue(tag.getBoolean("isFlipped"));

        RecipeNode deserialized = RecipeNode.deserializeNBT(tag);
        Assertions.assertTrue(deserialized.isFlipped());
    }

    @Test
    public void testStandardCardPortCoordinatesWhenFlipped() {
        RecipeNode node = RecipeNode.create("Assembler", 20.0, 32.0, GTVoltageTier.LV);
        node.setPos(100.0, 200.0);
        node.setCardWidth(250);
        node.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0));
        node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_plate"), "Iron Plate", 1.0));

        NodeWidget widget = new NodeWidget(node);

        // Standard: Input on Left (100 + 6 = 106), Output on Right (100 + 250 - 6 = 344)
        Assertions.assertEquals(106.0f, widget.getInputPortX(0), 0.001f);
        Assertions.assertEquals(344.0f, widget.getOutputPortX(0), 0.001f);

        // Flipped: Input on Right (344), Output on Left (106)
        node.setFlipped(true);
        Assertions.assertEquals(344.0f, widget.getInputPortX(0), 0.001f);
        Assertions.assertEquals(106.0f, widget.getOutputPortX(0), 0.001f);
    }

    @Test
    public void testHorizontalReroutePortCoordinates() {
        RecipeNode reroute = RecipeNode.createReroute(100.0, 200.0);
        NodeWidget widget = new NodeWidget(reroute);

        // Horizontal Reroute Standard (Left to Right):
        // Input on Left (x = 100), Output on Right (x + 32 = 132), Y at center (y + 16 = 216)
        Assertions.assertEquals(100.0f, widget.getInputPortX(0), 0.001f);
        Assertions.assertEquals(216.0f, widget.getInputPortY(0), 0.001f);
        Assertions.assertEquals(132.0f, widget.getOutputPortX(0), 0.001f);
        Assertions.assertEquals(216.0f, widget.getOutputPortY(0), 0.001f);

        // Horizontal Reroute Flipped (Right to Left):
        // Input on Right (x + 32 = 132), Output on Left (x = 100)
        reroute.setFlipped(true);
        Assertions.assertEquals(132.0f, widget.getInputPortX(0), 0.001f);
        Assertions.assertEquals(216.0f, widget.getInputPortY(0), 0.001f);
        Assertions.assertEquals(100.0f, widget.getOutputPortX(0), 0.001f);
        Assertions.assertEquals(216.0f, widget.getOutputPortY(0), 0.001f);
    }

    @Test
    public void testFlipNodesCommandUndoRedo() {
        FlowGraph graph = new FlowGraph();
        RecipeNode node1 = RecipeNode.create("Node1", 20.0, 32.0, GTVoltageTier.LV);
        RecipeNode node2 = RecipeNode.create("Node2", 20.0, 32.0, GTVoltageTier.LV);
        graph.addNode(node1);
        graph.addNode(node2);

        BoardCommand.FlipNodesCommand cmd = new BoardCommand.FlipNodesCommand(
                java.util.Map.of(node1.getId(), false, node2.getId(), false),
                java.util.Map.of(node1.getId(), true, node2.getId(), true)
        );

        cmd.redo(graph);
        Assertions.assertTrue(node1.isFlipped());
        Assertions.assertTrue(node2.isFlipped());

        cmd.undo(graph);
        Assertions.assertFalse(node1.isFlipped());
        Assertions.assertFalse(node2.isFlipped());
    }
}



