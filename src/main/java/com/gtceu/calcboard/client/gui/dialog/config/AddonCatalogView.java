package com.gtceu.calcboard.client.gui.dialog.config;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AddonCatalogView {

    private final MachineConfigDialog dialog;
    private EditBox searchBox;
    private int catalogScroll = 0;
    private double categoryScrollX = 0;
    private double maxCategoryScrollX = 0;
    private List<MachineAddon> cachedFilteredCatalog = null;
    private List<AddonCategory> cachedFilterCategories = null;

    public AddonCatalogView(MachineConfigDialog dialog) {
        this.dialog = dialog;
    }

    public void init() {
        Minecraft mc = Minecraft.getInstance();
        this.searchBox = new EditBox(mc.font, 0, 0, 160, 14, Component.translatable("gui.gtcalcboard.config.search_hint"));
        this.searchBox.setMaxLength(256);
        this.searchBox.setHint(Component.literal("§8").append(Component.translatable("gui.gtcalcboard.config.search_hint")));
        this.searchBox.setValue("");
        this.searchBox.setResponder(text -> {
            this.catalogScroll = 0;
            invalidateCache();
        });
        this.catalogScroll = 0;
        this.categoryScrollX = 0;
        invalidateCache();
    }

    public void invalidateCache() {
        this.cachedFilteredCatalog = null;
        this.cachedFilterCategories = null;
    }

    public double getCategoryScrollX() {
        return categoryScrollX;
    }

    public void setCategoryScrollX(double categoryScrollX) {
        this.categoryScrollX = Math.max(0, Math.min(maxCategoryScrollX, categoryScrollX));
    }

    public void ensureCategoryVisible(RecipeNode node, AddonCategory targetCat, int dialogWidth) {
        if (node == null) return;
        List<AddonCategory> allCats = getAllCategoriesForFilter(node);
        int targetIdx = (targetCat != null && targetCat.equals(AddonCategory.CUSTOM)) ? (allCats.size() - 1) : allCats.indexOf(targetCat);
        if (targetIdx < 0) return;
        Font font = Minecraft.getInstance().font;
        int chipLeft = 0;
        for (int i = 0; i < targetIdx; i++) {
            chipLeft += font.width(getCategoryLabel(allCats.get(i))) + 12 + 4;
        }
        int chipW = font.width(getCategoryLabel(allCats.get(targetIdx))) + 12;
        int availW = dialogWidth - 20;
        if (chipLeft < categoryScrollX) {
            categoryScrollX = chipLeft;
        } else if (chipLeft + chipW > categoryScrollX + availW) {
            categoryScrollX = chipLeft + chipW - availW;
        }
    }

    public List<AddonCategory> getAllCategoriesForFilter(RecipeNode node) {
        if (this.cachedFilterCategories != null) {
            return this.cachedFilterCategories;
        }

        List<AddonCategory> list = new ArrayList<>();
        list.add(null);
        List<AddonCategory> relCats = MachineAddon.getRelevantCategories(node);
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        List<MachineAddon> allAddons = MachineAddonCatalog.getInstance().getAllAddons();

        java.util.Set<AddonCategory> activeCategories = new java.util.HashSet<>();
        if (adapter != null) {
            for (MachineAddon r : adapter.getResetAddonCards(node)) {
                if (r != null && adapter.isAddonCompatible(node, r)) {
                    activeCategories.add(r.getCategory());
                }
            }
        }

        for (MachineAddon a : allAddons) {
            if (a != null && a.isCompatibleWith(node)) {
                activeCategories.add(a.getCategory());
            }
        }

        for (AddonCategory cat : relCats) {
            if (cat != null && !cat.equals(AddonCategory.CUSTOM) && !list.contains(cat)) {
                if (cat.equals(AddonCategory.THREADING) || activeCategories.contains(cat)) {
                    list.add(cat);
                }
            }
        }
        list.add(AddonCategory.CUSTOM);
        this.cachedFilterCategories = list;
        return list;
    }

    public String getCategoryLabel(AddonCategory cat) {
        if (cat == null) return Component.translatable("gui.gtcalcboard.addon_cat.all").getString();
        return Component.translatable(cat.getTranslatableKey()).getString();
    }

    public void renderCategoryFilterChips(GuiGraphics graphics, Font font, RecipeNode node, int startX, int startY, int dialogW, int mouseX, int mouseY) {
        List<AddonCategory> allCats = getAllCategoriesForFilter(node);
        int totalCats = allCats.size();
        int availW = dialogW - 20;
        int scrollAreaX = startX;
        int scrollAreaW = availW;

        int totalWidth = 0;
        List<Integer> chipWidths = new ArrayList<>();
        for (AddonCategory cat : allCats) {
            int w = font.width(getCategoryLabel(cat)) + 12;
            chipWidths.add(w);
            totalWidth += w + 4;
        }
        totalWidth = Math.max(0, totalWidth - 4);

        maxCategoryScrollX = Math.max(0, totalWidth - scrollAreaW);
        categoryScrollX = Math.max(0, Math.min(maxCategoryScrollX, categoryScrollX));

        dialog.enableScaledScissor(graphics, scrollAreaX, startY - 1, scrollAreaX + scrollAreaW, startY + 17);

        int cx = scrollAreaX - (int) categoryScrollX;
        for (int i = 0; i < totalCats; i++) {
            AddonCategory cat = allCats.get(i);
            int bw = chipWidths.get(i);
            boolean active = dialog.isCustomBuilderActive() ? (cat != null && cat.equals(AddonCategory.CUSTOM))
                    : ((dialog.getSelectedCategory() == null && cat == null) || (dialog.getSelectedCategory() != null && dialog.getSelectedCategory().equals(cat)));
            if (cx + bw >= scrollAreaX && cx <= scrollAreaX + scrollAreaW) {
                renderChip(graphics, font, getCategoryLabel(cat), active, cx, startY, bw, mouseX, mouseY, scrollAreaX, scrollAreaX + scrollAreaW);
            }
            cx += bw + 4;
        }

        graphics.disableScissor();

        if (maxCategoryScrollX > 0) {
            if (categoryScrollX > 2) {
                graphics.drawString(font, "◀", scrollAreaX - 6, startY + 4, 0xFFFFAA00, false);
            }
            if (categoryScrollX < maxCategoryScrollX - 2) {
                graphics.drawString(font, "▶", scrollAreaX + scrollAreaW - 6, startY + 4, 0xFFFFAA00, false);
            }
        }
    }

    private void renderChip(GuiGraphics graphics, Font font, String label, boolean active, int bx, int by, int bw, int mouseX, int mouseY, int clipMinX, int clipMaxX) {
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 16 && mouseX >= clipMinX && mouseX <= clipMaxX;
        graphics.fill(bx, by, bx + bw, by + 16, active ? 0xFF1B1E28 : (hover ? 0xFF2E3544 : 0xFF222733));
        graphics.renderOutline(bx, by, bw, 16, active ? 0xFF58D3FF : 0xFF333A48);
        graphics.drawCenteredString(font, label, bx + bw / 2, by + 4, active ? 0xFF58D3FF : 0xFF9CA5B8);
    }

    public List<MachineAddon> getFilteredCatalog(RecipeNode node) {
        if (this.cachedFilteredCatalog != null) {
            return this.cachedFilteredCatalog;
        }

        List<MachineAddon> list = MachineAddonCatalog.getInstance().getAllAddons();
        String q = searchBox != null ? searchBox.getValue().toLowerCase().trim() : "";
        String qClean = q.replace('_', ' ').trim();
        String qUnder = q.replace(' ', '_').trim();

        List<MachineAddon> filtered = new ArrayList<>();
        List<AddonCategory> rel = (dialog.getSelectedCategory() == null) ? MachineAddon.getRelevantCategories(node) : null;

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null) {
            List<MachineAddon> resetCards = adapter.getResetAddonCards(node);
            for (MachineAddon resetCard : resetCards) {
                if (!adapter.isAddonCompatible(node, resetCard)) continue;
                if (dialog.getSelectedCategory() != null && !resetCard.getCategory().equals(dialog.getSelectedCategory())) continue;
                if (dialog.getSelectedCategory() == null && rel != null && !rel.contains(resetCard.getCategory())) continue;
                if (q.isEmpty() || resetCard.getName().toLowerCase().contains(q) || "reset".contains(q) || "standard".contains(q) || "기본".contains(q) || "none".contains(q)) {
                    filtered.add(resetCard);
                }
            }
        }

        for (MachineAddon addon : list) {
            if (addon == null) continue;
            if (dialog.getSelectedCategory() != null && !addon.getCategory().equals(dialog.getSelectedCategory())) continue;
            if (dialog.getSelectedCategory() == null && rel != null && !rel.contains(addon.getCategory())) continue;
            if (!addon.isCompatibleWith(node)) continue;
            if (!q.isEmpty()) {
                String n = addon.getName().toLowerCase();
                String d = addon.getDescription() != null ? addon.getDescription().toLowerCase() : "";
                String idStr = addon.getId() != null ? addon.getId().toString().toLowerCase() : "";
                if (!n.contains(q) && !n.contains(qClean) && !n.contains(qUnder)
                        && !d.contains(q) && !d.contains(qClean) && !d.contains(qUnder)
                        && !idStr.contains(q) && !idStr.contains(qClean) && !idStr.contains(qUnder)) {
                    continue;
                }
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
        if (!node.hasMultiblockOption() && !ModAdapterRegistry.getAdapterForNode(node).supportsAddons(node) && !dialog.isCustomBuilderActive()) {
            int bannerY = startY + 12;
            graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.config.singleblock_no_addons").getString(), startX + width / 2, bannerY, 0xFFAAAAAA);
            graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.config.singleblock_custom_hint").getString(), startX + width / 2, bannerY + 16, 0xFF888888);
            return;
        }

        List<MachineAddon> filtered = getFilteredCatalog(node);
        int totalCards = filtered.size();
        int cols = 3;
        int maxRows = (int) Math.ceil((double) totalCards / (double) cols);
        int visibleRows = 2;
        int cardsPerPage = cols * visibleRows;
        int maxScroll = Math.max(0, maxRows - visibleRows);
        if (catalogScroll > maxScroll) catalogScroll = maxScroll;

        int totalPages = Math.max(1, (int) Math.ceil((double) totalCards / (double) cardsPerPage));
        int currentPage = (catalogScroll / visibleRows) + 1;

        int navW = (totalPages > 1) ? 76 : 0;
        int pillSpace = 100;
        int searchW = width - pillSpace - navW - 12;

        if (searchBox != null) {
            searchBox.setX(startX + 2);
            searchBox.setY(startY);
            searchBox.setWidth(searchW);
            searchBox.render(graphics, mouseX, mouseY, 0);

            if (!searchBox.getValue().isEmpty()) {
                int clearBtnX = startX + searchW - 14;
                int clearBtnY = startY + 2;
                boolean clearHover = mouseX >= clearBtnX && mouseX <= clearBtnX + 12 && mouseY >= clearBtnY && mouseY <= clearBtnY + 12;
                graphics.fill(clearBtnX, clearBtnY, clearBtnX + 12, clearBtnY + 12, clearHover ? 0xFF772222 : 0xFF3D2020);
                graphics.renderOutline(clearBtnX, clearBtnY, 12, 12, clearHover ? 0xFFA03333 : 0xFF553030);
                graphics.drawCenteredString(font, "✕", clearBtnX + 6, clearBtnY + 2, 0xFFFFFFFF);
            }
        }

        if (totalPages > 1) {
            int navX = startX + searchW + 6;
            boolean prevHov = mouseX >= navX && mouseX <= navX + 14 && mouseY >= startY && mouseY <= startY + 14;
            graphics.fill(navX, startY, navX + 14, startY + 14, prevHov ? 0xFF3D4558 : 0xFF222733);
            graphics.renderOutline(navX, startY, 14, 14, prevHov ? 0xFF58D3FF : 0xFF333A48);
            graphics.drawCenteredString(font, "◀", navX + 7, startY + 3, catalogScroll > 0 ? 0xFFFFFFFF : 0xFF666666);

            String pageText = currentPage + "/" + totalPages;
            graphics.drawCenteredString(font, "§7" + pageText, navX + 38, startY + 3, 0xFFE0E0E0);

            boolean nextHov = mouseX >= navX + 62 && mouseX <= navX + 76 && mouseY >= startY && mouseY <= startY + 14;
            graphics.fill(navX + 62, startY, navX + 76, startY + 14, nextHov ? 0xFF3D4558 : 0xFF222733);
            graphics.renderOutline(navX + 62, startY, 14, 14, nextHov ? 0xFF58D3FF : 0xFF333A48);
            graphics.drawCenteredString(font, "▶", navX + 69, startY + 3, catalogScroll < maxScroll ? 0xFFFFFFFF : 0xFF666666);
        }

        renderIndexerStatusPill(graphics, font, startX + width - 2, startY, mouseX, mouseY);

        int gridStartY = startY + 18;
        int scrollbarW = (maxScroll > 0) ? 6 : 0;
        int gridW = width - scrollbarW - 2;
        int cardW = (gridW - ((cols - 1) * 4)) / cols;
        int cardH = 50;

        if (maxScroll > 0) {
            int sbX = startX + width - 5;
            int sbY = gridStartY;
            int sbH = visibleRows * (cardH + 4) - 4;
            graphics.fill(sbX, sbY, sbX + 4, sbY + sbH, 0xFF141720);
            graphics.renderOutline(sbX, sbY, 4, sbH, 0xFF2A3140);

            float thumbRatio = (float) visibleRows / (float) maxRows;
            int thumbH = Math.max(14, (int) (sbH * thumbRatio));
            int thumbY = sbY + (int) ((sbH - thumbH) * ((float) catalogScroll / (float) maxScroll));
            graphics.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, 0xFF4A556B);
            graphics.renderOutline(sbX, thumbY, 4, thumbH, 0xFF657592);
        }

        if (filtered.isEmpty()) {
            boolean isCatalogEmpty = MachineAddonCatalog.getInstance().getAllAddons().isEmpty();
            if (isCatalogEmpty && !MachineAddonCatalog.getInstance().isReady()) {
                if (!MachineAddonCatalog.getInstance().isLoading()) {
                    MachineAddonCatalog.getInstance().preloadAsync();
                }
                double prog = MachineAddonCatalog.getInstance().getExhaustiveProgress();
                int pct = (int) (prog * 100.0);
                String msg = "§e⏳ " + Component.translatable("gui.gtcalcboard.loading_addons").getString() + " (" + pct + "%)";
                int centerY = gridStartY + (visibleRows * cardH) / 2;
                graphics.drawCenteredString(font, msg, startX + width / 2, centerY - 14, 0xFFE0C040);

                int barW = Math.min(220, width - 40);
                int barH = 6;
                int barX = (startX + width / 2) - (barW / 2);
                int barY = centerY + 2;

                graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF222733);
                graphics.renderOutline(barX, barY, barW, barH, 0xFF3D4659);

                float fillRatio = Math.max(0.05f, (float) prog);
                int fillW = (int) (barW * fillRatio);
                graphics.fill(barX + 1, barY + 1, barX + fillW - 1, barY + barH - 1, 0xFF4A90E2);
                return;
            } else {
                String msg = "§8" + Component.translatable("gui.gtcalcboard.search.no_results").getString();
                graphics.drawCenteredString(font, msg, startX + width / 2, gridStartY + 24, 0xFF888888);
                return;
            }
        }

        MachineAddon hoveredAddon = null;

        for (int i = 0; i < cols * visibleRows; i++) {
            int cardIndex = (catalogScroll * cols) + i;
            if (cardIndex >= totalCards) break;

            int col = i % cols;
            int row = i / cols;
            int bx = startX + col * (cardW + 4);
            int by = gridStartY + row * (cardH + 4);

            boolean hover = mouseX >= bx && mouseX <= bx + cardW && mouseY >= by && mouseY <= by + cardH;
            MachineAddon addon = filtered.get(cardIndex);
            boolean isResetCard = "gtceu:standard_rotor".equals(addon.getId()) || "gtceu:reflector_none".equals(addon.getId());
            int installedCount = (int) node.getAddons().stream().filter(a -> a.getId().equals(addon.getId())).count();
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
            boolean isThermalFull = isThermal && !isUpgradeKit && totalThermalReg >= 3;

            int fillCol = isInstalled ? (hover ? 0xFF2A2026 : 0xFF1C3247) : (hover ? 0xFF273142 : 0xFF202430);
            int borderCol = isInstalled ? (hover ? 0xFFFF6B6B : 0xFF58D3FF) : (hover ? 0xFF58D3FF : 0xFF363E50);
            if (!isInstalled && isThermalFull) {
                fillCol = hover ? 0xFF22242C : 0xFF191B22;
                borderCol = 0xFF2B303C;
            }

            graphics.fill(bx, by, bx + cardW, by + cardH, fillCol);
            graphics.renderOutline(bx, by, cardW, cardH, borderCol);

            ItemStack sample = addon.getRenderItemStack();
            if (sample != null && !sample.isEmpty()) {
                graphics.renderItem(sample, bx + 4, by + (cardH - 16) / 2);
            }

            if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
                if (installedCount > 1) {
                    graphics.drawString(font, "§a✔x" + installedCount, bx + cardW - 28, by + 4, 0xFFFFFFFF, false);
                } else if (installedCount == 1) {
                    graphics.drawString(font, hover ? "§a+§7/§c-" : "§a✔", bx + cardW - (hover ? 18 : 11), by + 4, 0xFFFFFFFF, false);
                }
            } else if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
                int maxSlots = getMaxHatchSlotsAllowed(node, addon);
                int sameTypeTotal = getTotalInstalledHatchesOfSameType(node, addon);
                if (installedCount > 1) {
                    graphics.drawString(font, "§a✔x" + installedCount, bx + cardW - (installedCount >= 10 ? 36 : 28), by + 4, 0xFFFFFFFF, false);
                } else if (installedCount == 1) {
                    graphics.drawString(font, hover ? "§a+§7/§c-" : "§a✔", bx + cardW - (hover ? 18 : 11), by + 4, 0xFFFFFFFF, false);
                } else if (sameTypeTotal >= maxSlots) {
                    graphics.drawString(font, "§8" + sameTypeTotal + "/" + maxSlots, bx + cardW - 24, by + 4, 0xFF888888, false);
                }
            } else if (isThermal && !isUpgradeKit) {
                if (installedCount > 1) {
                    graphics.drawString(font, "§a✔x" + installedCount, bx + cardW - 28, by + 4, 0xFFFFFFFF, false);
                } else if (installedCount == 1) {
                    graphics.drawString(font, hover ? "§a+§7/§c-" : "§a✔", bx + cardW - (hover ? 18 : 11), by + 4, 0xFFFFFFFF, false);
                } else if (isThermalFull) {
                    graphics.drawString(font, "§83/3", bx + cardW - 18, by + 4, 0xFF888888, false);
                }
            } else if (isInstalled) {
                graphics.drawString(font, hover ? "§c✖" : "§a✔", bx + cardW - 11, by + 4, 0xFFFFFFFF, false);
            }

            String aName = font.plainSubstrByWidth(addon.getName(), cardW - 36);
            graphics.drawString(font, "§f" + aName, bx + 24, by + 5, 0xFFFFFFFF, false);

            String statsStr = dialog.formatAddonBadge(addon);
            graphics.drawString(font, statsStr, bx + 24, by + 19, 0xFFCCCCCC, false);

            String subTitle = font.plainSubstrByWidth(MachineConfigDialog.getAddonSubtitle(addon, node), cardW - 28);
            graphics.drawString(font, subTitle, bx + 24, by + 33, 0xFF888888, false);

            if (hover) {
                hoveredAddon = addon;
            }
        }

        if (hoveredAddon != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§f" + hoveredAddon.getName()));
            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
            adapter.buildAddonTooltip(node, hoveredAddon, false, tooltip);

            boolean isReset = "gtceu:standard_rotor".equals(hoveredAddon.getId()) || "gtceu:reflector_none".equals(hoveredAddon.getId());
            boolean isInst = !isReset && adapter.isAddonInstalled(node, hoveredAddon);
            if (hoveredAddon.getCategory() == MachineAddon.Category.HATCH_BUS) {
                int maxSlots = getMaxHatchSlotsAllowed(node, hoveredAddon);
                int sameTypeTotal = getTotalInstalledHatchesOfSameType(node, hoveredAddon);
                tooltip.add(Component.literal(String.format(Locale.ROOT, "§7[Slots: §a%d §7/ §e%d§7]", sameTypeTotal, maxSlots)));
                tooltip.add(Component.literal("§eLeft-Click: §aAdd 1 Hatch"));
                tooltip.add(Component.literal("§eShift + Left-Click: §aFill All (" + maxSlots + "x)"));
                tooltip.add(Component.literal("§eRight-Click: §cRemove 1 Hatch"));
            } else if (tooltip.stream().noneMatch(c -> c.getString().contains("[") || c.getString().contains("Install") || c.getString().contains("Remove") || c.getString().contains("장착") || c.getString().contains("제거"))) {
                if (isInst) {
                    tooltip.add(Component.literal("§c").append(Component.translatable("gui.gtcalcboard.config.remove")));
                } else {
                    tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.config.install")));
                }
            }

            MachineConfigDialog.appendAdvancedTooltipDebugInfo(tooltip, hoveredAddon);
            dialog.setDeferredTooltip(tooltip);
        }
    }

    private void renderIndexerStatusPill(GuiGraphics graphics, Font font, int rightX, int y, int mouseX, int mouseY) {
        var catalog = MachineAddonCatalog.getInstance();
        boolean running = catalog.isExhaustiveScanRunning();
        boolean complete = catalog.isExhaustiveScanComplete();

        if (!running && !complete) return;

        int pct = (int) Math.round(catalog.getExhaustiveProgress() * 100.0);
        String pillText = running
                ? "§e🔍 " + Component.translatable("gui.gtcalcboard.catalog.deep_scan_running", String.valueOf(pct)).getString()
                : "§a✔ " + Component.translatable("gui.gtcalcboard.catalog.deep_scan_complete").getString();

        int pillW = font.width(font.plainSubstrByWidth(pillText, 200)) + 12;
        int pillX = rightX - pillW;
        boolean hover = mouseX >= pillX && mouseX <= rightX && mouseY >= y && mouseY <= y + 14;

        int bg = running ? (hover ? 0xFF2E2818 : 0xFF221E14) : (hover ? 0xFF182A1E : 0xFF142018);
        int border = running ? (hover ? 0xFFE0C040 : 0xFF8A7320) : (hover ? 0xFF45B074 : 0xFF2D6E49);

        graphics.fill(pillX, y, rightX, y + 14, bg);
        graphics.renderOutline(pillX, y, pillW, 14, border);
        graphics.drawCenteredString(font, pillText, pillX + pillW / 2, y + 3, 0xFFFFFFFF);

        if (hover) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§e🔍 " + Component.translatable("gui.gtcalcboard.catalog.deep_scan_tooltip_title").getString()));
            tooltip.add(Component.literal(Component.translatable("gui.gtcalcboard.catalog.deep_scan_tooltip_track1").getString()));
            if (running) {
                tooltip.add(Component.literal(Component.translatable("gui.gtcalcboard.catalog.deep_scan_tooltip_track2_running", String.valueOf(pct)).getString()));
            } else {
                tooltip.add(Component.literal(Component.translatable("gui.gtcalcboard.catalog.deep_scan_tooltip_track2_complete").getString()));
            }
            dialog.setDeferredTooltip(tooltip);
        }
    }

    public boolean mouseClicked(double mX, double mY, int button, RecipeNode node, int startX, int startY, int width, int height, BoardScreen parent) {
        List<MachineAddon> filtered = getFilteredCatalog(node);
        int totalCards = filtered.size();
        int cols = 3;
        int visibleRows = 2;
        int cardsPerPage = cols * visibleRows;
        int maxRows = (int) Math.ceil((double) totalCards / (double) cols);
        int maxScroll = Math.max(0, maxRows - visibleRows);
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCards / (double) cardsPerPage));

        int navW = (totalPages > 1) ? 76 : 0;
        int pillSpace = 100;
        int searchW = width - pillSpace - navW - 12;

        if (searchBox != null) {
            searchBox.setX(startX + 2);
            searchBox.setY(startY);
            searchBox.setWidth(searchW);

            if (!searchBox.getValue().isEmpty()) {
                int clearBtnX = startX + searchW - 14;
                int clearBtnY = startY + 2;
                if (mX >= clearBtnX && mX <= clearBtnX + 12 && mY >= clearBtnY && mY <= clearBtnY + 12) {
                    searchBox.setValue("");
                    catalogScroll = 0;
                    return true;
                }
            }

            boolean clicked = searchBox.mouseClicked(mX, mY, button);
            searchBox.setFocused(clicked);
            if (clicked) return true;
        }

        if (totalPages > 1) {
            int navX = startX + searchW + 6;
            if (mX >= navX && mX <= navX + 14 && mY >= startY && mY <= startY + 14) {
                if (catalogScroll > 0) {
                    catalogScroll = Math.max(0, catalogScroll - visibleRows);
                }
                return true;
            }
            if (mX >= navX + 62 && mX <= navX + 76 && mY >= startY && mY <= startY + 14) {
                if (catalogScroll < maxScroll) {
                    catalogScroll = Math.min(maxScroll, catalogScroll + visibleRows);
                }
                return true;
            }
        }

        int gridStartY = startY + 18;
        int scrollbarW = (maxScroll > 0) ? 6 : 0;
        int gridW = width - scrollbarW - 2;
        int cardW = (gridW - ((cols - 1) * 4)) / cols;
        int cardH = 50;

        if (maxScroll > 0) {
            int sbX = startX + width - 8;
            int sbY = gridStartY;
            int sbH = visibleRows * (cardH + 4) - 4;
            if (mX >= sbX && mX <= sbX + 8 && mY >= sbY && mY <= sbY + sbH) {
                float clickRatio = (float) (mY - sbY) / (float) sbH;
                catalogScroll = Math.max(0, Math.min(maxScroll, (int) Math.round(clickRatio * maxScroll)));
                return true;
            }
        }

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        for (int i = 0; i < cols * 2; i++) {
            int cardIndex = (catalogScroll * cols) + i;
            if (cardIndex >= totalCards) break;

            int col = i % cols;
            int row = i / cols;
            int bx = startX + col * (cardW + 4);
            int by = gridStartY + row * (cardH + 4);

            if (mX >= bx && mX <= bx + cardW && mY >= by && mY <= by + cardH) {
                MachineAddon addon = filtered.get(cardIndex);
                if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
                    int installedCount = adapter.getAddonInstalledCount(node, addon);
                    int totalEnergyHatches = (int) node.getAddons().stream().filter(a -> a.getCategory() == MachineAddon.Category.ENERGY_HATCH).count();
                    if (button == 1) {
                        adapter.handleUninstallAddon(node, addon);
                    } else {
                        if (installedCount == 0 || (installedCount == 1 && totalEnergyHatches < 2)) {
                            adapter.handleInstallAddon(node, addon, false);
                        } else {
                            adapter.handleUninstallAddon(node, addon);
                        }
                    }
                } else if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
                    int maxSlots = getMaxHatchSlotsAllowed(node, addon);
                    int sameTypeTotal = getTotalInstalledHatchesOfSameType(node, addon);
                    if (button == 1) {
                        adapter.handleUninstallAddon(node, addon);
                    } else {
                        if (Screen.hasShiftDown()) {
                            int toAdd = Math.max(1, maxSlots - sameTypeTotal);
                            for (int k = 0; k < toAdd; k++) {
                                adapter.handleInstallAddon(node, addon, false);
                            }
                        } else if (sameTypeTotal < maxSlots) {
                            adapter.handleInstallAddon(node, addon, false);
                        } else {
                            adapter.handleUninstallAddon(node, addon);
                        }
                    }
                } else if (addon.getCategory().equals(AddonCategory.MAGNET) || addon.getCategory().equals(AddonCategory.THREADING) || addon.getCategory().equals(AddonCategory.THERMAL_AUGMENT)) {
                    if (button == 1) {
                        adapter.handleUninstallAddon(node, addon);
                    } else {
                        adapter.handleInstallAddon(node, addon, Screen.hasShiftDown());
                    }
                } else if (button == 1) {
                    adapter.handleUninstallAddon(node, addon);
                } else {
                    if (adapter.isAddonInstalled(node, addon)) {
                        adapter.handleUninstallAddon(node, addon);
                    } else {
                        adapter.handleInstallAddon(node, addon, Screen.hasShiftDown());
                    }
                }
                invalidateCache();
                if (parent != null) parent.markSummaryDirty();
                return true;
            }
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta, RecipeNode node, int startX, int startY, int width, int height) {
        List<MachineAddon> filtered = getFilteredCatalog(node);
        int maxRows = (int) Math.ceil((double) filtered.size() / 3.0);
        int maxScroll = Math.max(0, maxRows - 2);
        if (maxScroll > 0) {
            catalogScroll = Math.max(0, Math.min(maxScroll, catalogScroll - (int) Math.signum(delta)));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null) {
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                catalogScroll = 0;
            }
            if (searchBox.isFocused()) {
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null) {
            if (searchBox.charTyped(codePoint, modifiers)) {
                catalogScroll = 0;
                return true;
            }
        }
        return false;
    }

    public static int getMaxHatchSlotsAllowed(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return 1;
        GTHatchAddon.HatchType type = getHatchType(addon);

        int reqCount = switch (type) {
            case FLUID_OUTPUT -> (int) node.getOutputs().stream().filter(IngredientStack::isFluid).count();
            case FLUID_INPUT -> (int) node.getInputs().stream().filter(IngredientStack::isFluid).count();
            case ITEM_OUTPUT -> (int) node.getOutputs().stream().filter(IngredientStack::isItem).count();
            case ITEM_INPUT -> (int) node.getInputs().stream().filter(IngredientStack::isItem).count();
            case DUAL_INPUT -> Math.max(
                    (int) node.getInputs().stream().filter(IngredientStack::isFluid).count(),
                    (int) node.getInputs().stream().filter(IngredientStack::isItem).count()
            );
            case DUAL_OUTPUT -> Math.max(
                    (int) node.getOutputs().stream().filter(IngredientStack::isFluid).count(),
                    (int) node.getOutputs().stream().filter(IngredientStack::isItem).count()
            );
            default -> 1;
        };

        boolean isDT = false;
        ResourceLocation mbId = node.getMachineIcon();
        if (mbId == null || !MultiblockDetector.isMultiblock(mbId)) {
            mbId = node.getMultiblockWorkstation();
        }
        if (mbId != null && mbId.getPath().contains("distillation_tower")) {
            isDT = true;
        }
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("distillation_tower")) {
            isDT = true;
        }

        if (isDT && (type == GTHatchAddon.HatchType.FLUID_OUTPUT || type == GTHatchAddon.HatchType.DUAL_OUTPUT)) {
            return Math.max(1, reqCount);
        }

        if (mbId != null) {
            int rFluidOut = (int) node.getOutputs().stream().filter(IngredientStack::isFluid).count();
            int rItemOut = (int) node.getOutputs().stream().filter(IngredientStack::isItem).count();
            int rFluidIn = (int) node.getInputs().stream().filter(IngredientStack::isFluid).count();
            int rItemIn = (int) node.getInputs().stream().filter(IngredientStack::isItem).count();
            var def = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getMatchingStructure(mbId, rFluidOut, rItemOut, rFluidIn, rItemIn);
            if (def == null) {
                def = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(mbId);
            }
            if (def != null) {
                int defSlots = switch (type) {
                    case FLUID_OUTPUT -> def.outputHatchSlotCount();
                    case FLUID_INPUT -> def.inputHatchSlotCount();
                    case ITEM_OUTPUT -> def.outputBusSlotCount();
                    case ITEM_INPUT -> def.inputBusSlotCount();
                    default -> 1;
                };
                reqCount = Math.max(reqCount, defSlots);
            }
        }

        return Math.max(1, reqCount);
    }

    public static int getTotalInstalledHatchesOfSameType(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return 0;
        GTHatchAddon.HatchType type = getHatchType(addon);
        int total = 0;
        for (MachineAddon a : node.getAddons()) {
            if (a.getCategory() == MachineAddon.Category.HATCH_BUS) {
                if (getHatchType(a) == type) {
                    total++;
                }
            }
        }
        return total;
    }

    public static GTHatchAddon.HatchType getHatchType(MachineAddon addon) {
        if (addon instanceof GTHatchAddon gh) return gh.getHatchType();
        var stats = com.gtceu.calcboard.compat.gtceu.helper.GTHatchHelper.extractStatsFromMachineDef(null, addon.getItemIcon());
        if (stats != null) return stats.hatchType();
        String path = addon.getId().toLowerCase(Locale.ROOT);
        if (path.contains("input_hatch") || path.contains("fluid_import")) return GTHatchAddon.HatchType.FLUID_INPUT;
        if (path.contains("output_bus") || path.contains("export_bus")) return GTHatchAddon.HatchType.ITEM_OUTPUT;
        if (path.contains("input_bus") || path.contains("import_bus")) return GTHatchAddon.HatchType.ITEM_INPUT;
        return GTHatchAddon.HatchType.FLUID_OUTPUT;
    }
}
