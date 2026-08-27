package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Client-side visual renderer for IngredientStack components.
 * Isolates Minecraft GuiGraphics and EMI rendering from domain models.
 */
public final class IngredientRenderer {

    private IngredientRenderer() {}

    /**
     * Renders the item or fluid icon onto the GuiGraphics canvas.
     */
    public static void render(GuiGraphics graphics, IngredientStack stack, int x, int y) {
        if (stack == null) return;
        if (ModCompatHelper.isEmiLoaded()) {
            try {
                if (EmiRenderHelper.renderEmi(graphics, stack, x, y)) {
                    return;
                }
            } catch (Throwable ignored) {}
        }
        renderVanilla(graphics, stack, x, y);
    }

    private static void renderVanilla(GuiGraphics graphics, IngredientStack stack, int x, int y) {
        if (stack == null || stack.getId() == null) {
            graphics.fill(x, y, x + 16, y + 16, 0xFF888888);
            return;
        }
        ResourceLocation id = stack.getId();
        if (stack.isFluid()) {
            graphics.fill(x, y, x + 16, y + 16, 0xFF3366CC);
        } else {
            var item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != Items.AIR) {
                graphics.renderItem(new ItemStack(item), x, y);
            } else {
                graphics.fill(x, y, x + 16, y + 16, 0xFF888888);
            }
        }
    }

    private static class EmiRenderHelper {
        private static boolean renderEmi(GuiGraphics graphics, IngredientStack stack, int x, int y) {
            dev.emi.emi.api.stack.EmiStack emiStack = com.gtceu.calcboard.integration.emi.EmiStackHelper.toEmiStack(stack);
            if (!emiStack.isEmpty()) {
                emiStack.render(graphics, x, y, 0, dev.emi.emi.api.stack.EmiIngredient.RENDER_ICON);
                RenderSystem.disableDepthTest();
                return true;
            }
            return false;
        }
    }
}


