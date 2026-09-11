package com.gtceu.calcboard.client.gui.dialog.config;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Searchable machine addon catalog view supporting dynamic responsive row layouts
 * and one-click grid/list view mode toggling.
 */
public class AddonCatalogView {

    private final MachineConfigDialog dialog;
    private EditBox searchBox;
    private int catalogScroll = 0;
    private double categoryScrollX = 0;
    private double maxCategoryScrollX = 0;
    private List<MachineAddon> cachedFilteredCatalog = null;
    private List<AddonCategory> cachedFilterCategories = null;

    private List<AddonCatalogCardRenderer.CachedCardData> pageCardCache = null;
    private int cachedPageScroll = -1;
    private int cachedCols = -1;
    private int cachedCardW = -1;
    private boolean cachedIsListView = false;
    private int cachedNodeAddonHash = 0;
    private int cachedFilteredSize = 0;

    public AddonCatalogView(MachineConfigDialog dialog) {
        this.dialog = dialog;
    }

    public void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.font != null) {
            this.searchBox = new EditBox(mc.font, 0, 0, 160, 14, Component.translatable("gui.gtcalcboard.config.search_hint"));
            this.searchBox.setMaxLength(256);
            this.searchBox.setHint(Component.literal("§8").append(Component.translatable("gui.gtcalcboard.config.search_hint")));
            this.searchBox.setValue("");
            this.searchBox.setResponder(text -> {
                this.catalogScroll = 0;
                invalidateCache();
            });
        }
        this.catalogScroll = 0;
        this.categoryScrollX = 0;
        invalidateCache();
    }

    public void invalidateCache() {
        this.cachedFilteredCatalog = null;
        this.cachedFilterCategories = null;
        this.pageCardCache = null;
    }

    public double getCategoryScrollX() { return categoryScrollX; }
    public void setCategoryScrollX(double categoryScrollX) {
        this.categoryScrollX = Math.max(0, Math.min(maxCategoryScrollX, categoryScrollX));
    }

    public void ensureCategoryVisible(RecipeNode node, AddonCategory targetCat, int dialogWidth) {
        if (node == null) return;
        List<AddonCategory> allCats = getAllCategoriesForFilter(node);
        this.categoryScrollX = AddonCategoryChipRenderer.ensureCategoryVisible(node, targetCat, dialogWidth, this.categoryScrollX, allCats);
    }

    public List<AddonCategory> getAllCategoriesForFilter(RecipeNode node) {
        if (this.cachedFilterCategories == null) {
            this.cachedFilterCategories = AddonCategoryChipRenderer.getAllCategoriesForFilter(node);
        }
        return this.cachedFilterCategories;
    }

    public String getCategoryLabel(AddonCategory cat) { return AddonCategoryChipRenderer.getCategoryLabel(cat); }

    public void renderCategoryFilterChips(GuiGraphics graphics, Font font, RecipeNode node, int startX, int startY, int dialogW, int mouseX, int mouseY) {
        List<AddonCategory> allCats = getAllCategoriesForFilter(node);
        this.categoryScrollX = AddonCategoryChipRenderer.renderCategoryFilterChips(
                graphics, font, node, startX, startY, dialogW, mouseX, mouseY, this.categoryScrollX, dialog, allCats);
    }

    public List<MachineAddon> getFilteredCatalog(RecipeNode node) {
        if (this.cachedFilteredCatalog == null) {
            String q = searchBox != null ? searchBox.getValue() : "";
            this.cachedFilteredCatalog = AddonCatalogFilterHelper.filterCatalog(node, q, dialog);
        }
        return this.cachedFilteredCatalog;
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

        boolean isListView = BoardManager.getInstance().isAddonCatalogListView();
        int scrollbarW = 6;
        int gridW = width - scrollbarW - 2;

        int cols;
        int visibleRows;
        int cardW;
        int cardH;

        if (isListView) {
            cols = 1;
            cardH = 20;
            visibleRows = Math.max(4, (height - 22) / (cardH + 2));
            cardW = gridW;
        } else {
            cardH = 50;
            int minCardW = 120;
            cols = Math.max(3, (gridW + 4) / (minCardW + 4));
            visibleRows = Math.max(2, (height - 22) / (cardH + 4));
            cardW = (gridW - ((cols - 1) * 4)) / cols;
        }

        int cardsPerPage = cols * visibleRows;
        int maxRows = (int) Math.ceil((double) totalCards / (double) cols);
        int maxScroll = Math.max(0, maxRows - visibleRows);
        if (catalogScroll > maxScroll) catalogScroll = maxScroll;

        int totalPages = Math.max(1, (int) Math.ceil((double) totalCards / (double) cardsPerPage));
        int currentPage = Math.min(totalPages, (int) Math.ceil((double) (catalogScroll + visibleRows) / (double) visibleRows));

        int viewBtnW = 16;
        int navW = (totalPages > 1) ? 76 : 0;
        int pillSpace = 100;
        int searchW = Math.max(50, width - pillSpace - navW - viewBtnW - 16);

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

        // View Mode Toggle Button [▦ / ☰]
        int viewBtnX = startX + searchW + 4;
        boolean viewHov = mouseX >= viewBtnX && mouseX <= viewBtnX + viewBtnW && mouseY >= startY && mouseY <= startY + 14;
        graphics.fill(viewBtnX, startY, viewBtnX + viewBtnW, startY + 14, viewHov ? 0xFF3D4558 : 0xFF222733);
        graphics.renderOutline(viewBtnX, startY, viewBtnW, 14, viewHov ? 0xFF58D3FF : 0xFF333A48);
        graphics.drawCenteredString(font, isListView ? "☰" : "▦", viewBtnX + viewBtnW / 2, startY + 3, viewHov ? 0xFF58D3FF : 0xFFCCCCCC);
        if (viewHov) {
            String viewModeKey = isListView ? "gui.gtcalcboard.config.view_mode.list" : "gui.gtcalcboard.config.view_mode.grid";
            dialog.setDeferredTooltip(List.of(Component.translatable(viewModeKey)));
        }

        if (totalPages > 1) {
            int navX = viewBtnX + viewBtnW + 4;
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

        AddonCatalogCardRenderer.renderIndexerStatusPill(graphics, font, startX + width - 2, startY, mouseX, mouseY, dialog);

        int gridStartY = startY + 18;

        if (maxScroll > 0) {
            int sbX = startX + width - 5;
            int sbY = gridStartY;
            int rowSpacing = isListView ? (cardH + 2) : (cardH + 4);
            int sbH = visibleRows * rowSpacing - 4;
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
                String msg = (searchBox != null && !searchBox.getValue().isEmpty())
                        ? "§8" + Component.translatable("gui.gtcalcboard.search.no_results").getString()
                        : (!node.isMultiblock()
                                ? Component.translatable("gui.gtcalcboard.config.singleblock_no_addons").getString()
                                : "§8" + Component.translatable("gui.gtcalcboard.search.no_results").getString());
                graphics.drawCenteredString(font, msg, startX + width / 2, gridStartY + 24, 0xFF888888);
                return;
            }
        }

        updatePageCardCache(font, node, filtered, catalogScroll * cols, cardsPerPage, catalogScroll, cols, cardW, isListView);

        MachineAddon hoveredAddon = null;

        for (int i = 0; i < pageCardCache.size(); i++) {
            AddonCatalogCardRenderer.CachedCardData card = pageCardCache.get(i);
            int col = i % cols;
            int row = i / cols;
            int rowSpacing = isListView ? (cardH + 2) : (cardH + 4);
            int bx = isListView ? (startX + 2) : (startX + col * (cardW + 4));
            int by = gridStartY + row * rowSpacing;

            boolean hover = mouseX >= bx && mouseX <= bx + cardW && mouseY >= by && mouseY <= by + cardH;
            MachineAddon addon = card.addon();

            int fillCol = card.isInstalled() ? (hover ? 0xFF2A2026 : 0xFF1C3247) : (hover ? 0xFF273142 : 0xFF202430);
            int borderCol = card.isInstalled() ? (hover ? 0xFFFF6B6B : 0xFF58D3FF) : (hover ? 0xFF58D3FF : 0xFF363E50);
            if (!card.isInstalled() && card.isThermalFull()) {
                fillCol = hover ? 0xFF22242C : 0xFF191B22;
                borderCol = 0xFF2B303C;
            }

            graphics.fill(bx, by, bx + cardW, by + cardH, fillCol);
            graphics.renderOutline(bx, by, cardW, cardH, borderCol);

            ItemStack sample = card.sample();
            if (isListView) {
                AddonCatalogCardRenderer.renderListViewCard(graphics, font, card, sample, bx, by, cardW, hover);
            } else {
                AddonCatalogCardRenderer.renderGridViewCard(graphics, font, card, sample, bx, by, cardW, cardH, hover);
            }

            if (hover) {
                hoveredAddon = addon;
            }
        }

        if (hoveredAddon != null) {
            AddonCatalogCardRenderer.renderAddonHoverTooltip(node, hoveredAddon, dialog);
        }
    }

    private boolean isPageCardCacheValid(RecipeNode node, int scroll, int cols, int cardW, boolean isListView, int filteredSize) {
        if (pageCardCache == null) return false;
        if (cachedPageScroll != scroll) return false;
        if (cachedCols != cols) return false;
        if (cachedCardW != cardW) return false;
        if (cachedIsListView != isListView) return false;
        if (cachedFilteredSize != filteredSize) return false;
        int currentAddonHash = node != null ? node.getAddons().hashCode() : 0;
        return cachedNodeAddonHash == currentAddonHash;
    }

    private void updatePageCardCache(Font font, RecipeNode node, List<MachineAddon> filtered, int startIndex, int count, int scroll, int cols, int cardW, boolean isListView) {
        if (isPageCardCacheValid(node, scroll, cols, cardW, isListView, filtered.size())) {
            return;
        }

        List<AddonCatalogCardRenderer.CachedCardData> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int cardIndex = startIndex + i;
            if (cardIndex >= filtered.size()) break;
            MachineAddon addon = filtered.get(cardIndex);
            list.add(AddonCatalogCardRenderer.buildCardData(font, node, addon, cardW, isListView, dialog));
        }

        this.pageCardCache = list;
        this.cachedPageScroll = scroll;
        this.cachedCols = cols;
        this.cachedCardW = cardW;
        this.cachedIsListView = isListView;
        this.cachedFilteredSize = filtered.size();
        this.cachedNodeAddonHash = node != null ? node.getAddons().hashCode() : 0;
    }

    public boolean mouseClicked(double mX, double mY, int button, RecipeNode node, int startX, int startY, int width, int height, BoardScreen parent) {
        List<MachineAddon> filtered = getFilteredCatalog(node);
        int totalCards = filtered.size();

        boolean isListView = BoardManager.getInstance().isAddonCatalogListView();
        int scrollbarW = 6;
        int gridW = width - scrollbarW - 2;

        int cols;
        int visibleRows;
        int cardW;
        int cardH;

        if (isListView) {
            cols = 1;
            cardH = 20;
            visibleRows = Math.max(4, (height - 22) / (cardH + 2));
            cardW = gridW;
        } else {
            cardH = 50;
            int minCardW = 120;
            cols = Math.max(3, (gridW + 4) / (minCardW + 4));
            visibleRows = Math.max(2, (height - 22) / (cardH + 4));
            cardW = (gridW - ((cols - 1) * 4)) / cols;
        }

        int cardsPerPage = cols * visibleRows;
        int maxRows = (int) Math.ceil((double) totalCards / (double) cols);
        int maxScroll = Math.max(0, maxRows - visibleRows);
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCards / (double) cardsPerPage));

        int viewBtnW = 16;
        int navW = (totalPages > 1) ? 76 : 0;
        int pillSpace = 100;
        int searchW = Math.max(50, width - pillSpace - navW - viewBtnW - 16);

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

        // View Mode Toggle Button
        int viewBtnX = startX + searchW + 4;
        if (mX >= viewBtnX && mX <= viewBtnX + viewBtnW && mY >= startY && mY <= startY + 14) {
            boolean nextView = !BoardManager.getInstance().isAddonCatalogListView();
            BoardManager.getInstance().setAddonCatalogListView(nextView);
            BoardManager.getInstance().saveForCurrentContext();
            catalogScroll = 0;
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
            );
            return true;
        }

        if (totalPages > 1) {
            int navX = viewBtnX + viewBtnW + 4;
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

        if (maxScroll > 0) {
            int sbX = startX + width - 8;
            int sbY = gridStartY;
            int rowSpacing = isListView ? (cardH + 2) : (cardH + 4);
            int sbH = visibleRows * rowSpacing - 4;
            if (mX >= sbX && mX <= sbX + 8 && mY >= sbY && mY <= sbY + sbH) {
                float clickRatio = (float) (mY - sbY) / (float) sbH;
                catalogScroll = Math.max(0, Math.min(maxScroll, (int) Math.round(clickRatio * maxScroll)));
                return true;
            }
        }

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        for (int i = 0; i < cardsPerPage; i++) {
            int cardIndex = (catalogScroll * cols) + i;
            if (cardIndex >= totalCards) break;

            int col = i % cols;
            int row = i / cols;
            int rowSpacing = isListView ? (cardH + 2) : (cardH + 4);
            int bx = isListView ? (startX + 2) : (startX + col * (cardW + 4));
            int by = gridStartY + row * rowSpacing;

            if (mX >= bx && mX <= bx + cardW && mY >= by && mY <= by + cardH) {
                MachineAddon addon = filtered.get(cardIndex);
                AddonCatalogCardRenderer.handleCardClick(node, addon, button, adapter);
                invalidateCache();
                if (parent != null) parent.markSummaryDirty();
                return true;
            }
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta, RecipeNode node, int startX, int startY, int width, int height) {
        List<MachineAddon> filtered = getFilteredCatalog(node);
        boolean isListView = BoardManager.getInstance().isAddonCatalogListView();
        int scrollbarW = 6;
        int gridW = width - scrollbarW - 2;
        int cols = isListView ? 1 : Math.max(3, (gridW + 4) / 124);
        int cardH = isListView ? 20 : 50;
        int rowSpacing = isListView ? (cardH + 2) : (cardH + 4);
        int visibleRows = isListView ? Math.max(4, (height - 22) / rowSpacing) : Math.max(2, (height - 22) / rowSpacing);

        int maxRows = (int) Math.ceil((double) filtered.size() / (double) cols);
        int maxScroll = Math.max(0, maxRows - visibleRows);
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

    public static int getMaxHatchSlotsAllowed(RecipeNode node, MachineAddon addon) { return AddonHatchSlotHelper.getMaxHatchSlotsAllowed(node, addon); }
    public static int getTotalInstalledHatchesOfSameType(RecipeNode node, MachineAddon addon) { return AddonHatchSlotHelper.getTotalInstalledHatchesOfSameType(node, addon); }
    public static GTHatchAddon.HatchType getHatchType(MachineAddon addon) { return AddonHatchSlotHelper.getHatchType(addon); }
}
