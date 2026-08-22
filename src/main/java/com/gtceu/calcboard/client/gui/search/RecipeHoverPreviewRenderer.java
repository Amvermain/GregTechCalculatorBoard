package com.gtceu.calcboard.client.gui.search;

import com.gtceu.calcboard.api.EnergyType;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.client.gui.FormatUtil;
import com.gtceu.calcboard.client.gui.IngredientRenderer;
import com.gtceu.calcboard.integration.emi.EmiPreviewWidgetHolder;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * High-performance hover preview renderer with LRU caching for EMI recipe widgets & fallback custom cards.
 */
public class RecipeHoverPreviewRenderer {
    private static final int MAX_CACHE_SIZE = 40;

    private static final Map<Object, EmiPreviewWidgetHolder> WIDGET_CACHE = new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Object, EmiPreviewWidgetHolder> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    public static int[] calculatePreviewBounds(
            RecipeSearchEngine.SearchableRecipe sr,
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

        if (sr.recipe() instanceof EmiRecipe er) {
            EmiPreviewWidgetHolder holder = WIDGET_CACHE.computeIfAbsent(er, r -> {
                try {
                    EmiPreviewWidgetHolder h = new EmiPreviewWidgetHolder(er.getDisplayWidth(), er.getDisplayHeight());
                    er.addWidgets(h);
                    return h;
                } catch (Throwable t) {
                    return new EmiPreviewWidgetHolder(Math.max(120, er.getDisplayWidth()), Math.max(40, er.getDisplayHeight()));
                }
            });
            contentW = holder.getWidth();
            contentH = holder.getHeight();
        } else if (sr.recipe() instanceof RecipeNode rn) {
            int inCount = rn.getInputs().size();
            int outCount = rn.getOutputs().size();
            int maxRows = Math.max(1, Math.max(inCount, outCount));
            contentW = (inCount == 0 || outCount == 0) ? 140 : 190;
            contentH = maxRows * 18 + 4;
        }

        int cardW = Math.max(130, contentW + 16);
        int cardH = contentH + 34;

        int rightSpace = screenW - (dialogX + dialogW + 6);
        int leftSpace = dialogX - 6;

        int cardX;
        if (rightSpace >= cardW) {
            cardX = dialogX + dialogW + 6;
        } else if (leftSpace >= cardW) {
            cardX = dialogX - cardW - 6;
        } else {
            // Neither side has enough room: clamp to whichever side has more margin
            if (rightSpace >= leftSpace) {
                cardX = Math.min(screenW - cardW - 4, dialogX + dialogW + 6);
            } else {
                cardX = Math.max(4, dialogX - cardW - 6);
            }
        }

        int cardY = Math.max(4, Math.min(hoveredRowY - 10, screenH - cardH - 4));
        return new int[]{cardX, cardY, cardW, cardH};
    }

    public static dev.emi.emi.api.stack.EmiIngredient getHoveredIngredient(
            RecipeSearchEngine.SearchableRecipe sr,
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
        if (sr == null || !(sr.recipe() instanceof EmiRecipe er)) return null;
        int[] bounds = calculatePreviewBounds(sr, dialogX, dialogY, dialogW, dialogH, hoveredRowY, screenW, screenH);
        if (bounds == null) return null;
        int cardX = bounds[0];
        int cardY = bounds[1];
        int originX = cardX + 8;
        int originY = cardY + 22;

        EmiPreviewWidgetHolder holder = WIDGET_CACHE.get(er);
        if (holder != null) {
            return holder.getHoveredIngredient(originX, originY, mouseX, mouseY);
        }
        return null;
    }

    /**
     * Renders a floating recipe preview card for the hovered recipe.
     */
    public static void renderPreview(
            GuiGraphics graphics,
            RecipeSearchEngine.SearchableRecipe sr,
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

        if (sr.recipe() instanceof EmiRecipe er) {
            renderEmiRecipePreview(graphics, font, sr, er, dialogX, dialogY, dialogW, dialogH, hoveredRowY, mouseX, mouseY, partialTick, screenW, screenH);
        } else if (sr.recipe() instanceof RecipeNode rn) {
            renderRecipeNodePreview(graphics, font, sr, rn, dialogX, dialogY, dialogW, dialogH, hoveredRowY, mouseX, mouseY, screenW, screenH);
        }
    }

