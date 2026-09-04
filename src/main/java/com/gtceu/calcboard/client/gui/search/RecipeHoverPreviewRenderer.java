package com.gtceu.calcboard.client.gui.search;

import com.gtceu.calcboard.api.storage.BoardManager;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;

import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * High-performance hover preview renderer with LRU caching for EMI recipe widgets & fallback custom cards.
 * Safely isolated from EMI bytecode to prevent NoClassDefFoundError when EMI is absent.
 */
public class RecipeHoverPreviewRenderer {

    public static int[] calculatePreviewBounds(
            SearchableRecipe sr,
            int dialogX,
            int dialogY,
            int dialogW,
            int dialogH,
            int hoveredRowY,
            int screenW,
            int screenH
    ) {
        if (sr == null || sr.recipe() == null) return null;

        int contentW = 140;
        int contentH = 80;

        if (ModCompatHelper.isEmiLoaded() && EmiPreviewRendererImpl.isEmiRecipe(sr.recipe())) {
            int[] dim = EmiPreviewRendererImpl.getEmiRecipeDimensions(sr.recipe());
            if (dim != null) {
                contentW = dim[0];
                contentH = dim[1];
            }
        } else if (sr.recipe() instanceof RecipeNode rn) {
            int inCount = rn.getInputs().size();
            int outCount = rn.getOutputs().size();
            int maxRows = Math.max(1, Math.max(inCount, outCount));
            contentW = (inCount == 0 || outCount == 0) ? 140 : 190;
            contentH = maxRows * 18 + 4;
        } else if (sr.recipe() instanceof com.gtceu.calcboard.integration.jei.JeiRecipeWrapper<?> jrw) {
            RecipeNode rn = com.gtceu.calcboard.integration.jei.JeiRecipeConverter.convert(jrw);
            if (rn != null) {
                int inCount = rn.getInputs().size();
                int outCount = rn.getOutputs().size();
                int maxRows = Math.max(1, Math.max(inCount, outCount));
                contentW = (inCount == 0 || outCount == 0) ? 140 : 190;
                contentH = maxRows * 18 + 4;
            }
        }

        int extraWarningH = (sr != null && !sr.isSupported()) ? 24 : 0;
        int cardW = Math.max(140, contentW + 16);
        int cardH = contentH + 34 + extraWarningH;

        int rightSpace = screenW - (dialogX + dialogW + 6);
        int leftSpace = dialogX - 6;

        int cardX;
        if (rightSpace >= cardW) {
            cardX = dialogX + dialogW + 6;
        } else if (leftSpace >= cardW) {
            cardX = dialogX - cardW - 6;
        } else {
            if (rightSpace >= leftSpace) {
                cardX = Math.min(screenW - cardW - 4, dialogX + dialogW + 6);
            } else {
                cardX = Math.max(4, dialogX - cardW - 6);
            }
        }

        int cardY = Math.max(4, Math.min(hoveredRowY - 10, screenH - cardH - 4));
        return new int[]{cardX, cardY, cardW, cardH};
    }

    public static Object getHoveredIngredient(
            SearchableRecipe sr,
            int dialogX,
            int dialogY,
            int dialogW,
            int dialogH,
            int hoveredRowY,
            int mouseX,
            int mouseY,
            int screenW,
            int screenH
    ) {
        if (sr == null || sr.recipe() == null) return null;
        if (ModCompatHelper.isEmiLoaded() && EmiPreviewRendererImpl.isEmiRecipe(sr.recipe())) {
            int[] bounds = calculatePreviewBounds(sr, dialogX, dialogY, dialogW, dialogH, hoveredRowY, screenW, screenH);
            if (bounds == null) return null;
            int originX = bounds[0] + 8;
            int originY = bounds[1] + 22;
            return EmiPreviewRendererImpl.getHoveredIngredientFromCache(sr.recipe(), originX, originY, mouseX, mouseY);
        }

        RecipeNode rn = null;
        if (sr.recipe() instanceof RecipeNode node) {
            rn = node;
        } else if (sr.recipe() instanceof com.gtceu.calcboard.integration.jei.JeiRecipeWrapper<?> wrapper) {
            rn = com.gtceu.calcboard.integration.jei.JeiRecipeConverter.convert(wrapper);
        }

        if (rn != null) {
            return findHoveredIngredientInNode(sr, rn, dialogX, dialogY, dialogW, dialogH, hoveredRowY, mouseX, mouseY, screenW, screenH);
        }

        return null;
    }

