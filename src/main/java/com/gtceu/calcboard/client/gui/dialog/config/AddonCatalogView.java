/*
 * Decompiled with CFR 0.152.
 */
package com.gtceu.calcboard.client.gui.dialog.config;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

public class AddonCatalogView {
    private final MachineConfigDialog dialog;
    private EditBox searchBox;
    private int catalogScroll = 0;
    private double categoryScrollX = 0.0;
    private double maxCategoryScrollX = 0.0;
    private List<MachineAddon> cachedFilteredCatalog = null;
    private List<AddonCategory> cachedFilterCategories = null;

    public AddonCatalogView(MachineConfigDialog dialog) {
        this.dialog = dialog;
    }

    public void init() {
        Minecraft mc = Minecraft.getInstance();
        this.searchBox = new EditBox(mc.font, 0, 0, 160, 14, Component.translatable((String)"gui.gtcalcboard.config.search_hint"));
        this.searchBox.setMaxLength(256);
        this.searchBox.setHint(Component.literal((String)"\u00a78").append(Component.translatable((String)"gui.gtcalcboard.config.search_hint")));
        this.searchBox.setValue("");
        this.searchBox.setResponder(text -> {
            this.catalogScroll = 0;
            this.invalidateCache();
        });
        this.catalogScroll = 0;
        this.categoryScrollX = 0.0;
        this.invalidateCache();
    }

    public EditBox getSearchBox() {
        return this.searchBox;
    }

    public int getCatalogScroll() {
        return this.catalogScroll;
    }

    public void setCatalogScroll(int catalogScroll) {
        this.catalogScroll = catalogScroll;
    }

    public double getCategoryScrollX() {
        return this.categoryScrollX;
    }

    public void setCategoryScrollX(double categoryScrollX) {
        this.categoryScrollX = categoryScrollX;
    }

    public void invalidateCache() {
        this.cachedFilteredCatalog = null;
        this.cachedFilterCategories = null;
    }

    public void ensureCategoryVisible(RecipeNode node, AddonCategory targetCat, int dialogW) {
        int targetIdx;
        if (node == null) {
            return;
        }
        List<AddonCategory> allCats = this.getAllCategoriesForFilter(node);
        int n = targetIdx = targetCat != null && targetCat.equals(AddonCategory.CUSTOM) ? allCats.size() - 1 : allCats.indexOf(targetCat);
        if (targetIdx < 0) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int chipLeft = 0;
        for (int i = 0; i < targetIdx; ++i) {
            chipLeft += font.width(this.getCategoryLabel(allCats.get(i))) + 12 + 4;
        }
        int chipW = font.width(this.getCategoryLabel(allCats.get(targetIdx))) + 12;
        int availW = dialogW - 20;
        if ((double)chipLeft < this.categoryScrollX) {
            this.categoryScrollX = chipLeft;
        } else if ((double)(chipLeft + chipW) > this.categoryScrollX + (double)availW) {
            this.categoryScrollX = chipLeft + chipW - availW;
        }
    }

    public List<AddonCategory> getAllCategoriesForFilter(RecipeNode node) {
        if (this.cachedFilterCategories != null) {
            return this.cachedFilterCategories;
        }
        ArrayList<AddonCategory> list = new ArrayList<AddonCategory>();
        list.add(null);
        List<AddonCategory> relCats = MachineAddon.getRelevantCategories(node);
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        List<MachineAddon> allAddons = MachineAddonCatalog.getInstance().getAllAddons();
        HashSet<AddonCategory> activeCategories = new HashSet<AddonCategory>();
        if (adapter != null) {
            for (MachineAddon r : adapter.getResetAddonCards(node)) {
                if (r == null || !adapter.isAddonCompatible(node, r)) continue;
                activeCategories.add(r.getCategory());
            }
        }
        for (MachineAddon a : allAddons) {
            if (a == null || !a.isCompatibleWith(node)) continue;
            activeCategories.add(a.getCategory());
        }
        for (AddonCategory cat : relCats) {
            if (cat == null || cat.equals(AddonCategory.CUSTOM) || list.contains(cat) || !cat.equals(AddonCategory.THREADING) && !activeCategories.contains(cat)) continue;
            list.add(cat);
        }
        list.add(AddonCategory.CUSTOM);
        this.cachedFilterCategories = list;
        return list;
    }

    public String getCategoryLabel(AddonCategory cat) {
        if (cat == null) {
            return Component.translatable((String)"gui.gtcalcboard.addon_cat.all").getString();
        }
        return Component.translatable((String)cat.getTranslatableKey()).getString();
    }

