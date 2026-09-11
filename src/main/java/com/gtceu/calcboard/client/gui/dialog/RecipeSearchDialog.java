package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.event.CatalogLifecycleEvent;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.client.gui.widget.FavoritesDockWidget;
import com.gtceu.calcboard.integration.spi.RecipeViewerRegistry;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.client.gui.search.RecipeFilterConfig;
import com.gtceu.calcboard.client.gui.search.RecipeFilterDialog;
import com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer;
import com.gtceu.calcboard.client.gui.search.RecipeSearchCacheManager;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.ParsedQuery;
import com.gtceu.calcboard.client.gui.search.RecipeSearchQueryEngine;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import com.gtceu.calcboard.client.gui.dialog.modal.IBoardModal;
import com.gtceu.calcboard.client.gui.dialog.modal.ModalRenderContext;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RecipeSearchDialog implements IBoardModal {
    private final BoardScreen parent;
    private final EditBox searchBox;

    public static class ContextualWireTarget {
        public final RecipeNode sourceNode;
        public final int sourcePortIdx;
        public final boolean sourceIsInput;
        public final IngredientStack sourceStack;
        public final double canvasX;
        public final double canvasY;
        public final boolean shiftAutoRatio;

        public ContextualWireTarget(RecipeNode sourceNode, int sourcePortIdx, boolean sourceIsInput, IngredientStack sourceStack, double canvasX, double canvasY, boolean shiftAutoRatio) {
            this.sourceNode = sourceNode;
            this.sourcePortIdx = sourcePortIdx;
            this.sourceIsInput = sourceIsInput;
            this.sourceStack = sourceStack;
            this.canvasX = canvasX;
            this.canvasY = canvasY;
            this.shiftAutoRatio = shiftAutoRatio;
        }
    }

    private final RecipeSearchQueryEngine queryEngine = new RecipeSearchQueryEngine();
    private boolean showFavoritesOnly = false;
    private final List<SearchableRecipe> filteredRecipes = new ArrayList<>();
    private final RecipeFilterDialog filterDialog = new RecipeFilterDialog();
    private int scrollOffset = 0;
    private boolean visible = false;
    private boolean hasTargetSpawnPos = false;
    private double targetSpawnCanvasX = 0;
    private double targetSpawnCanvasY = 0;
    private ContextualWireTarget contextualWireTarget = null;
    private RecipeNode switchTargetNode = null;
    private SearchableRecipe stickyHoverRecipe = null;
    private int stickyHoverRowY = 0;
    private int lastMouseX = 0;
    private int lastMouseY = 0;
    private long lastObservedGlobalVersion = -1;
    private boolean isDraggingScrollBar = false;
    private double dragGrabOffsetY = 0;
    private final Runnable favoritesListener;

    public static void registerFavoritesListener(Runnable listener) {
        RecipeSearchCacheManager.registerFavoritesListener(listener);
    }

    public static void unregisterFavoritesListener(Runnable listener) {
        RecipeSearchCacheManager.unregisterFavoritesListener(listener);
    }

    public static void notifyFavoritesChanged() {
        RecipeSearchCacheManager.notifyFavoritesChanged();
    }

    private static final int BASE_DIALOG_WIDTH = 460;
    private static final int BASE_DIALOG_HEIGHT = 300;
    private static final int ROW_HEIGHT = 32;

    public static int getDialogWidth(int screenWidth) {
        return Math.min(BASE_DIALOG_WIDTH, screenWidth - 24);
    }

    public static int getDialogHeight(int screenHeight) {
        return Math.min(BASE_DIALOG_HEIGHT, screenHeight - 24);
    }

    public record PrefixGuideItem(
            String prefix,
            String labelKey,
            String descKey,
            int color,
            int hoverBg
    ) {}

    public static final List<PrefixGuideItem> PREFIX_ITEMS = List.of(
            new PrefixGuideItem("@", "gui.gtcalcboard.search.prefix.mod", "gui.gtcalcboard.search.prefix.mod.desc", 0xFF38BDF8, 0xFF1C2C44),
            new PrefixGuideItem("#", "gui.gtcalcboard.search.prefix.tag", "gui.gtcalcboard.search.prefix.tag.desc", 0xFFFBBF24, 0xFF3D351C),
            new PrefixGuideItem("[", "gui.gtcalcboard.search.prefix.category", "gui.gtcalcboard.search.prefix.category.desc", 0xFF4ADE80, 0xFF1B3D26),
            new PrefixGuideItem(">", "gui.gtcalcboard.search.prefix.input", "gui.gtcalcboard.search.prefix.input.desc", 0xFFFB923C, 0xFF3D2C1C),
            new PrefixGuideItem("<", "gui.gtcalcboard.search.prefix.output", "gui.gtcalcboard.search.prefix.output.desc", 0xFFF472B6, 0xFF3D1C34),
            new PrefixGuideItem("!", "gui.gtcalcboard.search.prefix.exclude", "gui.gtcalcboard.search.prefix.exclude.desc", 0xFFF87171, 0xFF3D1C1C),
            new PrefixGuideItem("|", "gui.gtcalcboard.search.prefix.or", "gui.gtcalcboard.search.prefix.or.desc", 0xFFA78BFA, 0xFF2A1C44),
            new PrefixGuideItem("\"", "gui.gtcalcboard.search.prefix.exact", "gui.gtcalcboard.search.prefix.exact.desc", 0xFFE2E8F0, 0xFF282E3B)
    );

    public RecipeSearchDialog(BoardScreen parent) {
        this.parent = parent;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc != null ? mc.font : null;
        if (font != null) {
            this.searchBox = new EditBox(font, 0, 0, BASE_DIALOG_WIDTH - 48, 16, Component.translatable("gui.gtcalcboard.search"));
            this.searchBox.setMaxLength(256);
            this.searchBox.setResponder(this::onSearchQueryChanged);
            this.searchBox.setHint(Component.translatable("gui.gtcalcboard.search.search_help"));
        } else {
            this.searchBox = null;
        }

        this.filterDialog.setOnFilterChanged(() -> {
            String query = searchBox != null ? searchBox.getValue() : "";
            updateSearchResults(query);
        });
        this.favoritesListener = () -> {
            if (this.visible) {
                String query = searchBox != null ? searchBox.getValue() : "";
                updateSearchResults(query);
            }
        };
        registerFavoritesListener(this.favoritesListener);
    }

    public static Set<ResourceLocation> getFavoriteRecipeIds() {
        return RecipeSearchCacheManager.getFavoriteRecipeIds();
    }

    public static boolean isRecipeFavorite(Object recipe) {
        return RecipeSearchCacheManager.isRecipeFavorite(recipe);
    }

    public static void toggleFavoriteRecipe(Object recipe) {
        RecipeSearchCacheManager.toggleFavoriteRecipe(recipe);
    }

    public static void clearGlobalCache() {
        RecipeSearchCacheManager.clearGlobalCache();
    }

    public static void invalidateCache() {
        RecipeSearchCacheManager.invalidateCache();
    }

    public static boolean isGlobalCached() {
        return RecipeSearchCacheManager.isGlobalCached();
    }

    public static long getGlobalVersion() {
        return RecipeSearchCacheManager.getGlobalVersion();
    }

    public static int getCachedRecipeCount() {
        return RecipeSearchCacheManager.getCachedRecipeCount();
    }

    public static List<SearchableRecipe> getGlobalRecipes() {
        return RecipeSearchCacheManager.getGlobalRecipes();
    }

    public static RecipeSearchCacheManager.RecipeLoadingProgress getCachingProgress() {
        return RecipeSearchCacheManager.getCachingProgress();
    }

    public static boolean isCaching() {
        return RecipeSearchCacheManager.isCaching();
    }

    public static void ensureGlobalRecipesCachedAsync(Runnable onComplete) {
        RecipeSearchCacheManager.ensureGlobalRecipesCachedAsync(onComplete);
    }

    public void openForContextualWire(RecipeNode sourceNode, int sourcePortIdx, boolean sourceIsInput, IngredientStack sourceStack, double canvasX, double canvasY, boolean shiftAutoRatio) {
        this.contextualWireTarget = new ContextualWireTarget(sourceNode, sourcePortIdx, sourceIsInput, sourceStack, canvasX, canvasY, shiftAutoRatio);
        this.hasTargetSpawnPos = true;
        this.targetSpawnCanvasX = canvasX;
        this.targetSpawnCanvasY = canvasY;
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                "[GTCalcBoard] [UI] RecipeSearchDialog opened (Contextual Drag & Search for stack: '{}', isInput: {}).",
                sourceStack != null ? sourceStack.getDisplayName() : "null", sourceIsInput
        );
        setVisible(true, true, canvasX, canvasY);
    }

    public void openAt(double canvasX, double canvasY) {
        this.contextualWireTarget = null;
        this.hasTargetSpawnPos = true;
        this.targetSpawnCanvasX = canvasX;
        this.targetSpawnCanvasY = canvasY;
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [UI] RecipeSearchDialog opened at canvas pos ({}, {}).", canvasX, canvasY);
        setVisible(true, true, canvasX, canvasY);
    }

    public void open() {
        this.contextualWireTarget = null;
        this.hasTargetSpawnPos = false;
        this.switchTargetNode = null;
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [UI] RecipeSearchDialog opened.");
        setVisible(true, false, 0, 0);
    }

    public String getSearchQuery() {
        return searchBox != null ? searchBox.getValue() : "";
    }

    public void openForSwitch(RecipeNode targetNode) {
        if (targetNode == null) return;
        this.switchTargetNode = targetNode;
        this.contextualWireTarget = null;
        this.hasTargetSpawnPos = false;
        this.visible = true;
        this.scrollOffset = 0;
        this.stickyHoverRecipe = null;
        this.lastObservedGlobalVersion = RecipeSearchCacheManager.getGlobalVersion();

        String prefill = "";
        if (targetNode.getRecipeCategoryId() != null) {
            prefill = "[" + targetNode.getRecipeCategoryId().getPath() + "] ";
        } else if (targetNode.getMachineIcon() != null) {
            prefill = "[" + targetNode.getMachineIcon().getPath() + "] ";
        }
        if (searchBox != null) {
            searchBox.setValue(prefill);
            searchBox.setFocused(true);
        }
        ensureGlobalRecipesCachedAsync(() -> {
            if (this.visible) {
                updateSearchResults(getSearchQuery());
            }
        });
        updateSearchResultsSynchronously(prefill);
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible, boolean isLeftClick, double canvasX, double canvasY) {
        this.visible = visible;
        if (visible) {
            this.scrollOffset = 0;
            this.hasTargetSpawnPos = isLeftClick;
            this.targetSpawnCanvasX = canvasX;
            this.targetSpawnCanvasY = canvasY;
            this.stickyHoverRecipe = null;
            this.lastObservedGlobalVersion = RecipeSearchCacheManager.getGlobalVersion();
            if (searchBox != null) {
                searchBox.setValue("");
                searchBox.setFocused(true);
            }
            ensureGlobalRecipesCachedAsync(() -> {
                if (this.visible) {
                    updateSearchResults(getSearchQuery());
                }
            });
            updateSearchResultsSynchronously("");
        } else {
            this.contextualWireTarget = null;
            this.switchTargetNode = null;
            this.stickyHoverRecipe = null;
            this.isDraggingScrollBar = false;
        }
    }

    public void setVisible(boolean visible) {
        setVisible(visible, false, 0, 0);
    }

    @Override
    public void close() {
        setVisible(false);
    }

    @Override
    public void renderModal(ModalRenderContext context) {
        render(context.graphics(), context.screenWidth(), context.screenHeight(), context.mouseX(), context.mouseY());
    }

    private void onSearchQueryChanged(String query) {
        scrollOffset = 0;
        queryEngine.scheduleSearch(query, contextualWireTarget, showFavoritesOnly, results -> {
            this.filteredRecipes.clear();
            this.filteredRecipes.addAll(results);
        });
    }

    public static List<SearchableRecipe> getTutorialDummyRecipes() {
        return RecipeSearchQueryEngine.getTutorialDummyRecipes();
    }

    private void updateSearchResults(String query) {
        onSearchQueryChanged(query);
    }

    private void updateSearchResultsSynchronously(String query) {
        List<SearchableRecipe> results = queryEngine.computeSearchResults(query, contextualWireTarget, showFavoritesOnly);
        filteredRecipes.clear();
        filteredRecipes.addAll(results);
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible) return;

        // Auto-retry trigger if EMI was not yet ready when opened
        if (!RecipeSearchCacheManager.isGlobalCached() && !RecipeSearchCacheManager.isCaching()) {
            ensureGlobalRecipesCachedAsync(() -> {
                if (this.visible) {
                    updateSearchResults(getSearchQuery());
                }
            });
        }

        // Auto-refresh when background indexing completes or global recipe cache is updated
        long currentGlobalVer = RecipeSearchCacheManager.getGlobalVersion();
        if (currentGlobalVer != lastObservedGlobalVersion) {
            lastObservedGlobalVersion = currentGlobalVer;
            updateSearchResults(getSearchQuery());
        }

        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        RecipeSearchDialogRenderer.render(this, graphics, screenWidth, screenHeight, mouseX, mouseY);
    }

    public BoardScreen getParent() {
        return parent;
    }

    public EditBox getSearchBox() {
        return searchBox;
    }

    public List<SearchableRecipe> getFilteredRecipes() {
        return filteredRecipes;
    }

    public boolean isShowFavoritesOnly() {
        return showFavoritesOnly;
    }

    public RecipeFilterDialog getFilterDialog() {
        return filterDialog;
    }

    public RecipeNode getSwitchTargetNode() {
        return switchTargetNode;
    }

    public ContextualWireTarget getContextualWireTarget() {
        return contextualWireTarget;
    }

    public SearchableRecipe getStickyHoverRecipe() {
        return stickyHoverRecipe;
    }

    public void setStickyHoverRecipe(SearchableRecipe stickyHoverRecipe) {
        this.stickyHoverRecipe = stickyHoverRecipe;
    }

    public int getStickyHoverRowY() {
        return stickyHoverRowY;
    }

    public void setStickyHoverRowY(int stickyHoverRowY) {
        this.stickyHoverRowY = stickyHoverRowY;
    }

    public RecipeSearchQueryEngine getQueryEngine() {
        return queryEngine;
    }

    private boolean handlePrefixSidePanelClick(double mouseX, double mouseY, int sideX, int y, int sideW, int dialogH) {
        if (mouseX < sideX || mouseX > sideX + sideW || mouseY < y || mouseY > y + dialogH) {
            return false;
        }

        int itemW = sideW - 12;
        int itemSpacing = 28;
        for (int i = 0; i < PREFIX_ITEMS.size(); i++) {
            int itemY = y + 28 + i * itemSpacing;
            if (mouseX >= sideX + 6 && mouseX <= sideX + 6 + itemW && mouseY >= itemY && mouseY <= itemY + 24) {
                appendSearchPrefix(PREFIX_ITEMS.get(i).prefix());
                return true;
            }
        }
        return true;
    }

    private void appendSearchPrefix(String prefix) {
        if (searchBox != null) {
            String current = searchBox.getValue();
            String spacePrefix = (!current.isEmpty() && !current.endsWith(" ")) ? " " : "";
            searchBox.setValue(current + spacePrefix + prefix);
            searchBox.setFocused(true);
            searchBox.setCursorPosition(searchBox.getValue().length());
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
            );
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return mouseClicked(mouseX, mouseY, button, parent.width, parent.height);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!visible) return false;

        if (filterDialog.isVisible()) {
            return filterDialog.mouseClicked(mouseX, mouseY, button, screenWidth, screenHeight);
        }

        int dialogW = getDialogWidth(screenWidth);
        int dialogH = getDialogHeight(screenHeight);
        int sideW = 104;
        int gap = 6;
        boolean hasSideSpace = screenWidth >= (dialogW + sideW + gap + 16);
        int totalW = hasSideSpace ? (dialogW + sideW + gap) : dialogW;
        int startX = (screenWidth - totalW) / 2;
        int sideX = hasSideSpace ? startX : -1000;
        int x = hasSideSpace ? (startX + sideW + gap) : startX;
        int y = (screenHeight - dialogH) / 2;

        // Side panel click handling
        if (hasSideSpace && handlePrefixSidePanelClick(mouseX, mouseY, sideX, y, sideW, dialogH)) {
            return true;
        }

        // If mouse is inside the sticky preview card on the right
        if (stickyHoverRecipe != null) {
            int[] bounds = RecipeHoverPreviewRenderer.calculatePreviewBounds(stickyHoverRecipe, x, y, dialogW, dialogH, stickyHoverRowY, screenWidth, screenHeight);
            if (bounds != null && mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[2] && mouseY >= bounds[1] && mouseY <= bounds[1] + bounds[3]) {
                var hoveredIngredient = RecipeHoverPreviewRenderer.getHoveredIngredient(stickyHoverRecipe, x, y, dialogW, dialogH, stickyHoverRowY, (int) mouseX, (int) mouseY, screenWidth, screenHeight);
                handlePreviewCardClick(hoveredIngredient, button);
                return true;
            }
        }

        // Click outside closes dialog
        if (mouseX < x || mouseX > x + dialogW || mouseY < y || mouseY > y + dialogH) {
            setVisible(false);
            return true;
        }

        // Close [X]
        int closeX = x + dialogW - 18;
        int closeY = y + 6;
        if (mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12) {
            setVisible(false);
            return true;
        }

        // Top action buttons
        int btnW = 20;
        int btnH = 16;
        int filterBtnX = x + dialogW - 12 - btnW;
        int favBtnX = filterBtnX - btnW - 3;
        int helpBtnX = favBtnX - btnW - 3;
        int btnY = y + 30;

        // Help button [?] -> Open Chapter 2 of Guidebook
        if (mouseX >= helpBtnX && mouseX <= helpBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
            );
            setVisible(false);
            if (parent != null && parent.getGuideDialog() != null) {
                parent.getGuideDialog().openCategory(GuideDialog.GuideCategory.SEARCH);
            }
            return true;
        }

        // Favorites toggle button [⭐]
        if (mouseX >= favBtnX && mouseX <= favBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            showFavoritesOnly = !showFavoritesOnly;
            updateSearchResultsSynchronously(getSearchQuery());
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), showFavoritesOnly ? 1.4F : 1.0F)
            );
            return true;
        }

        // Filter button [⚙]
        if (mouseX >= filterBtnX && mouseX <= filterBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            filterDialog.updateCategories(RecipeSearchEngine.discoverCategories(RecipeSearchCacheManager.getGlobalRecipes()));
            filterDialog.setVisible(true);
            return true;
        }

        // Search Box click
        int topBtnW = 20;
        int searchBoxW = dialogW - 24 - (topBtnW * 3) - 9;
        int searchBoxX = x + 12;
        int searchBoxY = y + 30;
        int searchBoxH = 16;
        if (searchBox != null) {
            if (mouseX >= searchBoxX && mouseX <= searchBoxX + searchBoxW && mouseY >= searchBoxY && mouseY <= searchBoxY + searchBoxH) {
                searchBox.setFocused(true);
                searchBox.mouseClicked(mouseX, mouseY, button);
                return true;
            } else {
                searchBox.setFocused(false);
            }
        }

        // Click anywhere on a row in the list to select & add that recipe
        int listX = x + 12;
        int listY = y + 52;
        int listW = dialogW - 24;
        int listH = dialogH - 60;
        int visibleRows = Math.max(1, listH / ROW_HEIGHT);
        int maxScroll = Math.max(0, filteredRecipes.size() - visibleRows);

        // Scrollbar track / thumb click check
        if (maxScroll > 0 && button == 0) {
            int scrollTrackH = listH - 4;
            int barH = Math.max(16, (int) ((double) visibleRows / filteredRecipes.size() * scrollTrackH));
            int barY = listY + 2 + (int) ((double) scrollOffset / maxScroll * (scrollTrackH - barH));
            int barX = listX + listW - 4;

            if (mouseX >= barX - 4 && mouseX <= barX + 8 && mouseY >= listY + 2 && mouseY <= listY + 2 + scrollTrackH) {
                if (mouseY >= barY && mouseY <= barY + barH) {
                    isDraggingScrollBar = true;
                    dragGrabOffsetY = mouseY - barY;
                } else {
                    double relativeY = mouseY - (listY + 2) - barH / 2.0;
                    double ratio = relativeY / Math.max(1.0, scrollTrackH - barH);
                    scrollOffset = Math.max(0, Math.min(maxScroll, (int) Math.round(ratio * maxScroll)));
                    isDraggingScrollBar = true;
                    dragGrabOffsetY = barH / 2.0;
                }
                return true;
            }
        }

        for (int i = 0; i < visibleRows; i++) {
            int index = scrollOffset + i;
            if (index >= filteredRecipes.size()) break;

            int rowY = listY + i * ROW_HEIGHT;
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT) {
                SearchableRecipe sr = filteredRecipes.get(index);
                int rowBtnW = 44;
                int btnX = listX + listW - rowBtnW - 6;
                int favStarX = btnX - 20;
                int favStarW = 16;
                int favStarY = rowY + 7;
                int favStarH = 18;

                // 1. Star button click or Right Click -> Toggle Favorite
                boolean isStarClicked = (button == 0 && mouseX >= favStarX && mouseX <= favStarX + favStarW && mouseY >= favStarY && mouseY <= favStarY + favStarH);
                if (isStarClicked || button == 1) {
                    if (sr.recipe() != null) {
                        toggleFavoriteRecipe(sr.recipe());
                        updateSearchResultsSynchronously(getSearchQuery());
                        Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
                        );
                        return true;
                    }
                }

                // 2. Left click -> Switch recipe OR Add recipe to board
                if (button == 0) {
                    if (switchTargetNode != null) {
                        RecipeNode template = com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter().convertToNode(sr.recipe());
                        if (template != null && parent != null) {
                            parent.switchNodeRecipe(switchTargetNode, template);
                            Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.1F)
                            );
                        }
                        setVisible(false);
                        return true;
                    }
                    addRecipeAt(sr, screenWidth, screenHeight);
                    return true;
                }
            }
        }

        return true;
    }

    public void addRecipeAt(SearchableRecipe sr, int screenWidth, int screenHeight) {
        RecipeSearchNodeSpawner.spawnRecipeAt(this, sr, screenWidth, screenHeight, parent, contextualWireTarget, hasTargetSpawnPos, targetSpawnCanvasX, targetSpawnCanvasY);
    }

    public void clearContextualWireTarget() {
        this.contextualWireTarget = null;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;
        if (filterDialog.isVisible()) {
            return filterDialog.mouseScrolled(mouseX, mouseY, delta, parent.width, parent.height);
        }
        int dialogH = getDialogHeight(parent.height);
        int listH = dialogH - 60;
        int visibleRows = Math.max(1, listH / ROW_HEIGHT);
        int maxScroll = Math.max(0, filteredRecipes.size() - visibleRows);
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(delta) * 2));
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (filterDialog.isVisible()) {
            return filterDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == 256) { // Escape closes search dialog
            setVisible(false);
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // Enter or Numpad Enter adds top recipe
            if (!filteredRecipes.isEmpty()) {
                addRecipeAt(filteredRecipes.get(0), parent.width, parent.height);
                return true;
            }
        }
        // R / U recipe lookup over preview card ingredient
        if ((keyCode == 82 || keyCode == 85) && stickyHoverRecipe != null) { // R or U
            int dialogW = getDialogWidth(parent.width);
            int dialogH = getDialogHeight(parent.height);
            int sideW = 104;
            int gap = 6;
            boolean hasSideSpace = parent.width >= (dialogW + sideW + gap + 16);
            int totalW = hasSideSpace ? (dialogW + sideW + gap) : dialogW;
            int startX = (parent.width - totalW) / 2;
            int x = hasSideSpace ? (startX + sideW + gap) : startX;
            int y = (parent.height - dialogH) / 2;

            var hoveredIngredient = RecipeHoverPreviewRenderer.getHoveredIngredient(stickyHoverRecipe, x, y, dialogW, dialogH, stickyHoverRowY, lastMouseX, lastMouseY, parent.width, parent.height);
            if (hoveredIngredient != null) {
                if (com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter().handleHoveredIngredientLookup(hoveredIngredient, keyCode == 82)) {
                    return true;
                }
            }
        }
        if (searchBox != null) {
            searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (filterDialog.isVisible()) {
            return filterDialog.charTyped(codePoint, modifiers);
        }
        return searchBox != null && searchBox.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, int screenWidth, int screenHeight) {
        if (!visible || !isDraggingScrollBar || button != 0) return false;

        int dialogH = getDialogHeight(screenHeight);
        int listH = dialogH - 60;
        int visibleRows = Math.max(1, listH / ROW_HEIGHT);
        int maxScroll = Math.max(0, filteredRecipes.size() - visibleRows);
        if (maxScroll <= 0) return false;

        int y = (screenHeight - dialogH) / 2;
        int listY = y + 52;
        int scrollTrackH = listH - 4;
        int barH = Math.max(16, (int) ((double) visibleRows / filteredRecipes.size() * scrollTrackH));

        double relativeY = mouseY - (listY + 2) - dragGrabOffsetY;
        double ratio = relativeY / Math.max(1.0, scrollTrackH - barH);
        scrollOffset = Math.max(0, Math.min(maxScroll, (int) Math.round(ratio * maxScroll)));
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return mouseDragged(mouseX, mouseY, button, dragX, dragY, parent != null ? parent.width : 800, parent != null ? parent.height : 600);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingScrollBar && button == 0) {
            isDraggingScrollBar = false;
            return true;
        }
        return false;
    }

    public boolean isDraggingScrollBar() {
        return isDraggingScrollBar;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public void setScrollOffset(int scrollOffset) {
        this.scrollOffset = scrollOffset;
    }

    public void setFilteredRecipesForTesting(List<SearchableRecipe> recipes) {
        this.filteredRecipes.clear();
        if (recipes != null) {
            this.filteredRecipes.addAll(recipes);
        }
    }

    public void destroy() {
        unregisterFavoritesListener(this.favoritesListener);
    }

    private boolean handlePreviewCardClick(Object hoveredIngredient, int button) {
        if (hoveredIngredient == null) return false;
        var adapter = com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter();
        if (button == 0) {
            return adapter.handleHoveredIngredientClick(hoveredIngredient, searchBox);
        }
        if (button == 1) {
            return adapter.handleHoveredIngredientLookup(hoveredIngredient, false);
        }
        return false;
    }

    static String resolveGenerationInfo(SearchableRecipe sr) {
        RecipeNode rn = null;
        if (sr.recipe() instanceof RecipeNode directNode) {
            rn = directNode;
        } else if (sr.recipe() instanceof java.util.function.Supplier<?> supp) {
            Object obj = supp.get();
            if (obj instanceof RecipeNode suppliedNode) {
                rn = suppliedNode;
            }
        }
        if (rn != null && rn.isGenerator()) {
            String unit = (rn.getEnergyType() != null) ? rn.getEnergyType().getUnitLabel() : "EU";
            String rate = com.gtceu.calcboard.client.gui.util.FormatUtil.formatRate(rn.getBaseEUt(), false);
            return "§a(+" + rate + " " + unit + ")";
        }
        return null;
    }
}
