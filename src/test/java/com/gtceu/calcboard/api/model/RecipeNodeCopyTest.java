package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.model.role.BoundaryPinNodeRole;
import com.gtceu.calcboard.api.model.role.JunctionNodeRole;
import com.gtceu.calcboard.api.model.role.MachineNodeRole;
import com.gtceu.calcboard.api.model.role.NodeRoleType;
import com.gtceu.calcboard.api.model.role.SubPageModuleNodeRole;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyKey;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.FlowSplitMode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.SupplyMode;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.storage.NodeClipboard;
import com.gtceu.calcboard.api.type.SteamMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class RecipeNodeCopyTest {

    @Test
    public void testMachineNodeCopyEquivalenceAndIndependence() {
        RecipeNode original = RecipeNode.create("High Voltage EBF", 120.0, 480.0, GTVoltageTier.HV);
        original.setName("Custom EBF #1");
        original.setHasCustomName(true);
        original.setPos(100.5, 250.0);
        original.setFlipped(true);
        original.setOverclockMode(OverclockMode.PERFECT);
        original.setEnergyType(EnergyType.ELECTRIC_EU);
        original.setMachineCount(4.5);
        original.setParallel(16);
        original.setBaseNode(true);
        original.setEfficiency(0.95);

        IngredientStack inputOre = IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ore"), "Iron Ore", 2.0);
        original.addInput(inputOre);
        IngredientStack outputIngot = IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 2.0);
        original.addOutput(outputIngot);

        NodePropertyKey<Double> testProp = NodeProperties.TARGET_BATCH_AMOUNT;
        original.getProperties().set(testProp, 99.0);
        original.getPortVisibility().hideInputPort(0, 1);

        RecipeNode copy = original.copy();

        Assertions.assertNotEquals(original.getId(), copy.getId());
        assertMachineNodePropertiesMatch(original, copy);

        mutateCopyAndVerifyIsolation(original, copy, testProp);
    }

    private void assertMachineNodePropertiesMatch(RecipeNode original, RecipeNode copy) {
        Assertions.assertEquals(original.getName(), copy.getName());
        Assertions.assertEquals(original.hasCustomName(), copy.hasCustomName());
        Assertions.assertEquals(original.getPosX(), copy.getPosX(), 0.001);
        Assertions.assertEquals(original.getPosY(), copy.getPosY(), 0.001);
        Assertions.assertEquals(original.isFlipped(), copy.isFlipped());
        Assertions.assertEquals(original.isBaseNode(), copy.isBaseNode());
        Assertions.assertEquals(original.getOverclockMode(), copy.getOverclockMode());
        Assertions.assertEquals(original.getEnergyType(), copy.getEnergyType());
        Assertions.assertEquals(original.getMachineCount(), copy.getMachineCount(), 0.001);
        Assertions.assertEquals(original.getParallel(), copy.getParallel());
        Assertions.assertEquals(original.getEfficiency(), copy.getEfficiency(), 0.001);
        Assertions.assertEquals(1, copy.getInputs().size());
        Assertions.assertEquals(1, copy.getOutputs().size());
        Assertions.assertTrue(copy.getPortVisibility().isInputPortHidden(0));
    }

    private void mutateCopyAndVerifyIsolation(RecipeNode original, RecipeNode copy, NodePropertyKey<Double> prop) {
        copy.setMachineCount(99.0);
        copy.getInputs().get(0).setChance(0.5);
        copy.getProperties().set(prop, 12345.0);
        copy.getPortVisibility().unhideInputPort(0);

        Assertions.assertEquals(4.5, original.getMachineCount(), 0.001);
        Assertions.assertEquals(1.0, original.getInputs().get(0).getChance(), 0.001);
        Assertions.assertEquals(99.0, original.getProperties().get(prop), 0.001);
        Assertions.assertTrue(original.getPortVisibility().isInputPortHidden(0));
    }

    @Test
    public void testJunctionNodeCopyEquivalenceAndIndependence() {
        RecipeNode original = RecipeNode.createReroute(50.0, 75.0);
        JunctionNodeRole role = original.asJunction();
        role.setSupplyMode(SupplyMode.FIXED_RATE);
        role.setExternalSupplyRate(120.0);
        role.setExternalDrainRate(45.0);
        role.setBuffer(true);
        role.setBufferSize(500.0);
        role.setSplitMode(FlowSplitMode.EQUAL);

        IngredientStack stack = IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0);
        role.setBoundIngredient(stack);

        RecipeNode copy = original.copy();

        Assertions.assertTrue(copy.isJunction());
        Assertions.assertNotEquals(original.getId(), copy.getId());
        JunctionNodeRole copyRole = copy.asJunction();
        Assertions.assertEquals(SupplyMode.FIXED_RATE, copyRole.getSupplyMode());
        Assertions.assertEquals(120.0, copyRole.getExternalSupplyRate(), 0.001);
        Assertions.assertEquals(45.0, copyRole.getExternalDrainRate(), 0.001);
        Assertions.assertTrue(copyRole.isBuffer());
        Assertions.assertEquals(500.0, copyRole.getBufferSize(), 0.001);
        Assertions.assertEquals(FlowSplitMode.EQUAL, copyRole.getSplitMode());
        Assertions.assertNotNull(copyRole.getBoundIngredient());
        Assertions.assertEquals("Water", copyRole.getBoundIngredient().getDisplayName());

        copyRole.setExternalSupplyRate(999.0);
        copyRole.getBoundIngredient().setChance(0.2);
        Assertions.assertEquals(120.0, role.getExternalSupplyRate(), 0.001);
        Assertions.assertEquals(1.0, role.getBoundIngredient().getChance(), 0.001);
    }

    @Test
    public void testBoundaryPinNodeCopyEquivalenceAndIndependence() {
        IngredientStack ing = IngredientStack.item(ResourceLocation.tryParse("minecraft:gold_ingot"), "Gold", 4.0);
        ModuleInputPin original = new ModuleInputPin("pin-1", "Input Pin", ing);
        original.setTargetPortIndex(2);

        RecipeNode copy = original.copy();

        Assertions.assertTrue(copy.isBoundaryPin());
        BoundaryPinNode pinCopy = (BoundaryPinNode) copy;
        Assertions.assertEquals(BoundaryPinNode.PinDirection.INPUT, pinCopy.getDirection());
        Assertions.assertEquals("Input Pin", pinCopy.getPinLabel());
        Assertions.assertEquals(2, pinCopy.getTargetPortIndex());

        pinCopy.setPinLabel("Modified Pin");
        Assertions.assertEquals("Input Pin", original.getPinLabel());
    }

    @Test
    public void testSubPageModuleNodeCopyWithCircularSubgraph() {
        RecipeNode moduleA = new RecipeNode("mod-a", "Module A", 0.0, 0.0, GTVoltageTier.LV);
        SubPageModuleNodeRole roleA = new SubPageModuleNodeRole("page-a");
        moduleA.setRole(roleA);

        RecipeNode moduleB = new RecipeNode("mod-b", "Module B", 0.0, 0.0, GTVoltageTier.LV);
        SubPageModuleNodeRole roleB = new SubPageModuleNodeRole("page-b");
        moduleB.setRole(roleB);

        FlowGraph graphA = new FlowGraph();
        graphA.addNode(moduleB);

        FlowGraph graphB = new FlowGraph();
        graphB.addNode(moduleA);

        roleA.setSubGraph(graphA);
        roleB.setSubGraph(graphB);

        RecipeNode copiedA = Assertions.assertDoesNotThrow(() -> moduleA.copy());
        Assertions.assertNotNull(copiedA);
        Assertions.assertTrue(copiedA.isModule());
        Assertions.assertEquals("page-a", copiedA.asModule().getSubPageId());
        Assertions.assertNotNull(copiedA.asModule().getSubGraph());
    }

    @Test
    public void testNbtSerializationParityWithOriginal() {
        RecipeNode original = RecipeNode.create("Chemical Reactor", 60.0, 120.0, GTVoltageTier.MV);
        original.setName("Reactor Alfa");
        original.setMachineCount(2.5);
        original.setParallel(4);
        original.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:hydrogen"), "Hydrogen", 2000.0));
        original.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:water"), "Water", 1000.0));

        RecipeNode cloned = new RecipeNode(original, original.getId());
        CompoundTag originalTag = original.serializeNBT();
        CompoundTag clonedTag = cloned.serializeNBT();

        Assertions.assertEquals(originalTag, clonedTag);
    }

    @Test
    public void testJunctionNodeCustomNamePreservedOnCopy() {
        RecipeNode original = RecipeNode.createReroute(50.0, 75.0);
        original.asJunction().setBoundIngredient(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0));
        original.setName("Custom Buffer Water");
        original.setHasCustomName(true);

        RecipeNode copy = original.copy();

        Assertions.assertEquals("Custom Buffer Water", copy.getName());
        Assertions.assertTrue(copy.hasCustomName());
    }

    @Test
    public void testMachineNodeAddonsDeepCopyAndMutationIsolation() {
        RecipeNode original = RecipeNode.create("Pyrolyse Oven", 100.0, 120.0, GTVoltageTier.MV);
        MachineAddon coil = new MachineAddon("cupronickel_coil", "Cupronickel Coil", MachineAddon.Category.COIL, "Coil", null);
        coil.setCoilTemperature(1800);
        original.addAddon(coil);

        RecipeNode copy = original.copy();

        Assertions.assertEquals(1, copy.getAddons().size());
        MachineAddon copiedAddon = copy.getAddons().get(0);
        Assertions.assertEquals(1800, copiedAddon.getCoilTemperature());

        copiedAddon.setCoilTemperature(2700);
        Assertions.assertEquals(1800, original.getAddons().get(0).getCoilTemperature());
    }

    @Test
    public void testNodeClipboardPreservesBoundaryPinNodePolymorphism() {
        FlowGraph graph = new FlowGraph();
        IngredientStack inStack = IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 2.0);
        ModuleInputPin inputPin = new ModuleInputPin("in-pin", "Input Port 1", inStack);
        IngredientStack outStack = IngredientStack.item(ResourceLocation.tryParse("minecraft:gold_ingot"), "Gold Ingot", 1.0);
        ModuleOutputPin outputPin = new ModuleOutputPin("out-pin", "Output Port 1", outStack);

        graph.addNode(inputPin);
        graph.addNode(outputPin);

        NodeClipboard.getInstance().copy(graph, Set.of(inputPin.getId(), outputPin.getId()));
        FlowGraph destGraph = new FlowGraph();
        NodeClipboard.PasteResult result = NodeClipboard.getInstance().paste(destGraph, 300.0, 300.0);

        Assertions.assertEquals(2, result.nodes().size());
        for (RecipeNode pasted : result.nodes()) {
            Assertions.assertTrue(pasted instanceof BoundaryPinNode);
            BoundaryPinNode pin = (BoundaryPinNode) pasted;
            Assertions.assertTrue(pin.getDirection() == BoundaryPinNode.PinDirection.INPUT
                    || pin.getDirection() == BoundaryPinNode.PinDirection.OUTPUT);
        }
    }

    @Test
    public void testFlowGraphDeepCopyAndMutationIsolation() {
        FlowGraph graph = new FlowGraph();
        RecipeNode n1 = RecipeNode.create("Macerator", 20.0, 30.0, GTVoltageTier.LV);
        RecipeNode n2 = RecipeNode.create("Centrifuge", 40.0, 30.0, GTVoltageTier.LV);
        graph.addNode(n1);
        graph.addNode(n2);
        graph.addConnection(n1.getId(), 0, n2.getId(), 0, 5.0);

        FlowGraph copy = graph.copy();

        Assertions.assertNotNull(copy);
        Assertions.assertEquals(2, copy.getNodes().size());
        Assertions.assertEquals(1, copy.getConnections().size());

        RecipeNode n3 = RecipeNode.create("Smelter", 20.0, 30.0, GTVoltageTier.LV);
        copy.addNode(n3);
        Assertions.assertEquals(3, copy.getNodes().size());
        Assertions.assertEquals(2, graph.getNodes().size());
    }

    @Test
    public void testSteamAndMultiblockPropertiesPreservedOnCopy() {
        RecipeNode steamNode = RecipeNode.create("Steam Macerator", 30.0, 0.0, GTVoltageTier.LV);
        steamNode.setSteamMode(SteamMode.HIGH_PRESSURE);

        RecipeNode steamCopy = steamNode.copy();
        Assertions.assertEquals(SteamMode.HIGH_PRESSURE, steamCopy.getSteamMode());

        steamCopy.setSteamMode(SteamMode.NONE);
        Assertions.assertEquals(SteamMode.HIGH_PRESSURE, steamNode.getSteamMode());

        RecipeNode mbNode = RecipeNode.create("Large Chemical Reactor", 60.0, 120.0, GTVoltageTier.MV);
        mbNode.setMultiblock(true);

        RecipeNode mbCopy = mbNode.copy();
        Assertions.assertTrue(mbCopy.isMultiblock());

        mbCopy.setMultiblock(false);
        Assertions.assertTrue(mbNode.isMultiblock());
    }
}
