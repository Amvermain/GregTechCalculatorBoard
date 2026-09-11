package com.gtceu.calcboard.api.solver.fuzz;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SupplyMode;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Deterministic seed-based random flow graph generator for property-based fuzz testing.
 * Supports DAGs, closed recirculation loops, junction networks, stoichiometric cycles, and pathological topologies.
 */
public final class RandomFlowGraphGenerator {

    private static final String[] ITEM_NAMES = {
            "Iron Ingot", "Copper Ingot", "Gold Ingot", "Steel Ingot",
            "Sulfur Dust", "Carbon Dust", "Silicon Dust", "Polyethylene Plate"
    };

    private static final String[] ITEM_IDS = {
            "minecraft:iron_ingot", "gtceu:copper_ingot", "minecraft:gold_ingot", "gtceu:steel_ingot",
            "gtceu:sulfur_dust", "gtceu:carbon_dust", "gtceu:silicon_dust", "gtceu:polyethylene_plate"
    };

    private static final String[] FLUID_NAMES = {
            "Water", "Steam", "Oxygen", "Hydrogen",
            "Sulfur Dioxide", "Sulfuric Acid", "Benzene", "Methane"
    };

    private static final String[] FLUID_IDS = {
            "minecraft:water", "gtceu:steam", "gtceu:oxygen", "gtceu:hydrogen",
            "gtceu:sulfur_dioxide", "gtceu:sulfuric_acid", "gtceu:benzene", "gtceu:methane"
    };

    private static final double[] COMMON_DURATIONS = {10.0, 20.0, 30.0, 40.0, 50.0, 100.0, 200.0};
    private static final double[] COMMON_AMOUNTS = {0.125, 0.25, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 10.0, 100.0, 1000.0};

    private RandomFlowGraphGenerator() {}

    public static FlowGraph generateDag(long seed, int nodeCount) {
        Random rng = new Random(seed);
        FlowGraph graph = new FlowGraph();
        int count = Math.max(2, Math.min(30, nodeCount));
        List<RecipeNode> nodes = createMachineNodes(graph, rng, count);

        for (int i = 0; i < count - 1; i++) {
            RecipeNode src = nodes.get(i);
            RecipeNode dst = nodes.get(i + 1);
            connectMatchingNodes(graph, rng, src, dst);

            if (count > 3 && rng.nextDouble() < 0.4) {
                int targetIdx = i + 2 + rng.nextInt(count - i - 1);
                if (targetIdx < count) {
                    connectMatchingNodes(graph, rng, src, nodes.get(targetIdx));
                }
            }
        }

        ensureRawInput(nodes.get(0), pickMaterial(rng, rng.nextInt(16), pickAmount(rng)));
        ensureProductOutput(nodes.get(count - 1), pickMaterial(rng, rng.nextInt(16), pickAmount(rng)));
        return graph;
    }

    public static FlowGraph generateRecirculationLoop(long seed, int nodeCount) {
        Random rng = new Random(seed);
        FlowGraph graph = new FlowGraph();
        int count = Math.max(2, Math.min(30, nodeCount));
        List<RecipeNode> nodes = createMachineNodes(graph, rng, count);

        for (int i = 0; i < count - 1; i++) {
            connectMatchingNodes(graph, rng, nodes.get(i), nodes.get(i + 1));
        }

        RecipeNode last = nodes.get(count - 1);
        RecipeNode first = nodes.get(0);
        connectMatchingNodes(graph, rng, last, first);

        ensureRawInput(first, pickMaterial(rng, rng.nextInt(16), pickAmount(rng)));
        ensureProductOutput(last, pickMaterial(rng, rng.nextInt(16), pickAmount(rng)));

        if (count >= 4 && rng.nextDouble() < 0.5) {
            int mid = count / 2;
            connectMatchingNodes(graph, rng, nodes.get(mid), first);
        }

        return graph;
    }

    public static FlowGraph generateJunctionNetwork(long seed, int nodeCount) {
        Random rng = new Random(seed);
        FlowGraph graph = new FlowGraph();
        int count = Math.max(3, Math.min(30, nodeCount));
        int machineCount = Math.max(2, (count * 2) / 3);
        int junctionCount = Math.max(1, count - machineCount);

        List<RecipeNode> machines = createMachineNodes(graph, rng, machineCount);
        List<RecipeNode> junctions = new ArrayList<>();

        for (int j = 0; j < junctionCount; j++) {
            SupplyMode mode = pickSupplyMode(rng);
            double rate = 1.0 + rng.nextInt(5);
            IngredientStack mat = pickMaterial(rng, rng.nextInt(16), 1.0);
            RecipeNode junc = createJunctionNode(j, mat, mode, rate);
            graph.addNode(junc);
            junctions.add(junc);
        }

        for (RecipeNode junc : junctions) {
            wireJunctionHub(graph, rng, junc, machines);
        }

        return graph;
    }

