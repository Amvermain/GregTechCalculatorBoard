package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.modal.IBoardModal;
import com.gtceu.calcboard.client.gui.dialog.modal.ModalRenderContext;
import com.gtceu.calcboard.client.gui.dialog.settings.*;
import com.gtceu.calcboard.client.update.ClientUpdateNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * Central Preferences & Customization Modal Dialog for GregTech Calculator Board.
 * Coordinates multi-tab settings for toolbars, HUDs, units, auto-ratio solver, wire colors, and updates.
 */
public class BoardSettingsDialog implements IBoardModal {

    private final BoardScreen parent;
    private boolean visible = false;
    private int activeTab = 0;

    private static final int DIALOG_WIDTH = 510;
    private static final int DIALOG_HEIGHT = 275;
    private static final int SIDEBAR_WIDTH = 125;

    private final ISettingsTab[] tabHandlers;

    public enum SettingsTab {
        TOOLBAR("gui.gtcalcboard.settings.tab_toolbar"),
        HUD("gui.gtcalcboard.settings.tab_hud"),
        UNITS("gui.gtcalcboard.settings.tab_units"),
        RATIO("gui.gtcalcboard.settings.tab_ratio"),
        WIRES("gui.gtcalcboard.settings.tab_wires"),
        PRESETS("gui.gtcalcboard.settings.tab_presets"),
        UPDATES("gui.gtcalcboard.settings.tab_updates");

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
        this.tabHandlers = new ISettingsTab[] {
                new ToolbarSettingsTab(this, parent),
                new HudSettingsTab(this, parent),
                new UnitsSettingsTab(this, parent),
                new RatioSettingsTab(this, parent),
                new WiresSettingsTab(this, parent),
                new PresetsSettingsTab(this, parent),
                new UpdatesSettingsTab(this, parent)
        };
    }

    public BoardScreen getParent() {
        return parent;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void open() {
        this.visible = true;
        for (ISettingsTab tab : tabHandlers) {
            tab.onOpen();
        }
        ClientUpdateNotifier.getInstance().refresh();
    }

    public void close() {
        this.visible = false;
        for (ISettingsTab tab : tabHandlers) {
            tab.onClose();
        }
    }

    @Override
    public void renderModal(ModalRenderContext context) {
        render(context.graphics(), context.screenWidth(), context.screenHeight(), context.mouseX(), context.mouseY());
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

        renderDialogBackground(graphics, screenWidth, screenHeight, dialogX, dialogY, dialogW, dialogH);
        renderHeader(graphics, font, dialogX, dialogY, dialogW, mouseX, mouseY);
        renderSidebar(graphics, font, dialogX, dialogY, dialogH, mouseX, mouseY);
        renderActiveTabContent(graphics, font, dialogX, dialogY, dialogW, dialogH, mouseX, mouseY);

        graphics.pose().popPose();
    }

    private void renderDialogBackground(GuiGraphics graphics, int screenWidth, int screenHeight,
                                        int dialogX, int dialogY, int dialogW, int dialogH) {
        graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, 0xF0151821);
        graphics.renderOutline(dialogX, dialogY, dialogW, dialogH, 0xFF35445E);
    }

    private void renderHeader(GuiGraphics graphics, Font font, int dialogX, int dialogY, int dialogW, int mouseX, int mouseY) {
        int headerH = 26;
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + headerH, 0xEE1C2433);
        graphics.fill(dialogX, dialogY + headerH - 1, dialogX + dialogW, dialogY + headerH, 0xFF2F3C54);

        String title = "⚙ " + Component.translatable("gui.gtcalcboard.settings.title").getString();
        graphics.drawString(font, title, dialogX + 10, dialogY + 8, 0xFFFFFFFF, false);

