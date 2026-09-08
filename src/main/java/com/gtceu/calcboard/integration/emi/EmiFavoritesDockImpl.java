package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.search.RecipeFilterConfig;
import com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer;
import com.gtceu.calcboard.client.gui.widget.FavoritesDockWidget;
import com.gtceu.calcboard.client.gui.widget.IFavoritesDockHandler;
import com.gtceu.calcboard.client.gui.widget.LeftActivityBarWidget;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.runtime.EmiReloadManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * EMI-backed Favorites Dock implementation.
 * Encapsulates all interactions with the EMI runtime and recipe view.
 */
public class EmiFavoritesDockImpl implements IFavoritesDockHandler {

    private final FavoritesDockWidget parent;
    private final BoardScreen screen;

    private double scrollY = 0;
    private double maxScrollY = 0;

    private double subScrollY = 0;
    private double subMaxScrollY = 0;
    private EmiFavorite activeFlyoutFavorite = null;
    private List<EmiRecipe> activeFlyoutRecipes = new ArrayList<>();
    private EmiRecipe hoveredFlyoutRecipe = null;

    public static final int EXPANDED_WIDTH = EmiFavoritesDockRenderer.EXPANDED_WIDTH;
    public static final int HEADER_HEIGHT = EmiFavoritesDockRenderer.HEADER_HEIGHT;
    public static final int ROW_HEIGHT = EmiFavoritesDockRenderer.ROW_HEIGHT;
    public static final int SUB_WIDTH = EmiFavoritesDockRenderer.SUB_WIDTH;
    public static final int SUB_ROW_HEIGHT = EmiFavoritesDockRenderer.SUB_ROW_HEIGHT;
    public static final int SCROLLBAR_TRACK_WIDTH = EmiFavoritesDockRenderer.SCROLLBAR_TRACK_WIDTH;
    public static final int SCROLLBAR_HIT_WIDTH = EmiFavoritesDockRenderer.SCROLLBAR_HIT_WIDTH;
    public static final int MIN_SCROLLBAR_HEIGHT = EmiFavoritesDockRenderer.MIN_SCROLLBAR_HEIGHT;

    private boolean isDraggingScrollBar = false;
    private boolean isDraggingSubScrollBar = false;
    private double scrollDragGrabOffsetY = 0;

    private EmiFavorite draggingFavorite = null;
    private EmiRecipe draggingFlyoutRecipe = null;
    private double dragStartX = 0;
    private double dragStartY = 0;
    private boolean isDragging = false;

    private EmiFavorite hoveredFavorite = null;
    private int hoveredFavRowY = 0;
    private EmiRecipe activePreviewRecipe = null;
    private int activePreviewRowY = 0;

    private static final Map<EmiFavorite, List<EmiRecipe>> FAVORITE_RECIPES_CACHE = new WeakHashMap<>();

    public EmiFavoritesDockImpl(FavoritesDockWidget parent, BoardScreen screen) {
        this.parent = parent;
        this.screen = screen;
        RecipeFilterConfig.getInstance().addChangeListener(this::onFilterConfigChanged);
    }

    private void onFilterConfigChanged() {
        clearCache();
        if (activeFlyoutFavorite != null) {
            activeFlyoutRecipes = findRecipesForFavorite(activeFlyoutFavorite);
            if (activeFlyoutRecipes.isEmpty()) {
                closeFlyout();
            } else {
                int subH = screen.height - 40;
                int totalH = activeFlyoutRecipes.size() * SUB_ROW_HEIGHT;
                int listH = subH - HEADER_HEIGHT - 4;
                subMaxScrollY = Math.max(0, totalH - listH);
                subScrollY = Math.max(0, Math.min(subMaxScrollY, subScrollY));
            }
        }
    }

    FavoritesDockWidget getParent() { return parent; }
    BoardScreen getScreen() { return screen; }
    int getDockX() { return LeftActivityBarWidget.BAR_WIDTH; }
    int getDockY() { return screen.getFavoritesDockY(); }

