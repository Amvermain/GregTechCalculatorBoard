package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.client.gui.BoardScreen;
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
    private final BoardScreen screen;
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

    private enum PromptMode { NONE, NEW_FOLDER, NEW_SUBFOLDER, RENAME_FOLDER, RENAME_PAGE }
    public record TreeItemRef(boolean isFolder, String idOrPath) {}

    private PromptMode promptMode = PromptMode.NONE;
    private EditBox promptBox = null;
    private String promptTargetFolder = "";
    private BoardPage promptTargetPage = null;

    private final Set<String> collapsedFolders = new HashSet<>();
    private final Set<String> selectedPageIds = new LinkedHashSet<>();
    private final Set<String> selectedFolderPaths = new LinkedHashSet<>();
    private TreeItemRef lastClickedItem = null;

    private String draggingFolder = null;
    private BoardPage draggingPage = null;
    private int draggingPageIndex = -1;
    private double dragStartX = 0;
    private double dragStartY = 0;
    private boolean isDragging = false;

    public static final int DRAWER_WIDTH = 230;
    private static final int ITEM_HEIGHT = 20;

    public PageBrowserDrawer(BoardScreen screen) {
        this.screen = screen;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
        if (open) {
            initSearchBox();
            this.contextMenuOpen = false;
            this.promptMode = PromptMode.NONE;
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
        this.searchBox = new EditBox(font, 12, topY + 22, DRAWER_WIDTH - 24, 16, Component.translatable("gui.gtcalcboard.browser.search_hint"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setValue("");
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!open) return;

        Font font = Minecraft.getInstance().font;
        int height = screen.height;
        int topY = screen.getHeaderBottomY() + 2;
        int drawerH = height - topY - 4;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400.0f);
        RenderSystem.disableDepthTest();

        renderBackground(graphics, topY, drawerH);
        renderHeader(graphics, font, topY, mouseX, mouseY);
        renderSearchBox(graphics, mouseX, mouseY, partialTicks, topY);
        renderTreeView(graphics, font, topY, drawerH, mouseX, mouseY);
        renderDragGhost(graphics, font, mouseX, mouseY);
        renderContextMenuOverlay(graphics, font, mouseX, mouseY);
        renderPromptModalOverlay(graphics, font, mouseX, mouseY, partialTicks);

        graphics.pose().popPose();
    }

    private void renderBackground(GuiGraphics graphics, int topY, int drawerH) {
        graphics.fill(4, topY, DRAWER_WIDTH, topY + drawerH, 0xF5141822);
        graphics.renderOutline(4, topY, DRAWER_WIDTH - 4, drawerH, 0xFF353C4D);
        graphics.renderOutline(5, topY + 1, DRAWER_WIDTH - 6, drawerH - 2, 0xFF0D1117);
    }

    private void renderHeader(GuiGraphics graphics, Font font, int topY, int mouseX, int mouseY) {
        graphics.drawString(font, "§6📁 " + Component.translatable("gui.gtcalcboard.browser.title").getString(), 12, topY + 8, 0xFFFFFFFF, false);

        int btnY = topY + 6;
        int closeX = DRAWER_WIDTH - 20;
        int importX = closeX - 22;
        int addPageX = importX - 22;
        int addFolderX = addPageX - 24;

        boolean addFolderHover = mouseX >= addFolderX && mouseX <= addFolderX + 20 && mouseY >= btnY && mouseY <= btnY + 14;
        graphics.fill(addFolderX, btnY, addFolderX + 20, btnY + 14, addFolderHover ? 0xFF2A364C : 0xFF1C2230);
        graphics.renderOutline(addFolderX, btnY, 20, 14, addFolderHover ? 0xFF5588DD : 0xFF353C4D);
        graphics.drawString(font, "§b+📁", addFolderX + 2, btnY + 3, 0xFFFFFFFF, false);

        boolean addPageHover = mouseX >= addPageX && mouseX <= addPageX + 20 && mouseY >= btnY && mouseY <= btnY + 14;
        graphics.fill(addPageX, btnY, addPageX + 20, btnY + 14, addPageHover ? 0xFF2A4C36 : 0xFF1C3024);
        graphics.renderOutline(addPageX, btnY, 20, 14, addPageHover ? 0xFF55FF88 : 0xFF356B48);
        graphics.drawString(font, "§a+📄", addPageX + 2, btnY + 3, 0xFFFFFFFF, false);

        boolean importHover = mouseX >= importX && mouseX <= importX + 20 && mouseY >= btnY && mouseY <= btnY + 14;
        graphics.fill(importX, btnY, importX + 20, btnY + 14, importHover ? 0xFF3D3A2A : 0xFF26241C);
        graphics.renderOutline(importX, btnY, 20, 14, importHover ? 0xFFFFDD55 : 0xFF66582B);
        graphics.drawString(font, "§e📥", importX + 3, btnY + 3, 0xFFFFFFFF, false);

        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= btnY && mouseY <= btnY + 14;
        graphics.drawString(font, "§c✕", closeX + 2, btnY + 3, closeHover ? 0xFFFF6666 : 0xFFAAAAAA, false);
    }

    private void renderSearchBox(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, int topY) {
        if (searchBox == null) return;
        searchBox.setX(12);
        searchBox.setY(topY + 24);
        searchBox.render(graphics, mouseX, mouseY, partialTicks);
    }

    private void renderTreeView(GuiGraphics graphics, Font font, int topY, int drawerH, int mouseX, int mouseY) {
        int listX = 8;
        int listY = topY + 44;
        int listW = DRAWER_WIDTH - 14;
        int listH = drawerH - 50;

        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF0D1017);
        graphics.renderOutline(listX, listY, listW, listH, 0xFF222733);
        graphics.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);

        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
        FolderTreeNode root = buildFolderTree(query);
        int activeIdx = BoardManager.getInstance().getActivePageIndex();

        int curY = listY + 4 - (int) scrollY;
        curY = renderTreeNodeRecursive(graphics, font, root, activeIdx, listX, listW, curY, mouseX, mouseY, query);

        int totalContentH = (curY + (int) scrollY) - listY;
        this.maxScrollY = Math.max(0, totalContentH - listH);
        graphics.disableScissor();
    }

    private int renderTreeNodeRecursive(GuiGraphics graphics, Font font, FolderTreeNode node, int activeIdx, int listX, int listW, int curY, int mouseX, int mouseY, String query) {
        if (!node.folderPath.isEmpty()) {
            curY = renderFolderRow(graphics, font, node, listX, listW, curY, mouseX, mouseY);
            if (collapsedFolders.contains(node.folderPath) && query.isEmpty()) {
                return curY;
            }
        }

        for (FolderTreeNode sub : node.subFolders.values()) {
            curY = renderTreeNodeRecursive(graphics, font, sub, activeIdx, listX, listW, curY, mouseX, mouseY, query);
        }

        for (IndexedPage ip : node.directPages) {
            curY = renderPageRow(graphics, font, ip, node.depth, activeIdx, listX, listW, curY, mouseX, mouseY);
        }

        return curY;
    }

    private int renderFolderRow(GuiGraphics graphics, Font font, FolderTreeNode node, int listX, int listW, int curY, int mouseX, int mouseY) {
        boolean isCollapsed = collapsedFolders.contains(node.folderPath);
        boolean isSelected = selectedFolderPaths.contains(node.folderPath);
        boolean folderHover = mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= curY && mouseY <= curY + 16;

        int bg = isSelected ? 0x662563EB : (folderHover ? 0xFF1C2433 : 0xFF141924);
        int border = isSelected ? 0xFF60A5FA : (folderHover ? 0xFF3D4B66 : 0xFF222B3D);

        graphics.fill(listX + 2, curY, listX + listW - 2, curY + 16, bg);
        graphics.renderOutline(listX + 2, curY, listW - 4, 16, border);

        int indent = (node.depth - 1) * 10 + 6;
        String toggleIcon = isCollapsed ? "▶ " : "▼ ";
        String nameColor = isSelected ? "§b" : "§e";
        graphics.drawString(font, nameColor + toggleIcon + "📁 " + node.simpleName + " §8(" + node.getTotalPageCount() + ")", listX + indent, curY + 4, 0xFFFFFFFF, false);

        return curY + 18;
    }

    private int renderPageRow(GuiGraphics graphics, Font font, IndexedPage ip, int depth, int activeIdx, int listX, int listW, int curY, int mouseX, int mouseY) {
        int pageIdx = ip.index;
        BoardPage page = ip.page;
        boolean isActive = (pageIdx == activeIdx);
        boolean isSelected = selectedPageIds.contains(page.getId());
        int indent = depth * 10 + 6;

        boolean pageHover = mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= curY && mouseY <= curY + ITEM_HEIGHT;
        int bg = isSelected ? 0x662563EB : (isActive ? 0xFF1C3D26 : (pageHover ? 0xFF222B3D : 0x00000000));
        int border = isSelected ? 0xFF60A5FA : (isActive ? 0xFF55FF88 : (pageHover ? 0xFF354460 : 0x00000000));

        if (bg != 0) {
            graphics.fill(listX + 2, curY, listX + listW - 2, curY + ITEM_HEIGHT, bg);
        }
        if (border != 0) {
            graphics.renderOutline(listX + 2, curY, listW - 4, ITEM_HEIGHT, border);
        }

        boolean pinHover = mouseX >= listX + indent && mouseX <= listX + indent + 10 && mouseY >= curY + 3 && mouseY <= curY + 15;
        String pinStr = page.isPinned() ? "§e📌" : (pinHover ? "§7📌" : "§8•");
        graphics.drawString(font, pinStr, listX + indent, curY + 5, 0xFFFFFFFF, false);

        ItemStack icon = page.getEffectiveRepresentativeIcon();
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, listX + indent + 12, curY + 2);
        } else {
            graphics.drawString(font, "§7📄", listX + indent + 12, curY + 5, 0xFFFFFFFF, false);
        }

        String nameColor = isSelected ? "§b" : (isActive ? "§a" : "§f");
        int nameX = listX + indent + 30;
        int maxNameW = listW - (indent + 34);
        String trimmedName = font.plainSubstrByWidth(page.getName(), maxNameW);
        graphics.drawString(font, nameColor + trimmedName, nameX, curY + 6, 0xFFFFFFFF, false);

        return curY + ITEM_HEIGHT + 2;
    }

    private void renderDragGhost(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!isDragging || (draggingPage == null && draggingFolder == null)) return;
        String ghostText;
        int totalSelectedCount = selectedFolderPaths.size() + selectedPageIds.size();
        if (draggingFolder != null) {
            String simpleName = draggingFolder.contains("/") ? draggingFolder.substring(draggingFolder.lastIndexOf('/') + 1) : draggingFolder;
            if (totalSelectedCount > 1 && selectedFolderPaths.contains(draggingFolder)) {
                ghostText = "§b📁 " + Component.translatable("gui.gtcalcboard.browser.drag_multiple_ghost",
                        simpleName,
                        String.valueOf(totalSelectedCount - 1),
                        String.valueOf(totalSelectedCount)).getString();
            } else {
                ghostText = "§e📁 " + simpleName;
            }
        } else {
            if (totalSelectedCount > 1 && selectedPageIds.contains(draggingPage.getId())) {
                ghostText = "§b📄 " + Component.translatable("gui.gtcalcboard.browser.drag_multiple_ghost",
                        draggingPage.getName(),
                        String.valueOf(totalSelectedCount - 1),
                        String.valueOf(totalSelectedCount)).getString();
            } else {
                ghostText = "§a📄 " + draggingPage.getName();
            }
        }
        int gw = font.width(ghostText) + 16;
        graphics.fill(mouseX + 4, mouseY + 4, mouseX + 4 + gw, mouseY + 22, 0xDD1C2C44);
        graphics.renderOutline(mouseX + 4, mouseY + 4, gw, 18, 0xFF5588DD);
        graphics.drawString(font, ghostText, mouseX + 8, mouseY + 9, 0xFFFFFFFF, false);
    }

    private void renderContextMenuOverlay(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!contextMenuOpen) return;
        List<ContextMenuItem> items = buildContextMenuItems();
        int menuW = 140;
        int menuH = items.size() * 18 + 6;

        int mx = Math.min(contextMenuX, DRAWER_WIDTH - menuW);
        int my = Math.min(contextMenuY, screen.height - menuH - 10);

        graphics.fill(mx, my, mx + menuW, my + menuH, 0xF5181C26);
        graphics.renderOutline(mx, my, menuW, menuH, 0xFF3D4B66);

        for (int i = 0; i < items.size(); i++) {
            ContextMenuItem it = items.get(i);
            int iy = my + 3 + i * 18;
            boolean hov = mouseX >= mx + 2 && mouseX <= mx + menuW - 2 && mouseY >= iy && mouseY <= iy + 16;
            if (hov) {
                graphics.fill(mx + 2, iy, mx + menuW - 2, iy + 16, 0xFF2A364C);
            }
            graphics.drawString(font, it.label, mx + 8, iy + 4, hov ? 0xFFFFFFFF : 0xFFCCCCCC, false);
        }
    }

    private void renderPromptModalOverlay(GuiGraphics graphics, Font font, int mouseX, int mouseY, float partialTicks) {
        if (promptMode == PromptMode.NONE) return;

        int topY = screen.getHeaderBottomY() + 2;
        int drawerH = screen.height - topY - 4;
        graphics.fill(4, topY, DRAWER_WIDTH, topY + drawerH, 0xAA000000);

        int pw = 180;
        int ph = 70;
        int px = (DRAWER_WIDTH - pw) / 2;
        int py = (screen.height - ph) / 2;

        graphics.fill(px, py, px + pw, py + ph, 0xF5161A24);
        graphics.renderOutline(px, py, pw, ph, 0xFF5588DD);

        String title = switch (promptMode) {
            case NEW_FOLDER -> "gui.gtcalcboard.browser.prompt_new_folder";
            case NEW_SUBFOLDER -> "gui.gtcalcboard.browser.prompt_new_subfolder";
            case RENAME_FOLDER -> "gui.gtcalcboard.browser.prompt_rename_folder";
            case RENAME_PAGE -> "gui.gtcalcboard.browser.prompt_rename_page";
            default -> "";
        };
        graphics.drawString(font, Component.translatable(title).getString(), px + 10, py + 8, 0xFFFFFFFF, false);

        if (promptBox != null) {
            promptBox.setX(px + 10);
            promptBox.setY(py + 24);
            promptBox.render(graphics, mouseX, mouseY, partialTicks);
        }

        int btnY = py + 46;
        boolean okHov = mouseX >= px + 10 && mouseX <= px + 80 && mouseY >= btnY && mouseY <= btnY + 16;
        graphics.fill(px + 10, btnY, px + 80, btnY + 16, okHov ? 0xFF2A5A38 : 0xFF1C3D26);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.dialog.btn_ok").getString(), px + 45, btnY + 4, 0xFFFFFFFF);

        boolean cancelHov = mouseX >= px + 95 && mouseX <= px + 165 && mouseY >= btnY && mouseY <= btnY + 16;
        graphics.fill(px + 95, btnY, px + 165, btnY + 16, cancelHov ? 0xFF3D2A2A : 0xFF261D1D);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.dialog.btn_cancel").getString(), px + 130, btnY + 4, 0xFFFFFFFF);
    }

    private record ContextMenuItem(String label, Runnable action) {}

    private List<ContextMenuItem> buildContextMenuItems() {
        List<ContextMenuItem> items = new ArrayList<>();
        int totalSelected = selectedFolderPaths.size() + selectedPageIds.size();

        if (totalSelected > 1) {
            if (!selectedPageIds.isEmpty()) {
                List<BoardPage> selectedPages = getSelectedPagesList();
                boolean anyPinned = selectedPages.stream().anyMatch(BoardPage::isPinned);
                String pinKey = anyPinned ? "gui.gtcalcboard.browser.unpin_multiple_pages" : "gui.gtcalcboard.browser.pin_multiple_pages";
                items.add(new ContextMenuItem("§e📌 " + Component.translatable(pinKey, String.valueOf(selectedPages.size())).getString(), () -> {
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
            items.add(new ContextMenuItem("§e📌 " + Component.translatable(pinKey).getString(), () -> {
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
            items.add(new ContextMenuItem("§e📁 " + Component.translatable("gui.gtcalcboard.browser.new_subfolder").getString(), () -> {
                promptMode = PromptMode.NEW_SUBFOLDER;
                promptTargetFolder = contextFolder;
                Font font = Minecraft.getInstance().font;
                promptBox = new EditBox(font, 0, 0, 160, 16, Component.literal(""));
                promptBox.setValue("New Subfolder");
                promptBox.setFocused(true);
                contextMenuOpen = false;
            }));
            items.add(new ContextMenuItem("§6📤 " + Component.translatable("gui.gtcalcboard.browser.export_folder").getString(), () -> {
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

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (mouseX > DRAWER_WIDTH) {
            setOpen(false);
            return true;
        }
        if (handlePromptClicks(mouseX, mouseY, button)) {
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

        return handleTreeClicks(mouseX, mouseY, button);
    }

    private boolean handlePromptClicks(double mouseX, double mouseY, int button) {
        if (promptMode == PromptMode.NONE) return false;

        int pw = 180;
        int ph = 70;
        int px = (DRAWER_WIDTH - pw) / 2;
        int py = (screen.height - ph) / 2;
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
        int mx = Math.min(contextMenuX, DRAWER_WIDTH - menuW);
        int my = Math.min(contextMenuY, screen.height - menuH - 10);

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
        int closeX = DRAWER_WIDTH - 20;
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
            screen.rebuildWidgets();
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
        int listX = 8;
        int listY = topY + 44;
        int listW = DRAWER_WIDTH - 14;
        int listH = screen.height - topY - 54;

        if (mouseX < listX || mouseX > listX + listW || mouseY < listY || mouseY > listY + listH) {
            return false;
        }

        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
        FolderTreeNode root = buildFolderTree(query);
        int curY = listY + 4 - (int) scrollY;

        return handleTreeNodeClickRecursive(root, listX, curY, mouseX, mouseY, button, query);
    }

    private boolean handleTreeNodeClickRecursive(FolderTreeNode node, int listX, int curY, double mouseX, double mouseY, int button, String query) {
        if (!node.folderPath.isEmpty()) {
            if (mouseY >= curY && mouseY <= curY + 16) {
                return handleFolderClick(node, listX, mouseX, mouseY, button);
            }
            curY += 18;
            if (collapsedFolders.contains(node.folderPath) && query.isEmpty()) {
                return false;
            }
        }

        for (FolderTreeNode sub : node.subFolders.values()) {
            if (handleTreeNodeClickRecursive(sub, listX, curY, mouseX, mouseY, button, query)) {
                return true;
            }
            curY = calculateNodeHeight(sub, curY, query);
        }

        for (IndexedPage ip : node.directPages) {
            if (mouseY >= curY && mouseY <= curY + ITEM_HEIGHT) {
                return handlePageClick(ip, node.depth, listX, mouseX, mouseY, button);
            }
            curY += ITEM_HEIGHT + 2;
        }

        return false;
    }

    private int calculateNodeHeight(FolderTreeNode node, int startY, String query) {
        int curY = startY;
        if (!node.folderPath.isEmpty()) {
            curY += 18;
            if (collapsedFolders.contains(node.folderPath) && query.isEmpty()) {
                return curY;
            }
        }
        for (FolderTreeNode sub : node.subFolders.values()) {
            curY = calculateNodeHeight(sub, curY, query);
        }
        curY += node.directPages.size() * (ITEM_HEIGHT + 2);
        return curY;
    }

    private boolean handleFolderClick(FolderTreeNode node, int listX, double mouseX, double mouseY, int button) {
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
                lastClickedItem = new TreeItemRef(true, folder);
                playClickSound();
                return true;
            }

            if (net.minecraft.client.gui.screens.Screen.hasShiftDown() && lastClickedItem != null) {
                String query = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
                FolderTreeNode root = buildFolderTree(query);
                applyRangeSelection(root, lastClickedItem, new TreeItemRef(true, folder), query);
                playClickSound();
                return true;
            }

            if (!selectedFolderPaths.contains(folder)) {
                selectedPageIds.clear();
                selectedFolderPaths.clear();
                selectedFolderPaths.add(folder);
            }
            lastClickedItem = new TreeItemRef(true, folder);

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
                lastClickedItem = new TreeItemRef(true, folder);
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

    private boolean handlePageClick(IndexedPage ip, int depth, int listX, double mouseX, double mouseY, int button) {
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

    private boolean handlePinClick(IndexedPage ip) {
        if (selectedPageIds.contains(ip.page.getId()) && selectedPageIds.size() > 1) {
            List<BoardPage> selectedPages = getSelectedPagesList();
            boolean anyPinned = selectedPages.stream().anyMatch(BoardPage::isPinned);
            boolean newPin = !anyPinned;
            for (BoardPage p : selectedPages) {
                p.setPinned(newPin);
            }
        } else {
            ip.page.setPinned(!ip.page.isPinned());
        }
        playClickSound();
        return true;
    }

    private boolean handlePageLeftClick(IndexedPage ip, double mouseX, double mouseY) {
        String pageId = ip.page.getId();
        if (net.minecraft.client.gui.screens.Screen.hasControlDown()) {
            if (selectedPageIds.contains(pageId)) {
                selectedPageIds.remove(pageId);
            } else {
                selectedPageIds.add(pageId);
            }
            lastClickedItem = new TreeItemRef(false, pageId);
            playClickSound();
            return true;
        }

        if (net.minecraft.client.gui.screens.Screen.hasShiftDown() && lastClickedItem != null) {
            String query = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
            FolderTreeNode root = buildFolderTree(query);
            applyRangeSelection(root, lastClickedItem, new TreeItemRef(false, pageId), query);
            playClickSound();
            return true;
        }

        if (!selectedPageIds.contains(pageId)) {
            selectedFolderPaths.clear();
            selectedPageIds.clear();
            selectedPageIds.add(pageId);
        }
        lastClickedItem = new TreeItemRef(false, pageId);

        BoardManager.getInstance().switchPage(ip.index);
        BoardPage active = BoardManager.getInstance().getActivePage();
        if (active != null) {
            screen.setPanX(active.getPanX());
            screen.setPanY(active.getPanY());
            screen.setZoom(active.getZoom());
        }
        screen.rebuildWidgets();
        playClickSound();

        draggingPage = ip.page;
        draggingPageIndex = ip.index;
        draggingFolder = null;
        dragStartX = mouseX;
        dragStartY = mouseY;
        isDragging = false;
        return true;
    }

    private void applyRangeSelection(FolderTreeNode root, TreeItemRef startRef, TreeItemRef endRef, String query) {
        List<TreeItemRef> visibleItems = new ArrayList<>();
        collectVisibleItems(root, visibleItems, query);

        int startIdx = visibleItems.indexOf(startRef);
        int endIdx = visibleItems.indexOf(endRef);
        if (startIdx >= 0 && endIdx >= 0) {
            int min = Math.min(startIdx, endIdx);
            int max = Math.max(startIdx, endIdx);
            for (int i = min; i <= max; i++) {
                TreeItemRef ref = visibleItems.get(i);
                if (ref.isFolder()) {
                    selectedFolderPaths.add(ref.idOrPath());
                } else {
                    selectedPageIds.add(ref.idOrPath());
                }
            }
        } else {
            if (endRef.isFolder()) {
                selectedFolderPaths.add(endRef.idOrPath());
            } else {
                selectedPageIds.add(endRef.idOrPath());
            }
        }
    }

    private void collectVisibleItems(FolderTreeNode node, List<TreeItemRef> result, String query) {
        if (!node.folderPath.isEmpty()) {
            result.add(new TreeItemRef(true, node.folderPath));
            if (collapsedFolders.contains(node.folderPath) && query.isEmpty()) {
                return;
            }
        }
        for (FolderTreeNode sub : node.subFolders.values()) {
            collectVisibleItems(sub, result, query);
        }
        for (IndexedPage ip : node.directPages) {
            result.add(new TreeItemRef(false, ip.page.getId()));
        }
    }

    private boolean handlePageRightClick(IndexedPage ip, double mouseX, double mouseY) {
        String pageId = ip.page.getId();
        if (!selectedPageIds.contains(pageId)) {
            selectedFolderPaths.clear();
            selectedPageIds.clear();
            selectedPageIds.add(pageId);
            lastClickedItem = new TreeItemRef(false, pageId);
        }
        contextMenuOpen = true;
        contextMenuX = (int) mouseX;
        contextMenuY = (int) mouseY;
        contextPage = ip.page;
        contextPageIndex = ip.index;
        contextFolder = null;
        return true;
    }

    private FolderTreeNode buildFolderTree(String query) {
        FolderTreeNode root = new FolderTreeNode("", "", 0);
        List<BoardPage> pages = BoardManager.getInstance().getPages();
        for (int i = 0; i < pages.size(); i++) {
            BoardPage p = pages.get(i);
            String f = p.getFolderPath() != null ? p.getFolderPath().trim() : "";
            if (!query.isEmpty() && !p.getName().toLowerCase().contains(query) && !f.toLowerCase().contains(query)) {
                continue;
            }
            if (f.isEmpty()) {
                root.directPages.add(new IndexedPage(i, p));
            } else {
                String[] parts = f.split("/");
                FolderTreeNode current = root;
                StringBuilder pathAcc = new StringBuilder();
                for (int depth = 0; depth < parts.length; depth++) {
                    String part = parts[depth];
                    if (pathAcc.length() > 0) pathAcc.append("/");
                    pathAcc.append(part);
                    String curPath = pathAcc.toString();
                    int curDepth = depth + 1;
                    current = current.subFolders.computeIfAbsent(part, k -> new FolderTreeNode(curPath, part, curDepth));
                }
                current.directPages.add(new IndexedPage(i, p));
            }
        }
        return root;
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
        draggingFolder = null;
        draggingPage = null;
        draggingPageIndex = -1;
        isDragging = false;
        return true;
    }

    private String resolveFolderUnderMouse(double mouseY) {
        int topY = screen.getHeaderBottomY() + 2;
        int listY = topY + 44;
        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
        FolderTreeNode root = buildFolderTree(query);
        int curY = listY + 4 - (int) scrollY;
        String found = resolveFolderUnderMouseRecursive(root, curY, mouseY, query);
        return found != null ? found : "";
    }

    private String resolveFolderUnderMouseRecursive(FolderTreeNode node, int curY, double mouseY, String query) {
        if (!node.folderPath.isEmpty()) {
            if (mouseY >= curY && mouseY <= curY + 16) {
                return node.folderPath;
            }
            curY += 18;
            if (collapsedFolders.contains(node.folderPath) && query.isEmpty()) {
                return null;
            }
        }
        for (FolderTreeNode sub : node.subFolders.values()) {
            String found = resolveFolderUnderMouseRecursive(sub, curY, mouseY, query);
            if (found != null) return found;
            curY = calculateNodeHeight(sub, curY, query);
        }
        for (IndexedPage ignored : node.directPages) {
            if (mouseY >= curY && mouseY <= curY + ITEM_HEIGHT) {
                return node.folderPath;
            }
            curY += ITEM_HEIGHT + 2;
        }
        return null;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!open || mouseX > DRAWER_WIDTH) return false;
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

        return true;
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
                    screen.rebuildWidgets();
                }
                case NEW_SUBFOLDER -> {
                    String subPath = promptTargetFolder.isEmpty() ? val : (promptTargetFolder + "/" + val);
                    BoardManager.getInstance().notifyFolderCreated(subPath);
                    BoardManager.getInstance().addPage("Page " + (BoardManager.getInstance().getPages().size() + 1), subPath);
                    screen.rebuildWidgets();
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

    public static class FolderTreeNode {
        public final String folderPath;
        public final String simpleName;
        public final int depth;
        public final Map<String, FolderTreeNode> subFolders = new TreeMap<>();
        public final List<IndexedPage> directPages = new ArrayList<>();

        public FolderTreeNode(String folderPath, String simpleName, int depth) {
            this.folderPath = folderPath;
            this.simpleName = simpleName;
            this.depth = depth;
        }

        public int getTotalPageCount() {
            int count = directPages.size();
            for (FolderTreeNode sub : subFolders.values()) {
                count += sub.getTotalPageCount();
            }
            return count;
        }
    }

    private record IndexedPage(int index, BoardPage page) {}
}
