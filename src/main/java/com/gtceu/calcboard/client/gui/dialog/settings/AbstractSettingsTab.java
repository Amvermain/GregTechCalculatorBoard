package com.gtceu.calcboard.client.gui.dialog.settings;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.BoardSettingsDialog;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Base class for BoardSettingsDialog tabs providing shared UI primitives and callbacks.
 */
public abstract class AbstractSettingsTab implements ISettingsTab {

    protected final BoardSettingsDialog dialog;
    protected final BoardScreen parent;

    protected AbstractSettingsTab(BoardSettingsDialog dialog, BoardScreen parent) {
        this.dialog = dialog;
        this.parent = parent;
    }

    protected void drawCheckbox(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY, String label, boolean checked) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        int boxSize = 14;
        int boxX = x + 4;
        int boxY = y + (h - boxSize) / 2;

        int bg = hover ? 0xFF2A364D : 0xFF1B2230;
        int border = checked ? 0xFF55FF88 : (hover ? 0xFF5B9BD5 : 0xFF3A4B66);

        graphics.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, bg);
        graphics.renderOutline(boxX, boxY, boxSize, boxSize, border);

        if (checked) {
            graphics.drawCenteredString(font, "✔", boxX + boxSize / 2, boxY + 3, 0xFF55FF88);
        }

        graphics.drawString(font, label, boxX + boxSize + 8, y + (h - 8) / 2, hover ? 0xFFFFFFFF : 0xFFDDDDDD, false);
    }

    protected void drawButton(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textCol, int bg, int border) {
        boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        int actualBg = hover ? (bg + 0x00151515) : bg;
        int actualBorder = hover ? 0xFF657595 : border;

        graphics.fill(bx, by, bx + bw, by + bh, actualBg);
        graphics.renderOutline(bx, by, bw, bh, actualBorder);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + (bh - 8) / 2, hover ? 0xFFFFFFFF : textCol);
    }

    protected boolean isInsideRow(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    protected void onSettingsChanged() {
        dialog.onSettingsChanged();
    }

    protected void playClickSound() {
        dialog.playClickSound();
    }
}
