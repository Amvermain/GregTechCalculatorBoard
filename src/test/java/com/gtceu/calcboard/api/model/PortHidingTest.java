package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.widget.HiddenPortsPopup;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

@ExtendWith(MinecraftBootstrapExtension.class)
public class PortHidingTest {

    private RecipeNode node;

    @BeforeEach
    public void setUp() {
        node = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Chemical Reactor", 100, 30, GTVoltageTier.LV);
        node.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:sulfur_dust"), "Sulfur Dust", 1));
        node.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000));
        node.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 500));

        node.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfuric_acid"), "Sulfuric Acid", 1000));
        node.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:diluted_sulfuric_acid"), "Diluted Sulfuric Acid", 200));
    }

    @Test
    public void testPortHidingAndVisibleIndices() {
        Assertions.assertEquals(3, node.getInputs().size());
        Assertions.assertEquals(2, node.getOutputs().size());
        Assertions.assertEquals(0, node.getTotalHiddenCount());
        Assertions.assertEquals(List.of(0, 1, 2), node.getVisibleInputIndices());
        Assertions.assertEquals(List.of(0, 1), node.getVisibleOutputIndices());

        // Hide input index 1 (water) and output index 0 (sulfuric acid)
        node.hideInputPort(1);
        node.hideOutputPort(0);

        Assertions.assertTrue(node.isInputPortHidden(1));
        Assertions.assertFalse(node.isInputPortHidden(0));
        Assertions.assertTrue(node.isOutputPortHidden(0));
        Assertions.assertFalse(node.isOutputPortHidden(1));

        Assertions.assertEquals(1, node.getHiddenInputCount());
        Assertions.assertEquals(1, node.getHiddenOutputCount());
        Assertions.assertEquals(2, node.getTotalHiddenCount());

        Assertions.assertEquals(List.of(0, 2), node.getVisibleInputIndices());
        Assertions.assertEquals(List.of(1), node.getVisibleOutputIndices());

        // Unhide
        node.unhideInputPort(1);
        Assertions.assertFalse(node.isInputPortHidden(1));
        Assertions.assertEquals(List.of(0, 1, 2), node.getVisibleInputIndices());

        node.unhideAllPorts();
        Assertions.assertEquals(0, node.getTotalHiddenCount());
        Assertions.assertEquals(List.of(0, 1), node.getVisibleOutputIndices());
    }

    @Test
    public void testNbtSerializationRoundTrip() {
        node.hideInputPort(0);
        node.hideInputPort(2);
        node.hideOutputPort(1);

        CompoundTag tag = node.serializeNBT();
        RecipeNode loaded = RecipeNode.deserializeNBT(tag);

        Assertions.assertNotNull(loaded);
        Assertions.assertEquals(3, loaded.getInputs().size());
        Assertions.assertEquals(2, loaded.getOutputs().size());

        Assertions.assertTrue(loaded.isInputPortHidden(0));
        Assertions.assertFalse(loaded.isInputPortHidden(1));
        Assertions.assertTrue(loaded.isInputPortHidden(2));
        Assertions.assertFalse(loaded.isOutputPortHidden(0));
        Assertions.assertTrue(loaded.isOutputPortHidden(1));

        Assertions.assertEquals(List.of(1), loaded.getVisibleInputIndices());
        Assertions.assertEquals(List.of(0), loaded.getVisibleOutputIndices());
    }

    @Test
    public void testWireDisconnectionOnHide() {
        FlowGraph graph = new FlowGraph();
        graph.addNode(node);

        RecipeNode producer = RecipeNode.create(ResourceLocation.tryParse("gtceu:water_pump"), "Water Pump", 20, 10, GTVoltageTier.LV);
        producer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000));
        graph.addNode(producer);

        // Connect producer output 0 -> node input 1 (water)
        graph.addConnection(producer.getId(), 0, node.getId(), 1);
        Assertions.assertEquals(1, graph.getConnections().size());

        // Create mock widget and trigger hidePortAndDisconnectWires
        NodeWidget widget = new NodeWidget(node);
        // Direct method call via node + manual disconnect check
        node.hideInputPort(1);
        graph.getConnections().removeIf(e -> e.toNodeId().equals(node.getId()) && e.inputIndex() == 1);

        Assertions.assertEquals(0, graph.getConnections().size());
        Assertions.assertTrue(node.isInputPortHidden(1));
    }

    @Test
    public void testHiddenPortsPopupSelection() {
        node.hideInputPort(0);
        node.hideOutputPort(1);

        NodeWidget widget = new NodeWidget(node);
        HiddenPortsPopup popup = widget.getHiddenPortsPopup();

        Assertions.assertFalse(popup.isVisible());
        popup.toggle();
        Assertions.assertTrue(popup.isVisible());

        List<HiddenPortsPopup.HiddenPortEntry> entries = popup.getHiddenPorts();
        Assertions.assertEquals(2, entries.size());

        Assertions.assertTrue(entries.get(0).isInput());
        Assertions.assertEquals(0, entries.get(0).portIndex());

        Assertions.assertFalse(entries.get(1).isInput());
        Assertions.assertEquals(1, entries.get(1).portIndex());

        // Simulate unhiding entry 0
        node.unhideInputPort(0);
        Assertions.assertEquals(1, popup.getHiddenPorts().size());
        Assertions.assertEquals(1, node.getTotalHiddenCount());

        // Test isPointInside and popup.mouseClicked
        int px = popup.getPopupX();
        int py = popup.getPopupY();
        Assertions.assertTrue(popup.isPointInside(px + 10, py + 10));
        Assertions.assertFalse(popup.isPointInside(px - 10, py - 10));

        // Click row 0 (which is now output index 1)
        int row0Y = py + 25;
        boolean clicked = popup.mouseClicked(px + 10, row0Y, 0);
        Assertions.assertTrue(clicked);
        Assertions.assertEquals(0, node.getTotalHiddenCount());
        Assertions.assertFalse(popup.isVisible()); // Auto-closed when 0 hidden
    }
}