        int closeBtnX = dialogX + dialogW - 20;
        int closeBtnY = dialogY + 5;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + 16 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16;
        graphics.fill(closeBtnX, closeBtnY, closeBtnX + 16, closeBtnY + 16, closeHover ? 0xFFFF4444 : 0x33FF4444);
        graphics.renderOutline(closeBtnX, closeBtnY, 16, 16, closeHover ? 0xFFFF8888 : 0x66FF4444);
        graphics.drawCenteredString(font, "✕", closeBtnX + 8, closeBtnY + 4, 0xFFFFFFFF);
    }

    private void renderSidebar(GuiGraphics graphics, Font font, int dialogX, int dialogY, int dialogH, int mouseX, int mouseY) {
        int contentY = dialogY + 26;
        int sidebarW = SIDEBAR_WIDTH;

        graphics.fill(dialogX, contentY, dialogX + sidebarW, dialogY + dialogH, 0xDD12141C);
        graphics.fill(dialogX + sidebarW - 1, contentY, dialogX + sidebarW, dialogY + dialogH, 0xFF2A364D);

        SettingsTab[] tabs = SettingsTab.values();
        int tabBtnH = 24;
        for (int i = 0; i < tabs.length; i++) {
            renderSidebarTabButton(graphics, font, dialogX, contentY, sidebarW, tabBtnH, i, tabs[i], mouseX, mouseY);
        }
    }

    private void renderSidebarTabButton(GuiGraphics graphics, Font font, int dialogX, int contentY, int sidebarW,
                                        int tabBtnH, int index, SettingsTab tab, int mouseX, int mouseY) {
        int tabY = contentY + 6 + index * (tabBtnH + 4);
        boolean isSelected = (index == activeTab);
        boolean tabHover = mouseX >= dialogX + 4 && mouseX <= dialogX + sidebarW - 4 && mouseY >= tabY && mouseY <= tabY + tabBtnH;

        int tabBg = isSelected ? 0xFF253347 : (tabHover ? 0xFF1C2536 : 0);
        int tabBorder = isSelected ? 0xFF5B9BD5 : (tabHover ? 0xFF3D4B66 : 0);
        if (tabBg != 0) {
            graphics.fill(dialogX + 4, tabY, dialogX + sidebarW - 4, tabY + tabBtnH, tabBg);
        }
        if (tabBorder != 0) {
            graphics.renderOutline(dialogX + 4, tabY, sidebarW - 8, tabBtnH, tabBorder);
        }

        int textColor = isSelected ? 0xFFFFFFFF : (tabHover ? 0xFFDDDDDD : 0xFF99AABF);
        graphics.drawString(font, tab.getDisplayName(), dialogX + 8, tabY + 8, textColor, false);

        if (tab == SettingsTab.UPDATES && ClientUpdateNotifier.getInstance().isBadgeVisible()) {
            int dotX = dialogX + sidebarW - 14;
            int dotY = tabY + 10;
            graphics.fill(dotX, dotY, dotX + 4, dotY + 4, 0xFF10B981);
            graphics.renderOutline(dotX, dotY, 4, 4, 0xFF064E3B);
        }
    }

    private void renderActiveTabContent(GuiGraphics graphics, Font font, int dialogX, int dialogY,
                                        int dialogW, int dialogH, int mouseX, int mouseY) {
        int mainX = dialogX + SIDEBAR_WIDTH + 8;
        int mainY = dialogY + 26 + 8;
        int mainW = dialogW - SIDEBAR_WIDTH - 16;
        int mainH = dialogH - 26 - 16;

        if (activeTab >= 0 && activeTab < tabHandlers.length) {
            tabHandlers[activeTab].render(graphics, font, mainX, mainY, mainW, mainH, mouseX, mouseY);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        int screenWidth = parent.width;
        int screenHeight = parent.height;

        int dialogW = Math.min(DIALOG_WIDTH, screenWidth - 24);
        int dialogH = Math.min(DIALOG_HEIGHT, screenHeight - 24);
        int dialogX = (screenWidth - dialogW) / 2;
        int dialogY = (screenHeight - dialogH) / 2;

        if (mouseX < dialogX || mouseX > dialogX + dialogW || mouseY < dialogY || mouseY > dialogY + dialogH) {
            close();
            playClickSound();
            return true;
        }

        int closeBtnX = dialogX + dialogW - 20;
        int closeBtnY = dialogY + 5;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 16 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16) {
            close();
            playClickSound();
            return true;
        }

        if (handleSidebarClick(mouseX, mouseY, dialogX, dialogY, dialogH)) {
            return true;
        }

        int mainX = dialogX + SIDEBAR_WIDTH + 8;
        int mainY = dialogY + 26 + 8;
        int mainW = dialogW - SIDEBAR_WIDTH - 16;
        int mainH = dialogH - 26 - 16;

        if (activeTab >= 0 && activeTab < tabHandlers.length) {
            return tabHandlers[activeTab].mouseClicked(mouseX, mouseY, mainX, mainY, mainW, mainH, button);
        }

        return true;
    }

    private boolean handleSidebarClick(double mouseX, double mouseY, int dialogX, int dialogY, int dialogH) {
        int contentY = dialogY + 26;
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
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;
        if (activeTab >= 0 && activeTab < tabHandlers.length) {
            return tabHandlers[activeTab].mouseScrolled(mouseX, mouseY, delta);
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (activeTab >= 0 && activeTab < tabHandlers.length) {
            return tabHandlers[activeTab].charTyped(codePoint, modifiers);
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (activeTab >= 0 && activeTab < tabHandlers.length && tabHandlers[activeTab].keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            playClickSound();
            return true;
        }
        return true;
    }

    public void onSettingsChanged() {
        playClickSound();
        BoardManager.getInstance().saveForCurrentContext();
        parent.rebuildWidgets();
        parent.markSummaryDirty();
    }

    public void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }
}