    private static IngredientStack findHoveredIngredientInNode(
            SearchableRecipe sr,
            RecipeNode rn,
            int dialogX,
            int dialogY,
            int dialogW,
            int dialogH,
            int hoveredRowY,
            int mouseX,
            int mouseY,
            int screenW,
            int screenH
    ) {
        int[] bounds = calculatePreviewBounds(sr, dialogX, dialogY, dialogW, dialogH, hoveredRowY, screenW, screenH);
        if (bounds == null) return null;
        int cardX = bounds[0];
        int cardY = bounds[1];
        int cardW = bounds[2];

        int inCount = rn.getInputs().size();
        int outCount = rn.getOutputs().size();
        int maxRows = Math.max(1, Math.max(inCount, outCount));
        int contentY = cardY + 22;

        if (inCount == 0 && outCount > 0) {
            for (int i = 0; i < outCount; i++) {
                int rowY = contentY + i * 18;
                int slotX = cardX + 10;
                if (mouseX >= slotX && mouseX <= slotX + 16 && mouseY >= rowY && mouseY <= rowY + 16) {
                    return rn.getOutputs().get(i);
                }
            }
        } else if (outCount == 0 && inCount > 0) {
            for (int i = 0; i < inCount; i++) {
                int rowY = contentY + i * 18;
                int slotX = cardX + 10;
                if (mouseX >= slotX && mouseX <= slotX + 16 && mouseY >= rowY && mouseY <= rowY + 16) {
                    return rn.getInputs().get(i);
                }
            }
        } else {
            for (int i = 0; i < maxRows; i++) {
                int rowY = contentY + i * 18;
                if (i < inCount) {
                    int inSlotX = cardX + 8;
                    if (mouseX >= inSlotX && mouseX <= inSlotX + 16 && mouseY >= rowY && mouseY <= rowY + 16) {
                        return rn.getInputs().get(i);
                    }
                }
                int arrowX = cardX + (cardW / 2) - 4;
                if (i < outCount) {
                    int outStackX = arrowX + 12;
                    if (mouseX >= outStackX && mouseX <= outStackX + 16 && mouseY >= rowY && mouseY <= rowY + 16) {
                        return rn.getOutputs().get(i);
                    }
                }
            }
        }
        return null;
    }

    public static void renderPreview(
            GuiGraphics graphics,
            SearchableRecipe sr,
            int dialogX,
            int dialogY,
            int dialogW,
            int dialogH,
            int hoveredRowY,
            int mouseX,
            int mouseY,
            float partialTick,
            int screenW,
            int screenH
    ) {
        if (sr == null || sr.recipe() == null) return;
        Font font = Minecraft.getInstance().font;

        if (ModCompatHelper.isEmiLoaded() && EmiPreviewRendererImpl.isEmiRecipe(sr.recipe())) {
            EmiPreviewRendererImpl.renderEmiRecipePreview(graphics, font, sr, sr.recipe(), dialogX, dialogY, dialogW, dialogH, hoveredRowY, mouseX, mouseY, partialTick, screenW, screenH);
        } else if (sr.recipe() instanceof RecipeNode rn) {
            renderRecipeNodePreview(graphics, font, sr, rn, dialogX, dialogY, dialogW, dialogH, hoveredRowY, mouseX, mouseY, screenW, screenH);
        } else if (sr.recipe() instanceof com.gtceu.calcboard.integration.jei.JeiRecipeWrapper<?> jrw) {
            RecipeNode rn = com.gtceu.calcboard.integration.jei.JeiRecipeConverter.convert(jrw);
            if (rn != null) {
                renderRecipeNodePreview(graphics, font, sr, rn, dialogX, dialogY, dialogW, dialogH, hoveredRowY, mouseX, mouseY, screenW, screenH);
            }
        }
    }

