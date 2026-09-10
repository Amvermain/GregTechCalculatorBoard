package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NodePortTooltipRenderer {

    private NodePortTooltipRenderer() {}

    public static boolean renderInputPortTooltip(GuiGraphics graphics, Font font, BoardScreen screen, NodeWidget widget, int inIdx, int mouseX, int mouseY) {
        if (inIdx < 0 || inIdx >= widget.getNode().getInputs().size()) {
            return false;
        }

        IngredientStack in = widget.getNode().getInputs().get(inIdx);
        FlowGraph graph = screen.getGraph();
        boolean isBatch = FormatUtil.getActiveTimeUnit().isRecipeBatchMode();
        FlowGraphSolver.PortFlowStats stats = graph != null
                ? (isBatch ? graph.getBatchInputPortStats(widget.getNode(), inIdx) : graph.getInputPortStats(widget.getNode(), inIdx))
                : new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        boolean showExact = Screen.hasShiftDown();
        boolean[] hiddenRef = new boolean[]{false};

        List<Component> tooltipLines = new ArrayList<>();
        tooltipLines.add(Component.literal("§b[« " + Component.translatable("gui.gtcalcboard.input").getString() + "] §f" + in.getDisplayName()));

        String reqDisplay = BoardTooltipRenderer.formatPortRate(stats.requiredOrProducedRate(), in, showExact, hiddenRef);
        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.demand").getString() + ": §f" + reqDisplay));

        appendInputPortStats(tooltipLines, stats, in, showExact, hiddenRef, graph, widget.getNode(), inIdx);

        appendInputChanceInfo(tooltipLines, widget.getNode(), in, inIdx);
        if (in.hasAlternatives()) {
            int curIdx = in.getAlternatives().indexOf(in.getId()) + 1;
            tooltipLines.add(Component.literal("§6[⟲ " + Component.translatable("gui.gtcalcboard.tooltip.scroll_cycle").getString() + "]: §e" + Component.translatable("gui.gtcalcboard.tooltip.tag_alts", String.valueOf(curIdx), String.valueOf(in.getAlternatives().size())).getString()));
        }

        if (BoardManager.getInstance().isShowDebugInfo()) {
            tooltipLines.add(Component.literal("§8§m------------------------"));
            tooltipLines.add(Component.literal("§7[Debug] §8Port: §fInput #" + inIdx));
            if (in.getId() != null) {
                tooltipLines.add(Component.literal("§7[Debug] §8ID: §7" + in.getId()));
            }
        }

        if (hiddenRef[0]) {
            tooltipLines.add(Component.literal("§7[Shift]: §8" + Component.translatable("gui.gtcalcboard.tooltip.shift_exact").getString()));
        }
        tooltipLines.add(Component.literal("§7[Drag]: §f" + Component.translatable("gui.gtcalcboard.tooltip.drag_connect").getString()));
        tooltipLines.add(Component.literal("§e[Shift+Drag]: §a⚡ " + Component.translatable("gui.gtcalcboard.tooltip.shift_auto_ratio").getString()));
        tooltipLines.add(Component.literal("§c[Right-Click]: §7" + Component.translatable("gui.gtcalcboard.tooltip.right_click_hide").getString()));
        tooltipLines.add(Component.literal("§8").append(Component.translatable("gui.gtcalcboard.tooltip.recipes_uses")));
        BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
        return true;
    }

    public static boolean renderOutputPortTooltip(GuiGraphics graphics, Font font, BoardScreen screen, NodeWidget widget, int outIdx, int mouseX, int mouseY) {
        if (outIdx < 0 || outIdx >= widget.getNode().getOutputs().size()) {
            return false;
        }

        IngredientStack out = widget.getNode().getOutputs().get(outIdx);
        FlowGraph graph = screen.getGraph();
        boolean isBatch = FormatUtil.getActiveTimeUnit().isRecipeBatchMode();
        FlowGraphSolver.PortFlowStats stats = graph != null
                ? (isBatch ? graph.getBatchOutputPortStats(widget.getNode(), outIdx) : graph.getOutputPortStats(widget.getNode(), outIdx))
                : new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        boolean showExact = Screen.hasShiftDown();
        boolean[] hiddenRef = new boolean[]{false};

        List<Component> tooltipLines = new ArrayList<>();
        tooltipLines.add(Component.literal("§a[» " + Component.translatable("gui.gtcalcboard.output").getString() + "] §f" + out.getDisplayName()));
        if (widget.getNode().isOutputPortVoided(outIdx)) {
            tooltipLines.add(Component.literal("§d[∅] §d" + Component.translatable("gui.gtcalcboard.tooltip.voided_port").getString()));
        }

        String prodDisplay = BoardTooltipRenderer.formatPortRate(stats.requiredOrProducedRate(), out, showExact, hiddenRef);
        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.production").getString() + ": §f" + prodDisplay));

        appendOutputPortStats(tooltipLines, stats, out, showExact, hiddenRef);

        RecipeNode node = widget.getNode();
        appendOutputChanceInfo(tooltipLines, node, out, outIdx);

        if (BoardManager.getInstance().isShowDebugInfo()) {
            tooltipLines.add(Component.literal("§8§m------------------------"));
            tooltipLines.add(Component.literal("§7[Debug] §8Port: §fOutput #" + outIdx));
            if (out.getId() != null) {
                tooltipLines.add(Component.literal("§7[Debug] §8ID: §7" + out.getId()));
            }
        }

        if (hiddenRef[0]) {
            tooltipLines.add(Component.literal("§7[Shift]: §8" + Component.translatable("gui.gtcalcboard.tooltip.shift_exact").getString()));
        }
        tooltipLines.add(Component.literal("§7[Drag]: §f" + Component.translatable("gui.gtcalcboard.tooltip.drag_connect").getString()));
        tooltipLines.add(Component.literal("§e[Shift+Drag]: §a⚡ " + Component.translatable("gui.gtcalcboard.tooltip.shift_auto_ratio").getString()));
        tooltipLines.add(Component.literal("§e[Ctrl+Click]: §b🎯 " + Component.translatable("gui.gtcalcboard.tooltip.port_target_rate").getString()));
        tooltipLines.add(Component.literal("§c[Right-Click]: §7" + Component.translatable("gui.gtcalcboard.tooltip.right_click_hide").getString()));
        tooltipLines.add(Component.literal("§d[Ctrl/Alt+Right-Click]: §f" + Component.translatable("gui.gtcalcboard.tooltip.alt_right_click_void").getString()));
        tooltipLines.add(Component.literal("§8").append(Component.translatable("gui.gtcalcboard.tooltip.recipes_uses")));
        BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
        return true;
    }

    private static void appendInputChanceInfo(List<Component> tooltipLines, RecipeNode node, IngredientStack in, int inIdx) {
        double effChance = node != null ? node.getEffectiveInputChance(inIdx) : in.getChance();
        if (effChance >= 1.0 && in.getChance() >= 1.0 && Math.abs(in.getTierChanceBoost()) <= 0.00001) {
            return;
        }

        String chanceLabel = Component.translatable("gui.gtcalcboard.chance").getString().replace("%s%%", "").replace(":", "").trim();
        if (effChance <= 0.0) {
            tooltipLines.add(Component.literal("§c⚠ " + chanceLabel + ": 0%"));
            return;
        }

        if (Math.abs(in.getTierChanceBoost()) > 0.00001) {
            int tierDelta = node != null ? node.getTierDelta() : 0;
            if (tierDelta > 0 && Math.abs(effChance - in.getChance()) > 0.0001) {
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§e%s: %.1f%% §7(%+.1f%%/Tier §a→ %.1f%%§7)",
                        chanceLabel,
                        in.getChance() * 100.0,
                        in.getTierChanceBoost() * 100.0,
                        effChance * 100.0)));
            } else {
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§e%s: %.1f%% §7(%+.1f%%/Tier)",
                        chanceLabel,
                        in.getChance() * 100.0,
                        in.getTierChanceBoost() * 100.0)));
            }
            return;
        }

        tooltipLines.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.chance", String.format(Locale.ROOT, "%.1f", effChance * 100.0))));
    }

    private static void appendOutputChanceInfo(List<Component> tooltipLines, RecipeNode node, IngredientStack out, int outIdx) {
        double effChance = node != null ? node.getEffectiveOutputChance(outIdx) : out.getChance();
        if (effChance >= 1.0 && out.getChance() >= 1.0 && Math.abs(out.getTierChanceBoost()) <= 0.00001) {
            return;
        }

        String chanceLabel = Component.translatable("gui.gtcalcboard.chance").getString().replace("%s%%", "").replace(":", "").trim();
        if (effChance <= 0.0) {
            if (node != null && node.getSteamMode().isSteam()) {
                tooltipLines.add(Component.literal("§c⚠ " + chanceLabel + ": 0% (Steam Mode: No Byproducts)"));
            } else if (node != null && ResourceLocation.tryParse("gtceu:macerator").equals(node.getRecipeCategoryId())) {
                var reqTier = (node.getRecipeTier() != null && node.getRecipeTier().ordinal() > com.gtceu.calcboard.api.type.GTVoltageTier.HV.ordinal())
                        ? node.getRecipeTier()
                        : com.gtceu.calcboard.api.type.GTVoltageTier.HV;
                tooltipLines.add(Component.literal("§c⚠ " + chanceLabel + ": 0% (Requires " + reqTier.getName() + "+)"));
            } else {
                tooltipLines.add(Component.literal("§c⚠ " + chanceLabel + ": 0%"));
            }
            return;
        }

        if (Math.abs(out.getTierChanceBoost()) > 0.00001) {
            int tierDelta = node != null ? node.getTierDelta() : 0;
            if (tierDelta > 0 && Math.abs(effChance - out.getChance()) > 0.0001) {
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§e%s: %.1f%% §7(%+.1f%%/Tier §a→ %.1f%%§7)",
                        chanceLabel,
                        out.getChance() * 100.0,
                        out.getTierChanceBoost() * 100.0,
                        effChance * 100.0)));
            } else {
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§e%s: %.1f%% §7(%+.1f%%/Tier)",
                        chanceLabel,
                        out.getChance() * 100.0,
                        out.getTierChanceBoost() * 100.0)));
            }
            return;
        }

        tooltipLines.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.chance", String.format(Locale.ROOT, "%.1f", effChance * 100.0))));
    }

    private static void appendInputPortStats(List<Component> tooltipLines, FlowGraphSolver.PortFlowStats stats, IngredientStack in, boolean showExact, boolean[] hiddenRef, FlowGraph graph, RecipeNode node, int inIdx) {
        if (!stats.isConnected()) {
            tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.unconnected_raw").getString()));
            return;
        }

        RecipeNode bufferNode = graph != null ? graph.findConnectedBufferNode(node, inIdx) : null;
        boolean isBuffered = bufferNode != null;

        String supDisplay = BoardTooltipRenderer.formatPortRate(stats.connectedRate(), in, showExact, hiddenRef);
        String percentCol = stats.isBalanced() ? "§a"
                : (stats.isSteadyStateRecirculating() ? "§b"
                : (stats.isInputDeficit() ? (isBuffered ? "§e" : "§c")
                : (stats.isUpstreamThrottled() ? "§3" : "§b")));

        if (stats.isBalanced()) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.supply").getString() + ": " + percentCol + supDisplay + " §7(§a✔ 100%§7)"));
            tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.connected_producers", String.valueOf(stats.connectionCount())).getString()));
            return;
        }

        if (stats.isSteadyStateRecirculating()) {
            appendSteadyStateRecirculatingStats(tooltipLines, stats, in, showExact, hiddenRef, node);
        } else if (stats.isInputDeficit()) {
            appendInputDeficitStats(tooltipLines, stats, in, showExact, hiddenRef, percentCol, supDisplay, isBuffered, bufferNode, graph);
        } else if (stats.isUpstreamThrottled()) {
            appendUpstreamThrottledStats(tooltipLines, stats, in, showExact, hiddenRef, percentCol, supDisplay);
        } else {
            appendInputSurplusStats(tooltipLines, stats, in, showExact, hiddenRef, percentCol, supDisplay);
        }

        tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.connected_producers", String.valueOf(stats.connectionCount())).getString()));
    }

    private static void appendSteadyStateRecirculatingStats(
            List<Component> tooltipLines,
            FlowGraphSolver.PortFlowStats stats,
            IngredientStack in,
            boolean showExact,
            boolean[] hiddenRef,
            RecipeNode node
    ) {
        String extDisplay = BoardTooltipRenderer.formatPortRate(stats.externalSupplyRate(), in, showExact, hiddenRef);
        String loopDisplay = BoardTooltipRenderer.formatPortRate(stats.loopSupplyRate(), in, showExact, hiddenRef);
        String totalDisplay = BoardTooltipRenderer.formatPortRate(stats.connectedRate(), in, showExact, hiddenRef);
        String ratedDisplay = BoardTooltipRenderer.formatPortRate(stats.requiredOrProducedRate(), in, showExact, hiddenRef);

        tooltipLines.add(Component.literal("§b[" + Component.translatable("gui.gtcalcboard.tooltip.steady_state_recirculating").getString() + "]"));
        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.external_net_feed").getString() + ": §f" + extDisplay));
        tooltipLines.add(Component.literal(String.format(
                Locale.ROOT,
                "§7%s: §f%s §7(%s %.1f%%)",
                Component.translatable("gui.gtcalcboard.tooltip.internal_recirculation").getString(),
                loopDisplay,
                Component.translatable("gui.gtcalcboard.tooltip.recirculation_ratio").getString(),
                stats.recirculationRatio() * 100.0
        )));
        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.total_recirculation_throughput").getString() + ": §a" + totalDisplay));
        double effPercent = (node != null ? node.getEfficiency() : (stats.connectedRate() / stats.requiredOrProducedRate())) * 100.0;
        tooltipLines.add(Component.literal(String.format(
                Locale.ROOT,
                "§7%s: §e%.1f%% §7(%s: %s)",
                Component.translatable("gui.gtcalcboard.tooltip.machine_effective_duty").getString(),
                effPercent,
                Component.translatable("gui.gtcalcboard.tooltip.rated_capacity").getString(),
                ratedDisplay
        )));
        tooltipLines.add(Component.literal("§8─────────────────────────"));
        double targetCount = node != null && stats.requiredOrProducedRate() > 0.0001
                ? Math.round(node.getMachineCount() * (stats.connectedRate() / stats.requiredOrProducedRate()) * 1000.0) / 1000.0
                : 1.0;
        String countStr = FormatUtil.formatCompactNumber(targetCount);
        tooltipLines.add(Component.translatable("gui.gtcalcboard.tooltip.scale_to_steady_state", countStr));
    }

    private static void appendInputDeficitStats(List<Component> tooltipLines, FlowGraphSolver.PortFlowStats stats, IngredientStack in, boolean showExact, boolean[] hiddenRef, String percentCol, String supDisplay, boolean isBuffered, RecipeNode bufferNode, FlowGraph graph) {
        if (stats.isUnfedDampedLoop()) {
            tooltipLines.add(Component.literal("§6[" + Component.translatable("gui.gtcalcboard.tooltip.damped_loop_unfed_title").getString() + "]"));
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.damped_loop_unfed_desc").getString()));
            tooltipLines.add(Component.literal(String.format(
                    Locale.ROOT,
                    "§7%s: §e%.1f%% §7(%s)",
                    Component.translatable("gui.gtcalcboard.tooltip.recirculation_ratio").getString(),
                    stats.recirculationRatio() * 100.0,
                    Component.translatable("gui.gtcalcboard.tooltip.damped_loop_deficit_note").getString()
            )));
            tooltipLines.add(Component.literal("§e💡 " + Component.translatable("gui.gtcalcboard.tooltip.damped_loop_unfed_hint").getString()));
            tooltipLines.add(Component.literal("§8─────────────────────────"));
        }

        double deficit = stats.requiredOrProducedRate() - stats.connectedRate();
        double defPercent = (1.0 - stats.getRatio()) * 100.0;
        String defDisplay = BoardTooltipRenderer.formatPortRate(deficit, in, showExact, hiddenRef);

        if (isBuffered && bufferNode != null) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.supply").getString() + ": " + percentCol + supDisplay
                    + String.format(Locale.ROOT, " §7(§e%.1f%% %s§7)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.fulfilled").getString())));
            tooltipLines.add(Component.literal("§e⏳ " + Component.translatable("gui.gtcalcboard.tooltip.intermittent_duty_cycle", String.format(Locale.ROOT, "%.1f%%", stats.getPercent())).getString()));
            double chargeTime = bufferNode.getJunctionChargeDuration(graph);
            double batchSize = bufferNode.getJunctionBufferSize();
            String chargeStr = String.format(Locale.ROOT, "%.2fs", chargeTime);
            String batchStr = FormatUtil.formatCompactNumber(batchSize);
            tooltipLines.add(Component.literal("§7   " + Component.translatable("gui.gtcalcboard.tooltip.buffer_charge_info", chargeStr, batchStr).getString()));
        } else {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.supply").getString() + ": " + percentCol + supDisplay
                    + String.format(Locale.ROOT, " §7(§c%.1f%% %s§7)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.fulfilled").getString())));
            tooltipLines.add(Component.literal("§c⚠ " + Component.translatable("gui.gtcalcboard.tooltip.deficit").getString() + ": §c-" + defDisplay
                    + String.format(Locale.ROOT, " §7(-%.1f%%)", defPercent)));
        }
    }

    private static void appendUpstreamThrottledStats(List<Component> tooltipLines, FlowGraphSolver.PortFlowStats stats, IngredientStack in, boolean showExact, boolean[] hiddenRef, String percentCol, String supDisplay) {
        double effRate = stats.effectiveRate();
        String effDisplay = BoardTooltipRenderer.formatPortRate(effRate, in, showExact, hiddenRef);
        double surplus = Math.max(0.0, stats.connectedRate() - effRate);
        String surDisplay = BoardTooltipRenderer.formatPortRate(surplus, in, showExact, hiddenRef);

        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.supply").getString() + ": " + percentCol + supDisplay
                + String.format(Locale.ROOT, " §7(§b%.1f%% %s§7)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.fulfilled").getString())));
        tooltipLines.add(Component.literal("§3↓ " + Component.translatable("gui.gtcalcboard.tooltip.upstream_throttled", effDisplay).getString()));
        if (surplus > 0.0001) {
            tooltipLines.add(Component.literal("§b+ " + Component.translatable("gui.gtcalcboard.tooltip.surplus").getString() + ": §b+" + surDisplay));
        }
    }

    private static void appendInputSurplusStats(List<Component> tooltipLines, FlowGraphSolver.PortFlowStats stats, IngredientStack in, boolean showExact, boolean[] hiddenRef, String percentCol, String supDisplay) {
        double surplus = Math.max(0.0, stats.connectedRate() - stats.requiredOrProducedRate());
        double surPercent = Math.max(0.0, (stats.getRatio() - 1.0) * 100.0);
        String surDisplay = BoardTooltipRenderer.formatPortRate(surplus, in, showExact, hiddenRef);

        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.supply").getString() + ": " + percentCol + supDisplay
                + String.format(Locale.ROOT, " §7(§b+%.1f%% %s§7)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.fulfilled").getString())));
        tooltipLines.add(Component.literal("§b+ " + Component.translatable("gui.gtcalcboard.tooltip.surplus").getString() + ": §b+" + surDisplay
                + String.format(Locale.ROOT, " §7(+%.1f%%)", surPercent)));
    }

    private static void appendOutputPortStats(List<Component> tooltipLines, FlowGraphSolver.PortFlowStats stats, IngredientStack out, boolean showExact, boolean[] hiddenRef) {
        if (!stats.isConnected()) {
            tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.unconnected_final").getString()));
            return;
        }

        String demDisplay = BoardTooltipRenderer.formatPortRate(stats.connectedRate(), out, showExact, hiddenRef);
        String percentCol = stats.isBalanced() ? "§a" : (stats.isOutputSurplus() ? "§b" : "§c");

        if (stats.isBalanced()) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.consumed").getString() + ": " + percentCol + demDisplay + " §7(§a✔ 100%§7)"));
            tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.connected_consumers", String.valueOf(stats.connectionCount())).getString()));
            return;
        }

        if (stats.isOutputSurplus()) {
            double surplus = stats.requiredOrProducedRate() - stats.connectedRate();
            double surPercent = (1.0 - stats.getRatio()) * 100.0;
            String surDisplay = BoardTooltipRenderer.formatPortRate(surplus, out, showExact, hiddenRef);

            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.consumed").getString() + ": " + percentCol + demDisplay
                    + String.format(Locale.ROOT, " §7(§b%.1f%% %s§7)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.consumed_stat").getString())));
            tooltipLines.add(Component.literal("§b+ " + Component.translatable("gui.gtcalcboard.tooltip.surplus").getString() + ": §b+" + surDisplay
                    + String.format(Locale.ROOT, " §7(+%.1f%%)", surPercent)));
        } else {
            double deficit = stats.connectedRate() - stats.requiredOrProducedRate();
            double defPercent = (stats.getRatio() - 1.0) * 100.0;
            String defDisplay = BoardTooltipRenderer.formatPortRate(deficit, out, showExact, hiddenRef);

            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.consumed").getString() + ": " + percentCol + demDisplay
                    + String.format(Locale.ROOT, " §7(§c%.1f%% %s§7)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.demanded_stat").getString())));
            tooltipLines.add(Component.literal("§c⚠ " + Component.translatable("gui.gtcalcboard.tooltip.deficit").getString() + ": §c-" + defDisplay
                    + String.format(Locale.ROOT, " §7(-%.1f%%)", defPercent)));
        }

        tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.connected_consumers", String.valueOf(stats.connectionCount())).getString()));
    }
}
