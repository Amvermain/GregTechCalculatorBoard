package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.CanvasGroupFrame;
import com.gtceu.calcboard.api.FlowGraph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Renders visual group frames, headers, action buttons, sticky notes, and resize grips on the canvas.
 */
public class CanvasGroupFrameRenderer {

    public enum FrameAction {
        NONE, COLOR, COLLAPSE, NOTE, DELETE, RESIZE
    }

    public static final int BTN_SIZE = 16;
    public static final int BTN_SPACING = 3;

    public static void renderFrames(GuiGraphics graphics, FlowGraph graph, double canvasMouseX, double canvasMouseY, String activeEditingFrameId) {
        if (graph == null || graph.getFrames().isEmpty()) return;

        Font font = Minecraft.getInstance().font;

        for (CanvasGroupFrame frame : graph.getFrames()) {
            renderSingleFrame(graphics, font, frame, canvasMouseX, canvasMouseY, frame.getId().equals(activeEditingFrameId));
        }
    }

    private static void renderSingleFrame(GuiGraphics graphics, Font font, CanvasGroupFrame frame, double mouseX, double mouseY, boolean isEditingTitle) {
        int x = (int) frame.getPosX();
        int y = (int) frame.getPosY();
        int w = (int) frame.getWidth();
        int h = (int) frame.getHeight();
        int color = frame.getColor();

        // 1. Semi-transparent background body (Alpha = 0x33 ~ 20%)
        int bodyBg = (color & 0x00FFFFFF) | 0x28000000;
        int borderCol = (color & 0x00FFFFFF) | 0xCC000000;
        graphics.fill(x, y, x + w, y + h, bodyBg);
        graphics.renderOutline(x, y, w, h, borderCol);

        // 2. Header bar background (Alpha = 0xAA)
        int headerH = (int) CanvasGroupFrame.HEADER_HEIGHT;
        int headerBg = (color & 0x00FFFFFF) | 0x99000000;
        graphics.fill(x, y, x + w, y + headerH, headerBg);
        graphics.fill(x, y + headerH - 1, x + w, y + headerH, borderCol);

        // 3. Header Title (Clean without emoji VS16 glyph)
        String title = isEditingTitle ? frame.getTitle() + "_" : frame.getTitle();
        graphics.drawString(font, title, x + 6, y + 8, 0xFFFFFFFF, true);

        // 4. Header Action Buttons (Right-aligned)
        // [🎨 Color] [📦 Collapse] [📝 Note] [✕ Delete]
        int btnY = y + 4;
        int curBtnX = x + w - BTN_SIZE - 5;

        // [✕ Delete / Ungroup]
        boolean delHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawIconButton(graphics, font, "✕", curBtnX, btnY, BTN_SIZE, BTN_SIZE, delHover, 0xFFFF5555, 0x55FF0000);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        // [📝 Note]
        boolean noteHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        boolean hasNote = frame.getNote() != null && !frame.getNote().trim().isEmpty();
        drawIconButton(graphics, font, "📝", curBtnX, btnY, BTN_SIZE, BTN_SIZE, noteHover, hasNote ? 0xFFFFD700 : 0xFFDDDDDD, hasNote ? 0x66FFD700 : 0x44000000);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        // [📦 Collapse to Module]
        boolean colHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        boolean isColGlowing = com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isFrameCollapseButtonGlowing(frame.getId());
        drawIconButton(graphics, font, "📦", curBtnX, btnY, BTN_SIZE, BTN_SIZE, colHover, 0xFF60A5FA, 0x553B82F6, isColGlowing);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        // [🎨 Color Picker]
        boolean colorHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawColorCycleButton(graphics, curBtnX, btnY, BTN_SIZE, BTN_SIZE, colorHover, color);

        // 5. Sticky Note Badge (Bottom-Left)
        if (hasNote) {
            String notePreview = frame.getNote();
            if (notePreview.length() > 36) notePreview = notePreview.substring(0, 33) + "...";
            String noteText = "📝 " + notePreview;
            int noteW = font.width(noteText) + 12;
            int noteH = 14;
            int noteX = x + 8;
            int noteY = y + h - noteH - 6;

            graphics.fill(noteX, noteY, noteX + noteW, noteY + noteH, 0xCC1E293B);
            graphics.renderOutline(noteX, noteY, noteW, noteH, (color & 0x00FFFFFF) | 0x88000000);
            graphics.drawString(font, noteText, noteX + 6, noteY + 3, 0xFFE2E8F0, false);
        }

        // 6. Resize Grip (Bottom-Right corner)
        drawResizeGrip(graphics, x + w - 12, y + h - 12, borderCol);
    }

