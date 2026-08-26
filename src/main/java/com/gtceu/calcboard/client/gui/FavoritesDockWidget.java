package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.BoardManager;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.*;

/**
 * Collapsible Favorites Dock Widget located at the Top-Left of BoardScreen.
 * Displays pinned recipes and items from EMI favorites (A key) with exact 1:1 mapping.
 * When hovering over or selecting an item favorite with multiple recipes, opens an
 * interactive right-side flyout panel allowing users to scroll, select, or drag & drop
 * any specific producing recipe onto the canvas.
 */
public class FavoritesDockWidget {

    private final BoardScreen screen;
    private boolean expanded;
    private double scrollY = 0;
    private double maxScrollY = 0;

    // Sub-flyout panel state
    private double subScrollY = 0;
    private double subMaxScrollY = 0;
    private EmiFavorite activeFlyoutFavorite = null;
    private List<EmiRecipe> activeFlyoutRecipes = new ArrayList<>();
    private EmiRecipe hoveredFlyoutRecipe = null;

    public int getDockX() {
        return 8;
    }

    private static final int EXPANDED_WIDTH = 145;
    private static final int COLLAPSED_WIDTH = 95;
    private static final int HEADER_HEIGHT = 18;
    private static final int ROW_HEIGHT = 22;

    public int getDockY() {
        return screen.getFavoritesDockY();
    }

    private static final int SUB_WIDTH = 185;
    private static final int SUB_ROW_HEIGHT = 28;

    // Drag-and-drop state
    private EmiFavorite draggingFavorite = null;
    private EmiRecipe draggingFlyoutRecipe = null;
    private double dragStartX = 0;
    private double dragStartY = 0;
    private boolean isDragging = false;

    // Hover tooltip tracking
    private EmiFavorite hoveredFavorite = null;
    private int hoveredFavRowY = 0;
    private EmiRecipe activePreviewRecipe = null;
    private int activePreviewRowY = 0;

    public FavoritesDockWidget(BoardScreen screen) {
        this.screen = screen;
        this.expanded = BoardManager.getInstance().isFavoritesDockExpanded();
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        BoardManager.getInstance().setFavoritesDockExpanded(expanded);
        if (!expanded) {
            closeFlyout();
        }
    }

    public void toggle() {
        setExpanded(!this.expanded);
    }

    private void closeFlyout() {
        activeFlyoutFavorite = null;
        activeFlyoutRecipes.clear();
        subScrollY = 0;
        activePreviewRecipe = null;
        hoveredFavorite = null;
    }

