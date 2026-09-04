package com.gtceu.calcboard.integration.vanilla;

import com.gtceu.calcboard.client.gui.render.IngredientRenderer;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer;
import com.gtceu.calcboard.integration.spi.IRecipeViewerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class VanillaRecipeViewerAdapter implements IRecipeViewerAdapter {

    @Override
    public String getViewerId() {
        return "vanilla";
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isRecipeBakingComplete() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null;
    }

    @Override
    public void runWhenReady(Runnable callback) {
        if (callback != null) {
            callback.run();
        }
    }

    @Override
    public List<SearchableRecipe> collectSearchableRecipes() {
        return Collections.emptyList();
    }

    @Override
    public RecipeNode convertToNode(Object viewerRecipe) {
        if (viewerRecipe instanceof RecipeNode rn) {
            RecipeNode copy = rn.copy();
            com.gtceu.calcboard.api.preset.CategoryMachinePresetManager.getInstance().applyPresetIfPresent(copy);
            return copy;
        }
        return null;
    }

    @Override
    public boolean displayRecipes(IngredientStack ingredient) {
        return false;
    }

    @Override
    public boolean displayUses(IngredientStack ingredient) {
        return false;
    }

    @Override
    public Set<ResourceLocation> getFavoriteRecipeIds() {
        return Collections.emptySet();
    }

    @Override
    public boolean isFavorite(Object viewerRecipe) {
        return false;
    }

    @Override
    public void toggleFavorite(Object viewerRecipe) {}

    @Override
    public int[] calculatePreviewBounds(SearchableRecipe sr, int dialogX, int dialogY, int dialogW, int dialogH, int hoveredRowY, int screenW, int screenH) {
        return RecipeHoverPreviewRenderer.calculatePreviewBounds(sr, dialogX, dialogY, dialogW, dialogH, hoveredRowY, screenW, screenH);
    }

    @Override
    public void renderPreviewCard(GuiGraphics graphics, Font font, SearchableRecipe sr, int dialogX, int dialogY, int dialogW, int dialogH, int hoveredRowY, int mouseX, int mouseY, float partialTick, int screenW, int screenH) {
        RecipeHoverPreviewRenderer.renderPreview(graphics, sr, dialogX, dialogY, dialogW, dialogH, hoveredRowY, mouseX, mouseY, partialTick, screenW, screenH);
    }

    @Override
    public Object getHoveredPreviewIngredient(SearchableRecipe sr, int dialogX, int dialogY, int dialogW, int dialogH, int hoveredRowY, int mouseX, int mouseY, int screenW, int screenH) {
        return RecipeHoverPreviewRenderer.getHoveredIngredient(sr, dialogX, dialogY, dialogW, dialogH, hoveredRowY, mouseX, mouseY, screenW, screenH);
    }

    @Override
    public boolean handleHoveredIngredientClick(Object hoveredIngredient, EditBox searchBox) {
        if (hoveredIngredient instanceof IngredientStack is) {
            String name = is.getDisplayName();
            if (searchBox != null && name != null && !name.isEmpty()) {
                searchBox.setValue(com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.stripFormatting(name).trim());
                searchBox.setFocused(true);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean handleHoveredIngredientLookup(Object hoveredIngredient, boolean isRecipes) {
        return false;
    }

    @Override
    public int renderRowIcon(GuiGraphics graphics, Font font, Object viewerRecipe, int listX, int rowY, ResourceLocation matchedOutputId, String matchedOutputName) {
        if (viewerRecipe instanceof RecipeNode rn) {
            int currentX = listX + 6;
            if (!rn.getInputs().isEmpty()) {
                com.gtceu.calcboard.client.gui.render.IngredientRenderer.render(graphics, rn.getInputs().get(0), currentX, rowY + 8);
            }
            currentX += 18;

            graphics.drawString(font, "➔", currentX + 2, rowY + 12, 0xFF657595, false);
            currentX += 14;

            if (rn.isGenerator()) {
                graphics.drawString(font, "⚡", currentX + 2, rowY + 12, 0xFFFFD700, false);
                currentX += 16;
            }

            var outputs = rn.getOutputs();
            if (outputs != null && !outputs.isEmpty()) {
                var sortedOutputs = new java.util.ArrayList<>(outputs);
                if (matchedOutputId != null || matchedOutputName != null) {
                    int matchIdx = -1;
                    for (int i = 0; i < sortedOutputs.size(); i++) {
                        var stack = sortedOutputs.get(i);
                        if (stack == null) continue;
                        if (matchedOutputId != null && matchedOutputId.equals(stack.getId())) {
                            matchIdx = i;
                            break;
                        }
                        if (matchedOutputName != null && matchedOutputName.equalsIgnoreCase(stack.getDisplayName())) {
                            matchIdx = i;
                            break;
                        }
                    }
                    if (matchIdx > 0) {
                        var matched = sortedOutputs.remove(matchIdx);
                        sortedOutputs.add(0, matched);
                    }
                }

                int maxDisplay = rn.isGenerator() ? 2 : 3;
                int count = Math.min(sortedOutputs.size(), maxDisplay);
                for (int i = 0; i < count; i++) {
                    var out = sortedOutputs.get(i);
                    if (out != null) {
                        com.gtceu.calcboard.client.gui.render.IngredientRenderer.render(graphics, out, currentX, rowY + 8);
                    }
                    currentX += 18;
                }

                if (sortedOutputs.size() > maxDisplay) {
                    int remaining = sortedOutputs.size() - maxDisplay;
                    String badge = "+" + remaining;
                    graphics.drawString(font, badge, currentX, rowY + 12, 0xFF94A3B8, false);
                    currentX += font.width(badge) + 2;
                }
            }
            return currentX - listX;
        }
        return 58;
    }

    @Override
    public boolean isBoMGoalRegistrationSupported() {
        return false;
    }

    @Override
    public void registerBoMGoal(MultiblockBOMSummary summary) {}
}