    public void renderCategoryFilterChips(GuiGraphics graphics, Font font, RecipeNode node, int startX, int startY, int dialogW, int mouseX, int mouseY) {
        List<AddonCategory> allCats = this.getAllCategoriesForFilter(node);
        int totalCats = allCats.size();
        int availW = dialogW - 20;
        int scrollAreaX = startX;
        int scrollAreaW = availW;
        int totalWidth = 0;
        ArrayList<Integer> chipWidths = new ArrayList<Integer>();
        for (AddonCategory cat : allCats) {
            int w = font.width(this.getCategoryLabel(cat)) + 12;
            chipWidths.add(w);
            totalWidth += w + 4;
        }
        totalWidth = Math.max(0, totalWidth - 4);
        this.maxCategoryScrollX = Math.max(0, totalWidth - scrollAreaW);
        this.categoryScrollX = Math.max(0.0, Math.min(this.maxCategoryScrollX, this.categoryScrollX));
        this.dialog.enableScaledScissor(graphics, scrollAreaX, startY - 1, scrollAreaX + scrollAreaW, startY + 17);
        int cx = scrollAreaX - (int)this.categoryScrollX;
        for (int i = 0; i < totalCats; ++i) {
            AddonCategory cat = allCats.get(i);
            int bw = (Integer)chipWidths.get(i);
            boolean active = this.dialog.isCustomBuilderActive() ? cat != null && cat.equals(AddonCategory.CUSTOM) : (this.dialog.getSelectedCategory() == null && cat == null || this.dialog.getSelectedCategory() != null && this.dialog.getSelectedCategory().equals(cat));
            if (cx + bw >= scrollAreaX && cx <= scrollAreaX + scrollAreaW) {
                this.renderChip(graphics, font, this.getCategoryLabel(cat), active, cx, startY, bw, mouseX, mouseY, scrollAreaX, scrollAreaX + scrollAreaW);
            }
            cx += bw + 4;
        }
        graphics.disableScissor();
        if (this.maxCategoryScrollX > 0.0) {
            if (this.categoryScrollX > 2.0) {
                graphics.drawString(font, "\u00ab", scrollAreaX - 6, startY + 4, -22016, false);
            }
            if (this.categoryScrollX < this.maxCategoryScrollX - 2.0) {
                graphics.drawString(font, "\u00bb", scrollAreaX + scrollAreaW - 6, startY + 4, -22016, false);
            }
        }
    }

    private void renderChip(GuiGraphics graphics, Font font, String label, boolean active, int bx, int by, int bw, int mouseX, int mouseY, int clipMinX, int clipMaxX) {
        boolean hover;
        boolean bl = hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 16 && mouseX >= clipMinX && mouseX <= clipMaxX;
        graphics.fill(bx, by, bx + bw, by + 16, active ? -15000024 : (hover ? -13748924 : -14538957));
        graphics.renderOutline(bx, by, bw, 16, active ? -10955777 : -13419960);
        graphics.drawCenteredString(font, label, bx + bw / 2, by + 4, active ? -10955777 : -6511176);
    }

    public void renderIndexerStatusPill(GuiGraphics graphics, Font font, int rightX, int y, int mouseX, int mouseY) {
        MachineAddonCatalog catalog = MachineAddonCatalog.getInstance();
        boolean running = catalog.isExhaustiveScanRunning();
        boolean complete = catalog.isExhaustiveScanComplete();
        if (!running && !complete) {
            return;
        }
        int pct = (int)Math.round(catalog.getExhaustiveProgress() * 100.0);
        String pillText = running ? "\u00a7e\u23f3 " + Component.translatable((String)"gui.gtcalcboard.catalog.deep_scan_running", (Object[])new Object[]{String.valueOf(pct)}).getString() : "\u00a7a\u2714 " + Component.translatable((String)"gui.gtcalcboard.catalog.deep_scan_complete").getString();
        int pillW = font.width(font.plainSubstrByWidth(pillText, 200)) + 12;
        int pillX = rightX - pillW;
        boolean hover = mouseX >= pillX && mouseX <= rightX && mouseY >= y && mouseY <= y + 14;
        int bg = running ? (hover ? -13752296 : -14541292) : (hover ? -15193570 : -15458280);
        int border = running ? (hover ? -2047936 : -7703776) : (hover ? -12210060 : -13799863);
        graphics.fill(pillX, y, rightX, y + 14, bg);
        graphics.renderOutline(pillX, y, pillW, 14, border);
        graphics.drawCenteredString(font, pillText, pillX + pillW / 2, y + 3, -1);
        if (hover) {
            ArrayList<Component> tooltip = new ArrayList<Component>();
            tooltip.add(Component.literal((String)("\u00a7e\u26a1 " + Component.translatable((String)"gui.gtcalcboard.catalog.deep_scan_tooltip_title").getString())));
            tooltip.add(Component.literal((String)Component.translatable((String)"gui.gtcalcboard.catalog.deep_scan_tooltip_track1").getString()));
            if (running) {
                tooltip.add(Component.literal((String)Component.translatable((String)"gui.gtcalcboard.catalog.deep_scan_tooltip_track2_running", (Object[])new Object[]{String.valueOf(pct)}).getString()));
            } else {
                tooltip.add(Component.literal((String)Component.translatable((String)"gui.gtcalcboard.catalog.deep_scan_tooltip_track2_complete").getString()));
            }
            this.dialog.setDeferredTooltip(tooltip);
        }
    }

