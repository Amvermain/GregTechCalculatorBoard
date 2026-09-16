package com.gtceu.calcboard.client.gui.inspector;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.BoundaryPinNode;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.client.gui.widget.NodeInspectorPanel;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

public class BoundaryPinInspector implements INodeSubInspector {

    private final IBoardScreenContext screen;
    private NodeWidget targetWidget;
    private Component pendingTooltip;

    public BoundaryPinInspector(IBoardScreenContext screen) {
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
        RecipeNode pin = targetWidget.getNode();
        renderBoundaryPinHeader(graphics, font, px, py, pin, mouseX, mouseY);

        int curY = py + 28;
        int x = px + 8;
        int contentW = NodeInspectorPanel.PANEL_WIDTH - 16;

        renderBoundaryPinDirectionSection(graphics, font, x, curY, contentW, pin);
        curY += 36;

        renderBoundaryPinFlipButton(graphics, font, x, curY, contentW, mouseX, mouseY);
        curY += 40;

        renderBoundaryPinIngredientSection(graphics, font, x, curY, contentW, pin);
        curY += 40;

        renderBoundaryPinFlowSection(graphics, font, x, curY, contentW, pin);
        curY += 56;

        renderBoundaryPinRenameButton(graphics, font, x, curY, contentW, mouseX, mouseY);
    }

    private void renderBoundaryPinHeader(GuiGraphics graphics, Font font, int px, int py, RecipeNode pin, int mouseX, int mouseY) {
        graphics.fill(px, py, px + NodeInspectorPanel.PANEL_WIDTH, py + 22, 0xFF1E293B);
        graphics.renderOutline(px, py, NodeInspectorPanel.PANEL_WIDTH, 22, 0xFF475569);

        int titleX = px + 6;
        IngredientStack bound = pin.asBoundaryPin().getBoundIngredient();
        if (bound != null) {
            IngredientRenderer.render(graphics, bound, px + 4, py + 3);
            titleX = px + 24;
        }
        String title = pin.asBoundaryPin().getPinLabel().isEmpty() ? pin.getName() : pin.asBoundaryPin().getPinLabel();
        graphics.drawString(font, font.plainSubstrByWidth(title, NodeInspectorPanel.PANEL_WIDTH - 44), titleX, py + 7, 0xFFE2E8F0, false);

        int closeX = px + NodeInspectorPanel.PANEL_WIDTH - 16;
        int closeY = py + 5;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        graphics.drawString(font, "✕", closeX + 1, closeY + 1, closeHov ? 0xFFEF4444 : 0xFF94A3B8, false);
    }

    private void renderBoundaryPinDirectionSection(GuiGraphics graphics, Font font, int x, int y, int w, RecipeNode pin) {
        boolean isInput = pin.asBoundaryPin().getDirection() == BoundaryPinNode.PinDirection.INPUT;
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.boundary_pin.label").getString(), x, y, 0xFF94A3B8, false);
        int boxY = y + 12;
        graphics.fill(x, boxY, x + w, boxY + 20, isInput ? 0xFF042F2E : 0xFF331B05);
        graphics.renderOutline(x, boxY, w, 20, isInput ? 0xFF0D9488 : 0xFFD97706);

        String badge = isInput ? "» IN" : "« OUT";
        String dirText = Component.translatable(isInput ? "gui.gtcalcboard.boundary_pin.input" : "gui.gtcalcboard.boundary_pin.output").getString();
        graphics.drawString(font, badge + " - " + dirText, x + 6, boxY + 6, isInput ? 0xFF5EEAD4 : 0xFFFCD34D, false);
    }

    private void renderBoundaryPinFlipButton(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 32;
        graphics.fill(x, y, x + w, y + 32, hov ? 0xFF1E293B : 0xFF0F172A);
        graphics.renderOutline(x, y, w, 32, hov ? 0xFF38BDF8 : 0xFF334155);

        String title = "⇄ " + Component.translatable("gui.gtcalcboard.menu.flip_node").getString();
        graphics.drawString(font, font.plainSubstrByWidth(title, w - 20), x + 6, y + 6, 0xFF38BDF8, false);
        String sub = Component.translatable("gui.gtcalcboard.boundary_pin.toggle_side").getString();
        graphics.drawString(font, font.plainSubstrByWidth(sub, w - 20), x + 6, y + 18, 0xFF94A3B8, false);
        graphics.drawString(font, "»", x + w - 12, y + 11, hov ? 0xFF38BDF8 : 0xFF64748B, false);
    }

    private void renderBoundaryPinIngredientSection(GuiGraphics graphics, Font font, int x, int y, int w, RecipeNode pin) {
        IngredientStack bound = pin.asBoundaryPin().getBoundIngredient();
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.boundary_pin.bound_ingredient").getString(), x, y, 0xFF94A3B8, false);
        int boxY = y + 12;
        graphics.fill(x, boxY, x + w, boxY + 24, 0xFF0F172A);
        graphics.renderOutline(x, boxY, w, 24, 0xFF334155);

        if (bound != null) {
            IngredientRenderer.render(graphics, bound, x + 4, boxY + 4);
            graphics.drawString(font, font.plainSubstrByWidth(bound.getDisplayName(), w - 26), x + 24, boxY + 8, 0xFFE2E8F0, false);
        } else {
            String unbound = Component.translatable("gui.gtcalcboard.junction.no_bound_ingredient").getString();
            graphics.drawString(font, unbound, x + 6, boxY + 8, 0xFF64748B, false);
        }
    }

