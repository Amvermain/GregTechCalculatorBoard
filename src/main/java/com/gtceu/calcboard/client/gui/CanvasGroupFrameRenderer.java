package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.CanvasGroupFrame;
import com.gtceu.calcboard.api.FlowGraph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Renders visual group frames, headers, action buttons, and multi-edge resize handles on the canvas.
 */
public class CanvasGroupFrameRenderer {

    public enum FrameAction {
        NONE, COLOR, COLLAPSE, DELETE, RESIZE
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
        if (graph == null || graph.getFrames().isEmpty()) return;

        Font font = Minecraft.getInstance().font;

        for (CanvasGroupFrame frame : graph.getFrames()) {
            boolean isSelected = selectedFrameIds != null && selectedFrameIds.contains(frame.getId());
            renderSingleFrame(graphics, font, frame, canvasMouseX, canvasMouseY, frame.getId().equals(activeEditingFrameId), isSelected);
        }
    }

    private static void renderSingleFrame(GuiGraphics graphics, Font font, CanvasGroupFrame frame, double mouseX, double mouseY, boolean isEditingTitle, boolean selected) {
        int x = (int) frame.getPosX();
        int y = (int) frame.getPosY();
        int w = (int) frame.getWidth();
        int h = (int) frame.getHeight();
        int color = frame.getColor();

        // Selection highlight glow
        if (selected) {
            graphics.renderOutline(x - 2, y - 2, w + 4, h + 4, 0xFF00E5FF);
            graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, 0x8800E5FF);
        }

        // 1. Semi-transparent background body (Alpha = 0x28 ~ 16%)
        int bodyBg = (color & 0x00FFFFFF) | 0x28000000;
        int borderCol = (color & 0x00FFFFFF) | 0xCC000000;
        graphics.fill(x, y, x + w, y + h, bodyBg);
        graphics.renderOutline(x, y, w, h, borderCol);

        // 2. Header Bar Background & Border
        int headerH = (int) CanvasGroupFrame.HEADER_HEIGHT;
        int headerBg = (color & 0x00FFFFFF) | 0x66000000;
        graphics.fill(x, y, x + w, y + headerH, headerBg);
        graphics.fill(x, y + headerH - 1, x + w, y + headerH, borderCol);

        // 3. Header Title
        String title = isEditingTitle ? frame.getTitle() + "_" : frame.getTitle();
        graphics.drawString(font, title, x + 6, y + 8, 0xFFFFFFFF, true);

        // 4. Header Action Buttons (Right-aligned)
        // [🎨 Color] [📦 Collapse] [✕ Delete]
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

        // [🎨 Color Picker]
        boolean colorHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawColorCycleButton(graphics, curBtnX, btnY, BTN_SIZE, BTN_SIZE, colorHover, color);

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
                    graphics.renderTooltip(font, Component.literal("§c✕ ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_delete")), mouseX, mouseY);
                    return;
                }
                curBtnX -= (BTN_SIZE + BTN_SPACING);

                // [📦 Collapse]
                if (isMouseOver(canvasMouseX, canvasMouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                    graphics.renderTooltip(font, Component.literal("§b📦 ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_collapse")), mouseX, mouseY);
                    return;
                }
                curBtnX -= (BTN_SIZE + BTN_SPACING);

                // [🎨 Color]
                if (isMouseOver(canvasMouseX, canvasMouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                    graphics.renderTooltip(font, Component.literal("§e🎨 ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_color")), mouseX, mouseY);
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

            // [🎨 Color]
            if (isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                return FrameAction.COLOR;
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
