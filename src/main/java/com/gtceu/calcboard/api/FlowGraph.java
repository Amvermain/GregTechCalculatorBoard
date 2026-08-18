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
        autoRatioFromAnchor(anchor, true);
    }

    public void autoRatioFromAnchor(RecipeNode anchor, boolean integerCounts) {
        if (anchor == null || nodes.isEmpty()) return;

        double targetAnchorCount = anchor.getMachineCount();
        if (integerCounts) {
            targetAnchorCount = Math.max(1.0, Math.ceil(targetAnchorCount - 0.00001));
        }
        anchor.setMachineCount(targetAnchorCount);

        Map<String, Double> upstreamCounts = new HashMap<>();
        upstreamCounts.put(anchor.getId(), targetAnchorCount);

        // ==========================================
        // 1. UPSTREAM PASS (Supply Anchor & Upstream Inputs with Supply >= Demand)
        // ==========================================
        Queue<RecipeNode> upQueue = new ArrayDeque<>();
        upQueue.add(anchor);
        Set<String> upVisited = new HashSet<>();
        upVisited.add(anchor.getId());

        while (!upQueue.isEmpty()) {
            RecipeNode consumer = upQueue.poll();
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
                            if (integerCounts) {
                                neededCount = Math.max(1.0, Math.ceil(neededCount - 0.00001));
                            }
                            double currentMax = upstreamCounts.getOrDefault(producer.getId(), 0.0);
                            upstreamCounts.put(producer.getId(), Math.max(currentMax, neededCount));

                            if (!upVisited.contains(producer.getId())) {
                                upVisited.add(producer.getId());
                                upQueue.add(producer);
                            }
                        }
                    }
                }
            }
        }

        // Apply upstream calculated counts to graph
        for (Map.Entry<String, Double> entry : upstreamCounts.entrySet()) {
            RecipeNode n = findNodeById(entry.getKey());
            if (n != null) {
                n.setMachineCount(entry.getValue());
            }
        }

        // ==========================================
        // 2. DOWNSTREAM PASS (Consume Anchor & Upstream Outputs with Supply >= Demand)
        // ==========================================
        Map<String, Double> downstreamCounts = new HashMap<>(upstreamCounts);

        Queue<RecipeNode> downQueue = new ArrayDeque<>();
        Set<String> downVisited = new HashSet<>();

        // Start downstream propagation from anchor and all upstream producers
        for (String upId : upstreamCounts.keySet()) {
            RecipeNode n = findNodeById(upId);
            if (n != null) {
                downQueue.add(n);
                downVisited.add(n.getId());
            }
        }

        while (!downQueue.isEmpty()) {
            RecipeNode producer = downQueue.poll();
            double pCount = downstreamCounts.getOrDefault(producer.getId(), producer.getMachineCount());

            // Find next consumers that are downstream of this producer
            Set<RecipeNode> nextConsumers = new LinkedHashSet<>();
            for (ConnectionEdge edge : connections) {
                if (edge.fromNodeId().equals(producer.getId())) {
                    RecipeNode consumer = findNodeById(edge.toNodeId());
                    if (consumer != null && consumer != anchor && !upstreamCounts.containsKey(consumer.getId())) {
                        nextConsumers.add(consumer);
                    }
                }
            }

            for (RecipeNode consumer : nextConsumers) {
                double requiredConsumerCount = 0.0;

                for (int inIdx = 0; inIdx < consumer.getInputs().size(); inIdx++) {
                    IngredientStack inStack = consumer.getInputs().get(inIdx);
                    double singleInRate = consumer.getOverclockResult().getCyclesPerSecond() * consumer.getParallel() * inStack.getAmount();
                    if (singleInRate <= 0.0001) continue;

                    // Sum up all incoming supplies to this input port from all connected producers
                    double totalIncomingSupply = 0.0;
                    for (ConnectionEdge edge : connections) {
                        if (edge.toNodeId().equals(consumer.getId()) && edge.inputIndex() == inIdx) {
                            RecipeNode p = findNodeById(edge.fromNodeId());
                            if (p != null && edge.outputIndex() < p.getOutputs().size()) {
                                double pC = downstreamCounts.getOrDefault(p.getId(), p.getMachineCount());
                                double pCps = p.getOverclockResult().getCyclesPerSecond() * p.getParallel() * pC;
                                IngredientStack outStack = p.getOutputs().get(edge.outputIndex());
                                double pRate = outStack.getExpectedAmount() * pCps;

                                int outDegree = 0;
                                for (ConnectionEdge outEdge : connections) {
                                    if (outEdge.fromNodeId().equals(p.getId()) && outEdge.outputIndex() == edge.outputIndex()) {
                                        outDegree++;
                                    }
                                }
                                totalIncomingSupply += pRate / Math.max(1, outDegree);
                            }
                        }
                    }

                    if (totalIncomingSupply > 0.0001) {
                        double portConsumerCount = totalIncomingSupply / singleInRate;
                        requiredConsumerCount = Math.max(requiredConsumerCount, portConsumerCount);
                    }
                }

                if (requiredConsumerCount > 0.0001) {
                    double finalConsumerCount = requiredConsumerCount;
                    if (integerCounts) {
                        // Floor consumer count so consumer demand never exceeds producer supply (Supply >= Demand)
                        finalConsumerCount = Math.max(1.0, Math.floor(finalConsumerCount + 0.00001));
                    } else {
                        finalConsumerCount = Math.max(0.01, Math.round(finalConsumerCount * 100.0) / 100.0);
                    }
                    downstreamCounts.put(consumer.getId(), finalConsumerCount);

                    if (!downVisited.contains(consumer.getId())) {
                        downVisited.add(consumer.getId());
                        downQueue.add(consumer);
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

        // Enforce integer or 2 decimal places across the entire graph
        for (RecipeNode n : nodes) {
            if (integerCounts) {
                n.setMachineCount(Math.max(1.0, Math.ceil(n.getMachineCount() - 0.00001)));
            } else {
                n.setMachineCount(Math.round(n.getMachineCount() * 100.0) / 100.0);
            }
        }

        // Always enforce anchor machine count
        anchor.setMachineCount(targetAnchorCount);
    }

    public record PortFlowStats(
        double requiredOrProducedRate,
        double connectedRate,
        int connectionCount,
        boolean isConnected
    ) {
        public double getRatio() {
            if (requiredOrProducedRate <= 0.0001) return 1.0;
            return connectedRate / requiredOrProducedRate;
        }

        public double getPercent() {
            return getRatio() * 100.0;
        }

        public boolean isBalanced() {
            return isConnected && Math.abs(connectedRate - requiredOrProducedRate) <= 0.001;
        }

        public boolean isDeficit() {
            return isConnected && connectedRate < requiredOrProducedRate - 0.001;
        }

        public boolean isSurplus() {
            return isConnected && connectedRate > requiredOrProducedRate + 0.001;
        }
    }

    public PortFlowStats getInputPortStats(RecipeNode node, int inputIndex) {
        if (node == null || inputIndex < 0 || inputIndex >= node.getInputs().size()) {
            return new PortFlowStats(0, 0, 0, false);
        }
        IngredientStack in = node.getInputs().get(inputIndex);
        double req = in.getAmount() * node.getCyclesPerSecond();

        double supplied = 0.0;
        int count = 0;
        for (ConnectionEdge edge : connections) {
            if (edge.toNodeId().equals(node.getId()) && edge.inputIndex() == inputIndex) {
                RecipeNode p = findNodeById(edge.fromNodeId());
                if (p != null && edge.outputIndex() < p.getOutputs().size()) {
                    IngredientStack pOut = p.getOutputs().get(edge.outputIndex());
                    double pRate = pOut.getExpectedAmount() * p.getCyclesPerSecond();

                    int outDegree = 0;
                    for (ConnectionEdge outEdge : connections) {
                        if (outEdge.fromNodeId().equals(p.getId()) && outEdge.outputIndex() == edge.outputIndex()) {
                            outDegree++;
                        }
                    }
                    supplied += pRate / Math.max(1, outDegree);
                    count++;
                }
            }
        }
        return new PortFlowStats(req, supplied, count, count > 0);
    }

    public PortFlowStats getOutputPortStats(RecipeNode node, int outputIndex) {
        if (node == null || outputIndex < 0 || outputIndex >= node.getOutputs().size()) {
            return new PortFlowStats(0, 0, 0, false);
        }
        IngredientStack out = node.getOutputs().get(outputIndex);
        double produced = out.getExpectedAmount() * node.getCyclesPerSecond();

        double demanded = 0.0;
        int count = 0;
        for (ConnectionEdge edge : connections) {
            if (edge.fromNodeId().equals(node.getId()) && edge.outputIndex() == outputIndex) {
                RecipeNode c = findNodeById(edge.toNodeId());
                if (c != null && edge.inputIndex() < c.getInputs().size()) {
                    IngredientStack cIn = c.getInputs().get(edge.inputIndex());
                    double cRate = cIn.getAmount() * c.getCyclesPerSecond();

                    int inDegree = 0;
                    for (ConnectionEdge inEdge : connections) {
                        if (inEdge.toNodeId().equals(c.getId()) && inEdge.inputIndex() == edge.inputIndex()) {
                            inDegree++;
                        }
                    }
                    demanded += cRate / Math.max(1, inDegree);
                    count++;
                }
            }
        }
        return new PortFlowStats(produced, demanded, count, count > 0);
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
        double totalConsumedEUt = 0.0;
        double totalGeneratedEUt = 0.0;
        GTVoltageTier highestTier = GTVoltageTier.ULV;

        int totalMachineCount = 0;
        Map<String, Integer> machineBreakdown = new LinkedHashMap<>();

        Map<IngredientStack, Double> totalProduction = new HashMap<>();
        Map<IngredientStack, Double> totalConsumption = new HashMap<>();

        for (RecipeNode node : nodes) {
            if (node.isModule()) {
                // Compound Module: aggregate contained machine counts and breakdown recursively
                int moduleCount = (int) Math.max(1, Math.ceil(node.getMachineCount() - 0.00001));
                if (node.getSubGraph() != null) {
                    BalanceSummary subSummary = node.getSubGraph().computeSummary();
                    int subMachines = subSummary.totalMachineCount() * moduleCount;
                    totalMachineCount += subMachines;
                    for (Map.Entry<String, Integer> entry : subSummary.machineBreakdown().entrySet()) {
                        machineBreakdown.put(entry.getKey(), machineBreakdown.getOrDefault(entry.getKey(), 0) + entry.getValue() * moduleCount);
                    }
                } else {
                    int subMachines = Math.max(1, node.getContainedMachineCount()) * moduleCount;
                    totalMachineCount += subMachines;
                    machineBreakdown.put(node.getName(), machineBreakdown.getOrDefault(node.getName(), 0) + subMachines);
                }
            } else {
                int nodeMachines = (int) Math.max(1, Math.ceil(node.getMachineCount() - 0.00001));
                totalMachineCount += nodeMachines;
                machineBreakdown.put(node.getName(), machineBreakdown.getOrDefault(node.getName(), 0) + nodeMachines);
            }

            if (node.isGenerator()) {
                totalGeneratedEUt += node.getTotalEUt();
            } else {
                totalConsumedEUt += node.getTotalEUt();
            }
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

        double netEUt = totalConsumedEUt - totalGeneratedEUt;
        return new BalanceSummary(netEUt, highestTier, totalMachineCount, machineBreakdown, rawInputs, netOutputs, balanced, totalProduction, totalConsumption);
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

    public RecipeNode groupIntoModule(String moduleName) {
        return groupIntoModule(null, moduleName);
    }

    public RecipeNode groupIntoModule(Set<String> targetNodeIds, String moduleName) {
        List<RecipeNode> selectedNodes = new ArrayList<>();
        if (targetNodeIds != null && !targetNodeIds.isEmpty()) {
            for (RecipeNode n : nodes) {
                if (targetNodeIds.contains(n.getId())) {
                    selectedNodes.add(n);
                }
            }
        } else {
            selectedNodes.addAll(nodes);
        }

        if (selectedNodes.isEmpty()) return null;

        Set<String> selectedIdSet = new HashSet<>();
        for (RecipeNode n : selectedNodes) selectedIdSet.add(n.getId());

        // 1. Create subGraph with selected nodes and their internal connections
        FlowGraph subGraph = new FlowGraph();
        for (RecipeNode n : selectedNodes) {
            subGraph.nodes.add(n);
        }
        for (ConnectionEdge edge : connections) {
            if (selectedIdSet.contains(edge.fromNodeId()) && selectedIdSet.contains(edge.toNodeId())) {
                subGraph.connections.add(edge);
            }
        }

        // 2. Calculate balance summary for subGraph
        BalanceSummary summary = subGraph.computeSummary();

        // 3. Compute centroid position
        double sumX = 0, sumY = 0;
        for (RecipeNode n : selectedNodes) {
            sumX += n.getPosX();
            sumY += n.getPosY();
        }
        double centerX = sumX / selectedNodes.size();
        double centerY = sumY / selectedNodes.size();

        // 4. Create Module RecipeNode
        String name = (moduleName != null && !moduleName.trim().isEmpty()) ? moduleName.trim() : "Compound Module";
        double baseEUt = Math.max(1.0, Math.abs(summary.totalEUt()));
        boolean isGen = summary.totalEUt() < -0.001;
        GTVoltageTier tier = summary.highestVoltageTier();

        // Standard 1.0 second duration (20 ticks) base for modules
        RecipeNode moduleNode = RecipeNode.create(name, 20.0, baseEUt, tier);
        moduleNode.setModule(true);
        moduleNode.setSubGraph(subGraph);
        moduleNode.setContainedMachineCount(summary.totalMachineCount());
        moduleNode.setGenerator(isGen);
        moduleNode.setPos(centerX, centerY);
        moduleNode.setCardWidth(230); // Default wider card for compound modules

        // Add net external inputs
        for (Map.Entry<IngredientStack, Double> entry : summary.rawInputs().entrySet()) {
            IngredientStack original = entry.getKey();
            double ratePerSec = entry.getValue();
            IngredientStack netIn = original.isFluid()
                ? IngredientStack.fluid(original.getId(), original.getDisplayName(), ratePerSec, 1.0)
                : IngredientStack.item(original.getId(), original.getDisplayName(), ratePerSec, 1.0);
            moduleNode.addInput(netIn);
        }

        // Add net external outputs
        for (Map.Entry<IngredientStack, Double> entry : summary.netOutputs().entrySet()) {
            IngredientStack original = entry.getKey();
            double ratePerSec = entry.getValue();
            IngredientStack netOut = original.isFluid()
                ? IngredientStack.fluid(original.getId(), original.getDisplayName(), ratePerSec, 1.0)
                : IngredientStack.item(original.getId(), original.getDisplayName(), ratePerSec, 1.0);
            moduleNode.addOutput(netOut);
        }

        // 5. External connections rewiring
        List<ConnectionEdge> externalEdges = new ArrayList<>();
        for (ConnectionEdge edge : connections) {
            boolean fromSelected = selectedIdSet.contains(edge.fromNodeId());
            boolean toSelected = selectedIdSet.contains(edge.toNodeId());

            if (fromSelected && !toSelected) {
                // Outgoing wire from module to outside
                RecipeNode origFrom = findNodeById(edge.fromNodeId());
                if (origFrom != null && edge.outputIndex() < origFrom.getOutputs().size()) {
                    IngredientStack outStack = origFrom.getOutputs().get(edge.outputIndex());
                    // Find matching output port on moduleNode
                    for (int mOutIdx = 0; mOutIdx < moduleNode.getOutputs().size(); mOutIdx++) {
                        if (moduleNode.getOutputs().get(mOutIdx).equals(outStack)) {
                            externalEdges.add(new ConnectionEdge(moduleNode.getId(), mOutIdx, edge.toNodeId(), edge.inputIndex()));
                            break;
                        }
                    }
                }
            } else if (!fromSelected && toSelected) {
                // Incoming wire from outside into module
                RecipeNode origTo = findNodeById(edge.toNodeId());
                if (origTo != null && edge.inputIndex() < origTo.getInputs().size()) {
                    IngredientStack inStack = origTo.getInputs().get(edge.inputIndex());
                    // Find matching input port on moduleNode
                    for (int mInIdx = 0; mInIdx < moduleNode.getInputs().size(); mInIdx++) {
                        if (moduleNode.getInputs().get(mInIdx).equals(inStack)) {
                            externalEdges.add(new ConnectionEdge(edge.fromNodeId(), edge.outputIndex(), moduleNode.getId(), mInIdx));
                            break;
                        }
                    }
                }
            } else if (!fromSelected && !toSelected) {
                // Unrelated wire outside
                externalEdges.add(edge);
            }
        }

        // 6. Update this graph
        this.nodes.removeAll(selectedNodes);
        this.nodes.add(moduleNode);
        this.connections.clear();
        this.connections.addAll(externalEdges);

        return moduleNode;
    }

    public boolean expandModule(RecipeNode moduleNode) {
        if (moduleNode == null || !moduleNode.isModule() || moduleNode.getSubGraph() == null) {
            return false;
        }

        FlowGraph subGraph = moduleNode.getSubGraph();
        if (subGraph.getNodes().isEmpty()) return false;

        // Calculate centroid of subGraph to apply relative positioning offset
        double sumX = 0, sumY = 0;
        for (RecipeNode n : subGraph.getNodes()) {
            sumX += n.getPosX();
            sumY += n.getPosY();
        }
        double origCenterX = sumX / subGraph.getNodes().size();
        double origCenterY = sumY / subGraph.getNodes().size();

        double offsetX = moduleNode.getPosX() - origCenterX;
        double offsetY = moduleNode.getPosY() - origCenterY;
        double moduleScale = moduleNode.getMachineCount();

        // Remove moduleNode
        this.nodes.remove(moduleNode);
        this.connections.removeIf(e -> e.fromNodeId().equals(moduleNode.getId()) || e.toNodeId().equals(moduleNode.getId()));

        // Restore subGraph nodes
        for (RecipeNode n : subGraph.getNodes()) {
            n.setPos(n.getPosX() + offsetX, n.getPosY() + offsetY);
            if (moduleScale > 1.0) {
                n.setMachineCount(n.getMachineCount() * moduleScale);
            }
            this.nodes.add(n);
        }

        // Restore subGraph connections
        this.connections.addAll(subGraph.getConnections());
        return true;
    }

    public void optimizeMaxThroughput(boolean preferParallels, boolean integerCounts) {
        RecipeNode anchor = findBaseNode();
        if (anchor == null && !nodes.isEmpty()) {
            anchor = nodes.get(0);
        }
        if (anchor == null) return;

        // 1. Overclock nodes up to MAX
        for (RecipeNode n : nodes) {
            GTVoltageTier baseTier = n.getRecipeTier();
            GTVoltageTier targetTier = GTVoltageTier.MAX;
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
