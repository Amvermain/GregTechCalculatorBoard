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
 * Supports double-click inline renaming and horizontal drag/wheel scrolling.
 */
public class PageTabBarWidget {
    public static final int TAB_HEIGHT = 18;
    private final BoardScreen screen;

    private int editingPageIndex = -1;
    private EditBox renameBox = null;

    // Scrolling & Dragging State
    private double scrollX = 0;
    private double maxScrollX = 0;
    private boolean isDraggingTabBar = false;
    private double dragStartX = 0;
    private double initialScrollX = 0;
    private boolean hasDragged = false;

    // Double-click detection state
    private long lastClickTime = 0;
    private int lastClickedTabIdx = -1;

    public PageTabBarWidget(BoardScreen screen) {
        this.screen = screen;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        BoardManager bm = BoardManager.getInstance();
        List<BoardPage> pages = bm.getPages();
        int activeIdx = bm.getActivePageIndex();
        Font font = Minecraft.getInstance().font;

        // 1. Calculate total width of all tabs + Add button
        int totalWidth = 6;
        for (int i = 0; i < pages.size(); i++) {
            BoardPage page = pages.get(i);
            int textW = font.width(page.getName());
            int tabW = textW + (pages.size() > 1 ? 26 : 16);
            totalWidth += tabW + 3;
        }
        int addW = 18;
        totalWidth += addW + 6;

        this.maxScrollX = Math.max(0, totalWidth - screen.width);
        this.scrollX = Math.max(0, Math.min(maxScrollX, scrollX));

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();

        // Enable horizontal scissor so tabs don't overflow outside screen
        graphics.enableScissor(0, 0, screen.width, TAB_HEIGHT + 8);

        graphics.pose().pushPose();
        graphics.pose().translate((float) -scrollX, 0, 0);

        int curX = 6;
        int tabY = 4;

        for (int i = 0; i < pages.size(); i++) {
            BoardPage page = pages.get(i);
            boolean isActive = (i == activeIdx);
            String pageName = page.getName();

            int textW = font.width(pageName);
            int tabW = textW + (pages.size() > 1 ? 26 : 16);

            double virtualMouseX = mouseX + scrollX;
            boolean hover = virtualMouseX >= curX && virtualMouseX <= curX + tabW && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT;

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
                renameBox.render(graphics, (int) virtualMouseX, mouseY, partialTicks);
            } else {
                graphics.drawString(font, prefix + pageName, curX + 4, tabY + 5, isActive ? 0xFFFFFFFF : 0xFFAAAAAA, false);
            }

            // Delete [x] button on tab (if more than 1 page)
            if (pages.size() > 1 && editingPageIndex != i) {
                int closeX = curX + tabW - 12;
                int closeY = tabY + 4;
                boolean closeHover = virtualMouseX >= closeX && virtualMouseX <= closeX + 10 && mouseY >= closeY && mouseY <= closeY + 10;
                graphics.drawString(font, "x", closeX + 1, closeY, closeHover ? 0xFFFF4444 : 0x88888888, false);
            }

            curX += tabW + 3;
        }

        // Add New Page Button [+]
        double virtualMouseX = mouseX + scrollX;
        boolean addHover = virtualMouseX >= curX && virtualMouseX <= curX + addW && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT;
        graphics.fill(curX, tabY, curX + addW, tabY + TAB_HEIGHT, addHover ? 0xFF2A623A : 0xFF222630);
        graphics.renderOutline(curX, tabY, addW, TAB_HEIGHT, addHover ? 0xFF55FF88 : 0xFF3D4455);
        graphics.drawCenteredString(font, "§a+", curX + addW / 2, tabY + 5, 0xFFFFFFFF);

        graphics.pose().popPose();
        graphics.disableScissor();

