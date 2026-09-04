package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
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
 * Top bar widget for managing and switching between multiple open board preset pages / tabs (IDE/Browser style).
 * Supports Local Board open tabs and Team Board pages, inline renaming, horizontal drag/wheel scrolling,
 * tab close (undock), middle-click close, and confirmation modal on tab deletion.
 */
public class PageTabBarWidget {
    public static final int TAB_HEIGHT = 18;
    public static final int TAB_Y = 22;
    private final BoardScreen screen;

    private int editingPageIndex = -1;
    private EditBox renameBox = null;

    private double scrollX = 0;
    private double maxScrollX = 0;
    private boolean isDraggingTabBar = false;
    private double dragStartX = 0;
    private double initialScrollX = 0;
    private boolean hasDragged = false;

    private long lastClickTime = 0;
    private int lastClickedTabIdx = -1;

    public PageTabBarWidget(BoardScreen screen) {
        this.screen = screen;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        ClientWorkspaceState teamState = ClientWorkspaceState.getInstance();
        boolean isTeam = teamState.isTeamMode();
        Font font = Minecraft.getInstance().font;

        List<String> pageTitles = getPageTitles(teamState, isTeam);
        int activeIdx = getActivePageIndex(teamState, isTeam);

        int browserBtnW = 22;
        int leftMargin = screen.getDynamicLeftMargin() + browserBtnW + 4;
        int totalWidth = calculateTotalWidth(pageTitles, font, activeIdx, isTeam);
        int navBtnW = 16;
        int rightPadding = 16;
        this.maxScrollX = Math.max(0, (totalWidth + rightPadding) - (screen.width - leftMargin));
        this.scrollX = Math.max(0, Math.min(maxScrollX, scrollX));

        boolean hasLeftBtn = maxScrollX > 0 && scrollX > 1;
        boolean hasRightBtn = maxScrollX > 0 && scrollX < maxScrollX - 1;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();

        int tabY = screen.getPageTabY();
        renderBrowserToggleButton(graphics, font, mouseX, mouseY, tabY, browserBtnW);

        int scissorLeft = hasLeftBtn ? (leftMargin + navBtnW + 2) : (leftMargin - 2);
        int scissorRight = hasRightBtn ? (screen.width - navBtnW - 2) : screen.width;
        BoardScissorHelper.enableScissor(graphics, scissorLeft, tabY - 2, scissorRight, tabY + TAB_HEIGHT + 4);

        graphics.pose().pushPose();
        graphics.pose().translate((float) -scrollX, 0, 0);

        int curX = renderTabList(graphics, font, pageTitles, activeIdx, isTeam, leftMargin, tabY, mouseX, mouseY, partialTicks);
        renderAddTabButton(graphics, font, curX, tabY, mouseX);

        graphics.pose().popPose();
        BoardScissorHelper.disableScissor(graphics);

        renderScrollButtons(graphics, font, hasLeftBtn, hasRightBtn, leftMargin, navBtnW, tabY, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private void renderBrowserToggleButton(GuiGraphics graphics, Font font, int mouseX, int mouseY, int tabY, int browserBtnW) {
        int brX = screen.getDynamicLeftMargin();
        boolean brHover = mouseX >= brX && mouseX <= brX + browserBtnW && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT;
        boolean isDrawerOpen = screen.getPageBrowserDrawer() != null && screen.getPageBrowserDrawer().isOpen();
        graphics.fill(brX, tabY, brX + browserBtnW, tabY + TAB_HEIGHT, isDrawerOpen ? 0xFF2A4A38 : (brHover ? 0xFF2A364C : 0xFF1C2230));
        graphics.renderOutline(brX, tabY, browserBtnW, TAB_HEIGHT, isDrawerOpen ? 0xFF55FF88 : (brHover ? 0xFF5588DD : 0xFF353C4D));
        graphics.drawCenteredString(font, isDrawerOpen ? "§a≡" : "§f≡", brX + browserBtnW / 2, tabY + 5, 0xFFFFFFFF);
    }

    private int renderTabList(GuiGraphics graphics, Font font, List<String> pageTitles, int activeIdx, boolean isTeam, int leftMargin, int tabY, int mouseX, int mouseY, float partialTicks) {
        int curX = leftMargin;

        for (int i = 0; i < pageTitles.size(); i++) {
            String pageName = pageTitles.get(i);
            boolean isActive = (i == activeIdx);
            String prefix = resolveTabPrefix(i, isActive, isTeam);
            int tabW = computeTabWidth(font, pageName, prefix, pageTitles.size());

            double virtualMouseX = mouseX + scrollX;
            boolean hover = virtualMouseX >= curX && virtualMouseX <= curX + tabW && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT;

            int bg = isActive ? (isTeam ? 0xFF1C4232 : 0xFF2A623A) : (hover ? 0xFF353C4D : 0xFF222630);
            int border = isActive ? 0xFF55FF88 : (hover ? 0xFF5577AA : 0xFF3D4455);
            graphics.fill(curX, tabY, curX + tabW, tabY + TAB_HEIGHT, bg);
            graphics.renderOutline(curX, tabY, tabW, TAB_HEIGHT, border);

            if (editingPageIndex == i && renameBox != null) {
                renameBox.setX(curX + 16);
                renameBox.setY(tabY + 1);
                renameBox.render(graphics, (int) virtualMouseX, mouseY, partialTicks);
            } else {
                graphics.drawString(font, prefix + pageName, curX + 4, tabY + 5, isActive ? 0xFFFFFFFF : 0xFFAAAAAA, false);
            }

            if (pageTitles.size() > 1 && editingPageIndex != i) {
                int closeX = curX + tabW - 12;
                int closeY = tabY + 4;
                boolean closeHover = virtualMouseX >= closeX && virtualMouseX <= closeX + 10 && mouseY >= closeY && mouseY <= closeY + 10;
                graphics.drawString(font, "x", closeX + 1, closeY, closeHover ? 0xFFFF4444 : 0x88888888, false);
            }

            curX += tabW + 3;
        }
        return curX;
    }

    private void renderAddTabButton(GuiGraphics graphics, Font font, int curX, int tabY, int mouseX) {
        int addW = 18;
        double virtualMouseX = mouseX + scrollX;
        boolean addHover = virtualMouseX >= curX && virtualMouseX <= curX + addW;
        graphics.fill(curX, tabY, curX + addW, tabY + TAB_HEIGHT, addHover ? 0xFF2A623A : 0xFF222630);
        graphics.renderOutline(curX, tabY, addW, TAB_HEIGHT, addHover ? 0xFF55FF88 : 0xFF3D4455);
        graphics.drawCenteredString(font, "§a+", curX + addW / 2, tabY + 5, 0xFFFFFFFF);
    }

    private void renderScrollButtons(GuiGraphics graphics, Font font, boolean hasLeft, boolean hasRight, int leftMargin, int navBtnW, int tabY, int mouseX, int mouseY) {
        if (hasLeft) {
            int btnX = leftMargin;
            boolean btnHover = mouseX >= btnX && mouseX <= btnX + navBtnW && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT;
            graphics.fill(btnX, tabY, btnX + navBtnW, tabY + TAB_HEIGHT, btnHover ? 0xFF2A364C : 0xEE11151C);
            graphics.renderOutline(btnX, tabY, navBtnW, TAB_HEIGHT, btnHover ? 0xFF55FF88 : 0xFF353C4D);
            graphics.drawCenteredString(font, "§a«", btnX + navBtnW / 2, tabY + 5, btnHover ? 0xFF55FF88 : 0xFF88AA99);
        }
        if (hasRight) {
            int btnX = screen.width - navBtnW - 2;
            boolean btnHover = mouseX >= btnX && mouseX <= btnX + navBtnW && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT;
            graphics.fill(btnX, tabY, btnX + navBtnW, tabY + TAB_HEIGHT, btnHover ? 0xFF2A364C : 0xEE11151C);
            graphics.renderOutline(btnX, tabY, navBtnW, TAB_HEIGHT, btnHover ? 0xFF55FF88 : 0xFF353C4D);
            graphics.drawCenteredString(font, "§a»", btnX + navBtnW / 2, tabY + 5, btnHover ? 0xFF55FF88 : 0xFF88AA99);
        }
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

        List<String> pageTitles = getPageTitles(teamState, isTeam);
        int activeIdx = getActivePageIndex(teamState, isTeam);

        int browserBtnW = 22;
        int leftMargin = screen.getDynamicLeftMargin() + browserBtnW + 4;
        int totalWidth = calculateTotalWidth(pageTitles, font, activeIdx, isTeam);
        int navBtnW = 16;
        int rightPadding = 16;
        this.maxScrollX = Math.max(0, (totalWidth + rightPadding) - (screen.width - leftMargin));
        this.scrollX = Math.max(0, Math.min(maxScrollX, scrollX));

        if (handleNavigationButtonClick(mouseX, button, leftMargin, navBtnW, browserBtnW)) {
            return true;
        }

        double virtualMouseX = mouseX + scrollX;
        int curX = leftMargin;

        for (int i = 0; i < pageTitles.size(); i++) {
            String pageName = pageTitles.get(i);
            boolean isActive = (i == activeIdx);
            String prefix = resolveTabPrefix(i, isActive, isTeam);
            int textW = font.width(prefix + pageName);
            int tabW = computeTabWidth(font, pageName, prefix, pageTitles.size());

            if (virtualMouseX >= curX && virtualMouseX <= curX + tabW) {
                return handleTabItemClick(i, pageName, activeIdx, isTeam, teamState, button, virtualMouseX, curX, tabW, textW, tabY);
            }
            curX += tabW + 3;
        }

        int addW = 18;
        if (virtualMouseX >= curX && virtualMouseX <= curX + addW && button == 0) {
            return handleAddPageClick(isTeam, pageTitles.size());
        }

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

    private List<String> getPageTitles(ClientWorkspaceState teamState, boolean isTeam) {
        List<String> titles = new ArrayList<>();
        if (isTeam) {
            for (TeamWorkspacePage tp : teamState.getRemotePages()) {
                titles.add(tp.getTitle());
            }
        } else {
            for (BoardPage p : BoardManager.getInstance().getOpenPages()) {
                titles.add(p.getName());
            }
        }
        if (titles.isEmpty()) {
            titles.add("Page 1");
        }
        return titles;
    }

    private int getActivePageIndex(ClientWorkspaceState teamState, boolean isTeam) {
        if (!isTeam) {
            BoardManager bm = BoardManager.getInstance();
            String activeId = bm.getActivePage().getId();
            List<BoardPage> openPages = bm.getOpenPages();
            for (int i = 0; i < openPages.size(); i++) {
                if (openPages.get(i).getId().equals(activeId)) {
                    return i;
                }
            }
            return 0;
        }
        String activePageId = teamState.getActiveTeamPageId();
        List<TeamWorkspacePage> teamPages = new ArrayList<>(teamState.getRemotePages());
        for (int i = 0; i < teamPages.size(); i++) {
            if (teamPages.get(i).getPageId().equals(activePageId)) {
                return i;
            }
        }
        return 0;
    }

    private boolean handleNavigationButtonClick(double mouseX, int button, int leftMargin, int navBtnW, int browserBtnW) {
        int brX = screen.getDynamicLeftMargin();
        if (mouseX >= brX && mouseX <= brX + browserBtnW && button == 0) {
            commitRename();
            if (screen.getPageBrowserDrawer() != null) {
                screen.getPageBrowserDrawer().toggle();
                playClickSound();
            }
            return true;
        }

        boolean hasLeftBtn = maxScrollX > 0 && scrollX > 1;
        if (hasLeftBtn && mouseX <= leftMargin + navBtnW + 2 && button == 0) {
            commitRename();
            this.scrollX = Math.max(0, this.scrollX - 80);
            playClickSound();
            return true;
        }

        boolean hasRightBtn = maxScrollX > 0 && scrollX < maxScrollX - 1;
        if (hasRightBtn && mouseX >= screen.width - navBtnW - 4 && mouseX <= screen.width && button == 0) {
            commitRename();
            this.scrollX = Math.min(maxScrollX, this.scrollX + 80);
            playClickSound();
            return true;
        }

        return false;
    }

    private boolean handleTabItemClick(int index, String pageName, int activeIdx, boolean isTeam, ClientWorkspaceState teamState, int button, double virtualMouseX, int curX, int tabW, int textW, int tabY) {
        int pageCount = isTeam ? teamState.getRemotePages().size() : BoardManager.getInstance().getOpenPages().size();
        boolean isCloseIconClicked = pageCount > 1 && virtualMouseX >= curX + tabW - 14 && virtualMouseX <= curX + tabW - 2 && button == 0;
        boolean isMiddleClicked = (button == 2);
        if (isCloseIconClicked || isMiddleClicked) {
            return handleCloseTabClick(index, pageName, isTeam, teamState);
        }

        long now = System.currentTimeMillis();
        boolean isDoubleClick = (now - lastClickTime < 350 && lastClickedTabIdx == index && button == 0);
        boolean isRightClick = (button == 1);

        if (!isTeam && isRightClick && net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            BoardManager bm = BoardManager.getInstance();
            List<BoardPage> openPages = bm.getOpenPages();
            if (index < openPages.size()) {
                screen.openTemplateCloneDialog(openPages.get(index));
                return true;
            }
        }

        if (!isTeam && (isDoubleClick || isRightClick)) {
            startRename(index, pageName, curX + 16, tabY + 1, textW + 10);
            lastClickTime = 0;
            lastClickedTabIdx = -1;
            return true;
        }

        if (button == 0) {
            lastClickTime = now;
            lastClickedTabIdx = index;
            commitRename();
            return performTabSwitch(index, activeIdx, isTeam, teamState);
        }

        return true;
    }

    private boolean handleCloseTabClick(int index, String pageName, boolean isTeam, ClientWorkspaceState teamState) {
        commitRename();
        if (isTeam) {
            List<TeamWorkspacePage> teamPages = new ArrayList<>(teamState.getRemotePages());
            if (index < teamPages.size()) {
                TeamWorkspacePage tp = teamPages.get(index);
                screen.openDeleteTeamPageDialog(tp.getPageId(), tp.getTitle());
            }
            return true;
        }

        BoardManager bm = BoardManager.getInstance();
        List<BoardPage> openPages = bm.getOpenPages();
        if (index >= openPages.size()) return false;
        BoardPage targetPage = openPages.get(index);

        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            int actualPageIndex = bm.getPages().indexOf(targetPage);
            if (actualPageIndex >= 0) {
                screen.openDeletePageDialog(actualPageIndex, pageName);
            }
            return true;
        }

        bm.closeTab(targetPage.getId());
        BoardPage active = bm.getActivePage();
        if (active != null) {
            screen.setPanX(active.getPanX());
            screen.setPanY(active.getPanY());
            screen.setZoom(active.getZoom());
            BoardScreen.lastPanX = active.getPanX();
            BoardScreen.lastPanY = active.getPanY();
            BoardScreen.lastZoom = active.getZoom();
        }
        screen.rebuildWidgets();
        playClickSound();
        return true;
    }

    private boolean performTabSwitch(int index, int activeIdx, boolean isTeam, ClientWorkspaceState teamState) {
        if (TutorialManager.getInstance().isActive() && index != activeIdx) {
            if (isTeam) {
                List<TeamWorkspacePage> teamPages = new ArrayList<>(teamState.getRemotePages());
                if (index < teamPages.size()) {
                    screen.openTutorialExitDialogForTeamPage(teamPages.get(index).getPageId());
                }
            } else {
                screen.openTutorialExitDialog(index);
            }
            return true;
        }

        if (isTeam) {
            List<TeamWorkspacePage> teamPages = new ArrayList<>(teamState.getRemotePages());
            if (index < teamPages.size()) {
                String newPageId = teamPages.get(index).getPageId();
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
            return true;
        }

        BoardManager bm = BoardManager.getInstance();
        List<BoardPage> openPages = bm.getOpenPages();
        if (index < openPages.size()) {
            BoardPage targetPage = openPages.get(index);
            if (!targetPage.getId().equals(bm.getActivePage().getId())) {
                BoardPage cur = bm.getActivePage();
                if (cur != null) {
                    cur.setPanX(screen.getPanX());
                    cur.setPanY(screen.getPanY());
                    cur.setZoom(screen.getZoom());
                }
                bm.openPage(targetPage.getId());
                BoardPage next = bm.getActivePage();
                if (next != null) {
                    screen.setPanX(next.getPanX());
                    screen.setPanY(next.getPanY());
                    screen.setZoom(next.getZoom());
                }
                screen.rebuildWidgets();
                screen.markSummaryDirty();
                playClickSound();
            }
        }
        return true;
    }

    private boolean handleAddPageClick(boolean isTeam, int currentCount) {
        commitRename();
        if (isTeam) {
            ClientWorkspaceState teamState = ClientWorkspaceState.getInstance();
            String newTitle = "Page " + (currentCount + 1);
            String newPageId = "page_" + System.currentTimeMillis();
            com.gtceu.calcboard.network.NetworkHandler.sendToServer(
                new com.gtceu.calcboard.network.packet.c2s.C2SCommitWorkspacePacket(
                    teamState.getCurrentTeamId(), newPageId, newTitle, 0, "Created " + newTitle, new byte[0], 0, 0, 0
                )
            );
            playClickSound();
            return true;
        }

        BoardManager bm = BoardManager.getInstance();
        BoardPage cur = bm.getActivePage();
        if (cur != null) {
            cur.setPanX(screen.getPanX());
            cur.setPanY(screen.getPanY());
            cur.setZoom(screen.getZoom());
        }

        bm.addPage("Page " + (bm.getPages().size() + 1));
        BoardPage next = bm.getActivePage();
        if (next != null) {
            screen.setPanX(next.getPanX());
            screen.setPanY(next.getPanY());
            screen.setZoom(next.getZoom());
        }
        screen.rebuildWidgets();
        playClickSound();
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingTabBar) {
            isDraggingTabBar = false;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingTabBar && maxScrollX > 0) {
            double delta = dragStartX - mouseX;
            this.scrollX = Math.max(0, Math.min(maxScrollX, initialScrollX + delta));
            if (Math.abs(delta) > 3) {
                this.hasDragged = true;
            }
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int tabY = screen.getPageTabY();
        if (mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT + 2 && maxScrollX > 0) {
            commitRename();
            this.scrollX = Math.max(0, Math.min(maxScrollX, scrollX - delta * 30));
            return true;
        }
        return false;
    }

    public boolean isEditing() {
        return editingPageIndex >= 0 && renameBox != null;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isEditing()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitRename();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
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

    private void startRename(int index, String currentName, int x, int y, int width) {
        if (screen.getPageBrowserDrawer() != null) {
            screen.getPageBrowserDrawer().clearSearchFocus();
        }
        this.editingPageIndex = index;
        Font font = Minecraft.getInstance().font;
        this.renameBox = new EditBox(font, x, y, Math.max(width, 70), 16, Component.literal(""));
        this.renameBox.setValue(currentName);
        this.renameBox.setFocused(true);
    }

    private void commitRename() {
        if (editingPageIndex < 0 || renameBox == null) return;
        String newName = renameBox.getValue().trim();
        if (!newName.isEmpty()) {
            ClientWorkspaceState teamState = ClientWorkspaceState.getInstance();
            if (teamState.isTeamMode()) {
                List<TeamWorkspacePage> teamPages = new ArrayList<>(teamState.getRemotePages());
                if (editingPageIndex < teamPages.size()) {
                    TeamWorkspacePage tp = teamPages.get(editingPageIndex);
                    com.gtceu.calcboard.network.NetworkHandler.sendToServer(
                        new com.gtceu.calcboard.network.packet.c2s.C2SCommitWorkspacePacket(
                            teamState.getCurrentTeamId(), tp.getPageId(), newName, tp.getPageRevision(), "Renamed page to " + newName, tp.getCompressedGraphData(), 0, 0, 0
                        )
                    );
                }
            } else {
                BoardManager bm = BoardManager.getInstance();
                List<BoardPage> openPages = bm.getOpenPages();
                if (editingPageIndex < openPages.size()) {
                    BoardPage targetPage = openPages.get(editingPageIndex);
                    int actualIndex = bm.getPages().indexOf(targetPage);
                    if (actualIndex >= 0) {
                        bm.renamePage(actualIndex, newName);
                    }
                }
            }
        }
        editingPageIndex = -1;
        renameBox = null;
        screen.rebuildWidgets();
    }

    private int calculateTotalWidth(List<String> titles, Font font, int activeIdx, boolean isTeam) {
        int width = 0;
        for (int i = 0; i < titles.size(); i++) {
            String prefix = resolveTabPrefix(i, i == activeIdx, isTeam);
            width += computeTabWidth(font, titles.get(i), prefix, titles.size()) + 3;
        }
        return width;
    }

    private String resolveTabPrefix(int index, boolean isActive, boolean isTeam) {
        if (isTeam) return "";
        BoardManager bm = BoardManager.getInstance();
        List<BoardPage> openPages = bm.getOpenPages();
        if (index >= openPages.size()) return "";
        BoardPage page = openPages.get(index);
        boolean isPinned = page.isPinned();
        boolean isAe2 = com.gtceu.calcboard.integration.ae2.registry.PatternGraphRegistry.getInstance().isPageBound(page.getId());
        return getTabPrefix(isAe2, isPinned, isActive);
    }

    private int computeTabWidth(Font font, String pageName, String prefix, int totalTabCount) {
        int textW = font.width(prefix + pageName);
        return textW + (totalTabCount > 1 ? 26 : 16);
    }

    private String getTabPrefix(boolean isAe2, boolean isPinned, boolean isActive) {
        if (isAe2 && isPinned) {
            return "§b⚡§e★ ";
        } else if (isAe2) {
            return "§b⚡ ";
        } else if (isPinned) {
            return isActive ? "§a★ " : "§e★ ";
        }
        return "";
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }
}
