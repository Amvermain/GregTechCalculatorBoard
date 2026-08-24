package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.IngredientStack;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Client-side visual renderer for IngredientStack components.
 * Isolates Minecraft GuiGraphics and EMI rendering from domain models.
 */
public final class IngredientRenderer {

    private IngredientRenderer() {}

    /**
     * Converts an IngredientStack to an EMI stack for lookup or rendering.
     */
    public static EmiStack toEmiStack(IngredientStack ingredient) {
        if (ingredient == null || ingredient.getId() == null) return EmiStack.EMPTY;
        ResourceLocation id = ingredient.getId();
        if (ingredient.isFluid()) {
            var fluid = ForgeRegistries.FLUIDS.getValue(id);
            if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
                // Try namespace fallbacks
                String path = id.getPath();
                for (String ns : new String[]{"gtceu", "start_core", "gtceu_start"}) {
                    var alt = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(ns, path));
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

    /**
     * Renders the item or fluid icon onto the GuiGraphics canvas.
     */
    public static void render(GuiGraphics graphics, IngredientStack stack, int x, int y) {
        if (stack == null) return;
        EmiStack emiStack = toEmiStack(stack);
        if (!emiStack.isEmpty()) {
            emiStack.render(graphics, x, y, 0, EmiIngredient.RENDER_ICON);
            RenderSystem.disableDepthTest();
        } else {
            graphics.fill(x, y, x + 16, y + 16, stack.isFluid() ? 0xFF3366CC : 0xFF888888);
        }
    }
}
