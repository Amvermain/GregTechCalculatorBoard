package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class RecipeSearchDialog {
    private final BoardScreen parent;
    private final EditBox searchBox;

    public record SearchableRecipe(EmiRecipe recipe, String displayName, String searchIndex) {}

    private final List<SearchableRecipe> cachedAllRecipes = new ArrayList<>();
    private final List<SearchableRecipe> filteredRecipes = new ArrayList<>();
    private boolean recipesCached = false;
    private int scrollOffset = 0;
    private boolean visible = false;

    private static final int DIALOG_WIDTH = 360;
    private static final int DIALOG_HEIGHT = 280;
    private static final int ROW_HEIGHT = 32;

    public RecipeSearchDialog(BoardScreen parent) {
        this.parent = parent;
        Font font = Minecraft.getInstance().font;
        this.searchBox = new EditBox(font, 0, 0, DIALOG_WIDTH - 24, 16, Component.translatable("gui.gtcalcboard.search"));
        this.searchBox.setResponder(this::onSearchQueryChanged);
        this.searchBox.setHint(Component.translatable("gui.gtcalcboard.search"));
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible) {
            searchBox.setValue("");
            searchBox.setFocused(true);
            ensureRecipesCached();
            updateSearchResults("");
        }
    }

    private void ensureRecipesCached() {
        if (recipesCached && !cachedAllRecipes.isEmpty()) return;
        cachedAllRecipes.clear();
        try {
            var recipeManager = EmiApi.getRecipeManager();
            if (recipeManager != null) {
                for (EmiRecipeCategory cat : recipeManager.getCategories()) {
                    for (EmiRecipe recipe : recipeManager.getRecipes(cat)) {
                        String displayName = extractDisplayName(recipe);
                        String searchIndex = buildSearchIndex(recipe, displayName);
                        cachedAllRecipes.add(new SearchableRecipe(recipe, displayName, searchIndex));
                    }
                }
                recipesCached = true;
            }
        } catch (Throwable ignored) {}
    }

    private String extractDisplayName(EmiRecipe recipe) {
        if (!recipe.getOutputs().isEmpty()) {
            var firstOut = recipe.getOutputs().get(0);
            if (!firstOut.getEmiStacks().isEmpty()) {
                return firstOut.getEmiStacks().get(0).getName().getString();
            }
        }
        if (recipe.getId() != null) {
            String path = recipe.getId().getPath();
            if (path.contains("/")) path = path.substring(path.lastIndexOf('/') + 1);
            return EmiRecipeConverter.formatName(path);
        }
        return "Unknown Recipe";
    }

    private String buildSearchIndex(EmiRecipe recipe, String displayName) {
        StringBuilder sb = new StringBuilder();
        sb.append(displayName.toLowerCase()).append(" ");

        if (recipe.getId() != null) {
            sb.append(recipe.getId().toString().toLowerCase()).append(" ");
        }
        if (recipe.getCategory() != null && recipe.getCategory().getId() != null) {
            sb.append(recipe.getCategory().getId().toString().toLowerCase()).append(" ");
        }
        for (var out : recipe.getOutputs()) {
            for (var stack : out.getEmiStacks()) {
                sb.append(stack.getName().getString().toLowerCase()).append(" ");
            }
        }
        for (var in : recipe.getInputs()) {
            for (var stack : in.getEmiStacks()) {
                sb.append(stack.getName().getString().toLowerCase()).append(" ");
            }
        }
        return sb.toString();
    }

    private void onSearchQueryChanged(String query) {
        scrollOffset = 0;
        updateSearchResults(query);
    }

    private void updateSearchResults(String query) {
        filteredRecipes.clear();
        String lower = query.toLowerCase().trim();

        if (lower.isEmpty()) {
            int limit = Math.min(80, cachedAllRecipes.size());
            for (int i = 0; i < limit; i++) {
                filteredRecipes.add(cachedAllRecipes.get(i));
            }
            return;
        }

        // Extremely fast String.contains lookup without dynamic Component extraction
        for (SearchableRecipe sr : cachedAllRecipes) {
            if (filteredRecipes.size() >= 80) break;
            if (sr.searchIndex.contains(lower)) {
                filteredRecipes.add(sr);
            }
        }
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible) return;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);

        Font font = Minecraft.getInstance().font;
        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;

        // Solid Dark Backdrop
        graphics.fill(0, 0, screenWidth, screenHeight, 0xCC000000);
        graphics.fill(x, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xFF1E222B);
        graphics.renderOutline(x, y, DIALOG_WIDTH, DIALOG_HEIGHT, 0xFF4A90E2);

        // Header
        graphics.fill(x, y, x + DIALOG_WIDTH, y + 24, 0xFF282E3B);
        graphics.drawString(font, "§6➕ Add Recipe to Board", x + 10, y + 8, 0xFFFFFFFF, false);

        // Close [X]
        int closeX = x + DIALOG_WIDTH - 18;
        int closeY = y + 6;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        graphics.fill(closeX, closeY, closeX + 12, closeY + 12, closeHover ? 0xFFFF4444 : 0x44FF4444);
        graphics.drawString(font, "x", closeX + 3, closeY + 2, 0xFFFFFFFF, false);

        // Search Box
        searchBox.setX(x + 12);
        searchBox.setY(y + 30);
        searchBox.render(graphics, mouseX, mouseY, 0);

        // Custom Blank Node Button
        int blankBtnY = y + 50;
        boolean blankHover = mouseX >= x + 12 && mouseX <= x + DIALOG_WIDTH - 12 && mouseY >= blankBtnY && mouseY <= blankBtnY + 16;
        graphics.fill(x + 12, blankBtnY, x + DIALOG_WIDTH - 12, blankBtnY + 16, blankHover ? 0xFF355E8D : 0xFF2A394D);
        graphics.renderOutline(x + 12, blankBtnY, DIALOG_WIDTH - 24, 16, 0xFF4A90E2);
        graphics.drawCenteredString(font, "§b+ Create Blank Custom Recipe Node", x + DIALOG_WIDTH / 2, blankBtnY + 4, 0xFFFFFFFF);

        // Recipe List Area
        int listY = blankBtnY + 22;
        int listHeight = DIALOG_HEIGHT - (listY - y) - 10;
        graphics.fill(x + 12, listY, x + DIALOG_WIDTH - 12, listY + listHeight, 0xFF14171E);
        graphics.renderOutline(x + 12, listY, DIALOG_WIDTH - 24, listHeight, 0xFF3D4455);

        if (filteredRecipes.isEmpty()) {
            graphics.drawCenteredString(font, "§7No recipes found. Type to search...", x + DIALOG_WIDTH / 2, listY + listHeight / 2 - 4, 0xFF888888);
            graphics.pose().popPose();
            return;
        }

        int maxVisibleRows = listHeight / ROW_HEIGHT;
        for (int i = 0; i < maxVisibleRows; i++) {
            int recipeIdx = scrollOffset + i;
            if (recipeIdx >= filteredRecipes.size()) break;

            SearchableRecipe sr = filteredRecipes.get(recipeIdx);
            EmiRecipe recipe = sr.recipe;
            int rowY = listY + i * ROW_HEIGHT;

            boolean rowHover = mouseX >= x + 12 && mouseX <= x + DIALOG_WIDTH - 12 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (rowHover) {
                graphics.fill(x + 13, rowY + 1, x + DIALOG_WIDTH - 13, rowY + ROW_HEIGHT - 1, 0xFF283244);
            }

            // Recipe Name
            graphics.drawString(font, font.plainSubstrByWidth(sr.displayName, 170), x + 16, rowY + 4, 0xFFFFFFFF, false);

            // Inputs & Outputs Mini preview
            int slotX = x + 190;
            renderMiniStackList(graphics, recipe.getInputs(), slotX, rowY + 8);
            graphics.drawString(font, "➔", slotX + 46, rowY + 12, 0xFF888888, false);
            renderMiniStackList(graphics, recipe.getOutputs(), slotX + 62, rowY + 8);

            // Bottom line separator
            graphics.fill(x + 16, rowY + ROW_HEIGHT - 1, x + DIALOG_WIDTH - 16, rowY + ROW_HEIGHT, 0xFF2D3546);
        }

        graphics.pose().popPose();
    }

    private void renderMiniStackList(GuiGraphics graphics, List<? extends EmiIngredient> list, int x, int y) {
        int max = Math.min(2, list.size());
        for (int i = 0; i < max; i++) {
            var ing = list.get(i);
            if (!ing.getEmiStacks().isEmpty()) {
                var emiStack = ing.getEmiStacks().get(0);
                emiStack.render(graphics, x + i * 18, y, 0);
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!visible) return false;

        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;

        if (mouseX < x || mouseX > x + DIALOG_WIDTH || mouseY < y || mouseY > y + DIALOG_HEIGHT) {
            setVisible(false);
            return true;
        }

        // Close [X]
        int closeX = x + DIALOG_WIDTH - 18;
        int closeY = y + 6;
        if (mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12) {
            setVisible(false);
            return true;
        }

        if (searchBox.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Blank Recipe Button
        int blankBtnY = y + 50;
        if (mouseX >= x + 12 && mouseX <= x + DIALOG_WIDTH - 12 && mouseY >= blankBtnY && mouseY <= blankBtnY + 16) {
            RecipeNode blankNode = RecipeNode.create("Custom Recipe", 100.0, 30.0, GTVoltageTier.LV);
            blankNode.setPos(-parent.getPanX() + (parent.width / 2.0) - 70, -parent.getPanY() + (parent.height / 2.0) - 50);
            parent.getGraph().addNode(blankNode);
            parent.markSummaryDirty();
            parent.rebuildWidgets();
            setVisible(false);
            return true;
        }

        // Click recipe row
        int listY = blankBtnY + 22;
        int listHeight = DIALOG_HEIGHT - (listY - y) - 10;
        int maxVisibleRows = listHeight / ROW_HEIGHT;

        for (int i = 0; i < maxVisibleRows; i++) {
            int recipeIdx = scrollOffset + i;
            if (recipeIdx >= filteredRecipes.size()) break;

            int rowY = listY + i * ROW_HEIGHT;
            if (mouseX >= x + 12 && mouseX <= x + DIALOG_WIDTH - 12 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                SearchableRecipe sr = filteredRecipes.get(recipeIdx);
                RecipeNode newNode = EmiRecipeConverter.convert(sr.recipe);
                newNode.setPos(-parent.getPanX() + (parent.width / 2.0) - 70, -parent.getPanY() + (parent.height / 2.0) - 50);
                parent.getGraph().addNode(newNode);
                parent.markSummaryDirty();
                parent.rebuildWidgets();
                setVisible(false);
                return true;
            }
        }

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;
        int maxScroll = Math.max(0, filteredRecipes.size() - (DIALOG_HEIGHT - 100) / ROW_HEIGHT);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(delta)));
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        return searchBox.charTyped(codePoint, modifiers);
    }
}
