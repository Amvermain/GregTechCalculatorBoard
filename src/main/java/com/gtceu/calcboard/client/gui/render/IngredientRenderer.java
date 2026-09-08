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

    public record CachedFluid(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, float r, float g, float b, float a) {}
    public record CachedItem(ItemStack itemStack, net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, float r, float g, float b, float a, boolean isFastSprite) {}

    private static final java.util.Map<net.minecraft.world.item.Item, ItemStack> ITEM_STACK_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<net.minecraft.world.level.material.Fluid, net.minecraftforge.fluids.FluidStack> FLUID_STACK_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<ResourceLocation, CachedFluid> FLUID_RENDER_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<ResourceLocation, CachedItem> ITEM_RENDER_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private IngredientRenderer() {}

    public static void clearCache() {
        FLUID_RENDER_CACHE.clear();
        ITEM_STACK_CACHE.clear();
        FLUID_STACK_CACHE.clear();
        ITEM_RENDER_CACHE.clear();
    }

    /**
     * Renders the item or fluid icon onto the GuiGraphics canvas.
     */
    public static void render(GuiGraphics graphics, IngredientStack stack, int x, int y) {
        if (stack == null) return;
        try {
            if (stack.isFluid()) {
                if (renderFluid(graphics, stack.getId(), x, y)) {
                    return;
                }
            } else {
                if (renderItem(graphics, stack.getId(), x, y)) {
                    return;
                }
            }
            if (ModCompatHelper.isEmiLoaded()) {
                try {
                    if (EmiRenderHelper.renderEmi(graphics, stack, x, y)) {
                        return;
                    }
                } catch (Throwable ignored) {}
            }
            renderFallback(graphics, stack, x, y);
        } catch (Throwable ignored) {}
    }

    public static boolean renderItem(GuiGraphics graphics, ResourceLocation id, int x, int y) {
        if (id == null) return false;
        CachedItem cached = ITEM_RENDER_CACHE.computeIfAbsent(id, IngredientRenderer::computeItem);
        if (cached == null) return false;
        if (cached.isFastSprite() && cached.sprite() != null) {
            graphics.blit(x, y, 0, 16, 16, cached.sprite(), cached.r(), cached.g(), cached.b(), cached.a());
            return true;
        }
        graphics.renderItem(cached.itemStack(), x, y);
        return true;
    }

    public static boolean renderItemStack(GuiGraphics graphics, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.hasTag()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null) {
                return renderItem(graphics, id, x, y);
            }
        }
        graphics.renderItem(stack, x, y);
        return true;
    }

    private static CachedItem computeItem(ResourceLocation id) {
        try {
            var item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) return null;
            ItemStack stack = new ItemStack(item);
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.getItemRenderer() == null) {
                return new CachedItem(stack, null, 1.0f, 1.0f, 1.0f, 1.0f, false);
            }
            var itemRenderer = mc.getItemRenderer();
            var model = itemRenderer.getModel(stack, null, null, 0);
            if (model == null || model.isGui3d() || model.isCustomRenderer()) {
                return new CachedItem(stack, null, 1.0f, 1.0f, 1.0f, 1.0f, false);
            }
            var sprite = model.getParticleIcon();
            if (sprite == null) {
                return new CachedItem(stack, null, 1.0f, 1.0f, 1.0f, 1.0f, false);
            }
            var quads = model.getQuads(null, null, net.minecraft.util.RandomSource.create(42));
            if (hasMultipleTintLayers(quads)) {
                return new CachedItem(stack, null, 1.0f, 1.0f, 1.0f, 1.0f, false);
            }
            int tint = -1;
            if (mc.getItemColors() != null) {
                tint = mc.getItemColors().getColor(stack, 0);
            }
            float r = 1.0f, g = 1.0f, b = 1.0f, a = 1.0f;
            if (tint != -1) {
                a = Math.max(0.01f, ((tint >> 24) & 0xFF) / 255.0f);
                r = ((tint >> 16) & 0xFF) / 255.0f;
                g = ((tint >> 8) & 0xFF) / 255.0f;
                b = (tint & 0xFF) / 255.0f;
            }
            return new CachedItem(stack, sprite, r, g, b, a, true);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean hasMultipleTintLayers(java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads) {
        if (quads == null || quads.isEmpty()) return false;
        for (var quad : quads) {
            if (quad.isTinted() && quad.getTintIndex() > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean renderFluid(GuiGraphics graphics, ResourceLocation id, int x, int y) {
        if (id == null) return false;
        CachedFluid cached = FLUID_RENDER_CACHE.computeIfAbsent(id, IngredientRenderer::computeFluid);
        if (cached != null && cached.sprite() != null) {
            graphics.blit(x, y, 0, 16, 16, cached.sprite(), cached.r(), cached.g(), cached.b(), cached.a());
            return true;
        }
        return false;
    }

    private static net.minecraft.world.level.material.Fluid resolveFluid(ResourceLocation id) {
        var fluid = ForgeRegistries.FLUIDS.getValue(id);
        if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
            return fluid;
        }
        String path = id.getPath();
        for (String ns : new String[]{"gtceu", "start_core", "gtceu_start"}) {
            var alt = ForgeRegistries.FLUIDS.getValue(ResourceLocation.tryParse(ns + ":" + path));
            if (alt != null && alt != net.minecraft.world.level.material.Fluids.EMPTY) {
                return alt;
            }
        }
        return null;
    }

    private static CachedFluid computeFluid(ResourceLocation id) {
        try {
            var fluid = resolveFluid(id);
            if (fluid == null) return null;

            var ext = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid);
            net.minecraftforge.fluids.FluidStack fs = FLUID_STACK_CACHE.computeIfAbsent(fluid, f -> new net.minecraftforge.fluids.FluidStack(f, 1000));
            ResourceLocation stillTexture = ext.getStillTexture(fs);
            if (stillTexture == null) {
                stillTexture = ext.getStillTexture();
            }
            if (stillTexture == null) return null;

            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.getModelManager() == null) return null;

            var atlas = mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
            var sprite = atlas.apply(stillTexture);
            if (sprite == null) return null;

            int tint = ext.getTintColor(fs);
            if (tint == 0xFFFFFFFF || tint == 0) {
                int baseTint = ext.getTintColor();
                tint = (baseTint != 0) ? baseTint : tint;
            }
            float a = Math.max(0.01f, ((tint >> 24) & 0xFF) / 255.0f);
            float r = ((tint >> 16) & 0xFF) / 255.0f;
            float g = ((tint >> 8) & 0xFF) / 255.0f;
            float b = (tint & 0xFF) / 255.0f;
            return new CachedFluid(sprite, r, g, b, a);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void renderFallback(GuiGraphics graphics, IngredientStack stack, int x, int y) {
        if (stack != null && stack.isFluid()) {
            graphics.fill(x, y, x + 16, y + 16, 0xFF3366CC);
        } else {
            graphics.fill(x, y, x + 16, y + 16, 0xFF888888);
        }
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


