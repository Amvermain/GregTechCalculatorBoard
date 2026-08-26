package com.gtceu.calcboard.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.gtceu.calcboard.api.BoardManager;

public class HotkeyHudWidget {
    private final BoardScreen screen;
    private boolean expanded;

    private static final int EXPANDED_WIDTH = 195;
    private static final int EXPANDED_HEIGHT = 184;
    private static final int COLLAPSED_WIDTH = 22;
    private static final int COLLAPSED_HEIGHT = 20;

    public HotkeyHudWidget(BoardScreen screen) {
        this.screen = screen;
        this.expanded = BoardManager.getInstance().isHotkeyHudExpanded();
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        BoardManager.getInstance().setHotkeyHudExpanded(expanded);
    }

    public void toggle() {
        setExpanded(!this.expanded);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int screenW = screen.width;
        int screenH = screen.height;

        if (!expanded) {
            // Collapsed Chip [?] at Bottom-Left
            int chipX = 8;
            int chipY = screenH - COLLAPSED_HEIGHT - 8;

            boolean hovered = mouseX >= chipX && mouseX <= chipX + COLLAPSED_WIDTH && mouseY >= chipY && mouseY <= chipY + COLLAPSED_HEIGHT;
            int bgColor = hovered ? 0xEE1E293B : 0xAA0F172A;
            int borderColor = hovered ? 0xFF38BDF8 : 0xFF475569;

            graphics.fill(chipX, chipY, chipX + COLLAPSED_WIDTH, chipY + COLLAPSED_HEIGHT, bgColor);
            graphics.renderOutline(chipX, chipY, COLLAPSED_WIDTH, COLLAPSED_HEIGHT, borderColor);

            String icon = "?";
            int iconW = font.width(icon);
            graphics.drawString(font, icon, chipX + (COLLAPSED_WIDTH - iconW) / 2, chipY + 6, hovered ? 0xFFFFFFFF : 0xFF94A3B8, false);

            if (hovered) {
                graphics.renderTooltip(font, Component.translatable("gui.gtcalcboard.hotkey_hud.expand"), mouseX, mouseY);
            }
            return;
        }

        // Expanded Panel at Bottom-Left
        int panelX = 8;
        int panelY = screenH - EXPANDED_HEIGHT - 8;

        // Background & Modern Glowing Border
        graphics.fill(panelX, panelY, panelX + EXPANDED_WIDTH, panelY + EXPANDED_HEIGHT, 0xEE0B1120);
        graphics.renderOutline(panelX, panelY, EXPANDED_WIDTH, EXPANDED_HEIGHT, 0xFF1E293B);

        // Header Title & Minimize button [-]
        graphics.fill(panelX, panelY, panelX + EXPANDED_WIDTH, panelY + 16, 0xFF1E293B);
        String title = Component.translatable("gui.gtcalcboard.hotkey_hud.title").getString();
        graphics.drawString(font, title, panelX + 6, panelY + 4, 0xFF38BDF8, false);

        int minBtnX = panelX + EXPANDED_WIDTH - 14;
        int minBtnY = panelY + 2;
        boolean minHovered = mouseX >= minBtnX && mouseX <= minBtnX + 12 && mouseY >= minBtnY && mouseY <= minBtnY + 12;
        graphics.drawString(font, "x", minBtnX + 2, minBtnY + 2, minHovered ? 0xFFFF5555 : 0xFF64748B, false);

        // Hotkey lines
        int curY = panelY + 20;
        renderKeyLine(graphics, font, panelX + 6, curY, "B", "gui.gtcalcboard.hotkey_hud.balance");
        curY += 12;
        renderKeyLine(graphics, font, panelX + 6, curY, "Shift+B / M", "gui.gtcalcboard.hotkey_hud.bom");
        curY += 12;
        renderKeyLine(graphics, font, panelX + 6, curY, "T", "gui.gtcalcboard.hotkey_hud.time_unit");
        curY += 12;
        renderKeyLine(graphics, font, panelX + 6, curY, "J", "gui.gtcalcboard.hotkey_hud.junction");
        curY += 12;
        renderKeyLine(graphics, font, panelX + 6, curY, "Space / Dbl-Click", "gui.gtcalcboard.hotkey_hud.add");
        curY += 12;
        renderKeyLine(graphics, font, panelX + 6, curY, "Shift + Drag", "gui.gtcalcboard.hotkey_hud.shift_wire");
        curY += 12;
        renderKeyLine(graphics, font, panelX + 6, curY, "Ctrl + G", "gui.gtcalcboard.hotkey_hud.frame");
        curY += 12;
        renderKeyLine(graphics, font, panelX + 6, curY, "Ctrl + Shift + G", "gui.gtcalcboard.hotkey_hud.group");
        curY += 12;
        renderKeyLine(graphics, font, panelX + 6, curY, "Ctrl + Z / Y", "gui.gtcalcboard.hotkey_hud.undo_redo");
        curY += 12;
        renderKeyLine(graphics, font, panelX + 6, curY, "Ctrl + C / V / X", "gui.gtcalcboard.hotkey_hud.clipboard");
        curY += 12;
        renderKeyLine(graphics, font, panelX + 6, curY, "F", "gui.gtcalcboard.hotkey_hud.flip");
        curY += 12;
        renderKeyLine(graphics, font, panelX + 6, curY, "Right-Click Wire", "gui.gtcalcboard.hotkey_hud.sever");
        curY += 12;
        renderKeyLine(graphics, font, panelX + 6, curY, "Delete", "gui.gtcalcboard.hotkey_hud.delete");
    }

    private void renderKeyLine(GuiGraphics graphics, Font font, int x, int y, String key, String langKey) {
        graphics.drawString(font, key, x, y, 0xFFE2E8F0, false);
        String desc = Component.translatable(langKey).getString();
        int descW = font.width(desc);
        int targetX = x + EXPANDED_WIDTH - 12 - descW;
        graphics.drawString(font, desc, targetX, y, 0xFF94A3B8, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int screenW = screen.width;
        int screenH = screen.height;

        if (!expanded) {
            int chipX = 8;
            int chipY = screenH - COLLAPSED_HEIGHT - 8;
            if (mouseX >= chipX && mouseX <= chipX + COLLAPSED_WIDTH && mouseY >= chipY && mouseY <= chipY + COLLAPSED_HEIGHT) {
                setExpanded(true);
                return true;
            }
            return false;
        }

        int panelX = 8;
        int panelY = screenH - EXPANDED_HEIGHT - 8;

        if (mouseX >= panelX && mouseX <= panelX + EXPANDED_WIDTH && mouseY >= panelY && mouseY <= panelY + EXPANDED_HEIGHT) {
            int minBtnX = panelX + EXPANDED_WIDTH - 14;
            int minBtnY = panelY + 2;
            if (mouseX >= minBtnX && mouseX <= minBtnX + 12 && mouseY >= minBtnY && mouseY <= minBtnY + 12) {
                setExpanded(false);
                return true;
            }
            return true; // Consume clicks inside panel
        }

        return false;
    }
}
