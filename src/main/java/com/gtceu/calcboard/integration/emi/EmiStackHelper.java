package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.model.IngredientStack;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public final class EmiStackHelper {

    private EmiStackHelper() {}

    public static EmiStack toEmiStack(IngredientStack ingredient) {
        if (ingredient == null || ingredient.getId() == null) return EmiStack.EMPTY;
        ResourceLocation id = ingredient.getId();
        if (ingredient.isFluid()) {
            var fluid = ForgeRegistries.FLUIDS.getValue(id);
            if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
                String path = id.getPath();
                for (String ns : new String[]{"gtceu", "start_core", "gtceu_start"}) {
                    var alt = ForgeRegistries.FLUIDS.getValue(ResourceLocation.tryParse(ns + ":" + path));
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
            if (ingredient.isStressUnit()) {
                var cogItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("create:cogwheel"));
                if (cogItem != null && cogItem != Items.AIR) {
                    return EmiStack.of(cogItem, 1);
                }
            }
            var item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != Items.AIR) {
                return EmiStack.of(item, Math.max(1, (long) Math.round(ingredient.getAmount())));
            }
        }
        return EmiStack.EMPTY;
    }
}

