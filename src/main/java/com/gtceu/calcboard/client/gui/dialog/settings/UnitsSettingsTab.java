package com.gtceu.calcboard.client.gui.dialog.settings;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.FluidUnitMode;
import com.gtceu.calcboard.api.type.PowerDisplayMode;
import com.gtceu.calcboard.api.type.RateTimeUnit;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.BoardSettingsDialog;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.client.storage.ClientPreferenceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Settings tab for configuring measurement units, rate display formats, and game pause behavior.
 */
public class UnitsSettingsTab extends AbstractSettingsTab {

    public UnitsSettingsTab(BoardSettingsDialog dialog, BoardScreen parent) {
        super(dialog, parent);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        BoardManager bm = BoardManager.getInstance();
        graphics.drawString(font, "§e" + Component.translatable("gui.gtcalcboard.settings.units_desc").getString(), x, y, 0xFFFFFFFF, false);

        int rowY = y + 20;
        int btnW = 150;
        int btnX = x + w - btnW - 4;

        graphics.drawString(font, Component.translatable("gui.gtcalcboard.settings.time_unit_label").getString(), x, rowY + 5, 0xFFCCCCCC, false);
        RateTimeUnit curUnit = bm.getTimeUnit();
        String timeBtnTxt = curUnit.getSuffix() + " (" + Component.translatable(curUnit.getTranslationKey()).getString() + ") ▼";
        drawButton(graphics, font, timeBtnTxt, btnX, rowY, btnW, 20, mouseX, mouseY, 0xFFFFF176, 0xFF222834, 0xFF35445E);
        rowY += 26;

        graphics.drawString(font, Component.translatable("gui.gtcalcboard.settings.fluid_unit_label").getString(), x, rowY + 5, 0xFFCCCCCC, false);
        FluidUnitMode curFluid = bm.getFluidUnitMode();
        String fluidBtnTxt = curFluid.getLabel() + " (" + Component.translatable(curFluid.getTranslationKey()).getString() + ") ▼";
        drawButton(graphics, font, fluidBtnTxt, btnX, rowY, btnW, 20, mouseX, mouseY, 0xFF66E5FF, 0xFF222834, 0xFF35445E);
        rowY += 26;

        graphics.drawString(font, Component.translatable("gui.gtcalcboard.settings.power_unit_label").getString(), x, rowY + 5, 0xFFCCCCCC, false);
        PowerDisplayMode curPower = bm.getPowerDisplayMode();
        String powerBtnTxt = curPower.getLabel() + " ▼";
        drawButton(graphics, font, powerBtnTxt, btnX, rowY, btnW, 20, mouseX, mouseY, 0xFFFFCC66, 0xFF222834, 0xFF35445E);
        rowY += 26;

        drawCheckbox(graphics, font, x, rowY, w, 22, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.preserve_units").getString(),
                ClientPreferenceManager.getInstance().isPreserveUnitPreferences());
        rowY += 24;

        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer()) {
            drawCheckbox(graphics, font, x, rowY, w, 22, mouseX, mouseY,
                    Component.translatable("gui.gtcalcboard.settings.singleplayer_pause").getString(),
                    bm.isPauseGameInSingleplayer());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, int h, int button) {
        BoardManager bm = BoardManager.getInstance();
        int rowY = y + 20;
        int btnW = 150;
        int btnX = x + w - btnW - 4;

        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= rowY && mouseY <= rowY + 20) {
            RateTimeUnit next = bm.cycleTimeUnit();
            FormatUtil.setActiveTimeUnit(next);
            onSettingsChanged();
            return true;
        }
        rowY += 26;

        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= rowY && mouseY <= rowY + 20) {
            FluidUnitMode next = bm.cycleFluidUnitMode();
            FormatUtil.setActiveFluidUnitMode(next);
            onSettingsChanged();
            return true;
        }
        rowY += 26;

        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= rowY && mouseY <= rowY + 20) {
            bm.cyclePowerDisplayMode();
            onSettingsChanged();
            return true;
        }
        rowY += 26;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, 22)) {
            boolean cur = ClientPreferenceManager.getInstance().isPreserveUnitPreferences();
            ClientPreferenceManager.getInstance().setPreserveUnitPreferences(!cur);
            if (!cur) {
                ClientPreferenceManager.getInstance().onTimeUnitChanged(bm.getTimeUnit());
                ClientPreferenceManager.getInstance().onFluidUnitModeChanged(bm.getFluidUnitMode());
            }
            onSettingsChanged();
            return true;
        }
        rowY += 24;

        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && isInsideRow(mouseX, mouseY, x, rowY, w, 22)) {
            bm.setPauseGameInSingleplayer(!bm.isPauseGameInSingleplayer());
            onSettingsChanged();
            return true;
        }
        return false;
    }
}
