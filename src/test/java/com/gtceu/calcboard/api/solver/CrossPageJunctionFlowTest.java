package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.CrossPageExportTarget;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.role.JunctionNodeRole;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.FlowSplitMode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SupplyMode;
import com.gtceu.calcboard.api.storage.RecipeNodeSerializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class CrossPageJunctionFlowTest {

    private RecipeNode createJunction(String id, String name) {
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), name, 20, 0, GTVoltageTier.LV);
        node.setId(id);
        node.setReroute(true);
        node.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("minecraft:oak_log"), "Oak Log", 1));
        return node;
    }

    private RecipeNode createConsumer(String id, String name, double requiredRate) {
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:consumer"), name, 20, 30, GTVoltageTier.LV);
        node.setId(id);
        node.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:oak_log"), "Oak Log", requiredRate));
        return node;
    }

    @Test
    @DisplayName("RFC-064: CrossPageExportTarget and JunctionNodeRole NBT serialization and deep copy")
    void testSerializationAndCopy() {
        JunctionNodeRole role = new JunctionNodeRole();
        role.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        role.setLinkedSourcePageId("page_source_1");
        role.setLinkedSourceNodeId("node_source_1");
        role.setAllocatedInputRate(75.5);
        role.addExportTarget(new CrossPageExportTarget("page_target_1", 2, 60.0));
        role.addExportTarget(new CrossPageExportTarget("page_target_2", 1, 0.0));

        CompoundTag tag = new CompoundTag();
        role.serializeRoleNBT(tag, Set.of(), 0);

        JunctionNodeRole loaded = new JunctionNodeRole();
        loaded.deserializeRoleNBT(tag);

        Assertions.assertEquals(SupplyMode.LINKED_JUNCTION, loaded.getSupplyMode());
        Assertions.assertEquals("page_source_1", loaded.getLinkedSourcePageId());
        Assertions.assertEquals("node_source_1", loaded.getLinkedSourceNodeId());
        Assertions.assertEquals(2, loaded.getExportTargets().size());

        CrossPageExportTarget t1 = loaded.getExportTargets().get(0);
        Assertions.assertEquals("page_target_1", t1.targetPageId());
        Assertions.assertEquals(2, t1.priority());
        Assertions.assertEquals(60.0, t1.fixedLimit(), 0.001);

        CrossPageExportTarget t2 = loaded.getExportTargets().get(1);
        Assertions.assertEquals("page_target_2", t2.targetPageId());
        Assertions.assertEquals(1, t2.priority());
        Assertions.assertEquals(0.0, t2.fixedLimit(), 0.001);

        JunctionNodeRole copied = role.copy(Set.of(), 0);
        Assertions.assertEquals(role.getSupplyMode(), copied.getSupplyMode());
        Assertions.assertEquals(role.getLinkedSourcePageId(), copied.getLinkedSourcePageId());
        Assertions.assertEquals(role.getLinkedSourceNodeId(), copied.getLinkedSourceNodeId());
        Assertions.assertEquals(role.getAllocatedInputRate(), copied.getAllocatedInputRate(), 0.001);
        Assertions.assertEquals(role.getExportTargets().size(), copied.getExportTargets().size());
    }

    @Test
    @DisplayName("RFC-064: Multi-page priority cascade allocation (P2 -> P1)")
    void testMultiPagePriorityCascade() {
        BoardPage pageA = new BoardPage("page_a", "Timber Farm", new FlowGraph());
        RecipeNode producerJunction = createJunction("prod_junc", "Wood Out");
        producerJunction.setSupplyMode(SupplyMode.FIXED_RATE);
        producerJunction.setExternalSupplyRate(100.0);
        producerJunction.addExportTarget(new CrossPageExportTarget("page_b", 2, 0.0));
        producerJunction.addExportTarget(new CrossPageExportTarget("page_c", 1, 0.0));
        pageA.getGraph().addNode(producerJunction);

        BoardPage pageB = new BoardPage("page_b", "Creosote Plant", new FlowGraph());
        RecipeNode consumerB = createJunction("cons_b", "Wood In B");
        consumerB.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        consumerB.setLinkedSourcePageId("page_a");
        consumerB.setLinkedSourceNodeId("prod_junc");
        RecipeNode machineB = createConsumer("mach_b", "Machine B", 60.0);
        pageB.getGraph().addNode(consumerB);
        pageB.getGraph().addNode(machineB);
        pageB.getGraph().addConnection(new FlowGraph.ConnectionEdge(consumerB.getId(), 0, machineB.getId(), 0));

        BoardPage pageC = new BoardPage("page_c", "Charcoal Plant", new FlowGraph());
        RecipeNode consumerC = createJunction("cons_c", "Wood In C");
        consumerC.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        consumerC.setLinkedSourcePageId("page_a");
        consumerC.setLinkedSourceNodeId("prod_junc");
        RecipeNode machineC = createConsumer("mach_c", "Machine C", 60.0);
        pageC.getGraph().addNode(consumerC);
        pageC.getGraph().addNode(machineC);
        pageC.getGraph().addConnection(new FlowGraph.ConnectionEdge(consumerC.getId(), 0, machineC.getId(), 0));

        WorkspaceFlowCoordinator.WorkspaceFlowResult result = WorkspaceFlowCoordinator.coordinate(List.of(pageA, pageB, pageC));

        Assertions.assertFalse(result.hasCycles());
        Assertions.assertEquals(60.0, result.getAllocatedRate("cons_b"), 0.001);
        Assertions.assertEquals(40.0, result.getAllocatedRate("cons_c"), 0.001);
        Assertions.assertFalse(result.isStarved("cons_b"));
        Assertions.assertTrue(result.isStarved("cons_c"));
        Assertions.assertEquals(60.0, consumerB.getExternalSupplyRate(), 0.001);
        Assertions.assertEquals(40.0, consumerC.getExternalSupplyRate(), 0.001);
    }

    @Test
    @DisplayName("RFC-064: Multi-page equal split mode")
    void testMultiPageEqualSplit() {
        BoardPage pageA = new BoardPage("page_a", "Supply Page", new FlowGraph());
        RecipeNode producer = createJunction("prod_junc", "Wood Out");
        producer.setSupplyMode(SupplyMode.FIXED_RATE);
        producer.setExternalSupplyRate(90.0);
        producer.setJunctionSplitMode(FlowSplitMode.EQUAL);
        producer.addExportTarget(new CrossPageExportTarget("page_b", 0, 0.0));
        producer.addExportTarget(new CrossPageExportTarget("page_c", 0, 0.0));
        pageA.getGraph().addNode(producer);

        BoardPage pageB = new BoardPage("page_b", "Consumer B", new FlowGraph());
        RecipeNode consumerB = createJunction("cons_b", "Wood In B");
        consumerB.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        consumerB.setLinkedSourcePageId("page_a");
        consumerB.setLinkedSourceNodeId("prod_junc");
        RecipeNode machineB = createConsumer("mach_b", "Machine B", 100.0);
        pageB.getGraph().addNode(consumerB);
        pageB.getGraph().addNode(machineB);
        pageB.getGraph().addConnection(new FlowGraph.ConnectionEdge(consumerB.getId(), 0, machineB.getId(), 0));

        BoardPage pageC = new BoardPage("page_c", "Consumer C", new FlowGraph());
        RecipeNode consumerC = createJunction("cons_c", "Wood In C");
        consumerC.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        consumerC.setLinkedSourcePageId("page_a");
        consumerC.setLinkedSourceNodeId("prod_junc");
        RecipeNode machineC = createConsumer("mach_c", "Machine C", 100.0);
        pageC.getGraph().addNode(consumerC);
        pageC.getGraph().addNode(machineC);
        pageC.getGraph().addConnection(new FlowGraph.ConnectionEdge(consumerC.getId(), 0, machineC.getId(), 0));

        WorkspaceFlowCoordinator.WorkspaceFlowResult result = WorkspaceFlowCoordinator.coordinate(List.of(pageA, pageB, pageC));

        Assertions.assertEquals(45.0, result.getAllocatedRate("cons_b"), 0.001);
        Assertions.assertEquals(45.0, result.getAllocatedRate("cons_c"), 0.001);
    }

    @Test
    @DisplayName("RFC-064: Circular dependency Tarjan SCC detection and flow clamping")
    void testCircularDependencyDetection() {
        BoardPage pageA = new BoardPage("page_a", "Page A", new FlowGraph());
        RecipeNode junctionA = createJunction("junc_a", "Junction A");
        junctionA.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        junctionA.setLinkedSourcePageId("page_b");
        junctionA.setLinkedSourceNodeId("junc_b");
        junctionA.addExportTarget(new CrossPageExportTarget("page_b", 0, 0.0));
        pageA.getGraph().addNode(junctionA);

        BoardPage pageB = new BoardPage("page_b", "Page B", new FlowGraph());
        RecipeNode junctionB = createJunction("junc_b", "Junction B");
        junctionB.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        junctionB.setLinkedSourcePageId("page_a");
        junctionB.setLinkedSourceNodeId("junc_a");
        junctionB.addExportTarget(new CrossPageExportTarget("page_a", 0, 0.0));
        pageB.getGraph().addNode(junctionB);

        WorkspaceFlowCoordinator.WorkspaceFlowResult result = WorkspaceFlowCoordinator.coordinate(List.of(pageA, pageB));

        Assertions.assertTrue(result.hasCycles());
        Assertions.assertTrue(result.circularPageIds().contains("page_a"));
        Assertions.assertTrue(result.circularPageIds().contains("page_b"));
        Assertions.assertTrue(result.isCircular("junc_a"));
        Assertions.assertTrue(result.isCircular("junc_b"));
        Assertions.assertEquals(0.0, result.getAllocatedRate("junc_a"), 0.001);
        Assertions.assertEquals(0.0, result.getAllocatedRate("junc_b"), 0.001);
    }

    @Test
    @DisplayName("RFC-064: Broken link detection for missing target page or node")
    void testBrokenLinkDetection() {
        BoardPage pageA = new BoardPage("page_a", "Page A", new FlowGraph());
        RecipeNode consumer = createJunction("cons_a", "Consumer A");
        consumer.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        consumer.setLinkedSourcePageId("non_existent_page");
        consumer.setLinkedSourceNodeId("non_existent_node");
        pageA.getGraph().addNode(consumer);

        RecipeNode producer = createJunction("prod_a", "Producer A");
        producer.addExportTarget(new CrossPageExportTarget("ghost_page", 1, 0.0));
        pageA.getGraph().addNode(producer);

        WorkspaceFlowCoordinator.WorkspaceFlowResult result = WorkspaceFlowCoordinator.coordinate(List.of(pageA));

        Assertions.assertTrue(result.isBroken("cons_a"));
        Assertions.assertTrue(result.isBroken("prod_a"));
    }

    @Test
    @DisplayName("RFC-064 Bugfix: Multiple consumers on same page linking to different junctions on same source page")
    void testMultipleConsumersOnSamePageDifferentiated() {
        BoardPage pageA = new BoardPage("page_a", "Page A", new FlowGraph());
        RecipeNode prodIron = createJunction("prod_iron", "Iron Producer");
        prodIron.setSupplyMode(SupplyMode.FIXED_RATE);
        prodIron.setExternalSupplyRate(100.0);
        prodIron.addExportTarget(new CrossPageExportTarget("page_b", 1, 0.0));
        pageA.getGraph().addNode(prodIron);

        RecipeNode prodCopper = createJunction("prod_copper", "Copper Producer");
        prodCopper.setSupplyMode(SupplyMode.FIXED_RATE);
        prodCopper.setExternalSupplyRate(50.0);
        prodCopper.addExportTarget(new CrossPageExportTarget("page_b", 1, 0.0));
        pageA.getGraph().addNode(prodCopper);

        BoardPage pageB = new BoardPage("page_b", "Page B", new FlowGraph());
        RecipeNode consIron = createJunction("cons_iron", "Iron Consumer");
        consIron.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        consIron.setLinkedSource("page_a", "prod_iron");
        RecipeNode ironMach = createConsumer("iron_mach", "Iron Machine", 80.0);
        pageB.getGraph().addNode(consIron);
        pageB.getGraph().addNode(ironMach);
        pageB.getGraph().addConnection(new FlowGraph.ConnectionEdge(consIron.getId(), 0, ironMach.getId(), 0));

        RecipeNode consCopper = createJunction("cons_copper", "Copper Consumer");
        consCopper.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        consCopper.setLinkedSource("page_a", "prod_copper");
        RecipeNode copperMach = createConsumer("copper_mach", "Copper Machine", 30.0);
        pageB.getGraph().addNode(consCopper);
        pageB.getGraph().addNode(copperMach);
        pageB.getGraph().addConnection(new FlowGraph.ConnectionEdge(consCopper.getId(), 0, copperMach.getId(), 0));

        WorkspaceFlowCoordinator.WorkspaceFlowResult result = WorkspaceFlowCoordinator.coordinate(List.of(pageA, pageB));

        Assertions.assertEquals(80.0, result.getAllocatedRate("cons_iron"), 0.001);
        Assertions.assertEquals(80.0, consIron.getAllocatedInputRate(), 0.001);
        Assertions.assertEquals(30.0, result.getAllocatedRate("cons_copper"), 0.001);
        Assertions.assertEquals(30.0, consCopper.getAllocatedInputRate(), 0.001);
    }

    @Test
    @DisplayName("RFC-064: Multiple consumers on same page with fixed limit caps")
    void testMultipleConsumersOnSamePageWithFixedLimits() {
        BoardPage pageA = new BoardPage("page_a", "Page A", new FlowGraph());
        RecipeNode prodIron = createJunction("prod_iron", "Iron Producer");
        prodIron.setSupplyMode(SupplyMode.FIXED_RATE);
        prodIron.setExternalSupplyRate(100.0);
        prodIron.addExportTarget(new CrossPageExportTarget("page_b", 1, 80.0));
        pageA.getGraph().addNode(prodIron);

        RecipeNode prodCopper = createJunction("prod_copper", "Copper Producer");
        prodCopper.setSupplyMode(SupplyMode.FIXED_RATE);
        prodCopper.setExternalSupplyRate(50.0);
        prodCopper.addExportTarget(new CrossPageExportTarget("page_b", 1, 30.0));
        pageA.getGraph().addNode(prodCopper);

        BoardPage pageB = new BoardPage("page_b", "Page B", new FlowGraph());
        RecipeNode consIron = createJunction("cons_iron", "Iron Consumer");
        consIron.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        consIron.setLinkedSource("page_a", "prod_iron");
        RecipeNode ironMach = createConsumer("iron_mach", "Iron Machine", 80.0);
        pageB.getGraph().addNode(consIron);
        pageB.getGraph().addNode(ironMach);
        pageB.getGraph().addConnection(new FlowGraph.ConnectionEdge(consIron.getId(), 0, ironMach.getId(), 0));

        RecipeNode consCopper = createJunction("cons_copper", "Copper Consumer");
        consCopper.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        consCopper.setLinkedSource("page_a", "prod_copper");
        RecipeNode copperMach = createConsumer("copper_mach", "Copper Machine", 30.0);
        pageB.getGraph().addNode(consCopper);
        pageB.getGraph().addNode(copperMach);
        pageB.getGraph().addConnection(new FlowGraph.ConnectionEdge(consCopper.getId(), 0, copperMach.getId(), 0));

        WorkspaceFlowCoordinator.WorkspaceFlowResult result = WorkspaceFlowCoordinator.coordinate(List.of(pageA, pageB));

        Assertions.assertEquals(80.0, result.getAllocatedRate("cons_iron"), 0.001);
        Assertions.assertEquals(80.0, consIron.getAllocatedInputRate(), 0.001);
        Assertions.assertEquals(30.0, result.getAllocatedRate("cons_copper"), 0.001);
        Assertions.assertEquals(30.0, consCopper.getAllocatedInputRate(), 0.001);
    }

    @Test
    @DisplayName("RFC-064 Bugfix: Circular dependency actively clamps node allocatedInputRate to 0.0")
    void testCircularClampsNodeAllocatedInputRate() {
        BoardPage pageA = new BoardPage("page_a", "Page A", new FlowGraph());
        RecipeNode junctionA = createJunction("junc_a", "Junction A");
        junctionA.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        junctionA.setLinkedSource("page_b", "junc_b");
        junctionA.addExportTarget(new CrossPageExportTarget("page_b", 0, 0.0));
        junctionA.asJunction().setAllocatedInputRate(999.0);
        pageA.getGraph().addNode(junctionA);

        BoardPage pageB = new BoardPage("page_b", "Page B", new FlowGraph());
        RecipeNode junctionB = createJunction("junc_b", "Junction B");
        junctionB.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        junctionB.setLinkedSource("page_a", "junc_a");
        junctionB.addExportTarget(new CrossPageExportTarget("page_a", 0, 0.0));
        junctionB.asJunction().setAllocatedInputRate(888.0);
        pageB.getGraph().addNode(junctionB);

        WorkspaceFlowCoordinator.WorkspaceFlowResult result = WorkspaceFlowCoordinator.coordinate(List.of(pageA, pageB));

        Assertions.assertTrue(result.hasCycles());
        Assertions.assertEquals(0.0, junctionA.getAllocatedInputRate(), 0.001);
        Assertions.assertEquals(0.0, junctionB.getAllocatedInputRate(), 0.001);
        Assertions.assertEquals(0.0, junctionA.getExternalSupplyRate(), 0.001);
        Assertions.assertEquals(0.0, junctionB.getExternalSupplyRate(), 0.001);
    }

    @Test
    @DisplayName("RFC-064 Bugfix: Broken link resets node allocatedInputRate to 0.0")
    void testBrokenLinkResetsAllocatedInputRate() {
        BoardPage pageA = new BoardPage("page_a", "Page A", new FlowGraph());
        RecipeNode consumer = createJunction("cons_a", "Consumer A");
        consumer.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        consumer.setLinkedSource("deleted_page", "deleted_node");
        consumer.asJunction().setAllocatedInputRate(120.0);
        pageA.getGraph().addNode(consumer);

        WorkspaceFlowCoordinator.WorkspaceFlowResult result = WorkspaceFlowCoordinator.coordinate(List.of(pageA));

        Assertions.assertTrue(result.isBroken("cons_a"));
        Assertions.assertEquals(0.0, consumer.getAllocatedInputRate(), 0.001);
        Assertions.assertEquals(0.0, consumer.getExternalSupplyRate(), 0.001);
    }

    @Test
    @DisplayName("RFC-064: Joint local and cross-page allocation prevents double dipping")
    void testJointLocalAndCrossPageAllocation() {
        BoardPage pageA = new BoardPage("page_a", "Page A", new FlowGraph());
        RecipeNode producer = createJunction("prod_junc", "Producer Junction");
        producer.setSupplyMode(SupplyMode.FIXED_RATE);
        producer.setExternalSupplyRate(100.0);
        producer.addExportTarget(new CrossPageExportTarget("page_b", 2, 0.0));

        RecipeNode localMach = createConsumer("local_mach", "Local Machine", 50.0);
        pageA.getGraph().addNode(producer);
        pageA.getGraph().addNode(localMach);
        FlowGraph.ConnectionEdge localEdge = new FlowGraph.ConnectionEdge(producer.getId(), 0, localMach.getId(), 0, 0.0, 1, 1.0);
        pageA.getGraph().addConnection(localEdge);

        BoardPage pageB = new BoardPage("page_b", "Page B", new FlowGraph());
        RecipeNode consB = createJunction("cons_b", "Remote Consumer");
        consB.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        consB.setLinkedSource("page_a", "prod_junc");
        RecipeNode remoteMach = createConsumer("remote_mach", "Remote Machine", 80.0);
        pageB.getGraph().addNode(consB);
        pageB.getGraph().addNode(remoteMach);
        pageB.getGraph().addConnection(new FlowGraph.ConnectionEdge(consB.getId(), 0, remoteMach.getId(), 0));

        WorkspaceFlowCoordinator.WorkspaceFlowResult result = WorkspaceFlowCoordinator.coordinate(List.of(pageA, pageB));

        Assertions.assertEquals(80.0, result.getAllocatedRate("cons_b"), 0.001);
        Assertions.assertEquals(80.0, consB.getAllocatedInputRate(), 0.001);

        double localFlow = FlowEdgeAllocator.getEdgeAllocatedFlow(pageA.getGraph(), localEdge, null);
        Assertions.assertEquals(20.0, localFlow, 0.001);
    }

    @Test
    @DisplayName("RFC-064 Bugfix: Producer junction tracks allocatedExportRate and reflects consumption in page summary")
    void testProducerAllocatedExportRateAndConsumption() {
        BoardPage pageA = new BoardPage("page_a", "Page A", new FlowGraph());
        RecipeNode hammer = RecipeNode.create(ResourceLocation.tryParse("gtceu:forge_hammer"), "Forge Hammer", 20, 0, GTVoltageTier.LV);
        hammer.setId("hammer");
        hammer.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:oak_log"), "Oak Log", 4.0));
        RecipeNode producerJunc = createJunction("prod_junc", "Producer Junction");

        pageA.getGraph().addNode(hammer);
        pageA.getGraph().addNode(producerJunc);
        pageA.getGraph().addConnection(new FlowGraph.ConnectionEdge(hammer.getId(), 0, producerJunc.getId(), 0));

        BoardPage pageB = new BoardPage("page_b", "Page B", new FlowGraph());
        RecipeNode consJunc = createJunction("cons_junc", "Consumer Junction");
        consJunc.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        consJunc.setLinkedSource("page_a", "prod_junc");
        RecipeNode consumerMach = createConsumer("consumer_mach", "Consumer Machine", 2.67);

        pageB.getGraph().addNode(consJunc);
        pageB.getGraph().addNode(consumerMach);
        pageB.getGraph().addConnection(new FlowGraph.ConnectionEdge(consJunc.getId(), 0, consumerMach.getId(), 0));

        WorkspaceFlowCoordinator.WorkspaceFlowResult result = WorkspaceFlowCoordinator.coordinate(List.of(pageA, pageB));

        // 1. Verify consumer allocation
        Assertions.assertEquals(2.67, consJunc.getAllocatedInputRate(), 0.001);

        // 2. Verify producer allocatedExportRate
        Assertions.assertEquals(2.67, producerJunc.getAllocatedExportRate(), 0.001);

        // 3. Verify producer upstream demand includes cross-page export
        double hammerOutputDemand = FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(pageA.getGraph(), hammer, 0, null);
        Assertions.assertEquals(2.67, hammerOutputDemand, 0.001);

        // 4. Verify page A summary reflects export consumption and net balance
        BalanceSummary summaryA = pageA.getGraph().getCachedSummary();
        Assertions.assertNotNull(summaryA);

        IngredientStack oakKey = summaryA.totalConsumption().keySet().stream()
                .filter(k -> k.getDisplayName().equals("Oak Log"))
                .findFirst()
                .orElse(null);
        Assertions.assertNotNull(oakKey);
        Assertions.assertEquals(2.67, summaryA.totalConsumption().get(oakKey), 0.001);

        IngredientStack oakNetKey = summaryA.netOutputs().keySet().stream()
                .filter(k -> k.getDisplayName().equals("Oak Log"))
                .findFirst()
                .orElse(null);
        Assertions.assertNotNull(oakNetKey);
        Assertions.assertEquals(1.33, summaryA.netOutputs().get(oakNetKey), 0.01);
    }

    @Test
    @DisplayName("RFC-064: SourceJunctionMetrics accurately calculates totalProduction, totalUsage, and availableSurplus")
    void testSourceJunctionMetricsCalculation() {
        BoardPage pageA = new BoardPage("page_a", "Page A", new FlowGraph());
        RecipeNode hammer = RecipeNode.create(ResourceLocation.tryParse("gtceu:forge_hammer"), "Forge Hammer", 20, 0, GTVoltageTier.LV);
        hammer.setId("hammer");
        hammer.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:oak_log"), "Oak Log", 4.0));
        RecipeNode producerJunc = createJunction("prod_junc", "Producer Junction");
        RecipeNode localConsumer = createConsumer("local_mach", "Local Consumer", 1.0);

        pageA.getGraph().addNode(hammer);
        pageA.getGraph().addNode(producerJunc);
        pageA.getGraph().addNode(localConsumer);
        pageA.getGraph().addConnection(new FlowGraph.ConnectionEdge(hammer.getId(), 0, producerJunc.getId(), 0));
        pageA.getGraph().addConnection(new FlowGraph.ConnectionEdge(producerJunc.getId(), 0, localConsumer.getId(), 0));

        BoardPage pageB = new BoardPage("page_b", "Page B", new FlowGraph());
        RecipeNode consJunc = createJunction("cons_junc", "Consumer Junction");
        consJunc.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        consJunc.setLinkedSource("page_a", "prod_junc");
        RecipeNode remoteConsumer = createConsumer("remote_mach", "Remote Consumer", 1.5);

        pageB.getGraph().addNode(consJunc);
        pageB.getGraph().addNode(remoteConsumer);
        pageB.getGraph().addConnection(new FlowGraph.ConnectionEdge(consJunc.getId(), 0, remoteConsumer.getId(), 0));

        WorkspaceFlowCoordinator.coordinate(List.of(pageA, pageB));

        WorkspaceFlowCoordinator.SourceJunctionMetrics metrics =
                WorkspaceFlowCoordinator.calculateSourceJunctionMetrics(pageA, producerJunc);

        Assertions.assertEquals(4.0, metrics.totalProduction(), 0.001);
        Assertions.assertEquals(1.0, metrics.localDemand(), 0.001);
        Assertions.assertEquals(1.5, metrics.remoteExport(), 0.001);
        Assertions.assertEquals(2.5, metrics.totalUsage(), 0.001);
        Assertions.assertEquals(1.5, metrics.availableSurplus(), 0.001);
    }

    @Test
    @DisplayName("RFC-064: Sync Junction deduces inter-page priority from outgoing wire priority")
    void testOutgoingWirePriorityDeductionForSyncJunction() {
        BoardPage pageA = new BoardPage("page_a", "Producer Page", new FlowGraph());
        RecipeNode producerJunc = createJunction("prod_junc", "Producer Junction");
        producerJunc.setSupplyMode(SupplyMode.FIXED_RATE);
        producerJunc.setExternalSupplyRate(2.0);
        RecipeNode localMachine = createConsumer("local_mach", "Local Machine", 1.5);
        pageA.getGraph().addNode(producerJunc);
        pageA.getGraph().addNode(localMachine);
        // Local edge has default priority 0
        pageA.getGraph().addConnection(new FlowGraph.ConnectionEdge(producerJunc.getId(), 0, localMachine.getId(), 0, 0.0, 0, 1.0));

        BoardPage pageB = new BoardPage("page_b", "Consumer Page", new FlowGraph());
        RecipeNode syncJunc = createJunction("sync_junc", "Sync Junction");
        syncJunc.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        syncJunc.setLinkedSource("page_a", "prod_junc");
        RecipeNode forgeHammer = createConsumer("forge_hammer", "Forge Hammer", 1.5);
        pageB.getGraph().addNode(syncJunc);
        pageB.getGraph().addNode(forgeHammer);
        // Outgoing edge from sync junction has Priority 1!
        pageB.getGraph().addConnection(new FlowGraph.ConnectionEdge(syncJunc.getId(), 0, forgeHammer.getId(), 0, 0.0, 1, 1.0));

        WorkspaceFlowCoordinator.WorkspaceFlowResult result =
                WorkspaceFlowCoordinator.coordinate(List.of(pageA, pageB));

        // Sync junction's outgoing wire priority (P1) must be deduced and satisfy 1.5/s first over local machine (P0)
        Assertions.assertEquals(1.5, syncJunc.getAllocatedInputRate(), 0.001);
        Assertions.assertFalse(result.isStarved(syncJunc.getId()));
        Assertions.assertEquals(0.5, FlowEdgeAllocator.getEdgeAllocatedFlow(pageA.getGraph(), pageA.getGraph().getConnections().get(0), null), 0.001);

        // Also test between two remote sync junctions: P2 vs P1
        BoardPage pageC = new BoardPage("page_c", "High Priority Page", new FlowGraph());
        RecipeNode highPriJunc = createJunction("high_pri_junc", "High Priority Sync Junction");
        highPriJunc.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        highPriJunc.setLinkedSource("page_a", "prod_junc");
        RecipeNode highPriMachine = createConsumer("high_pri_mach", "High Priority Machine", 1.2);
        pageC.getGraph().addNode(highPriJunc);
        pageC.getGraph().addNode(highPriMachine);
        // Outgoing edge has Priority 2!
        pageC.getGraph().addConnection(new FlowGraph.ConnectionEdge(highPriJunc.getId(), 0, highPriMachine.getId(), 0, 0.0, 2, 1.0));

        WorkspaceFlowCoordinator.coordinate(List.of(pageA, pageB, pageC));

        // P2 (pageC, demand 1.2) gets 1.2 first
        Assertions.assertEquals(1.2, highPriJunc.getAllocatedInputRate(), 0.001);
        // P1 (pageB, demand 1.5) gets remaining 0.8
        Assertions.assertEquals(0.8, syncJunc.getAllocatedInputRate(), 0.001);
        // P0 (pageA local, demand 1.5) gets 0.0
        Assertions.assertEquals(0.0, FlowEdgeAllocator.getEdgeAllocatedFlow(pageA.getGraph(), pageA.getGraph().getConnections().get(0), null), 0.001);
    }

    @Test
    @DisplayName("RFC-064: Sync junction ingredient persists and synchronizes across reload")
    void testCrossPageJunctionIngredientPersistenceOnReload() {
        IngredientStack sandStack = IngredientStack.item(ResourceLocation.tryParse("minecraft:sand"), "Sand", 1.0);

        BoardPage pageA = new BoardPage("page_a", "Producer Page", new FlowGraph());
        RecipeNode producerJunc = createJunction("prod_junc", "Producer Junction");
        producerJunc.setSupplyMode(SupplyMode.FIXED_RATE);
        producerJunc.setExternalSupplyRate(4.0);
        producerJunc.bindRerouteIngredient(sandStack.copy());
        pageA.getGraph().addNode(producerJunc);

        BoardPage pageB = new BoardPage("page_b", "Consumer Page", new FlowGraph());
        RecipeNode syncJunc = createJunction("sync_junc", "Sync Junction");
        syncJunc.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        syncJunc.setLinkedSource("page_a", "prod_junc");
        RecipeNode consumerMachine = createConsumer("consumer_mach", "Consumer Machine", 2.0);
        pageB.getGraph().addNode(syncJunc);
        pageB.getGraph().addNode(consumerMachine);
        pageB.getGraph().addConnection(new FlowGraph.ConnectionEdge(syncJunc.getId(), 0, consumerMachine.getId(), 0));

        WorkspaceFlowCoordinator.coordinate(List.of(pageA, pageB));

        Assertions.assertNotNull(syncJunc.getRerouteIngredient());
        Assertions.assertEquals("Sand", syncJunc.getRerouteIngredient().getDisplayName());

        CompoundTag tagA = RecipeNodeSerializer.serialize(producerJunc);
        CompoundTag tagB = RecipeNodeSerializer.serialize(syncJunc);

        RecipeNode loadedProd = RecipeNodeSerializer.deserialize(tagA);
        RecipeNode loadedSync = RecipeNodeSerializer.deserialize(tagB);

        Assertions.assertNotNull(loadedProd.getRerouteIngredient());
        Assertions.assertEquals("Sand", loadedProd.getRerouteIngredient().getDisplayName());

        Assertions.assertNotNull(loadedSync.getRerouteIngredient());
        Assertions.assertEquals("Sand", loadedSync.getRerouteIngredient().getDisplayName());
        Assertions.assertEquals(1, loadedSync.getInputs().size());
        Assertions.assertEquals("Sand", loadedSync.getInputs().get(0).getDisplayName());
        Assertions.assertEquals(1, loadedSync.getOutputs().size());
        Assertions.assertEquals("Sand", loadedSync.getOutputs().get(0).getDisplayName());
    }

    @Test
    @DisplayName("RFC-064: Direct junction serialization does not erase ports via empty baseSpec projection")
    void testSyncJunctionDirectSerializationWithoutSpecDestruction() {
        IngredientStack sandStack = IngredientStack.item(ResourceLocation.tryParse("minecraft:sand"), "Sand", 1.0);

        RecipeNode junc = createJunction("direct_junc", "Direct Junction");
        junc.getBaseSpec();
        junc.bindRerouteIngredient(sandStack);

        CompoundTag tag = RecipeNodeSerializer.serialize(junc);
        RecipeNode deserialized = RecipeNodeSerializer.deserialize(tag);

        Assertions.assertNotNull(deserialized.getRerouteIngredient());
        Assertions.assertEquals("Sand", deserialized.getRerouteIngredient().getDisplayName());
        Assertions.assertFalse(deserialized.getInputs().isEmpty());
        Assertions.assertEquals("Sand", deserialized.getInputs().get(0).getDisplayName());
        Assertions.assertFalse(deserialized.getOutputs().isEmpty());
        Assertions.assertEquals("Sand", deserialized.getOutputs().get(0).getDisplayName());
    }

    @Test
    @DisplayName("RFC-064: Custom junction name is preserved across bind and unbind and survives serialization")
    void testCustomJunctionNamePreservationOnBindAndUnbind() {
        RecipeNode junction = createJunction("junc_custom", "Oxygen Bus");
        junction.setName("Main Oxygen Bus");
        junction.setHasCustomName(true);

        IngredientStack oxygen = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 1000);
        junction.bindRerouteIngredient(oxygen);
        Assertions.assertEquals("Main Oxygen Bus", junction.getName());
        Assertions.assertTrue(junction.hasCustomName());
        Assertions.assertEquals("Oxygen", junction.getRerouteIngredient().getDisplayName());

        junction.unbindRerouteIngredient();
        Assertions.assertEquals("Main Oxygen Bus", junction.getName());
        Assertions.assertTrue(junction.hasCustomName());

        CompoundTag tag = RecipeNodeSerializer.serialize(junction);
        RecipeNode loaded = RecipeNodeSerializer.deserialize(tag);
        Assertions.assertEquals("Main Oxygen Bus", loaded.getName());
        Assertions.assertTrue(loaded.hasCustomName());

        RecipeNode defaultJunc = createJunction("junc_def", "Oak Log");
        Assertions.assertFalse(defaultJunc.hasCustomName());
        defaultJunc.bindRerouteIngredient(oxygen);
        Assertions.assertEquals("Oxygen", defaultJunc.getName());
        defaultJunc.unbindRerouteIngredient();
        Assertions.assertEquals("Reroute", defaultJunc.getName());
    }

    @Test
    @DisplayName("RFC-064: Source junction metrics and custom name identification for cross-page search")
    void testSourceJunctionMetricsAndCustomNameIdentification() {
        BoardPage page = new BoardPage("page_cryo", "Cryogenics Facility", new FlowGraph());
        RecipeNode producer = RecipeNode.create(ResourceLocation.tryParse("gtceu:cryo_air"), "Air Distillation", 20, 30, GTVoltageTier.MV);
        producer.setId("cryo_prod");
        producer.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 500.0));
        page.getGraph().addNode(producer);

        RecipeNode junc = createJunction("cryo_junc", "Oxygen");
        junc.setName("Cryo Oxygen Out");
        junc.setHasCustomName(true);
        junc.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 1.0));
        page.getGraph().addNode(junc);

        page.getGraph().addConnection(producer.getId(), 0, junc.getId(), 0);

        WorkspaceFlowCoordinator.SourceJunctionMetrics metrics = WorkspaceFlowCoordinator.calculateSourceJunctionMetrics(page, junc);
        Assertions.assertEquals(500.0, metrics.totalProduction(), 0.001);
        Assertions.assertEquals(0.0, metrics.totalUsage(), 0.001);
        Assertions.assertEquals(500.0, metrics.availableSurplus(), 0.001);
        Assertions.assertEquals("Cryo Oxygen Out", junc.getName());
    }
}