    public boolean isEmiLoading() {
        try {
            if (!dev.emi.emi.runtime.EmiReloadManager.isLoaded()) {
                return true;
            }
            var rm = EmiApi.getRecipeManager();
            if (rm == null || rm.getRecipes().isEmpty()) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static final Map<EmiFavorite, List<EmiRecipe>> FAVORITE_RECIPES_CACHE = new WeakHashMap<>();

    public static void clearCache() {
        FAVORITE_RECIPES_CACHE.clear();
    }

    public List<EmiFavorite> getFavorites() {
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

    public List<EmiRecipe> findRecipesForFavorite(EmiFavorite fav) {
        if (fav == null) return List.of();
        List<EmiRecipe> cached = FAVORITE_RECIPES_CACHE.get(fav);
        if (cached != null) return cached;

        List<EmiRecipe> list = new ArrayList<>();
        if (fav.getRecipe() != null) {
            list.add(fav.getRecipe());
            FAVORITE_RECIPES_CACHE.put(fav, list);
            return list;
        }

        if (!fav.getEmiStacks().isEmpty()) {
            var rm = EmiApi.getRecipeManager();
            if (rm != null) {
                var filterConfig = com.gtceu.calcboard.client.gui.search.RecipeFilterConfig.getInstance();
                EmiRecipe defaultRecipe = null;
                try {
                    for (var stack : fav.getEmiStacks()) {
                        EmiRecipe def = dev.emi.emi.bom.BoM.getRecipe(stack);
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

                    // Look up all recipes for categories where this stack is a Workstation
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
                                    List<EmiRecipe> catRecipes = rm.getRecipes(cat);
                                    if (catRecipes != null) {
                                        for (EmiRecipe cr : catRecipes) {
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

                // If EMI Default Recipe is set, prioritize it at the top (index 0)
                if (defaultRecipe != null && list.contains(defaultRecipe)) {
                    list.remove(defaultRecipe);
                    list.add(0, defaultRecipe);
                }
            }
        }
        FAVORITE_RECIPES_CACHE.put(fav, list);
        return list;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        boolean loading = isEmiLoading();
        List<EmiFavorite> favorites = getFavorites();
        int count = favorites.size();
        hoveredFavorite = null;

        if (activeFlyoutFavorite != null && !favorites.contains(activeFlyoutFavorite)) {
            closeFlyout();
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);

        int dockY = getDockY();
        String countDisplay = loading ? Component.translatable("gui.gtcalcboard.favorites_dock.loading").getString() : String.valueOf(count);

        if (!expanded) {
            // Collapsed Tab Button [⭐ Favorites (N / Loading...) ▶]
            boolean hovered = mouseX >= getDockX() && mouseX <= getDockX() + COLLAPSED_WIDTH && mouseY >= dockY && mouseY <= dockY + HEADER_HEIGHT;
            int bg = hovered ? 0xEE1E293B : 0xAA0F172A;
            int border = hovered ? 0xFFFFD700 : (loading ? 0xFFF59E0B : 0xFF475569);

            graphics.fill(getDockX(), dockY, getDockX() + COLLAPSED_WIDTH, dockY + HEADER_HEIGHT, bg);
            graphics.renderOutline(getDockX(), dockY, COLLAPSED_WIDTH, HEADER_HEIGHT, border);

            String title = "⭐ " + Component.translatable("gui.gtcalcboard.favorites").getString() + " (" + countDisplay + ") ▶";
            graphics.drawString(font, font.plainSubstrByWidth(title, COLLAPSED_WIDTH - 6), getDockX() + 6, dockY + 5, hovered ? 0xFFFFD700 : (loading ? 0xFFFDE047 : 0xFFE2E8F0), false);
        } else {
            // Expanded Dock Panel
            int maxH = Math.min(240, screen.height - dockY - 60);
            int contentH = maxH - HEADER_HEIGHT;

            int bg = 0xF00F172A;
            int border = loading ? 0xFFF59E0B : 0xFF38BDF8;

            graphics.fill(getDockX(), dockY, getDockX() + EXPANDED_WIDTH, dockY + maxH, bg);
            graphics.renderOutline(getDockX(), dockY, EXPANDED_WIDTH, maxH, border);

            // Header [⭐ Favorites (N / Loading...) ◀]
            boolean headerHover = mouseX >= getDockX() && mouseX <= getDockX() + EXPANDED_WIDTH && mouseY >= dockY && mouseY <= dockY + HEADER_HEIGHT;
            graphics.fill(getDockX() + 1, dockY + 1, getDockX() + EXPANDED_WIDTH - 1, dockY + HEADER_HEIGHT, headerHover ? 0xFF1E293B : 0xFF172033);
            graphics.fill(getDockX() + 1, dockY + HEADER_HEIGHT, getDockX() + EXPANDED_WIDTH - 1, dockY + HEADER_HEIGHT + 1, 0xFF334155);

            String title = "⭐ " + Component.translatable("gui.gtcalcboard.favorites").getString() + " (" + countDisplay + ")";
            graphics.drawString(font, font.plainSubstrByWidth(title, EXPANDED_WIDTH - 24), getDockX() + 6, dockY + 5, loading ? 0xFFFDE047 : 0xFFFFD700, false);
            graphics.drawString(font, "◀", getDockX() + EXPANDED_WIDTH - 14, dockY + 5, headerHover ? 0xFFFF5555 : 0xFF94A3B8, false);

            // Content Area
            int listY = dockY + HEADER_HEIGHT + 2;
            int listH = contentH - 4;

            int subX = getDockX() + EXPANDED_WIDTH + 3;
            int screenW = screen.width;
            int screenH = screen.height;

            EmiRecipe activeEmiRecipe = (activePreviewRecipe != null) ? activePreviewRecipe : (hoveredFavorite != null && hoveredFavorite.getRecipe() != null ? hoveredFavorite.getRecipe() : null);
            int activeEmiRowY = (activePreviewRecipe != null) ? activePreviewRowY : hoveredFavRowY;
            int previewAnchorX = (activeFlyoutFavorite != null) ? (subX + SUB_WIDTH + 6) : (getDockX() + EXPANDED_WIDTH + 6);

            int[] previewBounds = (activeEmiRecipe != null) ? com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer.calculateEmiPreviewBounds(activeEmiRecipe, previewAnchorX, activeEmiRowY, screenW, screenH) : null;

            boolean mouseInPreview = previewBounds != null && mouseX >= previewBounds[0] && mouseX <= previewBounds[0] + previewBounds[2] && mouseY >= previewBounds[1] && mouseY <= previewBounds[1] + previewBounds[3];
            int totalDockRight = (activeFlyoutFavorite != null) ? (subX + SUB_WIDTH) : (getDockX() + EXPANDED_WIDTH);
            boolean mouseInDockArea = mouseX >= getDockX() && mouseX <= totalDockRight && mouseY >= dockY && mouseY <= dockY + maxH;

            // Seamless bridge between Sub Panel and Preview Panel so fast/slow cursor movement doesn't drop hover
            boolean mouseInBridge = false;
            if (activeFlyoutFavorite != null && previewBounds != null) {
                int bridgeLeft = subX + SUB_WIDTH;
                int bridgeRight = previewBounds[0];
                int bridgeTop = Math.min(dockY, previewBounds[1]);
                int bridgeBottom = Math.max(dockY + maxH, previewBounds[1] + previewBounds[3]);
                mouseInBridge = (mouseX >= bridgeLeft && mouseX <= bridgeRight && mouseY >= bridgeTop && mouseY <= bridgeBottom);
            }

            if (loading) {
                int dotCount = (int) ((System.currentTimeMillis() / 400) % 4);
                String dots = ".".repeat(dotCount);
                graphics.drawString(font, "§e⏳ " + Component.translatable("gui.gtcalcboard.favorites_dock.loading_emi").getString() + dots, getDockX() + 8, listY + 8, 0xFFFDE047, false);
                graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.favorites_dock.loading_hint").getString(), getDockX() + 8, listY + 22, 0xFF64748B, false);
                closeFlyout();
            } else if (favorites.isEmpty()) {
                graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.favorites_dock.empty").getString(), getDockX() + 8, listY + 8, 0xFF888888, false);
                graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.favorites_dock.empty_hint").getString(), getDockX() + 8, listY + 22, 0xFF666666, false);
                closeFlyout();
            } else {
                int totalH = favorites.size() * ROW_HEIGHT;
                maxScrollY = Math.max(0, totalH - listH);
                scrollY = Math.max(0, Math.min(maxScrollY, scrollY));

                graphics.enableScissor(getDockX() + 2, listY, getDockX() + EXPANDED_WIDTH - 2, listY + listH);

                int curY = listY - (int) scrollY;
                for (EmiFavorite fav : favorites) {
                    if (curY + ROW_HEIGHT >= listY && curY <= listY + listH) {
                        boolean rowHover = mouseX >= getDockX() + 4 && mouseX <= getDockX() + EXPANDED_WIDTH - 4 && mouseY >= curY && mouseY <= curY + ROW_HEIGHT - 2 && mouseY >= listY && mouseY <= listY + listH;
                        boolean isFlyoutActive = (activeFlyoutFavorite == fav);

                        if (rowHover) {
                            hoveredFavorite = fav;
                            hoveredFavRowY = curY;
                            if (activeFlyoutFavorite != fav) {
                                activeFlyoutFavorite = fav;
                                activeFlyoutRecipes = findRecipesForFavorite(fav);
                                subScrollY = 0;
                                activePreviewRecipe = !activeFlyoutRecipes.isEmpty() ? activeFlyoutRecipes.get(0) : null;
                                activePreviewRowY = dockY + HEADER_HEIGHT + 2;
                            }
                        }

                        if (rowHover || isFlyoutActive) {
                            graphics.fill(getDockX() + 4, curY, getDockX() + EXPANDED_WIDTH - 4, curY + ROW_HEIGHT - 2, isFlyoutActive ? 0xFF2A3649 : 0xFF1E293B);
                            graphics.renderOutline(getDockX() + 4, curY, EXPANDED_WIDTH - 8, ROW_HEIGHT - 2, isFlyoutActive ? 0xFF38BDF8 : 0xFF64748B);
                        }

                        // Render Favorite Icon
                        renderFavoriteIcon(graphics, fav, getDockX() + 6, curY + 2);

                        // Favorite Name
                        String name = extractFavoriteName(fav);
                        graphics.drawString(font, font.plainSubstrByWidth(name, EXPANDED_WIDTH - 44), getDockX() + 26, curY + 5, (rowHover || isFlyoutActive) ? 0xFFFFFFFF : 0xFFCBD5E1, false);

                        // Sub-indicator (▶ / 1)
                        if (fav.getRecipe() != null) {
                            graphics.drawString(font, "§81", getDockX() + EXPANDED_WIDTH - 14, curY + 5, 0xFF64748B, false);
                        } else {
                            graphics.drawString(font, "§e▶", getDockX() + EXPANDED_WIDTH - 14, curY + 5, 0xFFE2E8F0, false);
                        }
                    }
                    curY += ROW_HEIGHT;
                }

                graphics.disableScissor();

                // Scrollbar
                if (maxScrollY > 0) {
                    int barH = Math.max(12, (int) ((float) listH / (float) totalH * listH));
                    int barY = listY + (int) ((float) scrollY / (float) maxScrollY * (listH - barH));
                    graphics.fill(getDockX() + EXPANDED_WIDTH - 4, barY, getDockX() + EXPANDED_WIDTH - 2, barY + barH, 0xFF475569);
                }

                // Check if mouse left all Main Dock, Sub Panel, Bridge, and Preview Panel
                if (!mouseInDockArea && !mouseInBridge && !mouseInPreview && !isDragging) {
                    closeFlyout();
                }
            }

            // Render Sub-Flyout Recipe Selector Panel
            if (activeFlyoutFavorite != null && !activeFlyoutRecipes.isEmpty()) {
                renderSubFlyout(graphics, font, mouseX, mouseY, subX, dockY, maxH, mouseInPreview || mouseInBridge);
            }
        }

        // Render Dragging Ghost Item under Cursor
        if (isDragging) {
            if (draggingFlyoutRecipe != null) {
                ResourceLocation prefWs = (activeFlyoutFavorite != null && !activeFlyoutFavorite.getEmiStacks().isEmpty()) ? activeFlyoutFavorite.getEmiStacks().get(0).getId() : null;
                renderRecipeIcon(graphics, draggingFlyoutRecipe, mouseX - 8, mouseY - 8, prefWs);
                String name = extractRecipeDisplayName(draggingFlyoutRecipe);
                graphics.drawString(font, "§a+ " + name, mouseX + 12, mouseY - 4, 0xFFFFFFFF, true);
            } else if (draggingFavorite != null) {
                renderFavoriteIcon(graphics, draggingFavorite, mouseX - 8, mouseY - 8);
                String name = extractFavoriteName(draggingFavorite);
                graphics.drawString(font, "§a+ " + name, mouseX + 12, mouseY - 4, 0xFFFFFFFF, true);
            }
        }

        graphics.pose().popPose();
    }

    private void renderSubFlyout(GuiGraphics graphics, Font font, int mouseX, int mouseY, int subX, int subY, int maxH, boolean previewActive) {
        int subH = maxH;
        int count = activeFlyoutRecipes.size();

        graphics.fill(subX, subY, subX + SUB_WIDTH, subY + subH, 0xF80B111E);
        graphics.renderOutline(subX, subY, SUB_WIDTH, subH, 0xFF38BDF8);

        // Header
        graphics.fill(subX + 1, subY + 1, subX + SUB_WIDTH - 1, subY + HEADER_HEIGHT, 0xFF172033);
        graphics.fill(subX + 1, subY + HEADER_HEIGHT, subX + SUB_WIDTH - 1, subY + HEADER_HEIGHT + 1, 0xFF334155);

        String favName = extractFavoriteName(activeFlyoutFavorite);
        String headerTitle = font.plainSubstrByWidth(favName + " (" + count + ")", SUB_WIDTH - 12);
        graphics.drawString(font, headerTitle, subX + 6, subY + 5, 0xFF38BDF8, false);

        int listY = subY + HEADER_HEIGHT + 2;
        int listH = subH - HEADER_HEIGHT - 4;

        int totalH = count * SUB_ROW_HEIGHT;
        subMaxScrollY = Math.max(0, totalH - listH);
        subScrollY = Math.max(0, Math.min(subMaxScrollY, subScrollY));

        graphics.enableScissor(subX + 2, listY, subX + SUB_WIDTH - 2, listY + listH);

        int curY = listY - (int) subScrollY;
        for (EmiRecipe recipe : activeFlyoutRecipes) {
            if (curY + SUB_ROW_HEIGHT >= listY && curY <= listY + listH) {
                boolean rowHover = mouseX >= subX + 4 && mouseX <= subX + SUB_WIDTH - 4 && mouseY >= curY && mouseY <= curY + SUB_ROW_HEIGHT - 2 && mouseY >= listY && mouseY <= listY + listH;
                boolean isSelected = (activePreviewRecipe == recipe);

                if (rowHover) {
                    activePreviewRecipe = recipe;
                    activePreviewRowY = curY;
                }

                boolean isDefault = false;
                try {
                    if (activeFlyoutFavorite != null && !activeFlyoutFavorite.getEmiStacks().isEmpty()) {
                        EmiRecipe def = dev.emi.emi.bom.BoM.getRecipe(activeFlyoutFavorite.getEmiStacks().get(0));
                        if (def != null && def.equals(recipe)) {
                            isDefault = true;
                        }
                    }
                } catch (Throwable ignored) {}

                if (rowHover || (previewActive && isSelected)) {
                    graphics.fill(subX + 4, curY, subX + SUB_WIDTH - 4, curY + SUB_ROW_HEIGHT - 2, 0xFF1E293B);
                    graphics.renderOutline(subX + 4, curY, SUB_WIDTH - 8, SUB_ROW_HEIGHT - 2, 0xFFFFD700);
                } else if (isDefault) {
                    graphics.fill(subX + 4, curY, subX + SUB_WIDTH - 4, curY + SUB_ROW_HEIGHT - 2, 0xFF1B2436);
                    graphics.renderOutline(subX + 4, curY, SUB_WIDTH - 8, SUB_ROW_HEIGHT - 2, 0xFF38BDF8);
                } else {
                    graphics.fill(subX + 4, curY, subX + SUB_WIDTH - 4, curY + SUB_ROW_HEIGHT - 2, 0xFF131C2E);
                }

                ResourceLocation prefWs = (activeFlyoutFavorite != null && !activeFlyoutFavorite.getEmiStacks().isEmpty()) ? activeFlyoutFavorite.getEmiStacks().get(0).getId() : null;

                // Render Workstation / Recipe Icon
                renderRecipeIcon(graphics, recipe, subX + 6, curY + 4, prefWs);

                // Recipe / Machine Name Line
                String machineName = getRecipeRowDisplayName(recipe, prefWs);
                String label = isDefault ? ("§6★ §r" + machineName) : machineName;
                graphics.drawString(font, font.plainSubstrByWidth(label, SUB_WIDTH - 30), subX + 26, curY + 3, (rowHover || (previewActive && isSelected)) ? 0xFFFFD700 : (isDefault ? 0xFF38BDF8 : 0xFFFFFFFF), false);

                // Specs Line (Time / EU/t / Steam)
                RecipeNode node = EmiRecipeConverter.convert(recipe, prefWs);
                if (node != null) {
                    double dur = node.getBaseDurationTicks();
                    double eut = node.getBaseEUt();
                    String spec;
                    if (node.isGenerator()) {
                        spec = String.format("§7%.1fs §8| §a+%.0f EU/t §7(%s)", dur / 20.0, eut, node.getTargetTier().name());
                    } else if (eut <= 0.0) {
                        IngredientStack steamOut = node.getOutputs().stream().filter(o -> o.getId() != null && o.getId().getPath().contains("steam")).findFirst().orElse(null);
                        if (steamOut != null) {
                            double rate = (steamOut.getAmount() / (dur / 20.0)) / 20.0;
                            spec = String.format("§7%.1fs §8| §b+%.0f mB/t Steam", dur / 20.0, rate);
                        } else {
                            spec = String.format("§7%.1fs §8| §70 EU/t", dur / 20.0);
                        }
                    } else {
                        spec = String.format("§7%.1fs §8| §e%.0f EU/t §7(%s)", dur / 20.0, eut, node.getTargetTier().name());
                    }
                    graphics.drawString(font, font.plainSubstrByWidth(spec, SUB_WIDTH - 30), subX + 26, curY + 14, 0xFF94A3B8, false);
                }
            }
            curY += SUB_ROW_HEIGHT;
        }

        graphics.disableScissor();

        // Sub Scrollbar
        if (subMaxScrollY > 0) {
            int barH = Math.max(12, (int) ((float) listH / (float) totalH * listH));
            int barY = listY + (int) ((float) subScrollY / (float) subMaxScrollY * (listH - barH));
            graphics.fill(subX + SUB_WIDTH - 4, barY, subX + SUB_WIDTH - 2, barY + barH, 0xFF38BDF8);
        }
    }

    public void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
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

                List<EmiRecipe> recipes = findRecipesForFavorite(hoveredFavorite);
                tooltip.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.recipes_count", recipes.size()).getString()));
                tooltip.add(Component.literal("§e" + Component.translatable("gui.gtcalcboard.favorites_dock.hover_flyout_hint").getString()));
                tooltip.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.favorites_dock.click_hint").getString()));
                tooltip.add(Component.literal("§c" + Component.translatable("gui.gtcalcboard.favorites_dock.remove_hint").getString()));
                graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private void renderFavoriteIcon(GuiGraphics graphics, EmiFavorite fav, int x, int y) {
        if (fav.getRecipe() != null) {
            EmiRecipe recipe = fav.getRecipe();
            if (!recipe.getOutputs().isEmpty()) {
                recipe.getOutputs().get(0).render(graphics, x, y, 0, EmiIngredient.RENDER_ICON);
                return;
            }
        }
        if (!fav.getEmiStacks().isEmpty()) {
            fav.getEmiStacks().get(0).render(graphics, x, y, 0, EmiIngredient.RENDER_ICON);
        } else {
            graphics.fill(x, y, x + 16, y + 16, 0xFF4A90E2);
        }
    }

    private void renderRecipeIcon(GuiGraphics graphics, EmiRecipe recipe, int x, int y, ResourceLocation preferredWs) {
        if (!recipe.getOutputs().isEmpty() && !recipe.getOutputs().get(0).getEmiStacks().isEmpty()) {
            recipe.getOutputs().get(0).render(graphics, x, y, 0, EmiIngredient.RENDER_ICON);
            return;
        }
        if (!recipe.getInputs().isEmpty() && !recipe.getInputs().get(0).getEmiStacks().isEmpty()) {
            recipe.getInputs().get(0).render(graphics, x, y, 0, EmiIngredient.RENDER_ICON);
            return;
        }
        if (preferredWs != null) {
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(preferredWs);
            if (item != null) {
                dev.emi.emi.api.stack.EmiStack.of(item).render(graphics, x, y, 0, EmiIngredient.RENDER_ICON);
                return;
            }
        }
        var workstations = dev.emi.emi.api.EmiApi.getRecipeManager().getWorkstations(recipe.getCategory());
        if (workstations != null && !workstations.isEmpty() && !workstations.get(0).getEmiStacks().isEmpty()) {
            workstations.get(0).getEmiStacks().get(0).render(graphics, x, y, 0, EmiIngredient.RENDER_ICON);
            return;
        }
        graphics.fill(x, y, x + 16, y + 16, 0xFF4A90E2);
    }

    private String getRecipeRowDisplayName(EmiRecipe recipe, ResourceLocation preferredWs) {
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

    private String getRecipeWorkstationName(EmiRecipe recipe) {
        EmiRecipeCategory cat = recipe.getCategory();
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

    private String extractFavoriteName(EmiFavorite fav) {
        if (fav.getRecipe() != null) {
            return extractRecipeDisplayName(fav.getRecipe());
        }
        if (!fav.getEmiStacks().isEmpty()) {
            return fav.getEmiStacks().get(0).getName().getString();
        }
        return "Favorite";
    }

    private String extractRecipeDisplayName(EmiRecipe recipe) {
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

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!expanded) return false;
        EmiRecipe activeEmi = (activePreviewRecipe != null) ? activePreviewRecipe : (hoveredFavorite != null && hoveredFavorite.getRecipe() != null ? hoveredFavorite.getRecipe() : null);
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
            if (ing != null) {
                if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
                    EmiApi.displayRecipes(ing);
                    return true;
                } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_U) {
                    EmiApi.displayUses(ing);
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

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int dockY = getDockY();
        if (!expanded) {
            if (button == 0 && mouseX >= getDockX() && mouseX <= getDockX() + COLLAPSED_WIDTH && mouseY >= dockY && mouseY <= dockY + HEADER_HEIGHT) {
                toggle();
                Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
                );
                return true;
            }
            return false;
        }

        int maxH = Math.min(240, screen.height - dockY - 60);

        // Header click -> Toggle Collapsed
        if (button == 0 && mouseX >= getDockX() && mouseX <= getDockX() + EXPANDED_WIDTH && mouseY >= dockY && mouseY <= dockY + HEADER_HEIGHT) {
            toggle();
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
            );
            return true;
        }

        // 3rd-Tier EMI Preview Card Interaction
        EmiRecipe activeEmi = (activePreviewRecipe != null) ? activePreviewRecipe : (hoveredFavorite != null && hoveredFavorite.getRecipe() != null ? hoveredFavorite.getRecipe() : null);
        int activeEmiRowY = (activePreviewRecipe != null) ? activePreviewRowY : hoveredFavRowY;
        int subX = getDockX() + EXPANDED_WIDTH + 3;
        int previewAnchorX = (activeFlyoutFavorite != null) ? (subX + SUB_WIDTH + 6) : (getDockX() + EXPANDED_WIDTH + 6);
        int screenW = screen.width;
        int screenH = screen.height;

        if (activeEmi != null) {
            int[] bounds = com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer.calculateEmiPreviewBounds(activeEmi, previewAnchorX, activeEmiRowY, screenW, screenH);
            if (bounds != null && mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[2] && mouseY >= bounds[1] && mouseY <= bounds[1] + bounds[3]) {
                var ing = com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer.getEmiHoveredIngredient(activeEmi, previewAnchorX, activeEmiRowY, (int) mouseX, (int) mouseY, screenW, screenH);
                if (ing != null) {
                    if (button == 0) {
                        EmiApi.displayRecipes(ing);
                    } else if (button == 1) {
                        EmiApi.displayUses(ing);
                    }
                    Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                } else {
                    // Click on preview card body/header -> add recipe to board!
                    if (button == 0) {
                        double[] pos = BoardScreen.getNextNodeCenterPosition(screenW, screenH);
                        spawnRecipeNode(activeEmi, pos[0], pos[1]);
                        return true;
                    }
                }
            }
        }

        if (button == 1) {
            // Right-click on Main Dock item -> Remove Favorite
            int listY = dockY + HEADER_HEIGHT + 2;
            int listH = maxH - HEADER_HEIGHT - 4;
            if (mouseX >= getDockX() && mouseX <= getDockX() + EXPANDED_WIDTH && mouseY >= listY && mouseY <= listY + listH) {
                List<EmiFavorite> favorites = getFavorites();
                int curY = listY - (int) scrollY;
                for (EmiFavorite fav : favorites) {
                    if (mouseY >= curY && mouseY <= curY + ROW_HEIGHT - 2) {
                        try {
                            EmiFavorites.removeFavorite(fav);
                            RecipeSearchDialog.notifyFavoritesChanged();
                        } catch (Throwable ignored) {}
                        closeFlyout();
                        Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 0.8F)
                        );
                        return true;
                    }
                    curY += ROW_HEIGHT;
                }
            }
            return false;
        }

        if (button != 0) return false;

        // Sub-Flyout interaction
        if (activeFlyoutFavorite != null && !activeFlyoutRecipes.isEmpty()) {
            int subListY = dockY + HEADER_HEIGHT + 2;
            int subListH = maxH - HEADER_HEIGHT - 4;
            if (mouseX >= subX && mouseX <= subX + SUB_WIDTH && mouseY >= subListY && mouseY <= subListY + subListH) {
                int curY = subListY - (int) subScrollY;
                for (EmiRecipe recipe : activeFlyoutRecipes) {
                    if (mouseY >= curY && mouseY <= curY + SUB_ROW_HEIGHT - 2) {
                        this.draggingFlyoutRecipe = recipe;
                        this.draggingFavorite = null;
                        this.dragStartX = mouseX;
                        this.dragStartY = mouseY;
                        this.isDragging = false;
                        return true;
                    }
                    curY += SUB_ROW_HEIGHT;
                }
                return true;
            }
        }

        // Main Dock content list
        int listY = dockY + HEADER_HEIGHT + 2;
        int listH = maxH - HEADER_HEIGHT - 4;
        if (mouseX >= getDockX() && mouseX <= getDockX() + EXPANDED_WIDTH && mouseY >= listY && mouseY <= listY + listH) {
            List<EmiFavorite> favorites = getFavorites();
            int curY = listY - (int) scrollY;
            for (EmiFavorite fav : favorites) {
                if (mouseY >= curY && mouseY <= curY + ROW_HEIGHT - 2) {
                    // Start drag or prepare click
                    this.draggingFavorite = fav;
                    this.draggingFlyoutRecipe = null;
                    this.dragStartX = mouseX;
                    this.dragStartY = mouseY;
                    this.isDragging = false;
                    return true;
                }
                curY += ROW_HEIGHT;
            }
            return true;
        }

        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if ((draggingFavorite != null || draggingFlyoutRecipe != null) && button == 0) {
            if (!isDragging && Math.hypot(mouseX - dragStartX, mouseY - dragStartY) > 5) {
                isDragging = true;
            }
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingFlyoutRecipe != null) {
            if (isDragging) {
                // Dropped from flyout onto canvas
                double canvasX = screen.toCanvasX(mouseX);
                double canvasY = screen.toCanvasY(mouseY);
                spawnRecipeNode(draggingFlyoutRecipe, canvasX, canvasY);
            } else {
                // Single-click on flyout row
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
                // Drop onto canvas!
                double canvasX = screen.toCanvasX(mouseX);
                double canvasY = screen.toCanvasY(mouseY);
                spawnFavoriteNode(draggingFavorite, canvasX, canvasY);
            } else {
                // Single-click on main item -> Toggle Flyout or spawn if single
                if (draggingFavorite.getRecipe() != null) {
                    int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                    int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
                    double[] pos = BoardScreen.getNextNodeCenterPosition(screenW, screenH);
                    spawnFavoriteNode(draggingFavorite, pos[0], pos[1]);
                } else {
                    // Open/Refresh Flyout
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

    private RecipeNode resolveRecipeNode(EmiFavorite fav) {
        if (fav.getRecipe() != null) {
            return EmiRecipeConverter.convert(fav.getRecipe());
        }
        List<EmiRecipe> recipes = findRecipesForFavorite(fav);
        if (!recipes.isEmpty()) {
            // Prioritize GTCEu machine recipe or first recipe
            EmiRecipe best = recipes.get(0);
            for (EmiRecipe r : recipes) {
                if (r.getId() != null && r.getId().getNamespace().equals("gtceu")) {
                    best = r;
                    break;
                }
            }
            ResourceLocation prefWs = !fav.getEmiStacks().isEmpty() ? fav.getEmiStacks().get(0).getId() : null;
            return EmiRecipeConverter.convert(best, prefWs);
        }
        if (!fav.getEmiStacks().isEmpty()) {
            // Fallback: Create Raw Material Input Node for this item stack
            EmiStack stack = fav.getEmiStacks().get(0);
            RecipeNode rawNode = RecipeNode.create(stack.getName().getString(), 20.0, 0.0, com.gtceu.calcboard.api.GTVoltageTier.ULV);
            ResourceLocation id = stack.getId();
            rawNode.addOutput(IngredientStack.item(id, stack.getName().getString(), stack.getAmount(), 1.0));
            return rawNode;
        }
        return null;
    }

    private void spawnFavoriteNode(EmiFavorite fav, double canvasX, double canvasY) {
        RecipeNode node = resolveRecipeNode(fav);
        if (node == null) return;

        node.setPosX(canvasX);
        node.setPosY(canvasY);

        screen.getGraph().addNode(node);
        screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.AddNodesCommand(node, "Add from Favorites Dock"));

        String name = extractFavoriteName(fav);
        BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.recipe_added", name)));
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F)
        );

        screen.rebuildWidgets();
        screen.markSummaryDirty();
    }

    private void spawnRecipeNode(EmiRecipe recipe, double canvasX, double canvasY) {
        ResourceLocation prefWs = (activeFlyoutFavorite != null && !activeFlyoutFavorite.getEmiStacks().isEmpty()) ? activeFlyoutFavorite.getEmiStacks().get(0).getId() : null;
        RecipeNode node = EmiRecipeConverter.convert(recipe, prefWs);
        if (node == null) return;

        node.setPosX(canvasX);
        node.setPosY(canvasY);

        screen.getGraph().addNode(node);
        screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.AddNodesCommand(node, "Add from Favorites Flyout"));

        String name = extractRecipeDisplayName(recipe);
        BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.recipe_added", name)));
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F)
        );

        screen.rebuildWidgets();
        screen.markSummaryDirty();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!expanded) return false;
        int dockY = getDockY();
        int maxH = Math.min(240, screen.height - dockY - 60);

        // Scroll Sub-Flyout if cursor is on it
        int subX = getDockX() + EXPANDED_WIDTH + 3;
        if (activeFlyoutFavorite != null && !activeFlyoutRecipes.isEmpty()) {
            if (mouseX >= subX && mouseX <= subX + SUB_WIDTH && mouseY >= dockY && mouseY <= dockY + maxH) {
                if (subMaxScrollY > 0) {
                    subScrollY = Math.max(0, Math.min(subMaxScrollY, subScrollY - delta * SUB_ROW_HEIGHT));
                    return true;
                }
            }
        }

        // Scroll Main Dock
        if (mouseX >= getDockX() && mouseX <= getDockX() + EXPANDED_WIDTH && mouseY >= dockY && mouseY <= dockY + maxH) {
            if (maxScrollY > 0) {
                scrollY = Math.max(0, Math.min(maxScrollY, scrollY - delta * ROW_HEIGHT));
                return true;
            }
        }
        return false;
    }
}
