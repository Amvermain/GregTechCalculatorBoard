package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.BoardManager;
import com.gtceu.calcboard.api.BoardPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Top bar widget for managing and switching between multiple board preset pages / tabs.
 */
public class PageTabBarWidget {
    public static final int TAB_HEIGHT = 18;
    private final BoardScreen screen;

    private int editingPageIndex = -1;
    private EditBox renameBox = null;

    public PageTabBarWidget(BoardScreen screen) {
        this.screen = screen;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();

        BoardManager bm = BoardManager.getInstance();
        List<BoardPage> pages = bm.getPages();
        int activeIdx = bm.getActivePageIndex();
        Font font = Minecraft.getInstance().font;

        int curX = 6;
        int tabY = 4;

        for (int i = 0; i < pages.size(); i++) {
            BoardPage page = pages.get(i);
            boolean isActive = (i == activeIdx);
            String pageName = page.getName();

            int textW = font.width(pageName);
            int tabW = textW + (pages.size() > 1 ? 26 : 16);

            boolean hover = mouseX >= curX && mouseX <= curX + tabW && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT;

            // Draw Tab Background
            int bg = isActive ? 0xFF2A623A : (hover ? 0xFF353C4D : 0xFF222630);
            int border = isActive ? 0xFF55FF88 : (hover ? 0xFF5577AA : 0xFF3D4455);
            graphics.fill(curX, tabY, curX + tabW, tabY + TAB_HEIGHT, bg);
            graphics.renderOutline(curX, tabY, tabW, TAB_HEIGHT, border);

            // Tab Icon & Name
            String prefix = isActive ? "§a📑 " : "§7📄 ";
            if (editingPageIndex == i && renameBox != null) {
                renameBox.setX(curX + 16);
                renameBox.setY(tabY + 1);
                renameBox.render(graphics, mouseX, mouseY, partialTicks);
            } else {
                graphics.drawString(font, prefix + pageName, curX + 4, tabY + 5, isActive ? 0xFFFFFFFF : 0xFFAAAAAA, false);
            }

            // Delete [x] button on tab (if more than 1 page)
            if (pages.size() > 1 && editingPageIndex != i) {
                int closeX = curX + tabW - 12;
                int closeY = tabY + 4;
                boolean closeHover = mouseX >= closeX && mouseX <= closeX + 10 && mouseY >= closeY && mouseY <= closeY + 10;
                graphics.drawString(font, "x", closeX + 1, closeY, closeHover ? 0xFFFF4444 : 0x88888888, false);
            }

            curX += tabW + 3;
        }

        // Add New Page Button [+]
        int addW = 18;
        boolean addHover = mouseX >= curX && mouseX <= curX + addW && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT;
        graphics.fill(curX, tabY, curX + addW, tabY + TAB_HEIGHT, addHover ? 0xFF2A623A : 0xFF222630);
        graphics.renderOutline(curX, tabY, addW, TAB_HEIGHT, addHover ? 0xFF55FF88 : 0xFF3D4455);
        graphics.drawCenteredString(font, "§a+", curX + addW / 2, tabY + 5, 0xFFFFFFFF);

        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY < 4 || mouseY > 4 + TAB_HEIGHT) {
            commitRename();
            return false;
        }

        BoardManager bm = BoardManager.getInstance();
        List<BoardPage> pages = bm.getPages();
        int activeIdx = bm.getActivePageIndex();
        Font font = Minecraft.getInstance().font;

        int curX = 6;
        int tabY = 4;

        for (int i = 0; i < pages.size(); i++) {
            BoardPage page = pages.get(i);
            String pageName = page.getName();
            int textW = font.width(pageName);
            int tabW = textW + (pages.size() > 1 ? 26 : 16);

            if (mouseX >= curX && mouseX <= curX + tabW) {
                // Delete [x] button clicked
                if (pages.size() > 1 && mouseX >= curX + tabW - 14 && mouseX <= curX + tabW - 2) {
                    commitRename();
                    bm.removePage(i);
                    screen.rebuildWidgets();
                    playClickSound();
                    return true;
                }

                // If clicked already active tab -> start rename on double click / click
                if (i == activeIdx && button == 1) {
                    startRename(i, page.getName(), curX + 16, tabY + 1, textW + 10);
                    return true;
                }

                // Switch to this tab
                commitRename();
                bm.switchPage(i);
                screen.rebuildWidgets();
                playClickSound();
                return true;
            }

            curX += tabW + 3;
        }

        // Add Button [+] clicked
        int addW = 18;
        if (mouseX >= curX && mouseX <= curX + addW) {
            commitRename();
            bm.addPage("Page " + (pages.size() + 1));
            screen.rebuildWidgets();
            playClickSound();
            return true;
        }

        commitRename();
        return false;
    }

    private void startRename(int index, String currentName, int x, int y, int width) {
        this.editingPageIndex = index;
        Font font = Minecraft.getInstance().font;
        this.renameBox = new EditBox(font, x, y, Math.max(60, width), 16, Component.literal("Page Name"));
        this.renameBox.setValue(currentName);
        this.renameBox.setFocused(true);
    }

    public void commitRename() {
        if (editingPageIndex >= 0 && renameBox != null) {
            String val = renameBox.getValue().trim();
            if (!val.isEmpty()) {
                BoardManager.getInstance().renamePage(editingPageIndex, val);
            }
            editingPageIndex = -1;
            renameBox = null;
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingPageIndex >= 0 && renameBox != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitRename();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                editingPageIndex = -1;
                renameBox = null;
                return true;
            }
            return renameBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (editingPageIndex >= 0 && renameBox != null) {
            return renameBox.charTyped(codePoint, modifiers);
        }
        return false;
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }
}
