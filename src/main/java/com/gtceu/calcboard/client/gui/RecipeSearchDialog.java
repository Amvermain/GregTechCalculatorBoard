package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class RecipeSearchDialog {
    private final BoardScreen parent;
    private final EditBox searchBox;
    private final List<EmiRecipe> filteredRecipes = new ArrayList<>();
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
            updateSearchResults("");
        }
    }

    private void onSearchQueryChanged(String query) {
        scrollOffset = 0;
        updateSearchResults(query);
    }

    private void updateSearchResults(String query) {
        filteredRecipes.clear();
        String lower = query.toLowerCase().trim();

        try {
            var recipeManager = EmiApi.getRecipeManager();
            if (recipeManager != null) {
                for (EmiRecipeCategory cat : recipeManager.getCategories()) {
                    for (EmiRecipe recipe : recipeManager.getRecipes(cat)) {
                        if (filteredRecipes.size() >= 100) break; // Limit search results for fast UI
                        if (matchesQuery(recipe, lower)) {
                            filteredRecipes.add(recipe);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private boolean matchesQuery(EmiRecipe recipe, String query) {
        if (query.isEmpty()) return true;

        if (recipe.getId() != null && recipe.getId().toString().toLowerCase().contains(query)) {
            return true;
        }
        if (recipe.getCategory() != null && recipe.getCategory().getId().toString().toLowerCase().contains(query)) {
            return true;
        }
        // Match outputs
        for (var out : recipe.getOutputs()) {
            if (out.getName().getString().toLowerCase().contains(query)) {
                return true;
            }
        }
        // Match inputs
        for (var in : recipe.getInputs()) {
            for (var stack : in.getEmiStacks()) {
                if (stack.getName().getString().toLowerCase().contains(query)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible) return;

        Font font = Minecraft.getInstance().font;
        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;

        // Dark Modal Background & Dimmer
        graphics.fill(0, 0, screenWidth, screenHeight, 0x99000000);
        graphics.fill(x, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xFA1E222B);
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
            return;
        }

        int maxVisibleRows = listHeight / ROW_HEIGHT;
        for (int i = 0; i < maxVisibleRows; i++) {
            int recipeIdx = scrollOffset + i;
            if (recipeIdx >= filteredRecipes.size()) break;

            EmiRecipe recipe = filteredRecipes.get(recipeIdx);
            int rowY = listY + i * ROW_HEIGHT;

            boolean rowHover = mouseX >= x + 14 && mouseX <= x + DIALOG_WIDTH - 14 && mouseY >= rowY + 2 && mouseY <= rowY + ROW_HEIGHT - 2;
            graphics.fill(x + 14, rowY + 2, x + DIALOG_WIDTH - 14, rowY + ROW_HEIGHT - 2, rowHover ? 0xFF283446 : 0xFF1C222D);

            // Recipe Name / ID
            String rName = recipe.getId() != null ? recipe.getId().getPath() : "Recipe";
            if (rName.contains("/")) rName = rName.substring(rName.lastIndexOf('/') + 1);
            graphics.drawString(font, "§f" + rName, x + 20, rowY + 5, 0xFFFFFFFF, false);

            // Input / Output summary text
            String cat = recipe.getCategory() != null ? recipe.getCategory().getId().getPath() : "Recipe";
            graphics.drawString(font, "§7[" + cat + "] §e" + recipe.getInputs().size() + " in §7-> §a" + recipe.getOutputs().size() + " out", x + 20, rowY + 17, 0xFFAAAAAA, false);

            // Add button
            int addBtnX = x + DIALOG_WIDTH - 50;
            int addBtnY = rowY + 8;
            boolean btnHover = mouseX >= addBtnX && mouseX <= addBtnX + 32 && mouseY >= addBtnY && mouseY <= addBtnY + 14;
            graphics.fill(addBtnX, addBtnY, addBtnX + 32, addBtnY + 14, btnHover ? 0xFF55AA55 : 0xFF336633);
            graphics.drawCenteredString(font, "Add", addBtnX + 16, addBtnY + 3, 0xFFFFFFFF);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!visible) return false;

        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;

        // Close [X]
        int closeX = x + DIALOG_WIDTH - 18;
        int closeY = y + 6;
        if (mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12) {
            setVisible(false);
            return true;
        }

        // Custom Blank Node Button
        int blankBtnY = y + 50;
        if (mouseX >= x + 12 && mouseX <= x + DIALOG_WIDTH - 12 && mouseY >= blankBtnY && mouseY <= blankBtnY + 16) {
            RecipeNode customNode = RecipeNode.create("Custom Machine", 100.0, 32.0, GTVoltageTier.LV);
            customNode.addInput(IngredientStack.item(new ResourceLocation("minecraft", "iron_ingot"), "Iron Ingot", 1, 1.0));
            customNode.addOutput(IngredientStack.item(new ResourceLocation("minecraft", "iron_nugget"), "Iron Nugget", 9, 1.0));
            parent.addNode(customNode);
            setVisible(false);
            return true;
        }

        // Search Box click
        if (searchBox.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Recipe Row Click
        int listY = blankBtnY + 22;
        int listHeight = DIALOG_HEIGHT - (listY - y) - 10;
        int maxVisibleRows = listHeight / ROW_HEIGHT;

        if (mouseX >= x + 14 && mouseX <= x + DIALOG_WIDTH - 14 && mouseY >= listY && mouseY <= listY + listHeight) {
            int clickedRow = (int) (mouseY - listY) / ROW_HEIGHT;
            int recipeIdx = scrollOffset + clickedRow;
            if (recipeIdx >= 0 && recipeIdx < filteredRecipes.size()) {
                EmiRecipe recipe = filteredRecipes.get(recipeIdx);
                RecipeNode node = EmiRecipeConverter.convert(recipe);
                parent.addNode(node);
                setVisible(false);
                return true;
            }
        }

        // Outside modal click -> close
        if (mouseX < x || mouseX > x + DIALOG_WIDTH || mouseY < y || mouseY > y + DIALOG_HEIGHT) {
            setVisible(false);
            return true;
        }

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;
        if (delta > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else if (delta < 0) {
            scrollOffset = Math.min(Math.max(0, filteredRecipes.size() - 5), scrollOffset + 1);
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == 256) { // ESC
            setVisible(false);
            return true;
        }
        return searchBox.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        return searchBox.charTyped(codePoint, modifiers);
    }
}