    public static int[] calculateEmiPreviewBounds(
            Object emiRecipeObj,
            int anchorX,
            int anchorY,
            int screenW,
            int screenH
    ) {
        if (!ModCompatHelper.isEmiLoaded() || emiRecipeObj == null) return null;
        return EmiPreviewRendererImpl.calculateEmiPreviewBounds(emiRecipeObj, anchorX, anchorY, screenW, screenH);
    }

    public static Object getEmiHoveredIngredient(
            Object emiRecipeObj,
            int anchorX,
            int anchorY,
            int mouseX,
            int mouseY,
            int screenW,
            int screenH
    ) {
        if (!ModCompatHelper.isEmiLoaded() || emiRecipeObj == null) return null;
        return EmiPreviewRendererImpl.getHoveredIngredientFromAnchor(emiRecipeObj, anchorX, anchorY, mouseX, mouseY, screenW, screenH);
    }

    public static void renderEmiPreviewDirect(
            GuiGraphics graphics,
            Object emiRecipeObj,
            int anchorX,
            int anchorY,
            int mouseX,
            int mouseY,
            float partialTick,
            int screenW,
            int screenH
    ) {
        if (!ModCompatHelper.isEmiLoaded() || emiRecipeObj == null) return;
        Font font = Minecraft.getInstance().font;
        EmiPreviewRendererImpl.renderEmiPreviewDirect(graphics, font, emiRecipeObj, anchorX, anchorY, mouseX, mouseY, partialTick, screenW, screenH);
    }

    public static void renderRecipeNodePreview(
            GuiGraphics graphics,
            Font font,
            SearchableRecipe sr,
            RecipeNode rn,
            int dialogX,
            int dialogY,
            int dialogW,
            int dialogH,
            int hoveredRowY,
            int mouseX,
            int mouseY,
            int screenW,
            int screenH
    ) {
        int[] bounds = calculatePreviewBounds(sr, dialogX, dialogY, dialogW, dialogH, hoveredRowY, screenW, screenH);
        if (bounds == null) return;
        int cardX = bounds[0];
        int cardY = bounds[1];
        int cardW = bounds[2];
        int cardH = bounds[3];

        int inCount = rn.getInputs().size();
        int outCount = rn.getOutputs().size();
        int maxRows = Math.max(1, Math.max(inCount, outCount));

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);

