package com.gtceu.calcboard.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

public class FlowGraph {
    private final List<RecipeNode> nodes = new ArrayList<>();
    private final List<ConnectionEdge> connections = new ArrayList<>();

    public record ConnectionEdge(
        String fromNodeId,
        int outputIndex,
        String toNodeId,
        int inputIndex
    ) {
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("fromNode", fromNodeId);
            tag.putInt("outIdx", outputIndex);
            tag.putString("toNode", toNodeId);
            tag.putInt("inIdx", inputIndex);
            return tag;
        }

        public static ConnectionEdge deserializeNBT(CompoundTag tag) {
            return new ConnectionEdge(
                tag.getString("fromNode"),
                tag.getInt("outIdx"),
                tag.getString("toNode"),
                tag.getInt("inIdx")
            );
        }
    }

    public List<RecipeNode> getNodes() {
        return nodes;
    }

    public List<ConnectionEdge> getConnections() {
        return connections;
    }

    public void addNode(RecipeNode node) {
        nodes.add(node);
    }

    public void removeNode(RecipeNode node) {
        nodes.remove(node);
        connections.removeIf(edge -> edge.fromNodeId.equals(node.getId()) || edge.toNodeId.equals(node.getId()));
    }

    public RecipeNode findBaseNode() {
        for (RecipeNode n : nodes) {
            if (n.isBaseNode()) return n;
        }
        return null;
    }

    public void setBaseNode(RecipeNode target) {
        for (RecipeNode n : nodes) {
            n.setBaseNode(n == target);
        }
    }

    public void autoRatioFromAnchor(RecipeNode anchor) {
        if (anchor == null || nodes.isEmpty()) return;

        double targetAnchorCount = anchor.getMachineCount();

        // 1. Upstream Wavefront Propagation (Supply Anchor's Inputs)
        Map<String, Double> upstreamCounts = new HashMap<>();
        upstreamCounts.put(anchor.getId(), targetAnchorCount);

        Queue<RecipeNode> upstreamQueue = new ArrayDeque<>();
        upstreamQueue.add(anchor);

        Set<String> upstreamVisited = new HashSet<>();
        upstreamVisited.add(anchor.getId());

        while (!upstreamQueue.isEmpty()) {
            RecipeNode consumer = upstreamQueue.poll();
            double consumerCount = upstreamCounts.getOrDefault(consumer.getId(), consumer.getMachineCount());
            double consumerCps = consumer.getOverclockResult().getCyclesPerSecond() * consumer.getParallel() * consumerCount;

            for (int inIdx = 0; inIdx < consumer.getInputs().size(); inIdx++) {
                IngredientStack inStack = consumer.getInputs().get(inIdx);
                double neededRate = inStack.getAmount() * consumerCps;

                List<ConnectionEdge> inEdges = new ArrayList<>();
                for (ConnectionEdge edge : connections) {
                    if (edge.toNodeId().equals(consumer.getId()) && edge.inputIndex() == inIdx) {
                        inEdges.add(edge);
                    }
                }

                if (inEdges.isEmpty()) continue;
                double sharePerProducer = neededRate / inEdges.size();

                for (ConnectionEdge edge : inEdges) {
                    RecipeNode producer = findNodeById(edge.fromNodeId());
                    if (producer == null || producer == anchor) continue;

                    if (edge.outputIndex() < producer.getOutputs().size()) {
                        IngredientStack outStack = producer.getOutputs().get(edge.outputIndex());
                        double singleRate = producer.calculateSingleMachineOutputRate(outStack);

                        if (singleRate > 0.0001) {
                            double neededCount = sharePerProducer / singleRate;
                            double currentMax = upstreamCounts.getOrDefault(producer.getId(), 0.0);
                            // Take max demand across multiple outputs
                            upstreamCounts.put(producer.getId(), Math.max(currentMax, neededCount));

                            if (!upstreamVisited.contains(producer.getId())) {
                                upstreamVisited.add(producer.getId());
                                upstreamQueue.add(producer);
                            }
                        }
                    }
                }
            }
        }

        // Apply upstream calculated counts
        for (Map.Entry<String, Double> entry : upstreamCounts.entrySet()) {
            RecipeNode n = findNodeById(entry.getKey());
            if (n != null) {
                n.setMachineCount(entry.getValue());
            }
        }

        // 2. Downstream Wavefront Propagation (Consume Anchor's Outputs)
        Map<String, Double> downstreamCounts = new HashMap<>();
        downstreamCounts.put(anchor.getId(), targetAnchorCount);

        Queue<RecipeNode> downstreamQueue = new ArrayDeque<>();
        downstreamQueue.add(anchor);

        Set<String> downstreamVisited = new HashSet<>();
        downstreamVisited.add(anchor.getId());

        while (!downstreamQueue.isEmpty()) {
            RecipeNode producer = downstreamQueue.poll();
            double prodCount = downstreamCounts.getOrDefault(producer.getId(), producer.getMachineCount());
            double prodCps = producer.getOverclockResult().getCyclesPerSecond() * producer.getParallel() * prodCount;

            for (int outIdx = 0; outIdx < producer.getOutputs().size(); outIdx++) {
                IngredientStack outStack = producer.getOutputs().get(outIdx);
                double producedRate = outStack.getExpectedAmount() * prodCps;

                List<ConnectionEdge> outEdges = new ArrayList<>();
                for (ConnectionEdge edge : connections) {
                    if (edge.fromNodeId().equals(producer.getId()) && edge.outputIndex() == outIdx) {
                        outEdges.add(edge);
                    }
                }

                if (outEdges.isEmpty()) continue;
                double sharePerConsumer = producedRate / outEdges.size();

                for (ConnectionEdge edge : outEdges) {
                    RecipeNode consumer = findNodeById(edge.toNodeId());
                    if (consumer == null || consumer == anchor || upstreamVisited.contains(consumer.getId())) continue;

                    if (edge.inputIndex() < consumer.getInputs().size()) {
                        IngredientStack inStack = consumer.getInputs().get(edge.inputIndex());
                        double singleInRate = consumer.getOverclockResult().getCyclesPerSecond() * consumer.getParallel() * inStack.getAmount();

                        if (singleInRate > 0.0001) {
                            double neededCount = sharePerConsumer / singleInRate;
                            double currentMax = downstreamCounts.getOrDefault(consumer.getId(), 0.0);
                            downstreamCounts.put(consumer.getId(), Math.max(currentMax, neededCount));

                            if (!downstreamVisited.contains(consumer.getId())) {
                                downstreamVisited.add(consumer.getId());
                                downstreamQueue.add(consumer);
                            }
                        }
                    }
                }
            }
        }

        // Apply downstream calculated counts
        for (Map.Entry<String, Double> entry : downstreamCounts.entrySet()) {
            RecipeNode n = findNodeById(entry.getKey());
            if (n != null) {
                n.setMachineCount(entry.getValue());
            }
        }

        // Always enforce anchor machine count
        anchor.setMachineCount(targetAnchorCount);

        // Round counts to 2 decimal places
        for (RecipeNode n : nodes) {
            n.setMachineCount(Math.round(n.getMachineCount() * 100.0) / 100.0);
        }
    }

    public RecipeNode findNodeById(String id) {
        for (RecipeNode n : nodes) {
            if (n.getId().equals(id)) return n;
        }
        return null;
    }

    public void clear() {
        nodes.clear();
        connections.clear();
    }

    public void addConnection(String fromNodeId, int outIdx, String toNodeId, int inIdx) {
        // Avoid duplicate connections
        for (ConnectionEdge edge : connections) {
            if (edge.fromNodeId.equals(fromNodeId) && edge.outputIndex == outIdx
                && edge.toNodeId.equals(toNodeId) && edge.inputIndex == inIdx) {
                return;
            }
        }
        connections.add(new ConnectionEdge(fromNodeId, outIdx, toNodeId, inIdx));
    }

    public void removeConnection(ConnectionEdge edge) {
        connections.remove(edge);
    }

    /**
     * Solves the overall graph and computes total EU/t, raw ingredients, net outputs, and byproducts.
     */
    public BalanceSummary computeSummary() {
        double totalEUt = 0.0;
        GTVoltageTier highestTier = GTVoltageTier.ULV;

        Map<IngredientStack, Double> totalProduction = new HashMap<>();
        Map<IngredientStack, Double> totalConsumption = new HashMap<>();

        for (RecipeNode node : nodes) {
            totalEUt += node.getTotalEUt();
            if (node.getTargetTier().ordinal() > highestTier.ordinal()) {
                highestTier = node.getTargetTier();
            }

            // Aggregate outputs
            Map<IngredientStack, Double> outRates = node.calculateOutputRates();
            for (Map.Entry<IngredientStack, Double> entry : outRates.entrySet()) {
                mergeRate(totalProduction, entry.getKey(), entry.getValue());
            }

            // Aggregate inputs
            Map<IngredientStack, Double> inRates = node.calculateInputRates();
            for (Map.Entry<IngredientStack, Double> entry : inRates.entrySet()) {
                mergeRate(totalConsumption, entry.getKey(), entry.getValue());
            }
        }

        // Compute net balance (Produced - Consumed)
        Map<IngredientStack, Double> rawInputs = new LinkedHashMap<>();
        Map<IngredientStack, Double> netOutputs = new LinkedHashMap<>();
        Map<IngredientStack, Double> balanced = new LinkedHashMap<>();

        Set<IngredientStack> allStacks = new HashSet<>();
        allStacks.addAll(totalProduction.keySet());
        allStacks.addAll(totalConsumption.keySet());

        for (IngredientStack stack : allStacks) {
            double produced = findRate(totalProduction, stack);
            double consumed = findRate(totalConsumption, stack);
            double delta = produced - consumed;

            if (Math.abs(delta) < 0.0001) {
                balanced.put(stack, produced);
            } else if (delta > 0) {
                netOutputs.put(stack, delta);
            } else {
                rawInputs.put(stack, -delta);
            }
        }

        return new BalanceSummary(totalEUt, highestTier, rawInputs, netOutputs, balanced, totalProduction, totalConsumption);
    }

    private void mergeRate(Map<IngredientStack, Double> map, IngredientStack stack, double rate) {
        for (Map.Entry<IngredientStack, Double> entry : map.entrySet()) {
            if (entry.getKey().equals(stack)) {
                entry.setValue(entry.getValue() + rate);
                return;
            }
        }
        map.put(stack, rate);
    }

    private double findRate(Map<IngredientStack, Double> map, IngredientStack stack) {
        for (Map.Entry<IngredientStack, Double> entry : map.entrySet()) {
            if (entry.getKey().equals(stack)) {
                return entry.getValue();
            }
        }
        return 0.0;
    }

    private GTVoltageTier maxTierCap = null; // null = ALL / Unlimited

    public GTVoltageTier getMaxTierCap() {
        return maxTierCap;
    }

    public void setMaxTierCap(GTVoltageTier maxTierCap) {
        this.maxTierCap = maxTierCap;
    }

    public void optimizeMaxThroughput(boolean preferParallels, boolean integerCounts) {
        RecipeNode anchor = findBaseNode();
        if (anchor == null && !nodes.isEmpty()) {
            anchor = nodes.get(0);
        }
        if (anchor == null) return;

        // 1. Overclock nodes up to maxTierCap
        for (RecipeNode n : nodes) {
            GTVoltageTier baseTier = n.getRecipeTier();
            GTVoltageTier targetTier = (maxTierCap != null) ? maxTierCap : GTVoltageTier.MAX;
            if (targetTier.ordinal() < baseTier.ordinal()) {
                targetTier = baseTier;
            }
            n.setTargetTier(targetTier);
        }

        // 2. Propagate auto ratio from anchor
        autoRatioFromAnchor(anchor);

        // 3. Balance machine count and parallels
        if (preferParallels || integerCounts) {
            for (RecipeNode n : nodes) {
                if (n == anchor && anchor.isBaseNode()) continue;

                double count = n.getMachineCount();
                if (preferParallels && count > 1.0) {
                    // Try to consolidate multiple machines into parallel multiplier
                    // e.g. 4.0 machines -> 1 machine with 4x parallel
                    int[] standardParallels = {1, 2, 4, 8, 16, 64, 128, 256};
                    int bestP = 1;
                    for (int p : standardParallels) {
                        if (p <= Math.ceil(count)) {
                            bestP = p;
                        }
                    }
                    if (bestP > 1) {
                        n.setParallel(bestP);
                        count = count / bestP;
                        n.setMachineCount(Math.round(count * 100.0) / 100.0);
                    }
                }

                if (integerCounts) {
                    n.setMachineCount(Math.max(1.0, Math.ceil(n.getMachineCount())));
                }
            }
        }
    }

    public void copyFrom(FlowGraph other) {
        this.nodes.clear();
        this.connections.clear();
        if (other != null) {
            this.nodes.addAll(other.nodes);
            this.connections.addAll(other.connections);
            this.maxTierCap = other.maxTierCap;
        }
    }

    public CompoundTag serializeNBT() {
        return serializeNBT(0, 0, 1.0);
    }

    public CompoundTag serializeNBT(double panX, double panY, double zoom) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("panX", panX);
        tag.putDouble("panY", panY);
        tag.putDouble("zoom", zoom);
        if (maxTierCap != null) {
            tag.putString("maxTierCap", maxTierCap.name());
        }

        ListTag nodeList = new ListTag();
        for (RecipeNode n : nodes) {
            nodeList.add(n.serializeNBT());
        }
        tag.put("nodes", nodeList);

        ListTag edgeList = new ListTag();
        for (ConnectionEdge edge : connections) {
            edgeList.add(edge.serializeNBT());
        }
        tag.put("connections", edgeList);
        return tag;
    }

    public static FlowGraph deserializeNBT(CompoundTag tag) {
        FlowGraph graph = new FlowGraph();
        if (tag.contains("maxTierCap")) {
            try {
                graph.maxTierCap = GTVoltageTier.valueOf(tag.getString("maxTierCap"));
            } catch (Exception ignored) {}
        }
        if (tag.contains("nodes", Tag.TAG_LIST)) {
            ListTag nodeList = tag.getList("nodes", Tag.TAG_COMPOUND);
            for (int i = 0; i < nodeList.size(); i++) {
                graph.nodes.add(RecipeNode.deserializeNBT(nodeList.getCompound(i)));
            }
        }
        if (tag.contains("connections", Tag.TAG_LIST)) {
            ListTag edgeList = tag.getList("connections", Tag.TAG_COMPOUND);
            for (int i = 0; i < edgeList.size(); i++) {
                graph.connections.add(ConnectionEdge.deserializeNBT(edgeList.getCompound(i)));
            }
        }
        return graph;
    }
}