    double getScrollY() { return scrollY; }
    void setScrollY(double scrollY) { this.scrollY = scrollY; }
    double getMaxScrollY() { return maxScrollY; }
    void setMaxScrollY(double maxScrollY) { this.maxScrollY = maxScrollY; }

    double getSubScrollY() { return subScrollY; }
    void setSubScrollY(double subScrollY) { this.subScrollY = subScrollY; }
    double getSubMaxScrollY() { return subMaxScrollY; }
    void setSubMaxScrollY(double subMaxScrollY) { this.subMaxScrollY = subMaxScrollY; }

    EmiFavorite getActiveFlyoutFavorite() { return activeFlyoutFavorite; }
    void setActiveFlyoutFavorite(EmiFavorite activeFlyoutFavorite) { this.activeFlyoutFavorite = activeFlyoutFavorite; }
    List<EmiRecipe> getActiveFlyoutRecipes() { return activeFlyoutRecipes; }
    void setActiveFlyoutRecipes(List<EmiRecipe> activeFlyoutRecipes) { this.activeFlyoutRecipes = activeFlyoutRecipes; }

    EmiFavorite getHoveredFavorite() { return hoveredFavorite; }
    void setHoveredFavorite(EmiFavorite hoveredFavorite) { this.hoveredFavorite = hoveredFavorite; }
    int getHoveredFavRowY() { return hoveredFavRowY; }
    void setHoveredFavRowY(int hoveredFavRowY) { this.hoveredFavRowY = hoveredFavRowY; }

    EmiRecipe getActivePreviewRecipe() { return activePreviewRecipe; }
    void setActivePreviewRecipe(EmiRecipe activePreviewRecipe) { this.activePreviewRecipe = activePreviewRecipe; }
    int getActivePreviewRowY() { return activePreviewRowY; }
    void setActivePreviewRowY(int activePreviewRowY) { this.activePreviewRowY = activePreviewRowY; }

    EmiRecipe getHoveredFlyoutRecipe() { return hoveredFlyoutRecipe; }
    void setHoveredFlyoutRecipe(EmiRecipe hoveredFlyoutRecipe) { this.hoveredFlyoutRecipe = hoveredFlyoutRecipe; }

    boolean isDraggingScrollBar() { return isDraggingScrollBar; }
    boolean isDraggingSubScrollBar() { return isDraggingSubScrollBar; }
    boolean isDragging() { return isDragging; }
    EmiFavorite getDraggingFavorite() { return draggingFavorite; }
    EmiRecipe getDraggingFlyoutRecipe() { return draggingFlyoutRecipe; }

    @Override
    public void closeFlyout() {
        activeFlyoutFavorite = null;
        activeFlyoutRecipes = Collections.emptyList();
        subScrollY = 0;
        isDraggingSubScrollBar = false;
        activePreviewRecipe = null;
        hoveredFavorite = null;
    }

    @Override
    public void resetScrollBarDrag() {
        isDraggingScrollBar = false;
        isDraggingSubScrollBar = false;
    }

