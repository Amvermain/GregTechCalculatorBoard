package com.gtceu.calcboard.integration.jei;

import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.util.Locale;

public record JeiRecipeWrapper<T>(
        IRecipeCategory<T> category,
        T recipe
) {
    public ResourceLocation getCategoryId() {
        if (category == null || category.getRecipeType() == null) return null;
        return category.getRecipeType().getUid();
    }

    public Component getCategoryTitle() {
        return category != null ? category.getTitle() : Component.empty();
    }

    public ResourceLocation getRecipeId() {
        if (category != null && recipe != null) {
            try {
                ResourceLocation rName = category.getRegistryName(recipe);
                if (rName != null) return rName;
            } catch (Throwable ignored) {}
        }
        if (recipe instanceof Recipe<?> r) {
            return r.getId();
        }
        if (recipe != null) {
            try {
                var m = recipe.getClass().getMethod("getId");
                Object res = m.invoke(recipe);
                if (res instanceof ResourceLocation rl) return rl;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public String getModId() {
        ResourceLocation rId = getRecipeId();
        if (rId != null) return rId.getNamespace().toLowerCase(Locale.ROOT);
        ResourceLocation cId = getCategoryId();
        if (cId != null) return cId.getNamespace().toLowerCase(Locale.ROOT);
        return "minecraft";
    }
}