    public static int[] calculateEmiPreviewBounds(
            EmiRecipe er,
            int anchorX,
            int anchorY,
            int screenW,
            int screenH
    ) {
        if (er == null) return null;

        EmiPreviewWidgetHolder holder = WIDGET_CACHE.computeIfAbsent(er, r -> {
            try {
                EmiPreviewWidgetHolder h = new EmiPreviewWidgetHolder(er.getDisplayWidth(), er.getDisplayHeight());
                er.addWidgets(h);
                return h;
            } catch (Throwable t) {
                return new EmiPreviewWidgetHolder(Math.max(120, er.getDisplayWidth()), Math.max(40, er.getDisplayHeight()));
            }
        });

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

    public static dev.emi.emi.api.stack.EmiIngredient getHoveredIngredientFromAnchor(
            EmiRecipe er,
            int anchorX,
            int anchorY,
            int mouseX,
            int mouseY,
            int screenW,
            int screenH
    ) {
        if (er == null) return null;
        int[] bounds = calculateEmiPreviewBounds(er, anchorX, anchorY, screenW, screenH);
        if (bounds == null) return null;
        int cardX = bounds[0];
        int cardY = bounds[1];
        int originX = cardX + 8;
        int originY = cardY + 22;

        EmiPreviewWidgetHolder holder = WIDGET_CACHE.get(er);
        if (holder != null) {
            return holder.getHoveredIngredient(originX, originY, mouseX, mouseY);
        }
        return null;
    }

    public static dev.emi.emi.api.stack.EmiIngredient getEmiHoveredIngredient(
            EmiRecipe er,
            int anchorX,
            int anchorY,
            int mouseX,
            int mouseY,
            int screenW,
            int screenH
    ) {
        return getHoveredIngredientFromAnchor(er, anchorX, anchorY, mouseX, mouseY, screenW, screenH);
    }

    public static void renderEmiPreviewDirect(
            GuiGraphics graphics,
            EmiRecipe er,
            int anchorX,
            int anchorY,
            int mouseX,
            int mouseY,
            float partialTick,
            int screenW,
            int screenH
    ) {
        renderEmiRecipePreviewFromAnchor(graphics, Minecraft.getInstance().font, er, anchorX, anchorY, mouseX, mouseY, partialTick, screenW, screenH);
    }

    public static void renderEmiRecipePreviewFromAnchor(
            GuiGraphics graphics,
            Font font,
            EmiRecipe er,
            int anchorX,
            int anchorY,
            int mouseX,
            int mouseY,
            float partialTick,
            int screenW,
            int screenH
    ) {
        if (er == null) return;
        int[] bounds = calculateEmiPreviewBounds(er, anchorX, anchorY, screenW, screenH);
        if (bounds == null) return;

        int cardX = bounds[0];
        int cardY = bounds[1];
        int cardW = bounds[2];
        int cardH = bounds[3];

        EmiPreviewWidgetHolder holder = WIDGET_CACHE.computeIfAbsent(er, r -> {
            try {
                EmiPreviewWidgetHolder h = new EmiPreviewWidgetHolder(er.getDisplayWidth(), er.getDisplayHeight());
                er.addWidgets(h);
                return h;
            } catch (Throwable t) {
                return new EmiPreviewWidgetHolder(Math.max(120, er.getDisplayWidth()), Math.max(40, er.getDisplayHeight()));
            }
        });

        // Push pose & high z-index
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);

        // Dark modern background & border
        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xF00A0F1D);
        graphics.renderOutline(cardX, cardY, cardW, cardH, 0xFF38BDF8);

        // Header bar
        graphics.fill(cardX, cardY, cardX + cardW, cardY + 18, 0xFF1E293B);

        EmiRecipeCategory cat = er.getCategory();
        String catName = cat != null && cat.getName() != null ? cat.getName().getString() : "";
        String title = (catName.isEmpty() ? "" : "§7[" + catName + "§7] ") + "§f" + (er.getId() != null ? er.getId().getPath() : "");
        graphics.drawString(font, font.plainSubstrByWidth(title, cardW - 12), cardX + 6, cardY + 5, 0xFFFFFFFF, false);

        // Render Native EMI Widgets
        int originX = cardX + 8;
        int originY = cardY + 22;
        holder.render(graphics, originX, originY, mouseX, mouseY, partialTick);

        // Render tooltips for hovered widget/ingredient
        holder.renderTooltips(graphics, font, originX, originY, mouseX, mouseY);

