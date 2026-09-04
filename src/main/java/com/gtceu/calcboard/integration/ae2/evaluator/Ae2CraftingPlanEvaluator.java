package com.gtceu.calcboard.integration.ae2.evaluator;

import appeng.api.crafting.IPatternDetails;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.integration.ae2.model.Ae2PlanEvaluationResult;
import com.gtceu.calcboard.integration.ae2.model.Ae2PlanStep;
import com.gtceu.calcboard.integration.ae2.model.PageProcessingSpec;
import com.gtceu.calcboard.integration.ae2.model.PatternId;
import com.gtceu.calcboard.integration.ae2.registry.PatternGraphRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * High-performance O(K) precision ETA and bottleneck evaluation engine for AE2 autocrafting plans.
 */
public final class Ae2CraftingPlanEvaluator {

    private Ae2CraftingPlanEvaluator() {}

    public static Ae2PlanEvaluationResult evaluate(Map<IPatternDetails, Long> patternTimes, int coProcessors) {
        if (patternTimes == null || patternTimes.isEmpty()) {
            return Ae2PlanEvaluationResult.EMPTY;
        }

        Map<PatternId, Long> patternCounts = new HashMap<>();
        for (Map.Entry<IPatternDetails, Long> entry : patternTimes.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
                patternCounts.put(PatternId.of(entry.getKey()), entry.getValue());
            }
        }

