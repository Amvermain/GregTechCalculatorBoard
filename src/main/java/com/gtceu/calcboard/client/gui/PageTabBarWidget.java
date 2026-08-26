package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.BoardManager;
import com.gtceu.calcboard.api.BoardPage;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Top bar widget for managing and switching between multiple board preset pages / tabs.
 * Supports Local Board pages and Team Board pages, inline renaming, horizontal drag/wheel scrolling,
 * and confirmation modal on tab deletion.
 */
public class PageTabBarWidget {
    public static final int TAB_HEIGHT = 18;
    public static final int TAB_Y = 22;
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
        ClientWorkspaceState teamState = ClientWorkspaceState.getInstance();
        boolean isTeam = teamState.isTeamMode();
        Font font = Minecraft.getInstance().font;

        List<String> pageTitles = new ArrayList<>();
        int activeIdx = 0;

        if (isTeam) {
            List<TeamWorkspacePage> teamPages = new ArrayList<>(teamState.getRemotePages());
            String activePageId = teamState.getActiveTeamPageId();
            for (int i = 0; i < teamPages.size(); i++) {
                TeamWorkspacePage tp = teamPages.get(i);
                pageTitles.add(tp.getTitle());
                if (tp.getPageId().equals(activePageId)) {
                    activeIdx = i;
                }
            }
        } else {
            BoardManager bm = BoardManager.getInstance();
            List<BoardPage> pages = bm.getPages();
            activeIdx = bm.getActivePageIndex();
            for (BoardPage p : pages) {
                pageTitles.add(p.getName());
            }
        }

        if (pageTitles.isEmpty()) {
            pageTitles.add("Page 1");
        }

        // 1. Calculate total width of all tabs + Add button
        int totalWidth = 6;
        for (int i = 0; i < pageTitles.size(); i++) {
            String title = pageTitles.get(i);
            int textW = font.width(title);
            int tabW = textW + (pageTitles.size() > 1 ? 26 : 16);
            totalWidth += tabW + 3;
        }
        int addW = 18;
        totalWidth += addW + 6;

        int leftMargin = screen.getDynamicLeftMargin();
        this.maxScrollX = Math.max(0, totalWidth - (screen.width - leftMargin));
        this.scrollX = Math.max(0, Math.min(maxScrollX, scrollX));

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();

        int tabY = screen.getPageTabY();

        // Enable horizontal scissor so tabs don't overflow outside screen or left margin
        graphics.enableScissor(leftMargin - 2, tabY - 2, screen.width, tabY + TAB_HEIGHT + 4);

        graphics.pose().pushPose();
        graphics.pose().translate((float) -scrollX, 0, 0);

        int curX = leftMargin;

