package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.FluidUnitMode;
import com.gtceu.calcboard.api.type.PowerDisplayMode;
import com.gtceu.calcboard.api.type.RateTimeUnit;
import com.gtceu.calcboard.api.type.WireColorPreset;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.render.ConnectionRenderer;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Central Preferences & Customization Modal Dialog for GregTech Calculator Board.
 * Provides granular toggles for toolbar buttons, HUDs, simulation defaults, and wire theme palettes.
 */
public class BoardSettingsDialog {
    private final BoardScreen parent;
    private boolean visible = false;
    private int activeTab = 0;

    private static final int DIALOG_WIDTH = 480;
    private static final int DIALOG_HEIGHT = 270;
    private static final int SIDEBAR_WIDTH = 135;

    public enum SettingsTab {
        TOOLBAR("gui.gtcalcboard.settings.tab_toolbar"),
        HUD("gui.gtcalcboard.settings.tab_hud"),
        UNITS("gui.gtcalcboard.settings.tab_units"),
        WIRES("gui.gtcalcboard.settings.tab_wires");

        private final String nameKey;

        SettingsTab(String nameKey) {
            this.nameKey = nameKey;
        }

        public String getDisplayName() {
            return Component.translatable(nameKey).getString();
        }
    }

    public BoardSettingsDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void open() {
        this.visible = true;
    }