        return evaluatePatternCounts(patternCounts, coProcessors);
    }

    public static Ae2PlanEvaluationResult evaluatePatternCounts(Map<PatternId, Long> patternCounts, int coProcessors) {
        if (patternCounts == null || patternCounts.isEmpty()) {
            return Ae2PlanEvaluationResult.EMPTY;
        }

        Map<String, PagePlanDemand> demandMap = new LinkedHashMap<>();
        PatternGraphRegistry registry = PatternGraphRegistry.getInstance();

        Map<PatternId, BoardPage> directPages = new LinkedHashMap<>();
        for (PatternId pid : patternCounts.keySet()) {
            if (pid != null) {
                registry.getDirectBoundPage(pid).ifPresent(p -> directPages.put(pid, p));
            }
        }

        Set<ResourceLocation> secondaryByproducts = new HashSet<>();
        for (Map.Entry<PatternId, BoardPage> entry : directPages.entrySet()) {
            ResourceLocation primaryId = entry.getKey().getPrimaryOutputId();
            Map<ResourceLocation, Double> outs = PatternGraphRegistry.extractAllPageOutputs(entry.getValue());
            for (ResourceLocation outId : outs.keySet()) {
                if (!Objects.equals(outId, primaryId)) {
                    secondaryByproducts.add(outId);
                }
            }
        }

        List<Map.Entry<PatternId, Long>> sortedEntries = new ArrayList<>(patternCounts.entrySet());
        sortedEntries.sort((e1, e2) -> {
            boolean e1IsByproduct = secondaryByproducts.contains(e1.getKey().getPrimaryOutputId());
            boolean e2IsByproduct = secondaryByproducts.contains(e2.getKey().getPrimaryOutputId());
            return Boolean.compare(e1IsByproduct, e2IsByproduct);
        });

        Map<ResourceLocation, Double> byproductPool = new HashMap<>();

        for (Map.Entry<PatternId, Long> entry : sortedEntries) {
            PatternId pid = entry.getKey();
            Long count = entry.getValue();
            if (pid == null || count == null || count <= 0) continue;

            Optional<BoardPage> directOpt = registry.getDirectBoundPage(pid);
            if (directOpt.isPresent()) {
                BoardPage page = directOpt.get();
                ResourceLocation outId = pid.getPrimaryOutputId();
                double batchAmount = PatternGraphRegistry.findPageBatchOutputAmount(page, outId);

                double credit = (outId != null) ? byproductPool.getOrDefault(outId, 0.0) : 0.0;
                double netNeeded = Math.max(0.0, count - credit);
                if (netNeeded > 0.0) {
                    long batches = (long) Math.ceil(netNeeded / Math.max(1.0, batchAmount));
                    PagePlanDemand demand = demandMap.computeIfAbsent(page.getId(), k -> new PagePlanDemand(page, pid));
                    demand.maxBatches += batches;
                    demand.totalCount += (long) Math.ceil(netNeeded);

                    Map<ResourceLocation, Double> pageOutputs = PatternGraphRegistry.extractPageOutputsForPrimary(page, outId);
                    for (Map.Entry<ResourceLocation, Double> po : pageOutputs.entrySet()) {
                        if (!Objects.equals(po.getKey(), outId)) {
                            byproductPool.put(po.getKey(), byproductPool.getOrDefault(po.getKey(), 0.0) + batches * po.getValue());
                        }
                    }
                }
            }
        }

        for (Map.Entry<PatternId, Long> entry : sortedEntries) {
            PatternId pid = entry.getKey();
            Long count = entry.getValue();
            if (pid == null || count == null || count <= 0) continue;

            if (registry.getDirectBoundPage(pid).isPresent()) {
                continue;
            }

            ResourceLocation outId = pid.getPrimaryOutputId();
            double credit = (outId != null) ? byproductPool.getOrDefault(outId, 0.0) : 0.0;
            double netNeeded = Math.max(0.0, count - credit);
            if (netNeeded <= 0.0) {
                continue;
            }

            Optional<BoardPage> secondaryOpt = registry.getBoundPage(pid);
            if (secondaryOpt.isPresent()) {
                BoardPage page = secondaryOpt.get();
                double batchAmount = PatternGraphRegistry.findPageBatchOutputAmount(page, outId);
                long batches = (long) Math.ceil(netNeeded / Math.max(1.0, batchAmount));
                PagePlanDemand demand = demandMap.computeIfAbsent(page.getId(), k -> new PagePlanDemand(page, pid));
                demand.maxBatches += batches;
                demand.totalCount += (long) Math.ceil(netNeeded);
            } else {
                long batches = resolveFallbackBatches(pid, (long) Math.ceil(netNeeded));
                PagePlanDemand demand = demandMap.computeIfAbsent("fallback:" + pid.getKey(), k -> new PagePlanDemand(null, pid));
                demand.maxBatches += batches;
                demand.totalCount += (long) Math.ceil(netNeeded);
            }
        }

        if (demandMap.isEmpty()) {
            return Ae2PlanEvaluationResult.EMPTY;
        }

        List<PagePlanDemand> demandList = new ArrayList<>();
        List<Ae2PlanStep> steps = new ArrayList<>();

        for (PagePlanDemand demand : demandMap.values()) {
            if (demand.maxBatches <= 0) continue;
            PageProcessingSpec spec = (demand.page != null)
                    ? PageDurationEvaluator.evaluate(demand.page)
                    : createFallbackSpec(demand.patternId);

            int batches = (int) Math.ceil((double) demand.maxBatches / spec.effectiveParallel());
            double totalStepTicks = batches * spec.unitDurationTicks();

            demandList.add(demand);
            steps.add(new Ae2PlanStep(
                    demand.patternId,
                    spec.primaryOutputName(),
                    demand.totalCount,
                    batches,
                    spec.unitDurationTicks(),
                    spec.effectiveParallel(),
                    totalStepTicks,
                    spec.pageId(),
                    spec.pageName()
            ));
        }

        if (steps.isEmpty()) {
            return Ae2PlanEvaluationResult.EMPTY;
        }

        return buildPipelinedResult(demandList, steps, coProcessors);
    }

    private static Ae2PlanEvaluationResult buildPipelinedResult(List<PagePlanDemand> demands, List<Ae2PlanStep> steps, int coProcessors) {
        int n = steps.size();
        if (n == 0) return Ae2PlanEvaluationResult.EMPTY;

        Map<String, List<Integer>> predecessors = new HashMap<>();
        for (int i = 0; i < n; i++) {
            predecessors.put(String.valueOf(i), new ArrayList<>());
        }

        for (int i = 0; i < n; i++) {
            PagePlanDemand consumerDemand = demands.get(i);
            Set<ResourceLocation> requiredInputs = (consumerDemand.page != null)
                    ? PatternGraphRegistry.extractAllPageInputs(consumerDemand.page)
                    : Collections.emptySet();

            if (requiredInputs.isEmpty()) continue;

            for (ResourceLocation reqIn : requiredInputs) {
                for (int j = 0; j < n; j++) {
                    if (i == j) continue;
                    PagePlanDemand producerDemand = demands.get(j);
                    boolean produces = (producerDemand.page != null)
                            ? PatternGraphRegistry.pageProducesOutput(producerDemand.page, reqIn)
                            : Objects.equals(producerDemand.patternId.getPrimaryOutputId(), reqIn);
                    if (produces) {
                        predecessors.get(String.valueOf(i)).add(j);
                    }
                }
            }
        }

        double[] tStart = new double[n];
        double[] tFinish = new double[n];
        boolean[] memoized = new boolean[n];
        boolean[] inStack = new boolean[n];

        for (int i = 0; i < n; i++) {
            computeStepTiming(i, steps, predecessors, tStart, tFinish, memoized, inStack);
        }

        double pipelinedTotalTicks = 0.0;
        double maxStepDuration = 0.0;
        String bottleneckName = "";
        String bottleneckPageId = "";

        for (int i = 0; i < n; i++) {
            Ae2PlanStep step = steps.get(i);
            pipelinedTotalTicks = Math.max(pipelinedTotalTicks, tFinish[i]);
            if (step.totalDurationTicks() > maxStepDuration) {
                maxStepDuration = step.totalDurationTicks();
                bottleneckName = step.stepName();
                bottleneckPageId = step.boundPageId();
            }
        }

        long finalEtaTicks = (long) Math.ceil(pipelinedTotalTicks);
        double finalEtaSeconds = finalEtaTicks / 20.0;
        String formattedEta = formatEtaDuration(finalEtaSeconds);

        return new Ae2PlanEvaluationResult(
                finalEtaTicks,
                finalEtaSeconds,
                formattedEta,
                bottleneckName,
                bottleneckPageId,
                steps,
                coProcessors
        );
    }

    private static void computeStepTiming(int i, List<Ae2PlanStep> steps, Map<String, List<Integer>> predecessors,
                                          double[] tStart, double[] tFinish, boolean[] memoized, boolean[] inStack) {
        if (memoized[i]) return;
        if (inStack[i]) return;

        inStack[i] = true;
        Ae2PlanStep step = steps.get(i);
        List<Integer> preds = predecessors.get(String.valueOf(i));

        double maxPredStartPlusUnit = 0.0;
        double maxPredFinish = 0.0;
        boolean hasPreds = preds != null && !preds.isEmpty();

        if (hasPreds) {
            for (int p : preds) {
                computeStepTiming(p, steps, predecessors, tStart, tFinish, memoized, inStack);
                Ae2PlanStep pStep = steps.get(p);
                maxPredStartPlusUnit = Math.max(maxPredStartPlusUnit, tStart[p] + pStep.unitDurationTicks());
                maxPredFinish = Math.max(maxPredFinish, tFinish[p]);
            }
        }

        tStart[i] = hasPreds ? maxPredStartPlusUnit : 0.0;
        double selfFinish = tStart[i] + step.totalDurationTicks();
        double tailFinish = hasPreds ? (maxPredFinish + step.unitDurationTicks()) : selfFinish;
        tFinish[i] = Math.max(selfFinish, tailFinish);

        inStack[i] = false;
        memoized[i] = true;
    }

    private static long resolveFallbackBatches(PatternId patternId, long count) {
        if (count >= 100) {
            return (long) Math.ceil((double) count / 1000.0);
        }
        return count;
    }

    private static PageProcessingSpec createFallbackSpec(PatternId patternId) {
        return new PageProcessingSpec(
                20.0,
                1,
                "",
                "Standard Crafting",
                patternId != null ? patternId.getDisplayName() : "Item"
        );
    }

    public static String formatEtaDuration(double seconds) {
        if (seconds <= 0.05) return "< 1s";
        if (seconds < 60.0) {
            return String.format(Locale.ROOT, "~%.1fs", seconds);
        }
        long totalSec = Math.round(seconds);
        long hours = totalSec / 3600;
        long mins = (totalSec % 3600) / 60;
        long secs = totalSec % 60;

        if (hours > 0) {
            return String.format(Locale.ROOT, "~%dh %dm %ds", hours, mins, secs);
        }
        return String.format(Locale.ROOT, "~%dm %ds", mins, secs);
    }

    private static class PagePlanDemand {
        private final BoardPage page;
        private final PatternId patternId;
        private long maxBatches = 0L;
        private long totalCount = 0L;

        private PagePlanDemand(BoardPage page, PatternId patternId) {
            this.page = page;
            this.patternId = patternId;
        }
    }
}
