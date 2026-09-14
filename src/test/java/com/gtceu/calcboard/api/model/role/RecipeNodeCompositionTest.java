package com.gtceu.calcboard.api.model.role;

import com.gtceu.calcboard.api.model.BoundaryPinNode;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.ModuleInputPin;
import com.gtceu.calcboard.api.model.ModuleOutputPin;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.RecipeNodeSerializer;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.FlowSplitMode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SupplyMode;
import com.gtceu.calcboard.api.solver.FlowGraphModuleHandler;
import com.gtceu.calcboard.api.solver.FlowSummaryAggregator;
import com.gtceu.calcboard.api.solver.linear.TwoStageLinearFlowSolver;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RecipeNodeCompositionTest {

    @Test
    public void testDefaultMachineNodeRole() {
        RecipeNode node = RecipeNode.create("Test Macerator", 100.0, 32.0, GTVoltageTier.LV);
        Assertions.assertTrue(node.isMachine());
        Assertions.assertFalse(node.isJunction());
        Assertions.assertFalse(node.isModule());
        Assertions.assertFalse(node.isBoundaryPin());

        Assertions.assertNotNull(node.asMachine());
        Assertions.assertEquals(NodeRoleType.MACHINE, node.getRole().getRoleType());
        Assertions.assertThrows(IllegalStateException.class, node::asJunction);
        Assertions.assertThrows(IllegalStateException.class, node::asModule);
        Assertions.assertThrows(IllegalStateException.class, node::asBoundaryPin);
    }

    @Test
    public void testJunctionNodeRole() {
        RecipeNode node = RecipeNode.createReroute(15.0, 25.0);
        Assertions.assertTrue(node.isJunction());
        Assertions.assertTrue(node.isReroute());
        Assertions.assertFalse(node.isMachine());
        Assertions.assertEquals(32, node.getCardWidth());
        Assertions.assertEquals(32, node.getCardHeight());

        JunctionNodeRole junctionRole = node.asJunction();
        Assertions.assertNotNull(junctionRole);
        Assertions.assertEquals(NodeRoleType.JUNCTION, junctionRole.getRoleType());

        junctionRole.setSupplyMode(SupplyMode.FIXED_RATE);
        junctionRole.setExternalSupplyRate(45.0);
        Assertions.assertTrue(node.isExternalSupply());
        Assertions.assertEquals(45.0, node.getExternalSupplyRate(), 0.001);

        junctionRole.setBuffer(true);
        junctionRole.setBufferSize(200.0);
        junctionRole.setSplitMode(FlowSplitMode.EQUAL);
        Assertions.assertTrue(node.isJunctionBuffer());
        Assertions.assertEquals(200.0, node.getJunctionBufferSize(), 0.001);
        Assertions.assertEquals(FlowSplitMode.EQUAL, node.getJunctionSplitMode());
    }

    @Test
    public void testSubPageModuleNodeRole() {
        RecipeNode node = new RecipeNode("mod-1", "Composite Module", 20.0, 0.0, GTVoltageTier.LV);
        node.setRole(new SubPageModuleNodeRole("page-abc"));

        Assertions.assertTrue(node.isModule());
        Assertions.assertFalse(node.isMachine());

        SubPageModuleNodeRole moduleRole = node.asModule();
        Assertions.assertNotNull(moduleRole);
        Assertions.assertEquals("page-abc", moduleRole.getSubPageId());
        Assertions.assertEquals("page-abc", node.getSubPageId());

        moduleRole.setContainedMachineCount(7);
        Assertions.assertEquals(7, node.getContainedMachineCount());

        moduleRole.setScaleMultiplier(2.5);
        Assertions.assertEquals(2.5, moduleRole.getScaleMultiplier(), 0.001);
    }

    @Test
    public void testBoundaryPinNodeRole() {
        IngredientStack ing = IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper", 4, 1.0);
        ModuleInputPin inPin = new ModuleInputPin("pin-in", "Input A", ing);
        Assertions.assertTrue(inPin.isBoundaryPin());
        Assertions.assertEquals(BoundaryPinNode.PinDirection.INPUT, inPin.getDirection());
        Assertions.assertEquals(32, inPin.getCardWidth());
        Assertions.assertEquals(32, inPin.getCardHeight());
        Assertions.assertEquals("Input A", inPin.getPinLabel());

        ModuleOutputPin outPin = new ModuleOutputPin("pin-out", "Output B", ing);
        Assertions.assertTrue(outPin.isBoundaryPin());
        Assertions.assertEquals(BoundaryPinNode.PinDirection.OUTPUT, outPin.getDirection());
        Assertions.assertEquals("Output B", outPin.getPinLabel());
    }

    @Test
    public void testRoleSwitchingAndLifecycle() {
        RecipeNode node = RecipeNode.create("Polymerizer", 50.0, 128.0, GTVoltageTier.MV);
        Assertions.assertTrue(node.isMachine());
        Assertions.assertTrue(node.getRole(MachineNodeRole.class).isPresent());

        node.setReroute(true);
        Assertions.assertTrue(node.isJunction());
        Assertions.assertTrue(node.getRole(JunctionNodeRole.class).isPresent());
        Assertions.assertFalse(node.getRole(MachineNodeRole.class).isPresent());

        node.setReroute(false);
        Assertions.assertTrue(node.isMachine());
        Assertions.assertTrue(node.getRole(MachineNodeRole.class).isPresent());
    }

    @Test
    public void testLegacyV20BlueprintLoading() {
        CompoundTag legacyTag = new CompoundTag();
        legacyTag.putString("id", "legacy-m1");
        legacyTag.putString("name", "Legacy EBF");
        legacyTag.putDouble("baseDuration", 200.0);
        legacyTag.putDouble("baseEUt", 120.0);
        legacyTag.putString("recipeTier", "MV");
        legacyTag.putString("targetTier", "HV");
        legacyTag.putDouble("machineCount", 3.5);
        legacyTag.putInt("parallel", 4);

        RecipeNode loaded = RecipeNodeSerializer.deserialize(legacyTag);
        Assertions.assertNotNull(loaded);
        Assertions.assertTrue(loaded.isMachine());
        Assertions.assertEquals(NodeRoleType.MACHINE, loaded.getRole().getRoleType());
        Assertions.assertEquals("Legacy EBF", loaded.getName());
        Assertions.assertEquals(3.5, loaded.getMachineCount(), 0.001);
        Assertions.assertEquals(4, loaded.getParallel());
        Assertions.assertEquals(GTVoltageTier.HV, loaded.getTargetTier());
    }

    @Test
    public void testLegacyRerouteNbtLoading() {
        CompoundTag legacyReroute = new CompoundTag();
        legacyReroute.putString("id", "legacy-r1");
        legacyReroute.putString("name", "Legacy Reroute");
        legacyReroute.putBoolean("isReroute", true);
        legacyReroute.putString("supplyMode", "FIXED_RATE");
        legacyReroute.putDouble("externalSupplyRate", 75.0);

        RecipeNode loaded = RecipeNodeSerializer.deserialize(legacyReroute);
        Assertions.assertNotNull(loaded);
        Assertions.assertTrue(loaded.isJunction());
        Assertions.assertTrue(loaded.isReroute());
        Assertions.assertEquals(NodeRoleType.JUNCTION, loaded.getRole().getRoleType());
        Assertions.assertEquals(SupplyMode.FIXED_RATE, loaded.getSupplyMode());
        Assertions.assertEquals(75.0, loaded.getExternalSupplyRate(), 0.001);
    }

    @Test
    public void testLegacyModuleNbtLoading() {
        CompoundTag legacyModule = new CompoundTag();
        legacyModule.putString("id", "legacy-mod1");
        legacyModule.putString("name", "Legacy SubPage");
        legacyModule.putBoolean("isModule", true);
        legacyModule.putString("subPageId", "subpage-xyz");
        legacyModule.putInt("containedMachineCount", 12);

        RecipeNode loaded = RecipeNodeSerializer.deserialize(legacyModule);
        Assertions.assertNotNull(loaded);
        Assertions.assertTrue(loaded.isModule());
        Assertions.assertEquals(NodeRoleType.MODULE, loaded.getRole().getRoleType());
        Assertions.assertEquals("subpage-xyz", loaded.getSubPageId());
        Assertions.assertEquals(12, loaded.getContainedMachineCount());
    }

    @Test
    public void testLegacyBoundaryPinNbtLoading() {
        CompoundTag legacyPin = new CompoundTag();
        legacyPin.putString("id", "legacy-pin1");
        legacyPin.putString("pinType", "OUTPUT");
        legacyPin.putString("pinLabel", "Exhaust Port");
        legacyPin.putInt("targetPortIndex", 3);

        RecipeNode loaded = RecipeNodeSerializer.deserialize(legacyPin);
        Assertions.assertNotNull(loaded);
        Assertions.assertTrue(loaded.isBoundaryPin());
        Assertions.assertEquals(NodeRoleType.BOUNDARY_PIN, loaded.getRole().getRoleType());
        Assertions.assertEquals("Exhaust Port", loaded.getName());
        BoundaryPinNode pinNode = (BoundaryPinNode) loaded;
        Assertions.assertEquals(BoundaryPinNode.PinDirection.OUTPUT, pinNode.getDirection());
        Assertions.assertEquals(3, pinNode.getTargetPortIndex());
    }

    @Test
    public void testDualWriteNbtRoundtrip() {
        RecipeNode machine = RecipeNode.create("Dual Machine", 80.0, 64.0, GTVoltageTier.MV);
        machine.setMachineCount(2.0);
        machine.setParallel(8);

        CompoundTag machineTag = RecipeNodeSerializer.serialize(machine);
        Assertions.assertTrue(machineTag.contains("roleType"));
        Assertions.assertEquals("MACHINE", machineTag.getString("roleType"));
        Assertions.assertTrue(machineTag.contains("roleData"));
        Assertions.assertTrue(machineTag.contains("machineCount"));
        Assertions.assertEquals(2.0, machineTag.getDouble("machineCount"), 0.001);

        RecipeNode roundtripMachine = RecipeNodeSerializer.deserialize(machineTag);
        Assertions.assertNotNull(roundtripMachine);
        Assertions.assertTrue(roundtripMachine.isMachine());
        Assertions.assertEquals(2.0, roundtripMachine.getMachineCount(), 0.001);
        Assertions.assertEquals(8, roundtripMachine.getParallel());

        RecipeNode junction = RecipeNode.createReroute(100.0, 200.0);
        junction.setSupplyMode(SupplyMode.FIXED_DRAIN);
        junction.setExternalDrainRate(12.5);

        CompoundTag junctionTag = RecipeNodeSerializer.serialize(junction);
        Assertions.assertTrue(junctionTag.contains("roleType"));
        Assertions.assertEquals("JUNCTION", junctionTag.getString("roleType"));
        Assertions.assertTrue(junctionTag.getBoolean("isReroute"));
        Assertions.assertEquals(12.5, junctionTag.getDouble("externalDrainRate"), 0.001);

        RecipeNode roundtripJunction = RecipeNodeSerializer.deserialize(junctionTag);
        Assertions.assertNotNull(roundtripJunction);
        Assertions.assertTrue(roundtripJunction.isJunction());
        Assertions.assertEquals(SupplyMode.FIXED_DRAIN, roundtripJunction.getSupplyMode());
        Assertions.assertEquals(12.5, roundtripJunction.getExternalDrainRate(), 0.001);
    }

    @Test
    public void testNodeCalculationSnapshotImmutability() {
        FlowGraph graph = new FlowGraph();
        RecipeNode machine = RecipeNode.create("Calc Machine", 100.0, 32.0, GTVoltageTier.LV);
        machine.setMachineCount(1.0);
        machine.setParallel(1);
        graph.addNode(machine);

        FlowGraphSnapshot initialSnapshot = graph.captureSnapshot();
        Assertions.assertNotNull(initialSnapshot);
        NodeCalculationSnapshot initialNodeSnap = initialSnapshot.getNodeSnapshot(machine.getId());
        Assertions.assertNotNull(initialNodeSnap);
        Assertions.assertEquals(NodeRoleType.MACHINE, initialNodeSnap.roleType());
        double originalNominalCps = initialNodeSnap.nominalCyclesPerSecond();

        machine.setMachineCount(10.0);
        machine.markOverclockDirty();

        Assertions.assertEquals(originalNominalCps, initialNodeSnap.nominalCyclesPerSecond(), 0.0001);
        Assertions.assertEquals(originalNominalCps, graph.getSnapshot().getNodeSnapshot(machine.getId()).nominalCyclesPerSecond(), 0.0001);

        FlowGraphSnapshot newSnapshot = graph.captureSnapshot();
        Assertions.assertNotEquals(originalNominalCps, newSnapshot.getNodeSnapshot(machine.getId()).nominalCyclesPerSecond(), 0.0001);
    }

    @Test
    public void testModuleSubGraphDualWriteRoundtrip() {
        RecipeNode moduleNode = new RecipeNode("mod-sub", "Composite Sub", 20.0, 0.0, GTVoltageTier.LV);
        SubPageModuleNodeRole role = new SubPageModuleNodeRole("subpage-1");
        FlowGraph sub = new FlowGraph();
        RecipeNode innerMachine = RecipeNode.create("Inner Machine", 40.0, 30.0, GTVoltageTier.LV);
        sub.addNode(innerMachine);
        role.setSubGraph(sub);
        moduleNode.setRole(role);

        CompoundTag tag = RecipeNodeSerializer.serialize(moduleNode);
        Assertions.assertTrue(tag.contains("roleData"));
        Assertions.assertTrue(tag.contains("subGraph"));
        Assertions.assertTrue(tag.getCompound("roleData").contains("subGraph"));

        RecipeNode loaded = RecipeNodeSerializer.deserialize(tag);
        Assertions.assertNotNull(loaded);
        Assertions.assertTrue(loaded.isModule());
        Assertions.assertNotNull(loaded.getSubGraph());
        Assertions.assertEquals(1, loaded.getSubGraph().getNodes().size());
        Assertions.assertEquals("Inner Machine", loaded.getSubGraph().getNodes().get(0).getName());
    }

    @Test
    public void testTwoStageLinearFlowSolverWithModuleNode() {
        FlowGraph graph = new FlowGraph();

        RecipeNode module = new RecipeNode("mod-prod", "Module Producer", 20.0, 0.0, GTVoltageTier.LV);
        SubPageModuleNodeRole role = new SubPageModuleNodeRole("subpage-2");
        module.setRole(role);
        IngredientStack copper = IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper", 4.0, 1.0);
        module.addOutput(copper);
        module.setMachineCount(1.0);
        graph.addNode(module);

        RecipeNode consumer = RecipeNode.create("Wiremill", 20.0, 30.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.item(copper.getId(), copper.getDisplayName(), 2.0, 1.0));
        consumer.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:copper_wire"), "Copper Wire", 4.0, 1.0));
        consumer.setMachineCount(4.0);
        graph.addNode(consumer);

        graph.addConnection(module.getId(), 0, consumer.getId(), 0);

        TwoStageLinearFlowSolver.SolveResult result = TwoStageLinearFlowSolver.solve(graph, consumer, true);
        Assertions.assertTrue(result.successful());
        Assertions.assertEquals(2.0, result.machineCounts().get(module.getId()), 1e-4);
        Assertions.assertEquals(4.0, result.machineCounts().get(consumer.getId()), 1e-4);
    }

    @Test
    public void testModuleOverclockResultCps() {
        RecipeNode module = new RecipeNode("mod-dur", "Module Duration", 20.0, 0.0, GTVoltageTier.LV);
        SubPageModuleNodeRole role = new SubPageModuleNodeRole("page-dur");
        role.setBaseDurationTicks(100.0);
        module.setRole(role);

        Assertions.assertEquals(0.2, module.getOverclockResult().getCyclesPerSecond(), 1e-4);
    }

    @Test
    public void testCanonicalRecipeNodeBoundaryPinHandling() {
        RecipeNode pinNode = new RecipeNode("pin-canonical", "Pin Output", 20.0, 0.0, GTVoltageTier.LV);
        BoundaryPinNodeRole role = new BoundaryPinNodeRole(BoundaryPinNode.PinDirection.OUTPUT, "Out Slot", 0, IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 1.0, 1.0));
        pinNode.setRole(role);

        FlowGraph subGraph = new FlowGraph();
        subGraph.addNode(pinNode);

        RecipeNode parentModule = new RecipeNode("parent-mod", "Parent Mod", 20.0, 0.0, GTVoltageTier.LV);
        SubPageModuleNodeRole modRole = new SubPageModuleNodeRole("subpage-pin");
        modRole.setSubGraph(subGraph);
        parentModule.setRole(modRole);

        FlowGraphModuleHandler.syncModulePortsFromSubGraph(parentModule, subGraph);
        Assertions.assertEquals(1, parentModule.getOutputs().size());
        Assertions.assertEquals(1, modRole.getOutputPinNodeIds().size());
        Assertions.assertEquals("pin-canonical", modRole.getOutputPinNodeIds().get(0));
    }

    @Test
    public void testBoundaryPinNamePreservedOnDeserialization() {
        CompoundTag pinTag = new CompoundTag();
        pinTag.putString("id", "pin-named");
        pinTag.putString("name", "Custom Named Pin");
        pinTag.putString("pinType", "INPUT");
        pinTag.putInt("targetPortIndex", 1);

        RecipeNode loaded = RecipeNodeSerializer.deserialize(pinTag);
        Assertions.assertNotNull(loaded);
        Assertions.assertTrue(loaded.isBoundaryPin());
        Assertions.assertEquals("Custom Named Pin", loaded.getName());
    }

    @Test
    public void testRoleTransitionResetsCardDimensions() {
        RecipeNode node = RecipeNode.createReroute(10.0, 20.0);
        Assertions.assertEquals(32, node.getCardWidth());
        Assertions.assertEquals(32, node.getCardHeight());

        node.setRole(new MachineNodeRole());
        Assertions.assertEquals(245, node.getCardWidth());
        Assertions.assertEquals(0, node.getCardHeight());

        node.setRole(new JunctionNodeRole());
        Assertions.assertEquals(32, node.getCardWidth());
        Assertions.assertEquals(32, node.getCardHeight());
    }

    @Test
    public void testSubGraphSnapshotCapturedInAggregator() {
        FlowGraph root = new FlowGraph();
        FlowGraph sub = new FlowGraph();

        RecipeNode innerMachine = RecipeNode.create("Inner Machine", 20.0, 30.0, GTVoltageTier.LV);
        innerMachine.setMachineCount(1.0);
        sub.addNode(innerMachine);

        RecipeNode module = new RecipeNode("mod-agg", "Module Agg", 20.0, 0.0, GTVoltageTier.LV);
        SubPageModuleNodeRole modRole = new SubPageModuleNodeRole("sub-agg");
        modRole.setSubGraph(sub);
        module.setRole(modRole);
        root.addNode(module);

        FlowSummaryAggregator.computeSummary(root);

        Assertions.assertNotNull(root.getSnapshot());
        Assertions.assertNotNull(sub.getSnapshot());
        Assertions.assertNotNull(sub.getSnapshot().getNodeSnapshot(innerMachine.getId()));
    }

    @Test
    public void testSubPageModuleNodeRoleSnapshotDetails() {
        FlowGraph graph = new FlowGraph();
        RecipeNode module = new RecipeNode("mod-snap", "Module Snapshot", 20.0, 0.0, GTVoltageTier.LV);
        SubPageModuleNodeRole modRole = new SubPageModuleNodeRole("sub-snap");
        modRole.setBaseEUt(64.0);
        modRole.setBaseDurationTicks(40.0);
        modRole.setScaleMultiplier(2.0);
        module.setRole(modRole);
        module.setEfficiency(0.5);
        module.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 2.0, 1.0));
        module.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:iron_plate"), "Plate", 1.0, 1.0));
        graph.addNode(module);

        NodeCalculationSnapshot snap = modRole.captureSnapshot(graph);
        Assertions.assertNotNull(snap);
        Assertions.assertEquals(NodeRoleType.MODULE, snap.roleType());
        Assertions.assertTrue(snap.isStarved());
        Assertions.assertEquals(1.0, snap.nominalCyclesPerSecond(), 1e-4);
        Assertions.assertEquals(0.5, snap.effectiveCyclesPerSecond(), 1e-4);
        Assertions.assertEquals(64.0, snap.singleMachinePower(), 1e-4);
        Assertions.assertEquals(128.0, snap.totalPower(), 1e-4);
        Assertions.assertEquals(64.0, snap.effectiveTotalPower(), 1e-4);
        Assertions.assertEquals(1.0, snap.effectiveInputChances().get(0), 1e-4);
        Assertions.assertEquals(1.0, snap.effectiveOutputChances().get(0), 1e-4);
    }
}
