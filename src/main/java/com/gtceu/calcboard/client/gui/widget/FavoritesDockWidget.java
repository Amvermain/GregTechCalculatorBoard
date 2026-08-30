package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.search.RecipeFilterConfig;
import com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.*;

/**
 * Collapsible Favorites Dock Widget located at the Top-Left of BoardScreen.
 * Safely isolated from EMI bytecode to prevent NoClassDefFoundError when EMI is absent.
 */
public class FavoritesDockWidget {

    private final BoardScreen screen;
    private boolean expanded;
    private final Object emiImpl;

    public FavoritesDockWidget(BoardScreen screen) {
        this.screen = screen;
        this.expanded = BoardManager.getInstance().isFavoritesDockExpanded();
        this.emiImpl = ModCompatHelper.isEmiLoaded() ? new EmiFavoritesDockImpl(this, screen) : null;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        BoardManager.getInstance().setFavoritesDockExpanded(expanded);
        if (!expanded && emiImpl instanceof EmiFavoritesDockImpl impl) {
            impl.closeFlyout();
        }
    }

    public void toggle() {
        setExpanded(!this.expanded);
    }

    public boolean isEmiLoading() {
        if (!ModCompatHelper.isEmiLoaded() || !(emiImpl instanceof EmiFavoritesDockImpl impl)) {
            return false;
        }
        return impl.isEmiLoading();
    }

    public static void clearCache() {
        if (ModCompatHelper.isEmiLoaded()) {
            EmiFavoritesDockImpl.clearCache();
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!ModCompatHelper.isEmiLoaded() || !(emiImpl instanceof EmiFavoritesDockImpl impl)) {
            return;
        }
        impl.render(graphics, mouseX, mouseY, partialTick);
    }

