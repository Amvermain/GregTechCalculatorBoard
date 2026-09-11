package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.event.FlowGraphEvent;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraftforge.common.MinecraftForge;

import java.util.*;

/**
 * Optimizes graph throughput, calculates single/parallel match ratios,
 * and harmonizes integer machine counts and shared machine pool capacity scaling.
 */
public final class HarmonizedRatioOptimizer {

    private HarmonizedRatioOptimizer() {}

    public static void optimizeMaxThroughput(FlowGraph graph, boolean preferParallels, boolean integerCounts) {
        if (graph == null) return;
        RecipeNode anchor = graph.findBaseNode();
        if (anchor == null && !graph.getNodes().isEmpty()) {
            anchor = graph.getNodes().get(0);
        }
        if (anchor == null) return;

        for (RecipeNode n : graph.getNodes()) {
            GTVoltageTier baseTier = n.getRecipeTier();
            GTVoltageTier targetTier = GTVoltageTier.MAX;
            if (targetTier.ordinal() < baseTier.ordinal()) {
                targetTier = baseTier;
            }
            n.setTargetTier(targetTier);
        }

        AutoRatioEngine.autoRatioFromAnchor(graph, anchor, integerCounts);

        if (preferParallels || integerCounts) {
            for (RecipeNode n : graph.getNodes()) {
                if (n == anchor && anchor.isBaseNode()) continue;
                applyParallelOptimization(graph, n, preferParallels, integerCounts);
            }
        }
    }

    private static void applyParallelOptimization(FlowGraph graph, RecipeNode n, boolean preferParallels, boolean integerCounts) {
        double count = n.getMachineCount();
        if (preferParallels && count > 1.0) {
            int bestP = findBestParallel(count);
            if (bestP > 1) {
                n.setParallel(bestP);
                count = count / bestP;
                n.setMachineCount(Math.round(count * 100.0) / 100.0);
            }
        }
        n.setMachineCount(AutoRatioEngine.quantizeMachineCount(graph, n, n.getMachineCount(), FlowBalanceMatrixSolver.CountRoundingMode.CEIL, integerCounts));
    }

    private static int findBestParallel(double count) {
        int[] standardParallels = {1, 2, 4, 8, 16, 64, 128, 256};
        int bestP = 1;
        for (int p : standardParallels) {
            if (p <= Math.ceil(count)) {
                bestP = p;
            }
        }
        return bestP;
    }

    public static double calculateConsumerMatchCount(FlowGraph graph, RecipeNode producer, int outPortIdx, RecipeNode consumer, int inPortIdx) {
        if (graph == null || producer == null || consumer == null) return 1.0;
        if (consumer.isReroute() || consumer.isBoundaryPin()) return 1.0;
        if (outPortIdx >= producer.getOutputs().size() || inPortIdx >= consumer.getInputs().size()) return 1.0;

        double producedRate;
        if (producer.isReroute()) {
            producedRate = FlowEdgeAllocator.getEffectiveProducerOutputRate(graph, producer, outPortIdx, null);
        } else {
            IngredientStack outStack = producer.getOutputs().get(outPortIdx);
            double prodEff = producer.getEfficiency();
            double effFactor = (prodEff > 0.00001) ? prodEff : 1.0;
            producedRate = producer.calculateSingleMachineOutputRate(outStack) * producer.getMachineCount() * effFactor;
        }

        IngredientStack inStack = consumer.getInputs().get(inPortIdx);
        double singleInRate = consumer.calculateSingleMachineInputRate(inStack);
        if (singleInRate <= 0.0001) return 1.0;

        double existingSupply = calculateAlternateIncomingSupply(graph, consumer, inPortIdx, producer.getId());
        double totalAvailableSupply = producedRate + existingSupply;
        boolean isShared = (graph != null && graph.isNodeInSharedMachineFrame(consumer));
        return AutoRatioEngine.quantizeMachineCount(graph, consumer, totalAvailableSupply / singleInRate, FlowBalanceMatrixSolver.CountRoundingMode.FLOOR, !isShared);
    }

