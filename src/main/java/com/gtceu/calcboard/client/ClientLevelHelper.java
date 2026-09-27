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
        if (mc == null || mc.level == null) return;
        RecipeManager recipeManager = mc.level.getRecipeManager();
        if (recipeManager == null) return;

        RegistryAccess access = mc.level.registryAccess();
        for (Recipe<?> r : recipeManager.getRecipes()) {
            collectRecipeOutputs(r, access, collector);
        }
    }

    private void collectRecipeOutputs(Recipe<?> r, RegistryAccess access, Consumer<ItemStack> collector) {
        try {
            List<ItemStack> temp = new java.util.ArrayList<>();
            com.gtceu.calcboard.api.catalog.DynamicAddonCrawler.extractRecipeOutputs(r, temp);
            if (temp.isEmpty()) {
                addFallbackRecipeResult(r, access, temp);
            }
            for (ItemStack is : temp) {
                acceptIfValid(is, collector);
            }
        } catch (Throwable ignored) {}
    }

    private void addFallbackRecipeResult(Recipe<?> r, RegistryAccess access, List<ItemStack> temp) {
        ItemStack res = r.getResultItem(access);
        if (res != null && !res.isEmpty()) {
            temp.add(res);
        }
    }

    private void acceptIfValid(ItemStack is, Consumer<ItemStack> collector) {
        if (is != null && !is.isEmpty()) {
            collector.accept(is);
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

    @Override
    public RegistryAccess getRegistryAccess() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            return mc.level.registryAccess();
        }
        return null;
    }
}

