package com.gtceu.calcboard.client.web;

import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Off-screen framebuffer renderer that renders item, fluid, and machine textures
 * into 32x32 PNG byte streams for the web dashboard.
 */
public final class MicroIconRenderer {

    private MicroIconRenderer() {}

    public static boolean isClientRenderAvailable() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return false;
            }
            if (mc.level == null) {
                return false;
            }
            if (mc.getItemRenderer() == null) {
                return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static byte[] renderItem(String itemId, String nbt) {
        if (!isClientRenderAvailable()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (RenderSystem.isOnRenderThread()) {
            return renderItemDirect(mc, itemId, nbt);
        }
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                future.complete(renderItemDirect(mc, itemId, nbt));
            } catch (Throwable t) {
                com.gtceu.calcboard.GregTechCalcBoard.LOGGER.error("[IconRenderer] mc.execute failed for item: " + itemId, t);
                future.complete(null);
            }
        });
        try {
            return future.get(5000, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.error("[IconRenderer] future.get timed out or failed for item: " + itemId, t);
            return null;
        }
    }

    public static byte[] renderFluid(String fluidId, Integer tint) {
        if (!isClientRenderAvailable()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (RenderSystem.isOnRenderThread()) {
            return renderFluidDirect(mc, fluidId, tint);
        }
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                future.complete(renderFluidDirect(mc, fluidId, tint));
            } catch (Throwable t) {
                future.complete(null);
            }
        });
        try {
            return future.get(5000, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            return null;
        }
    }

    private static TextureTarget sharedTarget;

    private static TextureTarget getOrCreateSharedTarget() {
        if (sharedTarget == null) {
            sharedTarget = new TextureTarget(32, 32, true, Minecraft.ON_OSX);
        }
        return sharedTarget;
    }

    public static void releaseSharedTarget() {
        if (RenderSystem.isOnRenderThread()) {
            destroySharedTargetNow();
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(MicroIconRenderer::destroySharedTargetNow);
            }
        }
    }

    private static void destroySharedTargetNow() {
        if (sharedTarget != null) {
            sharedTarget.destroyBuffers();
            sharedTarget = null;
        }
    }

    public static NativeImage renderItemImageDirect(Minecraft mc, String itemId, String nbt) {
        try {
            ItemStack stack = resolveItemStack(itemId, nbt);
            if (stack == null || stack.isEmpty()) {
                com.gtceu.calcboard.GregTechCalcBoard.LOGGER.warn("[IconRenderer] resolveItemStack returned empty/null for: " + itemId);
                return null;
            }
            return renderItemImageInternal(mc, itemId, stack);
        } catch (Throwable t) {
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.error("[IconRenderer] renderItemImageDirect exception for: " + itemId, t);
            return null;
        }
    }

    private static NativeImage renderItemImageInternal(Minecraft mc, String itemId, ItemStack stack) {

        int[] viewport = new int[4];
        org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, viewport);
        int drawTarget = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readTarget = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        boolean blend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        int srcRgb = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB);
        int dstRgb = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int srcAlpha = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        boolean scissor = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        int[] scissorBox = new int[4];
        if (scissor) {
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, scissorBox);
        }
        boolean depth = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        int depthFunction = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_DEPTH_FUNC);
        boolean depthWrite = org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();

        RenderSystem.disableScissor();

        TextureTarget target = getOrCreateSharedTarget();
        target.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        GuiGraphics graphics = null;
        modelView.pushPose();
        try {
            RenderSystem.viewport(0, 0, 32, 32);
            modelView.setIdentity();
            modelView.translate(0, 0, -11000);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 32, 32, 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems();

            graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            graphics.pose().scale(2.0f, 2.0f, 1.0f);
            IngredientRenderer.renderItemStack(graphics, stack, 0, 0);
            graphics.flush();

            return net.minecraft.client.Screenshot.takeScreenshot(target);
        } catch (Throwable t) {
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.error("MicroIconRenderer failed to render item: " + itemId, t);
            return null;
        } finally {
            try {
                if (graphics != null) {
                    graphics.flush();
                }
            } finally {
                com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
                com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, readTarget);
                RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                modelView.popPose();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(projection, sorting);
                RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
                RenderSystem.depthFunc(depthFunction);
                RenderSystem.depthMask(depthWrite);
                RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
                if (blend) {
                    RenderSystem.enableBlend();
                } else {
                    RenderSystem.disableBlend();
                }
                if (depth) {
                    RenderSystem.enableDepthTest();
                } else {
                    RenderSystem.disableDepthTest();
                }
                if (scissor) {
                    RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
                } else {
                    RenderSystem.disableScissor();
                }
                com.mojang.blaze3d.platform.Lighting.setupForFlatItems();
            }
        }
    }

    public static NativeImage renderFluidImageDirect(Minecraft mc, String fluidId, Integer tint) {
        if (fluidId == null || fluidId.isBlank()) return null;
        ResourceLocation id = ResourceLocation.tryParse(fluidId);
        if (id == null) return null;

        int[] viewport = new int[4];
        org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, viewport);
        int drawTarget = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readTarget = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        boolean blend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        int srcRgb = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB);
        int dstRgb = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int srcAlpha = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        boolean scissor = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        int[] scissorBox = new int[4];
        if (scissor) {
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, scissorBox);
        }
        boolean depth = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        int depthFunction = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_DEPTH_FUNC);
        boolean depthWrite = org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();

        RenderSystem.disableScissor();

        TextureTarget target = getOrCreateSharedTarget();
        target.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        GuiGraphics graphics = null;
        modelView.pushPose();
        try {
            RenderSystem.viewport(0, 0, 32, 32);
            modelView.setIdentity();
            modelView.translate(0, 0, -11000);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 32, 32, 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);

            graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            graphics.pose().scale(2.0f, 2.0f, 1.0f);
            boolean rendered = IngredientRenderer.renderFluid(graphics, id, 0, 0);
            graphics.flush();

            if (rendered) {
                return net.minecraft.client.Screenshot.takeScreenshot(target);
            }
            return null;
        } catch (Throwable t) {
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.error("MicroIconRenderer failed to render fluid: " + fluidId, t);
            return null;
        } finally {
            try {
                if (graphics != null) {
                    graphics.flush();
                }
            } finally {
                com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
                com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, readTarget);
                RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                modelView.popPose();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(projection, sorting);
                RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
                RenderSystem.depthFunc(depthFunction);
                RenderSystem.depthMask(depthWrite);
                RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
                if (blend) {
                    RenderSystem.enableBlend();
                } else {
                    RenderSystem.disableBlend();
                }
                if (depth) {
                    RenderSystem.enableDepthTest();
                } else {
                    RenderSystem.disableDepthTest();
                }
                if (scissor) {
                    RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
                } else {
                    RenderSystem.disableScissor();
                }
            }
        }
    }

    private static byte[] renderItemDirect(Minecraft mc, String itemId, String nbt) {
        NativeImage image = renderItemImageDirect(mc, itemId, nbt);
        if (image == null) {
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.warn("[IconRenderer] renderItemDirect: image is null for: " + itemId);
            return null;
        }
        try (image) {
            byte[] bytes = image.asByteArray();
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info("[IconRenderer] Successfully rendered item: " + itemId + " (" + bytes.length + " bytes)");
            return bytes;
        } catch (Throwable t) {
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.error("[IconRenderer] image.asByteArray failed for item: " + itemId, t);
            return null;
        }
    }

    private static byte[] renderFluidDirect(Minecraft mc, String fluidId, Integer tint) {
        NativeImage image = renderFluidImageDirect(mc, fluidId, tint);
        if (image == null) {
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.warn("[IconRenderer] renderFluidDirect: image is null for: " + fluidId);
            return null;
        }
        try (image) {
            byte[] bytes = image.asByteArray();
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info("[IconRenderer] Successfully rendered fluid: " + fluidId + " (" + bytes.length + " bytes)");
            return bytes;
        } catch (Throwable t) {
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.error("[IconRenderer] image.asByteArray failed for fluid: " + fluidId, t);
            return null;
        }
    }

    private static ItemStack resolveItemStack(String itemId, String nbt) {
        if (itemId == null || itemId.isBlank()) return null;
        if ("create:stress_units".equals(itemId) || "create:stress".equals(itemId)) {
            var cogItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("create:cogwheel"));
            if (cogItem != null && cogItem != Items.AIR) {
                return new ItemStack(cogItem);
            }
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return null;
        var item = ForgeRegistries.ITEMS.getValue(id);
        if ((item == null || item == Items.AIR) && ForgeRegistries.BLOCKS != null) {
            var block = ForgeRegistries.BLOCKS.getValue(id);
            if (block != null && block.asItem() != Items.AIR) {
                item = block.asItem();
            }
        }
        if (item == null || item == Items.AIR) return null;
        ItemStack stack = new ItemStack(item);
        if (nbt != null && !nbt.isEmpty()) {
            try {
                stack.setTag(net.minecraft.nbt.TagParser.parseTag(nbt));
            } catch (Throwable ignored) {}
        }
        return stack;
    }
}
