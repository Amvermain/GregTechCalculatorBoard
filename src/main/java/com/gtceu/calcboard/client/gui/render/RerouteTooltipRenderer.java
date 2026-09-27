package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowBalanceMatrixSolver;
import com.gtceu.calcboard.api.solver.ProductionETACalculator;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.api.model.CrossPageExportTarget;
import com.gtceu.calcboard.api.solver.WorkspaceFlowCoordinator;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class RerouteTooltipRenderer {

    private RerouteTooltipRenderer() {}

    public static boolean renderRerouteTooltip(GuiGraphics graphics, Font font, BoardScreen screen, NodeWidget widget, FlowGraph graph, int mouseX, int mouseY) {
        if (!widget.getNode().isReroute()) {
            return false;
        }

        List<Component> tooltipLines = new ArrayList<>();
        RecipeNode rNode = widget.getNode();
        IngredientStack rStack = !rNode.getInputs().isEmpty() ? rNode.getInputs().get(0) : null;

        if (rStack == null) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.reroute_junction").getString()));
            BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
            return true;
        }

        boolean hasIncoming = graph != null && graph.getConnections().stream().anyMatch(e -> e.toNodeId().equals(rNode.getId()) && e.inputIndex() == 0);
        double upstreamSupply = calculateRerouteSupply(rNode, graph, hasIncoming);
        double downstreamDemand = graph != null ? FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(graph, rNode, 0, null) : 0.0;
        if (rNode.isFixedDrain()) {
            downstreamDemand += rNode.getExternalDrainRate();
        }
        if (rNode.getAllocatedExportRate() > 0.0) {
            downstreamDemand += rNode.getAllocatedExportRate();
        }
        boolean hasSupply = hasIncoming || rNode.isExternalSupply() || rNode.isInfiniteSupply();
        boolean isInputSource = !hasSupply && !rNode.isVoidSink() && !rNode.isFixedDrain() && downstreamDemand > 0.0001;

        appendRerouteHeader(rNode, rStack, isInputSource, tooltipLines);
        appendRerouteBatchBuffer(rNode, rStack, graph, tooltipLines);
        appendRerouteFlowRates(rNode, rStack, graph, upstreamSupply, downstreamDemand, hasSupply, isInputSource, tooltipLines);
        appendRerouteTargetBatch(rNode, rStack, graph, upstreamSupply, downstreamDemand, isInputSource, tooltipLines);
        appendCrossPageDetails(rNode, graph, tooltipLines);

        BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
        return true;
    }

    public static double calculateRerouteSupply(RecipeNode rNode, FlowGraph graph, boolean hasIncoming) {
        return WorkspaceFlowCoordinator.calculateJunctionProduction(rNode, graph);
    }

    private static void appendRerouteHeader(RecipeNode rNode, IngredientStack rStack, boolean isInputSource, List<Component> tooltipLines) {
        if (!rNode.hasTargetBatch()) {
            tooltipLines.add(Component.literal("§6↔ §f" + rStack.getDisplayName() + " §7(" + Component.translatable("gui.gtcalcboard.tooltip.reroute_junction").getString() + ")"));
            return;
        }
        String titleKey = isInputSource ? "gui.gtcalcboard.dt.tooltip.title" : "gui.gtcalcboard.eta.tooltip.title";
        String icon = isInputSource ? "§b«" : "§6⌖";
        tooltipLines.add(Component.literal(icon + " §f" + Component.translatable(titleKey).getString() + " §7(" + rStack.getDisplayName() + "§7)"));
    }

    private static void appendRerouteBatchBuffer(RecipeNode rNode, IngredientStack rStack, FlowGraph graph, List<Component> tooltipLines) {
        if (!rNode.isJunctionBuffer()) return;
        double bufSize = rNode.getJunctionBufferSize();
        double chargeSec = rNode.getJunctionChargeDuration(graph);
        String sizeStr = FormatUtil.formatBatchAmount(bufSize, rStack != null && rStack.isFluid());
        tooltipLines.add(Component.literal("§d📦 " + Component.translatable("gui.gtcalcboard.junction.mode_buffer").getString() + ": §f" + sizeStr));
        if (chargeSec > 0.0) {
            String durStr = String.format("%.2fs", chargeSec);
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.junction.charge_duration", "§e" + durStr).getString()));
        }
    }

    private static void appendRerouteFlowRates(RecipeNode rNode, IngredientStack rStack, FlowGraph graph, double upstreamSupply, double downstreamDemand, boolean hasSupply, boolean isInputSource, List<Component> tooltipLines) {
        if (rNode.isFixedDrain()) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.junction.supply_mode.fixed_drain").getString() + ": §c-" + FormatUtil.formatRate(rNode.getExternalDrainRate(), rStack)));
        }
        if (rNode.getAllocatedExportRate() > 0.0) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.junction.tooltip.cross_page_export").getString() + ": §c-" + FormatUtil.formatRate(rNode.getAllocatedExportRate(), rStack)));
        }
        if (hasSupply) {
            String supStr = Double.isInfinite(upstreamSupply) ? "∞" : ("+" + FormatUtil.formatRate(upstreamSupply, rStack));
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.supply").getString() + ": §a" + supStr));
        }
        if (downstreamDemand > 0.0001 && !rNode.isFixedDrain()) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.demand").getString() + ": §c-" + FormatUtil.formatRate(downstreamDemand, rStack)));
        }

        double netSurplus = upstreamSupply - downstreamDemand;
        if (hasSupply && downstreamDemand > 0.0001) {
            appendBalancedRateLine(upstreamSupply, netSurplus, rStack, tooltipLines);
            return;
        }
        if (isInputSource) {
            double actualDrainRate = ProductionETACalculator.calculateNetOutflowRate(graph, rNode);
            double displayDrain = actualDrainRate > 0.0001 ? actualDrainRate : downstreamDemand;
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.dt.tooltip.rate").getString() + ": §e-" + FormatUtil.formatRate(displayDrain, rStack) + " §7(" + Component.translatable("gui.gtcalcboard.tooltip.unconnected_raw").getString() + ")"));
            return;
        }
        if (hasSupply) {
            String supStr = Double.isInfinite(upstreamSupply) ? "∞" : ("+" + FormatUtil.formatRate(upstreamSupply, rStack));
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.rate").getString() + ": §a" + supStr));
            return;
        }
        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.rate").getString() + ": §70/s"));
    }

    private static void appendBalancedRateLine(double upstreamSupply, double netSurplus, IngredientStack rStack, List<Component> tooltipLines) {
        if (Double.isInfinite(upstreamSupply)) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.rate").getString() + ": §b+∞ §7(" + Component.translatable("gui.gtcalcboard.tooltip.surplus").getString() + ")"));
            return;
        }
        if (Math.abs(netSurplus) < 0.0001) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.rate").getString() + ": §a✔ " + Component.translatable("gui.gtcalcboard.tooltip.supply").getString() + " = " + Component.translatable("gui.gtcalcboard.tooltip.demand").getString()));
            return;
        }
        if (netSurplus > 0) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.rate").getString() + ": §b+" + FormatUtil.formatRate(netSurplus, rStack) + " §7(" + Component.translatable("gui.gtcalcboard.tooltip.surplus").getString() + ")"));
            return;
        }
        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.rate").getString() + ": §c-" + FormatUtil.formatRate(-netSurplus, rStack) + " §7(" + Component.translatable("gui.gtcalcboard.tooltip.deficit").getString() + ")"));
    }

    private static void appendRerouteTargetBatch(RecipeNode rNode, IngredientStack rStack, FlowGraph graph, double upstreamSupply, double downstreamDemand, boolean isInputSource, List<Component> tooltipLines) {
        if (!rNode.hasTargetBatch()) {
            tooltipLines.add(Component.literal("§8§m------------------------"));
            tooltipLines.add(Component.literal("§e" + Component.translatable("gui.gtcalcboard.eta.tooltip.click_set").getString()));
            return;
        }

        double targetAmount = rNode.getTargetBatchAmount();
        String goalStr = FormatUtil.formatBatchAmount(targetAmount, rStack.isFluid());
        String targetKey = isInputSource ? "gui.gtcalcboard.dt.tooltip.target" : "gui.gtcalcboard.eta.tooltip.target";
        tooltipLines.add(Component.literal("§7" + Component.translatable(targetKey).getString() + ": §e" + goalStr));

        if (isInputSource) {
            appendRerouteDepletionTime(graph, rNode, targetAmount, tooltipLines);
        } else {
            appendRerouteEstimatedTime(graph, rNode, targetAmount, upstreamSupply, downstreamDemand, tooltipLines);
        }

        tooltipLines.add(Component.literal("§8§m------------------------"));
        tooltipLines.add(Component.literal("§e" + Component.translatable("gui.gtcalcboard.eta.tooltip.click_edit").getString()));
        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.shift_reset").getString()));
    }

    private static void appendRerouteDepletionTime(FlowGraph graph, RecipeNode rNode, double targetAmount, List<Component> tooltipLines) {
        double drainRate = ProductionETACalculator.calculateNetOutflowRate(graph, rNode);
        double depletionSec = ProductionETACalculator.calculateDepletionTime(graph, rNode, targetAmount, drainRate);
        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.dt.tooltip.duration").getString() + ": §b" + FormatUtil.formatETA(depletionSec)));
    }

    private static void appendRerouteEstimatedTime(FlowGraph graph, RecipeNode rNode, double targetAmount, double upstreamSupply, double downstreamDemand, List<Component> tooltipLines) {
        double netSurplus = upstreamSupply - downstreamDemand;
        double effectiveRate = upstreamSupply > 0.0001 ? (netSurplus > 0.0001 ? netSurplus : upstreamSupply) : 0.0;
        double etaSec = ProductionETACalculator.calculateETA(graph, rNode, targetAmount, effectiveRate);
        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.duration").getString() + ": §b" + FormatUtil.formatETA(etaSec)));

        double totalEU = ProductionETACalculator.calculateTotalEnergyForBatch(graph, rNode, targetAmount);
        if (totalEU > 0) {
            tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.eta.tooltip.total_energy").getString() + ": §e" + FormatUtil.formatCompactNumber(totalEU) + " EU"));
        }
    }

    private static void appendCrossPageDetails(RecipeNode rNode, FlowGraph graph, List<Component> tooltipLines) {
        if (rNode.isLinkedJunction()) {
            appendLinkedSourceDetails(rNode, graph, tooltipLines);
        }
        if (!rNode.getExportTargets().isEmpty()) {
            appendExportTargetsDetails(rNode, tooltipLines);
        }
    }

    private static void appendLinkedSourceDetails(RecipeNode rNode, FlowGraph graph, List<Component> tooltipLines) {
        String srcPageId = rNode.getLinkedSourcePageId();
        String srcNodeId = rNode.getLinkedSourceNodeId();
        BoardPage srcPage = (srcPageId != null && !srcPageId.isEmpty()) ? BoardManager.getInstance().getPage(srcPageId).orElse(null) : null;
        RecipeNode srcNode = (srcPage != null && srcNodeId != null) ? srcPage.getGraph().findNodeById(srcNodeId) : null;

        if (srcPage == null || srcNode == null) {
            tooltipLines.add(Component.literal("§c\u26A0 " + Component.translatable("gui.gtcalcboard.junction.badge_broken_link").getString()));
        } else {
            String pageName = srcPage.getName() != null && !srcPage.getName().isEmpty() ? srcPage.getName() : srcPageId;
            String nodeName = srcNode.getName() != null && !srcNode.getName().isEmpty() ? srcNode.getName() : srcNodeId;
            tooltipLines.add(Component.literal("§b\uD83D\uDD17 " + Component.translatable("gui.gtcalcboard.junction.source_page_label").getString() + ": §f" + pageName));
            tooltipLines.add(Component.literal("§7   " + Component.translatable("gui.gtcalcboard.junction.source_junction_label").getString() + ": §f" + nodeName));
            int reqPri = WorkspaceFlowCoordinator.getOutgoingMaxPriority(graph, rNode);
            if (reqPri > 0) {
                tooltipLines.add(Component.literal("§7   " + Component.translatable("gui.gtcalcboard.junction.priority_label").getString() + ": §e[P" + reqPri + "]"));
            }
            appendSourceMetricsLines(srcPage, srcNode, rNode, tooltipLines);
            tooltipLines.add(Component.literal("§e   " + Component.translatable("gui.gtcalcboard.junction.click_to_jump").getString()));
        }

        WorkspaceFlowCoordinator.WorkspaceFlowResult flowResult = WorkspaceFlowCoordinator.getLastResult();
        if (flowResult != null && flowResult.isCircular(rNode.getId())) {
            tooltipLines.add(Component.literal("§d\u26A0 " + Component.translatable("gui.gtcalcboard.junction.badge_circular_loop").getString()));
        }
    }

    private static void appendSourceMetricsLines(BoardPage srcPage, RecipeNode srcNode, RecipeNode rNode, List<Component> tooltipLines) {
        WorkspaceFlowCoordinator.SourceJunctionMetrics metrics = WorkspaceFlowCoordinator.calculateSourceJunctionMetrics(srcPage, srcNode);
        IngredientStack rStack = srcNode.getRerouteIngredient() != null ? srcNode.getRerouteIngredient() : rNode.getRerouteIngredient();

        String prodStr = Double.isInfinite(metrics.totalProduction()) ? "∞" : ("+" + FormatUtil.formatRate(metrics.totalProduction(), rStack));
        String usageStr = "-" + FormatUtil.formatRate(metrics.totalUsage(), rStack);
        String surplusStr = formatSurplusString(metrics.availableSurplus(), rStack);

        tooltipLines.add(Component.literal("§7   ├ " + Component.translatable("gui.gtcalcboard.junction.source_total_production", "§a" + prodStr).getString()));
        tooltipLines.add(Component.literal("§7   ├ " + Component.translatable("gui.gtcalcboard.junction.source_total_usage", "§c" + usageStr).getString()));
        tooltipLines.add(Component.literal("§7   └ " + Component.translatable("gui.gtcalcboard.junction.source_available_surplus", surplusStr).getString()));
    }

    private static String formatSurplusString(double surplus, IngredientStack rStack) {
        if (Double.isInfinite(surplus)) {
            return "§b+∞";
        }
        if (surplus > 0.0001) {
            return "§a+" + FormatUtil.formatRate(surplus, rStack);
        }
        return "§70/s";
    }

    private static void appendExportTargetsDetails(RecipeNode rNode, List<Component> tooltipLines) {
        tooltipLines.add(Component.literal("§6── " + Component.translatable("gui.gtcalcboard.junction.export_targets_header").getString() + " ──"));
        for (CrossPageExportTarget target : rNode.getExportTargets()) {
            BoardPage targetPage = BoardManager.getInstance().getPage(target.targetPageId()).orElse(null);
            String pName = targetPage != null && targetPage.getName() != null ? targetPage.getName() : target.targetPageId();
            String capStr = target.fixedLimit() > 0 ? String.format(java.util.Locale.ROOT, " (Cap: %.1f)", target.fixedLimit()) : "";
            tooltipLines.add(Component.literal("§7→ §f" + pName + " §e[P" + target.priority() + "]" + capStr));
        }
    }
}