        // Background
        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xF00A0F1D);
        graphics.renderOutline(cardX, cardY, cardW, cardH, 0xFF38BDF8);

        // Header
        graphics.fill(cardX, cardY, cardX + cardW, cardY + 18, 0xFF1E293B);
        String catName = !sr.categoryName().isEmpty() ? sr.categoryName() : sr.categoryId();
        String title = (catName.isEmpty() ? "" : "§7[" + catName + "§7] ") + "§f" + rn.getName();
        graphics.drawString(font, font.plainSubstrByWidth(title, cardW - 12), cardX + 6, cardY + 5, 0xFFFFFFFF, false);

        // Content
        IngredientStack hoveredIngredient = null;
        int contentY = cardY + 22;
        if (inCount == 0 && outCount > 0) {
            for (int i = 0; i < outCount; i++) {
                int rowY = contentY + i * 18;
                IngredientStack out = rn.getOutputs().get(i);
                int slotX = cardX + 10;
                IngredientRenderer.render(graphics, out, slotX, rowY);
                if (mouseX >= slotX && mouseX <= slotX + 16 && mouseY >= rowY && mouseY <= rowY + 16) {
                    graphics.fill(slotX, rowY, slotX + 16, rowY + 16, 0x55FFFFFF);
                    hoveredIngredient = out;
                }
                String amtStr = out.isFluid() ? String.format("%.0f mB", out.getAmount()) : String.format("%.0f", out.getAmount());
                if (out.getChance() < 0.999) {
                    amtStr += String.format(" §d(%.0f%%)", out.getChance() * 100.0);
                }
                amtStr += "  §a(Output)";
                graphics.drawString(font, font.plainSubstrByWidth(amtStr, cardW - 36), cardX + 30, rowY + 5, 0xFFE2E8F0, false);
            }
        } else if (outCount == 0 && inCount > 0) {
            for (int i = 0; i < inCount; i++) {
                int rowY = contentY + i * 18;
                IngredientStack in = rn.getInputs().get(i);
                int slotX = cardX + 10;
                IngredientRenderer.render(graphics, in, slotX, rowY);
                if (mouseX >= slotX && mouseX <= slotX + 16 && mouseY >= rowY && mouseY <= rowY + 16) {
                    graphics.fill(slotX, rowY, slotX + 16, rowY + 16, 0x55FFFFFF);
                    hoveredIngredient = in;
                }
                String amtStr = in.isFluid() ? String.format("%.0f mB", in.getAmount()) : String.format("%.0f", in.getAmount());
                amtStr += "  §c(Input)";
                graphics.drawString(font, font.plainSubstrByWidth(amtStr, cardW - 36), cardX + 30, rowY + 5, 0xFFE2E8F0, false);
            }
        } else {
            for (int i = 0; i < maxRows; i++) {
                int rowY = contentY + i * 18;

                if (i < inCount) {
                    IngredientStack in = rn.getInputs().get(i);
                    int inSlotX = cardX + 8;
                    IngredientRenderer.render(graphics, in, inSlotX, rowY);
                    if (mouseX >= inSlotX && mouseX <= inSlotX + 16 && mouseY >= rowY && mouseY <= rowY + 16) {
                        graphics.fill(inSlotX, rowY, inSlotX + 16, rowY + 16, 0x55FFFFFF);
                        hoveredIngredient = in;
                    }
                    String amtStr = in.isFluid() ? String.format("%.0f mB", in.getAmount()) : String.format("%.0f", in.getAmount());
                    graphics.drawString(font, font.plainSubstrByWidth(amtStr, 60), cardX + 26, rowY + 5, 0xFFE2E8F0, false);
                }

                int arrowX = cardX + (cardW / 2) - 4;
                graphics.drawString(font, "➔", arrowX, rowY + 5, 0xFF64748B, false);

                if (i < outCount) {
                    IngredientStack out = rn.getOutputs().get(i);
                    int outStackX = arrowX + 12;
                    IngredientRenderer.render(graphics, out, outStackX, rowY);
                    if (mouseX >= outStackX && mouseX <= outStackX + 16 && mouseY >= rowY && mouseY <= rowY + 16) {
                        graphics.fill(outStackX, rowY, outStackX + 16, rowY + 16, 0x55FFFFFF);
                        hoveredIngredient = out;
                    }
                    String amtStr = out.isFluid() ? String.format("%.0f mB", out.getAmount()) : String.format("%.0f", out.getAmount());
                    if (out.getChance() < 0.999) {
                        amtStr += String.format(" §d(%.0f%%)", out.getChance() * 100.0);
                    }
                    graphics.drawString(font, font.plainSubstrByWidth(amtStr, cardW - (outStackX + 18) - 4), outStackX + 18, rowY + 5, 0xFFE2E8F0, false);
                }
            }
        }

        // Stats Footer (Duration & EU/t / RF/t / Heat)
        int footerY = cardY + cardH - 16 - ((sr != null && !sr.isSupported()) ? 24 : 0);
        graphics.fill(cardX, footerY - 2, cardX + cardW, footerY - 1, 0xFF1E293B);
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(rn);
        String statsStr = String.format("§b⏱ %.2fs  %s", rn.getBaseDurationTicks() / 20.0, adapter.formatEnergyStats(rn, com.gtceu.calcboard.api.storage.BoardManager.getInstance().getPowerDisplayMode()));
        graphics.drawString(font, font.plainSubstrByWidth(statsStr, cardW - 8), cardX + 6, footerY + 2, 0xFF94A3B8, false);

        if (sr != null && !sr.isSupported()) {
            int warnY = cardY + cardH - 24;
            graphics.fill(cardX + 2, warnY, cardX + cardW - 2, cardY + cardH - 2, 0xEE3D2C1C);
            graphics.renderOutline(cardX + 2, warnY, cardW - 4, 22, 0xFFFB923C);
            String wTitle = "§6⚠ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.search.preview.unsupported_title").getString();
            String wDesc = "§7" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.search.preview.unsupported_desc").getString();
            graphics.drawString(font, font.plainSubstrByWidth(wTitle, cardW - 12), cardX + 6, warnY + 3, 0xFFFB923C, false);
            graphics.drawString(font, font.plainSubstrByWidth(wDesc, cardW - 12), cardX + 6, warnY + 12, 0xFFAAAAAA, false);
        }

        graphics.pose().popPose();

        if (hoveredIngredient != null) {
            renderIngredientTooltip(graphics, font, hoveredIngredient, mouseX, mouseY, screenW, screenH);
        }
    }

    private static void renderIngredientTooltip(
            GuiGraphics graphics,
            Font font,
            IngredientStack stack,
            int mouseX,
            int mouseY,
            int screenW,
            int screenH
    ) {
        if (stack == null || stack.getId() == null) return;
        List<net.minecraft.network.chat.Component> lines = new ArrayList<>();

        if (stack.isFluid()) {
            var fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(stack.getId());
            if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                net.minecraftforge.fluids.FluidStack fs = new net.minecraftforge.fluids.FluidStack(fluid, 1000);
                lines.add(fs.getDisplayName());
            } else {
                lines.add(net.minecraft.network.chat.Component.literal(stack.getDisplayName()));
            }
            lines.add(net.minecraft.network.chat.Component.literal(String.format(Locale.ROOT, "§7Amount: §f%.0f mB", stack.getAmount())));
        } else {
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(stack.getId());
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                net.minecraft.world.item.ItemStack is = new net.minecraft.world.item.ItemStack(item);
                var player = Minecraft.getInstance().player;
                var flag = Minecraft.getInstance().options.advancedItemTooltips
                        ? net.minecraft.world.item.TooltipFlag.Default.ADVANCED
                        : net.minecraft.world.item.TooltipFlag.Default.NORMAL;
                lines.addAll(is.getTooltipLines(player, flag));
            } else {
                lines.add(net.minecraft.network.chat.Component.literal(stack.getDisplayName()));
            }
            if (stack.getAmount() > 0) {
                lines.add(net.minecraft.network.chat.Component.literal(String.format(Locale.ROOT, "§7Amount: §f%.0f", stack.getAmount())));
            }
        }

        if (stack.getChance() < 0.999) {
            lines.add(net.minecraft.network.chat.Component.literal(String.format(Locale.ROOT, "§eChance: §f%.1f%%", stack.getChance() * 100.0)));
        }

        if (Minecraft.getInstance().options.advancedItemTooltips && stack.isFluid()) {
            lines.add(net.minecraft.network.chat.Component.literal("§8" + stack.getId()));
        }

        lines.add(net.minecraft.network.chat.Component.literal("§8").append(net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.tooltip.recipes_uses")));
        BoardTooltipRenderer.renderComponentTooltip(graphics, font, lines, mouseX, mouseY, screenW, screenH);
    }

    // =========================================================================
    // Static Nested Implementation: Only loaded by JVM when EMI is present
    // =========================================================================
    private static class EmiPreviewRendererImpl {

        private static final int MAX_CACHE_SIZE = 40;

        private static final Map<Object, com.gtceu.calcboard.integration.emi.EmiPreviewWidgetHolder> WIDGET_CACHE =
                new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Object, com.gtceu.calcboard.integration.emi.EmiPreviewWidgetHolder> eldest) {
                        return size() > MAX_CACHE_SIZE;
                    }
                };

        private static boolean isEmiRecipe(Object recipe) {
            return recipe instanceof dev.emi.emi.api.recipe.EmiRecipe;
        }

        private static int[] getEmiRecipeDimensions(Object recipe) {
            if (recipe instanceof dev.emi.emi.api.recipe.EmiRecipe er) {
                var holder = getOrCreateHolder(er);
                return new int[]{holder.getWidth(), holder.getHeight()};
            }
            return null;
        }

        private static com.gtceu.calcboard.integration.emi.EmiPreviewWidgetHolder getOrCreateHolder(dev.emi.emi.api.recipe.EmiRecipe er) {
            return WIDGET_CACHE.computeIfAbsent(er, r -> {
                try {
                    com.gtceu.calcboard.integration.emi.EmiPreviewWidgetHolder h =
                            new com.gtceu.calcboard.integration.emi.EmiPreviewWidgetHolder(er.getDisplayWidth(), er.getDisplayHeight());
                    er.addWidgets(h);
                    return h;
                } catch (Throwable t) {
                    return new com.gtceu.calcboard.integration.emi.EmiPreviewWidgetHolder(
                            Math.max(120, er.getDisplayWidth()), Math.max(40, er.getDisplayHeight())
                    );
                }
            });
        }

        private static Object getHoveredIngredientFromCache(Object recipe, int originX, int originY, int mouseX, int mouseY) {
            if (recipe instanceof dev.emi.emi.api.recipe.EmiRecipe er) {
                var holder = WIDGET_CACHE.get(er);
                if (holder != null) {
                    return holder.getHoveredIngredient(originX, originY, mouseX, mouseY);
                }
            }
            return null;
        }

        private static int[] calculateEmiPreviewBounds(Object emiRecipeObj, int anchorX, int anchorY, int screenW, int screenH) {
            if (!(emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe er)) return null;
            var holder = getOrCreateHolder(er);

            int cardW = holder.getWidth() + 16;
            int cardH = holder.getHeight() + 28;

            int cardX = anchorX + 16;
            if (cardX + cardW > screenW - 4) {
                cardX = anchorX - cardW - 6;
            }
            if (cardX < 4) cardX = 4;

            int cardY = Math.max(4, Math.min(anchorY - 10, screenH - cardH - 4));
            return new int[]{cardX, cardY, cardW, cardH};
        }

        private static Object getHoveredIngredientFromAnchor(Object emiRecipeObj, int anchorX, int anchorY, int mouseX, int mouseY, int screenW, int screenH) {
            if (!(emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe er)) return null;
            int[] bounds = calculateEmiPreviewBounds(er, anchorX, anchorY, screenW, screenH);
            if (bounds == null) return null;
            int cardX = bounds[0];
            int cardY = bounds[1];
            int originX = cardX + 8;
            int originY = cardY + 22;

            var holder = WIDGET_CACHE.get(er);
            if (holder != null) {
                return holder.getHoveredIngredient(originX, originY, mouseX, mouseY);
            }
            return null;
        }

        private static void renderEmiRecipePreview(
                GuiGraphics graphics,
                Font font,
                SearchableRecipe sr,
                Object emiRecipeObj,
                int dialogX,
                int dialogY,
                int dialogW,
                int dialogH,
                int hoveredRowY,
                int mouseX,
                int mouseY,
                float partialTick,
                int screenW,
                int screenH
        ) {
            if (!(emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe er)) return;
            int[] bounds = calculatePreviewBounds(sr, dialogX, dialogY, dialogW, dialogH, hoveredRowY, screenW, screenH);
            if (bounds == null) return;
            int cardX = bounds[0];
            int cardY = bounds[1];
            int cardW = bounds[2];
            int cardH = bounds[3];

            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);

            // Background Card
            graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xF00A0F1D);
            graphics.renderOutline(cardX, cardY, cardW, cardH, 0xFF38BDF8);

            // Header Banner
            graphics.fill(cardX, cardY, cardX + cardW, cardY + 18, 0xFF1E293B);
            String catName = !sr.categoryName().isEmpty() ? sr.categoryName() : sr.categoryId();
            String title = (catName.isEmpty() ? "" : "§7[" + catName + "§7] ") + "§f" + sr.displayName();
            graphics.drawString(font, font.plainSubstrByWidth(title, cardW - 12), cardX + 6, cardY + 5, 0xFFFFFFFF, false);

            // Render EMI Recipe Widgets
            int originX = cardX + 8;
            int originY = cardY + 22;
            var holder = getOrCreateHolder(er);
            holder.render(graphics, originX, originY, mouseX, mouseY, partialTick);

            if (sr != null && !sr.isSupported()) {
                int warnY = cardY + cardH - 24;
                graphics.fill(cardX + 2, warnY, cardX + cardW - 2, cardY + cardH - 2, 0xEE3D2C1C);
                graphics.renderOutline(cardX + 2, warnY, cardW - 4, 22, 0xFFFB923C);
                String wTitle = "§6⚠ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.search.preview.unsupported_title").getString();
                String wDesc = "§7" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.search.preview.unsupported_desc").getString();
                graphics.drawString(font, font.plainSubstrByWidth(wTitle, cardW - 12), cardX + 6, warnY + 3, 0xFFFB923C, false);
                graphics.drawString(font, font.plainSubstrByWidth(wDesc, cardW - 12), cardX + 6, warnY + 12, 0xFFAAAAAA, false);
            }

            graphics.pose().popPose();
            holder.renderTooltips(graphics, font, originX, originY, mouseX, mouseY, screenW, screenH);
        }

        private static void renderEmiPreviewDirect(
                GuiGraphics graphics,
                Font font,
                Object emiRecipeObj,
                int anchorX,
                int anchorY,
                int mouseX,
                int mouseY,
                float partialTick,
                int screenW,
                int screenH
        ) {
            if (!(emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe er)) return;
            int[] bounds = calculateEmiPreviewBounds(er, anchorX, anchorY, screenW, screenH);
            if (bounds == null) return;
            int cardX = bounds[0];
            int cardY = bounds[1];
            int cardW = bounds[2];
            int cardH = bounds[3];

            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);

            // Background Card
            graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xF00A0F1D);
            graphics.renderOutline(cardX, cardY, cardW, cardH, 0xFF38BDF8);

            // Header Banner
            graphics.fill(cardX, cardY, cardX + cardW, cardY + 18, 0xFF1E293B);
            dev.emi.emi.api.recipe.EmiRecipeCategory cat = er.getCategory();
            String catName = (cat != null && cat.getName() != null) ? cat.getName().getString() : "";
            String rName = getRecipeDisplayName(er);
            String title = (catName.isEmpty() ? "" : "§7[" + catName + "§7] ") + "§f" + rName;
            graphics.drawString(font, font.plainSubstrByWidth(title, cardW - 12), cardX + 6, cardY + 5, 0xFFFFFFFF, false);

            // Render EMI Recipe Widgets
            int originX = cardX + 8;
            int originY = cardY + 22;
            var holder = getOrCreateHolder(er);
            holder.render(graphics, originX, originY, mouseX, mouseY, partialTick);

            graphics.pose().popPose();
            holder.renderTooltips(graphics, font, originX, originY, mouseX, mouseY, screenW, screenH);
        }

        private static String getRecipeDisplayName(dev.emi.emi.api.recipe.EmiRecipe recipe) {
            if (!recipe.getOutputs().isEmpty()) {
                var stacks = recipe.getOutputs().get(0).getEmiStacks();
                if (!stacks.isEmpty()) return stacks.get(0).getName().getString();
            }
            if (recipe.getId() != null) {
                String path = recipe.getId().getPath();
                if (path.contains("/")) path = path.substring(path.lastIndexOf('/') + 1);
                return path;
            }
            return "Recipe";
        }
    }
}



