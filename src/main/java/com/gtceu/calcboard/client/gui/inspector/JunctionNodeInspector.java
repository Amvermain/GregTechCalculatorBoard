package com.gtceu.calcboard.client.gui.inspector;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.ProductionETACalculator;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.client.gui.widget.NodeInspectorPanel;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class JunctionNodeInspector implements INodeSubInspector {

    private final IBoardScreenContext screen;
    private NodeWidget targetWidget;
    private Component pendingTooltip;

    public JunctionNodeInspector(IBoardScreenContext screen) {
        this.screen = screen;
    }

    @Override
    public void bind(NodeWidget targetWidget) {
        this.targetWidget = targetWidget;
    }

    public NodeWidget getTargetWidget() {
        return targetWidget;
    }

    @Override
    public int getContentHeight() {
        return 240;
    }

    @Override
    public Component getPendingTooltip() {
        return pendingTooltip;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int px, int py, int ph, int mouseX, int mouseY) {
        this.pendingTooltip = null;
        if (targetWidget == null || targetWidget.getNode() == null) {
            return;
        }
        RecipeNode node = targetWidget.getNode();
        renderJunctionHeader(graphics, font, px, py, node, mouseX, mouseY);

        int curY = py + 28;
        int x = px + 8;
        int contentW = NodeInspectorPanel.PANEL_WIDTH - 16;

        renderJunctionSupplySection(graphics, font, x, curY, contentW, node);
        curY += 36;

        renderJunctionBufferSection(graphics, font, x, curY, contentW, node);
        curY += 36;

        renderJunctionBatchSection(graphics, font, x, curY, contentW, node);
        curY += 40;

        renderJunctionConfigButton(graphics, font, x, curY, contentW, mouseX, mouseY);
        curY += 44;

        renderJunctionFlowStats(graphics, font, x, curY, contentW, node);
    }

    private void renderJunctionHeader(GuiGraphics graphics, Font font, int px, int py, RecipeNode node, int mouseX, int mouseY) {
        graphics.fill(px, py, px + NodeInspectorPanel.PANEL_WIDTH, py + 22, 0xFF1E293B);
        graphics.renderOutline(px, py, NodeInspectorPanel.PANEL_WIDTH, 22, 0xFF475569);

        int titleX = px + 6;
        var boundStack = node.getRerouteIngredient();
        if (boundStack != null) {
            IngredientRenderer.render(graphics, boundStack, px + 4, py + 3);
            titleX = px + 24;
        }
        String title = boundStack != null ? boundStack.getDisplayName() : Component.translatable("gui.gtcalcboard.inspector.junction_title").getString();
        graphics.drawString(font, font.plainSubstrByWidth(title, NodeInspectorPanel.PANEL_WIDTH - 44), titleX, py + 7, 0xFFE2E8F0, false);

        int closeX = px + NodeInspectorPanel.PANEL_WIDTH - 16;
        int closeY = py + 5;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        graphics.drawString(font, "✕", closeX + 1, closeY + 1, closeHov ? 0xFFEF4444 : 0xFF94A3B8, false);
    }

    private void renderJunctionSupplySection(GuiGraphics graphics, Font font, int x, int y, int w, RecipeNode node) {
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.inspector.supply_mode").getString(), x, y, 0xFF94A3B8, false);
        int boxY = y + 12;
        graphics.fill(x, boxY, x + w, boxY + 20, 0xFF0F172A);
        graphics.renderOutline(x, boxY, w, 20, 0xFF334155);

        var mode = node.getSupplyMode();
        String label = Component.translatable(mode.getTranslationKey()).getString();
        var boundStack = node.getRerouteIngredient();
        int col = switch (mode) {
            case INFINITE -> 0xFF38BDF8;
            case FIXED_RATE -> {
                label += " (+" + FormatUtil.formatRate(node.getExternalSupplyRate(), boundStack) + ")";
                yield 0xFFFBBF24;
            }
            case FIXED_DRAIN -> {
                label += " (-" + FormatUtil.formatRate(node.getExternalDrainRate(), boundStack) + ")";
                yield 0xFFF97316;
            }
            case VOID_SINK -> 0xFFF87171;
            default -> 0xFFCBD5E1;
        };
        graphics.drawString(font, font.plainSubstrByWidth(label, w - 8), x + 5, boxY + 6, col, false);
    }

    private void renderJunctionBufferSection(GuiGraphics graphics, Font font, int x, int y, int w, RecipeNode node) {
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.inspector.buffer_mode").getString(), x, y, 0xFF94A3B8, false);
        int boxY = y + 12;
        graphics.fill(x, boxY, x + w, boxY + 20, 0xFF0F172A);
        graphics.renderOutline(x, boxY, w, 20, 0xFF334155);

        boolean isBuffer = node.isJunctionBuffer();
        var boundStack = node.getRerouteIngredient();
        String label = isBuffer
                ? Component.translatable("gui.gtcalcboard.junction.mode_buffer").getString() + " (" + FormatUtil.formatBatchAmount(node.getJunctionBufferSize(), boundStack != null && boundStack.isFluid()) + ")"
                : Component.translatable("gui.gtcalcboard.junction.mode_passthrough").getString();
        int col = isBuffer ? 0xFF38BDF8 : 0xFF94A3B8;
        graphics.drawString(font, font.plainSubstrByWidth(label, w - 8), x + 5, boxY + 6, col, false);
    }

    private void renderJunctionBatchSection(GuiGraphics graphics, Font font, int x, int y, int w, RecipeNode node) {
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.inspector.target_batch").getString(), x, y, 0xFF94A3B8, false);
        int boxY = y + 12;
        graphics.fill(x, boxY, x + w, boxY + 24, 0xFF0F172A);
        graphics.renderOutline(x, boxY, w, 24, 0xFF334155);

        double targetAmount = node.getTargetBatchAmount();
        var boundStack = node.getRerouteIngredient();
        boolean isFluid = boundStack != null && boundStack.isFluid();

        if (targetAmount <= 0.0) {
            String noGoal = Component.translatable("gui.gtcalcboard.inspector.no_target_batch").getString();
            graphics.drawString(font, noGoal, x + 6, boxY + 8, 0xFF64748B, false);
            return;
        }

        String amountStr = FormatUtil.formatBatchAmount(targetAmount, isFluid);
        graphics.drawString(font, amountStr, x + 6, boxY + 8, 0xFFFCD34D, false);

        var graph = screen != null ? screen.getGraph() : null;
        if (graph != null) {
            boolean isInput = NodeCardRenderer.isInputSourceJunction(graph, node);
            String timeBadge = isInput ? computeJunctionDepletionBadge(graph, node, targetAmount) : computeJunctionEtaBadge(graph, node, targetAmount);
            int timeCol = isInput ? 0xFF7DD3FC : 0xFF86EFAC;
            int badgeW = font.width(timeBadge);
            graphics.drawString(font, timeBadge, x + w - badgeW - 6, boxY + 8, timeCol, false);
        }
    }

    private String computeJunctionDepletionBadge(FlowGraph graph, RecipeNode node, double targetAmount) {
        double drainRate = ProductionETACalculator.calculateNetOutflowRate(graph, node);
        double dtSec = ProductionETACalculator.calculateDepletionTime(graph, node, targetAmount, drainRate);
        return "DT: " + FormatUtil.formatETA(dtSec);
    }

    private String computeJunctionEtaBadge(FlowGraph graph, RecipeNode node, double targetAmount) {
        double netRate = ProductionETACalculator.calculateNetInflowRate(graph, node, 0);
        double etaSec = ProductionETACalculator.calculateETA(graph, node, targetAmount, netRate);
        return "ET: " + FormatUtil.formatETA(etaSec);
    }

    private void renderJunctionConfigButton(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 36;
        graphics.fill(x, y, x + w, y + 36, hov ? 0xFF1E293B : 0xFF0F172A);
        graphics.renderOutline(x, y, w, 36, hov ? 0xFF38BDF8 : 0xFF334155);

        String btnTitle = "⚙ " + Component.translatable("gui.gtcalcboard.inspector.configure_junction_btn").getString();
        graphics.drawString(font, font.plainSubstrByWidth(btnTitle, w - 20), x + 6, y + 6, 0xFF38BDF8, false);

        String sub = Component.translatable("gui.gtcalcboard.inspector.configure_junction_sub").getString();
        graphics.drawString(font, font.plainSubstrByWidth(sub, w - 20), x + 6, y + 20, 0xFF94A3B8, false);
        graphics.drawString(font, "»", x + w - 12, y + 13, hov ? 0xFF38BDF8 : 0xFF64748B, false);
    }

    private void renderJunctionFlowStats(GuiGraphics graphics, Font font, int x, int y, int w, RecipeNode node) {
        graphics.fill(x, y, x + w, y + 48, 0xFF0B1120);
        graphics.renderOutline(x, y, w, 48, 0xFF1E293B);

        var graph = screen != null ? screen.getGraph() : null;
        var boundStack = node.getRerouteIngredient();
        double inRate = graph != null ? ProductionETACalculator.calculateNetInflowRate(graph, node, 0) : 0.0;
        double outRate = graph != null ? ProductionETACalculator.calculateNetOutflowRate(graph, node) : 0.0;
        double netRate = inRate - outRate;

        String inLabel = Component.translatable("gui.gtcalcboard.inspector.inflow_rate").getString();
        String inVal = "+" + FormatUtil.formatRate(inRate, boundStack);
        graphics.drawString(font, inLabel, x + 6, y + 6, 0xFF64748B, false);
        graphics.drawString(font, inVal, x + w - font.width(inVal) - 6, y + 6, 0xFF10B981, false);

        String outLabel = Component.translatable("gui.gtcalcboard.inspector.outflow_rate").getString();
        String outVal = "-" + FormatUtil.formatRate(outRate, boundStack);
        graphics.drawString(font, outLabel, x + 6, y + 20, 0xFF64748B, false);
        graphics.drawString(font, outVal, x + w - font.width(outVal) - 6, y + 20, 0xFFF59E0B, false);

        String netLabel = Component.translatable("gui.gtcalcboard.inspector.net_rate").getString();
        String netVal = (netRate >= 0 ? "+" : "") + FormatUtil.formatRate(netRate, boundStack);
        int netCol = Math.abs(netRate) < 1e-4 ? 0xFF94A3B8 : (netRate > 0 ? 0xFF38BDF8 : 0xFFEF4444);
        graphics.drawString(font, netLabel, x + 6, y + 34, 0xFF64748B, false);
        graphics.drawString(font, netVal, x + w - font.width(netVal) - 6, y + 34, netCol, false);
    }

    @Override
    public boolean mouseClicked(int px, int py, double mouseX, double mouseY, int button) {
        if (button != 0 || targetWidget == null || targetWidget.getNode() == null) {
            return false;
        }
        RecipeNode node = targetWidget.getNode();
        int contentW = NodeInspectorPanel.PANEL_WIDTH - 16;
        int x = px + 8;

        int batchY = py + 28 + 36 + 36;
        if (mouseX >= x && mouseX <= x + contentW && mouseY >= batchY + 12 && mouseY <= batchY + 36) {
            if (targetWidget.getTargetBatchEditor() != null) {
                targetWidget.getTargetBatchEditor().startEditing();
            }
            return true;
        }

        int btnY = batchY + 40;
        if (mouseX >= x && mouseX <= x + contentW && mouseY >= btnY && mouseY <= btnY + 36) {
            if (screen != null) {
                screen.openJunctionSupplyDialog(node);
            }
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        return true;
    }
}