    public static FlowGraph generateMixed(long seed, int nodeCount) {
        Random rng = new Random(seed);
        FlowGraph graph = new FlowGraph();
        int count = Math.max(4, Math.min(30, nodeCount));

        List<RecipeNode> allNodes = new ArrayList<>();
        int junctionCount = Math.max(1, count / 4);
        int machineCount = count - junctionCount;

        for (int i = 0; i < machineCount; i++) {
            RecipeNode m = createMachine("Machine_" + i, pickDuration(rng), 30.0);
            graph.addNode(m);
            allNodes.add(m);
        }

        for (int j = 0; j < junctionCount; j++) {
            SupplyMode mode = pickSupplyMode(rng);
            double rate = 1.0 + rng.nextInt(10);
            IngredientStack mat = pickMaterial(rng, rng.nextInt(16), 1.0);
            RecipeNode junc = createJunctionNode(j, mat, mode, rate);
            graph.addNode(junc);
            allNodes.add(junc);
        }

        for (int i = 0; i < allNodes.size() - 1; i++) {
            connectAnyNodes(graph, rng, allNodes.get(i), allNodes.get(i + 1));
        }

        if (allNodes.size() >= 3) {
            RecipeNode tail = allNodes.get(allNodes.size() - 1);
            RecipeNode head = allNodes.get(0);
            connectAnyNodes(graph, rng, tail, head);
        }

        for (int k = 0; k < count / 2; k++) {
            RecipeNode src = allNodes.get(rng.nextInt(allNodes.size()));
            RecipeNode dst = allNodes.get(rng.nextInt(allNodes.size()));
            if (src != dst) {
                connectAnyNodes(graph, rng, src, dst);
            }
        }

        return graph;
    }

    public static FlowGraph generateStoichiometricCycle(long seed, int cycleLength) {
        Random rng = new Random(seed);
        FlowGraph graph = new FlowGraph();
        int len = Math.max(2, Math.min(8, cycleLength));
        List<RecipeNode> nodes = new ArrayList<>();

        for (int i = 0; i < len; i++) {
            RecipeNode n = createMachine("Stoich_" + i, pickDuration(rng), 30.0);
            graph.addNode(n);
            nodes.add(n);
        }

        IngredientStack recycle = pickMaterial(rng, 0, 1.0 + rng.nextInt(3));
        IngredientStack rawFeed = pickMaterial(rng, 1, 1.0 + rng.nextInt(3));

        nodes.get(0).addInput(rawFeed.copy());
        nodes.get(0).addInput(recycle.copy());

        for (int i = 0; i < len; i++) {
            RecipeNode curr = nodes.get(i);
            RecipeNode next = nodes.get((i + 1) % len);
            double amount = 1.0 + rng.nextInt(4);
            IngredientStack mat = (i == len - 1) ? recycle : pickMaterial(rng, i + 2, amount);

            int outIdx = curr.getOutputs().size();
            curr.addOutput(mat.copy());

            if (i < len - 1) {
                int inIdx = next.getInputs().size();
                next.addInput(mat.copy());
                graph.addConnection(curr.getId(), outIdx, next.getId(), inIdx);
            } else {
                graph.addConnection(curr.getId(), outIdx, next.getId(), 1);
            }
        }

        return graph;
    }

    public static FlowGraph generatePathological(long seed, int nodeCount) {
        Random rng = new Random(seed);
        FlowGraph graph = new FlowGraph();
        int count = Math.max(2, Math.min(30, nodeCount));
        List<RecipeNode> machines = createMachineNodes(graph, rng, count);

        RecipeNode selfLoop = machines.get(0);
        IngredientStack loopMat = pickMaterial(rng, 0, 1.0);
        selfLoop.addOutput(loopMat.copy());
        selfLoop.addInput(loopMat.copy());
        graph.addConnection(selfLoop.getId(), selfLoop.getOutputs().size() - 1, selfLoop.getId(), selfLoop.getInputs().size() - 1);

        RecipeNode extreme1 = machines.get(rng.nextInt(count));
        RecipeNode extreme2 = machines.get(rng.nextInt(count));
        IngredientStack tiny = pickMaterial(rng, 2, 0.0001);
        IngredientStack huge = pickMaterial(rng, 3, 10000.0);
        extreme1.addOutput(tiny);
        extreme2.addInput(tiny);
        graph.addConnection(extreme1.getId(), extreme1.getOutputs().size() - 1, extreme2.getId(), extreme2.getInputs().size() - 1);

        for (int i = 0; i < count; i++) {
            for (int j = 0; j < count; j++) {
                if (i != j && rng.nextDouble() < 0.25) {
                    connectMatchingNodes(graph, rng, machines.get(i), machines.get(j));
                }
            }
        }

        return graph;
    }

