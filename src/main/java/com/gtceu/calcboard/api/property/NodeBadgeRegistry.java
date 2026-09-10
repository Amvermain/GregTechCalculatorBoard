package com.gtceu.calcboard.api.property;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of declarative {@link INodeBadgeProvider}s for assembling node badges.
 */
public final class NodeBadgeRegistry {

    private static final List<INodeBadgeProvider> PROVIDERS = new ArrayList<>();

    static {
        register((node, store) -> {
            if (node == null || store == null) return List.of();
            if (Boolean.TRUE.equals(store.get(NodeProperties.IS_GENERIC_UNSUPPORTED))) {
                String badgeText = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.generic_unsupported").getString();
                List<net.minecraft.network.chat.Component> tooltip = List.of(
                        net.minecraft.network.chat.Component.literal("§6⚙ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.generic_unsupported_title").getString()),
                        net.minecraft.network.chat.Component.literal("§7" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.generic_unsupported_desc").getString()),
                        net.minecraft.network.chat.Component.literal("§e★ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.generic_unsupported_hint").getString())
                );
                return List.of(new NodeBadge(badgeText, 0xFFFB923C, 0xEE3D2C1C, 0xFFFB923C, tooltip));
            }
            return List.of();
        });

        // Compound / Layered Recipe Badge Provider
        register((node, store) -> {
            if (node == null || store == null) return List.of();
            if (node.isCompoundNode()) {
                int layerIdx = node.getCompoundLayerIndex();
                int totalLayers = node.getCompoundTotalLayers();
                String roman = com.gtceu.calcboard.api.model.CompoundRecipeBuilder.formatRoman(layerIdx + 1);
                String badgeText = "▦ " + roman + "/" + com.gtceu.calcboard.api.model.CompoundRecipeBuilder.formatRoman(totalLayers);
                List<net.minecraft.network.chat.Component> tooltip = List.of(
                        net.minecraft.network.chat.Component.literal("§d▦ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.compound_layer_title", roman, totalLayers).getString()),
                        net.minecraft.network.chat.Component.literal("§7" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.compound_layer_desc").getString()),
                        net.minecraft.network.chat.Component.literal("§b↔ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.compound_layer_hint").getString())
                );
                return List.of(new NodeBadge(badgeText, 0xFFE879F9, 0xEE3B1D45, 0xFFC084FC, tooltip));
            }
            return List.of();
        });

        // RFC-032 / RFC-033: Auto-Ratio Divergence Warning Badge Provider
        register((node, store) -> {
            if (node == null || store == null) return List.of();
            if (!Boolean.TRUE.equals(store.get(NodeProperties.DIVERGENCE_WARNING))) {
                return List.of();
            }
            String reason = store.get(NodeProperties.DIVERGENCE_REASON);
            if ("positive_feedback".equals(reason)) {
                return List.of(createPositiveFeedbackBadge(node, store));
            }
            if ("catalyst_decay".equals(reason)) {
                return List.of(createCatalystDecayBadge(node, store));
            }
            if ("anchor_conflict".equals(reason)) {
                return List.of(createAnchorConflictBadge(node, store));
            }
            if ("micro_yield_clamp".equals(reason)) {
                return List.of(createMicroYieldBadge(node, store));
            }
            if ("damped_loop".equals(reason)) {
                return List.of();
            }
            return List.of(createRecirculationBadge(node, store));
        });

        register((IGraphNodeBadgeProvider) (node, store, graph) -> {
            if (node == null) return List.of();
            FlowGraph effectiveGraph = graph != null ? graph : node.getParentGraph();
            if (effectiveGraph == null) return List.of();
            if (hasUnfedDampedLoop(effectiveGraph, node)) {
                return List.of(createDampedLoopBadge(node, store));
            }
            return List.of();
        });
    }

    private static boolean hasUnfedDampedLoop(FlowGraph graph, RecipeNode node) {
        if (graph == null || node == null || node.isReroute()) return false;
        int inputCount = node.getInputs().size();
        for (int i = 0; i < inputCount; i++) {
            FlowGraphSolver.PortFlowStats stats = graph.getInputPortStats(node, i);
            if (stats != null && stats.isUnfedDampedLoop()) {
                return true;
            }
        }
        return false;
    }

    private static NodeBadge createPositiveFeedbackBadge(RecipeNode node, NodePropertyStore store) {
        String badgeText = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.positive_feedback").getString();
        List<net.minecraft.network.chat.Component> tooltip = List.of(
                net.minecraft.network.chat.Component.literal("§b§l[⚠ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.positive_feedback_title").getString() + "]"),
                net.minecraft.network.chat.Component.literal("§7" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.positive_feedback_desc").getString()),
                net.minecraft.network.chat.Component.literal("§e💡 " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.positive_feedback_hint_1").getString()),
                resolveHint2(node, "gui.gtcalcboard.node_badge.positive_feedback_hint_2"),
                createActionOrStatusLine(node)
        );
        return new NodeBadge(badgeText, 0xFF06B6D4, 0xEE083344, 0xFF0891B2, tooltip, true, resolveAnchorAction(node, store));
    }

    private static NodeBadge createCatalystDecayBadge(RecipeNode node, NodePropertyStore store) {
        String badgeText = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.catalyst_decay").getString();
        List<net.minecraft.network.chat.Component> tooltip = List.of(
                net.minecraft.network.chat.Component.literal("§6§l[⚠ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.catalyst_decay_title").getString() + "]"),
                net.minecraft.network.chat.Component.literal("§7" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.catalyst_decay_desc").getString()),
                net.minecraft.network.chat.Component.literal("§e💡 " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.catalyst_decay_hint_1").getString()),
                resolveHint2(node, "gui.gtcalcboard.node_badge.catalyst_decay_hint_2"),
                createActionOrStatusLine(node)
        );
        return new NodeBadge(badgeText, 0xFFFBBF24, 0xEE451A03, 0xFFD97706, tooltip, true, resolveAnchorAction(node, store));
    }

    private static NodeBadge createAnchorConflictBadge(RecipeNode node, NodePropertyStore store) {
        String badgeText = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.anchor_conflict").getString();
        List<net.minecraft.network.chat.Component> tooltip = List.of(
                net.minecraft.network.chat.Component.literal("§c§l[⚠ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.anchor_conflict_title").getString() + "]"),
                net.minecraft.network.chat.Component.literal("§7" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.anchor_conflict_desc").getString()),
                net.minecraft.network.chat.Component.literal("§e💡 " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.anchor_conflict_hint_1").getString()),
                net.minecraft.network.chat.Component.literal("§e💡 " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.anchor_conflict_hint_2").getString()),
                net.minecraft.network.chat.Component.literal("§b§n[" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.anchor_conflict_action").getString() + "]")
        );
        return new NodeBadge(badgeText, 0xFFEF4444, 0xEE450A0A, 0xFFDC2626, tooltip, true, () -> {
            node.setBaseNode(false);
            store.set(NodeProperties.DIVERGENCE_WARNING, false);
            store.set(NodeProperties.DIVERGENCE_REASON, "");
        });
    }

    private static NodeBadge createMicroYieldBadge(RecipeNode node, NodePropertyStore store) {
        String badgeText = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.micro_yield").getString();
        List<net.minecraft.network.chat.Component> tooltip = List.of(
                net.minecraft.network.chat.Component.literal("§6§l[⚠ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.micro_yield_title").getString() + "]"),
                net.minecraft.network.chat.Component.literal("§7" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.micro_yield_desc").getString()),
                net.minecraft.network.chat.Component.literal("§e💡 " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.micro_yield_hint_1").getString()),
                resolveHint2(node, "gui.gtcalcboard.node_badge.micro_yield_hint_2"),
                createActionOrStatusLine(node)
        );
        return new NodeBadge(badgeText, 0xFFF59E0B, 0xEE451A03, 0xFFD97706, tooltip, true, resolveAnchorAction(node, store));
    }

    private static NodeBadge createRecirculationBadge(RecipeNode node, NodePropertyStore store) {
        String badgeText = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.divergence_warning").getString();
        List<net.minecraft.network.chat.Component> tooltip = List.of(
                net.minecraft.network.chat.Component.literal("§6§l[⚠ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.divergence_warning_title").getString() + "]"),
                net.minecraft.network.chat.Component.literal("§7" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.divergence_warning_desc").getString()),
                net.minecraft.network.chat.Component.literal("§e💡 " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.divergence_warning_hint_1").getString()),
                resolveHint2(node, "gui.gtcalcboard.node_badge.divergence_warning_hint_2"),
                createActionOrStatusLine(node)
        );
        return new NodeBadge(badgeText, 0xFFF59E0B, 0xEE451A03, 0xFFD97706, tooltip, true, resolveAnchorAction(node, store));
    }

    private static NodeBadge createDampedLoopBadge(RecipeNode node, NodePropertyStore store) {
        String badgeText = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.damped_loop").getString();
        List<net.minecraft.network.chat.Component> tooltip = List.of(
                net.minecraft.network.chat.Component.literal("§6§l[⚠ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.damped_loop_title").getString() + "]"),
                net.minecraft.network.chat.Component.literal("§7" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.damped_loop_desc").getString()),
                net.minecraft.network.chat.Component.literal("§e💡 " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.damped_loop_hint_1").getString()),
                net.minecraft.network.chat.Component.literal("§e💡 " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.damped_loop_hint_2").getString()),
                net.minecraft.network.chat.Component.literal("§7" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.damped_loop_action").getString())
        );
        return new NodeBadge(badgeText, 0xFFF59E0B, 0xEE451A03, 0xFFD97706, tooltip, true, () -> {});
    }

    private static net.minecraft.network.chat.Component resolveHint2(RecipeNode node, String defaultKey) {
        if (node != null && node.isBaseNode()) {
            return net.minecraft.network.chat.Component.literal("§e💡 " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.divergence_warning_hint_2_anchored").getString());
        }
        return net.minecraft.network.chat.Component.literal("§e💡 " + net.minecraft.network.chat.Component.translatable(defaultKey).getString());
    }

    private static net.minecraft.network.chat.Component createActionOrStatusLine(RecipeNode node) {
        if (node != null && node.isBaseNode()) {
            return net.minecraft.network.chat.Component.literal("§e⚓ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.divergence_warning_already_anchor").getString());
        }
        return net.minecraft.network.chat.Component.literal("§b§n[" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.node_badge.divergence_warning_action").getString() + "]");
    }

    private static Runnable resolveAnchorAction(RecipeNode node, NodePropertyStore store) {
        if (node != null && node.isBaseNode()) {
            return () -> {};
        }
        return () -> clearWarningAsAnchor(node, store);
    }

    private static void clearWarningAsAnchor(RecipeNode node, NodePropertyStore store) {
        node.setBaseNode(true);
        store.set(NodeProperties.DIVERGENCE_WARNING, false);
        store.set(NodeProperties.DIVERGENCE_REASON, "");
    }

    private NodeBadgeRegistry() {}

    public static synchronized void register(INodeBadgeProvider provider) {
        if (provider != null && !PROVIDERS.contains(provider)) {
            PROVIDERS.add(provider);
        }
    }

    public static synchronized void register(IGraphNodeBadgeProvider provider) {
        register((INodeBadgeProvider) provider);
    }

    public static List<INodeBadgeProvider> getProviders() {
        return Collections.unmodifiableList(PROVIDERS);
    }

    /**
     * Gathers all badges provided for the specified node, resolving graph context from the node itself.
     */
    public static List<NodeBadge> getBadgesForNode(RecipeNode node) {
        return getBadgesForNode(node, node != null ? node.getParentGraph() : null);
    }

    /**
     * Gathers all badges provided for the specified node within the explicit graph context.
     */
    public static List<NodeBadge> getBadgesForNode(RecipeNode node, FlowGraph graph) {
        if (node == null) return List.of();
        FlowGraph effectiveGraph = graph != null ? graph : node.getParentGraph();
        NodePropertyStore store = node.getProperties();
        List<NodeBadge> list = new ArrayList<>();
        for (INodeBadgeProvider provider : PROVIDERS) {
            try {
                List<NodeBadge> badges = provider.createBadges(node, store, effectiveGraph);
                if (badges != null && !badges.isEmpty()) {
                    list.addAll(badges);
                }
            } catch (Throwable ignored) {}
        }
        return list;
    }
}