    public void close() {
        this.visible = false;
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible) return;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);

        Font font = Minecraft.getInstance().font;

        int dialogW = Math.min(DIALOG_WIDTH, screenWidth - 24);
        int dialogH = Math.min(DIALOG_HEIGHT, screenHeight - 24);
        int dialogX = (screenWidth - dialogW) / 2;
        int dialogY = (screenHeight - dialogH) / 2;

        // 1. Modal Scrim Dim Background
        graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);

        // 2. Dialog Container Frame
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, 0xF0151821);
        graphics.renderOutline(dialogX, dialogY, dialogW, dialogH, 0xFF35445E);

        // 3. Header Bar
        int headerH = 26;
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + headerH, 0xEE1C2433);
        graphics.fill(dialogX, dialogY + headerH - 1, dialogX + dialogW, dialogY + headerH, 0xFF2F3C54);

        String title = "⚙ " + Component.translatable("gui.gtcalcboard.settings.title").getString();
        graphics.drawString(font, title, dialogX + 10, dialogY + 8, 0xFFFFFFFF, false);

        // Close Button [X]
        int closeBtnX = dialogX + dialogW - 20;
        int closeBtnY = dialogY + 5;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + 16 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16;
        graphics.fill(closeBtnX, closeBtnY, closeBtnX + 16, closeBtnY + 16, closeHover ? 0xFFFF4444 : 0x33FF4444);
        graphics.renderOutline(closeBtnX, closeBtnY, 16, 16, closeHover ? 0xFFFF8888 : 0x66FF4444);
        graphics.drawCenteredString(font, "✕", closeBtnX + 8, closeBtnY + 4, 0xFFFFFFFF);

        // 4. Sidebar Tabs
        int contentY = dialogY + headerH;
        int contentH = dialogH - headerH;
        int sidebarW = SIDEBAR_WIDTH;

        graphics.fill(dialogX, contentY, dialogX + sidebarW, dialogY + dialogH, 0xDD12141C);
        graphics.fill(dialogX + sidebarW - 1, contentY, dialogX + sidebarW, dialogY + dialogH, 0xFF2A364D);

        SettingsTab[] tabs = SettingsTab.values();
        int tabBtnH = 24;
        for (int i = 0; i < tabs.length; i++) {
            int tabY = contentY + 6 + i * (tabBtnH + 4);
            boolean isSelected = (i == activeTab);
            boolean tabHover = mouseX >= dialogX + 4 && mouseX <= dialogX + sidebarW - 4 && mouseY >= tabY && mouseY <= tabY + tabBtnH;

            int tabBg = isSelected ? 0xFF253347 : (tabHover ? 0xFF1C2536 : 0x00000000);
            int tabBorder = isSelected ? 0xFF5B9BD5 : (tabHover ? 0xFF3D4B66 : 0x00000000);
            if (tabBg != 0) {
                graphics.fill(dialogX + 4, tabY, dialogX + sidebarW - 4, tabY + tabBtnH, tabBg);
            }
            if (tabBorder != 0) {
                graphics.renderOutline(dialogX + 4, tabY, sidebarW - 8, tabBtnH, tabBorder);
            }

            int textColor = isSelected ? 0xFFFFFFFF : (tabHover ? 0xFFDDDDDD : 0xFF99AABF);
            String tabText = tabs[i].getDisplayName();
            graphics.drawString(font, tabText, dialogX + 10, tabY + 8, textColor, false);
        }

        // 5. Main Content Area
        int mainX = dialogX + sidebarW + 8;
        int mainY = contentY + 8;
        int mainW = dialogW - sidebarW - 16;
        int mainH = contentH - 16;

        BoardManager bm = BoardManager.getInstance();

        switch (tabs[activeTab]) {
            case TOOLBAR -> renderToolbarTab(graphics, font, mainX, mainY, mainW, mainH, mouseX, mouseY, bm);
            case HUD -> renderHudTab(graphics, font, mainX, mainY, mainW, mainH, mouseX, mouseY, bm);
            case UNITS -> renderUnitsTab(graphics, font, mainX, mainY, mainW, mainH, mouseX, mouseY, bm);
            case WIRES -> renderWiresTab(graphics, font, mainX, mainY, mainW, mainH, mouseX, mouseY, bm);
        }

        graphics.pose().popPose();
    }

    private void renderToolbarTab(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY, BoardManager bm) {
        graphics.drawString(font, "§6" + Component.translatable("gui.gtcalcboard.settings.toolbar_desc").getString(), x, y, 0xFFFFFFFF, false);

        int rowY = y + 20;
        int rowH = 22;

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

        if (com.gtceu.calcboard.api.util.ModCompatHelper.isBoMSupported()) {
            drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                    Component.translatable("gui.gtcalcboard.settings.show_bom_btn").getString(),
                    bm.isShowMultiblockBomButton());
        }
    }

    private void renderHudTab(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY, BoardManager bm) {
        graphics.drawString(font, "§b" + Component.translatable("gui.gtcalcboard.settings.hud_desc").getString(), x, y, 0xFFFFFFFF, false);

        int rowY = y + 20;
        int rowH = 22;

        drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.show_hotkey_hud").getString(),
                bm.isShowHotkeyHud());
        rowY += rowH + 2;

        drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.show_pulse_anim").getString(),
                bm.isShowWirePulseAnimation());
        rowY += rowH + 2;

        drawCheckbox(graphics, font, x, rowY, w, rowH, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.collapse_summary").getString(),
                bm.isSummaryOverlayCollapsed());
    }

    private void renderUnitsTab(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY, BoardManager bm) {
        graphics.drawString(font, "§e" + Component.translatable("gui.gtcalcboard.settings.units_desc").getString(), x, y, 0xFFFFFFFF, false);

        int rowY = y + 20;

        // 1. Time Unit
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.settings.time_unit_label").getString(), x, rowY + 5, 0xFFCCCCCC, false);
        RateTimeUnit curUnit = bm.getTimeUnit();
        String timeBtnTxt = curUnit.getSuffix() + " (" + Component.translatable(curUnit.getTranslationKey()).getString() + ") ▼";
        int btnW = 160;
        int btnX = x + w - btnW - 4;
        drawButton(graphics, font, timeBtnTxt, btnX, rowY, btnW, 20, mouseX, mouseY, 0xFFFFF176, 0xFF222834, 0xFF35445E);
        rowY += 26;

        // 2. Fluid Unit Mode
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.settings.fluid_unit_label").getString(), x, rowY + 5, 0xFFCCCCCC, false);
        FluidUnitMode curFluid = bm.getFluidUnitMode();
        String fluidBtnTxt = curFluid.getLabel() + " (" + Component.translatable(curFluid.getTranslationKey()).getString() + ") ▼";
        drawButton(graphics, font, fluidBtnTxt, btnX, rowY, btnW, 20, mouseX, mouseY, 0xFF66E5FF, 0xFF222834, 0xFF35445E);
        rowY += 26;

        // 3. Power Display Mode
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.settings.power_unit_label").getString(), x, rowY + 5, 0xFFCCCCCC, false);
        PowerDisplayMode curPower = bm.getPowerDisplayMode();
        String powerBtnTxt = curPower.getLabel() + " ▼";
        drawButton(graphics, font, powerBtnTxt, btnX, rowY, btnW, 20, mouseX, mouseY, 0xFFFFCC66, 0xFF222834, 0xFF35445E);
        rowY += 26;

        // 4. Singleplayer Pause Toggle
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer()) {
            drawCheckbox(graphics, font, x, rowY, w, 22, mouseX, mouseY,
                    Component.translatable("gui.gtcalcboard.settings.singleplayer_pause").getString(),
                    bm.isPauseGameInSingleplayer());
        }
    }

    private void renderWiresTab(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY, BoardManager bm) {
        graphics.drawString(font, "§a" + Component.translatable("gui.gtcalcboard.settings.wires_desc").getString(), x, y, 0xFFFFFFFF, false);

        int rowY = y + 18;

        // 1. Default Wire Color Palette
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.settings.default_wire_color").getString(), x, rowY, 0xFFCCCCCC, false);
        rowY += 12;

        WireColorPreset curDef = bm.getWireColorPreset();
        WireColorPreset[] presets = WireColorPreset.values();
        int palX = x;
        int palSize = 18;
        for (WireColorPreset p : presets) {
            boolean isSel = (p == curDef);
            boolean hover = mouseX >= palX && mouseX <= palX + palSize && mouseY >= rowY && mouseY <= rowY + palSize;
            graphics.fill(palX, rowY, palX + palSize, rowY + palSize, p.getArgb());
            graphics.renderOutline(palX, rowY, palSize, palSize, isSel ? 0xFFFFFFFF : (hover ? 0xFFAAAAAA : 0xFF3D4B66));
            if (isSel) {
                graphics.drawCenteredString(font, "✔", palX + palSize / 2, rowY + 5, 0xFF000000);
            }
            if (hover) {
                graphics.renderTooltip(font, p.getDisplayName(), mouseX, mouseY);
            }
            palX += palSize + 6;
        }

        rowY += palSize + 14;

        // 2. Matched / Dragging Wire Color Palette
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.settings.matched_wire_color").getString(), x, rowY, 0xFFCCCCCC, false);
        rowY += 12;

        WireColorPreset curMatched = bm.getMatchedWireColorPreset();
        palX = x;
        for (WireColorPreset p : presets) {
            boolean isSel = (p == curMatched);
            boolean hover = mouseX >= palX && mouseX <= palX + palSize && mouseY >= rowY && mouseY <= rowY + palSize;
            graphics.fill(palX, rowY, palX + palSize, rowY + palSize, p.getArgb());
            graphics.renderOutline(palX, rowY, palSize, palSize, isSel ? 0xFFFFFFFF : (hover ? 0xFFAAAAAA : 0xFF3D4B66));
            if (isSel) {
                graphics.drawCenteredString(font, "✔", palX + palSize / 2, rowY + 5, 0xFF000000);
            }
            if (hover) {
                graphics.renderTooltip(font, p.getDisplayName(), mouseX, mouseY);
            }
            palX += palSize + 6;
        }

        rowY += palSize + 16;

        // 3. Live Wire Preview Box
        int previewH = 50;
        int previewW = w - 4;
        graphics.fill(x, rowY, x + previewW, rowY + previewH, 0xEE10131A);
        graphics.renderOutline(x, rowY, previewW, previewH, 0xFF2C394F);

        // Preview Label
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.settings.preview_label").getString(), x + 6, rowY + 4, 0xFF8899AA, false);

        // Render Bezier curves for Default and Matched wire preview
        float wx1 = x + 25;
        float wy1 = rowY + 34;
        float wx2 = x + previewW / 2 - 20;
        float wy2 = rowY + 22;
        ConnectionRenderer.renderBezier(graphics, wx1, wy1, wx2, wy2, curDef.getArgb(), 2.5f);
        graphics.drawCenteredString(font, "Default", (int) (wx1 + wx2) / 2, (int) Math.min(wy1, wy2) - 9, curDef.getArgb());

        float mx1 = x + previewW / 2 + 20;
        float my1 = rowY + 34;
        float mx2 = x + previewW - 25;
        float my2 = rowY + 22;
        ConnectionRenderer.renderBezier(graphics, mx1, my1, mx2, my2, curMatched.getArgb(), 3.0f);
        graphics.drawCenteredString(font, "Matched", (int) (mx1 + mx2) / 2, (int) Math.min(my1, my2) - 9, curMatched.getArgb());
    }

    private void drawCheckbox(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY, String label, boolean checked) {
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

    private void drawButton(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textCol, int bg, int border) {
        boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        int actualBg = hover ? (bg + 0x00151515) : bg;
        int actualBorder = hover ? 0xFF657595 : border;

        graphics.fill(bx, by, bx + bw, by + bh, actualBg);
        graphics.renderOutline(bx, by, bw, bh, actualBorder);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + (bh - 8) / 2, hover ? 0xFFFFFFFF : textCol);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = parent.width;
        int screenHeight = parent.height;

        int dialogW = Math.min(DIALOG_WIDTH, screenWidth - 24);
        int dialogH = Math.min(DIALOG_HEIGHT, screenHeight - 24);
        int dialogX = (screenWidth - dialogW) / 2;
        int dialogY = (screenHeight - dialogH) / 2;

        // Block click if outside dialog (or click scrim to close)
        if (mouseX < dialogX || mouseX > dialogX + dialogW || mouseY < dialogY || mouseY > dialogY + dialogH) {
            close();
            playClickSound();
            return true;
        }

        // Close Button
        int closeBtnX = dialogX + dialogW - 20;
        int closeBtnY = dialogY + 5;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 16 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16) {
            close();
            playClickSound();
            return true;
        }

        // Sidebar Tabs Click
        int headerH = 26;
        int contentY = dialogY + headerH;
        int sidebarW = SIDEBAR_WIDTH;
        SettingsTab[] tabs = SettingsTab.values();
        int tabBtnH = 24;
        for (int i = 0; i < tabs.length; i++) {
            int tabY = contentY + 6 + i * (tabBtnH + 4);
            if (mouseX >= dialogX + 4 && mouseX <= dialogX + sidebarW - 4 && mouseY >= tabY && mouseY <= tabY + tabBtnH) {
                if (activeTab != i) {
                    activeTab = i;
                    playClickSound();
                }
                return true;
            }
        }

        // Main Area Click Handling
        int mainX = dialogX + sidebarW + 8;
        int mainY = contentY + 8;
        int mainW = dialogW - sidebarW - 16;
        BoardManager bm = BoardManager.getInstance();

        switch (tabs[activeTab]) {
            case TOOLBAR -> handleToolbarClick(mouseX, mouseY, mainX, mainY, mainW, bm);
            case HUD -> handleHudClick(mouseX, mouseY, mainX, mainY, mainW, bm);
            case UNITS -> handleUnitsClick(mouseX, mouseY, mainX, mainY, mainW, bm);
            case WIRES -> handleWiresClick(mouseX, mouseY, mainX, mainY, mainW, bm);
        }

        return true;
    }

    private void handleToolbarClick(double mouseX, double mouseY, int x, int y, int w, BoardManager bm) {
        int rowY = y + 20;
        int rowH = 22;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setShowGuideButton(!bm.isShowGuideButton());
            onSettingsChanged();
            return;
        }
        rowY += rowH + 2;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setShowTutorialButton(!bm.isShowTutorialButton());
            onSettingsChanged();
            return;
        }
        rowY += rowH + 2;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setShowTimeUnitButton(!bm.isShowTimeUnitButton());
            onSettingsChanged();
            return;
        }
        rowY += rowH + 2;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setShowFluidUnitButton(!bm.isShowFluidUnitButton());
            onSettingsChanged();
            return;
        }
        rowY += rowH + 2;

        if (com.gtceu.calcboard.api.util.ModCompatHelper.isBoMSupported() && isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setShowMultiblockBomButton(!bm.isShowMultiblockBomButton());
            onSettingsChanged();
        }
    }

    private void handleHudClick(double mouseX, double mouseY, int x, int y, int w, BoardManager bm) {
        int rowY = y + 20;
        int rowH = 22;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setShowHotkeyHud(!bm.isShowHotkeyHud());
            onSettingsChanged();
            return;
        }
        rowY += rowH + 2;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setShowWirePulseAnimation(!bm.isShowWirePulseAnimation());
            onSettingsChanged();
            return;
        }
        rowY += rowH + 2;

        if (isInsideRow(mouseX, mouseY, x, rowY, w, rowH)) {
            bm.setSummaryOverlayCollapsed(!bm.isSummaryOverlayCollapsed());
            onSettingsChanged();
        }
    }

    private void handleUnitsClick(double mouseX, double mouseY, int x, int y, int w, BoardManager bm) {
        int rowY = y + 20;
        int btnW = 160;
        int btnX = x + w - btnW - 4;

        // 1. Time Unit
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= rowY && mouseY <= rowY + 20) {
            RateTimeUnit next = bm.cycleTimeUnit();
            FormatUtil.setActiveTimeUnit(next);
            onSettingsChanged();
            return;
        }
        rowY += 26;

        // 2. Fluid Unit Mode
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= rowY && mouseY <= rowY + 20) {
            FluidUnitMode next = bm.cycleFluidUnitMode();
            FormatUtil.setActiveFluidUnitMode(next);
            onSettingsChanged();
            return;
        }
        rowY += 26;

        // 3. Power Display Mode
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= rowY && mouseY <= rowY + 20) {
            bm.cyclePowerDisplayMode();
            onSettingsChanged();
            return;
        }
        rowY += 26;

        // 4. Singleplayer Pause
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && isInsideRow(mouseX, mouseY, x, rowY, w, 22)) {
            bm.setPauseGameInSingleplayer(!bm.isPauseGameInSingleplayer());
            onSettingsChanged();
        }
    }

    private void handleWiresClick(double mouseX, double mouseY, int x, int y, int w, BoardManager bm) {
        int rowY = y + 18 + 12;
        int palSize = 18;
        WireColorPreset[] presets = WireColorPreset.values();

        // Default Wire Palette
        int palX = x;
        for (WireColorPreset p : presets) {
            if (mouseX >= palX && mouseX <= palX + palSize && mouseY >= rowY && mouseY <= rowY + palSize) {
                bm.setWireColorPreset(p);
                onSettingsChanged();
                return;
            }
            palX += palSize + 6;
        }

        rowY += palSize + 14 + 12;

        // Matched Wire Palette
        palX = x;
        for (WireColorPreset p : presets) {
            if (mouseX >= palX && mouseX <= palX + palSize && mouseY >= rowY && mouseY <= rowY + palSize) {
                bm.setMatchedWireColorPreset(p);
                onSettingsChanged();
                return;
            }
            palX += palSize + 6;
        }
    }

    private boolean isInsideRow(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private void onSettingsChanged() {
        playClickSound();
        BoardManager.getInstance().saveForCurrentContext();
        parent.rebuildWidgets();
        parent.markSummaryDirty();
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }
}