    public static double calculateProducerMatchCount(FlowGraph graph, RecipeNode producer, int outPortIdx, RecipeNode consumer, int inPortIdx) {
        if (graph == null || producer == null || consumer == null) return 1.0;
        if (producer.isReroute() || producer.isBoundaryPin()) return 1.0;
        if (outPortIdx >= producer.getOutputs().size() || inPortIdx >= consumer.getInputs().size()) return 1.0;

        double totalDemand;
        if (consumer.isReroute()) {
            totalDemand = FlowEdgeAllocator.getConnectedConsumerDemand(graph, consumer, inPortIdx);
        } else {
            IngredientStack inStack = consumer.getInputs().get(inPortIdx);
            totalDemand = consumer.calculateSingleMachineInputRate(inStack) * consumer.getMachineCount();
        }

        double existingSupply = calculateAlternateIncomingSupply(graph, consumer, inPortIdx, producer.getId());
        double remainingDemand = Math.max(0.0, totalDemand - existingSupply);
        IngredientStack outStack = producer.getOutputs().get(outPortIdx);
        double prodEff = (producer.getEfficiency() > 0.00001) ? producer.getEfficiency() : 1.0;
        double singleOutRate = producer.calculateSingleMachineOutputRate(outStack) * prodEff;
        if (singleOutRate <= 0.0001) return 1.0;

        boolean isShared = (graph != null && graph.isNodeInSharedMachineFrame(producer));
        return AutoRatioEngine.quantizeMachineCount(graph, producer, remainingDemand / singleOutRate, FlowBalanceMatrixSolver.CountRoundingMode.CEIL, !isShared);
    }

