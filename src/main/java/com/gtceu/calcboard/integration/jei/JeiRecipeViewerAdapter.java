package com.gtceu.calcboard.integration.jei;

import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.SearchableRecipe;
import com.gtceu.calcboard.integration.spi.IRecipeViewerAdapter;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class JeiRecipeViewerAdapter implements IRecipeViewerAdapter {

    private static volatile IJeiRuntime jeiRuntime = null;
    private static final List<Runnable> READY_CALLBACKS = new ArrayList<>();

    public static synchronized void setJeiRuntime(IJeiRuntime runtime) {
        jeiRuntime = runtime;
        if (runtime != null) {
            for (Runnable cb : READY_CALLBACKS) {
                try {
                    cb.run();
                } catch (Throwable ignored) {}
            }
            READY_CALLBACKS.clear();
        }
    }

    public static IJeiRuntime getJeiRuntime() {
        return jeiRuntime;
    }

    @Override
    public String getViewerId() {
        return "jei";
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean isAvailable() {
        return ModCompatHelper.isJeiLoaded();
    }

    @Override
    public boolean isRecipeBakingComplete() {
        return jeiRuntime != null;
    }

    @Override
    public void runWhenReady(Runnable callback) {
        if (callback == null) return;
        if (isRecipeBakingComplete()) {
            callback.run();
        } else {
            synchronized (READY_CALLBACKS) {
                if (isRecipeBakingComplete()) {
                    callback.run();
                } else {
                    READY_CALLBACKS.add(callback);
                }
            }
        }
    }

    @Override
    public List<SearchableRecipe> collectSearchableRecipes() {
        return Collections.emptyList();
    }

    @Override
    public RecipeNode convertToNode(Object viewerRecipe) {
        if (viewerRecipe instanceof RecipeNode rn) {
            return rn.copy();
        }
        return null;
    }

    @Override
    public boolean displayRecipes(IngredientStack ingredient) {
        if (!isAvailable() || ingredient == null || jeiRuntime == null) return false;
        return showJeiFocus(ingredient, RecipeIngredientRole.OUTPUT);
    }

    @Override
    public boolean displayUses(IngredientStack ingredient) {
        if (!isAvailable() || ingredient == null || jeiRuntime == null) return false;
        return showJeiFocus(ingredient, RecipeIngredientRole.INPUT);
    }

    private boolean showJeiFocus(IngredientStack ingredient, RecipeIngredientRole role) {
        try {
            var focusFactory = jeiRuntime.getJeiHelpers().getFocusFactory();
            var recipesGui = jeiRuntime.getRecipesGui();

            if (ingredient.isFluid()) {
                var fluid = ForgeRegistries.FLUIDS.getValue(ingredient.getId());
                if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                    FluidStack fs = new FluidStack(fluid, (int) Math.max(1, ingredient.getAmount()));
                    var focus = focusFactory.createFocus(role, ForgeTypes.FLUID_STACK, fs);
                    recipesGui.show(List.of(focus));
                    return true;
                }
            } else {
                var item = ForgeRegistries.ITEMS.getValue(ingredient.getId());
                if (item != null && item != Items.AIR) {
                    ItemStack is = new ItemStack(item, (int) Math.max(1, Math.round(ingredient.getAmount())));
                    var focus = focusFactory.createFocus(role, VanillaTypes.ITEM_STACK, is);
                    recipesGui.show(List.of(focus));
                    return true;
                }
            }
        } catch (Throwable ignored) {}
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
        return null;
    }

    @Override
    public boolean handleHoveredIngredientClick(Object hoveredIngredient, EditBox searchBox) {
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
        return isAvailable() && JeiPlusPlusHelper.isJeiPlusPlusLoaded();
    }

    @Override
    public void registerBoMGoal(MultiblockBOMSummary summary) {
        if (!isAvailable() || summary == null) return;
        if (JeiPlusPlusHelper.isJeiPlusPlusLoaded()) {
            boolean success = JeiPlusPlusHelper.registerBoMGoal(jeiRuntime, summary);
            if (success) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                        Component.translatable("message.gtcalcboard.bom_registered_jei", summary.totalUniqueItemTypes()),
                        true
                    );
                }
            }
        }
    }
}