        for (int i = 0; i < pageTitles.size(); i++) {
            String pageName = pageTitles.get(i);
            boolean isActive = (i == activeIdx);

            int textW = font.width(pageName);
            int tabW = textW + (pageTitles.size() > 1 ? 26 : 16);

            double virtualMouseX = mouseX + scrollX;
            boolean hover = virtualMouseX >= curX && virtualMouseX <= curX + tabW && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT;

            // Draw Tab Background
            int bg = isActive ? (isTeam ? 0xFF1C4232 : 0xFF2A623A) : (hover ? 0xFF353C4D : 0xFF222630);
            int border = isActive ? (isTeam ? 0xFF55FF88 : 0xFF55FF88) : (hover ? 0xFF5577AA : 0xFF3D4455);
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
            if (pageTitles.size() > 1 && editingPageIndex != i) {
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
        int tabY = screen.getPageTabY();
        if (mouseY < tabY || mouseY > tabY + TAB_HEIGHT + 2) {
            commitRename();
            return false;
        }

        ClientWorkspaceState teamState = ClientWorkspaceState.getInstance();
        boolean isTeam = teamState.isTeamMode();
        Font font = Minecraft.getInstance().font;

        List<String> pageTitles = new ArrayList<>();
        List<TeamWorkspacePage> teamPages = isTeam ? new ArrayList<>(teamState.getRemotePages()) : null;
        int activeIdx = 0;

        if (isTeam) {
            String activePageId = teamState.getActiveTeamPageId();
            for (int i = 0; i < teamPages.size(); i++) {
                TeamWorkspacePage tp = teamPages.get(i);
                pageTitles.add(tp.getTitle());
                if (tp.getPageId().equals(activePageId)) {
                    activeIdx = i;
                }
            }
        } else {
            BoardManager bm = BoardManager.getInstance();
            List<BoardPage> pages = bm.getPages();
            activeIdx = bm.getActivePageIndex();
            for (BoardPage p : pages) {
                pageTitles.add(p.getName());
            }
        }

        if (pageTitles.isEmpty()) {
            pageTitles.add("Page 1");
        }

        double virtualMouseX = mouseX + scrollX;
        int curX = screen.getDynamicLeftMargin();

        for (int i = 0; i < pageTitles.size(); i++) {
            String pageName = pageTitles.get(i);
            int textW = font.width(pageName);
            int tabW = textW + (pageTitles.size() > 1 ? 26 : 16);

            if (virtualMouseX >= curX && virtualMouseX <= curX + tabW) {
                // 1. Delete [x] button clicked
                if (pageTitles.size() > 1 && virtualMouseX >= curX + tabW - 14 && virtualMouseX <= curX + tabW - 2 && button == 0) {
                    commitRename();
                    if (isTeam) {
                        if (teamPages != null && i < teamPages.size()) {
                            TeamWorkspacePage tp = teamPages.get(i);
                            screen.openDeleteTeamPageDialog(tp.getPageId(), tp.getTitle());
                        }
                    } else {
                        BoardManager bm = BoardManager.getInstance();
                        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                            bm.removePage(i);
                            screen.rebuildWidgets();
                            Minecraft.getInstance().getSoundManager().play(
                                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK, 1.0F)
                            );
                        } else {
                            screen.openDeletePageDialog(i, pageName);
                        }
                    }
                    return true;
                }

                // 2. Double-Click or Right-Click to Rename Tab (Local mode only)
                long now = System.currentTimeMillis();
                boolean isDoubleClick = (now - lastClickTime < 350 && lastClickedTabIdx == i && button == 0);
                boolean isRightClick = (button == 1);

                if (!isTeam && (isDoubleClick || isRightClick)) {
                    startRename(i, pageName, curX + 16, TAB_Y + 1, textW + 10);
                    lastClickTime = 0;
                    lastClickedTabIdx = -1;
                    return true;
                }

                // 3. Single Left-Click: Switch Tab
                if (button == 0) {
                    lastClickTime = now;
                    lastClickedTabIdx = i;
                    commitRename();

                    if (isTeam) {
                        if (teamPages != null && i < teamPages.size()) {
                            String newPageId = teamPages.get(i).getPageId();
                            if (!newPageId.equals(teamState.getActiveTeamPageId())) {
                                teamState.autoCommitAndRelease(screen, teamState.getActiveTeamPageId());
                                teamState.setActiveTeamPageId(newPageId);
                                com.gtceu.calcboard.network.NetworkHandler.sendToServer(
                                    new com.gtceu.calcboard.network.packet.c2s.C2SPingPresencePacket(teamState.getCurrentTeamId(), newPageId, true)
                                );
                                screen.rebuildWidgets();
                                screen.markSummaryDirty();
                                playClickSound();
                            }
                        }
                    } else {
                        if (i != activeIdx) {
                            BoardManager bm = BoardManager.getInstance();
                            BoardPage cur = bm.getActivePage();
                            if (cur != null) {
                                cur.setPanX(screen.getPanX());
                                cur.setPanY(screen.getPanY());
                                cur.setZoom(screen.getZoom());
                            }
                            bm.switchPage(i);
                            BoardPage next = bm.getActivePage();
                            if (next != null) {
                                screen.setPanX(next.getPanX());
                                screen.setPanY(next.getPanY());
                                screen.setZoom(next.getZoom());
                            }
                            screen.rebuildWidgets();
                            playClickSound();
                        }
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
            if (isTeam) {
                if (screen.getExportToTeamDialog() != null) {
                    screen.getExportToTeamDialog().open();
                }
            } else {
                BoardManager bm = BoardManager.getInstance();
                bm.addPage("Page " + (pageTitles.size() + 1));
                this.scrollX = this.maxScrollX + 100;
                screen.rebuildWidgets();
                playClickSound();
            }
            return true;
        }

        // If tabs overflow and require scrolling, allow drag-scrolling within the tab track
        if (maxScrollX > 0 && mouseX <= (curX + addW + 20) && (button == 0 || button == 2)) {
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
        int tabY = screen.getPageTabY();
        if (mouseY >= tabY - 2 && mouseY <= tabY + TAB_HEIGHT + 2) {
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
                com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onNodeRenamed();
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
            renameBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
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