    public void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!ModCompatHelper.isEmiLoaded() || !(emiImpl instanceof EmiFavoritesDockImpl impl)) {
            return;
        }
        impl.renderTooltips(graphics, font, mouseX, mouseY);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!ModCompatHelper.isEmiLoaded() || !(emiImpl instanceof EmiFavoritesDockImpl impl)) {
            return false;
        }
        return impl.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!ModCompatHelper.isEmiLoaded() || !(emiImpl instanceof EmiFavoritesDockImpl impl)) {
            return false;
        }
        return impl.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!ModCompatHelper.isEmiLoaded() || !(emiImpl instanceof EmiFavoritesDockImpl impl)) {
            return false;
        }
        return impl.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!ModCompatHelper.isEmiLoaded() || !(emiImpl instanceof EmiFavoritesDockImpl impl)) {
            return false;
        }
        return impl.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!ModCompatHelper.isEmiLoaded() || !(emiImpl instanceof EmiFavoritesDockImpl impl)) {
            return false;
        }
        return impl.mouseScrolled(mouseX, mouseY, delta);
    }

    // =========================================================================
    // Static Nested Implementation: Only loaded by JVM when EMI is present
    // =========================================================================
    private static class EmiFavoritesDockImpl {

        private final FavoritesDockWidget parent;
        private final BoardScreen screen;

        private double scrollY = 0;
        private double maxScrollY = 0;

        // Sub-flyout panel state
        private double subScrollY = 0;
        private double subMaxScrollY = 0;
        private dev.emi.emi.runtime.EmiFavorite activeFlyoutFavorite = null;
        private List<dev.emi.emi.api.recipe.EmiRecipe> activeFlyoutRecipes = new ArrayList<>();
        private dev.emi.emi.api.recipe.EmiRecipe hoveredFlyoutRecipe = null;

        private static final int EXPANDED_WIDTH = 145;
        private static final int COLLAPSED_WIDTH = 95;
        private static final int HEADER_HEIGHT = 18;
        private static final int ROW_HEIGHT = 22;
        private static final int SUB_WIDTH = 185;
        private static final int SUB_ROW_HEIGHT = 28;

        // Drag-and-drop state
        private dev.emi.emi.runtime.EmiFavorite draggingFavorite = null;
        private dev.emi.emi.api.recipe.EmiRecipe draggingFlyoutRecipe = null;
        private double dragStartX = 0;
        private double dragStartY = 0;
        private boolean isDragging = false;

        // Hover tooltip tracking
        private dev.emi.emi.runtime.EmiFavorite hoveredFavorite = null;
        private int hoveredFavRowY = 0;
        private dev.emi.emi.api.recipe.EmiRecipe activePreviewRecipe = null;
        private int activePreviewRowY = 0;

        private static final Map<dev.emi.emi.runtime.EmiFavorite, List<dev.emi.emi.api.recipe.EmiRecipe>> FAVORITE_RECIPES_CACHE = new WeakHashMap<>();

        private EmiFavoritesDockImpl(FavoritesDockWidget parent, BoardScreen screen) {
            this.parent = parent;
            this.screen = screen;
        }

        private int getDockX() {
            return 8;
        }

        private int getDockY() {
            return screen.getFavoritesDockY();
        }

        private void closeFlyout() {
            activeFlyoutFavorite = null;
            activeFlyoutRecipes = Collections.emptyList();
            subScrollY = 0;
            activePreviewRecipe = null;
            hoveredFavorite = null;
        }

        private boolean isEmiLoading() {
            try {
                if (!dev.emi.emi.runtime.EmiReloadManager.isLoaded()) {
                    return true;
                }
                var rm = dev.emi.emi.api.EmiApi.getRecipeManager();
                if (rm == null || rm.getRecipes().isEmpty()) {
                    return true;
                }
            } catch (Throwable ignored) {}
            return false;
        }

        private static void clearCache() {
            FAVORITE_RECIPES_CACHE.clear();
        }

        private List<dev.emi.emi.runtime.EmiFavorite> getFavorites() {
            try {
                if (dev.emi.emi.runtime.EmiFavorites.favoriteSidebar != null && !dev.emi.emi.runtime.EmiFavorites.favoriteSidebar.isEmpty()) {
                    return dev.emi.emi.runtime.EmiFavorites.favoriteSidebar;
                }
                if (dev.emi.emi.runtime.EmiFavorites.favorites != null) {
                    return dev.emi.emi.runtime.EmiFavorites.favorites;
                }
            } catch (Throwable ignored) {}
            return List.of();
        }

        private List<dev.emi.emi.api.recipe.EmiRecipe> findRecipesForFavorite(dev.emi.emi.runtime.EmiFavorite fav) {
            if (fav == null) return List.of();
            List<dev.emi.emi.api.recipe.EmiRecipe> cached = FAVORITE_RECIPES_CACHE.get(fav);
            if (cached != null) return cached;

            List<dev.emi.emi.api.recipe.EmiRecipe> list = new ArrayList<>();
            if (fav.getRecipe() != null) {
                list.add(fav.getRecipe());
                List<dev.emi.emi.api.recipe.EmiRecipe> unmodifiable = Collections.unmodifiableList(list);
                FAVORITE_RECIPES_CACHE.put(fav, unmodifiable);
                return unmodifiable;
            }

            if (!fav.getEmiStacks().isEmpty()) {
                var rm = dev.emi.emi.api.EmiApi.getRecipeManager();
                if (rm != null) {
                    var filterConfig = com.gtceu.calcboard.client.gui.search.RecipeFilterConfig.getInstance();
                    dev.emi.emi.api.recipe.EmiRecipe defaultRecipe = null;
                    try {
                        for (var stack : fav.getEmiStacks()) {
                            dev.emi.emi.api.recipe.EmiRecipe def = dev.emi.emi.bom.BoM.getRecipe(stack);
                            if (def != null) {
                                defaultRecipe = def;
                                break;
                            }
                        }
                    } catch (Throwable ignored) {}

                    for (var stack : fav.getEmiStacks()) {
                        var outRecipes = rm.getRecipesByOutput(stack);
                        if (outRecipes != null && !outRecipes.isEmpty()) {
                            for (var r : outRecipes) {
                                if (r != null && !list.contains(r)) {
                                    if (r.getCategory() != null && r.getCategory().getId() != null) {
                                        String catId = r.getCategory().getId().toString();
                                        if (filterConfig.isCategoryExcluded(catId)) {
                                            continue;
                                        }
                                    }
                                    list.add(r);
                                }
                            }
                        }

                        try {
                            for (dev.emi.emi.api.recipe.EmiRecipeCategory cat : rm.getCategories()) {
                                if (cat == null || cat.getId() == null) continue;
                                if (filterConfig.isCategoryExcluded(cat.getId().toString())) continue;

                                var workstations = rm.getWorkstations(cat);
                                if (workstations != null) {
                                    boolean isWs = false;
                                    for (dev.emi.emi.api.stack.EmiIngredient wsIng : workstations) {
                                        if (wsIng != null && wsIng.getEmiStacks() != null) {
                                            for (dev.emi.emi.api.stack.EmiStack wsStack : wsIng.getEmiStacks()) {
                                                if (wsStack != null && wsStack.isEqual(stack)) {
                                                    isWs = true;
                                                    break;
                                                }
                                            }
                                        }
                                        if (isWs) break;
                                    }
                                    if (isWs) {
                                        List<dev.emi.emi.api.recipe.EmiRecipe> catRecipes = rm.getRecipes(cat);
                                        if (catRecipes != null) {
                                            for (dev.emi.emi.api.recipe.EmiRecipe cr : catRecipes) {
                                                if (cr != null && !list.contains(cr)) {
                                                    list.add(cr);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    }

                    if (defaultRecipe != null && list.contains(defaultRecipe)) {
                        list.remove(defaultRecipe);
                        list.add(0, defaultRecipe);
                    }
                }
            }
            List<dev.emi.emi.api.recipe.EmiRecipe> unmodifiable = Collections.unmodifiableList(list);
            FAVORITE_RECIPES_CACHE.put(fav, unmodifiable);
            return unmodifiable;
        }

        private void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            boolean loading = isEmiLoading();
            List<dev.emi.emi.runtime.EmiFavorite> favorites = getFavorites();
            int count = favorites.size();
            hoveredFavorite = null;

            if (screen.isAnyModalOpen() || (activeFlyoutFavorite != null && !favorites.contains(activeFlyoutFavorite))) {
                closeFlyout();
            }

            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 10);

            int dockY = getDockY();
            String countDisplay = loading ? Component.translatable("gui.gtcalcboard.favorites_dock.loading").getString() : String.valueOf(count);

            if (!parent.isExpanded()) {
                boolean hovered = mouseX >= getDockX() && mouseX <= getDockX() + COLLAPSED_WIDTH && mouseY >= dockY && mouseY <= dockY + HEADER_HEIGHT;
                int bg = hovered ? 0xEE1E293B : 0xAA0F172A;
                int border = hovered ? 0xFFFFD700 : (loading ? 0xFFF59E0B : 0xFF475569);

                graphics.fill(getDockX(), dockY, getDockX() + COLLAPSED_WIDTH, dockY + HEADER_HEIGHT, bg);
                graphics.renderOutline(getDockX(), dockY, COLLAPSED_WIDTH, HEADER_HEIGHT, border);

                String title = "⭐ " + Component.translatable("gui.gtcalcboard.favorites").getString() + " (" + countDisplay + ") ▶";
                graphics.drawString(font, font.plainSubstrByWidth(title, COLLAPSED_WIDTH - 6), getDockX() + 6, dockY + 5, hovered ? 0xFFFFD700 : (loading ? 0xFFFDE047 : 0xFFE2E8F0), false);
            } else {
                int maxH = Math.min(240, screen.height - dockY - 60);
                int contentH = maxH - HEADER_HEIGHT;

                int bg = 0xF00F172A;
                int border = loading ? 0xFFF59E0B : 0xFF38BDF8;

                graphics.fill(getDockX(), dockY, getDockX() + EXPANDED_WIDTH, dockY + maxH, bg);
                graphics.renderOutline(getDockX(), dockY, EXPANDED_WIDTH, maxH, border);

                boolean headerHover = mouseX >= getDockX() && mouseX <= getDockX() + EXPANDED_WIDTH && mouseY >= dockY && mouseY <= dockY + HEADER_HEIGHT;
                graphics.fill(getDockX() + 1, dockY + 1, getDockX() + EXPANDED_WIDTH - 1, dockY + HEADER_HEIGHT, headerHover ? 0xFF1E293B : 0xFF172033);
                graphics.fill(getDockX() + 1, dockY + HEADER_HEIGHT, getDockX() + EXPANDED_WIDTH - 1, dockY + HEADER_HEIGHT + 1, 0xFF334155);

                String title = "⭐ " + Component.translatable("gui.gtcalcboard.favorites").getString() + " (" + countDisplay + ")";
                graphics.drawString(font, font.plainSubstrByWidth(title, EXPANDED_WIDTH - 24), getDockX() + 6, dockY + 5, loading ? 0xFFFDE047 : 0xFFFFD700, false);
                graphics.drawString(font, "◀", getDockX() + EXPANDED_WIDTH - 14, dockY + 5, headerHover ? 0xFFFF5555 : 0xFF94A3B8, false);

                int listY = dockY + HEADER_HEIGHT + 2;
                int listH = contentH - 4;

                int subX = getDockX() + EXPANDED_WIDTH + 3;
                int screenW = screen.width;
                int screenH = screen.height;

                dev.emi.emi.api.recipe.EmiRecipe activeEmiRecipe = (activePreviewRecipe != null) ? activePreviewRecipe : (hoveredFavorite != null && hoveredFavorite.getRecipe() != null ? hoveredFavorite.getRecipe() : null);
                int activeEmiRowY = (activePreviewRecipe != null) ? activePreviewRowY : hoveredFavRowY;
                int previewAnchorX = (activeFlyoutFavorite != null) ? (subX + SUB_WIDTH + 6) : (getDockX() + EXPANDED_WIDTH + 6);

                int[] previewBounds = (activeEmiRecipe != null) ? com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer.calculateEmiPreviewBounds(activeEmiRecipe, previewAnchorX, activeEmiRowY, screenW, screenH) : null;

                boolean mouseInPreview = previewBounds != null && mouseX >= previewBounds[0] && mouseX <= previewBounds[0] + previewBounds[2] && mouseY >= previewBounds[1] && mouseY <= previewBounds[1] + previewBounds[3];
                int totalDockRight = (activeFlyoutFavorite != null) ? (subX + SUB_WIDTH) : (getDockX() + EXPANDED_WIDTH);
                boolean mouseInDockArea = mouseX >= getDockX() && mouseX <= totalDockRight && mouseY >= dockY && mouseY <= dockY + maxH;

                boolean mouseInBridge = false;
                if (activeFlyoutFavorite != null && previewBounds != null) {
                    int bridgeLeft = subX + SUB_WIDTH;
                    int bridgeRight = previewBounds[0];
                    int bridgeTop = Math.min(dockY, previewBounds[1]);
                    int bridgeBottom = Math.max(dockY + maxH, previewBounds[1] + previewBounds[3]);
                    mouseInBridge = mouseX >= bridgeLeft && mouseX <= bridgeRight && mouseY >= bridgeTop && mouseY <= bridgeBottom;
                }

                if (!mouseInDockArea && !mouseInPreview && !mouseInBridge) {
                    closeFlyout();
                }

                if (favorites.isEmpty()) {
                    if (loading) {
                        Component syncComp = Component.translatable("gui.gtcalcboard.favorites_dock.syncing");
                        List<net.minecraft.util.FormattedCharSequence> lines = font.split(syncComp, EXPANDED_WIDTH - 16);
                        int sy = listY + 20;
                        for (net.minecraft.util.FormattedCharSequence line : lines) {
                            int lw = font.width(line);
                            graphics.drawString(font, line, getDockX() + (EXPANDED_WIDTH - lw) / 2, sy, 0xFFFDE047, false);
                            sy += 11;
                        }
                    } else {
                        graphics.drawCenteredString(font, "§7" + Component.translatable("gui.gtcalcboard.favorites_dock.empty").getString(), getDockX() + EXPANDED_WIDTH / 2, listY + 16, 0xFF94A3B8);

                        Component hintComp = Component.translatable("gui.gtcalcboard.favorites_dock.empty_hint");
                        List<net.minecraft.util.FormattedCharSequence> lines = font.split(hintComp, EXPANDED_WIDTH - 16);
                        int hy = listY + 30;
                        for (net.minecraft.util.FormattedCharSequence line : lines) {
                            int lw = font.width(line);
                            graphics.drawString(font, line, getDockX() + (EXPANDED_WIDTH - lw) / 2, hy, 0xFF64748B, false);
                            hy += 11;
                        }
                    }
                } else {
                    int totalH = favorites.size() * ROW_HEIGHT;
                    maxScrollY = Math.max(0, totalH - listH);
                    scrollY = Math.max(0, Math.min(maxScrollY, scrollY));

                    graphics.enableScissor(getDockX(), listY, getDockX() + EXPANDED_WIDTH, listY + listH);

                    for (int i = 0; i < favorites.size(); i++) {
                        dev.emi.emi.runtime.EmiFavorite fav = favorites.get(i);
                        int rowY = (int) (listY + (i * ROW_HEIGHT) - scrollY);

                        if (rowY + ROW_HEIGHT < listY || rowY > listY + listH) continue;

                        boolean isFlyoutActive = (activeFlyoutFavorite == fav);
                        boolean rowHover = mouseX >= getDockX() + 2 && mouseX <= getDockX() + EXPANDED_WIDTH - 2 && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;

                        if (rowHover) {
                            hoveredFavorite = fav;
                            hoveredFavRowY = rowY;
                            if (activeFlyoutFavorite != fav && !mouseInPreview) {
                                activeFlyoutFavorite = fav;
                                activeFlyoutRecipes = findRecipesForFavorite(fav);
                                subScrollY = 0;
                            }
                        }

                        int rowBg = (isFlyoutActive || rowHover) ? 0xFF1E293B : (i % 2 == 0 ? 0x880F172A : 0x440F172A);
                        graphics.fill(getDockX() + 2, rowY, getDockX() + EXPANDED_WIDTH - 2, rowY + ROW_HEIGHT - 1, rowBg);

                        if (isFlyoutActive) {
                            graphics.renderOutline(getDockX() + 2, rowY, EXPANDED_WIDTH - 4, ROW_HEIGHT - 1, 0xFF38BDF8);
                        } else if (rowHover) {
                            graphics.renderOutline(getDockX() + 2, rowY, EXPANDED_WIDTH - 4, ROW_HEIGHT - 1, 0xFFFFD700);
                        }

                        renderFavoriteIcon(graphics, fav, getDockX() + 4, rowY + 3);

                        String name = extractFavoriteName(fav);
                        int textColor = isFlyoutActive ? 0xFF38BDF8 : (rowHover ? 0xFFFFD700 : 0xFFE2E8F0);
                        graphics.drawString(font, font.plainSubstrByWidth(name, EXPANDED_WIDTH - 42), getDockX() + 24, rowY + 7, textColor, false);

                        int removeBtnX = getDockX() + EXPANDED_WIDTH - 16;
                        boolean removeHover = mouseX >= removeBtnX && mouseX <= removeBtnX + 12 && mouseY >= rowY + 5 && mouseY <= rowY + 17;
                        if (rowHover || isFlyoutActive) {
                            graphics.drawString(font, "✕", removeBtnX, rowY + 6, removeHover ? 0xFFFF5555 : 0xFF64748B, false);
                        }
                    }

                    graphics.disableScissor();

                    if (maxScrollY > 0) {
                        int scrollBarH = Math.max(10, (int) ((float) listH / totalH * listH));
                        int scrollBarY = (int) (listY + (scrollY / maxScrollY) * (listH - scrollBarH));
                        graphics.fill(getDockX() + EXPANDED_WIDTH - 3, listY, getDockX() + EXPANDED_WIDTH - 1, listY + listH, 0x44000000);
                        graphics.fill(getDockX() + EXPANDED_WIDTH - 3, scrollBarY, getDockX() + EXPANDED_WIDTH - 1, scrollBarY + scrollBarH, 0xFF38BDF8);
                    }
                }

                if (activeFlyoutFavorite != null && !activeFlyoutRecipes.isEmpty()) {
                    renderSubFlyoutPanel(graphics, font, subX, dockY, SUB_WIDTH, maxH, mouseX, mouseY);
                }
            }

            if (isDragging && (draggingFavorite != null || draggingFlyoutRecipe != null)) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 300);
                if (draggingFlyoutRecipe != null) {
                    renderRecipeIcon(graphics, draggingFlyoutRecipe, mouseX - 8, mouseY - 8, null);
                } else {
                    renderFavoriteIcon(graphics, draggingFavorite, mouseX - 8, mouseY - 8);
                }
                graphics.pose().popPose();
            }

            graphics.pose().popPose();
        }

        private void renderSubFlyoutPanel(GuiGraphics graphics, Font font, int subX, int subY, int subW, int subH, int mouseX, int mouseY) {
            graphics.fill(subX, subY, subX + subW, subY + subH, 0xF00F172A);
            graphics.renderOutline(subX, subY, subW, subH, 0xFF38BDF8);

            String favName = extractFavoriteName(activeFlyoutFavorite);
            graphics.fill(subX + 1, subY + 1, subX + subW - 1, subY + HEADER_HEIGHT, 0xFF1E293B);
            graphics.fill(subX + 1, subY + HEADER_HEIGHT, subX + subW - 1, subY + HEADER_HEIGHT + 1, 0xFF334155);
            graphics.drawString(font, font.plainSubstrByWidth(favName + " (" + activeFlyoutRecipes.size() + ")", subW - 8), subX + 6, subY + 5, 0xFF38BDF8, false);

            int listY = subY + HEADER_HEIGHT + 2;
            int listH = subH - HEADER_HEIGHT - 4;
            int totalH = activeFlyoutRecipes.size() * SUB_ROW_HEIGHT;

            subMaxScrollY = Math.max(0, totalH - listH);
            subScrollY = Math.max(0, Math.min(subMaxScrollY, subScrollY));

            graphics.enableScissor(subX, listY, subX + subW, listY + listH);
            hoveredFlyoutRecipe = null;

            ResourceLocation preferredWs = null;
            if (!activeFlyoutFavorite.getEmiStacks().isEmpty()) {
                var firstStack = activeFlyoutFavorite.getEmiStacks().get(0);
                if (firstStack.getItemStack() != null) {
                    preferredWs = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(firstStack.getItemStack().getItem());
                }
            }

            for (int i = 0; i < activeFlyoutRecipes.size(); i++) {
                dev.emi.emi.api.recipe.EmiRecipe recipe = activeFlyoutRecipes.get(i);
                int rowY = (int) (listY + (i * SUB_ROW_HEIGHT) - subScrollY);

                if (rowY + SUB_ROW_HEIGHT < listY || rowY > listY + listH) continue;

                boolean rowHover = mouseX >= subX + 2 && mouseX <= subX + subW - 2 && mouseY >= rowY && mouseY <= rowY + SUB_ROW_HEIGHT;

                boolean isDefault = false;
                if (activeFlyoutFavorite != null && !activeFlyoutFavorite.getEmiStacks().isEmpty()) {
                    try {
                        dev.emi.emi.api.recipe.EmiRecipe def = dev.emi.emi.bom.BoM.getRecipe(activeFlyoutFavorite.getEmiStacks().get(0));
                        if (def != null && def.getId() != null && recipe.getId() != null && def.getId().equals(recipe.getId())) {
                            isDefault = true;
                        }
                    } catch (Throwable ignored) {}
                }

                if (rowHover) {
                    hoveredFlyoutRecipe = recipe;
                    activePreviewRecipe = recipe;
                    activePreviewRowY = rowY;
                }

                int rowBg = rowHover ? 0xFF2A3649 : (isDefault ? 0xFF1A2638 : (i % 2 == 0 ? 0x880F172A : 0x440F172A));
                graphics.fill(subX + 2, rowY, subX + subW - 2, rowY + SUB_ROW_HEIGHT - 1, rowBg);

                if (rowHover) {
                    graphics.renderOutline(subX + 2, rowY, subW - 4, SUB_ROW_HEIGHT - 1, 0xFFFFD700);
                } else if (isDefault) {
                    graphics.renderOutline(subX + 2, rowY, subW - 4, SUB_ROW_HEIGHT - 1, 0xFF38BDF8);
                }

                renderRecipeIcon(graphics, recipe, subX + 5, rowY + 6, preferredWs);

                String rName = getRecipeRowDisplayName(recipe, preferredWs);
                String catName = getRecipeWorkstationName(recipe);

                int textColor = isDefault ? 0xFF38BDF8 : (rowHover ? 0xFFFFD700 : 0xFFFFFFFF);
                graphics.drawString(font, font.plainSubstrByWidth((isDefault ? "★ " : "") + rName, subW - 55), subX + 25, rowY + 4, textColor, false);
                graphics.drawString(font, font.plainSubstrByWidth("§7" + catName, subW - 55), subX + 25, rowY + 15, 0xFF94A3B8, false);

                int addBtnX = subX + subW - 24;
                int addBtnY = rowY + 6;
                boolean addHover = mouseX >= addBtnX && mouseX <= addBtnX + 18 && mouseY >= addBtnY && mouseY <= addBtnY + 16;
                graphics.fill(addBtnX, addBtnY, addBtnX + 18, addBtnY + 16, addHover ? 0xFF2B4466 : 0xFF1C2C44);
                graphics.renderOutline(addBtnX, addBtnY, 18, 16, addHover ? 0xFF55AAFF : 0xFF355580);
                graphics.drawCenteredString(font, "➕", addBtnX + 9, addBtnY + 4, 0xFFFFFFFF);
            }

            graphics.disableScissor();

            if (subMaxScrollY > 0) {
                int scrollBarH = Math.max(10, (int) ((float) listH / totalH * listH));
                int scrollBarY = (int) (listY + (subScrollY / subMaxScrollY) * (listH - scrollBarH));
                graphics.fill(subX + subW - 3, listY, subX + subW - 1, listY + listH, 0x44000000);
                graphics.fill(subX + subW - 3, scrollBarY, subX + subW - 1, scrollBarY + scrollBarH, 0xFF38BDF8);
            }
        }

        private void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
            if (isDragging) return;

            int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();

            if (activePreviewRecipe != null && activeFlyoutFavorite != null) {
                int subX = getDockX() + EXPANDED_WIDTH + 3;
                int previewAnchorX = subX + SUB_WIDTH + 6;
                com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer.renderEmiPreviewDirect(
                    graphics, activePreviewRecipe, previewAnchorX, activePreviewRowY, mouseX, mouseY, 0, screenW, screenH
                );
                return;
            }

            if (hoveredFavorite != null) {
                if (hoveredFavorite.getRecipe() != null) {
                    int previewAnchorX = getDockX() + EXPANDED_WIDTH + 6;
                    com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer.renderEmiPreviewDirect(
                        graphics, hoveredFavorite.getRecipe(), previewAnchorX, hoveredFavRowY, mouseX, mouseY, 0, screenW, screenH
                    );
                } else {
                    List<Component> tooltip = new ArrayList<>();
                    String name = extractFavoriteName(hoveredFavorite);
                    tooltip.add(Component.literal("§6⭐ " + name));

                    List<dev.emi.emi.api.recipe.EmiRecipe> recipes = findRecipesForFavorite(hoveredFavorite);
                    tooltip.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.recipes_count", recipes.size()).getString()));
                    tooltip.add(Component.literal("§e" + Component.translatable("gui.gtcalcboard.favorites_dock.hover_flyout_hint").getString()));
                    tooltip.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.favorites_dock.click_hint").getString()));
                    tooltip.add(Component.literal("§c" + Component.translatable("gui.gtcalcboard.favorites_dock.remove_hint").getString()));
                    graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
                }
            }
        }

        private void renderFavoriteIcon(GuiGraphics graphics, dev.emi.emi.runtime.EmiFavorite fav, int x, int y) {
            if (fav.getRecipe() != null) {
                dev.emi.emi.api.recipe.EmiRecipe recipe = fav.getRecipe();
                if (!recipe.getOutputs().isEmpty()) {
                    recipe.getOutputs().get(0).render(graphics, x, y, 0, dev.emi.emi.api.stack.EmiIngredient.RENDER_ICON);
                    return;
                }
            }
            if (!fav.getEmiStacks().isEmpty()) {
                fav.getEmiStacks().get(0).render(graphics, x, y, 0, dev.emi.emi.api.stack.EmiIngredient.RENDER_ICON);
            } else {
                graphics.fill(x, y, x + 16, y + 16, 0xFF4A90E2);
            }
        }

        private void renderRecipeIcon(GuiGraphics graphics, dev.emi.emi.api.recipe.EmiRecipe recipe, int x, int y, ResourceLocation preferredWs) {
            if (!recipe.getOutputs().isEmpty() && !recipe.getOutputs().get(0).getEmiStacks().isEmpty()) {
                recipe.getOutputs().get(0).render(graphics, x, y, 0, dev.emi.emi.api.stack.EmiIngredient.RENDER_ICON);
                return;
            }
            if (!recipe.getInputs().isEmpty() && !recipe.getInputs().get(0).getEmiStacks().isEmpty()) {
                recipe.getInputs().get(0).render(graphics, x, y, 0, dev.emi.emi.api.stack.EmiIngredient.RENDER_ICON);
                return;
            }
            if (preferredWs != null) {
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(preferredWs);
                if (item != null) {
                    dev.emi.emi.api.stack.EmiStack.of(item).render(graphics, x, y, 0, dev.emi.emi.api.stack.EmiIngredient.RENDER_ICON);
                    return;
                }
            }
            var workstations = dev.emi.emi.api.EmiApi.getRecipeManager().getWorkstations(recipe.getCategory());
            if (workstations != null && !workstations.isEmpty() && !workstations.get(0).getEmiStacks().isEmpty()) {
                workstations.get(0).getEmiStacks().get(0).render(graphics, x, y, 0, dev.emi.emi.api.stack.EmiIngredient.RENDER_ICON);
                return;
            }
            graphics.fill(x, y, x + 16, y + 16, 0xFF4A90E2);
        }

        private String getRecipeRowDisplayName(dev.emi.emi.api.recipe.EmiRecipe recipe, ResourceLocation preferredWs) {
            if (!recipe.getOutputs().isEmpty()) {
                var stacks = recipe.getOutputs().get(0).getEmiStacks();
                if (!stacks.isEmpty() && !stacks.get(0).getName().getString().isEmpty()) {
                    return stacks.get(0).getName().getString();
                }
            }
            if (!recipe.getInputs().isEmpty()) {
                var stacks = recipe.getInputs().get(0).getEmiStacks();
                if (!stacks.isEmpty() && !stacks.get(0).getName().getString().isEmpty()) {
                    return stacks.get(0).getName().getString();
                }
            }
            if (preferredWs != null) {
                return EmiRecipeConverter.formatName(preferredWs.getPath());
            }
            return getRecipeWorkstationName(recipe);
        }

        private String getRecipeWorkstationName(dev.emi.emi.api.recipe.EmiRecipe recipe) {
            dev.emi.emi.api.recipe.EmiRecipeCategory cat = recipe.getCategory();
            if (cat != null) {
                String name = cat.getName().getString();
                if (!name.isEmpty()) return name;
            }
            if (recipe.getId() != null) {
                String path = recipe.getId().getPath();
                if (path.contains("/")) path = path.substring(0, path.indexOf('/'));
                return path;
            }
            return "Recipe";
        }

        private String extractFavoriteName(dev.emi.emi.runtime.EmiFavorite fav) {
            if (fav.getRecipe() != null) {
                return extractRecipeDisplayName(fav.getRecipe());
            }
            if (!fav.getEmiStacks().isEmpty()) {
                return fav.getEmiStacks().get(0).getName().getString();
            }
            return "Favorite";
        }

        private String extractRecipeDisplayName(dev.emi.emi.api.recipe.EmiRecipe recipe) {
            if (!recipe.getOutputs().isEmpty()) {
                var stacks = recipe.getOutputs().get(0).getEmiStacks();
                if (!stacks.isEmpty()) return stacks.get(0).getName().getString();
            }
            if (recipe.getId() != null) {
                String path = recipe.getId().getPath();
                if (path.contains("/")) path = path.substring(path.lastIndexOf('/') + 1);
                return path;
            }
            return "Recipe";
        }

        private boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!parent.isExpanded()) return false;
            dev.emi.emi.api.recipe.EmiRecipe activeEmi = (activePreviewRecipe != null) ? activePreviewRecipe : (hoveredFavorite != null && hoveredFavorite.getRecipe() != null ? hoveredFavorite.getRecipe() : null);
            if (activeEmi == null) return false;

            Minecraft mc = Minecraft.getInstance();
            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();
            double mouseX = mc.mouseHandler.xpos() * screenW / mc.getWindow().getScreenWidth();
            double mouseY = mc.mouseHandler.ypos() * screenH / mc.getWindow().getScreenHeight();

            int subX = getDockX() + EXPANDED_WIDTH + 3;
            int previewAnchorX = (activeFlyoutFavorite != null) ? (subX + SUB_WIDTH + 6) : (getDockX() + EXPANDED_WIDTH + 6);
            int activeEmiRowY = (activePreviewRecipe != null) ? activePreviewRowY : hoveredFavRowY;

            int[] bounds = com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer.calculateEmiPreviewBounds(activeEmi, previewAnchorX, activeEmiRowY, screenW, screenH);
            if (bounds != null && mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[2] && mouseY >= bounds[1] && mouseY <= bounds[1] + bounds[3]) {
                var ing = com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer.getEmiHoveredIngredient(activeEmi, previewAnchorX, activeEmiRowY, (int) mouseX, (int) mouseY, screenW, screenH);
                if (ing instanceof dev.emi.emi.api.stack.EmiIngredient emiIng) {
                    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
                        dev.emi.emi.api.EmiApi.displayRecipes(emiIng);
                        return true;
                    } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_U) {
                        dev.emi.emi.api.EmiApi.displayUses(emiIng);
                        return true;
                    }
                }
                if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
                    double[] pos = BoardScreen.getNextNodeCenterPosition(screenW, screenH);
                    spawnRecipeNode(activeEmi, pos[0], pos[1]);
                    return true;
                }
            }
            return false;
        }

        private boolean mouseClicked(double mouseX, double mouseY, int button) {
            int dockY = getDockY();
            if (!parent.isExpanded()) {
                if (button == 0 && mouseX >= getDockX() && mouseX <= getDockX() + COLLAPSED_WIDTH && mouseY >= dockY && mouseY <= dockY + HEADER_HEIGHT) {
                    parent.toggle();
                    Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F)
                    );
                    return true;
                }
                return false;
            }

            int maxH = Math.min(240, screen.height - dockY - 60);

            if (button == 0 && mouseX >= getDockX() && mouseX <= getDockX() + EXPANDED_WIDTH && mouseY >= dockY && mouseY <= dockY + HEADER_HEIGHT) {
                parent.toggle();
                Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F)
                );
                return true;
            }

            dev.emi.emi.api.recipe.EmiRecipe activeEmi = (activePreviewRecipe != null) ? activePreviewRecipe : (hoveredFavorite != null && hoveredFavorite.getRecipe() != null ? hoveredFavorite.getRecipe() : null);
            int activeEmiRowY = (activePreviewRecipe != null) ? activePreviewRowY : hoveredFavRowY;
            int subX = getDockX() + EXPANDED_WIDTH + 3;
            int previewAnchorX = (activeFlyoutFavorite != null) ? (subX + SUB_WIDTH + 6) : (getDockX() + EXPANDED_WIDTH + 6);
            int screenW = screen.width;
            int screenH = screen.height;

            if (activeEmi != null) {
                int[] bounds = com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer.calculateEmiPreviewBounds(activeEmi, previewAnchorX, activeEmiRowY, screenW, screenH);
                if (bounds != null && mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[2] && mouseY >= bounds[1] && mouseY <= bounds[1] + bounds[3]) {
                    var ing = com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer.getEmiHoveredIngredient(activeEmi, previewAnchorX, activeEmiRowY, (int) mouseX, (int) mouseY, screenW, screenH);
                    if (ing instanceof dev.emi.emi.api.stack.EmiIngredient emiIng) {
                        if (button == 0) {
                            dev.emi.emi.api.EmiApi.displayRecipes(emiIng);
                            return true;
                        } else if (button == 1) {
                            dev.emi.emi.api.EmiApi.displayUses(emiIng);
                            return true;
                        }
                    }
                    if (button == 0) {
                        double[] pos = BoardScreen.getNextNodeCenterPosition(screenW, screenH);
                        spawnRecipeNode(activeEmi, pos[0], pos[1]);
                        return true;
                    }
                    return true;
                }
            }

            if (activeFlyoutFavorite != null && !activeFlyoutRecipes.isEmpty()) {
                if (mouseX >= subX && mouseX <= subX + SUB_WIDTH && mouseY >= dockY && mouseY <= dockY + maxH) {
                    int listY = dockY + HEADER_HEIGHT + 2;
                    int listH = maxH - HEADER_HEIGHT - 4;

                    for (int i = 0; i < activeFlyoutRecipes.size(); i++) {
                        dev.emi.emi.api.recipe.EmiRecipe recipe = activeFlyoutRecipes.get(i);
                        int rowY = (int) (listY + (i * SUB_ROW_HEIGHT) - subScrollY);

                        if (rowY + SUB_ROW_HEIGHT < listY || rowY > listY + listH) continue;

                        if (mouseX >= subX + 2 && mouseX <= subX + SUB_WIDTH - 2 && mouseY >= rowY && mouseY <= rowY + SUB_ROW_HEIGHT) {
                            int addBtnX = subX + SUB_WIDTH - 24;
                            int addBtnY = rowY + 6;
                            boolean addHover = mouseX >= addBtnX && mouseX <= addBtnX + 18 && mouseY >= addBtnY && mouseY <= addBtnY + 16;

                            if (button == 0 && addHover) {
                                double[] pos = BoardScreen.getNextNodeCenterPosition(screenW, screenH);
                                spawnRecipeNode(recipe, pos[0], pos[1]);
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
            }

            int listY = dockY + HEADER_HEIGHT + 2;
            int listH = maxH - HEADER_HEIGHT - 4;
            List<dev.emi.emi.runtime.EmiFavorite> favorites = getFavorites();

            for (int i = 0; i < favorites.size(); i++) {
                dev.emi.emi.runtime.EmiFavorite fav = favorites.get(i);
                int rowY = (int) (listY + (i * ROW_HEIGHT) - scrollY);

                if (rowY + ROW_HEIGHT < listY || rowY > listY + listH) continue;

                if (mouseX >= getDockX() + 2 && mouseX <= getDockX() + EXPANDED_WIDTH - 2 && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT) {
                    int removeBtnX = getDockX() + EXPANDED_WIDTH - 16;
                    boolean removeHover = mouseX >= removeBtnX && mouseX <= removeBtnX + 12 && mouseY >= rowY + 5 && mouseY <= rowY + 17;

                    if (button == 0 && removeHover) {
                        dev.emi.emi.runtime.EmiFavorites.removeFavorite(fav);
                        clearCache();
                        closeFlyout();
                        Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F)
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

        private boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if ((draggingFavorite != null || draggingFlyoutRecipe != null) && button == 0) {
                if (!isDragging && Math.hypot(mouseX - dragStartX, mouseY - dragStartY) > 5) {
                    isDragging = true;
                }
                return true;
            }
            return false;
        }

        private boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (draggingFlyoutRecipe != null) {
                if (isDragging) {
                    double canvasX = screen.toCanvasX(mouseX);
                    double canvasY = screen.toCanvasY(mouseY);
                    spawnRecipeNode(draggingFlyoutRecipe, canvasX, canvasY);
                } else {
                    int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                    int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
                    double[] pos = BoardScreen.getNextNodeCenterPosition(screenW, screenH);
                    spawnRecipeNode(draggingFlyoutRecipe, pos[0], pos[1]);
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
                    spawnFavoriteNode(draggingFavorite, canvasX, canvasY);
                } else {
                    if (draggingFavorite.getRecipe() != null) {
                        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
                        double[] pos = BoardScreen.getNextNodeCenterPosition(screenW, screenH);
                        spawnFavoriteNode(draggingFavorite, pos[0], pos[1]);
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

        private RecipeNode resolveRecipeNode(dev.emi.emi.runtime.EmiFavorite fav) {
            if (fav.getRecipe() != null) {
                return EmiRecipeConverter.convert(fav.getRecipe());
            }

            if (!fav.getEmiStacks().isEmpty()) {
                var rm = dev.emi.emi.api.EmiApi.getRecipeManager();
                if (rm != null) {
                    for (var stack : fav.getEmiStacks()) {
                        try {
                            dev.emi.emi.api.recipe.EmiRecipe def = dev.emi.emi.bom.BoM.getRecipe(stack);
                            if (def != null) {
                                RecipeNode node = EmiRecipeConverter.convert(def);
                                if (node != null) return node;
                            }
                        } catch (Throwable ignored) {}

                        var outRecipes = rm.getRecipesByOutput(stack);
                        if (outRecipes != null && !outRecipes.isEmpty()) {
                            RecipeNode node = EmiRecipeConverter.convert(outRecipes.get(0));
                            if (node != null) return node;
                        }
                    }
                }
            }

            String name = extractFavoriteName(fav);
            RecipeNode fallback = new RecipeNode(java.util.UUID.randomUUID().toString(), name, 20.0, 100.0, com.gtceu.calcboard.api.type.GTVoltageTier.ULV);
            if (!fav.getEmiStacks().isEmpty()) {
                var stack = fav.getEmiStacks().get(0);
                if (stack.getItemStack() != null && !stack.getItemStack().isEmpty()) {
                    var item = stack.getItemStack().getItem();
                    var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
                    if (id != null) {
                        fallback.getOutputs().add(IngredientStack.item(id, name, 1.0));
                    }
                }
            }
            return fallback;
        }

        private void spawnFavoriteNode(dev.emi.emi.runtime.EmiFavorite fav, double canvasX, double canvasY) {
            RecipeNode node = resolveRecipeNode(fav);
            if (node == null) return;

            node.setPosX(canvasX);
            node.setPosY(canvasY);
            screen.addNode(node);
            screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.AddNodesCommand(node, "Add from Favorites Dock"));

            String name = extractFavoriteName(fav);
            BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.recipe_added", name)));
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F)
            );

            screen.rebuildWidgets();
            screen.markSummaryDirty();
        }

        private void spawnRecipeNode(dev.emi.emi.api.recipe.EmiRecipe recipe, double canvasX, double canvasY) {
            com.gtceu.calcboard.api.model.CompoundRecipeBuilder.CompoundCluster cluster =
                    com.gtceu.calcboard.integration.emi.EmiStepRecipeDetector.tryDetectAndBuild(recipe, null, canvasX, canvasY);
            if (cluster != null && !cluster.nodes().isEmpty()) {
                for (RecipeNode n : cluster.nodes()) {
                    screen.addNode(n);
                }
                if (cluster.frame() != null) {
                    screen.getGraph().addFrame(cluster.frame());
                }
                for (com.gtceu.calcboard.api.model.FlowGraph.ConnectionEdge edge : cluster.internalEdges()) {
                    screen.getGraph().addConnection(edge.fromNodeId(), edge.outputIndex(), edge.toNodeId(), edge.inputIndex());
                }
                String name = extractRecipeDisplayName(recipe);
                BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.recipe_added", name)));
                Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F)
                );
                screen.rebuildWidgets();
                screen.markSummaryDirty();
                return;
            }

            RecipeNode node = EmiRecipeConverter.convert(recipe);
            if (node == null) return;

            node.setPosX(canvasX);
            node.setPosY(canvasY);
            screen.addNode(node);
            screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.AddNodesCommand(node, "Add from Favorites Flyout"));

            String name = extractRecipeDisplayName(recipe);
            BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.recipe_added", name)));
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F)
            );

            screen.rebuildWidgets();
            screen.markSummaryDirty();
        }

        private boolean mouseScrolled(double mouseX, double mouseY, double delta) {
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
}




