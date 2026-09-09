package com.gtceu.calcboard.integration.jei;

import com.gtceu.calcboard.api.spi.viewer.IRecipeViewerBridge;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IRecipeCatalystLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.BiConsumer;

/**
 * Concrete JEI recipe viewer bridge providing headless-safe access to JEI recipe models.
 */
public final class JeiRecipeViewerBridge implements IRecipeViewerBridge {

    private static final JeiRecipeViewerBridge INSTANCE = new JeiRecipeViewerBridge();

    private JeiRecipeViewerBridge() {}

    public static JeiRecipeViewerBridge getInstance() {
        return INSTANCE;
    }

    @Override
    public String getViewerId() {
        return "jei";
    }

    @Override
    public boolean isAvailable() {
        return ModCompatHelper.isJeiLoaded();
    }

    @Override
    public boolean isRecipeBakingComplete() {
        return JeiRecipeViewerAdapter.getJeiRuntime() != null;
    }

    @Override
    public void discoverCategoryWorkstations(BiConsumer<ResourceLocation, ResourceLocation> workstationConsumer) {
        if (!isAvailable() || workstationConsumer == null) return;
        Object runtimeObj = JeiRecipeViewerAdapter.getJeiRuntime();
        if (!(runtimeObj instanceof IJeiRuntime runtime)) return;

        try {
            IRecipeManager recipeManager = runtime.getRecipeManager();
            var categoryLookup = recipeManager.createRecipeCategoryLookup();
            if (categoryLookup == null) return;

            for (IRecipeCategory<?> cat : categoryLookup.get().toList()) {
                if (cat == null || cat.getRecipeType() == null) continue;
                ResourceLocation catId = cat.getRecipeType().getUid();
                IRecipeCatalystLookup catalystLookup = recipeManager.createRecipeCatalystLookup(cat.getRecipeType());
                if (catalystLookup == null) continue;

                for (ITypedIngredient<?> typedIng : catalystLookup.get().toList()) {
                    if (typedIng == null) continue;
                    ItemStack is = typedIng.getItemStack().orElse(ItemStack.EMPTY);
                    if (is.isEmpty() && typedIng.getIngredient() instanceof ItemStack s) {
                        is = s;
                    }
                    if (is.isEmpty()) continue;
                    ResourceLocation ws = ForgeRegistries.ITEMS.getKey(is.getItem());
                    if (ws != null) {
                        workstationConsumer.accept(catId, ws);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
}
