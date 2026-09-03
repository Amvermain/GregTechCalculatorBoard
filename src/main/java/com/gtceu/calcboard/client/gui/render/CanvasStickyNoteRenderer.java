package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Renders independent, standalone sticky notes and comment cards on the canvas.
 */
public class CanvasStickyNoteRenderer {

    public enum NoteAction {
        NONE, COLOR, DELETE, RESIZE
    }

    public static final int BTN_SIZE = 14;
    public static final int BTN_SPACING = 2;

    public static void renderNotes(GuiGraphics graphics, FlowGraph graph, double canvasMouseX, double canvasMouseY, java.util.Set<String> selectedNoteIds) {
        renderNotes(graphics, graph, canvasMouseX, canvasMouseY, selectedNoteIds, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public static void renderNotes(GuiGraphics graphics, FlowGraph graph, double canvasMouseX, double canvasMouseY, java.util.Set<String> selectedNoteIds,
                                   double screenLeft, double screenRight, double screenTop, double screenBottom) {
        if (graph == null || graph.getStickyNotes().isEmpty()) return;

        Font font = Minecraft.getInstance().font;
        for (CanvasStickyNote note : graph.getStickyNotes()) {
            if (note == null) continue;
            double nx = note.getPosX();
            double ny = note.getPosY();
            double nw = note.getWidth();
            double nh = note.getHeight();
            if (nx + nw < screenLeft || nx > screenRight || ny + nh < screenTop || ny > screenBottom) {
                continue;
            }
            boolean isSelected = selectedNoteIds != null && selectedNoteIds.contains(note.getId());
            renderSingleNote(graphics, font, note, canvasMouseX, canvasMouseY, isSelected);
        }
    }

    private static void renderSingleNote(GuiGraphics graphics, Font font, CanvasStickyNote note, double mouseX, double mouseY, boolean selected) {
        int x = (int) note.getPosX();
        int y = (int) note.getPosY();
        int w = (int) note.getWidth();
        int h = (int) note.getHeight();
        int color = note.getColor();

        // Selection highlight glow
        if (selected) {
            graphics.renderOutline(x - 2, y - 2, w + 4, h + 4, 0xFF00E5FF);
            graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, 0x8800E5FF);
        }

        // 1. Semi-transparent dark paper background
        int bodyBg = 0xEE1E2430;
        int borderCol = (color & 0x00FFFFFF) | 0xEE000000;
        graphics.fill(x, y, x + w, y + h, bodyBg);
        graphics.renderOutline(x, y, w, h, borderCol);

        // 2. Header bar
        int headerH = (int) CanvasStickyNote.HEADER_HEIGHT;
        int headerBg = (color & 0x00FFFFFF) | 0x99000000;
        graphics.fill(x, y, x + w, y + headerH, headerBg);
        graphics.fill(x, y + headerH - 1, x + w, y + headerH, borderCol);

        // 3. Header Title
        graphics.drawString(font, "📌 " + note.getTitle(), x + 4, y + 5, 0xFFFFFFFF, true);

        // 4. Action buttons on header
        int btnY = y + 2;
        int curBtnX = x + w - BTN_SIZE - 3;

        // [✕ Delete]
        boolean delHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawIconButton(graphics, font, "✕", curBtnX, btnY, BTN_SIZE, BTN_SIZE, delHover, 0xFFFF5555, 0x55FF0000);
        curBtnX -= (BTN_SIZE + BTN_SPACING);

        // [🎨 Color]
        boolean colorHover = isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE);
        drawColorCycleButton(graphics, curBtnX, btnY, BTN_SIZE, BTN_SIZE, colorHover, color);

        // 5. Multi-line note content
        if (note.getContent() != null && !note.getContent().isEmpty()) {
            List<FormattedCharSequence> lines = font.split(FormattedText.of(note.getContent()), w - 12);
            int textY = y + headerH + 5;
            for (FormattedCharSequence line : lines) {
                if (textY + 9 > y + h - 4) break; // Clip if overflowing height
                graphics.drawString(font, line, x + 6, textY, 0xFFE2E8F0, false);
                textY += 10;
            }
        }

        // 6. Resize Grip
        drawResizeGrip(graphics, x + w - 10, y + h - 10, borderCol);
    }

    public static void renderNoteTooltips(GuiGraphics graphics, Font font, FlowGraph graph, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
        if (graph == null || graph.getStickyNotes().isEmpty()) return;

        for (CanvasStickyNote note : graph.getStickyNotes()) {
            int x = (int) note.getPosX();
            int y = (int) note.getPosY();
            int w = (int) note.getWidth();

            int headerH = (int) CanvasStickyNote.HEADER_HEIGHT;
            if (canvasMouseY >= y && canvasMouseY <= y + headerH && canvasMouseX >= x && canvasMouseX <= x + w) {
                int btnY = y + 2;
                int curBtnX = x + w - BTN_SIZE - 3;

                // [✕ Delete]
                if (isMouseOver(canvasMouseX, canvasMouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                    graphics.renderTooltip(font, Component.literal("§c✕ ").append(Component.translatable("gui.gtcalcboard.note.tooltip_delete")), mouseX, mouseY);
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

    private static void drawResizeGrip(GuiGraphics graphics, int gx, int gy, int col) {
        graphics.fill(gx + 6, gy + 6, gx + 8, gy + 8, col);
        graphics.fill(gx + 3, gy + 6, gx + 5, gy + 8, col);
        graphics.fill(gx + 6, gy + 3, gx + 8, gy + 5, col);
    }

    private static boolean isMouseOver(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public static NoteAction getClickedAction(CanvasStickyNote note, double mouseX, double mouseY) {
        if (note == null) return NoteAction.NONE;

        int x = (int) note.getPosX();
        int y = (int) note.getPosY();
        int w = (int) note.getWidth();
        int h = (int) note.getHeight();

        // 1. Resize Grip
        if (mouseX >= x + w - 12 && mouseX <= x + w && mouseY >= y + h - 12 && mouseY <= y + h) {
            return NoteAction.RESIZE;
        }

        // 2. Header Bar Actions
        int headerH = (int) CanvasStickyNote.HEADER_HEIGHT;
        if (mouseY >= y && mouseY <= y + headerH && mouseX >= x && mouseX <= x + w) {
            int btnY = y + 2;
            int curBtnX = x + w - BTN_SIZE - 3;

            // [✕ Delete]
            if (isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                return NoteAction.DELETE;
            }
            curBtnX -= (BTN_SIZE + BTN_SPACING);

            // [🎨 Color]
            if (isMouseOver(mouseX, mouseY, curBtnX, btnY, BTN_SIZE, BTN_SIZE)) {
                return NoteAction.COLOR;
            }

            return NoteAction.NONE;
        }

        return NoteAction.NONE;
    }
}


