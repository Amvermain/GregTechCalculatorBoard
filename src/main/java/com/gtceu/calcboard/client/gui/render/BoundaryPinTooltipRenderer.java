package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.model.BoundaryPinNode;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class BoundaryPinTooltipRenderer {

    private BoundaryPinTooltipRenderer() {}

    public static boolean renderBoundaryPinTooltip(
            GuiGraphics graphics,
            Font font,
            BoardScreen screen,
            NodeWidget widget,
            FlowGraph graph,
            int mouseX,
            int mouseY
    ) {
        if (!(widget.getNode() instanceof BoundaryPinNode pin)) {
            return false;
        }

        boolean isInput = pin.getDirection() == BoundaryPinNode.PinDirection.INPUT;
        IngredientStack bound = resolveBoundIngredient(pin, isInput);

        List<Component> tooltipLines = new ArrayList<>();
        appendHeader(tooltipLines, pin, isInput);
        appendIngredientDetails(tooltipLines, pin, bound, isInput, graph);
        appendInteractionHints(tooltipLines);
        appendDebugInfo(tooltipLines, pin, bound);

        BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
        return true;
    }

    private static IngredientStack resolveBoundIngredient(BoundaryPinNode pin, boolean isInput) {
        IngredientStack bound = pin.getBoundIngredient();
        if (bound != null) {
            return bound;
        }
        if (isInput && !pin.getOutputs().isEmpty()) {
            return pin.getOutputs().get(0);
        }
        if (!isInput && !pin.getInputs().isEmpty()) {
            return pin.getInputs().get(0);
        }
        return null;
    }

    private static void appendHeader(List<Component> lines, BoundaryPinNode pin, boolean isInput) {
        String typeKey = isInput ? "gui.gtcalcboard.boundary_pin.input" : "gui.gtcalcboard.boundary_pin.output";
        String dirBadge = isInput ? "§b[» IN]" : "§6[« OUT]";
        String pinTitle = pin.getPinLabel().isEmpty() ? pin.getName() : pin.getPinLabel();
        lines.add(Component.literal(dirBadge + " §f" + pinTitle + " §7(" + Component.translatable(typeKey).getString() + ")"));
    }

    private static void appendIngredientDetails(List<Component> lines, BoundaryPinNode pin, IngredientStack bound, boolean isInput, FlowGraph graph) {
        if (bound == null) {
            return;
        }
        String icon = bound.isFluid() ? "§9◆ " : "§e■ ";
        lines.add(Component.literal(icon + "§f" + bound.getDisplayName()));

        double rate = bound.getAmount();
        String rateStr = FormatUtil.formatRate(rate, bound);
        if (isInput) {
            lines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.supply").getString() + ": §a+" + rateStr));
        } else {
            lines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.demand").getString() + ": §c-" + rateStr));
        }

        appendConnectionStats(lines, pin, bound, isInput, graph);
    }

    private static void appendConnectionStats(List<Component> lines, BoundaryPinNode pin, IngredientStack bound, boolean isInput, FlowGraph graph) {
        if (graph == null) {
            return;
        }
        com.gtceu.calcboard.api.solver.FlowGraphSolver.PortFlowStats stats = isInput
                ? graph.getOutputPortStats(pin, 0)
                : graph.getInputPortStats(pin, 0);
        if (stats == null || !stats.isConnected()) {
            String unconnKey = isInput ? "gui.gtcalcboard.tooltip.unconnected_final" : "gui.gtcalcboard.tooltip.unconnected_raw";
            lines.add(Component.literal("§8" + Component.translatable(unconnKey).getString()));
            return;
        }

        String connStr = FormatUtil.formatRate(stats.connectedRate(), bound);
        String percentCol = stats.isBalanced() ? "§a" : "§e";
        String statusBadge = stats.isBalanced() ? "§7(§a✔ 100%§7)" : "§7(" + percentCol + String.format(java.util.Locale.ROOT, "%.1f%%", stats.getPercent()) + "§7)";
        String statLabelKey = isInput ? "gui.gtcalcboard.tooltip.demand" : "gui.gtcalcboard.tooltip.supply";
        lines.add(Component.literal("§7" + Component.translatable(statLabelKey).getString() + ": " + percentCol + connStr + " " + statusBadge));

        String connCountKey = isInput ? "gui.gtcalcboard.tooltip.connected_consumers" : "gui.gtcalcboard.tooltip.connected_producers";
        lines.add(Component.literal("§8" + Component.translatable(connCountKey, String.valueOf(stats.connectionCount())).getString()));
    }

    private static void appendInteractionHints(List<Component> lines) {
        lines.add(Component.literal("§8§m------------------------"));
        lines.add(Component.literal("§7[Double-Click]: §f" + Component.translatable("gui.gtcalcboard.boundary_pin.rename_hint").getString()));
        lines.add(Component.literal("§7[Drag Port]: §f" + Component.translatable("gui.gtcalcboard.tooltip.drag_connect").getString()));
        lines.add(Component.literal("§c[✕]: §7" + Component.translatable("gui.gtcalcboard.tooltip.remove_node").getString()));
    }

    private static void appendDebugInfo(List<Component> lines, BoundaryPinNode pin, IngredientStack bound) {
        if (!BoardManager.getInstance().isShowDebugInfo()) {
            return;
        }
        lines.add(Component.literal("§8§m------------------------"));
        lines.add(Component.literal("§7[Debug] §8Pin ID: §7" + pin.getId()));
        lines.add(Component.literal("§7[Debug] §8Direction: §e" + pin.getDirection()));
        if (bound != null && bound.getId() != null) {
            lines.add(Component.literal("§7[Debug] §8Bound ID: §b" + bound.getId()));
        }
    }
}