    private void renderBoundaryPinFlowSection(GuiGraphics graphics, Font font, int x, int y, int w, RecipeNode pin) {
        graphics.fill(x, y, x + w, y + 48, 0xFF0B1120);
        graphics.renderOutline(x, y, w, 48, 0xFF1E293B);

        boolean isInput = pin.asBoundaryPin().getDirection() == BoundaryPinNode.PinDirection.INPUT;
        IngredientStack bound = pin.asBoundaryPin().getBoundIngredient();
        var graph = screen != null ? screen.getGraph() : null;
        var stats = graph != null
                ? (isInput ? graph.getOutputPortStats(pin, 0) : graph.getInputPortStats(pin, 0))
                : null;

        double ratedAmount = bound != null ? bound.getAmount() : 0.0;
        double connectedAmount = (stats != null && stats.isConnected()) ? stats.connectedRate() : 0.0;

        String ratedLabel = isInput ? Component.translatable("gui.gtcalcboard.tooltip.supply").getString() : Component.translatable("gui.gtcalcboard.tooltip.demand").getString();
        String ratedVal = (isInput ? "+" : "-") + FormatUtil.formatRate(ratedAmount, bound);
        graphics.drawString(font, ratedLabel, x + 6, y + 6, 0xFF64748B, false);
        graphics.drawString(font, ratedVal, x + w - font.width(ratedVal) - 6, y + 6, isInput ? 0xFF10B981 : 0xFFF59E0B, false);

        String flowLabel = isInput ? Component.translatable("gui.gtcalcboard.inspector.outflow_rate").getString() : Component.translatable("gui.gtcalcboard.inspector.inflow_rate").getString();
        String flowVal = FormatUtil.formatRate(connectedAmount, bound);
        int flowCol = (stats != null && stats.isConnected() && stats.isBalanced()) ? 0xFF10B981 : 0xFF38BDF8;
        graphics.drawString(font, flowLabel, x + 6, y + 20, 0xFF64748B, false);
        graphics.drawString(font, flowVal, x + w - font.width(flowVal) - 6, y + 20, flowCol, false);

        String statLabel = Component.translatable("gui.gtcalcboard.boundary_pin.status").getString();
        String statVal = (stats != null && stats.isConnected())
                ? (stats.isBalanced() ? Component.translatable("gui.gtcalcboard.boundary_pin.status_balanced").getString() : String.format(Locale.ROOT, "Flow: %.1f%%", stats.getPercent()))
                : Component.translatable("gui.gtcalcboard.boundary_pin.status_unconnected").getString();
        int statCol = (stats != null && stats.isConnected()) ? (stats.isBalanced() ? 0xFF10B981 : 0xFFF59E0B) : 0xFF64748B;
        graphics.drawString(font, statLabel, x + 6, y + 34, 0xFF64748B, false);
        graphics.drawString(font, statVal, x + w - font.width(statVal) - 6, y + 34, statCol, false);
    }

    private void renderBoundaryPinRenameButton(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 32;
        graphics.fill(x, y, x + w, y + 32, hov ? 0xFF1E293B : 0xFF0F172A);
        graphics.renderOutline(x, y, w, 32, hov ? 0xFF38BDF8 : 0xFF334155);

        String title = "✎ " + Component.translatable("gui.gtcalcboard.menu.rename_pin").getString();
        graphics.drawString(font, font.plainSubstrByWidth(title, w - 20), x + 6, y + 6, 0xFF38BDF8, false);
        String sub = Component.translatable("gui.gtcalcboard.boundary_pin.rename_hint").getString();
        graphics.drawString(font, font.plainSubstrByWidth(sub, w - 20), x + 6, y + 18, 0xFF94A3B8, false);
        graphics.drawString(font, "»", x + w - 12, y + 11, hov ? 0xFF38BDF8 : 0xFF64748B, false);
    }

    @Override
    public boolean mouseClicked(int px, int py, double mouseX, double mouseY, int button) {
        if (button != 0 || targetWidget == null || targetWidget.getNode() == null) {
            return false;
        }
        RecipeNode pin = targetWidget.getNode();
        int contentW = NodeInspectorPanel.PANEL_WIDTH - 16;
        int x = px + 8;

        int flipY = py + 28 + 36;
        if (mouseX >= x && mouseX <= x + contentW && mouseY >= flipY && mouseY <= flipY + 32) {
            boolean oldFlipped = pin.isFlipped();
            boolean newFlipped = !oldFlipped;
            pin.setFlipped(newFlipped);
            if (screen != null) {
                screen.recordCommand(new BoardCommand.FlipNodesCommand(pin, oldFlipped, newFlipped));
                if (screen.getGraph() != null) {
                    screen.getGraph().cleanupInvalidConnections();
                }
                screen.markSummaryDirty();
            }
            targetWidget.invalidateCache();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        int renameY = flipY + 40 + 40 + 56;
        if (mouseX >= x && mouseX <= x + contentW && mouseY >= renameY && mouseY <= renameY + 32) {
            if (targetWidget.getNameEditor() != null) {
                targetWidget.getNameEditor().startEditing();
            }
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        return true;
    }
}
