package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class PageBrowserDrawerRenderer {

    private PageBrowserDrawerRenderer() {}

    public static void render(PageBrowserDrawer drawer, GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!drawer.isOpen()) return;

        BoardScreen screen = drawer.getScreen();
        Font font = Minecraft.getInstance().font;
        int height = screen.height;
        int topY = screen.getHeaderBottomY() + 2;
        int drawerH = height - topY - 4;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400.0f);
        RenderSystem.disableDepthTest();

        renderBackground(graphics, topY, drawerH);
        renderHeader(graphics, font, topY, mouseX, mouseY);
        renderSearchBox(drawer, graphics, mouseX, mouseY, partialTicks, topY);
        renderTreeView(drawer, graphics, font, topY, drawerH, mouseX, mouseY);
        renderDragGhost(drawer, graphics, font, mouseX, mouseY);
        renderContextMenuOverlay(drawer, graphics, font, mouseX, mouseY);
        renderPromptModalOverlay(drawer, graphics, font, mouseX, mouseY, partialTicks);

        graphics.pose().popPose();
    }

    private static void renderBackground(GuiGraphics graphics, int topY, int drawerH) {
        graphics.fill(PageBrowserDrawer.DRAWER_X, topY, PageBrowserDrawer.DRAWER_X + PageBrowserDrawer.DRAWER_WIDTH, topY + drawerH, 0xF5141822);
        graphics.renderOutline(PageBrowserDrawer.DRAWER_X, topY, PageBrowserDrawer.DRAWER_WIDTH, drawerH, 0xFF353C4D);
        graphics.renderOutline(PageBrowserDrawer.DRAWER_X + 1, topY + 1, PageBrowserDrawer.DRAWER_WIDTH - 2, drawerH - 2, 0xFF0D1117);
    }

    private static void renderHeader(GuiGraphics graphics, Font font, int topY, int mouseX, int mouseY) {
        graphics.drawString(font, "§6≡ " + Component.translatable("gui.gtcalcboard.browser.title").getString(), PageBrowserDrawer.DRAWER_X + 8, topY + 8, 0xFFFFFFFF, false);

        int btnY = topY + 6;
        int closeX = PageBrowserDrawer.DRAWER_X + PageBrowserDrawer.DRAWER_WIDTH - 20;
        int importX = closeX - 22;
        int addPageX = importX - 22;
        int addFolderX = addPageX - 24;

        boolean addFolderHover = mouseX >= addFolderX && mouseX <= addFolderX + 20 && mouseY >= btnY && mouseY <= btnY + 14;
        graphics.fill(addFolderX, btnY, addFolderX + 20, btnY + 14, addFolderHover ? 0xFF2A364C : 0xFF1C2230);
        graphics.renderOutline(addFolderX, btnY, 20, 14, addFolderHover ? 0xFF5588DD : 0xFF353C4D);
        graphics.drawString(font, "§b+≡", addFolderX + 2, btnY + 3, 0xFFFFFFFF, false);

        boolean addPageHover = mouseX >= addPageX && mouseX <= addPageX + 20 && mouseY >= btnY && mouseY <= btnY + 14;
        graphics.fill(addPageX, btnY, addPageX + 20, btnY + 14, addPageHover ? 0xFF2A4C36 : 0xFF1C3024);
        graphics.renderOutline(addPageX, btnY, 20, 14, addPageHover ? 0xFF55FF88 : 0xFF356B48);
        graphics.drawString(font, "§a+▪", addPageX + 2, btnY + 3, 0xFFFFFFFF, false);

        boolean importHover = mouseX >= importX && mouseX <= importX + 20 && mouseY >= btnY && mouseY <= btnY + 14;
        graphics.fill(importX, btnY, importX + 20, btnY + 14, importHover ? 0xFF3D3A2A : 0xFF26241C);
        graphics.renderOutline(importX, btnY, 20, 14, importHover ? 0xFFFFDD55 : 0xFF66582B);
        graphics.drawString(font, "§e«", importX + 3, btnY + 3, 0xFFFFFFFF, false);

        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= btnY && mouseY <= btnY + 14;
        graphics.drawString(font, "§c✕", closeX + 2, btnY + 3, closeHover ? 0xFFFF6666 : 0xFFAAAAAA, false);
    }

    private static void renderSearchBox(PageBrowserDrawer drawer, GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, int topY) {
        if (drawer.getSearchBox() == null) return;
        drawer.getSearchBox().setX(PageBrowserDrawer.DRAWER_X + 8);
        drawer.getSearchBox().setY(topY + 24);
        drawer.getSearchBox().setWidth(PageBrowserDrawer.DRAWER_WIDTH - 16);
        drawer.getSearchBox().render(graphics, mouseX, mouseY, partialTicks);
    }

    private static void renderTreeView(PageBrowserDrawer drawer, GuiGraphics graphics, Font font, int topY, int drawerH, int mouseX, int mouseY) {
        int listX = PageBrowserDrawer.DRAWER_X + 6;
        int listY = topY + 44;
        int listW = PageBrowserDrawer.DRAWER_WIDTH - 12;
        int listH = drawerH - 50;

        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF0D1017);
        graphics.renderOutline(listX, listY, listW, listH, 0xFF222733);
        BoardScissorHelper.enableScissor(graphics, listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);

        String query = drawer.getSearchBox() != null ? drawer.getSearchBox().getValue().trim().toLowerCase() : "";
        PageBrowserTreeModel.FolderTreeNode root = PageBrowserTreeModel.buildFolderTree(query);
        int activeIdx = BoardManager.getInstance().getActivePageIndex();

        int curY = listY + 4 - (int) drawer.getScrollY();
        curY = renderTreeNodeRecursive(drawer, graphics, font, root, activeIdx, listX, listW, curY, mouseX, mouseY, query);

        int totalContentH = (curY + (int) drawer.getScrollY()) - listY;
        drawer.setMaxScrollY(Math.max(0, totalContentH - listH));
        BoardScissorHelper.disableScissor(graphics);
    }

    private static int renderTreeNodeRecursive(PageBrowserDrawer drawer, GuiGraphics graphics, Font font, PageBrowserTreeModel.FolderTreeNode node, int activeIdx, int listX, int listW, int curY, int mouseX, int mouseY, String query) {
        if (!node.folderPath.isEmpty()) {
            curY = renderFolderRow(drawer, graphics, font, node, listX, listW, curY, mouseX, mouseY);
            if (drawer.getCollapsedFolders().contains(node.folderPath) && query.isEmpty()) {
                return curY;
            }
        }

        for (PageBrowserTreeModel.FolderTreeNode sub : node.subFolders.values()) {
            curY = renderTreeNodeRecursive(drawer, graphics, font, sub, activeIdx, listX, listW, curY, mouseX, mouseY, query);
        }

        for (PageBrowserTreeModel.IndexedPage ip : node.directPages) {
            curY = renderPageRow(drawer, graphics, font, ip, node.depth, activeIdx, listX, listW, curY, mouseX, mouseY);
        }

        return curY;
    }

    private static int renderFolderRow(PageBrowserDrawer drawer, GuiGraphics graphics, Font font, PageBrowserTreeModel.FolderTreeNode node, int listX, int listW, int curY, int mouseX, int mouseY) {
        boolean isCollapsed = drawer.getCollapsedFolders().contains(node.folderPath);
        boolean isSelected = drawer.getSelectedFolderPaths().contains(node.folderPath);
        boolean folderHover = mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= curY && mouseY <= curY + 16;

        int bg = isSelected ? 0x662563EB : (folderHover ? 0xFF1C2433 : 0xFF141924);
        int border = isSelected ? 0xFF60A5FA : (folderHover ? 0xFF3D4B66 : 0xFF222B3D);

        graphics.fill(listX + 2, curY, listX + listW - 2, curY + 16, bg);
        graphics.renderOutline(listX + 2, curY, listW - 4, 16, border);

        int indent = (node.depth - 1) * 10 + 6;
        String toggleIcon = isCollapsed ? "▶ " : "▼ ";
        String nameColor = isSelected ? "§b" : "§e";
        graphics.drawString(font, nameColor + toggleIcon + "≡ " + node.simpleName + " §8(" + node.getTotalPageCount() + ")", listX + indent, curY + 4, 0xFFFFFFFF, false);

        return curY + 18;
    }

    private static int renderPageRow(PageBrowserDrawer drawer, GuiGraphics graphics, Font font, PageBrowserTreeModel.IndexedPage ip, int depth, int activeIdx, int listX, int listW, int curY, int mouseX, int mouseY) {
        int pageIdx = ip.index();
        BoardPage page = ip.page();
        boolean isActive = (pageIdx == activeIdx);
        boolean isSelected = drawer.getSelectedPageIds().contains(page.getId());
        int indent = depth * 10 + 6;

        boolean pageHover = mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= curY && mouseY <= curY + PageBrowserDrawer.ITEM_HEIGHT;
        int bg = isSelected ? 0x662563EB : (isActive ? 0xFF1C3D26 : (pageHover ? 0xFF222B3D : 0x00000000));
        int border = isSelected ? 0xFF60A5FA : (isActive ? 0xFF55FF88 : (pageHover ? 0xFF354460 : 0x00000000));

        if (bg != 0) {
            graphics.fill(listX + 2, curY, listX + listW - 2, curY + PageBrowserDrawer.ITEM_HEIGHT, bg);
        }
        if (border != 0) {
            graphics.renderOutline(listX + 2, curY, listW - 4, PageBrowserDrawer.ITEM_HEIGHT, border);
        }

        boolean pinHover = mouseX >= listX + indent && mouseX <= listX + indent + 10 && mouseY >= curY + 3 && mouseY <= curY + 15;
        String pinStr = page.isPinned() ? "§e★" : (pinHover ? "§7★" : "§8·");
        graphics.drawString(font, pinStr, listX + indent, curY + 5, 0xFFFFFFFF, false);

        ItemStack icon = page.getEffectiveRepresentativeIcon();
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, listX + indent + 12, curY + 2);
        } else {
            graphics.drawString(font, "§7▪", listX + indent + 12, curY + 5, 0xFFFFFFFF, false);
        }

        boolean isAe2 = com.gtceu.calcboard.integration.ae2.registry.PatternGraphRegistry.getInstance().isPageBound(page.getId());
        String nameColor = isSelected ? "§b" : (isActive ? "§a" : (isAe2 ? "§b" : "§f"));
        int nameX = listX + indent + 30;
        int maxNameW = listW - (indent + 34);
        String prefixTag = isAe2 && !page.getName().startsWith("[AE2]") ? "§b[AE2] " : "";
        String trimmedName = font.plainSubstrByWidth(prefixTag + page.getName(), maxNameW);
        graphics.drawString(font, nameColor + trimmedName, nameX, curY + 6, 0xFFFFFFFF, false);

        return curY + PageBrowserDrawer.ITEM_HEIGHT + 2;
    }

    private static void renderDragGhost(PageBrowserDrawer drawer, GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!drawer.isDragging() || (drawer.getDraggingPage() == null && drawer.getDraggingFolder() == null)) return;
        String ghostText;
        int totalSelectedCount = drawer.getSelectedFolderPaths().size() + drawer.getSelectedPageIds().size();
        if (drawer.getDraggingFolder() != null) {
            String folder = drawer.getDraggingFolder();
            String simpleName = folder.contains("/") ? folder.substring(folder.lastIndexOf('/') + 1) : folder;
            if (totalSelectedCount > 1 && drawer.getSelectedFolderPaths().contains(folder)) {
                ghostText = "§b≡ " + Component.translatable("gui.gtcalcboard.browser.drag_multiple_ghost",
                        simpleName,
                        String.valueOf(totalSelectedCount - 1),
                        String.valueOf(totalSelectedCount)).getString();
            } else {
                ghostText = "§e≡ " + simpleName;
            }
        } else {
            BoardPage page = drawer.getDraggingPage();
            if (totalSelectedCount > 1 && drawer.getSelectedPageIds().contains(page.getId())) {
                ghostText = "§b▪ " + Component.translatable("gui.gtcalcboard.browser.drag_multiple_ghost",
                        page.getName(),
                        String.valueOf(totalSelectedCount - 1),
                        String.valueOf(totalSelectedCount)).getString();
            } else {
                ghostText = "§a▪ " + page.getName();
            }
        }
        int gw = font.width(ghostText) + 16;
        graphics.fill(mouseX + 4, mouseY + 4, mouseX + 4 + gw, mouseY + 22, 0xDD1C2C44);
        graphics.renderOutline(mouseX + 4, mouseY + 4, gw, 18, 0xFF5588DD);
        graphics.drawString(font, ghostText, mouseX + 8, mouseY + 9, 0xFFFFFFFF, false);
    }

    private static void renderContextMenuOverlay(PageBrowserDrawer drawer, GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!drawer.isContextMenuOpen()) return;
        List<PageBrowserDrawer.ContextMenuItem> items = drawer.buildContextMenuItems();
        int menuW = 140;
        int menuH = items.size() * 18 + 6;

        int mx = Math.max(PageBrowserDrawer.DRAWER_X + 4, Math.min(drawer.getContextMenuX(), PageBrowserDrawer.DRAWER_X + PageBrowserDrawer.DRAWER_WIDTH - menuW - 4));
        int my = Math.min(drawer.getContextMenuY(), drawer.getScreen().height - menuH - 10);

        graphics.fill(mx, my, mx + menuW, my + menuH, 0xF5181C26);
        graphics.renderOutline(mx, my, menuW, menuH, 0xFF3D4B66);

        for (int i = 0; i < items.size(); i++) {
            PageBrowserDrawer.ContextMenuItem it = items.get(i);
            int iy = my + 3 + i * 18;
            boolean hov = mouseX >= mx + 2 && mouseX <= mx + menuW - 2 && mouseY >= iy && mouseY <= iy + 16;
            if (hov) {
                graphics.fill(mx + 2, iy, mx + menuW - 2, iy + 16, 0xFF2A364C);
            }
            graphics.drawString(font, it.label(), mx + 8, iy + 4, hov ? 0xFFFFFFFF : 0xFFCCCCCC, false);
        }
    }

    private static void renderPromptModalOverlay(PageBrowserDrawer drawer, GuiGraphics graphics, Font font, int mouseX, int mouseY, float partialTicks) {
        if (drawer.getPromptMode() == PageBrowserDrawer.PromptMode.NONE) return;

        BoardScreen screen = drawer.getScreen();
        int topY = screen.getHeaderBottomY() + 2;
        int drawerH = screen.height - topY - 4;
        graphics.fill(PageBrowserDrawer.DRAWER_X, topY, PageBrowserDrawer.DRAWER_X + PageBrowserDrawer.DRAWER_WIDTH, topY + drawerH, 0xAA000000);

        int pw = 180;
        int ph = 70;
        int px = PageBrowserDrawer.DRAWER_X + (PageBrowserDrawer.DRAWER_WIDTH - pw) / 2;
        int py = (screen.height - ph) / 2;

        graphics.fill(px, py, px + pw, py + ph, 0xF5161A24);
        graphics.renderOutline(px, py, pw, ph, 0xFF5588DD);

        String title = switch (drawer.getPromptMode()) {
            case NEW_FOLDER -> "gui.gtcalcboard.browser.prompt_new_folder";
            case NEW_SUBFOLDER -> "gui.gtcalcboard.browser.prompt_new_subfolder";
            case RENAME_FOLDER -> "gui.gtcalcboard.browser.prompt_rename_folder";
            case RENAME_PAGE -> "gui.gtcalcboard.browser.prompt_rename_page";
            default -> "";
        };
        graphics.drawString(font, Component.translatable(title).getString(), px + 10, py + 8, 0xFFFFFFFF, false);

        if (drawer.getPromptBox() != null) {
            drawer.getPromptBox().setX(px + 10);
            drawer.getPromptBox().setY(py + 24);
            drawer.getPromptBox().render(graphics, mouseX, mouseY, partialTicks);
        }

        int btnY = py + 46;
        boolean okHov = mouseX >= px + 10 && mouseX <= px + 80 && mouseY >= btnY && mouseY <= btnY + 16;
        graphics.fill(px + 10, btnY, px + 80, btnY + 16, okHov ? 0xFF2A5A38 : 0xFF1C3D26);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.dialog.btn_ok").getString(), px + 45, btnY + 4, 0xFFFFFFFF);

        boolean cancelHov = mouseX >= px + 95 && mouseX <= px + 165 && mouseY >= btnY && mouseY <= btnY + 16;
        graphics.fill(px + 95, btnY, px + 165, btnY + 16, cancelHov ? 0xFF3D2A2A : 0xFF261D1D);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.dialog.btn_cancel").getString(), px + 130, btnY + 4, 0xFFFFFFFF);
    }
}
