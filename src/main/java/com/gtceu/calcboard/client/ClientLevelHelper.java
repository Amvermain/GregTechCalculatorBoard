package com.gtceu.calcboard.client;

import com.gtceu.calcboard.integration.spi.RecipeViewerRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.List;
import java.util.function.Consumer;

/**
 * Client-only helper for accessing client level, recipe manager, and language manager safely.
 */
public final class ClientLevelHelper implements com.gtceu.calcboard.api.catalog.ILevelRecipeProvider {

    public static final ClientLevelHelper INSTANCE = new ClientLevelHelper();

    private ClientLevelHelper() {}

    @Override
    public boolean isRecipeBakingComplete() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return false;
        return com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter().isRecipeBakingComplete();
    }

    @Override
    public void collectClientRecipes(Consumer<ItemStack> collector) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            try {
                RecipeManager recipeManager = mc.level.getRecipeManager();
                if (recipeManager != null) {
                    RegistryAccess access = mc.level.registryAccess();
                    List<ItemStack> temp = new java.util.ArrayList<>();
                    for (Recipe<?> r : recipeManager.getRecipes()) {
                        try {
                            temp.clear();
                            com.gtceu.calcboard.api.catalog.DynamicAddonCrawler.extractRecipeOutputs(r, temp);
                            if (temp.isEmpty()) {
                                ItemStack res = r.getResultItem(access);
                                if (res != null && !res.isEmpty()) {
                                    temp.add(res);
                                }
                            }
                            for (ItemStack is : temp) {
                                if (is != null && !is.isEmpty()) {
                                    collector.accept(is);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public ItemStack getRecipeResultItem(Recipe<?> r) {
        if (r == null) return ItemStack.EMPTY;
        Minecraft mc = Minecraft.getInstance();
        try {
            return mc != null && mc.level != null ? r.getResultItem(mc.level.registryAccess()) : r.getResultItem(RegistryAccess.EMPTY);
        } catch (Throwable ignored) {
            try {
                return r.getResultItem(RegistryAccess.EMPTY);
            } catch (Throwable ignored2) {
                return ItemStack.EMPTY;
            }
        }
    }

    @Override
    public String getSelectedLanguage() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getLanguageManager() != null) {
            return mc.getLanguageManager().getSelected();
        }
        return null;
    }
}

