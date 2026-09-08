package com.gtceu.calcboard.client.gui.action;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.history.HistoryManager;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.RecipeNodeSerializer;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MachineSwitchNameUpdateTest {

    private FlowGraph graph;
    private HistoryManager historyManager;

    @BeforeEach
    public void setUp() {
        graph = new FlowGraph();
        historyManager = new HistoryManager();
    }

    @Test
    public void testComputeSwitchedNodeNameSimple() {
        String result = BoardActionHandler.computeSwitchedNodeName("Generator Coil", "Stirling Engine");
        Assertions.assertEquals("Stirling Engine", result);
    }

    @Test
    public void testComputeSwitchedNodeNameWithSuffix() {
        String result = BoardActionHandler.computeSwitchedNodeName("Electric Blast Furnace (Steel Ingot)", "Mega Blast Furnace");
        Assertions.assertEquals("Mega Blast Furnace (Steel Ingot)", result);
    }

    @Test
    public void testComputeSwitchedNodeNameEmptyOldName() {
        String result = BoardActionHandler.computeSwitchedNodeName("", "Creative Motor");
        Assertions.assertEquals("Creative Motor", result);
    }

    @Test
    public void testSwitchMachineUpdatesNodeNameWithoutCustomName() {
        ResourceLocation coilId = ResourceLocation.tryParse("createaddition:generator_coil");
        ResourceLocation stirlingId = ResourceLocation.tryParse("create:stirling_engine");

        RecipeNode node = RecipeNode.create(coilId, "Generator Coil", 100.0, 30.0, GTVoltageTier.LV);
        graph.addNode(node);

        Assertions.assertFalse(node.hasCustomName());
        Assertions.assertEquals("Generator Coil", node.getName());

        String newMachineName = "Stirling Engine";
        String oldName = node.getName();
        String newName = BoardActionHandler.computeSwitchedNodeName(oldName, newMachineName);
        node.setName(newName);
        node.setMachineIcon(stirlingId);

        Assertions.assertEquals("Stirling Engine", node.getName());
        Assertions.assertEquals(stirlingId, node.getMachineIcon());
    }

    @Test
    public void testSwitchMachinePreservesCustomName() {
        ResourceLocation coilId = ResourceLocation.tryParse("createaddition:generator_coil");
        ResourceLocation stirlingId = ResourceLocation.tryParse("create:stirling_engine");

        RecipeNode node = RecipeNode.create(coilId, "Generator Coil", 100.0, 30.0, GTVoltageTier.LV);
        node.setName("My Custom Power Station");
        node.setHasCustomName(true);
        graph.addNode(node);

        String newMachineName = "Stirling Engine";
        String oldName = node.getName();
        String newName = oldName;
        if (!node.hasCustomName()) {
            newName = BoardActionHandler.computeSwitchedNodeName(oldName, newMachineName);
            node.setName(newName);
        }
        node.setMachineIcon(stirlingId);

        Assertions.assertEquals("My Custom Power Station", node.getName());
        Assertions.assertEquals("My Custom Power Station", newName);
    }

    @Test
    public void testSetMachineIconCommandUndoRedoRestoresNodeName() {
        ResourceLocation coilId = ResourceLocation.tryParse("createaddition:generator_coil");
        ResourceLocation stirlingId = ResourceLocation.tryParse("create:stirling_engine");

        RecipeNode node = RecipeNode.create(coilId, "Generator Coil", 100.0, 30.0, GTVoltageTier.LV);
        graph.addNode(node);

        String oldName = node.getName();
        String newName = "Stirling Engine";

        node.setMachineIcon(stirlingId);
        node.setName(newName);

        BoardCommand.SetMachineIconCommand cmd = new BoardCommand.SetMachineIconCommand(
                node, coilId, stirlingId, false, 1, node.getSteamMode(), node.getTargetTier(), oldName, newName
        );
        historyManager.record(cmd);

        Assertions.assertEquals("Stirling Engine", node.getName());
        Assertions.assertEquals(stirlingId, node.getMachineIcon());

        historyManager.undo(graph);
        Assertions.assertEquals("Generator Coil", node.getName());
        Assertions.assertEquals(coilId, node.getMachineIcon());

        historyManager.redo(graph);
        Assertions.assertEquals("Stirling Engine", node.getName());
        Assertions.assertEquals(stirlingId, node.getMachineIcon());
    }

    @Test
    public void testRecipeNodeSerializerPreservesHasCustomName() {
        RecipeNode node = RecipeNode.create("Test Node", 100.0, 30.0, GTVoltageTier.LV);
        node.setHasCustomName(true);

        CompoundTag tag = RecipeNodeSerializer.serialize(node);
        RecipeNode deserialized = RecipeNodeSerializer.deserialize(tag);

        Assertions.assertNotNull(deserialized);
        Assertions.assertTrue(deserialized.hasCustomName());
    }
}
