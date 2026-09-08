package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer;
import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.runtime.EmiFavorite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public final class EmiFavoritesDockRenderer {

    public static final int EXPANDED_WIDTH = 145;
    public static final int HEADER_HEIGHT = 18;
    public static final int ROW_HEIGHT = 22;
    public static final int SUB_WIDTH = 185;
    public static final int SUB_ROW_HEIGHT = 28;
    public static final int SCROLLBAR_TRACK_WIDTH = 3;
    public static final int SCROLLBAR_HIT_WIDTH = 6;
    public static final int MIN_SCROLLBAR_HEIGHT = 12;

    private EmiFavoritesDockRenderer() {}

    public static void render(EmiFavoritesDockImpl dock, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        boolean loading = dock.isEmiLoading();
        List<EmiFavorite> favorites = dock.getFavorites();
        int count = favorites.size();
        dock.setHoveredFavorite(null);

        BoardScreen screen = dock.getScreen();
        boolean drawerBlocking = (screen.getPageBrowserDrawer() != null && screen.getPageBrowserDrawer().isOpen());
        if (screen.isAnyModalOpen() || drawerBlocking || (dock.getActiveFlyoutFavorite() != null && !favorites.contains(dock.getActiveFlyoutFavorite()))) {
            dock.closeFlyout();
        }

        if (!dock.getParent().isExpanded()) {
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 10);

        int dockX = dock.getDockX();
        int dockY = dock.getDockY();
        String countDisplay = loading ? Component.translatable("gui.gtcalcboard.favorites_dock.loading").getString() : String.valueOf(count);

        int maxH = Math.min(240, screen.height - dockY - 60);
        int contentH = maxH - HEADER_HEIGHT;

        int bg = 0xF00F172A;
        int border = loading ? 0xFFF59E0B : 0xFF38BDF8;

        graphics.fill(dockX, dockY, dockX + EXPANDED_WIDTH, dockY + maxH, bg);
        graphics.renderOutline(dockX, dockY, EXPANDED_WIDTH, maxH, border);

        boolean headerHover = !drawerBlocking && mouseX >= dockX && mouseX <= dockX + EXPANDED_WIDTH && mouseY >= dockY && mouseY <= dockY + HEADER_HEIGHT;
        graphics.fill(dockX + 1, dockY + 1, dockX + EXPANDED_WIDTH - 1, dockY + HEADER_HEIGHT, headerHover ? 0xFF1E293B : 0xFF172033);
        graphics.fill(dockX + 1, dockY + HEADER_HEIGHT, dockX + EXPANDED_WIDTH - 1, dockY + HEADER_HEIGHT + 1, 0xFF334155);

        String title = "⭐ " + Component.translatable("gui.gtcalcboard.favorites").getString() + " (" + countDisplay + ")";
        graphics.drawString(font, font.plainSubstrByWidth(title, EXPANDED_WIDTH - 24), dockX + 6, dockY + 5, loading ? 0xFFFDE047 : 0xFFFFD700, false);
        graphics.drawString(font, "◀", dockX + EXPANDED_WIDTH - 14, dockY + 5, headerHover ? 0xFFFF5555 : 0xFF94A3B8, false);

        int listY = dockY + HEADER_HEIGHT + 2;
        int listH = contentH - 4;

        int subX = dockX + EXPANDED_WIDTH + 3;
        int screenW = screen.width;
        int screenH = screen.height;

        EmiRecipe activeEmiRecipe = (dock.getActivePreviewRecipe() != null) ? dock.getActivePreviewRecipe() : (dock.getHoveredFavorite() != null && dock.getHoveredFavorite().getRecipe() != null ? dock.getHoveredFavorite().getRecipe() : null);
        int activeEmiRowY = (dock.getActivePreviewRecipe() != null) ? dock.getActivePreviewRowY() : dock.getHoveredFavRowY();
        int previewAnchorX = (dock.getActiveFlyoutFavorite() != null) ? (subX + SUB_WIDTH + 6) : (dockX + EXPANDED_WIDTH + 6);

        int[] previewBounds = (activeEmiRecipe != null) ? RecipeHoverPreviewRenderer.calculateEmiPreviewBounds(activeEmiRecipe, previewAnchorX, activeEmiRowY, screenW, screenH) : null;

        boolean mouseInPreview = !drawerBlocking && previewBounds != null && mouseX >= previewBounds[0] && mouseX <= previewBounds[0] + previewBounds[2] && mouseY >= previewBounds[1] && mouseY <= previewBounds[1] + previewBounds[3];
        int totalDockRight = (dock.getActiveFlyoutFavorite() != null) ? (subX + SUB_WIDTH) : (dockX + EXPANDED_WIDTH);
        boolean mouseInDockArea = !drawerBlocking && mouseX >= dockX && mouseX <= totalDockRight && mouseY >= dockY && mouseY <= dockY + maxH;

        boolean mouseInBridge = false;
        if (dock.getActiveFlyoutFavorite() != null && previewBounds != null) {
            int bridgeLeft = subX + SUB_WIDTH;
            int bridgeRight = previewBounds[0];
            int bridgeTop = Math.min(dockY, previewBounds[1]);
            int bridgeBottom = Math.max(dockY + maxH, previewBounds[1] + previewBounds[3]);
            mouseInBridge = mouseX >= bridgeLeft && mouseX <= bridgeRight && mouseY >= bridgeTop && mouseY <= bridgeBottom;
        }

        if (!dock.isDraggingScrollBar() && !dock.isDraggingSubScrollBar() && !dock.isDragging() && !mouseInDockArea && !mouseInPreview && !mouseInBridge) {
            dock.closeFlyout();
        }

        if (favorites.isEmpty()) {
            renderEmptyDock(dock, graphics, font, loading, listY);
        } else {
            renderFavoritesList(dock, graphics, font, favorites, listY, listH, drawerBlocking, mouseInPreview, mouseX, mouseY);
        }

        if (dock.getActiveFlyoutFavorite() != null && !dock.getActiveFlyoutRecipes().isEmpty()) {
            renderSubFlyoutPanel(dock, graphics, font, subX, dockY, SUB_WIDTH, maxH, mouseX, mouseY);
        }

        if (dock.isDragging() && (dock.getDraggingFavorite() != null || dock.getDraggingFlyoutRecipe() != null)) {
            renderDraggingIcon(dock, graphics, mouseX, mouseY);
        }

        graphics.pose().popPose();
    }

    private static void renderEmptyDock(EmiFavoritesDockImpl dock, GuiGraphics graphics, Font font, boolean loading, int listY) {
        int dockX = dock.getDockX();
        if (loading) {
            Component syncComp = Component.translatable("gui.gtcalcboard.favorites_dock.syncing");
            List<FormattedCharSequence> lines = font.split(syncComp, EXPANDED_WIDTH - 16);
            int sy = listY + 20;
            for (FormattedCharSequence line : lines) {
                int lw = font.width(line);
                graphics.drawString(font, line, dockX + (EXPANDED_WIDTH - lw) / 2, sy, 0xFFFDE047, false);
                sy += 11;
            }
        } else {
            graphics.drawCenteredString(font, "§7" + Component.translatable("gui.gtcalcboard.favorites_dock.empty").getString(), dockX + EXPANDED_WIDTH / 2, listY + 16, 0xFF94A3B8);

            Component hintComp = Component.translatable("gui.gtcalcboard.favorites_dock.empty_hint");
            List<FormattedCharSequence> lines = font.split(hintComp, EXPANDED_WIDTH - 16);
            int hy = listY + 30;
            for (FormattedCharSequence line : lines) {
                int lw = font.width(line);
                graphics.drawString(font, line, dockX + (EXPANDED_WIDTH - lw) / 2, hy, 0xFF64748B, false);
                hy += 11;
            }
        }
    }

    private static void renderFavoritesList(EmiFavoritesDockImpl dock, GuiGraphics graphics, Font font, List<EmiFavorite> favorites,
                                           int listY, int listH, boolean drawerBlocking, boolean mouseInPreview,
                                           int mouseX, int mouseY) {
        int dockX = dock.getDockX();
        int totalH = favorites.size() * ROW_HEIGHT;
        dock.setMaxScrollY(Math.max(0, totalH - listH));
        dock.setScrollY(Math.max(0, Math.min(dock.getMaxScrollY(), dock.getScrollY())));

        BoardScissorHelper.enableScissor(graphics, dockX, listY, dockX + EXPANDED_WIDTH, listY + listH);

        for (int i = 0; i < favorites.size(); i++) {
            EmiFavorite fav = favorites.get(i);
            int rowY = (int) (listY + (i * ROW_HEIGHT) - dock.getScrollY());

            if (rowY + ROW_HEIGHT < listY || rowY > listY + listH) continue;

            boolean isFlyoutActive = (dock.getActiveFlyoutFavorite() == fav);
            boolean mouseOverScrollBar = dock.getMaxScrollY() > 0 && mouseX >= dockX + EXPANDED_WIDTH - SCROLLBAR_HIT_WIDTH;
            boolean rowHover = !drawerBlocking && !dock.isDraggingScrollBar() && !dock.isDraggingSubScrollBar() && !mouseOverScrollBar
                    && mouseX >= dockX + 2 && mouseX <= dockX + EXPANDED_WIDTH - 2 && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;

            if (rowHover) {
                dock.setHoveredFavorite(fav);
                dock.setHoveredFavRowY(rowY);
                if (dock.getActiveFlyoutFavorite() != fav && !mouseInPreview) {
                    dock.setActiveFlyoutFavorite(fav);
                    dock.setActiveFlyoutRecipes(dock.findRecipesForFavorite(fav));
                    dock.setSubScrollY(0);
                }
            }

            int rowBg = (isFlyoutActive || rowHover) ? 0xFF1E293B : (i % 2 == 0 ? 0x880F172A : 0x440F172A);
            graphics.fill(dockX + 2, rowY, dockX + EXPANDED_WIDTH - 2, rowY + ROW_HEIGHT - 1, rowBg);

            if (isFlyoutActive) {
                graphics.renderOutline(dockX + 2, rowY, EXPANDED_WIDTH - 4, ROW_HEIGHT - 1, 0xFF38BDF8);
            } else if (rowHover) {
                graphics.renderOutline(dockX + 2, rowY, EXPANDED_WIDTH - 4, ROW_HEIGHT - 1, 0xFFFFD700);
            }

            renderFavoriteIcon(graphics, fav, dockX + 4, rowY + 3);

            String name = EmiFavoritesNodeSpawner.extractFavoriteName(fav);
            int textColor = isFlyoutActive ? 0xFF38BDF8 : (rowHover ? 0xFFFFD700 : 0xFFE2E8F0);
            graphics.drawString(font, font.plainSubstrByWidth(name, EXPANDED_WIDTH - 42), dockX + 24, rowY + 7, textColor, false);

            int removeBtnX = dockX + EXPANDED_WIDTH - 17;
            boolean removeHover = !drawerBlocking && !dock.isDraggingScrollBar() && !dock.isDraggingSubScrollBar()
                    && mouseX >= removeBtnX && mouseX <= removeBtnX + 10 && mouseY >= rowY + 5 && mouseY <= rowY + 17;
            if (rowHover || isFlyoutActive) {
                graphics.drawString(font, "✕", removeBtnX, rowY + 6, removeHover ? 0xFFFF5555 : 0xFF64748B, false);
            }
        }

        BoardScissorHelper.disableScissor(graphics);

        if (dock.getMaxScrollY() > 0) {
            int scrollBarH = Math.max(MIN_SCROLLBAR_HEIGHT, (int) ((float) listH / totalH * listH));
            int scrollBarY = (int) (listY + (dock.getScrollY() / dock.getMaxScrollY()) * (listH - scrollBarH));
            int trackX = dockX + EXPANDED_WIDTH - SCROLLBAR_TRACK_WIDTH - 1;
            boolean barHover = !drawerBlocking && mouseX >= dockX + EXPANDED_WIDTH - SCROLLBAR_HIT_WIDTH
                    && mouseX <= dockX + EXPANDED_WIDTH && mouseY >= listY && mouseY <= listY + listH;
            int thumbColor = (dock.isDraggingScrollBar() || barHover) ? 0xFF7DD3FC : 0xFF38BDF8;

            graphics.fill(trackX, listY, trackX + SCROLLBAR_TRACK_WIDTH, listY + listH, 0x44000000);
            graphics.fill(trackX, scrollBarY, trackX + SCROLLBAR_TRACK_WIDTH, scrollBarY + scrollBarH, thumbColor);
        }
    }

    private static void renderDraggingIcon(EmiFavoritesDockImpl dock, GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        if (dock.getDraggingFlyoutRecipe() != null) {
            renderRecipeIcon(graphics, dock.getDraggingFlyoutRecipe(), mouseX - 8, mouseY - 8, null);
        } else {
            renderFavoriteIcon(graphics, dock.getDraggingFavorite(), mouseX - 8, mouseY - 8);
        }
        graphics.pose().popPose();
    }

    private static void renderSubFlyoutPanel(EmiFavoritesDockImpl dock, GuiGraphics graphics, Font font, int subX, int subY, int subW, int subH, int mouseX, int mouseY) {
        graphics.fill(subX, subY, subX + subW, subY + subH, 0xF00F172A);
        graphics.renderOutline(subX, subY, subW, subH, 0xFF38BDF8);

        String favName = EmiFavoritesNodeSpawner.extractFavoriteName(dock.getActiveFlyoutFavorite());
        graphics.fill(subX + 1, subY + 1, subX + subW - 1, subY + HEADER_HEIGHT, 0xFF1E293B);
        graphics.fill(subX + 1, subY + HEADER_HEIGHT, subX + subW - 1, subY + HEADER_HEIGHT + 1, 0xFF334155);
        graphics.drawString(font, font.plainSubstrByWidth(favName + " (" + dock.getActiveFlyoutRecipes().size() + ")", subW - 8), subX + 6, subY + 5, 0xFF38BDF8, false);

        int listY = subY + HEADER_HEIGHT + 2;
        int listH = subH - HEADER_HEIGHT - 4;
        int totalH = dock.getActiveFlyoutRecipes().size() * SUB_ROW_HEIGHT;

        dock.setSubMaxScrollY(Math.max(0, totalH - listH));
        dock.setSubScrollY(Math.max(0, Math.min(dock.getSubMaxScrollY(), dock.getSubScrollY())));

        BoardScissorHelper.enableScissor(graphics, subX, listY, subX + subW, listY + listH);
        dock.setHoveredFlyoutRecipe(null);

        ResourceLocation preferredWs = extractPreferredWorkstation(dock.getActiveFlyoutFavorite());

        for (int i = 0; i < dock.getActiveFlyoutRecipes().size(); i++) {
            EmiRecipe recipe = dock.getActiveFlyoutRecipes().get(i);
            int rowY = (int) (listY + (i * SUB_ROW_HEIGHT) - dock.getSubScrollY());

            if (rowY + SUB_ROW_HEIGHT < listY || rowY > listY + listH) continue;

            boolean mouseOverSubScrollBar = dock.getSubMaxScrollY() > 0 && mouseX >= subX + subW - SCROLLBAR_HIT_WIDTH;
            boolean rowHover = !dock.isDraggingScrollBar() && !dock.isDraggingSubScrollBar() && !mouseOverSubScrollBar
                    && mouseX >= subX + 2 && mouseX <= subX + subW - 2 && mouseY >= rowY && mouseY <= rowY + SUB_ROW_HEIGHT;

            boolean isDefault = isDefaultRecipe(dock.getActiveFlyoutFavorite(), recipe);

            if (rowHover) {
                dock.setHoveredFlyoutRecipe(recipe);
                dock.setActivePreviewRecipe(recipe);
                dock.setActivePreviewRowY(rowY);
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
            boolean addHover = !dock.isDraggingScrollBar() && !dock.isDraggingSubScrollBar()
                    && mouseX >= addBtnX && mouseX <= addBtnX + 18 && mouseY >= addBtnY && mouseY <= addBtnY + 16;
            graphics.fill(addBtnX, addBtnY, addBtnX + 18, addBtnY + 16, addHover ? 0xFF2B4466 : 0xFF1C2C44);
            graphics.renderOutline(addBtnX, addBtnY, 18, 16, addHover ? 0xFF55AAFF : 0xFF355580);
            graphics.drawCenteredString(font, "➕", addBtnX + 9, addBtnY + 4, 0xFFFFFFFF);
        }

        BoardScissorHelper.disableScissor(graphics);

        if (dock.getSubMaxScrollY() > 0) {
            int scrollBarH = Math.max(MIN_SCROLLBAR_HEIGHT, (int) ((float) listH / totalH * listH));
            int scrollBarY = (int) (listY + (dock.getSubScrollY() / dock.getSubMaxScrollY()) * (listH - scrollBarH));
            int trackX = subX + subW - SCROLLBAR_TRACK_WIDTH - 1;
            boolean barHover = mouseX >= subX + subW - SCROLLBAR_HIT_WIDTH && mouseX <= subX + subW && mouseY >= listY && mouseY <= listY + listH;
            int thumbColor = (dock.isDraggingSubScrollBar() || barHover) ? 0xFF7DD3FC : 0xFF38BDF8;

            graphics.fill(trackX, listY, trackX + SCROLLBAR_TRACK_WIDTH, listY + listH, 0x44000000);
            graphics.fill(trackX, scrollBarY, trackX + SCROLLBAR_TRACK_WIDTH, scrollBarY + scrollBarH, thumbColor);
        }
    }

    public static void renderTooltips(EmiFavoritesDockImpl dock, GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!dock.getParent().isExpanded() || dock.isDragging()) return;

        BoardScreen screen = dock.getScreen();
        int screenW = screen.width;
        int screenH = screen.height;

        if (dock.getActivePreviewRecipe() != null && dock.getActiveFlyoutFavorite() != null) {
            int subX = dock.getDockX() + EXPANDED_WIDTH + 3;
            int previewAnchorX = subX + SUB_WIDTH + 6;
            RecipeHoverPreviewRenderer.renderEmiPreviewDirect(
                graphics, dock.getActivePreviewRecipe(), previewAnchorX, dock.getActivePreviewRowY(), mouseX, mouseY, 0, screenW, screenH
            );
            return;
        }

        if (dock.getHoveredFavorite() != null) {
            if (dock.getHoveredFavorite().getRecipe() != null) {
                int previewAnchorX = dock.getDockX() + EXPANDED_WIDTH + 6;
                RecipeHoverPreviewRenderer.renderEmiPreviewDirect(
                    graphics, dock.getHoveredFavorite().getRecipe(), previewAnchorX, dock.getHoveredFavRowY(), mouseX, mouseY, 0, screenW, screenH
                );
            } else {
                List<Component> tooltip = new ArrayList<>();
                String name = EmiFavoritesNodeSpawner.extractFavoriteName(dock.getHoveredFavorite());
                tooltip.add(Component.literal("§6⭐ " + name));

                List<EmiRecipe> recipes = dock.findRecipesForFavorite(dock.getHoveredFavorite());
                tooltip.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.recipes_count", recipes.size()).getString()));
                tooltip.add(Component.literal("§e" + Component.translatable("gui.gtcalcboard.favorites_dock.hover_flyout_hint").getString()));
                tooltip.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.favorites_dock.click_hint").getString()));
                tooltip.add(Component.literal("§c" + Component.translatable("gui.gtcalcboard.favorites_dock.remove_hint").getString()));
                BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltip, mouseX, mouseY, screenW, screenH);
            }
        }
    }

    public static void renderFavoriteIcon(GuiGraphics graphics, EmiFavorite fav, int x, int y) {
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

    public static void renderRecipeIcon(GuiGraphics graphics, EmiRecipe recipe, int x, int y, ResourceLocation preferredWs) {
        if (!recipe.getOutputs().isEmpty() && !recipe.getOutputs().get(0).getEmiStacks().isEmpty()) {
            recipe.getOutputs().get(0).render(graphics, x, y, 0, EmiIngredient.RENDER_ICON);
            return;
        }
        if (!recipe.getInputs().isEmpty() && !recipe.getInputs().get(0).getEmiStacks().isEmpty()) {
            recipe.getInputs().get(0).render(graphics, x, y, 0, EmiIngredient.RENDER_ICON);
            return;
        }
        if (preferredWs != null) {
            var item = ForgeRegistries.ITEMS.getValue(preferredWs);
            if (item != null) {
                EmiStack.of(item).render(graphics, x, y, 0, EmiIngredient.RENDER_ICON);
                return;
            }
        }
        var workstations = EmiApi.getRecipeManager().getWorkstations(recipe.getCategory());
        if (workstations != null && !workstations.isEmpty() && !workstations.get(0).getEmiStacks().isEmpty()) {
            workstations.get(0).getEmiStacks().get(0).render(graphics, x, y, 0, EmiIngredient.RENDER_ICON);
            return;
        }
        graphics.fill(x, y, x + 16, y + 16, 0xFF4A90E2);
    }

    public static String getRecipeRowDisplayName(EmiRecipe recipe, ResourceLocation preferredWs) {
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

    public static String getRecipeWorkstationName(EmiRecipe recipe) {
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

    public static ResourceLocation extractPreferredWorkstation(EmiFavorite fav) {
        if (fav != null && !fav.getEmiStacks().isEmpty()) {
            var firstStack = fav.getEmiStacks().get(0);
            if (firstStack.getItemStack() != null) {
                return ForgeRegistries.ITEMS.getKey(firstStack.getItemStack().getItem());
            }
        }
        return null;
    }

    public static boolean isDefaultRecipe(EmiFavorite fav, EmiRecipe recipe) {
        if (fav != null && !fav.getEmiStacks().isEmpty()) {
            try {
                EmiRecipe def = BoM.getRecipe(fav.getEmiStacks().get(0));
                if (def != null && def.getId() != null && recipe.getId() != null && def.getId().equals(recipe.getId())) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }
}