    public List<MachineAddon> getFilteredCatalog(RecipeNode node) {
        if (this.cachedFilteredCatalog != null) {
            return this.cachedFilteredCatalog;
        }
        List<MachineAddon> list = MachineAddonCatalog.getInstance().getAllAddons();
        String q = this.searchBox != null ? this.searchBox.getValue().toLowerCase().trim() : "";
        String qClean = q.replace('_', ' ').trim();
        String qUnder = q.replace(' ', '_').trim();
        ArrayList<MachineAddon> filtered = new ArrayList<MachineAddon>();
        AddonCategory selCat = this.dialog.getSelectedCategory();
        List<AddonCategory> rel = selCat == null ? MachineAddon.getRelevantCategories(node) : null;
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null) {
            List<MachineAddon> resetCards = adapter.getResetAddonCards(node);
            for (MachineAddon resetCard : resetCards) {
                if (!adapter.isAddonCompatible(node, resetCard) || selCat != null && !resetCard.getCategory().equals(selCat) || selCat == null && rel != null && !rel.contains(resetCard.getCategory()) || !q.isEmpty() && !resetCard.getName().toLowerCase().contains(q) && !"reset".contains(q) && !"standard".contains(q) && !"\uae30\ubcf8".contains(q) && !"none".contains(q)) continue;
                filtered.add(resetCard);
            }
        }
        for (MachineAddon addon : list) {
            if (addon == null || selCat != null && !addon.getCategory().equals(selCat) || selCat == null && rel != null && !rel.contains(addon.getCategory()) || !addon.isCompatibleWith(node)) continue;
            if (!q.isEmpty()) {
                String idStr;
                String n = addon.getName().toLowerCase();
                String d = addon.getDescription() != null ? addon.getDescription().toLowerCase() : "";
                String string = idStr = addon.getId() != null ? addon.getId().toString().toLowerCase() : "";
                if (!n.contains(q) && !n.contains(qClean) && !n.contains(qUnder) && !d.contains(q) && !d.contains(qClean) && !d.contains(qUnder) && !idStr.contains(q) && !idStr.contains(qClean) && !idStr.contains(qUnder)) continue;
            }
            filtered.add(addon);
        }
        filtered.sort((a, b) -> {
            if (a.getParallelMultiplier() != b.getParallelMultiplier()) {
                return Integer.compare(a.getParallelMultiplier(), b.getParallelMultiplier());
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });
        this.cachedFilteredCatalog = filtered;
        return filtered;
    }

