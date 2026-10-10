package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.PoolViewMode;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.client.gui.layout.SubCardLayoutBounds;
import com.gtceu.calcboard.client.gui.layout.SubCardLayoutCalculator;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Renders shared machine pool frames in EMBEDDED_PANEL view mode.
 * Displays a master header, summary bar, vertically stacked compact sub-cards, and inline recipe add slot.
 */
public final class EmbeddedPanelRenderer {

    public static final int BTN_SIZE = 16;
    public static final int BTN_SPACING = 3;

    public record EmbeddedPortHit(
            CanvasGroupFrame frame,
            RecipeNode subNode,
            boolean isInput,
            int portIndex,
            IngredientStack stack
    ) {}

    private EmbeddedPanelRenderer() {}

    public static void renderPanel(
            GuiGraphics graphics,
            Font font,
            FlowGraph graph,
            CanvasGroupFrame frame,
            double mouseX,
            double mouseY,
            boolean isEditing,
            boolean isSelected
    ) {
        if (frame == null) return;
        frame.relayoutEmbeddedCards(graph);

        int x = (int) frame.getPosX();
        int y = (int) frame.getPosY();
        int w = (int) frame.getWidth();
        int h = (int) frame.getHeight();
        int color = frame.getColor();
        boolean hasWarning = frame.hasOperationalWarnings(graph);

        graphics.fill(x, y, x + w, y + h, hasWarning ? 0xEE1E1418 : 0xEE14171E);
        int borderCol = isSelected ? 0xFF00FFFF : (hasWarning ? 0xFFE53935 : ((color & 0x00FFFFFF) | 0xAA000000));
        graphics.renderOutline(x, y, w, h, borderCol);
        if (isSelected) {
            graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xFF00FFFF);
        }

        renderMasterHeader(graphics, font, graph, frame, x, y, w, color, isSelected, hasWarning, mouseX, mouseY);
        renderSummaryBar(graphics, font, graph, frame, x, y + (int) CanvasGroupFrame.HEADER_HEIGHT, w, hasWarning, mouseX, mouseY);

        List<RecipeNode> subNodes = frame.getSubNodes(graph);
        for (RecipeNode subNode : subNodes) {
            if (subNode == null || subNode.isReroute()) continue;
            int sx = (int) subNode.getPosX();
            int sy = (int) subNode.getPosY();
            int sw = subNode.getCardWidth();
            int sh = subNode.getCardHeight();
            renderSubCard(graphics, font, graph, frame, subNode, sx, sy, sw, sh, mouseX, mouseY);
        }

        int addBtnY = y + h - 28;
        renderAddRecipeButton(graphics, font, frame, x + 6, addBtnY, w - 12, 22, mouseX, mouseY);

        // Corner Grips, Resize Grip & Edge Hover Highlight
        CanvasGroupFrameRenderer.drawCornerGrip(graphics, x, y, borderCol, false, false);
        CanvasGroupFrameRenderer.drawCornerGrip(graphics, x + w, y, borderCol, true, false);
        CanvasGroupFrameRenderer.drawCornerGrip(graphics, x, y + h, borderCol, false, true);
        if (!ExportRenderScope.isActive()) CanvasGroupFrameRenderer.drawResizeGrip(graphics, x + w - 12, y + h - 12, borderCol);

