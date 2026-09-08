package com.gtceu.calcboard.client.gui.dialog.settings;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.BoardSettingsDialog;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Settings tab for configuring GUI scale, HUD overlays, animations, and card layouts.
 */
public class HudSettingsTab extends AbstractSettingsTab {

    public HudSettingsTab(BoardSettingsDialog dialog, BoardScreen parent) {
        super(dialog, parent);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        BoardManager bm = BoardManager.getInstance();
        graphics.drawString(font, "§b" + Component.translatable("gui.gtcalcboard.settings.hud_desc").getString(), x, y, 0xFFFFFFFF, false);

        int rowY = y + 20;
        int rowH = 22;

        graphics.drawString(font, Component.translatable("gui.gtcalcboard.settings.gui_scale_label").getString(), x, rowY + 5, 0xFFCCCCCC, false);
        int btnW = 120;
        int btnX = x + w - btnW - 4;
        String scaleTxt = bm.getBoardGuiScale().getDisplayName() + " ▼";
        drawButton(graphics, font, scaleTxt, btnX, rowY, btnW, 20, mouseX, mouseY, 0xFF58D3FF, 0xFF222834, 0xFF35445E);
        rowY += 26;

        drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.show_hotkey_hud").getString(),
                bm.isShowHotkeyHud());
        rowY += rowH + 2;

        drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.addon_catalog_list_view").getString(),
                bm.isAddonCatalogListView());
        rowY += rowH + 2;

        String pulseAnimLabel = Component.translatable("gui.gtcalcboard.settings.show_pulse_anim").getString()
                + ": §b" + bm.getWireAnimationMode().getDisplayName();
        drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                pulseAnimLabel,
                bm.isShowWirePulseAnimation());
        rowY += rowH + 2;

        drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.collapse_summary").getString(),
                bm.isSummaryOverlayCollapsed());
        rowY += rowH + 2;

        drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.grid_snap").getString(),
                bm.isGridSnapEnabled());
        rowY += rowH + 2;

        drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.show_debug_info").getString(),
                bm.isShowDebugInfo());
        rowY += rowH + 2;

        drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.slim_card_mode").getString(),
                bm.isSlimCardMode());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, int h, int button) {
        BoardManager bm = BoardManager.getInstance();
        int rowY = y + 20;
        int rowH = 22;

        int btnW = 120;
        int btnX = x + w - btnW - 4;
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= rowY && mouseY <= rowY + 20) {
            bm.cycleBoardGuiScale();
            onSettingsChanged();
            if (parent != null) {
                parent.onGuiScaleChanged();
            }
            return true;
        }
        rowY += 26;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setShowHotkeyHud(!bm.isShowHotkeyHud());
            onSettingsChanged();
            return true;
        }
        rowY += rowH + 2;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setAddonCatalogListView(!bm.isAddonCatalogListView());
            onSettingsChanged();
            return true;
        }
        rowY += rowH + 2;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.cycleWireAnimationMode();
            onSettingsChanged();
            return true;
        }
        rowY += rowH + 2;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setSummaryOverlayCollapsed(!bm.isSummaryOverlayCollapsed());
            onSettingsChanged();
            return true;
        }
        rowY += rowH + 2;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setGridSnapEnabled(!bm.isGridSnapEnabled());
            onSettingsChanged();
            return true;
        }
        rowY += rowH + 2;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setShowDebugInfo(!bm.isShowDebugInfo());
            onSettingsChanged();
            return true;
        }
        rowY += rowH + 2;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setSlimCardMode(!bm.isSlimCardMode());
            onSettingsChanged();
            if (parent != null) {
                parent.rebuildWidgets();
            }
            return true;
        }
        return false;
    }
}