    public static void renderFrameTooltips(GuiGraphics graphics, Font font, FlowGraph graph, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
        if (graph == null || graph.getFrames().isEmpty()) return;

        for (CanvasGroupFrame frame : graph.getFrames()) {
            int x = (int) frame.getPosX();
            int y = (int) frame.getPosY();
            int w = (int) frame.getWidth();
            int h = (int) frame.getHeight();

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

                // [📝 Note]
                if (isMouseOver(canvasMouseX, canvasMouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                    graphics.renderTooltip(font, Component.literal("§a📝 ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_note")), mouseX, mouseY);
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

            // Note badge hover
            boolean hasNote = frame.getNote() != null && !frame.getNote().trim().isEmpty();
            if (hasNote) {
                String notePreview = frame.getNote();
                if (notePreview.length() > 36) notePreview = notePreview.substring(0, 33) + "...";
                String noteText = "📝 " + notePreview;
                int noteW = font.width(noteText) + 12;
                int noteH = 14;
                int noteX = x + 8;
                int noteY = y + h - noteH - 6;
                if (isMouseOver(canvasMouseX, canvasMouseY, noteX, noteY, noteW, noteH)) {
                    graphics.renderTooltip(font, java.util.List.of(
                        Component.literal("§e📝 ").append(Component.translatable("gui.gtcalcboard.frame.tooltip_note_badge")),
                        Component.literal("§7" + frame.getNote())
                    ), java.util.Optional.empty(), mouseX, mouseY);
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
        // Diagonal 3 dots / small lines
        graphics.fill(gx + 8, gy + 8, gx + 10, gy + 10, col);
        graphics.fill(gx + 4, gy + 8, gx + 6, gy + 10, col);
        graphics.fill(gx + 8, gy + 4, gx + 10, gy + 6, col);
    }

    private static boolean isMouseOver(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public static FrameAction getClickedAction(CanvasGroupFrame frame, double mouseX, double mouseY) {
        if (frame == null) return FrameAction.NONE;

        int x = (int) frame.getPosX();
        int y = (int) frame.getPosY();
        int w = (int) frame.getWidth();
        int h = (int) frame.getHeight();

        // 1. Resize Grip
        if (mouseX >= x + w - 14 && mouseX <= x + w && mouseY >= y + h - 14 && mouseY <= y + h) {
            return FrameAction.RESIZE;
        }

        // 2. Header Bar Actions
        int headerH = (int) CanvasGroupFrame.HEADER_HEIGHT;
        if (mouseY >= y && mouseY <= y + headerH && mouseX >= x && mouseX <= x + w) {
            int btnY = y + 4;
            int curBtnX = x + w - BTN_SIZE - 5;

            // [✕ Delete / Ungroup]
            if (isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                return FrameAction.DELETE;
            }
            curBtnX -= (BTN_SIZE + BTN_SPACING);

            // [📝 Note]
            if (isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                return FrameAction.NOTE;
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

            // Clicked on header body -> NONE (handled as drag/double-click by CanvasInteractionHandler)
            return FrameAction.NONE;
        }

        return FrameAction.NONE;
    }
}
