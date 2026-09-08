package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.search.RecipeFilterConfig;
import com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer;
import com.gtceu.calcboard.client.gui.search.RecipeSearchCacheManager;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.integration.emi.EmiSearchHelper;
import com.gtceu.calcboard.integration.spi.RecipeViewerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RecipeSearchDialogRenderer {

    public static final int ROW_HEIGHT = 32;

    private RecipeSearchDialogRenderer() {}

    public static void render(RecipeSearchDialog dialog, GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);

        Font font = Minecraft.getInstance().font;
        int dialogW = RecipeSearchDialog.getDialogWidth(screenWidth);
        int dialogH = RecipeSearchDialog.getDialogHeight(screenHeight);
        int sideW = 104;
        int gap = 6;
        boolean hasSideSpace = screenWidth >= (dialogW + sideW + gap + 16);
        int totalW = hasSideSpace ? (dialogW + sideW + gap) : dialogW;
        int startX = (screenWidth - totalW) / 2;
        int sideX = hasSideSpace ? startX : -1000;
        int x = hasSideSpace ? (startX + sideW + gap) : startX;
        int y = (screenHeight - dialogH) / 2;

        graphics.fill(0, 0, screenWidth, screenHeight, 0xCC000000);

        if (hasSideSpace) {
            renderPrefixSidePanel(dialog, graphics, font, sideX, y, sideW, dialogH, mouseX, mouseY);
        }

        graphics.fill(x, y, x + dialogW, y + dialogH, 0xFF1E222B);
        graphics.renderOutline(x, y, dialogW, dialogH, 0xFF4A90E2);

        renderHeader(dialog, graphics, font, x, y, dialogW, mouseX, mouseY);
        renderTopControls(dialog, graphics, font, x, y, dialogW, mouseX, mouseY, screenWidth, screenHeight);
        renderListArea(dialog, graphics, font, x, y, dialogW, dialogH, mouseX, mouseY, screenWidth, screenHeight);

        graphics.pose().popPose();

        if (dialog.getFilterDialog().isVisible()) {
            dialog.getFilterDialog().render(graphics, mouseX, mouseY, screenWidth, screenHeight);
        }
    }

    private static void renderHeader(RecipeSearchDialog dialog, GuiGraphics graphics, Font font, int x, int y, int dialogW, int mouseX, int mouseY) {
        graphics.fill(x, y, x + dialogW, y + 24, 0xFF282E3B);
        String headerTitle = resolveHeaderTitle(dialog);
        graphics.drawString(font, font.plainSubstrByWidth(headerTitle, dialogW - 36), x + 10, y + 8, 0xFFFFFFFF, false);

        int closeX = x + dialogW - 18;
        int closeY = y + 6;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        graphics.drawString(font, "✕", closeX, closeY, closeHover ? 0xFFFF5555 : 0xFFAAAAAA, false);
    }

    private static String resolveHeaderTitle(RecipeSearchDialog dialog) {
        if (dialog.getSwitchTargetNode() != null) {
            return "§e⟲ " + Component.translatable("gui.gtcalcboard.switch_recipe.title", dialog.getSwitchTargetNode().getName()).getString();
        }
        var target = dialog.getContextualWireTarget();
        if (target != null) {
            String stackName = target.sourceStack != null ? target.sourceStack.getDisplayName() : "Item";
            return !target.sourceIsInput
                    ? "§6➔ " + Component.translatable("gui.gtcalcboard.search.consumers_for", stackName).getString()
                    : "§a➔ " + Component.translatable("gui.gtcalcboard.search.producers_for", stackName).getString();
        }
        return "§6➕ " + Component.translatable("gui.gtcalcboard.add_recipe").getString();
    }

    private static void renderTopControls(RecipeSearchDialog dialog, GuiGraphics graphics, Font font, int x, int y, int dialogW, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        int topBtnW = 20;
        int topBtnH = 16;
        int filterBtnX = x + dialogW - 12 - topBtnW;
        int favBtnX = filterBtnX - topBtnW - 3;
        int helpBtnX = favBtnX - topBtnW - 3;
        int searchBoxW = dialogW - 24 - (topBtnW * 3) - 9;

        if (dialog.getSearchBox() != null) {
            dialog.getSearchBox().setX(x + 12);
            dialog.getSearchBox().setY(y + 30);
            dialog.getSearchBox().setWidth(searchBoxW);
            dialog.getSearchBox().render(graphics, mouseX, mouseY, 0);
        }

        renderHelpButton(dialog, graphics, font, helpBtnX, y + 30, topBtnW, topBtnH, mouseX, mouseY, screenWidth, screenHeight);
        renderFavoritesButton(dialog, graphics, font, favBtnX, y + 30, topBtnW, topBtnH, mouseX, mouseY, screenWidth, screenHeight);
        renderFilterButton(dialog, graphics, font, filterBtnX, y + 30, topBtnW, topBtnH, mouseX, mouseY, screenWidth, screenHeight);
    }

    private static void renderHelpButton(RecipeSearchDialog dialog, GuiGraphics graphics, Font font, int btnX, int btnY, int btnW, int btnH, int mouseX, int mouseY, int sw, int sh) {
        boolean helpHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, helpHover ? 0xFF334155 : 0xFF1E293B);
        graphics.renderOutline(btnX, btnY, btnW, btnH, helpHover ? 0xFFF59E0B : 0xFF475569);
        graphics.drawCenteredString(font, "?", btnX + btnW / 2, btnY + 4, helpHover ? 0xFFFBBF24 : 0xFF94A3B8);

        if (helpHover && !dialog.getFilterDialog().isVisible()) {
            String raw = Component.translatable("gui.gtcalcboard.search.help_tooltip").getString();
            if (raw.contains("\n")) {
                List<Component> lines = Arrays.stream(raw.split("\n")).<Component>map(Component::literal).toList();
                BoardTooltipRenderer.renderComponentTooltip(graphics, font, lines, mouseX, mouseY, sw, sh);
            } else {
                BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.search.help_tooltip"), mouseX, mouseY, sw, sh);
            }
        }
    }

    private static void renderFavoritesButton(RecipeSearchDialog dialog, GuiGraphics graphics, Font font, int btnX, int btnY, int btnW, int btnH, int mouseX, int mouseY, int sw, int sh) {
        boolean favHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        boolean favOnly = dialog.isShowFavoritesOnly();
        graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, favHover ? 0xFF3D3A20 : (favOnly ? 0xFF353018 : 0xFF1E293B));
        graphics.renderOutline(btnX, btnY, btnW, btnH, favOnly ? 0xFFFFD700 : (favHover ? 0xFF94A3B8 : 0xFF475569));
        graphics.drawCenteredString(font, favOnly ? "⭐" : "☆", btnX + btnW / 2, btnY + 4, favOnly ? 0xFFFFD700 : 0xFF94A3B8);

        if (favHover && !dialog.getFilterDialog().isVisible()) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.filter.favorites_tooltip"), mouseX, mouseY, sw, sh);
        }
    }

    private static void renderFilterButton(RecipeSearchDialog dialog, GuiGraphics graphics, Font font, int btnX, int btnY, int btnW, int btnH, int mouseX, int mouseY, int sw, int sh) {
        boolean filterHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        boolean filterActive = !RecipeFilterConfig.getInstance().getExcludedCategories().isEmpty();
        graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, filterHover ? 0xFF334155 : (filterActive ? 0xFF2A3649 : 0xFF1E293B));
        graphics.renderOutline(btnX, btnY, btnW, btnH, filterActive ? 0xFF38BDF8 : 0xFF475569);
        graphics.drawCenteredString(font, "⚙", btnX + btnW / 2, btnY + 4, filterActive ? 0xFF38BDF8 : 0xFF94A3B8);

        if (filterHover && !dialog.getFilterDialog().isVisible()) {
            String raw = Component.translatable("gui.gtcalcboard.filter.btn_tooltip").getString();
            if (raw.contains("\n")) {
                List<Component> lines = Arrays.stream(raw.split("\n")).<Component>map(Component::literal).toList();
                BoardTooltipRenderer.renderComponentTooltip(graphics, font, lines, mouseX, mouseY, sw, sh);
            } else {
                BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.filter.btn_tooltip"), mouseX, mouseY, sw, sh);
            }
        }
    }

    private static void renderListArea(RecipeSearchDialog dialog, GuiGraphics graphics, Font font, int x, int y, int dialogW, int dialogH, int mouseX, int mouseY, int sw, int sh) {
        int listX = x + 12;
        int listY = y + 52;
        int listW = dialogW - 24;
        int listH = dialogH - 60;

        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF14171E);
        graphics.renderOutline(listX, listY, listW, listH, 0xFF3D4455);

        int visibleRows = Math.max(1, listH / ROW_HEIGHT);
        List<SearchableRecipe> filteredRecipes = dialog.getFilteredRecipes();
        int maxScroll = Math.max(0, filteredRecipes.size() - visibleRows);
        int scrollOffset = Math.max(0, Math.min(dialog.getScrollOffset(), maxScroll));
        dialog.setScrollOffset(scrollOffset);

        if (filteredRecipes.isEmpty()) {
            renderEmptyOrLoadingList(graphics, font, listX, listY, listW, listH);
        } else {
            renderRecipeRows(dialog, graphics, font, listX, listY, listW, visibleRows, scrollOffset, mouseX, mouseY);
            if (filteredRecipes.size() > visibleRows) {
                renderScrollbar(dialog, graphics, listX, listY, listW, listH, visibleRows, maxScroll, scrollOffset, mouseX, mouseY);
            }
        }

        updateStickyHover(dialog, x, y, dialogW, dialogH, mouseX, mouseY, sw, sh);
        if (dialog.getStickyHoverRecipe() != null && !dialog.getFilterDialog().isVisible()) {
            RecipeHoverPreviewRenderer.renderPreview(graphics, dialog.getStickyHoverRecipe(), x, y, dialogW, dialogH, dialog.getStickyHoverRowY(), mouseX, mouseY, 0, sw, sh);
        }
    }

    private static void renderEmptyOrLoadingList(GuiGraphics graphics, Font font, int listX, int listY, int listW, int listH) {
        boolean isLoading = RecipeSearchCacheManager.getCachedRecipeCount() == 0 || !RecipeSearchCacheManager.isGlobalCached();
        if (isLoading) {
            renderLoadingProgressBar(graphics, font, listX, listY, listW, listH);
        } else {
            String emptyMsg = "§7" + Component.translatable("gui.gtcalcboard.no_matching_recipes").getString();
            graphics.drawCenteredString(font, emptyMsg, listX + listW / 2, listY + listH / 2 - 4, 0xFF888888);
        }
    }

    private static void renderLoadingProgressBar(GuiGraphics graphics, Font font, int listX, int listY, int listW, int listH) {
        long animDots = (System.currentTimeMillis() / 400L) % 4;
        String dots = ".".repeat((int) animDots);
        var progress = RecipeSearchCacheManager.getCachingProgress();
        String phaseText = Component.translatable(progress.phaseKey()).getString();
        String phaseTitle = "§e⏳ " + Component.translatable("gui.gtcalcboard.loading_recipes_phase",
                progress.currentPhase(), progress.totalPhases(), phaseText).getString() + dots;

        int centerY = listY + (listH / 2);
        graphics.drawCenteredString(font, phaseTitle, listX + listW / 2, centerY - 20, 0xFFE0C040);

        int barW = Math.min(220, listW - 40);
        int barH = 6;
        int barX = (listX + listW / 2) - (barW / 2);
        int barY = centerY - 4;

        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF222733);
        graphics.renderOutline(barX, barY, barW, barH, 0xFF3D4659);

        float fillRatio = Math.max(0.15f, (float) progress.currentPhase() / (float) progress.totalPhases());
        int fillW = (int) (barW * fillRatio);
        graphics.fill(barX + 1, barY + 1, barX + fillW - 1, barY + barH - 1, 0xFF4A90E2);

        if (progress.detail() != null && !progress.detail().isEmpty()) {
            graphics.drawCenteredString(font, "§7" + progress.detail(), listX + listW / 2, centerY + 8, 0xFFAAAAAA);
        }
        String hint = "§8" + Component.translatable("gui.gtcalcboard.loading_recipe_phase_hint").getString();
        graphics.drawCenteredString(font, hint, listX + listW / 2, centerY + 20, 0xFF666666);
    }

    private static void renderRecipeRows(RecipeSearchDialog dialog, GuiGraphics graphics, Font font, int listX, int listY, int listW, int visibleRows, int scrollOffset, int mouseX, int mouseY) {
        List<SearchableRecipe> filteredRecipes = dialog.getFilteredRecipes();
        SearchableRecipe newlyHoveredRecipe = null;
        int newlyHoveredRowY = 0;

        for (int i = 0; i < visibleRows; i++) {
            int index = scrollOffset + i;
            if (index >= filteredRecipes.size()) break;

            SearchableRecipe sr = filteredRecipes.get(index);
            int rowY = listY + i * ROW_HEIGHT;
            int btnW = 44;
            int btnH = 18;
            int btnX = listX + listW - btnW - 6;
            int btnY = rowY + 7;
            boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

            boolean mouseOverScrollBar = (filteredRecipes.size() > visibleRows) && (mouseX >= listX + listW - 8);
            boolean rowHover = !dialog.isDraggingScrollBar() && !mouseOverScrollBar && mouseX >= listX && mouseX <= listX + listW && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
            if (rowHover) {
                newlyHoveredRecipe = sr;
                newlyHoveredRowY = rowY;
            }

            renderSingleRow(dialog, graphics, font, sr, i, listX, rowY, listW, btnX, btnY, btnW, btnH, rowHover, btnHover, mouseX, mouseY);
        }

        if (newlyHoveredRecipe != null) {
            dialog.setStickyHoverRecipe(newlyHoveredRecipe);
            dialog.setStickyHoverRowY(newlyHoveredRowY);
        }
    }

    private static void renderSingleRow(RecipeSearchDialog dialog, GuiGraphics graphics, Font font, SearchableRecipe sr, int rowIdx, int listX, int rowY, int listW, int btnX, int btnY, int btnW, int btnH, boolean rowHover, boolean btnHover, int mouseX, int mouseY) {
        boolean isDefault = false;
        if (ModCompatHelper.isEmiLoaded()) {
            String query = dialog.getSearchBox() != null ? dialog.getSearchBox().getValue().trim() : "";
            isDefault = EmiSearchHelper.isDefaultRecipe(sr.recipe(), dialog.getContextualWireTarget() != null, dialog.getQueryEngine().getCurrentContextualDefaultRecipeId(), !query.isEmpty(), query);
        }

        boolean isSelectedOrHovered = rowHover || (dialog.getStickyHoverRecipe() == sr);
        renderRowBackground(graphics, listX, rowY, listW, isSelectedOrHovered, isDefault, rowIdx);

        RecipeSearchEngine.MatchedOutputResult matched = RecipeSearchEngine.findMatchedOutput(
                sr,
                dialog.getQueryEngine().getCurrentParsedQuery(),
                (dialog.getContextualWireTarget() != null && dialog.getContextualWireTarget().sourceStack != null) ? dialog.getContextualWireTarget().sourceStack.getId() : null,
                (dialog.getContextualWireTarget() != null && dialog.getContextualWireTarget().sourceStack != null) ? dialog.getContextualWireTarget().sourceStack.getDisplayName() : null
        );
        ResourceLocation matchedId = (matched != null) ? matched.id() : null;
        String matchedName = (matched != null) ? matched.name() : null;

        int iconW = RecipeViewerRegistry.getActiveAdapter().renderRowIcon(graphics, font, sr.recipe(), listX, rowY, matchedId, matchedName);
        renderRowText(dialog, graphics, font, sr, listX, rowY, iconW, btnX, isDefault, matchedName);

        renderRowFavoriteStar(graphics, font, sr, btnX, rowY, mouseX, mouseY, isSelectedOrHovered);
        renderRowActionButton(dialog, graphics, font, btnX, btnY, btnW, btnH, btnHover);
    }

    private static void renderRowBackground(GuiGraphics graphics, int listX, int rowY, int listW, boolean isHovered, boolean isDefault, int rowIdx) {
        if (isHovered) {
            graphics.fill(listX + 1, rowY + 1, listX + listW - 1, rowY + ROW_HEIGHT - 1, 0xFF2A3649);
            graphics.renderOutline(listX + 1, rowY + 1, listW - 2, ROW_HEIGHT - 2, 0xFFFFD700);
        } else if (isDefault) {
            graphics.fill(listX + 1, rowY + 1, listX + listW - 1, rowY + ROW_HEIGHT - 1, 0xFF1B2436);
            graphics.renderOutline(listX + 1, rowY + 1, listW - 2, ROW_HEIGHT - 2, 0xFF38BDF8);
        } else {
            graphics.fill(listX + 1, rowY + 1, listX + listW - 1, rowY + ROW_HEIGHT - 1, (rowIdx % 2 == 0 ? 0xFF1A1E26 : 0xFF161A21));
        }
    }

    private static void renderRowText(RecipeSearchDialog dialog, GuiGraphics graphics, Font font, SearchableRecipe sr, int listX, int rowY, int iconW, int btnX, boolean isDefault, String matchedName) {
        String rName = sr.displayName();
        String catText = !sr.categoryName().isEmpty() ? "§7[" + sr.categoryName() + "§7]" : (!sr.categoryId().isEmpty() ? "§7[" + sr.categoryId() + "§7]" : "");
        String star = isDefault ? "§6★ " : "";
        String genericBadge = !sr.isSupported() ? "§6[" + Component.translatable("gui.gtcalcboard.search.badge.unsupported").getString() + "] " : "";

        String line1 = star + genericBadge + (isDefault ? "§b" : "§f") + rName;
        String genInfo = dialog.resolveGenerationInfo(sr);
        String line2;
        if (genInfo != null) {
            line2 = catText.isEmpty() ? genInfo : (catText + " " + genInfo);
        } else if (matchedName != null && !matchedName.isEmpty() && !matchedName.equalsIgnoreCase(rName)) {
            line2 = catText.isEmpty() ? ("§8➔ §e" + matchedName) : (catText + " §8➔ §e" + matchedName);
        } else {
            line2 = catText;
        }

        int textStartX = listX + Math.max(58, iconW + 4);
        int maxTextW = (btnX - 24) - textStartX;
        if (maxTextW > 20) {
            graphics.drawString(font, font.plainSubstrByWidth(line1, maxTextW), textStartX, rowY + 6, isDefault ? 0xFF38BDF8 : 0xFFFFFFFF, false);
            if (!line2.isEmpty()) {
                graphics.drawString(font, font.plainSubstrByWidth(line2, maxTextW), textStartX, rowY + 18, 0xFFAAAAAA, false);
            }
        }
    }

    private static void renderRowFavoriteStar(GuiGraphics graphics, Font font, SearchableRecipe sr, int btnX, int rowY, int mouseX, int mouseY, boolean isHovered) {
        boolean isFav = RecipeSearchDialog.isRecipeFavorite(sr.recipe());
        int favStarX = btnX - 20;
        int favStarY = rowY + 7;
        boolean favStarHover = mouseX >= favStarX && mouseX <= favStarX + 16 && mouseY >= favStarY && mouseY <= favStarY + 18;

        if (isFav || favStarHover || isHovered) {
            graphics.drawString(font, isFav ? "⭐" : "☆", favStarX + 4, favStarY + 5, isFav ? 0xFFFFD700 : (favStarHover ? 0xFFFDE047 : 0xFF64748B), false);
        }
    }

    private static void renderRowActionButton(RecipeSearchDialog dialog, GuiGraphics graphics, Font font, int btnX, int btnY, int btnW, int btnH, boolean btnHover) {
        graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHover ? 0xFF2A6840 : 0xFF1E4D2F);
        graphics.renderOutline(btnX, btnY, btnW, btnH, 0xFF359050);
        String btnText = (dialog.getSwitchTargetNode() != null)
                ? ("⟲ " + Component.translatable("gui.gtcalcboard.switch_recipe.apply").getString())
                : ("➕ " + Component.translatable("gui.gtcalcboard.add_btn").getString());
        graphics.drawCenteredString(font, btnText, btnX + btnW / 2, btnY + 5, 0xFFFFFFFF);
    }

    private static void renderScrollbar(RecipeSearchDialog dialog, GuiGraphics graphics, int listX, int listY, int listW, int listH, int visibleRows, int maxScroll, int scrollOffset, int mouseX, int mouseY) {
        int scrollTrackH = listH - 4;
        int barH = Math.max(16, (int) ((double) visibleRows / dialog.getFilteredRecipes().size() * scrollTrackH));
        int barY = listY + 2 + (int) ((double) scrollOffset / maxScroll * (scrollTrackH - barH));
        int barX = listX + listW - 4;
        boolean barHover = mouseX >= barX - 4 && mouseX <= barX + 8 && mouseY >= listY + 2 && mouseY <= listY + 2 + scrollTrackH;
        int thumbColor = (dialog.isDraggingScrollBar() || barHover) ? 0xFF8EA5C8 : 0xFF657595;
        graphics.fill(barX, listY + 2, barX + 3, listY + 2 + scrollTrackH, 0x44000000);
        graphics.fill(barX, barY, barX + 3, barY + barH, thumbColor);
    }

    private static void updateStickyHover(RecipeSearchDialog dialog, int x, int y, int dialogW, int dialogH, int mouseX, int mouseY, int sw, int sh) {
        SearchableRecipe sticky = dialog.getStickyHoverRecipe();
        if (sticky == null) return;
        int[] bounds = RecipeHoverPreviewRenderer.calculatePreviewBounds(sticky, x, y, dialogW, dialogH, dialog.getStickyHoverRowY(), sw, sh);
        if (bounds != null) {
            int minX = Math.min(x, bounds[0]) - 16;
            int maxX = Math.max(x + dialogW, bounds[0] + bounds[2]) + 16;
            int minY = Math.min(y, bounds[1]) - 16;
            int maxY = Math.max(y + dialogH, bounds[1] + bounds[3]) + 16;
            if (mouseX < minX || mouseX > maxX || mouseY < minY || mouseY > maxY) {
                dialog.setStickyHoverRecipe(null);
            }
        } else {
            dialog.setStickyHoverRecipe(null);
        }
    }

    private static void renderPrefixSidePanel(RecipeSearchDialog dialog, GuiGraphics graphics, Font font, int sideX, int y, int sideW, int dialogH, int mouseX, int mouseY) {
        graphics.fill(sideX, y, sideX + sideW, y + dialogH, 0xFF1E222B);
        graphics.renderOutline(sideX, y, sideW, dialogH, 0xFF4A90E2);

        graphics.fill(sideX, y, sideX + sideW, y + 24, 0xFF282E3B);
        String headerTitle = "§e⌨ " + Component.translatable("gui.gtcalcboard.search.prefix_guide.title").getString();
        graphics.drawString(font, font.plainSubstrByWidth(headerTitle, sideW - 10), sideX + 6, y + 8, 0xFFFFFFFF, false);

        int itemW = sideW - 12;
        int itemH = 24;
        int itemSpacing = 28;
        RecipeSearchDialog.PrefixGuideItem hoveredItem = null;

        for (int i = 0; i < RecipeSearchDialog.PREFIX_ITEMS.size(); i++) {
            RecipeSearchDialog.PrefixGuideItem item = RecipeSearchDialog.PREFIX_ITEMS.get(i);
            int itemX = sideX + 6;
            int itemY = y + 28 + i * itemSpacing;

            boolean hover = mouseX >= itemX && mouseX <= itemX + itemW && mouseY >= itemY && mouseY <= itemY + itemH;
            if (hover) hoveredItem = item;

            graphics.fill(itemX, itemY, itemX + itemW, itemY + itemH, hover ? item.hoverBg() : 0xFF151922);
            graphics.renderOutline(itemX, itemY, itemW, itemH, hover ? item.color() : 0xFF334155);

            graphics.fill(itemX + 2, itemY + 2, itemX + 18, itemY + itemH - 2, 0xFF0F172A);
            graphics.drawCenteredString(font, item.prefix(), itemX + 10, itemY + 8, item.color());

            String label = Component.translatable(item.labelKey()).getString();
            if (label.startsWith(item.prefix())) {
                label = label.substring(item.prefix().length()).trim();
            }
            graphics.drawString(font, font.plainSubstrByWidth(label, itemW - 22), itemX + 22, itemY + 8, hover ? 0xFFFFFFFF : 0xFFCCCCCC, false);
        }

        if (hoveredItem != null && !dialog.getFilterDialog().isVisible()) {
            List<Component> tooltipLines = new ArrayList<>();
            tooltipLines.add(Component.literal("§6§l" + Component.translatable(hoveredItem.labelKey()).getString()));
            tooltipLines.add(Component.literal("§7" + Component.translatable(hoveredItem.descKey()).getString()));
            tooltipLines.add(Component.literal("§8----------------------"));
            tooltipLines.add(Component.literal("§e★ " + Component.translatable("gui.gtcalcboard.search.prefix.click_hint").getString()));
            BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, dialog.getParent().width, dialog.getParent().height);
        }
    }
}
