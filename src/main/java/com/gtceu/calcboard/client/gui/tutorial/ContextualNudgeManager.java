package com.gtceu.calcboard.client.gui.tutorial;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.client.gui.tutorial.model.ContextualNudge;
import com.gtceu.calcboard.client.gui.tutorial.model.NudgeTriggerType;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Detects contextual usage patterns and serves single-occurrence in-game hints.
 */
public class ContextualNudgeManager {
    private static final ContextualNudgeManager INSTANCE = new ContextualNudgeManager();
    private static final long NUDGE_DISPLAY_DURATION_MS = 5000L;

    public static ContextualNudgeManager getInstance() {
        return INSTANCE;
    }

    private final Map<String, ContextualNudge> registry = new LinkedHashMap<>();
    private final Set<String> seenNudges = new HashSet<>();
    private boolean nudgesEnabled = true;
    private ContextualNudge activeNudge = null;
    private long activeNudgeTimestamp = 0L;

    private ContextualNudgeManager() {
        registerNudge(new ContextualNudge(
                "nudge_auto_connect",
                NudgeTriggerType.AUTO_CONNECT,
                Component.translatable("gui.gtcalcboard.nudge.auto_connect"),
                "Shift+C",
                "ch2_wiring"
        ));
        registerNudge(new ContextualNudge(
                "nudge_wire_reroute",
                NudgeTriggerType.WIRE_REROUTE,
                Component.translatable("gui.gtcalcboard.nudge.wire_reroute"),
                "Double-Click",
                "ch2_wiring"
        ));
        registerNudge(new ContextualNudge(
                "nudge_loop_damped",
                NudgeTriggerType.LOOP_DAMPED,
                Component.translatable("gui.gtcalcboard.nudge.loop_damped"),
                "Shift+RClick",
                "ch1_solver"
        ));
        registerNudge(new ContextualNudge(
                "nudge_byproduct_void",
                NudgeTriggerType.BYPRODUCT_VOID,
                Component.translatable("gui.gtcalcboard.nudge.byproduct_void"),
                "Shift+RClick",
                "ch2_wiring"
        ));
        registerNudge(new ContextualNudge(
                "nudge_module_subpage",
                NudgeTriggerType.MODULE_SUBPAGE,
                Component.translatable("gui.gtcalcboard.nudge.module_subpage"),
                "Double-Click",
                "ch3_packaging"
        ));
    }

    public void registerNudge(ContextualNudge nudge) {
        if (nudge != null) {
            registry.put(nudge.nudgeId(), nudge);
        }
    }

    public boolean isNudgesEnabled() {
        return nudgesEnabled;
    }

    public void setNudgesEnabled(boolean enabled) {
        this.nudgesEnabled = enabled;
        if (!enabled) {
            dismissActiveNudge();
        }
    }

    public boolean hasSeenNudge(String nudgeId) {
        return seenNudges.contains(nudgeId);
    }

    public void markNudgeSeen(String nudgeId) {
        if (nudgeId != null) {
            seenNudges.add(nudgeId);
        }
    }

    public void resetSeenNudges() {
        seenNudges.clear();
        dismissActiveNudge();
    }

    public boolean triggerNudge(String nudgeId) {
        if (!nudgesEnabled || seenNudges.contains(nudgeId)) {
            return false;
        }
        if (getActiveNudge() != null) {
            return false;
        }
        ContextualNudge nudge = registry.get(nudgeId);
        if (nudge == null) {
            return false;
        }
        seenNudges.add(nudgeId);
        activeNudge = nudge;
        activeNudgeTimestamp = System.currentTimeMillis();
        return true;
    }

    public ContextualNudge getActiveNudge() {
        if (activeNudge == null) return null;
        if (System.currentTimeMillis() - activeNudgeTimestamp > NUDGE_DISPLAY_DURATION_MS) {
            activeNudge = null;
            return null;
        }
        return activeNudge;
    }

    public void dismissActiveNudge() {
        activeNudge = null;
        activeNudgeTimestamp = 0L;
    }

    public void checkTriggers(BoardPage page) {
        if (!nudgesEnabled || page == null || page.getGraph() == null) return;
        if (TutorialManager.getInstance().isActive()) return;
        if (getActiveNudge() != null) return;

        FlowGraph graph = page.getGraph();
        checkAutoConnectTrigger(graph);
        if (getActiveNudge() != null) return;
        checkWireRerouteTrigger(graph);
        if (getActiveNudge() != null) return;
        checkLoopDampedTrigger(graph);
        if (getActiveNudge() != null) return;
        checkByproductVoidTrigger(graph);
        if (getActiveNudge() != null) return;
        checkModuleSubpageTrigger(graph);
    }

    private void checkAutoConnectTrigger(FlowGraph graph) {
        if (seenNudges.contains("nudge_auto_connect")) return;
        long nonRerouteCount = graph.getNodes().stream().filter(n -> !n.isReroute()).count();
        if (nonRerouteCount >= 2 && graph.getConnections().isEmpty()) {
            triggerNudge("nudge_auto_connect");
        }
    }

    private void checkWireRerouteTrigger(FlowGraph graph) {
        if (seenNudges.contains("nudge_wire_reroute")) return;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            RecipeNode from = graph.findNodeById(edge.fromNodeId());
            RecipeNode to = graph.findNodeById(edge.toNodeId());
            if (from != null && to != null) {
                double dist = Math.hypot(to.getPosX() - from.getPosX(), to.getPosY() - from.getPosY());
                if (dist >= 250.0) {
                    triggerNudge("nudge_wire_reroute");
                    return;
                }
            }
        }
    }

    private void checkLoopDampedTrigger(FlowGraph graph) {
        if (seenNudges.contains("nudge_loop_damped")) return;
        boolean hasDamped = graph.getNodes().stream()
                .filter(n -> !n.isReroute())
                .anyMatch(n -> {
                    for (int i = 0; i < n.getInputs().size(); i++) {
                        var stats = graph.getInputPortStats(n, i);
                        if (stats != null && (stats.isUnfedDampedLoop() || stats.isSteadyStateRecirculating())) {
                            return true;
                        }
                    }
                    return false;
                });
        if (hasDamped) {
            triggerNudge("nudge_loop_damped");
        }
    }

    private void checkByproductVoidTrigger(FlowGraph graph) {
        if (seenNudges.contains("nudge_byproduct_void")) return;
        for (RecipeNode node : graph.getNodes()) {
            if (node.isReroute()) continue;
            for (int i = 0; i < node.getOutputs().size(); i++) {
                var stats = graph.getOutputPortStats(node, i);
                if (stats != null && stats.surplusRate() > 0.001 && !node.isOutputPortVoided(i)) {
                    triggerNudge("nudge_byproduct_void");
                    return;
                }
            }
        }
    }

    private void checkModuleSubpageTrigger(FlowGraph graph) {
        if (seenNudges.contains("nudge_module_subpage")) return;
        boolean hasModule = graph.getNodes().stream().anyMatch(RecipeNode::isModule);
        if (hasModule) {
            triggerNudge("nudge_module_subpage");
        }
    }
}