    private static double calculateAlternateIncomingSupply(FlowGraph graph, RecipeNode consumer, int inPortIdx, String excludedProducerId) {
        double existingSupply = 0.0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (!edge.toNodeId().equals(consumer.getId()) || edge.inputIndex() != inPortIdx) continue;
            if (edge.fromNodeId().equals(excludedProducerId)) continue;

            RecipeNode otherProd = graph.findNodeById(edge.fromNodeId());
            if (otherProd == null || edge.outputIndex() >= otherProd.getOutputs().size()) continue;

            double pRate = FlowEdgeAllocator.getEffectiveProducerOutputRate(graph, otherProd, edge.outputIndex(), null);
            int outDegree = countPortOutDegree(graph, otherProd.getId(), edge.outputIndex());
            existingSupply += pRate / Math.max(1, outDegree);
        }
        return existingSupply;
    }

    private static int countPortOutDegree(FlowGraph graph, String producerId, int outputIndex) {
        int outDegree = 0;
        for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
            if (outEdge.fromNodeId().equals(producerId) && outEdge.outputIndex() == outputIndex) {
                outDegree++;
            }
        }
        return outDegree;
    }

    public static double findPerfectHarmonizedAnchorCount(FlowGraph graph, RecipeNode anchor) {
        if (graph == null || anchor == null || graph.getNodes().isEmpty()) return 1.0;

        Map<String, Double> originalCounts = new HashMap<>();
        for (RecipeNode n : graph.getNodes()) {
            originalCounts.put(n.getId(), n.getMachineCount());
        }

        anchor.setMachineCount(1.0);
        AutoRatioEngine.autoRatioFromAnchor(graph, anchor, false);

        Map<String, Double> baseRatios = new HashMap<>();
        for (RecipeNode n : graph.getNodes()) {
            if (!n.isReroute()) {
                baseRatios.put(n.getId(), n.getMachineCount());
            }
        }

        for (Map.Entry<String, Double> e : originalCounts.entrySet()) {
            RecipeNode n = graph.findNodeById(e.getKey());
            if (n != null) n.setMachineCount(e.getValue());
        }

        int configuredMaxScale = 16;
        double configuredTolerance = 0.02;
        try {
            configuredMaxScale = BoardManager.getInstance().getMaxHarmonizeScale();
            configuredTolerance = BoardManager.getInstance().getHarmonizeSurplusTolerance();
        } catch (Throwable ignored) {}

        int maxScale = Math.max(1, configuredMaxScale);
        double tolerance = Math.max(0.0, configuredTolerance);

        for (int scale = 1; scale <= maxScale; scale++) {
            if (isScaleHarmonizedMatch(baseRatios.values(), scale, tolerance)) {
                return (double) scale;
            }
        }

        int bestScale = 1;
        double bestScore = Double.MAX_VALUE;

        for (int scale = 1; scale <= maxScale; scale++) {
            double score = computeScaleScore(baseRatios.values(), scale, tolerance);
            if (score < bestScore) {
                bestScore = score;
                bestScale = scale;
            }
        }

        return (double) bestScale;
    }

    private static boolean isScaleHarmonizedMatch(Collection<Double> baseRatios, int scale, double tolerance) {
        for (double r : baseRatios) {
            if (r <= 1e-4) continue;
            double scaled = r * scale;
            if (!isScaledValueTolerable(scaled, tolerance)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isScaledValueTolerable(double scaled, double tolerance) {
        if (tolerance <= 1e-5) {
            return Math.abs(scaled - Math.round(scaled)) <= 0.005;
        }
        if (scaled < 0.8) return true;
        double nearestInt = Math.max(1.0, Math.round(scaled));
        return Math.abs(scaled - nearestInt) / scaled <= tolerance;
    }

    private static double computeScaleScore(Collection<Double> baseRatios, int scale, double tolerance) {
        double totalError = 0.0;
        double maxMajorError = 0.0;
        double totalMachines = 0.0;

        for (double r : baseRatios) {
            if (r <= 1e-4) continue;
            double scaled = r * scale;
            double nearestInt = Math.max(1.0, Math.round(scaled));
            double err = Math.abs(scaled - nearestInt) / Math.max(1.0, scaled);
            if (scaled >= 0.8) {
                maxMajorError = Math.max(maxMajorError, err);
            }
            totalError += err;
            totalMachines += nearestInt;
        }

        double tolerancePenalty = (maxMajorError <= tolerance) ? 0.0 : (maxMajorError - tolerance) * 100.0;
        return tolerancePenalty + (totalError * 10.0) + (totalMachines * 0.1);
    }

    public static AutoRatioResult autoRatioHarmonized(FlowGraph graph, RecipeNode anchor) {
        if (graph == null || anchor == null) {
            return new AutoRatioResult(0, Collections.emptySet(), false);
        }
        double harmonizedAnchorCount = findPerfectHarmonizedAnchorCount(graph, anchor);
        anchor.setMachineCount(harmonizedAnchorCount);
        return AutoRatioEngine.autoRatioFromAnchor(graph, anchor, true);
    }

    public static int autoRatioFromSharedPool(FlowGraph graph, CanvasGroupFrame poolFrame, double targetMachines, AutoRatioMode mode) {
        if (graph == null || poolFrame == null || graph.getNodes().isEmpty()) return 0;
        if (!poolFrame.isSharedMachineFrame()) return 0;

        List<RecipeNode> poolNodes = getOperationalPoolNodes(graph, poolFrame);
        if (poolNodes.isEmpty()) return 0;

        double currentDuty = computePoolDuty(poolNodes);
        if (currentDuty <= 0.00001) return 0;

        double target = Math.max(0.01, targetMachines);
        double scale = target / currentDuty;

        List<String> poolNodeIds = poolNodes.stream().map(RecipeNode::getId).toList();
        Set<String> connectedIds = FlowGraphTopologyAnalyzer.findConnectedComponent(graph, poolNodeIds);
        if (connectedIds.isEmpty()) return 0;

        AutoRatioMode effectiveMode = mode != null ? mode : AutoRatioMode.INTEGER_CEIL;
        int changedCount = applyPoolScaling(graph, poolFrame, connectedIds, scale, effectiveMode);

        if (changedCount > 0) {
            postSolveEvent(graph);
        }
        return changedCount;
    }

    private static List<RecipeNode> getOperationalPoolNodes(FlowGraph graph, CanvasGroupFrame poolFrame) {
        List<RecipeNode> enclosed = poolFrame.getEnclosedNodes(graph);
        List<RecipeNode> operational = new ArrayList<>();
        for (RecipeNode n : enclosed) {
            if (n != null && !n.isReroute() && n.isOperational(graph)) {
                operational.add(n);
            }
        }
        return operational;
    }

    private static double computePoolDuty(List<RecipeNode> poolNodes) {
        double duty = 0.0;
        for (RecipeNode n : poolNodes) {
            duty += n.getMachineCount();
        }
        return duty;
    }

    private static int applyPoolScaling(FlowGraph graph, CanvasGroupFrame poolFrame, Set<String> connectedIds, double scale, AutoRatioMode mode) {
        return switch (mode) {
            case FRACTIONAL -> applyFractionalPoolScaling(graph, connectedIds, scale);
            case INTEGER_CEIL -> applyIntegerCeilPoolScaling(graph, poolFrame, connectedIds, scale);
            case HARMONIZED -> applyHarmonizedPoolScaling(graph, poolFrame, connectedIds, scale);
        };
    }

    private static int applyFractionalPoolScaling(FlowGraph graph, Set<String> connectedIds, double scale) {
        int changed = 0;
        for (String id : connectedIds) {
            RecipeNode n = graph.findNodeById(id);
            if (n != null && !n.isReroute() && updateNodeCount(n, roundFractional(n.getMachineCount() * scale))) {
                changed++;
            }
        }
        return changed;
    }

    private static int applyIntegerCeilPoolScaling(FlowGraph graph, CanvasGroupFrame poolFrame, Set<String> connectedIds, double scale) {
        int changed = 0;
        for (String id : connectedIds) {
            RecipeNode n = graph.findNodeById(id);
            if (n == null || n.isReroute()) continue;

            double targetCount = isNodeInPool(poolFrame, n)
                    ? roundFractional(n.getMachineCount() * scale)
                    : Math.max(1.0, Math.ceil(n.getMachineCount() * scale - 0.00001));

            if (updateNodeCount(n, targetCount)) {
                changed++;
            }
        }
        return changed;
    }

    private static int applyHarmonizedPoolScaling(FlowGraph graph, CanvasGroupFrame poolFrame, Set<String> connectedIds, double baseScale) {
        int multiplier = findBestHarmonizedMultiplier(graph, poolFrame, connectedIds, baseScale);
        double effectiveScale = baseScale * multiplier;

        int changed = 0;
        for (String id : connectedIds) {
            RecipeNode n = graph.findNodeById(id);
            if (n == null || n.isReroute()) continue;

            double targetCount = isNodeInPool(poolFrame, n)
                    ? roundFractional(n.getMachineCount() * effectiveScale)
                    : Math.max(1.0, (double) Math.round(n.getMachineCount() * effectiveScale));

            if (updateNodeCount(n, targetCount)) {
                changed++;
            }
        }
        return changed;
    }

    private static boolean isNodeInPool(CanvasGroupFrame poolFrame, RecipeNode node) {
        return poolFrame.containsNode(node.getId()) || poolFrame.isPointInside(node.getPosX(), node.getPosY());
    }

    private static double roundFractional(double value) {
        return Math.max(0.0001, Math.round(value * 10000.0) / 10000.0);
    }

    private static boolean updateNodeCount(RecipeNode node, double newCount) {
        double oldCount = node.getMachineCount();
        if (Math.abs(oldCount - newCount) > 0.0001) {
            node.setMachineCount(newCount);
            return true;
        }
        return false;
    }

    private static int findBestHarmonizedMultiplier(FlowGraph graph, CanvasGroupFrame poolFrame, Set<String> connectedIds, double baseScale) {
        int maxScale = 16;
        double tolerance = 0.02;
        try {
            maxScale = Math.max(1, BoardManager.getInstance().getMaxHarmonizeScale());
            tolerance = Math.max(0.0, BoardManager.getInstance().getHarmonizeSurplusTolerance());
        } catch (Throwable ignored) {}

        List<RecipeNode> externalNodes = getExternalOperationalNodes(graph, poolFrame, connectedIds);
        if (externalNodes.isEmpty()) return 1;

        for (int k = 1; k <= maxScale; k++) {
            if (isHarmonizedMatch(externalNodes, baseScale * k, tolerance)) {
                return k;
            }
        }
        return findBestScoredMultiplier(externalNodes, baseScale, maxScale, tolerance);
    }

    private static List<RecipeNode> getExternalOperationalNodes(FlowGraph graph, CanvasGroupFrame poolFrame, Set<String> connectedIds) {
        List<RecipeNode> external = new ArrayList<>();
        for (String id : connectedIds) {
            RecipeNode n = graph.findNodeById(id);
            if (n != null && !n.isReroute() && !isNodeInPool(poolFrame, n)) {
                external.add(n);
            }
        }
        return external;
    }

    private static boolean isHarmonizedMatch(List<RecipeNode> nodes, double scale, double tolerance) {
        for (RecipeNode n : nodes) {
            double scaled = n.getMachineCount() * scale;
            if (scaled <= 1e-4) continue;
            if (tolerance <= 1e-5) {
                if (Math.abs(scaled - Math.round(scaled)) > 0.005) return false;
            } else if (scaled >= 0.8) {
                double nearestInt = Math.max(1.0, Math.round(scaled));
                if (Math.abs(scaled - nearestInt) / scaled > tolerance) return false;
            }
        }
        return true;
    }

    private static int findBestScoredMultiplier(List<RecipeNode> nodes, double baseScale, int maxScale, double tolerance) {
        int bestScale = 1;
        double bestScore = Double.MAX_VALUE;

        for (int k = 1; k <= maxScale; k++) {
            double score = evaluateHarmonizeMultiplierScore(nodes, baseScale * k, tolerance);
            if (score < bestScore) {
                bestScore = score;
                bestScale = k;
            }
        }
        return bestScale;
    }

    private static double evaluateHarmonizeMultiplierScore(List<RecipeNode> nodes, double scale, double tolerance) {
        double totalError = 0.0;
        double maxMajorError = 0.0;
        double totalMachines = 0.0;

        for (RecipeNode n : nodes) {
            double scaled = n.getMachineCount() * scale;
            if (scaled <= 1e-4) continue;

            double nearestInt = Math.max(1.0, Math.round(scaled));
            double err = Math.abs(scaled - nearestInt) / Math.max(1.0, scaled);
            if (scaled >= 0.8) {
                maxMajorError = Math.max(maxMajorError, err);
            }
            totalError += err;
            totalMachines += nearestInt;
        }

        double tolerancePenalty = (maxMajorError <= tolerance) ? 0.0 : (maxMajorError - tolerance) * 100.0;
        return tolerancePenalty + (totalError * 10.0) + (totalMachines * 0.1);
    }

    private static void postSolveEvent(FlowGraph graph) {
        try {
            MinecraftForge.EVENT_BUS.post(new FlowGraphEvent.PostSolve(graph));
        } catch (Throwable ignored) {}
    }
}