    public static FlowGraph generateRandom(long seed) {
        Random rng = new Random(seed);
        int mode = rng.nextInt(6);
        int count = 3 + rng.nextInt(28);
        return switch (mode) {
            case 0 -> generateDag(seed, count);
            case 1 -> generateRecirculationLoop(seed, count);
            case 2 -> generateJunctionNetwork(seed, count);
            case 3 -> generateStoichiometricCycle(seed, 2 + rng.nextInt(5));
            case 4 -> generatePathological(seed, 2 + rng.nextInt(15));
            default -> generateMixed(seed, count);
        };
    }

    public static FlowGraph generateJunctionMeshWithMixedDrainAndSupply(long seed, int junctionCount) {
        Random rng = new Random(seed);
        FlowGraph graph = new FlowGraph();
        int jCount = Math.max(10, Math.min(25, junctionCount));
        int prodCount = 3 + rng.nextInt(3);
        int consCount = 3 + rng.nextInt(3);

        List<RecipeNode> producers = new ArrayList<>();
        for (int p = 0; p < prodCount; p++) {
            RecipeNode prod = createMachine("MeshProd_" + p, pickDuration(rng), 30.0);
            graph.addNode(prod);
            producers.add(prod);
        }

        List<RecipeNode> consumers = new ArrayList<>();
        for (int c = 0; c < consCount; c++) {
            RecipeNode cons = createMachine("MeshCons_" + c, pickDuration(rng), 30.0);
            graph.addNode(cons);
            consumers.add(cons);
        }

        List<RecipeNode> junctions = new ArrayList<>();
        for (int j = 0; j < jCount; j++) {
            SupplyMode mode = (j % 3 == 0) ? SupplyMode.FIXED_DRAIN : ((j % 3 == 1) ? SupplyMode.FIXED_RATE : SupplyMode.NONE);
            double rate = 5.0 * (1 + rng.nextInt(10));
            IngredientStack mat = pickMaterial(rng, j % 8, 1.0);
            RecipeNode junc = createJunctionNode(j, mat, mode, rate);
            graph.addNode(junc);
            junctions.add(junc);
        }

        for (RecipeNode prod : producers) {
            RecipeNode targetJunc = junctions.get(rng.nextInt(junctions.size() / 2));
            connectMachineToReroute(graph, rng, prod, targetJunc);
        }

        for (int j = 0; j < jCount - 1; j++) {
            connectRerouteToReroute(graph, rng, junctions.get(j), junctions.get(j + 1));
            if (rng.nextDouble() < 0.4 && j + 2 < jCount) {
                connectRerouteToReroute(graph, rng, junctions.get(j), junctions.get(j + 2));
            }
        }

        for (RecipeNode cons : consumers) {
            RecipeNode sourceJunc = junctions.get(junctions.size() / 2 + rng.nextInt(junctions.size() - junctions.size() / 2));
            connectRerouteToMachine(graph, rng, sourceJunc, cons);
        }

        return graph;
    }

    public static FlowGraph generateLargeScale(long seed, int nodeCount) {
        Random rng = new Random(seed);
        FlowGraph graph = new FlowGraph();
        int count = Math.max(50, Math.min(100, nodeCount));
        List<RecipeNode> nodes = createMachineNodes(graph, rng, count);

        for (int i = 0; i < count - 1; i++) {
            connectMatchingNodes(graph, rng, nodes.get(i), nodes.get(i + 1));
            if (i + 3 < count && rng.nextDouble() < 0.3) {
                connectMatchingNodes(graph, rng, nodes.get(i), nodes.get(i + 3));
            }
        }

        if (count >= 10) {
            connectMatchingNodes(graph, rng, nodes.get(count - 1), nodes.get(count / 2));
        }

        return graph;
    }

