package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class PageBrowserDrawer {
    private final IBoardScreenContext screen;
    private boolean open = false;

    private EditBox searchBox;
    private double scrollY = 0;
    private double maxScrollY = 0;

    private boolean contextMenuOpen = false;
    private int contextMenuX = 0;
    private int contextMenuY = 0;
    private BoardPage contextPage = null;
    private int contextPageIndex = -1;
    private String contextFolder = null;

    public enum PromptMode { NONE, NEW_FOLDER, NEW_SUBFOLDER, RENAME_FOLDER, RENAME_PAGE }

    private PromptMode promptMode = PromptMode.NONE;
    private EditBox promptBox = null;
    private String promptTargetFolder = "";
    private BoardPage promptTargetPage = null;

    private final Set<String> collapsedFolders = new HashSet<>();
    private final Set<String> selectedPageIds = new LinkedHashSet<>();
    private final Set<String> selectedFolderPaths = new LinkedHashSet<>();
    private PageBrowserTreeModel.TreeItemRef lastClickedItem = null;

    private String draggingFolder = null;
    private BoardPage draggingPage = null;
    private int draggingPageIndex = -1;
    private double dragStartX = 0;
    private double dragStartY = 0;
    private boolean isDragging = false;

    public static final int DRAWER_X = LeftActivityBarWidget.BAR_WIDTH;
    public static final int DRAWER_WIDTH = 230;
    public static final int ITEM_HEIGHT = 20;

    public PageBrowserDrawer(IBoardScreenContext screen) {
        this.screen = screen;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!open) return false;
        int topY = screen.getHeaderBottomY();
        int drawerH = screen.getScreenHeight() - topY - 4;
        return mouseX >= DRAWER_X && mouseX <= DRAWER_X + DRAWER_WIDTH && mouseY >= topY && mouseY <= topY + drawerH;
    }

    public void setOpen(boolean open) {
        this.open = open;
        if (open) {
            initSearchBox();
            this.contextMenuOpen = false;
            this.promptMode = PromptMode.NONE;
            if (screen.getFavoritesDockWidget() != null) {
                screen.getFavoritesDockWidget().setExpanded(false);
                screen.getFavoritesDockWidget().closeFlyout();
            }
            com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onFolderBrowserOpened();
        } else {
            this.searchBox = null;
            this.contextMenuOpen = false;
            this.promptMode = PromptMode.NONE;
            this.promptBox = null;
            this.selectedFolderPaths.clear();
            this.selectedPageIds.clear();
            this.lastClickedItem = null;
            this.draggingFolder = null;
            this.draggingPage = null;
            this.isDragging = false;
        }
    }

    public void toggle() {
        setOpen(!this.open);
    }

    private void initSearchBox() {
        Font font = Minecraft.getInstance().font;
        int topY = screen.getHeaderBottomY() + 4;
        this.searchBox = new EditBox(font, DRAWER_X + 8, topY + 22, DRAWER_WIDTH - 16, 16, Component.translatable("gui.gtcalcboard.browser.search_hint"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setValue("");
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        PageBrowserDrawerRenderer.render(this, graphics, mouseX, mouseY, partialTicks);
    }

    public IBoardScreenContext getScreen() {
        return screen;
    }

    public EditBox getSearchBox() {
        return searchBox;
    }

    public EditBox getFocusedEditBox() {
        if (promptBox != null && promptBox.isFocused()) return promptBox;
        if (searchBox != null && searchBox.isFocused()) return searchBox;
        return null;
    }

    public double getScrollY() {
        return scrollY;
    }

    public void setScrollY(double scrollY) {
        this.scrollY = scrollY;
    }

    public double getMaxScrollY() {
        return maxScrollY;
    }

    public void setMaxScrollY(double maxScrollY) {
        this.maxScrollY = maxScrollY;
    }

    public Set<String> getCollapsedFolders() {
        return collapsedFolders;
    }

    public Set<String> getSelectedPageIds() {
        return selectedPageIds;
    }

    public Set<String> getSelectedFolderPaths() {
        return selectedFolderPaths;
    }

    public boolean isDragging() {
        return isDragging;
    }

    public String getDraggingFolder() {
        return draggingFolder;
    }

    public BoardPage getDraggingPage() {
        return draggingPage;
    }

    public boolean isContextMenuOpen() {
        return contextMenuOpen;
    }

    public int getContextMenuX() {
        return contextMenuX;
    }

    public int getContextMenuY() {
        return contextMenuY;
    }

    public PromptMode getPromptMode() {
        return promptMode;
    }

    public EditBox getPromptBox() {
        return promptBox;
    }

    record ContextMenuItem(String label, Runnable action) {}

    List<ContextMenuItem> buildContextMenuItems() {
        List<ContextMenuItem> items = new ArrayList<>();
        int totalSelected = selectedFolderPaths.size() + selectedPageIds.size();

        if (totalSelected > 1) {
            if (!selectedPageIds.isEmpty()) {
                List<BoardPage> selectedPages = getSelectedPagesList();
                boolean anyPinned = selectedPages.stream().anyMatch(BoardPage::isPinned);
                String pinKey = anyPinned ? "gui.gtcalcboard.browser.unpin_multiple_pages" : "gui.gtcalcboard.browser.pin_multiple_pages";
                items.add(new ContextMenuItem("§e★ " + Component.translatable(pinKey, String.valueOf(selectedPages.size())).getString(), () -> {
                    boolean newPinState = !anyPinned;
                    for (BoardPage p : selectedPages) {
                        p.setPinned(newPinState);
                    }
                    contextMenuOpen = false;
                }));
            }

            items.add(new ContextMenuItem("§c✕ " + Component.translatable("gui.gtcalcboard.browser.delete_multiple_pages", String.valueOf(totalSelected)).getString(), () -> {
                for (String f : new ArrayList<>(selectedFolderPaths)) {
                    BoardManager.getInstance().deleteFolder(f);
                }
                if (!selectedPageIds.isEmpty()) {
                    screen.openDeleteMultiplePagesDialog(new ArrayList<>(selectedPageIds));
                }
                selectedFolderPaths.clear();
                contextMenuOpen = false;
                setOpen(false);
            }));
        } else if (contextPage != null) {
            String pinKey = contextPage.isPinned() ? "gui.gtcalcboard.browser.unpin" : "gui.gtcalcboard.browser.pin";
            items.add(new ContextMenuItem("§e★ " + Component.translatable(pinKey).getString(), () -> {
                contextPage.setPinned(!contextPage.isPinned());
                contextMenuOpen = false;
            }));
            items.add(new ContextMenuItem("§f✎ " + Component.translatable("gui.gtcalcboard.browser.rename_page").getString(), () -> {
                promptMode = PromptMode.RENAME_PAGE;
                promptTargetPage = contextPage;
                Font font = Minecraft.getInstance().font;
                promptBox = new EditBox(font, 0, 0, 160, 16, Component.literal(""));
                promptBox.setValue(contextPage.getName());
                promptBox.setFocused(true);
                contextMenuOpen = false;
            }));
            items.add(new ContextMenuItem("§b⚡ " + Component.translatable("gui.gtcalcboard.browser.clone_recipe").getString(), () -> {
                screen.openTemplateCloneDialog(contextPage);
                contextMenuOpen = false;
                setOpen(false);
            }));
            items.add(new ContextMenuItem("§c✕ " + Component.translatable("gui.gtcalcboard.browser.delete_page").getString(), () -> {
                screen.openDeletePageDialog(contextPageIndex, contextPage.getName());
                contextMenuOpen = false;
                setOpen(false);
            }));
        } else if (contextFolder != null && !contextFolder.isEmpty()) {
            items.add(new ContextMenuItem("§e≡ " + Component.translatable("gui.gtcalcboard.browser.new_subfolder").getString(), () -> {
                promptMode = PromptMode.NEW_SUBFOLDER;
                promptTargetFolder = contextFolder;
                Font font = Minecraft.getInstance().font;
                promptBox = new EditBox(font, 0, 0, 160, 16, Component.literal(""));
                promptBox.setValue("New Subfolder");
                promptBox.setFocused(true);
                contextMenuOpen = false;
            }));
            items.add(new ContextMenuItem("§6» " + Component.translatable("gui.gtcalcboard.browser.export_folder").getString(), () -> {
                screen.openExportFolderDialog(contextFolder);
                contextMenuOpen = false;
                setOpen(false);
            }));
            items.add(new ContextMenuItem("§f✎ " + Component.translatable("gui.gtcalcboard.browser.rename_folder").getString(), () -> {
                promptMode = PromptMode.RENAME_FOLDER;
                promptTargetFolder = contextFolder;
                Font font = Minecraft.getInstance().font;
                promptBox = new EditBox(font, 0, 0, 160, 16, Component.literal(""));
                promptBox.setValue(contextFolder);
                promptBox.setFocused(true);
                contextMenuOpen = false;
            }));
            items.add(new ContextMenuItem("§c✕ " + Component.translatable("gui.gtcalcboard.browser.delete_folder").getString(), () -> {
                BoardManager.getInstance().deleteFolder(contextFolder);
                selectedFolderPaths.remove(contextFolder);
                contextMenuOpen = false;
            }));
        }
        return items;
    }

    private List<BoardPage> getSelectedPagesList() {
        List<BoardPage> list = new ArrayList<>();
        for (BoardPage p : BoardManager.getInstance().getPages()) {
            if (selectedPageIds.contains(p.getId())) {
                list.add(p);
            }
        }
        return list;
    }

    public void clearSearchFocus() {
        if (searchBox != null) {
            searchBox.setFocused(false);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (mouseY < screen.getHeaderBottomY()) {
            return false;
        }
        if (mouseX < DRAWER_X) {
            return false;
        }
        if (mouseX > DRAWER_X + DRAWER_WIDTH) {
            setOpen(false);
            return true;
        }
        if (handlePromptClicks(mouseX, mouseY, button)) {
            return true;
        }
        if (promptMode != PromptMode.NONE) {
            return true;
        }
        if (handleContextMenuClicks(mouseX, mouseY, button)) {
            return true;
        }
        if (handleHeaderButtonClicks(mouseX, mouseY, button)) {
            return true;
        }
        if (searchBox != null && searchBox.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        handleTreeClicks(mouseX, mouseY, button);
        return true;
    }

    private boolean handlePromptClicks(double mouseX, double mouseY, int button) {
        if (promptMode == PromptMode.NONE) return false;

        int pw = 180;
        int ph = 70;
        int px = DRAWER_X + (DRAWER_WIDTH - pw) / 2;
        int py = (screen.getScreenHeight() - ph) / 2;
        int btnY = py + 46;

        if (mouseX >= px + 10 && mouseX <= px + 80 && mouseY >= btnY && mouseY <= btnY + 16) {
            confirmPrompt();
            return true;
        }
        if (mouseX >= px + 95 && mouseX <= px + 165 && mouseY >= btnY && mouseY <= btnY + 16) {
            promptMode = PromptMode.NONE;
            promptBox = null;
            return true;
        }
        if (promptBox != null) {
            promptBox.mouseClicked(mouseX, mouseY, button);
        }
        return true;
    }

    private boolean handleContextMenuClicks(double mouseX, double mouseY, int button) {
        if (!contextMenuOpen) return false;

        List<ContextMenuItem> items = buildContextMenuItems();
        int menuW = 140;
        int menuH = items.size() * 18 + 6;
        int mx = Math.max(DRAWER_X + 4, Math.min(contextMenuX, DRAWER_X + DRAWER_WIDTH - menuW - 4));
        int my = Math.min(contextMenuY, screen.getScreenHeight() - menuH - 10);

        if (mouseX >= mx && mouseX <= mx + menuW && mouseY >= my && mouseY <= my + menuH) {
            int relY = (int) (mouseY - my - 3);
            int idx = relY / 18;
            if (idx >= 0 && idx < items.size()) {
                items.get(idx).action.run();
                return true;
            }
        }
        contextMenuOpen = false;
        return true;
    }

    private boolean handleHeaderButtonClicks(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int topY = screen.getHeaderBottomY() + 2;
        int btnY = topY + 6;
        int closeX = DRAWER_X + DRAWER_WIDTH - 20;
        int importX = closeX - 22;
        int addPageX = importX - 22;
        int addFolderX = addPageX - 24;

        if (mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= btnY && mouseY <= btnY + 14) {
            setOpen(false);
            return true;
        }
        if (mouseX >= importX && mouseX <= importX + 20 && mouseY >= btnY && mouseY <= btnY + 14) {
            screen.openImportFolderDialog();
            setOpen(false);
            playClickSound();
            return true;
        }
        if (mouseX >= addPageX && mouseX <= addPageX + 20 && mouseY >= btnY && mouseY <= btnY + 14) {
            BoardManager.getInstance().addPage("Page " + (BoardManager.getInstance().getPages().size() + 1));
            screen.rebuildBoardWidgets();
            playClickSound();
            return true;
        }
        if (mouseX >= addFolderX && mouseX <= addFolderX + 20 && mouseY >= btnY && mouseY <= btnY + 14) {
            promptMode = PromptMode.NEW_FOLDER;
            Font font = Minecraft.getInstance().font;
            promptBox = new EditBox(font, 0, 0, 160, 16, Component.literal(""));
            promptBox.setValue("New Folder");
            promptBox.setFocused(true);
            return true;
        }
        return false;
    }

    private boolean handleTreeClicks(double mouseX, double mouseY, int button) {
        int topY = screen.getHeaderBottomY() + 2;
        int listX = DRAWER_X + 6;
        int listY = topY + 44;
        int listW = DRAWER_WIDTH - 12;
        int listH = screen.getScreenHeight() - topY - 54;

        if (mouseX < listX || mouseX > listX + listW || mouseY < listY || mouseY > listY + listH) {
            return false;
        }

        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
        PageBrowserTreeModel.FolderTreeNode root = PageBrowserTreeModel.buildFolderTree(query);
        int curY = listY + 4 - (int) scrollY;

        return handleTreeNodeClickRecursive(root, listX, curY, mouseX, mouseY, button, query);
    }

    private boolean handleTreeNodeClickRecursive(PageBrowserTreeModel.FolderTreeNode node, int listX, int curY, double mouseX, double mouseY, int button, String query) {
        if (!node.folderPath.isEmpty()) {
            if (mouseY >= curY && mouseY <= curY + 16) {
                return handleFolderClick(node, listX, mouseX, mouseY, button);
            }
            curY += 18;
            if (collapsedFolders.contains(node.folderPath) && query.isEmpty()) {
                return false;
            }
        }

        for (PageBrowserTreeModel.FolderTreeNode sub : node.subFolders.values()) {
            if (handleTreeNodeClickRecursive(sub, listX, curY, mouseX, mouseY, button, query)) {
                return true;
            }
            curY = PageBrowserTreeModel.calculateNodeHeight(sub, curY, query, collapsedFolders, ITEM_HEIGHT);
        }

        for (PageBrowserTreeModel.IndexedPage ip : node.directPages) {
            if (mouseY >= curY && mouseY <= curY + ITEM_HEIGHT) {
                return handlePageClick(ip, node.depth, listX, mouseX, mouseY, button);
            }
            curY += ITEM_HEIGHT + 2;
        }

        return false;
    }

    private boolean handleFolderClick(PageBrowserTreeModel.FolderTreeNode node, int listX, double mouseX, double mouseY, int button) {
        String folder = node.folderPath;
        int indent = (node.depth - 1) * 10 + 6;
        boolean arrowClicked = (mouseX >= listX + indent && mouseX <= listX + indent + 14);

        if (button == 0) {
            if (arrowClicked) {
                if (collapsedFolders.contains(folder)) {
                    collapsedFolders.remove(folder);
                } else {
                    collapsedFolders.add(folder);
                }
                playClickSound();
                return true;
            }

            if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                if (selectedFolderPaths.contains(folder)) {
                    selectedFolderPaths.remove(folder);
                } else {
                    selectedFolderPaths.add(folder);
                }
                lastClickedItem = new PageBrowserTreeModel.TreeItemRef(true, folder);
                playClickSound();
                return true;
            }

            if (net.minecraft.client.gui.screens.Screen.hasShiftDown() && lastClickedItem != null) {
                String query = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
                PageBrowserTreeModel.FolderTreeNode root = PageBrowserTreeModel.buildFolderTree(query);
                PageBrowserTreeModel.applyRangeSelection(root, lastClickedItem, new PageBrowserTreeModel.TreeItemRef(true, folder), query, collapsedFolders, selectedFolderPaths, selectedPageIds);
                playClickSound();
                return true;
            }

            if (!selectedFolderPaths.contains(folder)) {
                selectedPageIds.clear();
                selectedFolderPaths.clear();
                selectedFolderPaths.add(folder);
            }
            lastClickedItem = new PageBrowserTreeModel.TreeItemRef(true, folder);

            draggingFolder = folder;
            draggingPage = null;
            draggingPageIndex = -1;
            dragStartX = mouseX;
            dragStartY = mouseY;
            isDragging = false;
            return true;
        }

        if (button == 1) {
            if (!selectedFolderPaths.contains(folder)) {
                selectedPageIds.clear();
                selectedFolderPaths.clear();
                selectedFolderPaths.add(folder);
                lastClickedItem = new PageBrowserTreeModel.TreeItemRef(true, folder);
            }
            contextMenuOpen = true;
            contextMenuX = (int) mouseX;
            contextMenuY = (int) mouseY;
            contextPage = null;
            contextPageIndex = -1;
            contextFolder = folder;
            return true;
        }
        return false;
    }

    private boolean handlePageClick(PageBrowserTreeModel.IndexedPage ip, int depth, int listX, double mouseX, double mouseY, int button) {
        int indent = depth * 10 + 6;
        if (mouseX >= listX + indent && mouseX <= listX + indent + 10 && button == 0) {
            return handlePinClick(ip);
        }
        if (button == 0) {
            return handlePageLeftClick(ip, mouseX, mouseY);
        }
        if (button == 1) {
            return handlePageRightClick(ip, mouseX, mouseY);
        }
        return false;
    }

    private boolean handlePinClick(PageBrowserTreeModel.IndexedPage ip) {
        if (selectedPageIds.contains(ip.page().getId()) && selectedPageIds.size() > 1) {
            List<BoardPage> selectedPages = getSelectedPagesList();
            boolean anyPinned = selectedPages.stream().anyMatch(BoardPage::isPinned);
            boolean newPin = !anyPinned;
            for (BoardPage p : selectedPages) {
                p.setPinned(newPin);
            }
        } else {
            ip.page().setPinned(!ip.page().isPinned());
        }
        playClickSound();
        return true;
    }

    private boolean handlePageLeftClick(PageBrowserTreeModel.IndexedPage ip, double mouseX, double mouseY) {
        String pageId = ip.page().getId();
        if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
            if (selectedPageIds.contains(pageId)) {
                selectedPageIds.remove(pageId);
            } else {
                selectedPageIds.add(pageId);
            }
            lastClickedItem = new PageBrowserTreeModel.TreeItemRef(false, pageId);
            playClickSound();
            return true;
        }

        if (net.minecraft.client.gui.screens.Screen.hasShiftDown() && lastClickedItem != null) {
            String query = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
            PageBrowserTreeModel.FolderTreeNode root = PageBrowserTreeModel.buildFolderTree(query);
            PageBrowserTreeModel.applyRangeSelection(root, lastClickedItem, new PageBrowserTreeModel.TreeItemRef(false, pageId), query, collapsedFolders, selectedFolderPaths, selectedPageIds);
            playClickSound();
            return true;
        }

        if (!selectedPageIds.contains(pageId)) {
            selectedFolderPaths.clear();
            selectedPageIds.clear();
            selectedPageIds.add(pageId);
        }
        lastClickedItem = new PageBrowserTreeModel.TreeItemRef(false, pageId);

        BoardManager.getInstance().openPage(ip.page().getId());
        BoardPage active = BoardManager.getInstance().getActivePage();
        if (active != null) {
            screen.setPanX(active.getPanX());
            screen.setPanY(active.getPanY());
            screen.setZoom(active.getZoom());
        }
        screen.rebuildBoardWidgets();
        playClickSound();

        draggingPage = ip.page();
        draggingPageIndex = ip.index();
        draggingFolder = null;
        dragStartX = mouseX;
        dragStartY = mouseY;
        isDragging = false;
        return true;
    }

    private boolean handlePageRightClick(PageBrowserTreeModel.IndexedPage ip, double mouseX, double mouseY) {
        String pageId = ip.page().getId();
        if (!selectedPageIds.contains(pageId)) {
            selectedFolderPaths.clear();
            selectedPageIds.clear();
            selectedPageIds.add(pageId);
            lastClickedItem = new PageBrowserTreeModel.TreeItemRef(false, pageId);
        }
        contextMenuOpen = true;
        contextMenuX = (int) mouseX;
        contextMenuY = (int) mouseY;
        contextPage = ip.page();
        contextPageIndex = ip.index();
        contextFolder = null;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!open) return false;
        if ((draggingPage != null || draggingFolder != null) && (Math.abs(mouseX - dragStartX) > 3 || Math.abs(mouseY - dragStartY) > 3)) {
            isDragging = true;
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (isDragging && (draggingPage != null || draggingFolder != null)) {
            String targetFolder = resolveFolderUnderMouse(mouseY);
            if (targetFolder == null) targetFolder = "";

            BoardManager bm = BoardManager.getInstance();
            int totalSelected = selectedFolderPaths.size() + selectedPageIds.size();

            if (totalSelected > 1 && (
                    (draggingFolder != null && selectedFolderPaths.contains(draggingFolder)) ||
                    (draggingPage != null && selectedPageIds.contains(draggingPage.getId())))) {
                for (String f : new ArrayList<>(selectedFolderPaths)) {
                    bm.moveFolder(f, targetFolder);
                }
                for (BoardPage p : getSelectedPagesList()) {
                    p.setFolderPath(targetFolder);
                }
                playClickSound();
            } else {
                if (draggingFolder != null) {
                    bm.moveFolder(draggingFolder, targetFolder);
                    playClickSound();
                } else if (draggingPage != null) {
                    draggingPage.setFolderPath(targetFolder);
                    playClickSound();
                }
            }
        } else if (!isDragging && (draggingPage != null || draggingFolder != null) && !net.minecraft.client.gui.screens.Screen.hasControlDown() && !net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            int totalSelected = selectedFolderPaths.size() + selectedPageIds.size();
            if (totalSelected > 1) {
                selectedFolderPaths.clear();
                selectedPageIds.clear();
                if (draggingFolder != null) {
                    selectedFolderPaths.add(draggingFolder);
                } else if (draggingPage != null) {
                    selectedPageIds.add(draggingPage.getId());
                }
            }
        }
        boolean wasInteracting = (draggingPage != null || draggingFolder != null);
        draggingFolder = null;
        draggingPage = null;
        draggingPageIndex = -1;
        isDragging = false;
        return wasInteracting || isMouseOver(mouseX, mouseY);
    }

    private String resolveFolderUnderMouse(double mouseY) {
        int topY = screen.getHeaderBottomY() + 2;
        int listY = topY + 44;
        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
        PageBrowserTreeModel.FolderTreeNode root = PageBrowserTreeModel.buildFolderTree(query);
        return PageBrowserTreeModel.resolveFolderUnderMouse(root, listY, scrollY, mouseY, query, collapsedFolders, ITEM_HEIGHT);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!open || mouseX < DRAWER_X || mouseX > DRAWER_X + DRAWER_WIDTH) return false;
        int topY = screen.getHeaderBottomY();
        if (mouseY < topY || mouseY > screen.getScreenHeight() - 4) return false;
        scrollY = Math.max(0, Math.min(maxScrollY, scrollY - delta * 20.0));
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;

        if (keyCode == GLFW.GLFW_KEY_TAB && modifiers == 0) {
            if (promptMode != PromptMode.NONE) {
                promptMode = PromptMode.NONE;
                promptBox = null;
            }
            if (contextMenuOpen) {
                contextMenuOpen = false;
            }
            setOpen(false);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (promptMode != PromptMode.NONE) {
                promptMode = PromptMode.NONE;
                promptBox = null;
                return true;
            }
            if (contextMenuOpen) {
                contextMenuOpen = false;
                return true;
            }
            setOpen(false);
            return true;
        }

        if (promptMode != PromptMode.NONE && promptBox != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmPrompt();
                return true;
            }
            promptBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }

        if (searchBox != null && searchBox.isFocused()) {
            searchBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }

        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!open) return false;
        if (promptMode != PromptMode.NONE && promptBox != null) {
            return promptBox.charTyped(codePoint, modifiers);
        }
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return false;
    }

    private void confirmPrompt() {
        if (promptBox == null) return;
        String val = promptBox.getValue().trim();
        if (!val.isEmpty()) {
            switch (promptMode) {
                case NEW_FOLDER -> {
                    BoardManager.getInstance().notifyFolderCreated(val);
                    BoardManager.getInstance().addPage("Page " + (BoardManager.getInstance().getPages().size() + 1), val);
                    screen.rebuildBoardWidgets();
                }
                case NEW_SUBFOLDER -> {
                    String subPath = promptTargetFolder.isEmpty() ? val : (promptTargetFolder + "/" + val);
                    BoardManager.getInstance().notifyFolderCreated(subPath);
                    BoardManager.getInstance().addPage("Page " + (BoardManager.getInstance().getPages().size() + 1), subPath);
                    screen.rebuildBoardWidgets();
                }
                case RENAME_FOLDER -> BoardManager.getInstance().renameFolder(promptTargetFolder, val);
                case RENAME_PAGE -> {
                    if (promptTargetPage != null) {
                        promptTargetPage.setName(val);
                    }
                }
            }
            playClickSound();
        }
        promptMode = PromptMode.NONE;
        promptBox = null;
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }

    public static class FolderTreeNode extends PageBrowserTreeModel.FolderTreeNode {
        public FolderTreeNode(String folderPath, String simpleName, int depth) {
            super(folderPath, simpleName, depth);
        }
    }
}
