package com.gtceu.calcboard.integration.ae2.evaluator;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.integration.ae2.model.PageProcessingSpec;

/**
 * Evaluates the unit processing duration and effective parallelism of a calculator board page.
 * Analyzes the critical bottleneck processing machine configured on the page.
 */
public final class PageDurationEvaluator {

    private PageDurationEvaluator() {}

    public static PageProcessingSpec evaluate(BoardPage page) {
        if (page == null || page.getGraph() == null || page.getGraph().getNodes().isEmpty()) {
            return PageProcessingSpec.DEFAULT_FALLBACK;
        }

        FlowGraph graph = page.getGraph();
        RecipeNode criticalNode = findCriticalProcessingNode(graph);
        if (criticalNode == null) {
            return new PageProcessingSpec(20.0, 1, page.getId(), page.getName(), page.getName());
        }

        double unitTicks = resolveNodeUnitTicks(criticalNode);
        int parallel = resolveNodeEffectiveParallel(criticalNode);
        String outputName = resolveNodeDisplayName(criticalNode);

        return new PageProcessingSpec(unitTicks, parallel, page.getId(), page.getName(), outputName);
    }

    private static RecipeNode findCriticalProcessingNode(FlowGraph graph) {
        RecipeNode criticalNode = null;
        double maxTimePerBatch = 0.0;

        for (RecipeNode node : graph.getNodes()) {
            if (node == null || node.isReroute()) continue;

            double unitTicks = resolveNodeUnitTicks(node);
            int parallel = resolveNodeEffectiveParallel(node);
            double timePerBatch = unitTicks / parallel;

            if (criticalNode == null || timePerBatch > maxTimePerBatch) {
                maxTimePerBatch = timePerBatch;
                criticalNode = node;
            }
        }

        return criticalNode;
    }

    private static double resolveNodeUnitTicks(RecipeNode node) {
        double unitTicks = node.getOverclockResult().durationTicks();
        if (unitTicks <= 0.0) {
            unitTicks = Math.max(1.0, node.getBaseDurationTicks());
        }
        return unitTicks;
    }

    private static int resolveNodeEffectiveParallel(RecipeNode node) {
        return Math.max(1, (int) Math.round(node.getTotalParallel() * node.getMachineCount()));
    }

    private static String resolveNodeDisplayName(RecipeNode node) {
        if (!node.getOutputs().isEmpty()) {
            return node.getOutputs().get(0).getDisplayName();
        }
        return node.getMachineDisplayName();
    }
}