    public static FlowGraph generateDegenerate(int type, long seed) {
        Random rng = new Random(seed);
        FlowGraph graph = new FlowGraph();
        switch (type) {
            case 0 -> {
                return graph;
            }
            case 1 -> {
                RecipeNode isolated = createMachine("Isolated", 20.0, 30.0);
                graph.addNode(isolated);
                return graph;
            }
            case 2 -> {
                RecipeNode selfLoop = createMachine("SelfLoop", 20.0, 30.0);
                IngredientStack mat = pickMaterial(rng, 0, 1.0);
                selfLoop.addOutput(mat.copy());
                selfLoop.addInput(mat.copy());
                graph.addNode(selfLoop);
                graph.addConnection(selfLoop.getId(), 0, selfLoop.getId(), 0);
                return graph;
            }
            case 3 -> {
                RecipeNode m1 = createMachine("Sub1_M1", 20.0, 30.0);
                RecipeNode m2 = createMachine("Sub1_M2", 20.0, 30.0);
                graph.addNode(m1);
                graph.addNode(m2);
                connectMatchingNodes(graph, rng, m1, m2);

                RecipeNode m3 = createMachine("Sub2_M3", 20.0, 30.0);
                RecipeNode m4 = createMachine("Sub2_M4", 20.0, 30.0);
                graph.addNode(m3);
                graph.addNode(m4);
                connectMatchingNodes(graph, rng, m3, m4);
                return graph;
            }
            default -> {
                RecipeNode a = createMachine("Parallel_A", 20.0, 30.0);
                RecipeNode b = createMachine("Parallel_B", 20.0, 30.0);
                IngredientStack m = pickMaterial(rng, 0, 1.0);
                a.addOutput(m.copy());
                b.addInput(m.copy());
                graph.addNode(a);
                graph.addNode(b);
                graph.addConnection(a.getId(), 0, b.getId(), 0);
                graph.addConnection(a.getId(), 0, b.getId(), 0);
                return graph;
            }
        }
    }

    public static String summarizeGraph(FlowGraph graph) {
        if (graph == null) return "FlowGraph[null]";
        StringBuilder sb = new StringBuilder();
        sb.append("FlowGraph[nodes=").append(graph.getNodes().size())
                .append(", connections=").append(graph.getConnections().size()).append("]:\n");

        for (RecipeNode n : graph.getNodes()) {
            sb.append("  - Node[id=").append(n.getId())
                    .append(", name=").append(n.getName())
                    .append(", reroute=").append(n.isReroute())
                    .append(", count=").append(n.getMachineCount())
                    .append(", inputs=").append(n.getInputs().size())
                    .append(", outputs=").append(n.getOutputs().size());
            if (n.isReroute()) {
                sb.append(", supply=").append(n.getSupplyMode());
            }
            sb.append("]\n");
        }

        for (FlowGraph.ConnectionEdge e : graph.getConnections()) {
            sb.append("  * Edge: ").append(e.fromNodeId()).append(":out").append(e.outputIndex())
                    .append(" -> ").append(e.toNodeId()).append(":in").append(e.inputIndex()).append("\n");
        }

        return sb.toString();
    }

    public static RecipeNode findFirstMachine(FlowGraph graph) {
        if (graph == null) return null;
        for (RecipeNode n : graph.getNodes()) {
            if (!n.isReroute()) return n;
        }
        return null;
    }