    public void renderCatalogGrid(GuiGraphics graphics, Font font, RecipeNode node, int startX, int startY, int width, int height, int mouseX, int mouseY) {
        int cardIndex;
        if (!(node.hasMultiblockOption() || ModAdapterRegistry.getAdapterForNode(node).supportsAddons(node) || this.dialog.isCustomBuilderActive())) {
            int bannerY = startY + 12;
            graphics.drawCenteredString(font, Component.translatable((String)"gui.gtcalcboard.config.singleblock_no_addons").getString(), startX + width / 2, bannerY, -5592406);
            graphics.drawCenteredString(font, Component.translatable((String)"gui.gtcalcboard.config.singleblock_custom_hint").getString(), startX + width / 2, bannerY + 16, -7829368);
            return;
        }
        List<MachineAddon> filtered = this.getFilteredCatalog(node);
        int totalCards = filtered.size();
        int cols = 3;
        int maxRows = (int)Math.ceil((double)totalCards / (double)cols);
        int visibleRows = 2;
        int cardsPerPage = cols * visibleRows;
        int maxScroll = Math.max(0, maxRows - visibleRows);
        if (this.catalogScroll > maxScroll) {
            this.catalogScroll = maxScroll;
        }
        int totalPages = Math.max(1, (int)Math.ceil((double)totalCards / (double)cardsPerPage));
        int currentPage = this.catalogScroll / visibleRows + 1;
        int navW = totalPages > 1 ? 76 : 0;
        int pillSpace = 100;
        int searchW = width - pillSpace - navW - 12;
        if (this.searchBox != null) {
            this.searchBox.setX(startX + 2);
            this.searchBox.setY(startY);
            this.searchBox.setWidth(searchW);
            this.searchBox.render(graphics, mouseX, mouseY, 0.0f);
            if (!this.searchBox.getValue().isEmpty()) {
                int clearBtnX = startX + searchW - 14;
                int clearBtnY = startY + 2;
                boolean clearHover = mouseX >= clearBtnX && mouseX <= clearBtnX + 12 && mouseY >= clearBtnY && mouseY <= clearBtnY + 12;
                graphics.fill(clearBtnX, clearBtnY, clearBtnX + 12, clearBtnY + 12, clearHover ? -8969694 : -12771296);
                graphics.renderOutline(clearBtnX, clearBtnY, 12, 12, clearHover ? -6278349 : -11194320);
                graphics.drawCenteredString(font, "\u2715", clearBtnX + 6, clearBtnY + 2, -1);
            }
        }
        if (totalPages > 1) {
            int navX = startX + searchW + 6;
            boolean prevHov = mouseX >= navX && mouseX <= navX + 14 && mouseY >= startY && mouseY <= startY + 14;
            graphics.fill(navX, startY, navX + 14, startY + 14, prevHov ? -12761768 : -14538957);
            graphics.renderOutline(navX, startY, 14, 14, prevHov ? -10955777 : -13419960);
            graphics.drawCenteredString(font, "\u25c0", navX + 7, startY + 3, this.catalogScroll > 0 ? -1 : -10066330);
            String pageText = currentPage + "/" + totalPages;
            graphics.drawCenteredString(font, "\u00a77" + pageText, navX + 38, startY + 3, -2039584);
            boolean nextHov = mouseX >= navX + 62 && mouseX <= navX + 76 && mouseY >= startY && mouseY <= startY + 14;
            graphics.fill(navX + 62, startY, navX + 76, startY + 14, nextHov ? -12761768 : -14538957);
            graphics.renderOutline(navX + 62, startY, 14, 14, nextHov ? -10955777 : -13419960);
            graphics.drawCenteredString(font, "\u25b6", navX + 69, startY + 3, this.catalogScroll < maxScroll ? -1 : -10066330);
        }
        this.renderIndexerStatusPill(graphics, font, startX + width - 2, startY, mouseX, mouseY);
        int gridStartY = startY + 18;
        int scrollbarW = maxScroll > 0 ? 6 : 0;
        int gridW = width - scrollbarW - 2;
        int cardW = (gridW - (cols - 1) * 4) / cols;
        int cardH = 50;
        if (maxScroll > 0) {
            int sbX = startX + width - 5;
            int sbY = gridStartY;
            int sbH = visibleRows * (cardH + 4) - 4;
            graphics.fill(sbX, sbY, sbX + 4, sbY + sbH, -15460576);
            graphics.renderOutline(sbX, sbY, 4, sbH, -14012096);
            float thumbRatio = (float)visibleRows / (float)maxRows;
            int thumbH = Math.max(14, (int)((float)sbH * thumbRatio));
            int thumbY = sbY + (int)((float)(sbH - thumbH) * ((float)this.catalogScroll / (float)maxScroll));
            graphics.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, -11905685);
            graphics.renderOutline(sbX, thumbY, 4, thumbH, -10127982);
        }
        if (filtered.isEmpty()) {
            boolean isCatalogEmpty = MachineAddonCatalog.getInstance().getAllAddons().isEmpty();
            if (isCatalogEmpty && !MachineAddonCatalog.getInstance().isReady()) {
                if (!MachineAddonCatalog.getInstance().isLoading()) {
                    MachineAddonCatalog.getInstance().preloadAsync();
                }
                double prog = MachineAddonCatalog.getInstance().getExhaustiveProgress();
                int pct = (int)(prog * 100.0);
                String msg = "\u00a7e\u23f3 " + Component.translatable((String)"gui.gtcalcboard.loading_addons").getString() + " (" + pct + "%)";
                int centerY = gridStartY + visibleRows * cardH / 2;
                graphics.drawCenteredString(font, msg, startX + width / 2, centerY - 14, -2047936);
                int barW = Math.min(220, width - 40);
                int barH = 6;
                int barX = startX + width / 2 - barW / 2;
                int barY = centerY + 2;
                graphics.fill(barX, barY, barX + barW, barY + barH, -14538957);
                graphics.renderOutline(barX, barY, barW, barH, -12761511);
                float fillRatio = Math.max(0.05f, (float)prog);
                int fillW = (int)((float)barW * fillRatio);
                graphics.fill(barX + 1, barY + 1, barX + fillW - 1, barY + barH - 1, -11890462);
                return;
            }
            String msg = "\u00a78" + Component.translatable((String)"gui.gtcalcboard.search.no_results").getString();
            graphics.drawCenteredString(font, msg, startX + width / 2, gridStartY + 24, -7829368);
            return;
        }
        MachineAddon hoveredAddon = null;
        for (int i = 0; i < cols * visibleRows && (cardIndex = this.catalogScroll * cols + i) < totalCards; ++i) {
            int col = i % cols;
            int row = i / cols;
            int bx = startX + col * (cardW + 4);
            int by = gridStartY + row * (cardH + 4);
            boolean hover = mouseX >= bx && mouseX <= bx + cardW && mouseY >= by && mouseY <= by + cardH;
            MachineAddon addon = filtered.get(cardIndex);
            boolean isResetCard = "gtceu:standard_rotor".equals(addon.getId()) || "gtceu:reflector_none".equals(addon.getId());
            int installedCount = (int)node.getAddons().stream().filter(a -> a.getId().equals(addon.getId())).count();
            boolean isInstalled = !isResetCard && installedCount > 0;
            if (isResetCard) {
                if ("gtceu:standard_rotor".equals(addon.getId()) && node.getAddons().stream().noneMatch(a -> a.getCategory() == MachineAddon.Category.ROTOR)) {
                    isInstalled = true;
                } else if ("gtceu:reflector_none".equals(addon.getId()) && node.getAddons().stream().noneMatch(a -> a.getCategory() == MachineAddon.Category.REFLECTOR)) {
                    isInstalled = true;
                }
            }
            boolean isThermal = addon.getCategory() == MachineAddon.Category.THERMAL_AUGMENT;
            boolean isUpgradeKit = RecipeNode.isThermalUpgradeKit(addon);
            long totalThermalReg = node.getAddons().stream().filter(a -> a.getCategory() == MachineAddon.Category.THERMAL_AUGMENT && !RecipeNode.isThermalUpgradeKit(a)).count();
            boolean isThermalFull = isThermal && !isUpgradeKit && totalThermalReg >= 3L;
            int fillCol = isInstalled ? (hover ? -14016474 : -14929337) : (hover ? -14208702 : -14670800);
            int borderCol = isInstalled ? (hover ? -38037 : -10955777) : (hover ? -10955777 : -13222320);
            if (!isInstalled && isThermalFull) {
                fillCol = hover ? -14539732 : -15131870;
                borderCol = -13946820;
            }
            graphics.fill(bx, by, bx + cardW, by + cardH, fillCol);
            graphics.renderOutline(bx, by, cardW, cardH, borderCol);
            ItemStack sample = addon.getRenderItemStack();
            if (sample != null && !sample.isEmpty()) {
                graphics.renderItem(sample, bx + 4, by + (cardH - 16) / 2);
            }
            if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
                if (installedCount > 1) {
                    graphics.drawString(font, "\u00a7a\u2714 x" + installedCount, bx + cardW - 28, by + 4, -1, false);
                } else if (installedCount == 1) {
                    graphics.drawString(font, hover ? "\u00a7a+\u00a77/\u00a7c-" : "\u00a7a\u2714", bx + cardW - (hover ? 18 : 11), by + 4, -1, false);
                }
            } else if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
                int maxSlots = AddonCatalogView.getMaxHatchSlotsAllowed(node, addon);
                int sameTypeTotal = AddonCatalogView.getTotalInstalledHatchesOfSameType(node, addon);
                if (installedCount > 1) {
                    graphics.drawString(font, "\u00a7a\u2714 x" + installedCount, bx + cardW - (installedCount >= 10 ? 36 : 28), by + 4, -1, false);
                } else if (installedCount == 1) {
                    graphics.drawString(font, hover ? "\u00a7a+\u00a77/\u00a7c-" : "\u00a7a\u2714", bx + cardW - (hover ? 18 : 11), by + 4, -1, false);
                } else if (sameTypeTotal >= maxSlots) {
                    graphics.drawString(font, "\u00a78" + sameTypeTotal + "/" + maxSlots, bx + cardW - 24, by + 4, -7829368, false);
                }
            } else if (isThermal && !isUpgradeKit) {
                if (installedCount > 1) {
                    graphics.drawString(font, "\u00a7a\u2714 x" + installedCount, bx + cardW - 28, by + 4, -1, false);
                } else if (installedCount == 1) {
                    graphics.drawString(font, hover ? "\u00a7a+\u00a77/\u00a7c-" : "\u00a7a\u2714", bx + cardW - (hover ? 18 : 11), by + 4, -1, false);
                } else if (isThermalFull) {
                    graphics.drawString(font, "\u00a783/3", bx + cardW - 18, by + 4, -7829368, false);
                }
            } else if (isInstalled) {
                graphics.drawString(font, hover ? "\u00a7c\u2715" : "\u00a7a\u2714", bx + cardW - 11, by + 4, -1, false);
            }
            String aName = font.plainSubstrByWidth(addon.getName(), cardW - 36);
            graphics.drawString(font, "\u00a7f" + aName, bx + 24, by + 5, -1, false);
            String statsStr = this.dialog.formatAddonBadge(addon);
            graphics.drawString(font, statsStr, bx + 24, by + 19, -3355444, false);
            String subTitle = font.plainSubstrByWidth(MachineConfigDialog.getAddonSubtitle(addon, node), cardW - 28);
            graphics.drawString(font, subTitle, bx + 24, by + 33, -7829368, false);
            if (!hover) continue;
            hoveredAddon = addon;
        }
        if (hoveredAddon != null) {
            boolean isInst;
            ArrayList<Component> tooltip = new ArrayList<Component>();
            tooltip.add(Component.literal((String)("\u00a7f" + hoveredAddon.getName())));
            this.addFormattedDescriptionLines(tooltip, font, hoveredAddon.getDescription());
            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
            adapter.buildAddonTooltip(node, hoveredAddon, false, tooltip);
            boolean isReset = "gtceu:standard_rotor".equals(hoveredAddon.getId()) || "gtceu:reflector_none".equals(hoveredAddon.getId());
            boolean bl = isInst = !isReset && adapter.isAddonInstalled(node, hoveredAddon);
            if (hoveredAddon.getCategory() == MachineAddon.Category.HATCH_BUS) {
                int maxSlots = AddonCatalogView.getMaxHatchSlotsAllowed(node, hoveredAddon);
                int sameTypeTotal = AddonCatalogView.getTotalInstalledHatchesOfSameType(node, hoveredAddon);
                tooltip.add(Component.literal((String)String.format(Locale.ROOT, "\u00a77[Slots: \u00a7a%d \u00a77/ \u00a7e%d\u00a77]", sameTypeTotal, maxSlots)));
                tooltip.add(Component.literal((String)"\u00a7eLeft-Click: \u00a7aAdd 1 Hatch"));
                tooltip.add(Component.literal((String)("\u00a7eShift + Left-Click: \u00a7aFill All (" + maxSlots + "x)")));
                tooltip.add(Component.literal((String)"\u00a7eRight-Click: \u00a7cRemove 1 Hatch"));
            } else if (tooltip.stream().noneMatch(c -> c.getString().contains("[") || c.getString().contains("Install") || c.getString().contains("Remove") || c.getString().contains("\uc7a5\ucc29") || c.getString().contains("\uc81c\uac70"))) {
                if (isInst) {
                    tooltip.add(Component.literal((String)"\u00a7c").append(Component.translatable((String)"gui.gtcalcboard.config.remove")));
                } else {
                    tooltip.add(Component.literal((String)"\u00a7a").append(Component.translatable((String)"gui.gtcalcboard.config.install")));
                }
            }
            MachineConfigDialog.appendAdvancedTooltipDebugInfo(tooltip, hoveredAddon);
            this.dialog.setDeferredTooltip(tooltip);
        }
    }

    private void addFormattedDescriptionLines(List<Component> tooltip, Font font, String rawDesc) {
        String[] chunks;
        if (rawDesc == null || rawDesc.isEmpty()) {
            return;
        }
        for (String chunk : chunks = rawDesc.split("[\\r\\n]+|\\s*\\|\\s*|;")) {
            String lower;
            String trimmed = chunk.trim();
            if (trimmed.isEmpty() || (lower = trimmed.toLowerCase(Locale.ROOT)).contains("shift") || lower.contains("ctrl") || lower.contains("gregtech ceu modern")) continue;
            this.addWrappedBullet(tooltip, font, trimmed);
        }
    }

    private void addWrappedBullet(List<Component> tooltip, Font font, String text) {
        String cleanText = text;
        if (cleanText.startsWith("\u2022 ") || cleanText.startsWith("- ") || cleanText.startsWith("* ")) {
            cleanText = cleanText.substring(2).trim();
        } else if (cleanText.startsWith("\u2022") || cleanText.startsWith("-") || cleanText.startsWith("*")) {
            cleanText = cleanText.substring(1).trim();
        }
        List<FormattedCharSequence> split = font.split(Component.literal((String)("\u00a77\u2022 " + cleanText)), 240);
        for (FormattedCharSequence seq : split) {
            StringBuilder sb = new StringBuilder();
            seq.accept((index, style, codePoint) -> {
                sb.appendCodePoint(codePoint);
                return true;
            });
            tooltip.add(Component.literal((String)sb.toString()));
        }
    }

    public static int getMaxHatchSlotsAllowed(RecipeNode node, MachineAddon addon) {
        MultiblockStructureDef def;
        ResourceLocation ws;
        int reqFluidOut;
        GTHatchAddon.HatchType type;
        if (node == null || addon == null) {
            return 1;
        }
        if (GTAddonCompatibilityHandler.isDistillationTower(node) && ((type = GTAddonCompatibilityHandler.resolveHatchType(addon)) == GTHatchAddon.HatchType.FLUID_OUTPUT || type == GTHatchAddon.HatchType.DUAL_OUTPUT) && (reqFluidOut = (int)node.getOutputs().stream().filter(IngredientStack::isFluid).count()) > 0) {
            return reqFluidOut;
        }
        ResourceLocation resourceLocation = ws = node.getMachineIcon() != null ? node.getMachineIcon() : node.getMultiblockWorkstation();
        if (ws != null && (def = MultiblockStructureCatalog.getStructure(ws)) != null) {
            GTHatchAddon.HatchType type2 = GTAddonCompatibilityHandler.resolveHatchType(addon);
            return switch (type2) {
                case ITEM_INPUT -> def.inputBusSlotCount();
                case ITEM_OUTPUT -> def.outputBusSlotCount();
                case FLUID_INPUT -> def.inputHatchSlotCount();
                case FLUID_OUTPUT -> def.outputHatchSlotCount();
                case DUAL_INPUT -> Math.min(def.inputBusSlotCount(), def.inputHatchSlotCount());
                case DUAL_OUTPUT -> Math.min(def.outputBusSlotCount(), def.outputHatchSlotCount());
                default -> 1;
            };
        }
        return 1;
    }

    public static int getTotalInstalledHatchesOfSameType(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) {
            return 0;
        }
        GTHatchAddon.HatchType targetType = GTAddonCompatibilityHandler.resolveHatchType(addon);
        int total = 0;
        for (MachineAddon a : node.getAddons()) {
            if (a.getCategory() != MachineAddon.Category.HATCH_BUS || GTAddonCompatibilityHandler.resolveHatchType(a) != targetType) continue;
            ++total;
        }
        return total;
    }

    public boolean mouseClicked(double mX, double mY, int button, RecipeNode node, int startX, int startY, int width, int height, BoardScreen parent) {
        int cardIndex;
        List<MachineAddon> filtered = this.getFilteredCatalog(node);
        int totalCards = filtered.size();
        int cols = 3;
        int visibleRows = 2;
        int maxRows = (int)Math.ceil((double)totalCards / (double)cols);
        int maxScroll = Math.max(0, maxRows - visibleRows);
        int cardsPerPage = cols * visibleRows;
        int totalPages = Math.max(1, (int)Math.ceil((double)totalCards / (double)cardsPerPage));
        int navW = totalPages > 1 ? 76 : 0;
        int pillSpace = 100;
        int searchW = width - pillSpace - navW - 12;
        if (this.searchBox != null) {
            this.searchBox.setX(startX + 2);
            this.searchBox.setY(startY);
            this.searchBox.setWidth(searchW);
            if (!this.searchBox.getValue().isEmpty()) {
                int clearBtnX = startX + searchW - 14;
                int clearBtnY = startY + 2;
                if (mX >= (double)clearBtnX && mX <= (double)(clearBtnX + 12) && mY >= (double)clearBtnY && mY <= (double)(clearBtnY + 12)) {
                    this.searchBox.setValue("");
                    this.catalogScroll = 0;
                    return true;
                }
            }
            boolean clicked = this.searchBox.mouseClicked(mX, mY, button);
            this.searchBox.setFocused(clicked);
            if (clicked) {
                return true;
            }
        }
        if (totalPages > 1) {
            int navX = startX + searchW + 6;
            if (mX >= (double)navX && mX <= (double)(navX + 14) && mY >= (double)startY && mY <= (double)(startY + 14)) {
                if (this.catalogScroll > 0) {
                    this.catalogScroll = Math.max(0, this.catalogScroll - visibleRows);
                }
                return true;
            }
            if (mX >= (double)(navX + 62) && mX <= (double)(navX + 76) && mY >= (double)startY && mY <= (double)(startY + 14)) {
                if (this.catalogScroll < maxScroll) {
                    this.catalogScroll = Math.min(maxScroll, this.catalogScroll + visibleRows);
                }
                return true;
            }
        }
        int gridStartY = startY + 18;
        int scrollbarW = maxScroll > 0 ? 6 : 0;
        int gridW = width - scrollbarW - 2;
        int cardW = (gridW - (cols - 1) * 4) / cols;
        int cardH = 50;
        if (maxScroll > 0) {
            int sbX = startX + width - 8;
            int sbY = gridStartY;
            int sbH = visibleRows * (cardH + 4) - 4;
            if (mX >= (double)sbX && mX <= (double)(sbX + 8) && mY >= (double)sbY && mY <= (double)(sbY + sbH)) {
                float clickRatio = (float)(mY - (double)sbY) / (float)sbH;
                this.catalogScroll = Math.max(0, Math.min(maxScroll, Math.round(clickRatio * (float)maxScroll)));
                return true;
            }
        }
        for (int i = 0; i < cols * 2 && (cardIndex = this.catalogScroll * cols + i) < totalCards; ++i) {
            int col = i % cols;
            int row = i / cols;
            int bx = startX + col * (cardW + 4);
            int by = gridStartY + row * (cardH + 4);
            if (!(mX >= (double)bx) || !(mX <= (double)(bx + cardW)) || !(mY >= (double)by) || !(mY <= (double)(by + cardH))) continue;
            MachineAddon addon = filtered.get(cardIndex);
            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
            boolean isResetCard = "gtceu:standard_rotor".equals(addon.getId()) || "gtceu:reflector_none".equals(addon.getId());
            boolean isInstalled = !isResetCard && node.getAddons().stream().anyMatch(a -> a.getId().equals(addon.getId()));
            if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
                if (button == 1) {
                    for (int ai = node.getAddons().size() - 1; ai >= 0; --ai) {
                        if (!node.getAddons().get(ai).getId().equals(addon.getId())) continue;
                        MachineAddon rem = node.getAddons().remove(ai);
                        adapter.onAddonRemoved(node, rem);
                        break;
                    }
                } else {
                    int maxSlots = AddonCatalogView.getMaxHatchSlotsAllowed(node, addon);
                    int currentSameType = AddonCatalogView.getTotalInstalledHatchesOfSameType(node, addon);
                    boolean isShift = Screen.hasShiftDown();
                    if (isShift) {
                        int toAdd = Math.max(0, maxSlots - currentSameType);
                        for (int k = 0; k < toAdd; ++k) {
                            adapter.onAddonInstalled(node, addon.copy());
                        }
                    } else if (currentSameType < maxSlots && adapter.canInstallAddon(node, addon)) {
                        adapter.onAddonInstalled(node, addon.copy());
                    }
                }
            } else if (isInstalled) {
                node.getAddons().removeIf(a -> a.getId().equals(addon.getId()));
                adapter.onAddonRemoved(node, addon);
            } else if (adapter.canInstallAddon(node, addon)) {
                adapter.onAddonInstalled(node, addon.copy());
            }
            this.dialog.getActiveAddonsView().setScrollOffset(Math.max(0, node.getAddons().size() - 2));
            this.invalidateCache();
            if (parent != null) {
                parent.markSummaryDirty();
            }
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta, RecipeNode node, int startX, int startY, int width, int height) {
        int visibleRows;
        List<MachineAddon> filtered = this.getFilteredCatalog(node);
        int cols = 3;
        int maxRows = (int)Math.ceil((double)filtered.size() / (double)cols);
        int maxScroll = Math.max(0, maxRows - (visibleRows = 2));
        if (maxScroll > 0 && mouseX >= (double)startX && mouseX <= (double)(startX + width) && mouseY >= (double)startY && mouseY <= (double)(startY + height)) {
            if (delta < 0.0 && this.catalogScroll < maxScroll) {
                ++this.catalogScroll;
                return true;
            }
            if (delta > 0.0 && this.catalogScroll > 0) {
                --this.catalogScroll;
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            return this.searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            return this.searchBox.charTyped(codePoint, modifiers);
        }
        return false;
    }

    private static class Screen {
        private Screen() {
        }

        static boolean hasShiftDown() {
            return net.minecraft.client.gui.screens.Screen.hasShiftDown();
        }
    }
}
