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

    private static final java.util.Map<net.minecraft.world.item.Item, ItemStack> ITEM_STACK_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<net.minecraft.world.level.material.Fluid, net.minecraftforge.fluids.FluidStack> FLUID_STACK_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

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
            if (!renderFluid(graphics, id, x, y)) {
                graphics.fill(x, y, x + 16, y + 16, 0xFF3366CC);
            }
        } else {
            var item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != Items.AIR) {
                ItemStack itemStack = ITEM_STACK_CACHE.computeIfAbsent(item, ItemStack::new);
                graphics.renderItem(itemStack, x, y);
            } else {
                graphics.fill(x, y, x + 16, y + 16, 0xFF888888);
            }
        }
    }

    private static boolean renderFluid(GuiGraphics graphics, ResourceLocation id, int x, int y) {
        try {
            var fluid = ForgeRegistries.FLUIDS.getValue(id);
            if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                var ext = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid);
                net.minecraftforge.fluids.FluidStack fs = FLUID_STACK_CACHE.computeIfAbsent(fluid, f -> new net.minecraftforge.fluids.FluidStack(f, 1000));
                ResourceLocation stillTexture = ext.getStillTexture(fs);
                if (stillTexture == null) {
                    stillTexture = ext.getStillTexture();
                }
                if (stillTexture != null) {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    var atlas = mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
                    var sprite = atlas.apply(stillTexture);
                    if (sprite != null) {
                        int tint = ext.getTintColor(fs);
                        if (tint == 0xFFFFFFFF || tint == 0) {
                            int baseTint = ext.getTintColor();
                            if (baseTint != 0) {
                                tint = baseTint;
                            }
                        }
                        float a = ((tint >> 24) & 0xFF) / 255.0f;
                        if (a <= 0.0f) a = 1.0f;
                        float r = ((tint >> 16) & 0xFF) / 255.0f;
                        float g = ((tint >> 8) & 0xFF) / 255.0f;
                        float b = (tint & 0xFF) / 255.0f;

                        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
                        RenderSystem.setShaderTexture(0, net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
                        RenderSystem.enableBlend();
                        RenderSystem.defaultBlendFunc();

                        graphics.blit(x, y, 0, 16, 16, sprite, r, g, b, a);

                        RenderSystem.disableBlend();
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static class EmiRenderHelper {
        private static boolean renderEmi(GuiGraphics graphics, IngredientStack stack, int x, int y) {
            dev.emi.emi.api.stack.EmiStack emiStack = com.gtceu.calcboard.integration.emi.EmiStackHelper.toEmiStack(stack);
            if (!emiStack.isEmpty()) {
                emiStack.render(graphics, x, y, 0, dev.emi.emi.api.stack.EmiIngredient.RENDER_ICON);
                return true;
            }
            return false;
        }
    }
}


