package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders visual group frames, headers, action buttons, and multi-edge resize handles on the canvas.
 */
public class CanvasGroupFrameRenderer {

    public enum FrameAction {
        NONE, COLOR, COLLAPSE, DELETE, RESIZE, CONFIG, AUTOFIT
    }

    public enum ResizeDirection {
        NONE, NORTH, SOUTH, WEST, EAST, NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST
    }

    public static final int BTN_SIZE = 16;
    public static final int BTN_SPACING = 3;
    public static final double RESIZE_MARGIN = 6.0;
    public static final double CORNER_SIZE = 14.0;

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
        String prefix = frame.isSharedMachineFrame() ? "🔗 " : (frame.isCompoundFrame() ? "📦 " : "");
        String displayTitle = prefix + title;

        int btnCount = (frame.isSharedMachineFrame() ? 4 : 3) + 1;
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
        // [⚙ Config] [🎨 Color] [⛶ Auto-Fit] [📦 Collapse] [✕ Delete]
        int btnY = y + 4;
        int curBtnX = x + w - BTN_SIZE - 5;

        // [✕ Delete / Ungroup]
        boolean delHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawIconButton(graphics, font, "✕", curBtnX, btnY, BTN_SIZE, BTN_SIZE, delHover, 0xFFFF5555, 0x55FF0000);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        // [📦 Collapse to Module]
        boolean colHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        boolean isColGlowing = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isFrameCollapseButtonGlowing(frame.getId());
        drawIconButton(graphics, font, "📦", curBtnX, btnY, BTN_SIZE, BTN_SIZE, colHover, 0xFF60A5FA, 0x553B82F6, isColGlowing);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        // [⛶ Auto-Fit to Contents]
        boolean fitHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawIconButton(graphics, font, "⛶", curBtnX, btnY, BTN_SIZE, BTN_SIZE, fitHover, 0xFF34D399, 0x55059669);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        // [🎨 Color Picker]
        boolean colorHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawColorCycleButton(graphics, curBtnX, btnY, BTN_SIZE, BTN_SIZE, colorHover, color);

        // [⚙ Configure Shared Machine Hardware]
        if (frame.isSharedMachineFrame()) {
            curBtnX -= (BTN_SIZE + BTN_SPACING);
            boolean cfgHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
            drawIconButton(graphics, font, "⚙", curBtnX, btnY, BTN_SIZE, BTN_SIZE, cfgHover, 0xFFFCD34D, 0x55F59E0B);
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
            int x = (int) frame.getPosX();
            int y = (int) frame.getPosY();
            int w = (int) frame.getWidth();

            int headerH = (int) CanvasGroupFrame.HEADER_HEIGHT;
            if (canvasMouseY >= y && canvasMouseY <= y + headerH && canvasMouseX >= x && canvasMouseX <= x + w) {
                int btnY = y + 4;
                int curBtnX = x + w - BTN_SIZE - 5;

                // [✕ Delete]
                if (isMouseOver(canvasMouseX, canvasMouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                    BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§c✕ ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_delete")), mouseX, mouseY);
                    return;
                }
                curBtnX -= (BTN_SIZE + BTN_SPACING);

                // [📦 Collapse]
                if (isMouseOver(canvasMouseX, canvasMouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                    BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§b📦 ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_collapse")), mouseX, mouseY);
                    return;
                }
                curBtnX -= (BTN_SIZE + BTN_SPACING);

                // [⛶ Auto-Fit]
                if (isMouseOver(canvasMouseX, canvasMouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                    BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§a⛶ ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_autofit")), mouseX, mouseY);
                    return;
                }
                curBtnX -= (BTN_SIZE + BTN_SPACING);

                // [🎨 Color]
                if (isMouseOver(canvasMouseX, canvasMouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                    BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§e🎨 ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_color")), mouseX, mouseY);
                    return;
                }

                // [⚙ Configure Shared Machine]
                if (frame.isSharedMachineFrame()) {
                    curBtnX -= (BTN_SIZE + BTN_SPACING);
                    if (isMouseOver(canvasMouseX, canvasMouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                        BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§e⚙ ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_config")), mouseX, mouseY);
                        return;
                    }
                }

                // If hovering on header text / badge area of Shared Machine Frame -> Show comprehensive Breakdown tooltip
                if (frame.isSharedMachineFrame()) {
                    double totalDuty = frame.computeTotalMachineDuty(graph);
                    int reqMachines = frame.computeRequiredMachines(graph);
                    boolean isCompatible = frame.isMachineCompatible(graph);

                    List<Component> tooltipLines = new ArrayList<>();
                    tooltipLines.add(Component.literal("§b🔗 " + frame.getTitle() + " §7(").append(Component.translatable("gui.gtcalcboard.frame.shared_machine_tag")).append(Component.literal("§7)")));
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
                    return;
                }
            }
        }
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

            // [📦 Collapse]
            if (isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                return FrameAction.COLLAPSE;
            }
            curBtnX -= (BTN_SIZE + BTN_SPACING);

            // [⛶ Auto-Fit]
            if (isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                return FrameAction.AUTOFIT;
            }
            curBtnX -= (BTN_SIZE + BTN_SPACING);

            // [🎨 Color]
            if (isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                return FrameAction.COLOR;
            }

            // [⚙ Configure Shared Machine]
            if (frame.isSharedMachineFrame()) {
                curBtnX -= (BTN_SIZE + BTN_SPACING);
                if (isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                    return FrameAction.CONFIG;
                }
            }
        }

        // 2. Multi-direction Resizing Grip & Edge Hit Test
        ResizeDirection dir = getResizeDirection(frame, mouseX, mouseY);
        if (dir != ResizeDirection.NONE) {
            return FrameAction.RESIZE;
        }

        return FrameAction.NONE;
    }
}