    private static List<RecipeNode> createMachineNodes(FlowGraph graph, Random rng, int count) {
        List<RecipeNode> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            RecipeNode m = createMachine("Machine_" + i, pickDuration(rng), 30.0);
            graph.addNode(m);
            list.add(m);
        }
        return list;
    }

    private static RecipeNode createMachine(String name, double durationTicks, double baseEUt) {
        RecipeNode node = RecipeNode.create(name, durationTicks, baseEUt, GTVoltageTier.LV);
        node.setId(name);
        return node;
    }

    private static RecipeNode createJunctionNode(int idx, IngredientStack material, SupplyMode mode, double rate) {
        RecipeNode node = RecipeNode.createReroute(idx * 50.0, 100.0);
        node.setId("Reroute_" + idx);
        if (material != null) {
            node.bindRerouteIngredient(material);
        }
        node.setSupplyMode(mode != null ? mode : SupplyMode.NONE);
        if (mode == SupplyMode.FIXED_DRAIN) {
            node.setExternalDrainRate(rate);
        } else if (mode == SupplyMode.FIXED_RATE) {
            node.setExternalSupplyRate(rate);
        }
        return node;
    }

    private static void connectMatchingNodes(FlowGraph graph, Random rng, RecipeNode src, RecipeNode dst) {
        IngredientStack mat = pickMaterial(rng, rng.nextInt(16), pickAmount(rng));
        int outIdx = ensureOutputPort(src, mat);
        int inIdx = ensureInputPort(dst, mat);
        graph.addConnection(src.getId(), outIdx, dst.getId(), inIdx);
    }

    private static void connectAnyNodes(FlowGraph graph, Random rng, RecipeNode src, RecipeNode dst) {
        if (src.isReroute() && dst.isReroute()) {
            connectRerouteToReroute(graph, rng, src, dst);
            return;
        }
        if (src.isReroute()) {
            connectRerouteToMachine(graph, rng, src, dst);
            return;
        }
        if (dst.isReroute()) {
            connectMachineToReroute(graph, rng, src, dst);
            return;
        }
        connectMatchingNodes(graph, rng, src, dst);
    }

    private static void connectRerouteToReroute(FlowGraph graph, Random rng, RecipeNode src, RecipeNode dst) {
        IngredientStack mat = getOrBindRerouteMaterial(src, rng);
        if (dst.getInputs().isEmpty()) {
            dst.bindRerouteIngredient(mat);
        }
        graph.addConnection(src.getId(), 0, dst.getId(), 0);
    }

    private static void connectRerouteToMachine(FlowGraph graph, Random rng, RecipeNode src, RecipeNode dst) {
        IngredientStack mat = getOrBindRerouteMaterial(src, rng);
        int inIdx = ensureInputPort(dst, mat);
        graph.addConnection(src.getId(), 0, dst.getId(), inIdx);
    }

    private static void connectMachineToReroute(FlowGraph graph, Random rng, RecipeNode src, RecipeNode dst) {
        IngredientStack mat = getOrBindRerouteMaterial(dst, rng);
        int outIdx = ensureOutputPort(src, mat);
        graph.addConnection(src.getId(), outIdx, dst.getId(), 0);
    }

    private static IngredientStack getOrBindRerouteMaterial(RecipeNode reroute, Random rng) {
        if (!reroute.getOutputs().isEmpty()) {
            return reroute.getOutputs().get(0);
        }
        IngredientStack mat = pickMaterial(rng, rng.nextInt(16), 1.0);
        reroute.bindRerouteIngredient(mat);
        return mat;
    }

    private static void wireJunctionHub(FlowGraph graph, Random rng, RecipeNode junc, List<RecipeNode> machines) {
        if (machines.isEmpty()) return;
        RecipeNode prod = machines.get(rng.nextInt(machines.size()));
        connectMachineToReroute(graph, rng, prod, junc);

        int consumerCount = 1 + rng.nextInt(Math.min(3, machines.size()));
        for (int c = 0; c < consumerCount; c++) {
            RecipeNode cons = machines.get(rng.nextInt(machines.size()));
            if (cons != prod) {
                connectRerouteToMachine(graph, rng, junc, cons);
            }
        }
    }

    private static int ensureOutputPort(RecipeNode node, IngredientStack stack) {
        for (int i = 0; i < node.getOutputs().size(); i++) {
            if (node.getOutputs().get(i).equals(stack)) {
                return i;
            }
        }
        int idx = node.getOutputs().size();
        node.addOutput(stack.copy());
        return idx;
    }

    private static int ensureInputPort(RecipeNode node, IngredientStack stack) {
        for (int i = 0; i < node.getInputs().size(); i++) {
            if (node.getInputs().get(i).equals(stack)) {
                return i;
            }
        }
        int idx = node.getInputs().size();
        node.addInput(stack.copy());
        return idx;
    }

    private static void ensureRawInput(RecipeNode node, IngredientStack stack) {
        node.addInput(stack.copy());
    }

    private static void ensureProductOutput(RecipeNode node, IngredientStack stack) {
        node.addOutput(stack.copy());
    }

    private static IngredientStack pickMaterial(Random rng, int index, double amount) {
        int idx = Math.abs(index) % 16;
        if (idx < 8) {
            return IngredientStack.item(ResourceLocation.tryParse(ITEM_IDS[idx]), ITEM_NAMES[idx], amount, 1.0);
        }
        return IngredientStack.fluid(ResourceLocation.tryParse(FLUID_IDS[idx - 8]), FLUID_NAMES[idx - 8], amount);
    }

    private static double pickDuration(Random rng) {
        return COMMON_DURATIONS[rng.nextInt(COMMON_DURATIONS.length)];
    }

    private static double pickAmount(Random rng) {
        return COMMON_AMOUNTS[rng.nextInt(COMMON_AMOUNTS.length)];
    }

    private static SupplyMode pickSupplyMode(Random rng) {
        int r = rng.nextInt(4);
        return switch (r) {
            case 1 -> SupplyMode.FIXED_DRAIN;
            case 2 -> SupplyMode.FIXED_RATE;
            default -> SupplyMode.NONE;
        };
    }
}