    @Override
    public boolean isEmiLoading() {
        try {
            if (!EmiReloadManager.isLoaded()) {
                return true;
            }
            var rm = EmiApi.getRecipeManager();
            if (rm == null || rm.getRecipes().isEmpty()) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void clearCache() {
        FAVORITE_RECIPES_CACHE.clear();
    }

    private static boolean isCategoryExcluded(EmiRecipeCategory category) {
        if (category == null || category.getId() == null) return false;
        RecipeFilterConfig config = RecipeFilterConfig.getInstance();
        return config.isCategoryExcluded(category.getId().toString())
                || config.isCategoryExcluded(category.getId().getPath());
    }

    private static boolean isRecipeExcluded(EmiRecipe recipe) {
        if (recipe == null) return false;
        return isCategoryExcluded(recipe.getCategory());
    }

    private static boolean isFavoriteRecipeExcluded(EmiFavorite fav) {
        if (fav == null) return false;
        return isRecipeExcluded(fav.getRecipe());
    }

    List<EmiFavorite> getFavorites() {
        List<EmiFavorite> raw = getRawFavorites();
        if (raw.isEmpty()) return List.of();

        List<EmiFavorite> filtered = new ArrayList<>(raw.size());
        for (EmiFavorite fav : raw) {
            if (!isFavoriteRecipeExcluded(fav)) {
                filtered.add(fav);
            }
        }
        return filtered;
    }

    private List<EmiFavorite> getRawFavorites() {
        try {
            if (EmiFavorites.favoriteSidebar != null && !EmiFavorites.favoriteSidebar.isEmpty()) {
                return EmiFavorites.favoriteSidebar;
            }
            if (EmiFavorites.favorites != null) {
                return EmiFavorites.favorites;
            }
        } catch (Throwable ignored) {}
        return List.of();
    }

    List<EmiRecipe> findRecipesForFavorite(EmiFavorite fav) {
        if (fav == null) return List.of();
        List<EmiRecipe> cached = FAVORITE_RECIPES_CACHE.get(fav);
        if (cached != null) return cached;

        if (fav.getRecipe() != null) {
            return cacheSingleFavoriteRecipe(fav);
        }

        List<EmiRecipe> list = new ArrayList<>();
        if (!fav.getEmiStacks().isEmpty()) {
            collectStackRecipes(fav, list);
        }

        List<EmiRecipe> unmodifiable = Collections.unmodifiableList(list);
        FAVORITE_RECIPES_CACHE.put(fav, unmodifiable);
        return unmodifiable;
    }

    private List<EmiRecipe> cacheSingleFavoriteRecipe(EmiFavorite fav) {
        if (isRecipeExcluded(fav.getRecipe())) {
            FAVORITE_RECIPES_CACHE.put(fav, List.of());
            return List.of();
        }
        List<EmiRecipe> list = List.of(fav.getRecipe());
        FAVORITE_RECIPES_CACHE.put(fav, list);
        return list;
    }

    private void collectStackRecipes(EmiFavorite fav, List<EmiRecipe> list) {
        var rm = EmiApi.getRecipeManager();
        if (rm == null) return;

        EmiRecipe defaultRecipe = findDefaultRecipe(fav);
        for (var stack : fav.getEmiStacks()) {
            collectOutputRecipes(rm, stack, list);
            collectWorkstationRecipes(rm, stack, list);
        }

        if (defaultRecipe != null && list.contains(defaultRecipe)) {
            list.remove(defaultRecipe);
            list.add(0, defaultRecipe);
        }
    }

    private EmiRecipe findDefaultRecipe(EmiFavorite fav) {
        try {
            for (var stack : fav.getEmiStacks()) {
                EmiRecipe def = BoM.getRecipe(stack);
                if (def != null && !isRecipeExcluded(def)) {
                    return def;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void collectOutputRecipes(EmiRecipeManager rm, EmiStack stack, List<EmiRecipe> list) {
        var outRecipes = rm.getRecipesByOutput(stack);
        if (outRecipes == null || outRecipes.isEmpty()) return;
        for (var r : outRecipes) {
            if (r != null && !list.contains(r) && !isRecipeExcluded(r)) {
                list.add(r);
            }
        }
    }

    private void collectWorkstationRecipes(EmiRecipeManager rm, EmiStack stack, List<EmiRecipe> list) {
        try {
            for (EmiRecipeCategory cat : rm.getCategories()) {
                if (isCategoryExcluded(cat)) continue;
                if (isMatchingWorkstationCategory(rm, cat, stack)) {
                    collectMatchingCategoryRecipes(rm, cat, stack, list);
                }
            }
        } catch (Throwable ignored) {}
    }

    private boolean isMatchingWorkstationCategory(EmiRecipeManager rm, EmiRecipeCategory cat, EmiStack stack) {
        var workstations = rm.getWorkstations(cat);
        if (workstations == null) return false;
        for (EmiIngredient wsIng : workstations) {
            if (containsMatchingIngredient(wsIng, stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsMatchingIngredient(EmiIngredient wsIng, EmiStack stack) {
        if (wsIng == null || wsIng.getEmiStacks() == null) return false;
        for (EmiStack wsStack : wsIng.getEmiStacks()) {
            if (wsStack != null && wsStack.isEqual(stack)) {
                return true;
            }
        }
        return false;
    }

    private void collectMatchingCategoryRecipes(EmiRecipeManager rm, EmiRecipeCategory cat,
                                                EmiStack stack, List<EmiRecipe> list) {
        List<EmiRecipe> catRecipes = rm.getRecipes(cat);
        if (catRecipes == null) return;
        for (EmiRecipe cr : catRecipes) {
            if (cr != null && !list.contains(cr) && !isRecipeExcluded(cr)) {
                list.add(cr);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        EmiFavoritesDockRenderer.render(this, graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        EmiFavoritesDockRenderer.renderTooltips(this, graphics, font, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!parent.isExpanded()) return false;
        EmiRecipe activeEmi = (activePreviewRecipe != null) ? activePreviewRecipe : (hoveredFavorite != null && hoveredFavorite.getRecipe() != null ? hoveredFavorite.getRecipe() : null);
        if (activeEmi == null) return false;

        int screenW = screen.width;
        int screenH = screen.height;
        double mouseX = screen.getLastMouseX();
        double mouseY = screen.getLastMouseY();

        int subX = getDockX() + EXPANDED_WIDTH + 3;
        int previewAnchorX = (activeFlyoutFavorite != null) ? (subX + SUB_WIDTH + 6) : (getDockX() + EXPANDED_WIDTH + 6);
        int activeEmiRowY = (activePreviewRecipe != null) ? activePreviewRowY : hoveredFavRowY;

        int[] bounds = RecipeHoverPreviewRenderer.calculateEmiPreviewBounds(activeEmi, previewAnchorX, activeEmiRowY, screenW, screenH);
        if (bounds != null && mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[2] && mouseY >= bounds[1] && mouseY <= bounds[1] + bounds[3]) {
            var ing = RecipeHoverPreviewRenderer.getEmiHoveredIngredient(activeEmi, previewAnchorX, activeEmiRowY, (int) mouseX, (int) mouseY, screenW, screenH);
            if (ing instanceof EmiIngredient emiIng) {
                if (keyCode == GLFW.GLFW_KEY_R) {
                    EmiApi.displayRecipes(emiIng);
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_U) {
                    EmiApi.displayUses(emiIng);
                    return true;
                }
            }
            if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
                double[] pos = BoardScreen.getNextNodeCenterPosition(screenW, screenH);
                EmiFavoritesNodeSpawner.spawnRecipeNode(screen, activeEmi, pos[0], pos[1]);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!parent.isExpanded()) {
            return false;
        }

        int dockY = getDockY();
        int maxH = Math.min(240, screen.height - dockY - 60);

        if (button == 0 && mouseX >= getDockX() && mouseX <= getDockX() + EXPANDED_WIDTH && mouseY >= dockY && mouseY <= dockY + HEADER_HEIGHT) {
            parent.toggle();
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
            );
            return true;
        }

        if (handleActivePreviewClick(mouseX, mouseY, button)) {
            return true;
        }

        if (activeFlyoutFavorite != null && !activeFlyoutRecipes.isEmpty()) {
            if (handleFlyoutPanelClick(mouseX, mouseY, button, dockY, maxH)) {
                return true;
            }
        }

        int listY = dockY + HEADER_HEIGHT + 2;
        int listH = maxH - HEADER_HEIGHT - 4;
        List<EmiFavorite> favorites = getFavorites();
        int totalH = favorites.size() * ROW_HEIGHT;

        if (button == 0 && handleMainScrollBarClick(mouseX, mouseY, listY, listH, totalH)) {
            return true;
        }

        return handleFavoritesListClick(mouseX, mouseY, button, listY, listH, favorites);
    }

    private boolean handleActivePreviewClick(double mouseX, double mouseY, int button) {
        EmiRecipe activeEmi = (activePreviewRecipe != null) ? activePreviewRecipe : (hoveredFavorite != null && hoveredFavorite.getRecipe() != null ? hoveredFavorite.getRecipe() : null);
        if (activeEmi == null) return false;

        int activeEmiRowY = (activePreviewRecipe != null) ? activePreviewRowY : hoveredFavRowY;
        int subX = getDockX() + EXPANDED_WIDTH + 3;
        int previewAnchorX = (activeFlyoutFavorite != null) ? (subX + SUB_WIDTH + 6) : (getDockX() + EXPANDED_WIDTH + 6);
        int screenW = screen.width;
        int screenH = screen.height;

        int[] bounds = RecipeHoverPreviewRenderer.calculateEmiPreviewBounds(activeEmi, previewAnchorX, activeEmiRowY, screenW, screenH);
        if (bounds == null || mouseX < bounds[0] || mouseX > bounds[0] + bounds[2] || mouseY < bounds[1] || mouseY > bounds[1] + bounds[3]) {
            return false;
        }

        var ing = RecipeHoverPreviewRenderer.getEmiHoveredIngredient(activeEmi, previewAnchorX, activeEmiRowY, (int) mouseX, (int) mouseY, screenW, screenH);
        if (ing instanceof EmiIngredient emiIng) {
            if (button == 0) {
                EmiApi.displayRecipes(emiIng);
                return true;
            }
            if (button == 1) {
                EmiApi.displayUses(emiIng);
                return true;
            }
        }

        if (button == 0) {
            double[] pos = BoardScreen.getNextNodeCenterPosition(screenW, screenH);
            EmiFavoritesNodeSpawner.spawnRecipeNode(screen, activeEmi, pos[0], pos[1]);
            return true;
        }
        return true;
    }

    private boolean handleFlyoutPanelClick(double mouseX, double mouseY, int button, int dockY, int maxH) {
        int subX = getDockX() + EXPANDED_WIDTH + 3;
        if (mouseX < subX || mouseX > subX + SUB_WIDTH || mouseY < dockY || mouseY > dockY + maxH) {
            return false;
        }

        int listY = dockY + HEADER_HEIGHT + 2;
        int listH = maxH - HEADER_HEIGHT - 4;
        int totalH = activeFlyoutRecipes.size() * SUB_ROW_HEIGHT;

        if (button == 0 && handleSubScrollBarClick(mouseX, mouseY, subX, listY, listH, totalH)) {
            return true;
        }

        for (int i = 0; i < activeFlyoutRecipes.size(); i++) {
            EmiRecipe recipe = activeFlyoutRecipes.get(i);
            int rowY = (int) (listY + (i * SUB_ROW_HEIGHT) - subScrollY);
            if (rowY + SUB_ROW_HEIGHT < listY || rowY > listY + listH) continue;

            if (mouseX >= subX + 2 && mouseX <= subX + SUB_WIDTH - 2 && mouseY >= rowY && mouseY <= rowY + SUB_ROW_HEIGHT) {
                int addBtnX = subX + SUB_WIDTH - 24;
                int addBtnY = rowY + 6;
                boolean addHover = mouseX >= addBtnX && mouseX <= addBtnX + 18 && mouseY >= addBtnY && mouseY <= addBtnY + 16;

                if (button == 0 && addHover) {
                    double[] pos = BoardScreen.getNextNodeCenterPosition(screen.width, screen.height);
                    EmiFavoritesNodeSpawner.spawnRecipeNode(screen, recipe, pos[0], pos[1]);
                    return true;
                }

                if (button == 0) {
                    draggingFlyoutRecipe = recipe;
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                    isDragging = false;
                    return true;
                }
            }
        }
        return true;
    }

    private boolean handleFavoritesListClick(double mouseX, double mouseY, int button, int listY, int listH, List<EmiFavorite> favorites) {
        for (int i = 0; i < favorites.size(); i++) {
            EmiFavorite fav = favorites.get(i);
            int rowY = (int) (listY + (i * ROW_HEIGHT) - scrollY);
            if (rowY + ROW_HEIGHT < listY || rowY > listY + listH) continue;

            if (mouseX >= getDockX() + 2 && mouseX <= getDockX() + EXPANDED_WIDTH - 2 && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT) {
                int removeBtnX = getDockX() + EXPANDED_WIDTH - 17;
                boolean removeHover = mouseX >= removeBtnX && mouseX <= removeBtnX + 10 && mouseY >= rowY + 5 && mouseY <= rowY + 17;

                if (button == 0 && removeHover) {
                    EmiFavorites.removeFavorite(fav);
                    clearCache();
                    closeFlyout();
                    Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 0.8F)
                    );
                    return true;
                }

                if (button == 0) {
                    draggingFavorite = fav;
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                    isDragging = false;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean handleMainScrollBarClick(double mouseX, double mouseY, int listY, int listH, int totalH) {
        if (maxScrollY <= 0) return false;
        int barX = getDockX() + EXPANDED_WIDTH - SCROLLBAR_HIT_WIDTH;
        if (mouseX < barX || mouseX > getDockX() + EXPANDED_WIDTH || mouseY < listY || mouseY > listY + listH) {
            return false;
        }

        int scrollBarH = Math.max(MIN_SCROLLBAR_HEIGHT, (int) ((float) listH / totalH * listH));
        int scrollBarY = (int) (listY + (scrollY / maxScrollY) * (listH - scrollBarH));

        if (mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarH) {
            isDraggingScrollBar = true;
            scrollDragGrabOffsetY = mouseY - scrollBarY;
        } else {
            double relativeY = mouseY - listY - scrollBarH / 2.0;
            double ratio = relativeY / Math.max(1.0, listH - scrollBarH);
            scrollY = Math.max(0, Math.min(maxScrollY, ratio * maxScrollY));
            isDraggingScrollBar = true;
            scrollDragGrabOffsetY = scrollBarH / 2.0;
        }
        return true;
    }

    private boolean handleSubScrollBarClick(double mouseX, double mouseY, int subX, int listY, int listH, int totalH) {
        if (subMaxScrollY <= 0) return false;
        int subBarX = subX + SUB_WIDTH - SCROLLBAR_HIT_WIDTH;
        if (mouseX < subBarX || mouseX > subX + SUB_WIDTH || mouseY < listY || mouseY > listY + listH) {
            return false;
        }

        int scrollBarH = Math.max(MIN_SCROLLBAR_HEIGHT, (int) ((float) listH / totalH * listH));
        int scrollBarY = (int) (listY + (subScrollY / subMaxScrollY) * (listH - scrollBarH));

        if (mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarH) {
            isDraggingSubScrollBar = true;
            scrollDragGrabOffsetY = mouseY - scrollBarY;
        } else {
            double relativeY = mouseY - listY - scrollBarH / 2.0;
            double ratio = relativeY / Math.max(1.0, listH - scrollBarH);
            subScrollY = Math.max(0, Math.min(subMaxScrollY, ratio * subMaxScrollY));
            isDraggingSubScrollBar = true;
            scrollDragGrabOffsetY = scrollBarH / 2.0;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) return false;

        if (isDraggingScrollBar) {
            updateMainScrollBarDrag(mouseY);
            return true;
        }

        if (isDraggingSubScrollBar) {
            updateSubScrollBarDrag(mouseY);
            return true;
        }

        if (draggingFavorite != null || draggingFlyoutRecipe != null) {
            if (!isDragging && Math.hypot(mouseX - dragStartX, mouseY - dragStartY) > 5) {
                isDragging = true;
            }
            return true;
        }
        return false;
    }

    private void updateMainScrollBarDrag(double mouseY) {
        if (maxScrollY <= 0) return;
        int dockY = getDockY();
        int maxH = Math.min(240, screen.height - dockY - 60);
        int listY = dockY + HEADER_HEIGHT + 2;
        int listH = maxH - HEADER_HEIGHT - 4;
        int totalH = getFavorites().size() * ROW_HEIGHT;
        int scrollBarH = Math.max(MIN_SCROLLBAR_HEIGHT, (int) ((float) listH / totalH * listH));
        double relativeY = mouseY - listY - scrollDragGrabOffsetY;
        double ratio = relativeY / Math.max(1.0, listH - scrollBarH);
        scrollY = Math.max(0, Math.min(maxScrollY, ratio * maxScrollY));
    }

    private void updateSubScrollBarDrag(double mouseY) {
        if (subMaxScrollY <= 0 || activeFlyoutFavorite == null) return;
        int dockY = getDockY();
        int maxH = Math.min(240, screen.height - dockY - 60);
        int listY = dockY + HEADER_HEIGHT + 2;
        int listH = maxH - HEADER_HEIGHT - 4;
        int totalH = activeFlyoutRecipes.size() * SUB_ROW_HEIGHT;
        int scrollBarH = Math.max(MIN_SCROLLBAR_HEIGHT, (int) ((float) listH / totalH * listH));
        double relativeY = mouseY - listY - scrollDragGrabOffsetY;
        double ratio = relativeY / Math.max(1.0, listH - scrollBarH);
        subScrollY = Math.max(0, Math.min(subMaxScrollY, ratio * subMaxScrollY));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && (isDraggingScrollBar || isDraggingSubScrollBar)) {
            isDraggingScrollBar = false;
            isDraggingSubScrollBar = false;
            return true;
        }

        if (draggingFlyoutRecipe != null) {
            if (isDragging) {
                double canvasX = screen.toCanvasX(mouseX);
                double canvasY = screen.toCanvasY(mouseY);
                EmiFavoritesNodeSpawner.spawnRecipeNode(screen, draggingFlyoutRecipe, canvasX, canvasY);
            } else {
                double[] pos = BoardScreen.getNextNodeCenterPosition(screen.width, screen.height);
                EmiFavoritesNodeSpawner.spawnRecipeNode(screen, draggingFlyoutRecipe, pos[0], pos[1]);
            }
            draggingFlyoutRecipe = null;
            draggingFavorite = null;
            isDragging = false;
            return true;
        }

        if (draggingFavorite != null) {
            if (isDragging) {
                double canvasX = screen.toCanvasX(mouseX);
                double canvasY = screen.toCanvasY(mouseY);
                EmiFavoritesNodeSpawner.spawnFavoriteNode(screen, draggingFavorite, canvasX, canvasY);
            } else {
                if (draggingFavorite.getRecipe() != null) {
                    double[] pos = BoardScreen.getNextNodeCenterPosition(screen.width, screen.height);
                    EmiFavoritesNodeSpawner.spawnFavoriteNode(screen, draggingFavorite, pos[0], pos[1]);
                } else {
                    activeFlyoutFavorite = draggingFavorite;
                    activeFlyoutRecipes = findRecipesForFavorite(draggingFavorite);
                    subScrollY = 0;
                }
            }
            draggingFavorite = null;
            isDragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!parent.isExpanded()) return false;
        int dockY = getDockY();
        int maxH = Math.min(240, screen.height - dockY - 60);

        int subX = getDockX() + EXPANDED_WIDTH + 3;
        if (activeFlyoutFavorite != null && !activeFlyoutRecipes.isEmpty()) {
            if (mouseX >= subX && mouseX <= subX + SUB_WIDTH && mouseY >= dockY && mouseY <= dockY + maxH) {
                if (subMaxScrollY > 0) {
                    subScrollY = Math.max(0, Math.min(subMaxScrollY, subScrollY - delta * SUB_ROW_HEIGHT));
                    return true;
                }
            }
        }

        if (mouseX >= getDockX() && mouseX <= getDockX() + EXPANDED_WIDTH && mouseY >= dockY && mouseY <= dockY + maxH) {
            if (maxScrollY > 0) {
                scrollY = Math.max(0, Math.min(maxScrollY, scrollY - delta * ROW_HEIGHT));
                return true;
            }
        }
        return false;
    }
}
