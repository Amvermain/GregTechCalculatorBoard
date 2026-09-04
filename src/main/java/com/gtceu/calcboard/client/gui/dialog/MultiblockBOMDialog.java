package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import com.gtceu.calcboard.integration.spi.RecipeViewerRegistry;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.bom.MultiblockBOMCalculator;
import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import com.gtceu.calcboard.api.bom.PartCategory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class MultiblockBOMDialog {
    private final BoardScreen parent;
    private boolean visible = false;

    private final Set<String> selectedPageIds = new LinkedHashSet<>();
    private final Set<ResourceLocation> preparedItemIds = new HashSet<>();

    private int filterCategoryIndex = 0; // 0: All, 1: Casings, 2: Coils, 3: Hatches/Buses, 4: Controllers
    private boolean dualLowerTierEnergyHatches = false;

    private String searchQuery = "";
    private EditBox searchBox;

    private double pageScrollY = 0.0;
    private double itemScrollY = 0.0;
    private double maxPageScrollY = 0.0;
    private double maxItemScrollY = 0.0;

    private MultiblockBOMSummary cachedSummary;
    private boolean dirty = true;
    private MultiblockBOMSummary.BOMItemEntry hoveredEntry = null;

    private static final int DIALOG_WIDTH = 520;
    private static final int DIALOG_HEIGHT = 290;
    private static final int SIDEBAR_WIDTH = 105;

    public MultiblockBOMDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public boolean isVisible() {
        return visible;
    }

    public void open() {
        if (!com.gtceu.calcboard.api.util.ModCompatHelper.isBoMSupported()) {
            return;
        }
        this.visible = true;
        this.selectedPageIds.clear();
        BoardPage curPage = BoardManager.getInstance().getActivePage();
        if (curPage != null) {
            this.selectedPageIds.add(curPage.getId());
        } else {
            for (BoardPage p : BoardManager.getInstance().getPages()) {
                this.selectedPageIds.add(p.getId());
            }
        }
        this.searchQuery = "";
        this.filterCategoryIndex = 0;
        this.pageScrollY = 0.0;
        this.itemScrollY = 0.0;
        this.dirty = true;

        Font font = Minecraft.getInstance().font;
        int screenW = parent != null && parent.width > 0 ? parent.width : Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = parent != null && parent.height > 0 ? parent.height : Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int dialogW = Math.min(DIALOG_WIDTH, screenW - 16);
        int dialogH = Math.min(DIALOG_HEIGHT, screenH - 16);
        int dialogX = (screenW - dialogW) / 2;
        int dialogY = (screenH - dialogH) / 2;
        int bannerY = dialogY + 25;
        int sidebarY = bannerY + 22 + 4;
        int mainX = dialogX + SIDEBAR_WIDTH + 14;
        int row1Y = sidebarY + 1;
        int row2Y = row1Y + 17;

        this.searchBox = new EditBox(font, mainX, row2Y, 120, 14, Component.literal(""));
        this.searchBox.setMaxLength(256);
        this.searchBox.setHint(Component.translatable("gui.gtcalcboard.search.placeholder"));
        this.searchBox.setResponder(text -> {
            this.searchQuery = text.toLowerCase(Locale.ROOT).trim();
            this.itemScrollY = 0.0;
        });
        com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onBOMOpened();
    }

    public void close() {
        this.visible = false;
        if (searchBox != null) {
            searchBox.setFocused(false);
        }
    }

    public void markDirty() {
        this.dirty = true;
    }

    private void updateSummaryIfNeeded() {
        if (dirty || cachedSummary == null) {
            List<BoardPage> allPages = BoardManager.getInstance().getPages();
            List<MultiblockBOMSummary> pageSummaries = new ArrayList<>();
            for (BoardPage p : allPages) {
                if (selectedPageIds.contains(p.getId()) && p.getGraph() != null) {
                    pageSummaries.add(MultiblockBOMCalculator.calculateBOM(p.getGraph(), dualLowerTierEnergyHatches));
                }
            }
            cachedSummary = MultiblockBOMSummary.merge(pageSummaries);
            dirty = false;
        }
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible) return;

        updateSummaryIfNeeded();

        int dialogW = Math.min(DIALOG_WIDTH, screenWidth - 16);
        int dialogH = Math.min(DIALOG_HEIGHT, screenHeight - 16);
        int dialogX = (screenWidth - dialogW) / 2;
        int dialogY = (screenHeight - dialogH) / 2;

        Font font = Minecraft.getInstance().font;
        hoveredEntry = null;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);

        // 1. Semi-transparent backdrop overlay
        graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);

        // 2. Dialog container background & border
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, 0xF510141C);
        graphics.renderOutline(dialogX, dialogY, dialogW, dialogH, 0xFF35445E);

        // 3. Top Title Bar
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + 24, 0xDD182232);
        graphics.fill(dialogX, dialogY + 23, dialogX + dialogW, dialogY + 24, 0xFF2A364D);
        graphics.drawString(font, "§6📋 " + Component.translatable("gui.gtcalcboard.bom.title").getString(), dialogX + 8, dialogY + 8, 0xFFFFFFFF, false);

        // Close Button [✕]
        int closeBtnX = dialogX + dialogW - 20;
        int closeBtnY = dialogY + 4;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + 16 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16;
        graphics.fill(closeBtnX, closeBtnY, closeBtnX + 16, closeBtnY + 16, closeHover ? 0xFF552222 : 0x00000000);
        graphics.drawCenteredString(font, "§c✕", closeBtnX + 8, closeBtnY + 4, 0xFFFFFFFF);

        // 4. Statistics Banner (Under title bar)
        int bannerY = dialogY + 25;
        int bannerH = 22;
        graphics.fill(dialogX + 1, bannerY, dialogX + dialogW - 1, bannerY + bannerH, 0xAA141C28);
        graphics.fill(dialogX + 1, bannerY + bannerH - 1, dialogX + dialogW - 1, bannerY + bannerH, 0xFF222E40);

        String multiCountStr = "§6🏛 " + Component.translatable("gui.gtcalcboard.bom.total_multiblocks", cachedSummary.totalMultiblockCount()).getString();
        graphics.drawString(font, multiCountStr, dialogX + 10, bannerY + 7, 0xFFFFFFFF, false);

        String uniqueBlocksStr = "§b📦 " + Component.translatable("gui.gtcalcboard.bom.total_unique_blocks", cachedSummary.totalUniqueItemTypes()).getString();
        graphics.drawString(font, uniqueBlocksStr, dialogX + 10 + font.width(multiCountStr) + 14, bannerY + 7, 0xFFFFFFFF, false);

        // Quick action buttons in Banner: [📋 Copy List] and [⭐ Register in EMI/JEI]
        int copyBtnW = font.width(Component.translatable("gui.gtcalcboard.bom.copy_clipboard").getString()) + 14;
        int copyBtnX = dialogX + dialogW - 10 - copyBtnW;
        int copyBtnY = bannerY + 3;
        boolean copyHover = mouseX >= copyBtnX && mouseX <= copyBtnX + copyBtnW && mouseY >= copyBtnY && mouseY <= copyBtnY + 16;
        graphics.fill(copyBtnX, copyBtnY, copyBtnX + copyBtnW, copyBtnY + 16, copyHover ? 0xFF2B4466 : 0xFF1C2C44);
        graphics.renderOutline(copyBtnX, copyBtnY, copyBtnW, 16, copyHover ? 0xFF55AAFF : 0xFF355580);
        graphics.drawCenteredString(font, "§b📋 " + Component.translatable("gui.gtcalcboard.bom.copy_clipboard").getString(), copyBtnX + copyBtnW / 2, copyBtnY + 4, 0xFFFFFFFF);

        var activeViewer = com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter();
        if (activeViewer.isBoMGoalRegistrationSupported()) {
            boolean isEmi = "emi".equals(activeViewer.getViewerId());
            String regKey = isEmi ? "gui.gtcalcboard.bom.register_emi" : "gui.gtcalcboard.bom.register_jei";
            String regTxt = (isEmi ? "§a⭐ " : "§e⭐ ") + Component.translatable(regKey).getString();
            int regBtnW = font.width(Component.translatable(regKey).getString()) + 14;
            int regBtnX = copyBtnX - regBtnW - 6;
            boolean regHover = mouseX >= regBtnX && mouseX <= regBtnX + regBtnW && mouseY >= copyBtnY && mouseY <= copyBtnY + 16;
            graphics.fill(regBtnX, copyBtnY, regBtnX + regBtnW, copyBtnY + 16, regHover ? (isEmi ? 0xFF2B5533 : 0xFF4A3B18) : (isEmi ? 0xFF1C3A24 : 0xFF2D240E));
            graphics.renderOutline(regBtnX, copyBtnY, regBtnW, 16, regHover ? (isEmi ? 0xFF55FF77 : 0xFFFDE047) : (isEmi ? 0xFF358045 : 0xFF786221));
            graphics.drawCenteredString(font, regTxt, regBtnX + regBtnW / 2, copyBtnY + 4, 0xFFFFFFFF);
        }

        // 5. Left Sidebar (Included Pages)
        int sidebarX = dialogX + 6;
        int sidebarY = bannerY + bannerH + 4;
        int sidebarH = dialogH - (sidebarY - dialogY) - 6;

        renderSidebar(graphics, font, sidebarX, sidebarY, SIDEBAR_WIDTH, sidebarH, mouseX, mouseY);

        // Separator between sidebar and main content
        graphics.fill(dialogX + SIDEBAR_WIDTH + 8, sidebarY, dialogX + SIDEBAR_WIDTH + 9, sidebarY + sidebarH, 0xFF283448);

        // 6. Right Main Content (Material BOM Table & Filter Controls)
        int mainX = dialogX + SIDEBAR_WIDTH + 14;
        int mainY = sidebarY;
        int mainW = dialogW - (mainX - dialogX) - 8;
        int mainH = sidebarH;

        renderMainContent(graphics, font, mainX, mainY, mainW, mainH, mouseX, mouseY);

        // 7. Render hovered tooltips
        renderTooltips(graphics, font, mouseX, mouseY);

        graphics.pose().popPose();
    }

    private void renderSidebar(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        graphics.fill(x, y, x + w, y + h, 0xDD121620);
        graphics.renderOutline(x, y, w, h, 0xFF253042);

        // Sidebar Header
        int titleH = 16;
        graphics.fill(x, y, x + w, y + titleH, 0xFF18202C);
        graphics.drawString(font, "§6📑 " + Component.translatable("gui.gtcalcboard.global_balance.included_pages").getString(), x + 4, y + 4, 0xFFFFFFFF, false);

        // Bottom Action Buttons: [All] / [None]
        int btnH = 14;
        int btnY = y + h - btnH - 3;
        int halfW = (w - 8) / 2;

        int allBtnX = x + 3;
        boolean allHover = mouseX >= allBtnX && mouseX <= allBtnX + halfW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(allBtnX, btnY, allBtnX + halfW, btnY + btnH, allHover ? 0xFF2C3E55 : 0xFF1C2736);
        graphics.renderOutline(allBtnX, btnY, halfW, btnH, 0xFF3D5373);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.global_balance.select_all").getString(), allBtnX + halfW / 2, btnY + 3, 0xFFFFFFFF);

        int noneBtnX = allBtnX + halfW + 2;
        boolean noneHover = mouseX >= noneBtnX && mouseX <= noneBtnX + halfW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(noneBtnX, btnY, noneBtnX + halfW, btnY + btnH, noneHover ? 0xFF4A2525 : 0xFF2D1818);
        graphics.renderOutline(noneBtnX, btnY, halfW, btnH, 0xFF6B3636);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.global_balance.deselect_all").getString(), noneBtnX + halfW / 2, btnY + 3, 0xFFFFFFFF);

        // Scrollable Page List
        int listY = y + titleH + 2;
        int listH = btnY - listY - 2;
        List<BoardPage> pages = BoardManager.getInstance().getPages();
        int rowH = 16;
        int totalListH = pages.size() * rowH;
        maxPageScrollY = Math.max(0, totalListH - listH);

        BoardScissorHelper.enableScissor(graphics, x, listY, x + w, listY + listH);

        int curY = (int) (listY - pageScrollY);
        for (BoardPage page : pages) {
            if (curY + rowH >= listY && curY <= listY + listH) {
                boolean checked = selectedPageIds.contains(page.getId());
                boolean rowHover = mouseX >= x + 2 && mouseX <= x + w - 2 && mouseY >= curY && mouseY <= curY + rowH;

                if (rowHover) {
                    graphics.fill(x + 2, curY, x + w - 2, curY + rowH, 0x333A4D6B);
                }

                // Checkbox
                int cbX = x + 4;
                int cbY = curY + 3;
                graphics.fill(cbX, cbY, cbX + 10, cbY + 10, 0xFF141A24);
                graphics.renderOutline(cbX, cbY, 10, 10, checked ? 0xFF55FF55 : 0xFF35445E);
                if (checked) {
                    graphics.drawString(font, "✔", cbX + 1, cbY + 1, 0xFF55FF55, false);
                }

                // Page Title
                String pTitle = page.getName() != null && !page.getName().isBlank() ? page.getName() : "Page";
                String clippedTitle = font.plainSubstrByWidth(pTitle, w - 24);
                graphics.drawString(font, (checked ? "§f" : "§7") + clippedTitle, cbX + 14, curY + 4, 0xFFFFFFFF, false);
            }
            curY += rowH;
        }

        BoardScissorHelper.disableScissor(graphics);

        // Page Scrollbar
        if (maxPageScrollY > 0) {
            int sbX = x + w - 3;
            int sbH = Math.max(8, (int) ((double) listH / totalListH * listH));
            int sbY = listY + (int) (pageScrollY / maxPageScrollY * (listH - sbH));
            graphics.fill(sbX, listY, sbX + 2, listY + listH, 0x44000000);
            graphics.fill(sbX, sbY, sbX + 2, sbY + sbH, 0xFF557799);
        }
    }

    private void renderMainContent(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        // Row 1: Category Filter Tabs (Left) + Dual Hatch Toggle (Right)
        int row1Y = y + 1;
        String[] catLabels = {
            Component.translatable("gui.gtcalcboard.bom.filter.all").getString(),
            Component.translatable("gui.gtcalcboard.bom.filter.casings").getString(),
            Component.translatable("gui.gtcalcboard.bom.filter.coils").getString(),
            Component.translatable("gui.gtcalcboard.bom.filter.hatches").getString(),
            Component.translatable("gui.gtcalcboard.bom.filter.controllers").getString()
        };

        int tabX = x;
        for (int i = 0; i < catLabels.length; i++) {
            String label = catLabels[i];
            int tabW = font.width(label) + 8;
            boolean active = filterCategoryIndex == i;
            boolean tabHover = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= row1Y && mouseY <= row1Y + 14;

            graphics.fill(tabX, row1Y, tabX + tabW, row1Y + 14, active ? 0xFF2A4263 : (tabHover ? 0xFF202C3E : 0xFF141C28));
            graphics.renderOutline(tabX, row1Y, tabW, 14, active ? 0xFF55AAFF : 0xFF35445E);
            graphics.drawCenteredString(font, (active ? "§e" : (tabHover ? "§f" : "§7")) + label, tabX + tabW / 2, row1Y + 3, 0xFFFFFFFF);

            tabX += tabW + 2;
        }

        // Dual Hatch Toggle Button (Right-aligned in Row 1)
        String dualStr = dualLowerTierEnergyHatches
            ? Component.translatable("gui.gtcalcboard.bom.dual_hatch_2x").getString()
            : Component.translatable("gui.gtcalcboard.bom.dual_hatch_1x").getString();
        int dualW = font.width(dualStr) + 8;
        int dualX = x + w - dualW - 2;
        boolean dualHover = mouseX >= dualX && mouseX <= dualX + dualW && mouseY >= row1Y && mouseY <= row1Y + 14;

        graphics.fill(dualX, row1Y, dualX + dualW, row1Y + 14, dualLowerTierEnergyHatches ? 0xFF553820 : (dualHover ? 0xFF283446 : 0xFF182230));
        graphics.renderOutline(dualX, row1Y, dualW, 14, dualLowerTierEnergyHatches ? 0xFFFFAA00 : 0xFF3A4B62);
        graphics.drawCenteredString(font, (dualLowerTierEnergyHatches ? "§6⚡ " : "§7⚡ ") + dualStr, dualX + dualW / 2, row1Y + 3, 0xFFFFFFFF);

        // Row 2: Search Box + Clear Button + Item Count Info
        int row2Y = row1Y + 17;
        if (searchBox != null) {
            searchBox.setX(x);
            searchBox.setY(row2Y);
            searchBox.setWidth(120);
            searchBox.setHeight(14);
            searchBox.render(graphics, mouseX, mouseY, 0);

            if (!searchQuery.isEmpty()) {
                int clearBtnX = x + 106;
                int clearBtnY = row2Y + 2;
                boolean clearHover = mouseX >= clearBtnX && mouseX <= clearBtnX + 10 && mouseY >= clearBtnY && mouseY <= clearBtnY + 10;
                graphics.drawString(font, clearHover ? "§c✕" : "§7✕", clearBtnX, clearBtnY + 1, 0xFFFFFFFF, false);
            }
        }

        // Filter items
        List<MultiblockBOMSummary.BOMItemEntry> filtered = new ArrayList<>();
        for (MultiblockBOMSummary.BOMItemEntry entry : cachedSummary.aggregatedItems()) {
            if (filterCategoryIndex == 1 && entry.category() != PartCategory.CASING) continue;
            if (filterCategoryIndex == 2 && entry.category() != PartCategory.COIL) continue;
            if (filterCategoryIndex == 3 && entry.category() != PartCategory.HATCH_BUS) continue;
            if (filterCategoryIndex == 4 && entry.category() != PartCategory.CONTROLLER) continue;

            if (!searchQuery.isEmpty()) {
                String name = entry.displayName().toLowerCase(Locale.ROOT);
                String id = entry.itemId() != null ? entry.itemId().toString().toLowerCase(Locale.ROOT) : "";
                if (!name.contains(searchQuery) && !id.contains(searchQuery)) {
                    continue;
                }
            }
            filtered.add(entry);
        }

        String filterCountStr = String.format(Locale.ROOT, "§8(%d / %d items)", filtered.size(), cachedSummary.totalUniqueItemTypes());
        graphics.drawString(font, filterCountStr, x + 126, row2Y + 3, 0xFF888888, false);

        // Row 3: Table Header Row
        int headerY = row2Y + 17;
        int headerH = 13;
        graphics.fill(x, headerY, x + w, headerY + headerH, 0xCC1A2332);
        graphics.renderOutline(x, headerY, w, headerH, 0xFF2A364D);

        graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.bom.col.item").getString(), x + 20, headerY + 3, 0xFFFFFFFF, false);
        graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.bom.col.required").getString(), x + 165, headerY + 3, 0xFFFFFFFF, false);
        graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.bom.col.stacks").getString(), x + 215, headerY + 3, 0xFFFFFFFF, false);
        graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.bom.col.used_in").getString(), x + 300, headerY + 3, 0xFFFFFFFF, false);

        // Row 4: Scrollable Table Body
        int tableY = headerY + headerH + 2;
        int tableH = h - (tableY - y) - 2;
        int rowH = 18;
        int totalTableH = filtered.size() * rowH;
        maxItemScrollY = Math.max(0, totalTableH - tableH);

        BoardScissorHelper.enableScissor(graphics, x, tableY, x + w, tableY + tableH);

        int curY = (int) (tableY - itemScrollY);
        for (MultiblockBOMSummary.BOMItemEntry entry : filtered) {
            if (curY + rowH >= tableY && curY <= tableY + tableH) {
                boolean prepared = preparedItemIds.contains(entry.itemId());
                boolean rowHover = mouseX >= x && mouseX <= x + w - 4 && mouseY >= curY && mouseY <= curY + rowH;

                int rowBg = prepared ? 0x22114422 : (rowHover ? 0x3335445E : ((curY / rowH) % 2 == 0 ? 0x181A2434 : 0x0A141A26));
                graphics.fill(x, curY, x + w, curY + rowH, rowBg);

                // Prepared Checkbox
                int chkX = x + 3;
                int chkY = curY + 3;
                graphics.fill(chkX, chkY, chkX + 11, chkY + 11, 0xFF141A24);
                graphics.renderOutline(chkX, chkY, 11, 11, prepared ? 0xFF55FF55 : 0xFF35445E);
                if (prepared) {
                    graphics.drawString(font, "✔", chkX + 2, chkY + 2, 0xFF55FF55, false);
                }

                // Item Icon (Rendered via Minecraft GuiGraphics)
                int iconX = x + 17;
                int iconY = curY + 1;
                ItemStack is = entry.resolveItemStack();
                if (!is.isEmpty()) {
                    graphics.renderItem(is, iconX, iconY);
                    if (is.getCount() > 1) {
                        graphics.renderItemDecorations(font, is, iconX, iconY);
                    }
                }

                // Item Name
                String nameStr = entry.displayName();
                String clippedName = font.plainSubstrByWidth(nameStr, 125);
                int nameColor = prepared ? 0xFF779977 : 0xFFFFFFFF;
                graphics.drawString(font, (prepared ? "§m" : "") + clippedName, iconX + 18, curY + 5, nameColor, false);

                // Total Required
                String reqStr = String.format(Locale.ROOT, "%,d", entry.totalAmount());
                graphics.drawString(font, (prepared ? "§m" : "§e") + reqStr, x + 165, curY + 5, 0xFFFFAA00, false);

                // Stack Breakdown
                String stackStr = entry.formatStackCount();
                String clippedStack = font.plainSubstrByWidth(stackStr, 78);
                graphics.drawString(font, (prepared ? "§m§8" : "§b") + clippedStack, x + 215, curY + 5, 0xFF66DDFF, false);

                // Used In Summary
                String usedStr = String.join(", ", entry.usedByMachines());
                String clippedUsed = font.plainSubstrByWidth(usedStr, w - 305);
                graphics.drawString(font, (prepared ? "§m§8" : "§7") + clippedUsed, x + 300, curY + 5, 0xFFAAAAAA, false);

                if (rowHover) {
                    hoveredEntry = entry;
                }
            }
            curY += rowH;
        }

        BoardScissorHelper.disableScissor(graphics);

        // Main Table Scrollbar
        if (maxItemScrollY > 0) {
            int sbX = x + w - 3;
            int sbH = Math.max(10, (int) ((double) tableH / totalTableH * tableH));
            int sbY = tableY + (int) (itemScrollY / maxItemScrollY * (tableH - sbH));
            graphics.fill(sbX, tableY, sbX + 2, tableY + tableH, 0x44000000);
            graphics.fill(sbX, sbY, sbX + 2, sbY + sbH, 0xFF557799);
        }
    }

    private void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (hoveredEntry != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal("§6" + hoveredEntry.displayName()));
            tip.add(Component.literal(String.format(Locale.ROOT, "§eTotal Required: §f%,d blocks", hoveredEntry.totalAmount())));
            tip.add(Component.literal("§bStack Breakdown: §f" + hoveredEntry.formatStackCount()));
            tip.add(Component.literal("§7Category: §f" + hoveredEntry.category().name()));
            tip.add(Component.empty());
            tip.add(Component.literal("§aUsed by Multiblocks:"));
            for (String usage : hoveredEntry.usedByMachines()) {
                tip.add(Component.literal(" • §f" + usage));
            }
            tip.add(Component.empty());
            tip.add(Component.literal("§8[Click to toggle prepared checklist]"));
            int screenW = parent != null && parent.width > 0 ? parent.width : Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int screenH = parent != null && parent.height > 0 ? parent.height : Minecraft.getInstance().getWindow().getGuiScaledHeight();
            BoardTooltipRenderer.renderComponentTooltip(graphics, font, tip, mouseX, mouseY, screenW, screenH);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        int dialogW = Math.min(DIALOG_WIDTH, parent.width - 16);
        int dialogH = Math.min(DIALOG_HEIGHT, parent.height - 16);
        int dialogX = (parent.width - dialogW) / 2;
        int dialogY = (parent.height - dialogH) / 2;

        // Close Button [✕]
        int closeBtnX = dialogX + dialogW - 20;
        int closeBtnY = dialogY + 4;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 16 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16) {
            playClickSound();
            close();
            return true;
        }

        // Click outside dialog closes it
        if (mouseX < dialogX || mouseX > dialogX + dialogW || mouseY < dialogY || mouseY > dialogY + dialogH) {
            playClickSound();
            close();
            return true;
        }

        // Action Buttons: [📋 Copy] and [⭐ Register in EMI]
        int bannerY = dialogY + 25;
        Font font = Minecraft.getInstance().font;

        int copyBtnW = font.width(Component.translatable("gui.gtcalcboard.bom.copy_clipboard").getString()) + 14;
        int copyBtnX = dialogX + dialogW - 10 - copyBtnW;
        int copyBtnY = bannerY + 3;
        if (mouseX >= copyBtnX && mouseX <= copyBtnX + copyBtnW && mouseY >= copyBtnY && mouseY <= copyBtnY + 16) {
            playClickSound();
            copyToClipboard();
            return true;
        }

        var activeViewer = com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter();
        if (activeViewer.isBoMGoalRegistrationSupported()) {
            boolean isEmi = "emi".equals(activeViewer.getViewerId());
            String regKey = isEmi ? "gui.gtcalcboard.bom.register_emi" : "gui.gtcalcboard.bom.register_jei";
            int regBtnW = font.width(Component.translatable(regKey).getString()) + 14;
            int regBtnX = copyBtnX - regBtnW - 6;
            if (mouseX >= regBtnX && mouseX <= regBtnX + regBtnW && mouseY >= copyBtnY && mouseY <= copyBtnY + 16) {
                playClickSound();
                registerToBoMGoal();
                return true;
            }
        }

        // Sidebar Actions
        int sidebarX = dialogX + 6;
        int sidebarY = bannerY + 22 + 4;
        int sidebarH = dialogH - (sidebarY - dialogY) - 6;

        int btnH = 14;
        int btnY = sidebarY + sidebarH - btnH - 3;
        int halfW = (SIDEBAR_WIDTH - 8) / 2;

        // [All]
        int allBtnX = sidebarX + 3;
        if (mouseX >= allBtnX && mouseX <= allBtnX + halfW && mouseY >= btnY && mouseY <= btnY + btnH) {
            playClickSound();
            selectedPageIds.clear();
            for (BoardPage p : BoardManager.getInstance().getPages()) {
                selectedPageIds.add(p.getId());
            }
            dirty = true;
            return true;
        }

        // [None]
        int noneBtnX = allBtnX + halfW + 2;
        if (mouseX >= noneBtnX && mouseX <= noneBtnX + halfW && mouseY >= btnY && mouseY <= btnY + btnH) {
            playClickSound();
            selectedPageIds.clear();
            dirty = true;
            return true;
        }

        // Sidebar page toggles
        int listY = sidebarY + 16 + 2;
        int listH = btnY - listY - 2;
        if (mouseX >= sidebarX && mouseX <= sidebarX + SIDEBAR_WIDTH && mouseY >= listY && mouseY <= listY + listH) {
            List<BoardPage> pages = BoardManager.getInstance().getPages();
            int rowH = 16;
            int clickedIdx = (int) ((mouseY - listY + pageScrollY) / rowH);
            if (clickedIdx >= 0 && clickedIdx < pages.size()) {
                playClickSound();
                String pid = pages.get(clickedIdx).getId();
                if (selectedPageIds.contains(pid)) {
                    selectedPageIds.remove(pid);
                } else {
                    selectedPageIds.add(pid);
                }
                dirty = true;
                return true;
            }
        }

        // Main Content Row 1: Filter tabs and Dual Hatch Toggle
        int mainX = dialogX + SIDEBAR_WIDTH + 14;
        int mainY = sidebarY;
        int mainW = dialogW - (mainX - dialogX) - 8;
        int row1Y = mainY + 1;

        String[] catLabels = {
            Component.translatable("gui.gtcalcboard.bom.filter.all").getString(),
            Component.translatable("gui.gtcalcboard.bom.filter.casings").getString(),
            Component.translatable("gui.gtcalcboard.bom.filter.coils").getString(),
            Component.translatable("gui.gtcalcboard.bom.filter.hatches").getString(),
            Component.translatable("gui.gtcalcboard.bom.filter.controllers").getString()
        };

        int tabX = mainX;
        for (int i = 0; i < catLabels.length; i++) {
            String label = catLabels[i];
            int tabW = font.width(label) + 8;
            if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= row1Y && mouseY <= row1Y + 14) {
                playClickSound();
                filterCategoryIndex = i;
                itemScrollY = 0.0;
                return true;
            }
            tabX += tabW + 2;
        }

        // Dual Hatch Toggle Click
        String dualStr = dualLowerTierEnergyHatches
            ? Component.translatable("gui.gtcalcboard.bom.dual_hatch_2x").getString()
            : Component.translatable("gui.gtcalcboard.bom.dual_hatch_1x").getString();
        int dualW = font.width(dualStr) + 8;
        int dualX = mainX + mainW - dualW - 2;
        if (mouseX >= dualX && mouseX <= dualX + dualW && mouseY >= row1Y && mouseY <= row1Y + 14) {
            playClickSound();
            dualLowerTierEnergyHatches = !dualLowerTierEnergyHatches;
            dirty = true;
            return true;
        }

        // Search Box click
        int row2Y = row1Y + 17;
        if (searchBox != null) {
            searchBox.setX(mainX);
            searchBox.setY(row2Y);
            searchBox.setWidth(120);
            searchBox.setHeight(14);

            // Clear button click [✕]
            if (!searchQuery.isEmpty()) {
                int clearBtnX = mainX + 106;
                int clearBtnY = row2Y + 2;
                if (mouseX >= clearBtnX && mouseX <= clearBtnX + 10 && mouseY >= clearBtnY && mouseY <= clearBtnY + 10) {
                    playClickSound();
                    searchBox.setValue("");
                    searchBox.setFocused(true);
                    return true;
                }
            }

            boolean inSearchBox = mouseX >= mainX && mouseX <= mainX + 120 && mouseY >= row2Y && mouseY <= row2Y + 14;
            searchBox.setFocused(inSearchBox);
            if (inSearchBox) {
                searchBox.mouseClicked(mouseX, mouseY, button);
                return true;
            }
        }

        // Table Rows (Prepared Checklist Toggle)
        int headerY = row2Y + 17;
        int tableY = headerY + 13 + 2;
        int tableH = sidebarH - (tableY - mainY) - 2;

        if (mouseX >= mainX && mouseX <= mainX + mainW && mouseY >= tableY && mouseY <= tableY + tableH) {
            List<MultiblockBOMSummary.BOMItemEntry> filtered = new ArrayList<>();
            for (MultiblockBOMSummary.BOMItemEntry entry : cachedSummary.aggregatedItems()) {
                if (filterCategoryIndex == 1 && entry.category() != PartCategory.CASING) continue;
                if (filterCategoryIndex == 2 && entry.category() != PartCategory.COIL) continue;
                if (filterCategoryIndex == 3 && entry.category() != PartCategory.HATCH_BUS) continue;
                if (filterCategoryIndex == 4 && entry.category() != PartCategory.CONTROLLER) continue;

                if (!searchQuery.isEmpty()) {
                    String name = entry.displayName().toLowerCase(Locale.ROOT);
                    String id = entry.itemId() != null ? entry.itemId().toString().toLowerCase(Locale.ROOT) : "";
                    if (!name.contains(searchQuery) && !id.contains(searchQuery)) {
                        continue;
                    }
                }
                filtered.add(entry);
            }

            int rowH = 18;
            int clickedIdx = (int) ((mouseY - tableY + itemScrollY) / rowH);
            if (clickedIdx >= 0 && clickedIdx < filtered.size()) {
                playClickSound();
                ResourceLocation itemId = filtered.get(clickedIdx).itemId();
                if (preparedItemIds.contains(itemId)) {
                    preparedItemIds.remove(itemId);
                } else {
                    preparedItemIds.add(itemId);
                }
                return true;
            }
        }

        // Consume click inside dialog
        if (mouseX >= dialogX && mouseX <= dialogX + dialogW && mouseY >= dialogY && mouseY <= dialogY + dialogH) {
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;

        int dialogW = Math.min(DIALOG_WIDTH, parent.width - 16);
        int dialogH = Math.min(DIALOG_HEIGHT, parent.height - 16);
        int dialogX = (parent.width - dialogW) / 2;
        int dialogY = (parent.height - dialogH) / 2;

        int sidebarX = dialogX + 6;
        if (mouseX >= sidebarX && mouseX <= sidebarX + SIDEBAR_WIDTH) {
            pageScrollY = Math.max(0, Math.min(maxPageScrollY, pageScrollY - delta * 16));
            return true;
        }

        int mainX = dialogX + SIDEBAR_WIDTH + 14;
        if (mouseX >= mainX && mouseX <= dialogX + dialogW) {
            itemScrollY = Math.max(0, Math.min(maxItemScrollY, itemScrollY - delta * 18));
            return true;
        }

        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (searchBox != null && searchBox.isFocused()) {
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (searchBox != null) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return false;
    }

    private void copyToClipboard() {
        if (cachedSummary == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("# 📋 Multiblock Construction Bill of Materials (BOM)\n");
        sb.append(String.format(Locale.ROOT, "Total Multiblocks: %d | Unique Blocks: %d\n\n", cachedSummary.totalMultiblockCount(), cachedSummary.totalUniqueItemTypes()));
        sb.append("| Block Name | Total Required | Stacks | Used In |\n");
        sb.append("| :--- | :--- | :--- | :--- |\n");
        for (MultiblockBOMSummary.BOMItemEntry item : cachedSummary.aggregatedItems()) {
            sb.append(String.format(Locale.ROOT, "| %s | %d | %s | %s |\n",
                item.displayName(),
                item.totalAmount(),
                item.formatStackCount(),
                String.join(", ", item.usedByMachines())
            ));
        }

        Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                Component.translatable("message.gtcalcboard.bom_copied"),
                true
            );
        }
    }

    private void registerToBoMGoal() {
        if (cachedSummary == null) return;
        com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter().registerBoMGoal(cachedSummary);
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }
}




