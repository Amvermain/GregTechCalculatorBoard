package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.PoolViewMode;
import com.gtceu.calcboard.api.model.RecipeNode;
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

        graphics.fill(x, y, x + w, y + h, 0xEE14171E);
        int borderCol = isSelected ? 0xFF00FFFF : ((color & 0x00FFFFFF) | 0xAA000000);
        graphics.renderOutline(x, y, w, h, borderCol);
        if (isSelected) {
            graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xFF00FFFF);
        }

        renderMasterHeader(graphics, font, graph, frame, x, y, w, color, isSelected, mouseX, mouseY);
        renderSummaryBar(graphics, font, graph, frame, x, y + (int) CanvasGroupFrame.HEADER_HEIGHT, w, mouseX, mouseY);

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
            double mouseX,
            double mouseY
    ) {
        int headerH = (int) CanvasGroupFrame.HEADER_HEIGHT;
        int headerCol = (color & 0x00FFFFFF) | (isSelected ? 0xDD000000 : 0x99000000);
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
        String tierName = frame.getSharedVoltageTier(graph) != null ? "[" + frame.getSharedVoltageTier(graph).name() + "]" : "";
        String suffix = " " + Component.translatable("gui.gtcalcboard.frame.shared_pool_suffix").getString();
        String displayTitle = machineName + " " + tierName + suffix;

        int btnCount = 6;
        int rightBoundary = x + w - (BTN_SIZE * btnCount + BTN_SPACING * (btnCount - 1) + 6);
        int maxTitleW = rightBoundary - (iconX + 22);
        String clippedTitle = displayTitle;
        if (maxTitleW > 0 && font.width(displayTitle) > maxTitleW) {
            clippedTitle = font.plainSubstrByWidth(displayTitle, Math.max(0, maxTitleW - font.width("..."))) + "...";
        }
        graphics.drawString(font, clippedTitle, iconX + 20, y + 6, 0xFFFFFFFF, true);

        // Header Buttons: [✕] [⤡] [⛶] [✦] [⚙] [⚖]
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

        boolean colorHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawColorCycleButton(graphics, curBtnX, btnY, BTN_SIZE, BTN_SIZE, colorHover, color);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        boolean cfgHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawIconButton(graphics, font, "⚙", curBtnX, btnY, BTN_SIZE, BTN_SIZE, cfgHover, 0xFFFCD34D, 0x55F59E0B);
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
            double mouseX,
            double mouseY
    ) {
        graphics.fill(x, y, x + w, y + 20, 0xFF181F2A);
        graphics.fill(x, y + 19, x + w, y + 20, 0xFF334155);

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
        int dutyCol = deficit ? 0xFFF87171 : 0xFF34D399;
        int dutyX = countPlusX + 54;
        graphics.drawString(font, dutyStr, dutyX, y + 5, dutyCol, false);

        double eut = frame.computeSharedTotalEUt(graph);
        String eutStr = String.format(Locale.ROOT, "%.1f EU/t", eut);
        int eutW = font.width(eutStr);
        graphics.drawString(font, eutStr, x + w - eutW - 6, y + 5, 0xFFFCD34D, false);
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
        // Sub-card background and outline
        graphics.fill(x, y, x + w, y + h, 0xD01E293B);
        graphics.renderOutline(x, y, w, h, 0xFF475569);

        // Header: Recipe Name (left), machine duty editor (right), delete [✕]
        String name = node.getMachineDisplayName();
        if (name == null || name.isBlank()) name = node.getName();
        String clippedName = font.plainSubstrByWidth(name, Math.max(20, w - 100));
        graphics.drawString(font, clippedName, x + 6, y + 4, 0xFFE2E8F0, false);

        int curX = x + w - 16;
        boolean delHover = isMouseOver(mouseX, mouseY, curX, y + 2, 12, 12);
        drawBtn(graphics, font, "✕", curX, y + 2, 12, 12, mouseX, mouseY, delHover ? 0xFFFF5555 : 0xFF94A3B8);

        curX -= 14;
        drawBtn(graphics, font, "+", curX, y + 2, 12, 12, mouseX, mouseY, 0xFFFFFFFF);

        String dutyStr = String.format(Locale.ROOT, "%.2fx", node.getMachineCount());
        int dutyW = font.width(dutyStr);
        curX -= (dutyW + 4);
        graphics.drawString(font, dutyStr, curX + 2, y + 4, 0xFFFDE047, false);

        curX -= 14;
        drawBtn(graphics, font, "-", curX, y + 2, 12, 12, mouseX, mouseY, 0xFFFFFFFF);

        // Inputs & Outputs
        int inCount = node.getInputs().size();
        int outCount = node.getOutputs().size();
        int maxPorts = Math.max(inCount, outCount);

        for (int i = 0; i < maxPorts; i++) {
            int portY = y + 18 + i * 16;
            if (i < inCount) {
                renderSubCardInputPort(graphics, font, node, i, x, portY);
            }
            if (i < outCount) {
                renderSubCardOutputPort(graphics, font, node, i, x, portY, w);
            }
        }

        // Center transition indicator
        int midX = x + w / 2 - 6;
        int midY = y + 18 + (maxPorts > 0 ? (maxPorts * 16) / 2 - 4 : 0);
        graphics.drawString(font, "──>", midX, midY, 0xFF64748B, false);
    }

    private static void renderSubCardInputPort(GuiGraphics graphics, Font font, RecipeNode node, int idx, int x, int portY) {
        IngredientStack stack = node.getInputs().get(idx);
        if (stack == null) return;

        // Pin anchor circle
        graphics.fill(x + 4, portY + 6, x + 8, portY + 10, 0xFF38BDF8);
        graphics.renderOutline(x + 3, portY + 5, 6, 6, 0xFF0284C7);

        // Ingredient icon & rate
        IngredientRenderer.render(graphics, stack, x + 12, portY);

        double rate = node.getInputSlotRate(idx, true);
        String rateStr = FormatUtil.formatRate(rate, stack);
        graphics.drawString(font, rateStr, x + 30, portY + 4, 0xFF94A3B8, false);
    }

    private static void renderSubCardOutputPort(GuiGraphics graphics, Font font, RecipeNode node, int idx, int x, int portY, int w) {
        IngredientStack stack = node.getOutputs().get(idx);
        if (stack == null) return;

        // Pin anchor circle
        graphics.fill(x + w - 8, portY + 6, x + w - 4, portY + 10, 0xFFF59E0B);
        graphics.renderOutline(x + w - 9, portY + 5, 6, 6, 0xFFB45309);

        // Ingredient icon & rate
        int iconX = x + w - 24;
        IngredientRenderer.render(graphics, stack, iconX, portY);

        double rate = node.getOutputSlotRate(idx, true);
        String rateStr = "+" + FormatUtil.formatRate(rate, stack);
        int rw = font.width(rateStr);
        graphics.drawString(font, rateStr, iconX - rw - 4, portY + 4, 0xFF34D399, false);
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

        double cardX = subNode.getPosX();
        double cardW = subNode.getCardWidth() > 0 ? subNode.getCardWidth() : (frame.getWidth() - 12.0);

        for (int i = 0; i < subNode.getInputs().size(); i++) {
            double[] pt = getEmbeddedPortAnchor(frame, subNode, i, true);
            boolean xHit = canvasX >= cardX + 2.0 && canvasX <= cardX + 30.0;
            boolean yHit = Math.abs(canvasY - pt[1]) <= 10.0;
            if (xHit && yHit) {
                return new EmbeddedPortHit(frame, subNode, true, i, subNode.getInputs().get(i));
            }
        }
        for (int j = 0; j < subNode.getOutputs().size(); j++) {
            double[] pt = getEmbeddedPortAnchor(frame, subNode, j, false);
            boolean xHit = canvasX >= (cardX + cardW - 30.0) && canvasX <= (cardX + cardW - 2.0);
            boolean yHit = Math.abs(canvasY - pt[1]) <= 10.0;
            if (xHit && yHit) {
                return new EmbeddedPortHit(frame, subNode, false, j, subNode.getOutputs().get(j));
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
        double cardX = subNode.getPosX();
        double cardY = subNode.getPosY();
        double cardW = subNode.getCardWidth() > 0 ? subNode.getCardWidth() : Math.max(200.0, frame.getWidth() - 12.0);

        int safePortIdx = Math.max(0, portIndex);
        double pinX = isInput ? (cardX + 6.0) : (cardX + cardW - 6.0);
        double pinY = cardY + 18.0 + safePortIdx * 16.0 + 8.0;
        return new double[]{pinX, pinY};
    }
}
