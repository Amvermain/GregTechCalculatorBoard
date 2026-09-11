package com.gtceu.calcboard.integration.jei;

import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.client.gui.search.RecipeHoverPreviewRenderer;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class JeiRecipeViewerAdapter implements IRecipeViewerAdapter {

    private static volatile IJeiRuntime jeiRuntime = null;
    private static final List<Runnable> READY_CALLBACKS = new ArrayList<>();
    private static final Class<?> RECIPES_GUI_CLASS;
    private static final java.lang.reflect.Field LAYOUTS_FIELD;
    private static volatile java.lang.reflect.Field recipeLayoutsWithButtonsField = null;
    private static volatile Method recipeLayoutMethod = null;
    private static volatile Method ingredientHasKeyboardFocusMethod = null;
    private static volatile Method ingredientIsFilterFocusedMethod = null;
    private static volatile Method bookmarkHasKeyboardFocusMethod = null;
    private static volatile boolean focusMethodsResolved = false;

    static {
        Class<?> clazz = null;
        java.lang.reflect.Field layoutsF = null;
        try {
            clazz = Class.forName("mezz.jei.gui.recipes.RecipesGui");
            layoutsF = clazz.getDeclaredField("layouts");
            layoutsF.setAccessible(true);
        } catch (Throwable ignored) {}
        RECIPES_GUI_CLASS = clazz;
        LAYOUTS_FIELD = layoutsF;
    }

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

    private static final Set<ResourceLocation> FAVORITE_RECIPES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public List<SearchableRecipe> collectSearchableRecipes() {
        if (!isAvailable() || jeiRuntime == null) return Collections.emptyList();
        List<SearchableRecipe> list = new ArrayList<>();
        try {
            var recipeManager = jeiRuntime.getRecipeManager();
            var categoryLookup = recipeManager.createRecipeCategoryLookup();
            if (categoryLookup != null) {
                var categories = categoryLookup.get().toList();
                com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [JEI] Discovered {} recipe categories in JEI.", categories.size());
                for (var category : categories) {
                    if (category == null || category.getRecipeType() == null) continue;
                    try {
                        var recipeLookup = recipeManager.createRecipeLookup(category.getRecipeType());
                        if (recipeLookup != null) {
                            var recipes = recipeLookup.get().toList();
                            for (var recipe : recipes) {
                                if (recipe != null) {
                                    SearchableRecipe sr = JeiRecipeSearchIndexer.buildIndexUntyped(category, recipe, jeiRuntime);
                                    if (sr != null) {
                                        list.add(sr);
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {
                        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] [JEI] Failed to collect recipes for category '{}': {}", category.getRecipeType().getUid(), t.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().error("Failed to collect searchable recipes from JEI", t);
        }
        return list;
    }

    @Override
    public RecipeNode convertToNode(Object viewerRecipe) {
        if (viewerRecipe instanceof RecipeNode rn) {
            return rn.copy();
        }
        if (viewerRecipe instanceof JeiRecipeWrapper<?> wrapper) {
            return JeiRecipeConverter.convert(wrapper);
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
        return Collections.unmodifiableSet(FAVORITE_RECIPES);
    }

    @Override
    public boolean isFavorite(Object viewerRecipe) {
        ResourceLocation id = getRecipeId(viewerRecipe);
        return id != null && FAVORITE_RECIPES.contains(id);
    }

    @Override
    public void toggleFavorite(Object viewerRecipe) {
        ResourceLocation id = getRecipeId(viewerRecipe);
        if (id != null) {
            if (FAVORITE_RECIPES.contains(id)) {
                FAVORITE_RECIPES.remove(id);
            } else {
                FAVORITE_RECIPES.add(id);
            }
        }
    }

    private ResourceLocation getRecipeId(Object viewerRecipe) {
        if (viewerRecipe instanceof JeiRecipeWrapper<?> wrapper) {
            return wrapper.getRecipeId();
        } else if (viewerRecipe instanceof RecipeNode rn) {
            return rn.getRecipeCategoryId();
        }
        return null;
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
        if (hoveredIngredient instanceof IngredientStack is) {
            String name = is.getDisplayName();
            if (searchBox != null && name != null && !name.isEmpty()) {
                searchBox.setValue(RecipeSearchEngine.stripFormatting(name).trim());
                searchBox.setFocused(true);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean handleHoveredIngredientLookup(Object hoveredIngredient, boolean isRecipes) {
        if (hoveredIngredient instanceof IngredientStack is) {
            return isRecipes ? displayRecipes(is) : displayUses(is);
        }
        return false;
    }

    @Override
    public int renderRowIcon(GuiGraphics graphics, Font font, Object viewerRecipe, int listX, int rowY, ResourceLocation matchedOutputId, String matchedOutputName) {
        RecipeNode rn = null;
        if (viewerRecipe instanceof RecipeNode r) {
            rn = r;
        } else if (viewerRecipe instanceof JeiRecipeWrapper<?> wrapper) {
            rn = JeiRecipeConverter.convert(wrapper);
        }
        if (rn != null) {
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
        return isAvailable() && (JeiPlusPlusHelper.isJeiPlusPlusLoaded() || JeiUnofficialHelper.isJeiUnofficialLoaded(jeiRuntime));
    }

    @Override
    public void registerBoMGoal(MultiblockBOMSummary summary) {
        if (!isAvailable() || summary == null) return;
        boolean success = false;
        if (JeiPlusPlusHelper.isJeiPlusPlusLoaded()) {
            success = JeiPlusPlusHelper.registerBoMGoal(jeiRuntime, summary);
        } else if (JeiUnofficialHelper.isJeiUnofficialLoaded(jeiRuntime)) {
            success = JeiUnofficialHelper.registerBoMGroup(jeiRuntime, summary);
        }
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

    @Override
    public boolean isSearchFieldFocused() {
        if (!isAvailable() || jeiRuntime == null) return false;
        ensureFocusMethodsResolved();
        try {
            var overlay = jeiRuntime.getIngredientListOverlay();
            if (overlay != null) {
                if (ingredientHasKeyboardFocusMethod != null && Boolean.TRUE.equals(ingredientHasKeyboardFocusMethod.invoke(overlay))) return true;
                if (ingredientIsFilterFocusedMethod != null && Boolean.TRUE.equals(ingredientIsFilterFocusedMethod.invoke(overlay))) return true;
            }
            var bookmarkOverlay = jeiRuntime.getBookmarkOverlay();
            if (bookmarkOverlay != null && bookmarkHasKeyboardFocusMethod != null) {
                if (Boolean.TRUE.equals(bookmarkHasKeyboardFocusMethod.invoke(bookmarkOverlay))) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void ensureFocusMethodsResolved() {
        if (focusMethodsResolved) return;
        synchronized (JeiRecipeViewerAdapter.class) {
            if (focusMethodsResolved) return;
            var overlay = jeiRuntime != null ? jeiRuntime.getIngredientListOverlay() : null;
            if (overlay != null) {
                ingredientHasKeyboardFocusMethod = findMethodSilently(overlay.getClass(), "hasKeyboardFocus");
                ingredientIsFilterFocusedMethod = findMethodSilently(overlay.getClass(), "isFilterFocused");
            }
            var bookmarkOverlay = jeiRuntime != null ? jeiRuntime.getBookmarkOverlay() : null;
            if (bookmarkOverlay != null) {
                bookmarkHasKeyboardFocusMethod = findMethodSilently(bookmarkOverlay.getClass(), "hasKeyboardFocus");
            }
            focusMethodsResolved = true;
        }
    }

    private static Method findMethodSilently(Class<?> clazz, String name) {
        if (clazz == null) return null;
        try {
            return clazz.getMethod(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public boolean tryAddHoveredRecipeToBoard(net.minecraft.client.gui.screens.Screen screen, double mouseX, double mouseY) {
        if (!isAvailable() || jeiRuntime == null || screen == null || LAYOUTS_FIELD == null) return false;
        try {
            if (!isViewerScreen(screen)) return false;
            Object recipeGuiLayouts = LAYOUTS_FIELD.get(screen);
            if (recipeGuiLayouts == null) return false;

            if (recipeLayoutsWithButtonsField == null) {
                var f = recipeGuiLayouts.getClass().getDeclaredField("recipeLayoutsWithButtons");
                f.setAccessible(true);
                recipeLayoutsWithButtonsField = f;
            }
            @SuppressWarnings("unchecked")
            List<?> layoutsWithButtons = (List<?>) recipeLayoutsWithButtonsField.get(recipeGuiLayouts);
            if (layoutsWithButtons == null || layoutsWithButtons.isEmpty()) return false;

            mezz.jei.api.gui.IRecipeLayoutDrawable targetLayout = resolveHoveredOrFirstLayout(layoutsWithButtons, mouseX, mouseY);
            if (targetLayout != null) {
                var category = targetLayout.getRecipeCategory();
                var recipeObj = targetLayout.getRecipe();
                if (category != null && recipeObj != null) {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    JeiRecipeWrapper<?> wrapper = new JeiRecipeWrapper(category, recipeObj);
                    RecipeNode node = JeiRecipeConverter.convert(wrapper);
                    if (node != null) {
                        applyConvertedNodeToBoard(node, category.getTitle().getString());
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private mezz.jei.api.gui.IRecipeLayoutDrawable resolveHoveredOrFirstLayout(List<?> layoutsWithButtons, double mouseX, double mouseY) {
        mezz.jei.api.gui.IRecipeLayoutDrawable targetLayout = null;
        for (Object lwb : layoutsWithButtons) {
            if (lwb == null) continue;
            mezz.jei.api.gui.IRecipeLayoutDrawable layoutDrawable = extractLayoutDrawable(lwb);
            if (layoutDrawable != null && layoutDrawable.isMouseOver(mouseX, mouseY)) {
                return layoutDrawable;
            }
            if (targetLayout == null && layoutDrawable != null) {
                targetLayout = layoutDrawable;
            }
        }
        return targetLayout;
    }

    private mezz.jei.api.gui.IRecipeLayoutDrawable extractLayoutDrawable(Object lwb) {
        try {
            if (recipeLayoutMethod == null) {
                recipeLayoutMethod = lwb.getClass().getMethod("recipeLayout");
            }
            Object res = recipeLayoutMethod.invoke(lwb);
            return res instanceof mezz.jei.api.gui.IRecipeLayoutDrawable layout ? layout : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void applyConvertedNodeToBoard(RecipeNode node, String defaultCategoryName) {
        Minecraft mc = Minecraft.getInstance();
        double[] pos = com.gtceu.calcboard.client.gui.BoardScreen.getNextNodeCenterPosition();
        node.setPosX(pos[0]);
        node.setPosY(pos[1]);
        com.gtceu.calcboard.api.storage.BoardManager.getInstance().getActiveGraph().addNode(node);

        String name = node.getName();
        if (name == null || name.isEmpty()) {
            name = defaultCategoryName;
        }
        com.gtceu.calcboard.client.gui.widget.BoardToast.show(
            Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.recipe_added", name))
        );
        mc.getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F
            )
        );
        if (mc.screen instanceof com.gtceu.calcboard.client.gui.BoardScreen boardScreen) {
            boardScreen.rebuildWidgets();
            boardScreen.markSummaryDirty();
        }
    }

    @Override
    public boolean isViewerScreen(net.minecraft.client.gui.screens.Screen screen) {
        if (screen == null || RECIPES_GUI_CLASS == null) return false;
        return RECIPES_GUI_CLASS.isInstance(screen);
    }
}