        CanvasGroupFrameRenderer.ResizeDirection hoverDir = CanvasGroupFrameRenderer.getResizeDirection(frame, mouseX, mouseY);
        if (hoverDir != CanvasGroupFrameRenderer.ResizeDirection.NONE) {
            CanvasGroupFrameRenderer.renderResizeHighlight(graphics, x, y, w, h, color, hoverDir);
        }
    }

    private static void renderMasterHeader(
            GuiGraphics graphics,
            Font font,
            FlowGraph graph,
            CanvasGroupFrame frame,
            int x,
            int y,
            int w,
            int color,
            boolean isSelected,
            boolean hasWarning,
            double mouseX,
            double mouseY
    ) {
        int headerH = (int) CanvasGroupFrame.HEADER_HEIGHT;
        int headerCol = hasWarning
                ? (isSelected ? 0xEE4D161A : 0xDD3D1419)
                : ((color & 0x00FFFFFF) | (isSelected ? 0xDD000000 : 0x99000000));
        graphics.fill(x, y, x + w, y + headerH, headerCol);

        ResourceLocation icon = frame.getSharedMachineIcon(graph);
        int iconX = x + 4;
        int iconY = y + 4;
        boolean rendered = false;
        if (icon != null) {
            ItemStack iconStack = NodeCardRenderer.getOrCreateMachineIcon(icon);
            if (!iconStack.isEmpty()) {
                rendered = IngredientRenderer.renderItemStack(graphics, iconStack, iconX, iconY);
            }
        }
        if (!rendered) {
            graphics.drawString(font, "↔", iconX + 4, iconY + 4, 0xFFFFFFFF, false);
        }

        String machineName = frame.getSharedMachineName(graph);
        if (machineName == null || machineName.isEmpty()) machineName = frame.getTitle();
        String tierName = frame.getSharedVoltageTier(graph) != null ? "[" + (hasWarning ? "⚠ " : "") + frame.getSharedVoltageTier(graph).name() + "]" : "";
        String suffix = " " + Component.translatable("gui.gtcalcboard.frame.shared_pool_suffix").getString();
        String displayTitle = (hasWarning ? "⚠ " : "") + machineName + " " + tierName + suffix;

        int btnCount = 7;
        int rightBoundary = x + w - (BTN_SIZE * btnCount + BTN_SPACING * (btnCount - 1) + 6);
        int maxTitleW = rightBoundary - (iconX + 22);
        String clippedTitle = displayTitle;
        if (maxTitleW > 0 && font.width(displayTitle) > maxTitleW) {
            clippedTitle = font.plainSubstrByWidth(displayTitle, Math.max(0, maxTitleW - font.width("..."))) + "...";
        }
        graphics.drawString(font, clippedTitle, iconX + 20, y + 6, hasWarning ? 0xFFFFB3B3 : 0xFFFFFFFF, true);

        int btnY = y + 4;
        int curBtnX = x + w - BTN_SIZE - 5;

        boolean delHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawIconButton(graphics, font, "✕", curBtnX, btnY, BTN_SIZE, BTN_SIZE, delHover, 0xFFFF5555, 0x55FF0000);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        boolean foldHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawIconButton(graphics, font, "⤡", curBtnX, btnY, BTN_SIZE, BTN_SIZE, foldHover, 0xFF38BDF8, 0x550284C7);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        boolean expandHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawIconButton(graphics, font, "⛶", curBtnX, btnY, BTN_SIZE, BTN_SIZE, expandHover, 0xFF4ADE80, 0x5516A34A);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        boolean flipHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        String flipIcon = frame.isFlipped() ? "⬅" : "➔";
        drawIconButton(graphics, font, flipIcon, curBtnX, btnY, BTN_SIZE, BTN_SIZE, flipHover, 0xFF38BDF8, 0x550284C7);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        boolean colorHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawColorCycleButton(graphics, curBtnX, btnY, BTN_SIZE, BTN_SIZE, colorHover, color);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        boolean cfgHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        int cfgColor = hasWarning ? 0xFFFF5555 : 0xFFFCD34D;
        int cfgHoverBg = hasWarning ? 0x77FF0000 : 0x55F59E0B;
        drawIconButton(graphics, font, "⚙", curBtnX, btnY, BTN_SIZE, BTN_SIZE, cfgHover, cfgColor, cfgHoverBg);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        boolean ratioHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawIconButton(graphics, font, "⚖", curBtnX, btnY, BTN_SIZE, BTN_SIZE, ratioHover, 0xFF60A5FA, 0x553B82F6);
    }

    private static void renderSummaryBar(
            GuiGraphics graphics,
            Font font,
            FlowGraph graph,
            CanvasGroupFrame frame,
            int x,
            int y,
            int w,
            boolean hasWarning,
            double mouseX,
            double mouseY
    ) {
        graphics.fill(x, y, x + w, y + 20, hasWarning ? 0xFF22161A : 0xFF181F2A);
        graphics.fill(x, y + 19, x + w, y + 20, hasWarning ? 0xFF552228 : 0xFF334155);

        // Count: [ - ] [ 1.25 ] [ + ] [/2] [x2]
        graphics.drawString(font, "Count:", x + 6, y + 5, 0xFFAAAAAA, false);

        double targetCap = frame.getTargetPoolCapacity();
        String countText = String.format(Locale.ROOT, "%.2f", targetCap);
        int countMinusX = x + 40;
        drawBtn(graphics, font, "-", countMinusX, y + 3, 14, 14, mouseX, mouseY, 0xFFFFFFFF);

        int countBoxX = countMinusX + 16;
        int countBoxW = Math.max(30, font.width(countText) + 6);
        graphics.fill(countBoxX, y + 3, countBoxX + countBoxW, y + 17, 0xFF1E293B);
        graphics.renderOutline(countBoxX, y + 3, countBoxW, 14, 0xFF475569);
        graphics.drawString(font, countText, countBoxX + (countBoxW - font.width(countText)) / 2, y + 5, 0xFFFFFFAA, false);

        int countPlusX = countBoxX + countBoxW + 2;
        drawBtn(graphics, font, "+", countPlusX, y + 3, 14, 14, mouseX, mouseY, 0xFFFFFFFF);
        drawBtn(graphics, font, "/2", countPlusX + 16, y + 3, 16, 14, mouseX, mouseY, 0xFFFFFFFF);
        drawBtn(graphics, font, "x2", countPlusX + 34, y + 3, 16, 14, mouseX, mouseY, 0xFFFFFFFF);

        // Aggregated Duty Badge & Power
        double duty = frame.computeTotalMachineDuty(graph);
        int req = frame.computeRequiredMachines(graph);
        boolean deficit = duty > targetCap + 0.001;

        String dutyStr = String.format(Locale.ROOT, "%.1f%% (%dx)", duty * 100.0, req);
        int dutyCol = (deficit || hasWarning) ? 0xFFF87171 : 0xFF34D399;
        int dutyX = countPlusX + 54;
        graphics.drawString(font, dutyStr, dutyX, y + 5, dutyCol, false);

        int syncBtnX = dutyX + font.width(dutyStr) + 6;
        int syncBtnW = 16;
        int syncBtnH = 14;
        boolean syncHover = isMouseOver(mouseX, mouseY, syncBtnX, y + 3, syncBtnW, syncBtnH);
        drawIconButton(graphics, font, "⚡", syncBtnX, y + 3, syncBtnW, syncBtnH, syncHover, 0xFF38BDF8, 0x550284C7);

        double eut = frame.computeSharedTotalEUt(graph);
        String eutStr = String.format(Locale.ROOT, "%.1f EU/t", eut);
        int eutW = font.width(eutStr);
        int eutX = x + w - eutW - 6;

        int badgeStartX = syncBtnX + syncBtnW + 6;
        int curBadgeX = badgeStartX;
        List<NodeBadge> badges = frame.getSharedBadges(graph);
        for (NodeBadge b : badges) {
            if (!b.isWarning()) continue;
            int bw = font.width(b.text()) + 8;
            if (curBadgeX + bw > eutX - 6) break;
            renderWarningBadge(graphics, font, b, curBadgeX, y + 3, bw, 14);
            curBadgeX += bw + 4;
        }
        if (hasWarning && curBadgeX == badgeStartX) {
            String inactTxt = "⚠ " + Component.translatable("gui.gtcalcboard.node_warning.inactive").getString();
            int bw = font.width(inactTxt) + 8;
            if (curBadgeX + bw <= eutX - 6) {
                renderBadgeBox(graphics, font, inactTxt, curBadgeX, y + 3, bw, 14, 0xFFFF5555, 0xEE3D1E1E);
            }
        }

        graphics.drawString(font, eutStr, eutX, y + 5, hasWarning ? 0xFF94A3B8 : 0xFFFCD34D, false);
    }

    private static void renderWarningBadge(GuiGraphics graphics, Font font, NodeBadge badge, int bx, int by, int bw, int bh) {
        renderBadgeBox(graphics, font, badge.text(), bx, by, bw, bh, badge.outlineColor(), badge.bgColor());
    }

    private static void renderBadgeBox(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int outlineCol, int bgCol) {
        graphics.fill(bx, by, bx + bw, by + bh, bgCol);
        graphics.renderOutline(bx, by, bw, bh, outlineCol);
        int tw = font.width(text);
        graphics.drawString(font, text, bx + (bw - tw) / 2, by + (bh - 8) / 2, outlineCol, false);
    }

    private static void renderSubCard(
            GuiGraphics graphics,
            Font font,
            FlowGraph graph,
            CanvasGroupFrame frame,
            RecipeNode node,
            int x,
            int y,
            int w,
            int h,
            double mouseX,
            double mouseY
    ) {
        boolean subOperational = node.isOperational(graph);
        SubCardLayoutBounds bounds = SubCardLayoutCalculator.compute(frame, node);

        // Sub-card background and outline
        int subBg = subOperational ? 0xD01E293B : 0xD0251417;
        int subBorder = subOperational ? 0xFF475569 : 0xFF883333;
        graphics.fill(x, y, x + w, y + h, subBg);
        graphics.renderOutline(x, y, w, h, subBorder);

        // Header: Recipe Name (left), machine duty editor (right), delete [✕]
        String name = node.getMachineDisplayName();
        if (name == null || name.isBlank()) name = node.getName();
        String clippedName = font.plainSubstrByWidth((!subOperational ? "⚠ " : "") + name, Math.max(20, bounds.nameBounds().width()));
        graphics.drawString(font, clippedName, bounds.nameBounds().x(), bounds.nameBounds().y(), subOperational ? 0xFFE2E8F0 : 0xFFFF8888, false);

        var delB = bounds.deleteBtnBounds();
        boolean delHover = isMouseOver(mouseX, mouseY, delB.x(), delB.y(), delB.width(), delB.height());
        drawBtn(graphics, font, "✕", delB.x(), delB.y(), delB.width(), delB.height(), mouseX, mouseY, delHover ? 0xFFFF5555 : 0xFF94A3B8);

        var plusB = bounds.plusBtnBounds();
        drawBtn(graphics, font, "+", plusB.x(), plusB.y(), plusB.width(), plusB.height(), mouseX, mouseY, 0xFFFFFFFF);

        String dutyStr = String.format(Locale.ROOT, (!subOperational ? "⚠ " : "") + "%.2fx", node.getMachineCount());
        var dutyB = bounds.dutyTextBounds();
        graphics.drawString(font, dutyStr, dutyB.x(), dutyB.y(), subOperational ? 0xFFFDE047 : 0xFFFF8888, false);

        var minusB = bounds.minusBtnBounds();
        drawBtn(graphics, font, "-", minusB.x(), minusB.y(), minusB.width(), minusB.height(), mouseX, mouseY, 0xFFFFFFFF);

        // Inputs & Outputs
        boolean flipped = frame.isFlipped();
        for (var portB : bounds.inputPorts()) {
            renderSubCardInputPort(graphics, font, node, portB, flipped);
        }
        for (var portB : bounds.outputPorts()) {
            renderSubCardOutputPort(graphics, font, node, portB, !flipped);
        }

        // Center transition indicator
        var arrowB = bounds.centerArrowBounds();
        String arrow = flipped ? "<──" : "──>";
        graphics.drawString(font, arrow, arrowB.x(), arrowB.y(), 0xFF64748B, false);
    }

    private static void renderSubCardInputPort(GuiGraphics graphics, Font font, RecipeNode node, SubCardLayoutBounds.SubCardPortBounds portB, boolean onRight) {
        IngredientStack stack = node.getInputs().get(portB.portIndex());
        if (stack == null) return;

        double rate = node.getInputSlotRate(portB.portIndex(), true);
        String rateStr = FormatUtil.formatRate(rate, stack);

        renderPortPinAndSlot(graphics, font, stack, rateStr, portB, onRight, 0xFF38BDF8, 0xFF0284C7, 0xFF94A3B8);
    }

    private static void renderSubCardOutputPort(GuiGraphics graphics, Font font, RecipeNode node, SubCardLayoutBounds.SubCardPortBounds portB, boolean onRight) {
        IngredientStack stack = node.getOutputs().get(portB.portIndex());
        if (stack == null) return;

        double rate = node.getOutputSlotRate(portB.portIndex(), true);
        String rateStr = "+" + FormatUtil.formatRate(rate, stack);

        renderPortPinAndSlot(graphics, font, stack, rateStr, portB, onRight, 0xFFF59E0B, 0xFFB45309, 0xFF34D399);
    }

    private static void renderPortPinAndSlot(
            GuiGraphics graphics,
            Font font,
            IngredientStack stack,
            String rateStr,
            SubCardLayoutBounds.SubCardPortBounds portB,
            boolean onRight,
            int pinFill,
            int pinOutline,
            int rateColor
    ) {
        var pinB = portB.pinHitBox();
        graphics.fill(pinB.x() + 2, pinB.y() + 2, pinB.x() + 6, pinB.y() + 6, pinFill);
        graphics.renderOutline(pinB.x() + 1, pinB.y() + 1, 6, 6, pinOutline);

        var slotB = portB.slotHitBox();
        IngredientRenderer.render(graphics, stack, slotB.x(), slotB.y());

        int rw = font.width(rateStr);
        int textX = onRight ? (slotB.x() - rw - 4) : (slotB.x() + slotB.width() + 2);
        graphics.drawString(font, rateStr, textX, slotB.y() + 4, rateColor, false);
    }

    private static void renderAddRecipeButton(
            GuiGraphics graphics,
            Font font,
            CanvasGroupFrame frame,
            int x,
            int y,
            int w,
            int h,
            double mouseX,
            double mouseY
    ) {
        boolean hover = isMouseOver(mouseX, mouseY, x, y, w, h);
        int bg = hover ? 0x443B82F6 : 0x221E293B;
        int border = hover ? 0xFF60A5FA : 0x66475569;
        graphics.fill(x, y, x + w, y + h, bg);
        graphics.renderOutline(x, y, w, h, border);

        String text = Component.translatable("gui.gtcalcboard.frame.inline_add_recipe_placeholder").getString();
        int tw = font.width(text);
        int tx = x + Math.max(4, (w - tw) / 2);
        int ty = y + (h - 8) / 2;
        graphics.drawString(font, text, tx, ty, hover ? 0xFFFFFFFF : 0xFF94A3B8, false);
    }

    public static EmbeddedPortHit findHoveredEmbeddedPort(FlowGraph graph, double canvasX, double canvasY) {
        if (graph == null) return null;
        for (CanvasGroupFrame frame : graph.getFrames()) {
            if (frame == null || !frame.isSharedMachineFrame() || frame.getViewMode() != PoolViewMode.EMBEDDED_PANEL) {
                continue;
            }
            if (!frame.isPointInside(canvasX, canvasY)) continue;

            for (RecipeNode subNode : frame.getSubNodes(graph)) {
                EmbeddedPortHit hit = findHoveredPortInNode(frame, subNode, canvasX, canvasY);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private static EmbeddedPortHit findHoveredPortInNode(CanvasGroupFrame frame, RecipeNode subNode, double canvasX, double canvasY) {
        if (subNode == null || subNode.isReroute()) return null;

        SubCardLayoutBounds bounds = SubCardLayoutCalculator.compute(frame, subNode);
        var portB = bounds.findHoveredPort(canvasX, canvasY);
        if (portB != null) {
            IngredientStack stack = portB.isInput()
                    ? (portB.portIndex() < subNode.getInputs().size() ? subNode.getInputs().get(portB.portIndex()) : null)
                    : (portB.portIndex() < subNode.getOutputs().size() ? subNode.getOutputs().get(portB.portIndex()) : null);
            if (stack != null) {
                return new EmbeddedPortHit(frame, subNode, portB.isInput(), portB.portIndex(), stack);
            }
        }
        return null;
    }

    private static boolean isMouseOver(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static void drawIconButton(GuiGraphics graphics, Font font, String icon, int bx, int by, int bw, int bh, boolean hover, int textCol, int hoverBg) {
        int bg = hover ? (hoverBg != 0 ? hoverBg : 0x66FFFFFF) : 0x33000000;
        int border = hover ? 0xFFFFFFFF : 0x55FFFFFF;
        graphics.fill(bx, by, bx + bw, by + bh, bg);
        graphics.renderOutline(bx, by, bw, bh, border);
        int textW = font.width(icon);
        graphics.drawString(font, icon, bx + (bw - textW) / 2, by + (bh - 8) / 2, textCol, false);
    }

    private static void drawColorCycleButton(GuiGraphics graphics, int bx, int by, int bw, int bh, boolean hover, int color) {
        int border = hover ? 0xFFFFFFFF : 0x88FFFFFF;
        graphics.fill(bx, by, bx + bw, by + bh, 0x44000000);
        graphics.fill(bx + 2, by + 2, bx + bw - 2, by + bh - 2, color);
        graphics.renderOutline(bx, by, bw, bh, border);
    }

    private static void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, double mouseX, double mouseY, int textCol) {
        boolean hover = isMouseOver(mouseX, mouseY, bx, by, bw, bh);
        int bg = hover ? 0x66FFFFFF : 0x22000000;
        int border = hover ? 0xFFFFFFFF : 0x44FFFFFF;
        graphics.fill(bx, by, bx + bw, by + bh, bg);
        graphics.renderOutline(bx, by, bw, bh, border);
        int textW = font.width(text);
        graphics.drawString(font, text, bx + (bw - textW) / 2, by + (bh - 8) / 2, textCol, false);
    }

    public static double[] getEmbeddedPortAnchor(CanvasGroupFrame frame, RecipeNode subNode, int portIndex, boolean isInput) {
        if (frame == null || subNode == null) return new double[]{0.0, 0.0};
        SubCardLayoutBounds bounds = SubCardLayoutCalculator.compute(frame, subNode);
        var portB = bounds.findPort(isInput, portIndex);
        if (portB != null) {
            return new double[]{portB.anchorX(), portB.anchorY()};
        }
        return new double[]{subNode.getPosX(), subNode.getPosY()};
    }

    public static NodeBadge findHoveredSummaryBadge(CanvasGroupFrame frame, FlowGraph graph, Font font, double canvasMouseX, double canvasMouseY) {
        if (frame == null || graph == null || font == null) return null;
        int x = (int) frame.getPosX();
        int y = (int) frame.getPosY() + (int) CanvasGroupFrame.HEADER_HEIGHT;
        int w = (int) frame.getWidth();

        if (canvasMouseY < y + 3 || canvasMouseY > y + 17) return null;

        double targetCap = frame.getTargetPoolCapacity();
        String countText = String.format(Locale.ROOT, "%.2f", targetCap);
        int countMinusX = x + 40;
        int countBoxX = countMinusX + 16;
        int countBoxW = Math.max(30, font.width(countText) + 6);
        int countPlusX = countBoxX + countBoxW + 2;

        double duty = frame.computeTotalMachineDuty(graph);
        int req = frame.computeRequiredMachines(graph);
        String dutyStr = String.format(Locale.ROOT, "%.1f%% (%dx)", duty * 100.0, req);
        int dutyX = countPlusX + 54;
        int syncBtnX = dutyX + font.width(dutyStr) + 6;
        int badgeStartX = syncBtnX + 16 + 6;

        double eut = frame.computeSharedTotalEUt(graph);
        String eutStr = String.format(Locale.ROOT, "%.1f EU/t", eut);
        int eutW = font.width(eutStr);
        int eutX = x + w - eutW - 6;

        List<NodeBadge> badges = frame.getSharedBadges(graph);
        int curBadgeX = badgeStartX;
        for (NodeBadge b : badges) {
            if (!b.isWarning()) continue;
            int bw = font.width(b.text()) + 8;
            if (curBadgeX + bw > eutX - 6) break;
            if (canvasMouseX >= curBadgeX && canvasMouseX <= curBadgeX + bw) {
                return b;
            }
            curBadgeX += bw + 4;
        }
        return null;
    }

    public static boolean isMouseOverSyncButton(CanvasGroupFrame frame, FlowGraph graph, Font font, double canvasMouseX, double canvasMouseY) {
        if (frame == null || graph == null) return false;
        int x = (int) frame.getPosX();
        int y = (int) frame.getPosY() + (int) CanvasGroupFrame.HEADER_HEIGHT;
        if (canvasMouseY < y + 3 || canvasMouseY > y + 17) return false;

        double targetCap = frame.getTargetPoolCapacity();
        String countText = String.format(Locale.ROOT, "%.2f", targetCap);
        int countMinusX = x + 40;
        int countBoxX = countMinusX + 16;
        int countW = font != null ? font.width(countText) : (countText.length() * 6);
        int countBoxW = Math.max(30, countW + 6);
        int countPlusX = countBoxX + countBoxW + 2;

        double duty = frame.computeTotalMachineDuty(graph);
        int req = frame.computeRequiredMachines(graph);
        String dutyStr = String.format(Locale.ROOT, "%.1f%% (%dx)", duty * 100.0, req);
        int dutyW = font != null ? font.width(dutyStr) : (dutyStr.length() * 6);
        int dutyX = countPlusX + 54;
        int syncBtnX = dutyX + dutyW + 6;
        int syncBtnW = 16;

        return canvasMouseX >= syncBtnX && canvasMouseX <= syncBtnX + syncBtnW;
    }
}