        graphics.pose().popPose();
    }

    private static void renderEmiRecipePreview(
            GuiGraphics graphics,
            Font font,
            RecipeSearchEngine.SearchableRecipe sr,
            EmiRecipe er,
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
        int[] bounds = calculatePreviewBounds(sr, dialogX, dialogY, dialogW, dialogH, hoveredRowY, screenW, screenH);
        if (bounds == null) return;

        int cardX = bounds[0];
        int cardY = bounds[1];
        int cardW = bounds[2];
        int cardH = bounds[3];

        EmiPreviewWidgetHolder holder = WIDGET_CACHE.computeIfAbsent(er, r -> {
            try {
                EmiPreviewWidgetHolder h = new EmiPreviewWidgetHolder(er.getDisplayWidth(), er.getDisplayHeight());
                er.addWidgets(h);
                return h;
            } catch (Throwable t) {
                return new EmiPreviewWidgetHolder(Math.max(120, er.getDisplayWidth()), Math.max(40, er.getDisplayHeight()));
            }
        });

        // Push pose & high z-index
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);

        // Dark modern background & border
        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xF00A0F1D);
        graphics.renderOutline(cardX, cardY, cardW, cardH, 0xFF38BDF8);

        // Header bar
        graphics.fill(cardX, cardY, cardX + cardW, cardY + 18, 0xFF1E293B);

        String catName = !sr.categoryName().isEmpty() ? sr.categoryName() : sr.categoryId();
        String title = (catName.isEmpty() ? "" : "§7[" + catName + "§7] ") + "§f" + sr.displayName();
        graphics.drawString(font, font.plainSubstrByWidth(title, cardW - 12), cardX + 6, cardY + 5, 0xFFFFFFFF, false);

        // Render Native EMI Widgets
        int originX = cardX + 8;
        int originY = cardY + 22;
        holder.render(graphics, originX, originY, mouseX, mouseY, partialTick);

        // Render tooltips for hovered widget/ingredient
        holder.renderTooltips(graphics, font, originX, originY, mouseX, mouseY);

        graphics.pose().popPose();
    }

    private static void renderRecipeNodePreview(
            GuiGraphics graphics,
            Font font,
            RecipeSearchEngine.SearchableRecipe sr,
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
        int contentY = cardY + 22;
        if (inCount == 0 && outCount > 0) {
            // Source Recipe (Produces outputs only)
            for (int i = 0; i < outCount; i++) {
                int rowY = contentY + i * 18;
                IngredientStack out = rn.getOutputs().get(i);
                IngredientRenderer.render(graphics, out, cardX + 10, rowY);
                String amtStr = out.isFluid() ? String.format("%.0f mB", out.getAmount()) : String.format("%.0f", out.getAmount());
                if (out.getChance() < 0.999) {
                    amtStr += String.format(" §d(%.0f%%)", out.getChance() * 100.0);
                }
                amtStr += "  §a(Output)";
                graphics.drawString(font, font.plainSubstrByWidth(amtStr, cardW - 36), cardX + 30, rowY + 5, 0xFFE2E8F0, false);
            }
        } else if (outCount == 0 && inCount > 0) {
            // Sink Recipe (Consumes inputs only)
            for (int i = 0; i < inCount; i++) {
                int rowY = contentY + i * 18;
                IngredientStack in = rn.getInputs().get(i);
                IngredientRenderer.render(graphics, in, cardX + 10, rowY);
                String amtStr = in.isFluid() ? String.format("%.0f mB", in.getAmount()) : String.format("%.0f", in.getAmount());
                amtStr += "  §c(Input)";
                graphics.drawString(font, font.plainSubstrByWidth(amtStr, cardW - 36), cardX + 30, rowY + 5, 0xFFE2E8F0, false);
            }
        } else {
            for (int i = 0; i < maxRows; i++) {
                int rowY = contentY + i * 18;

                if (i < inCount) {
                    IngredientStack in = rn.getInputs().get(i);
                    IngredientRenderer.render(graphics, in, cardX + 8, rowY);
                    String amtStr = in.isFluid() ? String.format("%.0f mB", in.getAmount()) : String.format("%.0f", in.getAmount());
                    graphics.drawString(font, font.plainSubstrByWidth(amtStr, 60), cardX + 26, rowY + 5, 0xFFE2E8F0, false);
                }

                int arrowX = cardX + (cardW / 2) - 4;
                graphics.drawString(font, "➔", arrowX, rowY + 5, 0xFF64748B, false);

                if (i < outCount) {
                    IngredientStack out = rn.getOutputs().get(i);
                    int outStackX = arrowX + 12;
                    IngredientRenderer.render(graphics, out, outStackX, rowY);
                    String amtStr = out.isFluid() ? String.format("%.0f mB", out.getAmount()) : String.format("%.0f", out.getAmount());
                    if (out.getChance() < 0.999) {
                        amtStr += String.format(" §d(%.0f%%)", out.getChance() * 100.0);
                    }
                    graphics.drawString(font, font.plainSubstrByWidth(amtStr, cardW - (outStackX + 18) - 4), outStackX + 18, rowY + 5, 0xFFE2E8F0, false);
                }
            }
        }

        // Stats Footer (Duration & EU/t / RF/t / Heat)
        int footerY = cardY + cardH - 16;
        graphics.fill(cardX, footerY - 2, cardX + cardW, footerY - 1, 0xFF1E293B);
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(rn);
        String statsStr = String.format("§b⏱ %.2fs  %s", rn.getBaseDurationTicks() / 20.0, adapter.formatEnergyStats(rn, com.gtceu.calcboard.api.BoardManager.getInstance().getPowerDisplayMode()));
        graphics.drawString(font, font.plainSubstrByWidth(statsStr, cardW - 8), cardX + 6, footerY + 2, 0xFF94A3B8, false);

        graphics.pose().popPose();
    }
}
