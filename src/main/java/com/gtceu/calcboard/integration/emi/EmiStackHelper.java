package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.model.IngredientStack;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

public final class EmiStackHelper {

    private EmiStackHelper() {}

    public static EmiStack toEmiStack(IngredientStack ingredient) {
        if (ingredient == null || ingredient.getId() == null) return EmiStack.EMPTY;
        ResourceLocation id = ingredient.getId();
        if (ingredient.isFluid()) {
            var fluid = BuiltInRegistries.FLUID.get(id);
            if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
                String path = id.getPath();
                for (String ns : new String[]{"gtceu", "start_core", "gtceu_start"}) {
                    var alt = BuiltInRegistries.FLUID.get(ResourceLocation.tryParse(ns + ":" + path));
                    if (alt != null && alt != net.minecraft.world.level.material.Fluids.EMPTY) {
                        fluid = alt;
                        break;
                    }
                }
            }
            if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                return EmiStack.of(fluid, Math.max(1, (long) ingredient.getAmount()));
            }
        } else {
            try {
                if (ingredient.isStressUnit()) {
                    var cogItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("create:cogwheel"));
                    if (cogItem != null && cogItem != Items.AIR) {
                        return EmiStack.of(cogItem, 1);
                    }
                }
                var item = BuiltInRegistries.ITEM.get(id);
                if (item != null && item != Items.AIR) {
                    return EmiStack.of(item, Math.max(1, (long) Math.round(ingredient.getAmount())));
                }
            } catch (Throwable ignored) {}
        }
        return EmiStack.EMPTY;
    }
}