        // Scroll overflow edge indicators
        if (maxScrollX > 0) {
            if (scrollX > 2) {
                graphics.fill(0, tabY, 14, tabY + TAB_HEIGHT, 0xCC11151C);
                graphics.drawString(font, "§a«", 2, tabY + 5, 0xFF55FF88, false);
            }
            if (scrollX < maxScrollX - 2) {
                graphics.fill(screen.width - 14, tabY, screen.width, tabY + TAB_HEIGHT, 0xCC11151C);
                graphics.drawString(font, "§a»", screen.width - 10, tabY + 5, 0xFF55FF88, false);
            }
        }

        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY < 2 || mouseY > 4 + TAB_HEIGHT + 2) {
            commitRename();
            return false;
        }

        BoardManager bm = BoardManager.getInstance();
        List<BoardPage> pages = bm.getPages();
        int activeIdx = bm.getActivePageIndex();
        Font font = Minecraft.getInstance().font;

        double virtualMouseX = mouseX + scrollX;
        int curX = 6;
        int tabY = 4;

        for (int i = 0; i < pages.size(); i++) {
            BoardPage page = pages.get(i);
            String pageName = page.getName();
            int textW = font.width(pageName);
            int tabW = textW + (pages.size() > 1 ? 26 : 16);

            if (virtualMouseX >= curX && virtualMouseX <= curX + tabW) {
                // 1. Delete [x] button clicked
                if (pages.size() > 1 && virtualMouseX >= curX + tabW - 14 && virtualMouseX <= curX + tabW - 2 && button == 0) {
                    commitRename();
                    bm.removePage(i);
                    screen.rebuildWidgets();
                    playClickSound();
                    return true;
                }

                // 2. Double-Click or Right-Click to Rename Tab
                long now = System.currentTimeMillis();
                boolean isDoubleClick = (now - lastClickTime < 350 && lastClickedTabIdx == i && button == 0);
                boolean isRightClick = (button == 1);

                if (isDoubleClick || isRightClick) {
                    startRename(i, page.getName(), curX + 16, tabY + 1, textW + 10);
                    lastClickTime = 0;
                    lastClickedTabIdx = -1;
                    return true;
                }

                // 3. Single Left-Click: Switch Tab
                if (button == 0) {
                    lastClickTime = now;
                    lastClickedTabIdx = i;
                    commitRename();
                    if (i != activeIdx) {
                        bm.switchPage(i);
                        screen.rebuildWidgets();
                        playClickSound();
                    }
                    return true;
                }
                return true;
            }

            curX += tabW + 3;
        }

        // Add Button [+] clicked
        int addW = 18;
        if (virtualMouseX >= curX && virtualMouseX <= curX + addW && button == 0) {
            commitRename();
            bm.addPage("Page " + (pages.size() + 1));
            // Auto scroll to the end
            this.scrollX = this.maxScrollX + 100;
            screen.rebuildWidgets();
            playClickSound();
            return true;
        }

        // Clicked on empty tab bar area -> Start drag scrolling
        if (button == 0 || button == 2) {
            commitRename();
            this.isDraggingTabBar = true;
            this.dragStartX = mouseX;
            this.initialScrollX = this.scrollX;
            this.hasDragged = false;
            return true;
        }

        commitRename();
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingTabBar) {
            double deltaX = mouseX - dragStartX;
            if (Math.abs(deltaX) > 2) {
                hasDragged = true;
            }
            this.scrollX = Math.max(0, Math.min(maxScrollX, initialScrollX - deltaX));
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingTabBar) {
            isDraggingTabBar = false;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= 0 && mouseY <= 4 + TAB_HEIGHT + 4) {
            if (maxScrollX > 0) {
                this.scrollX = Math.max(0, Math.min(maxScrollX, scrollX - delta * 30.0));
                return true;
            }
        }
        return false;
    }

    private void startRename(int index, String currentName, int x, int y, int width) {
        this.editingPageIndex = index;
        Font font = Minecraft.getInstance().font;
        this.renameBox = new EditBox(font, x, y, Math.max(60, width), 16, Component.translatable("gui.gtcalcboard.page_name_hint"));
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
