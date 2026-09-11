package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders visual group frames, headers, action buttons, and multi-edge resize handles on the canvas.
 */
public class CanvasGroupFrameRenderer {

    public enum FrameAction {
        NONE, COLOR, COLLAPSE, DELETE, RESIZE, CONFIG, AUTOFIT, AUTO_RATIO
    }

    public enum ResizeDirection {
        NONE, NORTH, SOUTH, WEST, EAST, NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST
    }

    public static final int BTN_SIZE = 16;
    public static final int BTN_SPACING = 3;
    public static final double RESIZE_MARGIN = 6.0;
    public static final double CORNER_SIZE = 14.0;

    public record FoldedPortHit(
            CanvasGroupFrame frame,
            boolean isInput,
            int portIndex,
            FlowGraphTopologyAnalyzer.AggregatedFoldedPort port
    ) {}

    public static void renderFrames(GuiGraphics graphics, FlowGraph graph, double canvasMouseX, double canvasMouseY, String activeEditingFrameId) {
        renderFrames(graphics, graph, canvasMouseX, canvasMouseY, activeEditingFrameId, java.util.Collections.emptySet());
    }

    public static void renderFrames(GuiGraphics graphics, FlowGraph graph, double canvasMouseX, double canvasMouseY, String activeEditingFrameId, java.util.Set<String> selectedFrameIds) {
        renderFrames(graphics, graph, canvasMouseX, canvasMouseY, activeEditingFrameId, selectedFrameIds, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public static void renderFrames(GuiGraphics graphics, FlowGraph graph, double canvasMouseX, double canvasMouseY, String activeEditingFrameId, java.util.Set<String> selectedFrameIds,
                                    double screenLeft, double screenRight, double screenTop, double screenBottom) {
        if (graph == null || graph.getFrames().isEmpty()) return;

        Font font = Minecraft.getInstance().font;

        for (CanvasGroupFrame frame : graph.getFrames()) {
            if (frame == null) continue;
            double fx = frame.getPosX();
            double fy = frame.getPosY();
            double fw = frame.getWidth();
            double fh = frame.getHeight();
            if (fx + fw < screenLeft || fx > screenRight || fy + fh < screenTop || fy > screenBottom) {
                continue;
            }
            boolean isSelected = selectedFrameIds != null && selectedFrameIds.contains(frame.getId());
            renderSingleFrame(graphics, font, graph, frame, canvasMouseX, canvasMouseY, frame.getId().equals(activeEditingFrameId), isSelected);
        }
    }

    public static void renderSingleFrame(GuiGraphics graphics, Font font, FlowGraph graph, CanvasGroupFrame frame, double mouseX, double mouseY, boolean isEditing, boolean isSelected) {
        if (frame == null) return;
        if (frame.isSharedMachineFrame() && frame.isFolded()) {
            renderFoldedMachineCard(graphics, font, graph, frame, mouseX, mouseY, isEditing, isSelected);
            return;
        }

        int x = (int) frame.getPosX();
        int y = (int) frame.getPosY();
        int w = (int) frame.getWidth();
        int h = (int) frame.getHeight();
        int color = frame.getColor();

        // 1. Frame Background Fill
        int bgAlpha = isSelected ? 0x44000000 : 0x22000000;
        int bgColor = (color & 0x00FFFFFF) | bgAlpha;
        graphics.fill(x, y, x + w, y + h, bgColor);

        // 2. Outer Border Outline
        int borderAlpha = isSelected ? 0xFF000000 : 0xAA000000;
        int borderCol = (color & 0x00FFFFFF) | borderAlpha;
        graphics.renderOutline(x, y, w, h, borderCol);

        if (isSelected) {
            graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xFF00FFFF);
        }

        // 3. Header Bar Fill & Title
        int headerH = (int) CanvasGroupFrame.HEADER_HEIGHT;
        int headerAlpha = isSelected ? 0xDD000000 : 0x99000000;
        int headerCol = (color & 0x00FFFFFF) | headerAlpha;
        graphics.fill(x, y, x + w, y + headerH, headerCol);

        // Header Title
        String title = frame.getTitle();
        if (title == null || title.isBlank()) {
            title = frame.isSharedMachineFrame()
                    ? Component.translatable("gui.gtcalcboard.default_shared_frame_name").getString()
                    : Component.translatable("gui.gtcalcboard.default_frame_name").getString();
        }
        int titleCol = 0xFFFFFFFF;
        String prefix = frame.isSharedMachineFrame() ? "↔ " : (frame.isCompoundFrame() ? "▦ " : "");
        String displayTitle = prefix + title;

        int btnCount = 4 + (frame.isSharedMachineFrame() ? 2 : 0);
        int rightButtonsBoundary = x + w - (BTN_SIZE * btnCount + BTN_SPACING * (btnCount - 1) + 8);

        // Shared Machine Frame Load Badge & Incompatible Warning
        if (frame.isSharedMachineFrame()) {
            double duty = frame.computeTotalMachineDuty(graph);
            int reqMachines = frame.computeRequiredMachines(graph);
            boolean isCompatible = frame.isMachineCompatible(graph);

            String dutyText = String.format(Locale.ROOT, "%.1f%% (%dx)", duty * 100.0, reqMachines);
            int badgeBg = 0xCC064E3B;
            int badgeBorder = 0xFF10B981;
            int badgeTextCol = 0xFF6EE7B7;

            int badgeW = font.width(dutyText) + 8;
            int badgeH = 12;
            int badgeY = y + 6;
            int warnW = (!isCompatible) ? 14 : 0;
            int totalBadgeW = badgeW + warnW;

            int idealBadgeStartX = x + 6 + font.width(displayTitle) + 6;
            int badgeStartX;
            int maxTitleW;

            if (idealBadgeStartX + totalBadgeW <= rightButtonsBoundary) {
                badgeStartX = idealBadgeStartX;
                maxTitleW = font.width(displayTitle);
            } else {
                badgeStartX = Math.max(x + 6, rightButtonsBoundary - totalBadgeW - 3);
                maxTitleW = Math.max(0, badgeStartX - (x + 6) - 4);
            }

            // Render Title (truncated with ellipsis if needed to never hide the badge)
            if (maxTitleW > 0) {
                String clippedTitle = displayTitle;
                if (font.width(displayTitle) > maxTitleW) {
                    clippedTitle = font.plainSubstrByWidth(displayTitle, Math.max(0, maxTitleW - font.width("..."))) + "...";
                }
                graphics.drawString(font, clippedTitle, x + 6, y + 5, titleCol, true);
            }

            // Render Duty Badge (guaranteed visibility)
            if (badgeStartX + badgeW <= x + w - 4) {
                graphics.fill(badgeStartX, badgeY, badgeStartX + badgeW, badgeY + badgeH, badgeBg);
                graphics.renderOutline(badgeStartX, badgeY, badgeW, badgeH, badgeBorder);
                graphics.drawString(font, dutyText, badgeStartX + 4, badgeY + 2, badgeTextCol, false);

                if (!isCompatible) {
                    int warnX = badgeStartX + badgeW + 4;
                    if (warnX + 12 <= rightButtonsBoundary + 4) {
                        graphics.drawString(font, "\u26A0", warnX, badgeY + 1, 0xFFEF4444, false);
                    }
                }
            }
        } else {
            int maxTitleW = rightButtonsBoundary - (x + 6) - 4;
            String clippedTitle = displayTitle;
            if (maxTitleW > 0 && font.width(displayTitle) > maxTitleW) {
                clippedTitle = font.plainSubstrByWidth(displayTitle, Math.max(0, maxTitleW - font.width("..."))) + "...";
            }
            graphics.drawString(font, clippedTitle, x + 6, y + 5, titleCol, true);
        }

        // 4. Header Action Buttons (Right-aligned)
        // [⚙ Config] [✦ Color] [⛶ Auto-Fit] [▦ Collapse] [✕ Delete]
        int btnY = y + 4;
        int curBtnX = x + w - BTN_SIZE - 5;

        // [✕ Delete / Ungroup]
        boolean delHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawIconButton(graphics, font, "✕", curBtnX, btnY, BTN_SIZE, BTN_SIZE, delHover, 0xFFFF5555, 0x55FF0000);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        // [▦ Collapse to Module]
        boolean colHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        boolean isColGlowing = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isFrameCollapseButtonGlowing(frame.getId());
        drawIconButton(graphics, font, "▦", curBtnX, btnY, BTN_SIZE, BTN_SIZE, colHover, 0xFF60A5FA, 0x553B82F6, isColGlowing);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        // [⛶ Auto-Fit to Contents]
        boolean fitHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawIconButton(graphics, font, "⛶", curBtnX, btnY, BTN_SIZE, BTN_SIZE, fitHover, 0xFF34D399, 0x55059669);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        // [✦ Color Picker]
        boolean colorHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawColorCycleButton(graphics, curBtnX, btnY, BTN_SIZE, BTN_SIZE, colorHover, color);

        // [⚙ Configure Shared Machine Hardware]
        if (frame.isSharedMachineFrame()) {
            curBtnX -= (BTN_SIZE + BTN_SPACING);
            boolean cfgHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
            drawIconButton(graphics, font, "⚙", curBtnX, btnY, BTN_SIZE, BTN_SIZE, cfgHover, 0xFFFCD34D, 0x55F59E0B);

            curBtnX -= (BTN_SIZE + BTN_SPACING);
            boolean ratioHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
            drawIconButton(graphics, font, "⚖", curBtnX, btnY, BTN_SIZE, BTN_SIZE, ratioHover, 0xFF60A5FA, 0x553B82F6);
        }

        // 5. Corner Grips & Edge Hover Highlight
        drawCornerGrip(graphics, x, y, borderCol, false, false);
        drawCornerGrip(graphics, x + w, y, borderCol, true, false);
        drawCornerGrip(graphics, x, y + h, borderCol, false, true);
        drawResizeGrip(graphics, x + w - 12, y + h - 12, borderCol);

        ResizeDirection hoverDir = getResizeDirection(frame, mouseX, mouseY);
        if (hoverDir != ResizeDirection.NONE) {
            int highlightCol = (color & 0x00FFFFFF) | 0xFF000000;
            switch (hoverDir) {
                case NORTH -> graphics.fill(x, y - 1, x + w, y + 2, highlightCol);
                case SOUTH -> graphics.fill(x, y + h - 2, x + w, y + h + 1, highlightCol);
                case WEST -> graphics.fill(x - 1, y, x + 2, y + h, highlightCol);
                case EAST -> graphics.fill(x + w - 2, y, x + w + 1, y + h, highlightCol);
                case NORTH_WEST -> {
                    graphics.fill(x - 1, y - 1, x + 16, y + 2, highlightCol);
                    graphics.fill(x - 1, y - 1, x + 2, y + 16, highlightCol);
                }
                case NORTH_EAST -> {
                    graphics.fill(x + w - 16, y - 1, x + w + 1, y + 2, highlightCol);
                    graphics.fill(x + w - 2, y - 1, x + w + 1, y + 16, highlightCol);
                }
                case SOUTH_WEST -> {
                    graphics.fill(x - 1, y + h - 2, x + 16, y + h + 1, highlightCol);
                    graphics.fill(x - 1, y + h - 16, x + 2, y + h + 1, highlightCol);
                }
                case SOUTH_EAST -> {
                    graphics.fill(x + w - 16, y + h - 2, x + w + 1, y + h + 1, highlightCol);
                    graphics.fill(x + w - 2, y + h - 16, x + w + 1, y + h + 1, highlightCol);
                }
                default -> {}
            }
        }
    }

    public static void renderFrameTooltips(GuiGraphics graphics, Font font, FlowGraph graph, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
        if (graph == null || graph.getFrames().isEmpty()) return;

        for (CanvasGroupFrame frame : graph.getFrames()) {
            if (renderSingleFrameTooltip(graphics, font, graph, frame, canvasMouseX, canvasMouseY, mouseX, mouseY)) {
                return;
            }
        }
    }

    private static boolean renderSingleFrameTooltip(GuiGraphics graphics, Font font, FlowGraph graph, CanvasGroupFrame frame, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
        if (frame.isSharedMachineFrame() && frame.isFolded()) {
            return renderFoldedFrameTooltip(graphics, font, graph, frame, canvasMouseX, canvasMouseY, mouseX, mouseY);
        }

        int x = (int) frame.getPosX();
        int y = (int) frame.getPosY();
        int w = (int) frame.getWidth();
        int headerH = (int) CanvasGroupFrame.HEADER_HEIGHT;

        if (canvasMouseY < y || canvasMouseY > y + headerH || canvasMouseX < x || canvasMouseX > x + w) {
            return false;
        }

        if (renderHeaderButtonsTooltip(graphics, font, frame, canvasMouseX, canvasMouseY, x, y, w, mouseX, mouseY)) {
            return true;
        }

        if (frame.isSharedMachineFrame()) {
            renderSharedMachineBreakdownTooltip(graphics, font, graph, frame, mouseX, mouseY);
            return true;
        }

        return false;
    }

    private static boolean renderHeaderButtonsTooltip(GuiGraphics graphics, Font font, CanvasGroupFrame frame, double canvasMouseX, double canvasMouseY, int x, int y, int w, int mouseX, int mouseY) {
        int btnY = y + 4;
        int curBtnX = x + w - BTN_SIZE - 5;

        if (isMouseOver(canvasMouseX, canvasMouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§c✕ ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_delete")), mouseX, mouseY);
            return true;
        }
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        if (isMouseOver(canvasMouseX, canvasMouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
            Component tooltip = frame.isSharedMachineFrame()
                    ? Component.literal("§b⤡ ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_fold"))
                    : Component.literal("§b▦ ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_collapse"));
            BoardTooltipRenderer.renderTooltip(graphics, font, tooltip, mouseX, mouseY);
            return true;
        }
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        if (isMouseOver(canvasMouseX, canvasMouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§a⛶ ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_autofit")), mouseX, mouseY);
            return true;
        }
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        if (isMouseOver(canvasMouseX, canvasMouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§e✦ ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_color")), mouseX, mouseY);
            return true;
        }

        if (frame.isSharedMachineFrame()) {
            return renderSharedHeaderButtonsTooltip(graphics, font, frame, canvasMouseX, canvasMouseY, curBtnX, btnY, mouseX, mouseY);
        }
        return false;
    }

    private static boolean renderSharedHeaderButtonsTooltip(GuiGraphics graphics, Font font, CanvasGroupFrame frame, double canvasMouseX, double canvasMouseY, int curBtnX, int btnY, int mouseX, int mouseY) {
        int cfgBtnX = curBtnX - (BTN_SIZE + BTN_SPACING);
        if (isMouseOver(canvasMouseX, canvasMouseY, cfgBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§e⚙ ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_config")), mouseX, mouseY);
            return true;
        }

        int ratioBtnX = cfgBtnX - (BTN_SIZE + BTN_SPACING);
        if (isMouseOver(canvasMouseX, canvasMouseY, ratioBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
            String capacityStr = String.format(Locale.ROOT, "%.1f", frame.getTargetPoolCapacity());
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§b⚖ ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_auto_ratio", capacityStr)), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private static void renderSharedMachineBreakdownTooltip(GuiGraphics graphics, Font font, FlowGraph graph, CanvasGroupFrame frame, int mouseX, int mouseY) {
        double totalDuty = frame.computeTotalMachineDuty(graph);
        int reqMachines = frame.computeRequiredMachines(graph);
        boolean isCompatible = frame.isMachineCompatible(graph);

        List<Component> tooltipLines = new ArrayList<>();
        tooltipLines.add(Component.literal("§b↔ " + frame.getTitle() + " §7(").append(Component.translatable("gui.gtcalcboard.frame.shared_machine_tag")).append(Component.literal("§7)")));
        tooltipLines.add(Component.translatable("gui.gtcalcboard.frame.total_duty_tooltip",
                String.format(Locale.ROOT, "%.1f%%", totalDuty * 100.0),
                String.valueOf(reqMachines)));

        List<RecipeNode> enclosed = frame.getEnclosedNodes(graph);
        if (!enclosed.isEmpty()) {
            tooltipLines.add(Component.translatable("gui.gtcalcboard.frame.breakdown_header"));
            for (RecipeNode n : enclosed) {
                if (n != null && !n.isReroute()) {
                    double nDuty = n.getMachineCount();
                    String nodeName = n.getName() != null && !n.getName().isBlank() ? n.getName() : n.getMachineDisplayName();
                    String tierTag = n.getTargetTier() != null ? " §8[" + n.getTargetTier().name() + "]" : "";
                    tooltipLines.add(Component.literal("  §7• §f" + nodeName + tierTag + ": §e" + String.format(Locale.ROOT, "%.1f%%", nDuty * 100.0)));
                }
            }
        }

        if (!isCompatible) {
            tooltipLines.add(Component.literal("§c\u26A0 ").append(Component.translatable("gui.gtcalcboard.frame.incompatible_warning")));
        }

        BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY);
    }

    private static void drawIconButton(GuiGraphics graphics, Font font, String icon, int bx, int by, int bw, int bh, boolean hover, int textCol, int hoverBg) {
        drawIconButton(graphics, font, icon, bx, by, bw, bh, hover, textCol, hoverBg, false);
    }

    private static void drawIconButton(GuiGraphics graphics, Font font, String icon, int bx, int by, int bw, int bh, boolean hover, int textCol, int hoverBg, boolean isGlowing) {
        int bg = isGlowing ? com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getGlowBgColor(0x33000000) : (hover ? (hoverBg != 0 ? hoverBg : 0x66FFFFFF) : 0x33000000);
        int border = isGlowing ? com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getGlowBorderColor(0x55FFFFFF) : (hover ? 0xFFFFFFFF : 0x55FFFFFF);
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

    private static void drawResizeGrip(GuiGraphics graphics, int gx, int gy, int col) {
        graphics.fill(gx + 8, gy + 8, gx + 10, gy + 10, col);
        graphics.fill(gx + 4, gy + 8, gx + 6, gy + 10, col);
        graphics.fill(gx + 8, gy + 4, gx + 10, gy + 6, col);
    }

    private static void drawCornerGrip(GuiGraphics graphics, int cx, int cy, int col, boolean right, boolean bottom) {
        int sx = right ? cx - 8 : cx + 2;
        int sy = bottom ? cy - 8 : cy + 2;
        int ex = right ? cx - 2 : cx + 8;
        int ey = bottom ? cy - 2 : cy + 8;
        graphics.fill(sx, sy, ex, ey, (col & 0x00FFFFFF) | 0x44000000);
    }

    private static boolean isMouseOver(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public static ResizeDirection getResizeDirection(CanvasGroupFrame frame, double mouseX, double mouseY) {
        if (frame == null) return ResizeDirection.NONE;

        double x = frame.getPosX();
        double y = frame.getPosY();
        double w = frame.getWidth();
        double h = frame.getHeight();

        if (mouseX < x - RESIZE_MARGIN || mouseX > x + w + RESIZE_MARGIN ||
            mouseY < y - RESIZE_MARGIN || mouseY > y + h + RESIZE_MARGIN) {
            return ResizeDirection.NONE;
        }

        // Header buttons take precedence over top-right resizing
        double headerH = CanvasGroupFrame.HEADER_HEIGHT;
        if (mouseY >= y && mouseY <= y + headerH && mouseX >= x && mouseX <= x + w) {
            double btnY = y + 4;
            double rightEdge = x + w - 5;
            if (mouseX >= rightEdge - 65 && mouseX <= rightEdge && mouseY >= btnY && mouseY <= btnY + BTN_SIZE) {
                return ResizeDirection.NONE;
            }
        }

        boolean nearLeft = mouseX <= x + RESIZE_MARGIN;
        boolean nearRight = mouseX >= x + w - RESIZE_MARGIN;
        boolean nearTop = mouseY <= y + RESIZE_MARGIN;
        boolean nearBottom = mouseY >= y + h - RESIZE_MARGIN;

        boolean cornerLeft = mouseX <= x + CORNER_SIZE;
        boolean cornerRight = mouseX >= x + w - CORNER_SIZE;
        boolean cornerTop = mouseY <= y + CORNER_SIZE;
        boolean cornerBottom = mouseY >= y + h - CORNER_SIZE;

        // 4 Corners
        if (cornerTop && cornerLeft) return ResizeDirection.NORTH_WEST;
        if (cornerTop && cornerRight) return ResizeDirection.NORTH_EAST;
        if (cornerBottom && cornerLeft) return ResizeDirection.SOUTH_WEST;
        if (cornerBottom && cornerRight) return ResizeDirection.SOUTH_EAST;

        // 4 Edges
        if (nearTop) return ResizeDirection.NORTH;
        if (nearBottom) return ResizeDirection.SOUTH;
        if (nearLeft) return ResizeDirection.WEST;
        if (nearRight) return ResizeDirection.EAST;

        return ResizeDirection.NONE;
    }

    public static FrameAction getClickedAction(CanvasGroupFrame frame, double mouseX, double mouseY) {
        if (frame == null) return FrameAction.NONE;

        int x = (int) frame.getPosX();
        int y = (int) frame.getPosY();
        int w = (int) frame.getWidth();
        int h = (int) frame.getHeight();

        // 1. Header Bar Action Buttons
        int headerH = (int) CanvasGroupFrame.HEADER_HEIGHT;
        if (mouseY >= y && mouseY <= y + headerH && mouseX >= x && mouseX <= x + w) {
            int btnY = y + 4;
            int curBtnX = x + w - BTN_SIZE - 5;

            // [✕ Delete / Ungroup]
            if (isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                return FrameAction.DELETE;
            }
            curBtnX -= (BTN_SIZE + BTN_SPACING);

            // [⤢ Unfold] or [▦ Collapse]
            if (isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                return FrameAction.COLLAPSE;
            }
            curBtnX -= (BTN_SIZE + BTN_SPACING);

            if (frame.isSharedMachineFrame() && frame.isFolded()) {
                if (isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                    return FrameAction.AUTO_RATIO;
                }
                curBtnX -= (BTN_SIZE + BTN_SPACING);
                if (isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                    return FrameAction.CONFIG;
                }
                return FrameAction.NONE;
            }

            // [⛶ Auto-Fit]
            if (isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                return FrameAction.AUTOFIT;
            }
            curBtnX -= (BTN_SIZE + BTN_SPACING);

            // [✦ Color]
            if (isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                return FrameAction.COLOR;
            }

            // [⚙ Configure Shared Machine]
            if (frame.isSharedMachineFrame()) {
                curBtnX -= (BTN_SIZE + BTN_SPACING);
                if (isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                    return FrameAction.CONFIG;
                }
                curBtnX -= (BTN_SIZE + BTN_SPACING);
                if (isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                    return FrameAction.AUTO_RATIO;
                }
            }
        }

        // 2. Multi-direction Resizing Grip & Edge Hit Test
        if (!frame.isFolded()) {
            ResizeDirection dir = getResizeDirection(frame, mouseX, mouseY);
            if (dir != ResizeDirection.NONE) {
                return FrameAction.RESIZE;
            }
        }

        return FrameAction.NONE;
    }

    private static void renderFoldedMachineCard(
            GuiGraphics graphics,
            Font font,
            FlowGraph graph,
            CanvasGroupFrame frame,
            double mouseX,
            double mouseY,
            boolean isEditing,
            boolean isSelected
    ) {
        int targetW = (int) Math.max(frame.getWidth(), CanvasGroupFrame.MIN_SHARED_FRAME_WIDTH);
        if (frame.getWidth() < targetW) {
            frame.setWidth(targetW);
        }
        int x = (int) frame.getPosX();
        int y = (int) frame.getPosY();
        int w = (int) frame.getWidth();
        int color = frame.getColor();

        FlowGraphTopologyAnalyzer.FoldedPortSummary summary = FlowGraphTopologyAnalyzer.aggregateFoldedPorts(graph, frame);
        int portRows = summary.maxPortCount();
        int cardH = 64 + Math.max(portRows, 1) * 18 + 6;
        if (Math.abs(frame.getHeight() - cardH) > 0.5) {
            frame.setHeight(cardH);
        }
        int h = cardH;

        graphics.fill(x, y, x + w, y + h, 0xEE14171E);
        int borderCol = isSelected ? 0xFF00FFFF : ((color & 0x00FFFFFF) | 0xAA000000);
        graphics.renderOutline(x, y, w, h, borderCol);
        if (isSelected) {
            graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xFF00FFFF);
        }

        renderFoldedHeader(graphics, font, graph, frame, x, y, w, color, isSelected, mouseX, mouseY);
        renderFoldedCountRow(graphics, font, graph, frame, x, y, w, mouseX, mouseY);
        renderFoldedHardwareRow(graphics, font, graph, frame, x, y, w);
        renderFoldedPortRows(graphics, font, summary, x, y, w, portRows, mouseX, mouseY);
    }

    private static void renderFoldedHeader(GuiGraphics graphics, Font font, FlowGraph graph, CanvasGroupFrame frame, int x, int y, int w, int color, boolean isSelected, double mouseX, double mouseY) {
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
        String suffix = " " + Component.translatable("gui.gtcalcboard.frame.shared_pool_suffix").getString();
        String displayTitle = machineName + suffix;

        int btnCount = 4;
        int rightBoundary = x + w - (BTN_SIZE * btnCount + BTN_SPACING * (btnCount - 1) + 6);
        int maxTitleW = rightBoundary - (iconX + 22);
        String clippedTitle = displayTitle;
        if (maxTitleW > 0 && font.width(displayTitle) > maxTitleW) {
            clippedTitle = font.plainSubstrByWidth(displayTitle, Math.max(0, maxTitleW - font.width("..."))) + "...";
        }
        graphics.drawString(font, clippedTitle, iconX + 20, y + 6, 0xFFFFFFFF, true);

        int btnY = y + 4;
        int curBtnX = x + w - BTN_SIZE - 5;

        boolean delHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawIconButton(graphics, font, "✕", curBtnX, btnY, BTN_SIZE, BTN_SIZE, delHover, 0xFFFF5555, 0x55FF0000);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        boolean unfoldHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawIconButton(graphics, font, "⤢", curBtnX, btnY, BTN_SIZE, BTN_SIZE, unfoldHover, 0xFF38BDF8, 0x550284C7);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        boolean ratioHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawIconButton(graphics, font, "⚖", curBtnX, btnY, BTN_SIZE, BTN_SIZE, ratioHover, 0xFF60A5FA, 0x553B82F6);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        boolean cfgHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawIconButton(graphics, font, "⚙", curBtnX, btnY, BTN_SIZE, BTN_SIZE, cfgHover, 0xFFFCD34D, 0x55F59E0B);
    }

    private static void renderFoldedCountRow(GuiGraphics graphics, Font font, FlowGraph graph, CanvasGroupFrame frame, int x, int y, int w, double mouseX, double mouseY) {
        int ctrlY = y + 26;
        graphics.drawString(font, "Count:", x + 6, ctrlY + 3, 0xFFAAAAAA, false);

        double totalDuty = frame.computeTotalMachineDuty(graph);
        String countText = String.format(Locale.ROOT, "%.2f", totalDuty);
        int countMinusX = x + 38;
        drawBtn(graphics, font, "-", countMinusX, ctrlY, 14, 14, mouseX, mouseY, 0xFFFFFFFF);

        int countBoxX = countMinusX + 16;
        int countBoxW = Math.max(32, font.width(countText) + 8);
        graphics.fill(countBoxX, ctrlY, countBoxX + countBoxW, ctrlY + 14, 0xFF1E293B);
        graphics.renderOutline(countBoxX, ctrlY, countBoxW, 14, 0xFF475569);
        graphics.drawString(font, countText, countBoxX + (countBoxW - font.width(countText)) / 2, ctrlY + 3, 0xFFFFFFAA, false);

        int countPlusX = countBoxX + countBoxW + 2;
        drawBtn(graphics, font, "+", countPlusX, ctrlY, 14, 14, mouseX, mouseY, 0xFFFFFFFF);
        drawBtn(graphics, font, "/2", countPlusX + 16, ctrlY, 16, 14, mouseX, mouseY, 0xFFFFFFFF);
        drawBtn(graphics, font, "x2", countPlusX + 34, ctrlY, 16, 14, mouseX, mouseY, 0xFFFFFFFF);

        int recipeCount = frame.getEnclosedNodes(graph).size();
        String rBadge = "■ " + Component.translatable("gui.gtcalcboard.frame.recipes_count", recipeCount).getString();
        int rBadgeW = font.width(rBadge);
        graphics.drawString(font, rBadge, x + w - rBadgeW - 6, ctrlY + 3, 0xFF34D399, false);
    }

    private static void renderFoldedHardwareRow(GuiGraphics graphics, Font font, FlowGraph graph, CanvasGroupFrame frame, int x, int y, int w) {
        int hwY = y + 44;
        com.gtceu.calcboard.api.type.GTVoltageTier tier = frame.getSharedVoltageTier(graph);
        com.gtceu.calcboard.api.type.OverclockMode ocMode = frame.getSharedOverclockMode(graph);
        double totalEUt = frame.computeSharedTotalEUt(graph);

        String tierStr = "[" + tier.name() + "]";
        graphics.drawString(font, tierStr, x + 6, hwY + 3, tier.getColor(), false);
        int tierW = font.width(tierStr);

        String ocStr = "[" + ocMode.name() + "]";
        graphics.drawString(font, ocStr, x + 8 + tierW, hwY + 3, 0xFF94A3B8, false);

        String euStr = String.format(Locale.ROOT, "%.1f EU/t (%s)", totalEUt, tier.name());
        int euW = font.width(euStr);
        graphics.drawString(font, euStr, x + w - euW - 6, hwY + 3, 0xFFFCD34D, false);

        graphics.fill(x + 4, y + 62, x + w - 4, y + 63, 0x33FFFFFF);
    }

    private static void renderFoldedPortRows(GuiGraphics graphics, Font font, FlowGraphTopologyAnalyzer.FoldedPortSummary summary, int x, int y, int w, int portRows, double mouseX, double mouseY) {
        for (int r = 0; r < portRows; r++) {
            int rowY = y + 64 + r * 18;

            if (r < summary.inputs().size()) {
                FlowGraphTopologyAnalyzer.AggregatedFoldedPort in = summary.inputs().get(r);
                int portX = x + 3;
                int portY = rowY + 5;
                boolean inHover = mouseX >= x && mouseX <= x + 32 && mouseY >= rowY && mouseY <= rowY + 16;
                graphics.fill(portX, portY, portX + 6, portY + 6, inHover ? 0xFF67E8F9 : 0xFF38BDF8);
                if (inHover) {
                    graphics.renderOutline(x + 1, rowY, 30, 16, 0xFF38BDF8);
                }
                IngredientRenderer.render(graphics, in.ingredient(), x + 12, rowY - 1);
                if (in.hasDeficit()) {
                    String defText = String.format(Locale.ROOT, "+%s -%s \u26A0",
                            FormatUtil.formatRate(in.actualRate(), in.ingredient().isFluid()),
                            FormatUtil.formatRate(in.nominalRate(), in.ingredient().isFluid()));
                    graphics.drawString(font, defText, x + 32, rowY + 4, 0xFFEF4444, false);
                } else {
                    String rateText = "-" + FormatUtil.formatRate(in.nominalRate(), in.ingredient().isFluid());
                    graphics.drawString(font, rateText, x + 32, rowY + 4, 0xFFE2E8F0, false);
                }
            }

            if (r < summary.outputs().size()) {
                FlowGraphTopologyAnalyzer.AggregatedFoldedPort out = summary.outputs().get(r);
                int portX = x + w - 9;
                int portY = rowY + 5;
                boolean outHover = mouseX >= x + w - 32 && mouseX <= x + w && mouseY >= rowY && mouseY <= rowY + 16;
                graphics.fill(portX, portY, portX + 6, portY + 6, outHover ? 0xFF86EFAC : 0xFF4ADE80);
                if (outHover) {
                    graphics.renderOutline(x + w - 31, rowY, 30, 16, 0xFF4ADE80);
                }
                IngredientRenderer.render(graphics, out.ingredient(), x + w - 28, rowY - 1);
                String rateText = "+" + FormatUtil.formatRate(out.nominalRate(), out.ingredient().isFluid());
                int txtW = font.width(rateText);
                graphics.drawString(font, rateText, x + w - 32 - txtW, rowY + 4, 0xFF4ADE80, false);
            }
        }
    }

    public static FoldedPortHit findHoveredFoldedPort(FlowGraph graph, double canvasX, double canvasY) {
        if (graph == null) return null;
        for (CanvasGroupFrame frame : graph.getFrames()) {
            FoldedPortHit hit = checkFramePortHit(graph, frame, canvasX, canvasY);
            if (hit != null) return hit;
        }
        return null;
    }

    private static FoldedPortHit checkFramePortHit(FlowGraph graph, CanvasGroupFrame frame, double canvasX, double canvasY) {
        if (frame == null || !frame.isSharedMachineFrame() || !frame.isFolded()) return null;
        double fx = frame.getPosX();
        double fy = frame.getPosY();
        double fw = frame.getWidth();
        double fh = frame.getHeight();
        if (canvasX < fx || canvasX > fx + fw || canvasY < fy || canvasY > fy + fh) return null;

        FlowGraphTopologyAnalyzer.FoldedPortSummary summary = FlowGraphTopologyAnalyzer.aggregateFoldedPorts(graph, frame);
        for (int r = 0; r < summary.maxPortCount(); r++) {
            double rowY = fy + 64.0 + r * 18.0;
            if (canvasY < rowY || canvasY > rowY + 16.0) continue;

            if (canvasX >= fx && canvasX <= fx + 32.0 && r < summary.inputs().size()) {
                return new FoldedPortHit(frame, true, r, summary.inputs().get(r));
            }
            if (canvasX >= fx + fw - 32.0 && canvasX <= fx + fw && r < summary.outputs().size()) {
                return new FoldedPortHit(frame, false, r, summary.outputs().get(r));
            }
        }
        return null;
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

    private static boolean renderFoldedFrameTooltip(GuiGraphics graphics, Font font, FlowGraph graph, CanvasGroupFrame frame, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
        int x = (int) frame.getPosX();
        int y = (int) frame.getPosY();
        int w = (int) frame.getWidth();
        int h = (int) frame.getHeight();

        if (canvasMouseX < x || canvasMouseX > x + w || canvasMouseY < y || canvasMouseY > y + h) {
            return false;
        }

        int btnY = y + 4;
        int curBtnX = x + w - BTN_SIZE - 5;
        if (isMouseOver(canvasMouseX, canvasMouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§c✕ ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_delete")), mouseX, mouseY);
            return true;
        }
        curBtnX -= (BTN_SIZE + BTN_SPACING);
        if (isMouseOver(canvasMouseX, canvasMouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§b⤢ ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_unfold")), mouseX, mouseY);
            return true;
        }
        curBtnX -= (BTN_SIZE + BTN_SPACING);
        if (isMouseOver(canvasMouseX, canvasMouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
            String capacityStr = String.format(Locale.ROOT, "%.1f", frame.getTargetPoolCapacity());
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§b⚖ ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_auto_ratio", capacityStr)), mouseX, mouseY);
            return true;
        }
        curBtnX -= (BTN_SIZE + BTN_SPACING);
        if (isMouseOver(canvasMouseX, canvasMouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§e⚙ ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_config")), mouseX, mouseY);
            return true;
        }

        int ctrlY = y + 26;
        int countMinusX = x + 38;
        int countBoxX = countMinusX + 16;
        if (canvasMouseY >= ctrlY && canvasMouseY <= ctrlY + 14 && canvasMouseX >= countBoxX && canvasMouseX <= countBoxX + 40) {
            renderSharedMachineBreakdownTooltip(graphics, font, graph, frame, mouseX, mouseY);
            return true;
        }

        FlowGraphTopologyAnalyzer.FoldedPortSummary summary = FlowGraphTopologyAnalyzer.aggregateFoldedPorts(graph, frame);
        for (int r = 0; r < summary.maxPortCount(); r++) {
            int rowY = y + 64 + r * 18;
            if (canvasMouseY < rowY || canvasMouseY > rowY + 16) continue;
            if (renderFoldedPortTooltip(graphics, font, summary, r, x, w, mouseX, mouseY, canvasMouseX)) {
                return true;
            }
        }

        return false;
    }

    private static boolean renderFoldedPortTooltip(GuiGraphics graphics, Font font, FlowGraphTopologyAnalyzer.FoldedPortSummary summary, int r, int x, int w, int mouseX, int mouseY, double canvasMouseX) {
        if (r < summary.inputs().size() && canvasMouseX >= x + 2 && canvasMouseX <= x + w / 2) {
            var in = summary.inputs().get(r);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(in.ingredient().getDisplayName()));
            lines.add(Component.literal("§7Nominal Demand: §e" + FormatUtil.formatRate(in.nominalRate(), in.ingredient().isFluid())));
            lines.add(Component.literal("§7Actual Rate: §f" + FormatUtil.formatRate(in.actualRate(), in.ingredient().isFluid())));
            if (in.hasDeficit()) {
                lines.add(Component.literal("§c⚠ Upstream supply deficit!"));
            }
            BoardTooltipRenderer.renderComponentTooltip(graphics, font, lines, mouseX, mouseY);
            return true;
        }
        if (r < summary.outputs().size() && canvasMouseX >= x + w / 2 && canvasMouseX <= x + w - 2) {
            var out = summary.outputs().get(r);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(out.ingredient().getDisplayName()));
            lines.add(Component.literal("§7Production Rate: §a+" + FormatUtil.formatRate(out.nominalRate(), out.ingredient().isFluid())));
            BoardTooltipRenderer.renderComponentTooltip(graphics, font, lines, mouseX, mouseY);
            return true;
        }
        return false;
    }
}




