package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import com.gtceu.calcboard.client.gui.dialog.RecipeSearchDialog;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.integration.spi.IRecipeViewerAdapter;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.runtime.EmiPersistentData;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class EmiRecipeViewerAdapter implements IRecipeViewerAdapter {

    private static final java.lang.reflect.Field CURRENT_PAGE_FIELD;

    static {
        java.lang.reflect.Field field = null;
        try {
            field = dev.emi.emi.screen.RecipeScreen.class.getDeclaredField("currentPage");
            field.setAccessible(true);
        } catch (Throwable ignored) {}
        CURRENT_PAGE_FIELD = field;
    }

    @Override
    public String getViewerId() {
        return "emi";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean isAvailable() {
        return ModCompatHelper.isEmiLoaded();
    }

    @Override
    public boolean isRecipeBakingComplete() {
        if (!isAvailable()) return false;
        try {
            return EmiLifecycleHook.isEmiRecipeBakingComplete();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void runWhenReady(Runnable callback) {
        if (!isAvailable()) return;
        try {
            EmiLifecycleHook.runWhenEmiReady(callback);
        } catch (Throwable t) {
            if (callback != null) callback.run();
        }
    }

    @Override
    public List<SearchableRecipe> collectSearchableRecipes() {
        if (!isAvailable() || !isRecipeBakingComplete()) return Collections.emptyList();
        try {
            var emiManager = EmiApi.getRecipeManager();
            if (emiManager == null || emiManager.getRecipes() == null) return Collections.emptyList();

            List<EmiRecipe> recipes = emiManager.getRecipes();
            List<SearchableRecipe> list = new ArrayList<>(recipes.size());
            for (EmiRecipe r : recipes) {
                try {
                    SearchableRecipe sr = RecipeSearchEngine.buildIndex(r);
                    if (sr != null) list.add(sr);
                } catch (Throwable ignored) {}
            }
            return list;
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    @Override
    public RecipeNode convertToNode(Object viewerRecipe) {
        if (viewerRecipe instanceof EmiRecipe er) {
            return EmiRecipeConverter.convert(er);
        }
        if (viewerRecipe instanceof RecipeNode rn) {
            return rn.copy();
        }
        return null;
    }

    @Override
    public boolean displayRecipes(IngredientStack ingredient) {
        if (!isAvailable() || ingredient == null) return false;
        try {
            EmiStack stack = EmiStackHelper.toEmiStack(ingredient);
            if (!stack.isEmpty()) {
                EmiApi.displayRecipes(stack);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public boolean displayUses(IngredientStack ingredient) {
        if (!isAvailable() || ingredient == null) return false;
        try {
            EmiStack stack = EmiStackHelper.toEmiStack(ingredient);
            if (!stack.isEmpty()) {
                EmiApi.displayUses(stack);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public Set<ResourceLocation> getFavoriteRecipeIds() {
        if (!isAvailable()) return Collections.emptySet();
        Set<ResourceLocation> ids = new HashSet<>();
        try {
            List<EmiFavorite> favs = new ArrayList<>();
            if (EmiFavorites.favorites != null) {
                favs.addAll(EmiFavorites.favorites);
            }
            if (EmiFavorites.favoriteSidebar != null) {
                for (var sf : EmiFavorites.favoriteSidebar) {
                    if (sf != null && !favs.contains(sf)) {
                        favs.add(sf);
                    }
                }
            }

            if (!favs.isEmpty()) {
                var rm = EmiApi.getRecipeManager();
                for (var fav : favs) {
                    collectFavoriteRecipeIds(fav, rm, ids);
                }
            }
        } catch (Throwable ignored) {}
        return ids;
    }

    @Override
    public boolean isFavorite(Object viewerRecipe) {
        if (!isAvailable() || viewerRecipe == null) return false;
        if (viewerRecipe instanceof EmiRecipe er) {
            if (er.getId() == null) return false;
            return getFavoriteRecipeIds().contains(er.getId());
        }
        return false;
    }

    @Override
    public void toggleFavorite(Object viewerRecipe) {
        if (!isAvailable() || !(viewerRecipe instanceof EmiRecipe recipe)) return;
        try {
            var outputs = recipe.getOutputs();
            EmiIngredient stack = !outputs.isEmpty() ? outputs.get(0) : null;
            if (stack == null) return;

            if (EmiFavorites.favorites != null) {
                EmiFavorite existing = null;
                for (var fav : EmiFavorites.favorites) {
                    if (fav.getRecipe() != null && recipe.getId() != null && recipe.getId().equals(fav.getRecipe().getId())) {
                        existing = fav;
                        break;
                    }
                }
                if (existing != null) {
                    EmiFavorites.removeFavorite(existing);
                } else {
                    EmiFavorites.addFavorite(stack, recipe);
                }
                RecipeSearchDialog.notifyFavoritesChanged();
            }
        } catch (Throwable ignored) {}
    }

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
        if (hoveredIngredient instanceof EmiIngredient ing && !ing.isEmpty()) {
            if (!ing.getEmiStacks().isEmpty()) {
                String name = ing.getEmiStacks().get(0).getName().getString();
                if (searchBox != null) {
                    searchBox.setValue(name);
                    searchBox.setFocused(true);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean handleHoveredIngredientLookup(Object hoveredIngredient, boolean isRecipes) {
        if (hoveredIngredient instanceof EmiIngredient ing && !ing.isEmpty()) {
            if (isRecipes) {
                EmiApi.displayRecipes(ing);
            } else {
                EmiApi.displayUses(ing);
            }
            return true;
        }
        return false;
    }

    @Override
    public int renderRowIcon(GuiGraphics graphics, Font font, Object viewerRecipe, int listX, int rowY, ResourceLocation matchedOutputId, String matchedOutputName) {
        if (viewerRecipe instanceof EmiRecipe r) {
            int currentX = listX + 6;
            if (!r.getInputs().isEmpty() && !r.getInputs().get(0).getEmiStacks().isEmpty()) {
                r.getInputs().get(0).getEmiStacks().get(0).render(graphics, currentX, rowY + 8, 0);
            }
            currentX += 18;

            graphics.drawString(font, "➔", currentX + 2, rowY + 12, 0xFF657595, false);
            currentX += 14;

            List<EmiStack> outputs = r.getOutputs();
            if (outputs != null && !outputs.isEmpty()) {
                currentX = renderEmiOutputs(graphics, font, currentX, rowY, outputs, matchedOutputId, matchedOutputName);
            }
            return currentX - listX;
        } else if (viewerRecipe instanceof RecipeNode rn) {
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

            List<IngredientStack> outputs = rn.getOutputs();
            if (outputs != null && !outputs.isEmpty()) {
                currentX = renderNodeOutputs(graphics, font, currentX, rowY, outputs, rn.isGenerator(), matchedOutputId, matchedOutputName);
            }
            return currentX - listX;
        }
        return 58;
    }

    @Override
    public boolean isBoMGoalRegistrationSupported() {
        return isAvailable();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void registerBoMGoal(MultiblockBOMSummary summary) {
        if (!isAvailable() || summary == null) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            MultiblockBOMEmiRecipe projectRoot = MultiblockBOMEmiRecipe.createProjectRootRecipe(summary);
            dev.emi.emi.bom.BoM.setGoal(projectRoot);
            dev.emi.emi.bom.BoM.craftingMode = true;

            if (dev.emi.emi.bom.BoM.tree != null) {
                var recipeManager = EmiApi.getRecipeManager();
                for (MultiblockBOMSummary.BOMItemEntry item : summary.aggregatedItems()) {
                    registerBomItemResolution(item, recipeManager);
                }

                dev.emi.emi.bom.BoM.tree.recalculate();
                EmiPlayerInventory inv = new EmiPlayerInventory(mc.player);
                dev.emi.emi.bom.BoM.tree.calculateProgress(inv);
                EmiFavorites.updateSynthetic(inv);
            }

            EmiPersistentData.save();
            EmiScreenManager.repopulatePanels(SidebarType.FAVORITES);

            int addedCount = summary.totalUniqueItemTypes();
            mc.player.displayClientMessage(
                Component.translatable("message.gtcalcboard.bom_registered_emi", addedCount),
                true
            );
        } catch (Throwable t) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[GTCalcBoard] Failed to register EMI BoM: " + t.getMessage()),
                    false
                );
            }
        }
    }

    @Override
    public boolean isSearchFieldFocused() {
        if (!isAvailable()) return false;
        try {
            if (EmiScreenManager.search != null && EmiScreenManager.search.isFocused()) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public boolean tryAddHoveredRecipeToBoard(net.minecraft.client.gui.screens.Screen screen, double mouseX, double mouseY) {
        if (!isAvailable()) return false;
        try {
            int mX = (int) Math.round(mouseX);
            int mY = (int) Math.round(mouseY);

            if (tryAddHoveredFromEmiScreenManager(mX, mY)) {
                return true;
            }

            if (screen instanceof dev.emi.emi.screen.RecipeScreen recipeScreen) {
                return tryAddHoveredFromRecipeScreen(recipeScreen, mX, mY);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean tryAddHoveredFromEmiScreenManager(int mX, int mY) {
        try {
            var interaction = dev.emi.emi.screen.EmiScreenManager.getHoveredStack(mX, mY, true);
            if (interaction == null) return false;

            EmiRecipe r = interaction.getRecipeContext();
            if (r != null) {
                CalcBoardEmiPlugin.addRecipeToBoard(r, false);
                return true;
            }

            return tryAddFromIngredientStack(interaction.getStack());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean tryAddFromIngredientStack(dev.emi.emi.api.stack.EmiIngredient ing) {
        if (ing == null || ing.isEmpty() || ing.getEmiStacks().isEmpty()) return false;
        EmiStack es = ing.getEmiStacks().get(0);
        var rm = EmiApi.getRecipeManager();
        if (rm == null) return false;

        List<EmiRecipe> recipes = rm.getRecipesByOutput(es);
        if (recipes != null && !recipes.isEmpty()) {
            CalcBoardEmiPlugin.addRecipeToBoard(recipes.get(0), false);
            return true;
        }
        return false;
    }

    private static boolean tryAddHoveredFromRecipeScreen(dev.emi.emi.screen.RecipeScreen recipeScreen, int mX, int mY) {
        try {
            if (CURRENT_PAGE_FIELD == null) return false;
            Object pageObj = CURRENT_PAGE_FIELD.get(recipeScreen);
            if (pageObj instanceof List<?> list) {
                EmiRecipe targetRecipe = findRecipeFromWidgetGroups(list, mX, mY);
                if (targetRecipe != null) {
                    CalcBoardEmiPlugin.addRecipeToBoard(targetRecipe, false);
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static EmiRecipe findRecipeFromWidgetGroups(List<?> list, int mX, int mY) {
        for (Object o : list) {
            if (o instanceof dev.emi.emi.screen.WidgetGroup wg && wg.recipe != null && isWidgetHovered(wg, mX, mY)) {
                return wg.recipe;
            }
        }
        for (Object o : list) {
            if (o instanceof dev.emi.emi.screen.WidgetGroup wg && wg.recipe != null) {
                return wg.recipe;
            }
        }
        return null;
    }

    private static boolean isWidgetHovered(dev.emi.emi.screen.WidgetGroup wg, int mX, int mY) {
        return mX >= wg.x() && mX <= wg.x() + wg.getWidth() && mY >= wg.y() && mY <= wg.y() + wg.getHeight();
    }

    private static void collectFavoriteRecipeIds(EmiFavorite fav, EmiRecipeManager rm, Set<ResourceLocation> ids) {
        if (fav.getRecipe() != null && fav.getRecipe().getId() != null) {
            ids.add(fav.getRecipe().getId());
            return;
        }
        if (fav.getEmiStacks().isEmpty() || rm == null) return;
        for (var stack : fav.getEmiStacks()) {
            addOutputRecipeIds(rm, stack, ids);
        }
    }

    private static void addOutputRecipeIds(EmiRecipeManager rm, EmiStack stack, Set<ResourceLocation> ids) {
        var outRecipes = rm.getRecipesByOutput(stack);
        if (outRecipes == null) return;
        for (var r : outRecipes) {
            if (r != null && r.getId() != null) {
                ids.add(r.getId());
            }
        }
    }

    private static int renderEmiOutputs(
            GuiGraphics graphics,
            net.minecraft.client.gui.Font font,
            int startX,
            int rowY,
            List<EmiStack> outputs,
            ResourceLocation matchedId,
            String matchedName
    ) {
        List<EmiStack> sortedOutputs = new ArrayList<>(outputs);
        sortEmiOutputs(sortedOutputs, matchedId, matchedName);

        int currentX = startX;
        int maxDisplay = 3;
        int count = Math.min(sortedOutputs.size(), maxDisplay);
        for (int i = 0; i < count; i++) {
            EmiStack out = sortedOutputs.get(i);
            if (out != null) {
                out.render(graphics, currentX, rowY + 8, 0);
            }
            currentX += 18;
        }

        if (sortedOutputs.size() > maxDisplay) {
            int remaining = sortedOutputs.size() - maxDisplay;
            String badge = "+" + remaining;
            graphics.drawString(font, badge, currentX, rowY + 12, 0xFF94A3B8, false);
            currentX += font.width(badge) + 2;
        }
        return currentX;
    }

    private static void sortEmiOutputs(List<EmiStack> outputs, ResourceLocation matchedId, String matchedName) {
        if (matchedId == null && matchedName == null) return;
        int matchIdx = findMatchingEmiOutputIndex(outputs, matchedId, matchedName);
        if (matchIdx > 0) {
            outputs.add(0, outputs.remove(matchIdx));
        }
    }

    private static int findMatchingEmiOutputIndex(List<EmiStack> outputs, ResourceLocation matchedId, String matchedName) {
        for (int i = 0; i < outputs.size(); i++) {
            EmiStack stack = outputs.get(i);
            if (isEmiStackMatch(stack, matchedId, matchedName)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isEmiStackMatch(EmiStack stack, ResourceLocation matchedId, String matchedName) {
        if (stack == null) return false;
        if (matchedId != null && matchedId.equals(stack.getId())) return true;
        if (matchedName != null && stack.getName() != null) {
            return matchedName.equalsIgnoreCase(stack.getName().getString());
        }
        return false;
    }

    private static int renderNodeOutputs(
            GuiGraphics graphics,
            net.minecraft.client.gui.Font font,
            int startX,
            int rowY,
            List<IngredientStack> outputs,
            boolean isGenerator,
            ResourceLocation matchedId,
            String matchedName
    ) {
        List<IngredientStack> sortedOutputs = new ArrayList<>(outputs);
        sortIngredientOutputs(sortedOutputs, matchedId, matchedName);

        int currentX = startX;
        int maxDisplay = isGenerator ? 2 : 3;
        int count = Math.min(sortedOutputs.size(), maxDisplay);
        for (int i = 0; i < count; i++) {
            IngredientStack out = sortedOutputs.get(i);
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
        return currentX;
    }

    private static void sortIngredientOutputs(List<IngredientStack> outputs, ResourceLocation matchedId, String matchedName) {
        if (matchedId == null && matchedName == null) return;
        int matchIdx = findMatchingIngredientOutputIndex(outputs, matchedId, matchedName);
        if (matchIdx > 0) {
            outputs.add(0, outputs.remove(matchIdx));
        }
    }

    private static int findMatchingIngredientOutputIndex(List<IngredientStack> outputs, ResourceLocation matchedId, String matchedName) {
        for (int i = 0; i < outputs.size(); i++) {
            IngredientStack stack = outputs.get(i);
            if (isIngredientStackMatch(stack, matchedId, matchedName)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isIngredientStackMatch(IngredientStack stack, ResourceLocation matchedId, String matchedName) {
        if (stack == null) return false;
        if (matchedId != null && matchedId.equals(stack.getId())) return true;
        if (matchedName != null && matchedName.equalsIgnoreCase(stack.getDisplayName())) return true;
        return false;
    }

    private static void registerBomItemResolution(MultiblockBOMSummary.BOMItemEntry item, EmiRecipeManager recipeManager) {
        ItemStack is = item.resolveItemStack();
        if (is.isEmpty()) return;
        EmiStack stack = EmiStack.of(is, 1);
        EmiRecipe rec = resolveRecipeForStack(stack, recipeManager);
        if (rec != null) {
            dev.emi.emi.bom.BoM.tree.addResolution(stack, rec);
        }
    }

    private static EmiRecipe resolveRecipeForStack(EmiStack stack, EmiRecipeManager recipeManager) {
        EmiRecipe rec = dev.emi.emi.bom.BoM.getRecipe(stack);
        if (rec != null || recipeManager == null) return rec;
        List<EmiRecipe> recs = recipeManager.getRecipesByOutput(stack);
        if (recs != null && !recs.isEmpty()) {
            return recs.get(0);
        }
        return null;
    }

    @Override
    public boolean isViewerScreen(net.minecraft.client.gui.screens.Screen screen) {
        if (screen == null) return false;
        return screen instanceof dev.emi.emi.screen.RecipeScreen;
    }
}


