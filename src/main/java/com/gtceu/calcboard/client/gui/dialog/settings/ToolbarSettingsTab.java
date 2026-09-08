package com.gtceu.calcboard.client.gui.dialog.settings;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.BoardSettingsDialog;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Settings tab for configuring toolbar display mode and button visibility.
 */
public class ToolbarSettingsTab extends AbstractSettingsTab {

    public ToolbarSettingsTab(BoardSettingsDialog dialog, BoardScreen parent) {
        super(dialog, parent);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        BoardManager bm = BoardManager.getInstance();
        graphics.drawString(font, "§6" + Component.translatable("gui.gtcalcboard.settings.toolbar_desc").getString(), x, y, 0xFFFFFFFF, false);

        int rowY = y + 20;
        int rowH = 22;

        graphics.drawString(font, Component.translatable("gui.gtcalcboard.settings.toolbar_mode_label").getString(), x, rowY + 5, 0xFFCCCCCC, false);
        int btnW = 120;
        int btnX = x + w - btnW - 4;
        String modeTxt = bm.getToolbarDisplayMode().getDisplayName() + " ▼";
        drawButton(graphics, font, modeTxt, btnX, rowY, btnW, 20, mouseX, mouseY, 0xFF66E5FF, 0xFF222834, 0xFF35445E);
        rowY += 26;

        drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.show_guide_btn").getString(),
                bm.isShowGuideButton());
        rowY += rowH + 2;

        drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.show_tutorial_btn").getString(),
                bm.isShowTutorialButton());
        rowY += rowH + 2;

        drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.show_time_unit_btn").getString(),
                bm.isShowTimeUnitButton());
        rowY += rowH + 2;

        drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.show_fluid_unit_btn").getString(),
                bm.isShowFluidUnitButton());
        rowY += rowH + 2;

        if (ModCompatHelper.isBoMSupported()) {
            drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                    Component.translatable("gui.gtcalcboard.settings.show_bom_btn").getString(),
                    bm.isShowMultiblockBomButton());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, int h, int button) {
        BoardManager bm = BoardManager.getInstance();
        int rowY = y + 20;
        int rowH = 22;

        int btnW = 120;
        int btnX = x + w - btnW - 4;
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= rowY && mouseY <= rowY + 20) {
            bm.cycleToolbarDisplayMode();
            onSettingsChanged();
            return true;
        }
        rowY += 26;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setShowGuideButton(!bm.isShowGuideButton());
            onSettingsChanged();
            return true;
        }
        rowY += rowH + 2;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setShowTutorialButton(!bm.isShowTutorialButton());
            onSettingsChanged();
            return true;
        }
        rowY += rowH + 2;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setShowTimeUnitButton(!bm.isShowTimeUnitButton());
            onSettingsChanged();
            return true;
        }
        rowY += rowH + 2;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setShowFluidUnitButton(!bm.isShowFluidUnitButton());
            onSettingsChanged();
            return true;
        }
        rowY += rowH + 2;

        if (ModCompatHelper.isBoMSupported() && isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setShowMultiblockBomButton(!bm.isShowMultiblockBomButton());
            onSettingsChanged();
            return true;
        }
        return false;
    }
}
