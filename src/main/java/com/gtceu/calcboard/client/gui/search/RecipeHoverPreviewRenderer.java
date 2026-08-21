package com.gtceu.calcboard.client.gui.search;

import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.client.gui.FormatUtil;
import com.gtceu.calcboard.client.gui.IngredientRenderer;
import com.gtceu.calcboard.integration.emi.EmiPreviewWidgetHolder;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

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
            contentW = 200;
            contentH = 60 + Math.max(rn.getInputs().size(), rn.getOutputs().size()) * 18;
        }

        int cardW = Math.max(140, contentW + 16);
        int cardH = contentH + 28;

        int cardX = dialogX + dialogW + 6;
        if (cardX + cardW > screenW - 4) {
            cardX = dialogX - cardW - 6;
        }
        if (cardX < 4) {
            cardX = Math.max(4, screenW - cardW - 4);
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

        int contentW = holder.getWidth();
        int contentH = holder.getHeight();

        int cardW = Math.max(140, contentW + 16);
        int cardH = contentH + 28;

        int cardX = anchorX;
        if (cardX + cardW > screenW - 4) {
            cardX = Math.max(4, screenW - cardW - 4);
        }

        int cardY = Math.max(4, Math.min(anchorY - 10, screenH - cardH - 4));
        return new int[]{cardX, cardY, cardW, cardH};
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
        if (er == null) return;
        Font font = Minecraft.getInstance().font;

        int[] bounds = calculateEmiPreviewBounds(er, anchorX, anchorY, screenW, screenH);
        if (bounds == null) return;

        int cardX = bounds[0];
        int cardY = bounds[1];
        int cardW = bounds[2];
        int cardH = bounds[3];

        EmiPreviewWidgetHolder holder = WIDGET_CACHE.get(er);
        if (holder == null) return;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);

        // Dark modern background & border
        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xF00A0F1D);
        graphics.renderOutline(cardX, cardY, cardW, cardH, 0xFF38BDF8);

        // Header bar
        graphics.fill(cardX, cardY, cardX + cardW, cardY + 18, 0xFF1E293B);

        String catName = er.getCategory() != null ? er.getCategory().getName().getString() : "";
        String rName = "";
        if (!er.getOutputs().isEmpty() && !er.getOutputs().get(0).getEmiStacks().isEmpty()) {
            rName = er.getOutputs().get(0).getEmiStacks().get(0).getName().getString();
        } else if (er.getId() != null) {
            rName = er.getId().getPath();
        }

        String title = (catName.isEmpty() ? "" : "§7[" + catName + "§7] ") + "§f" + rName;
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
        int inCount = rn.getInputs().size();
        int outCount = rn.getOutputs().size();
        int maxRows = Math.max(1, Math.max(inCount, outCount));

        int cardW = 220;
        int cardH = 26 + maxRows * 18 + 20;

        int cardX = dialogX + dialogW + 6;
        if (cardX + cardW > screenW - 4) {
            cardX = dialogX - cardW - 6;
        }
        if (cardX < 4) cardX = 4;

        int cardY = Math.max(4, Math.min(hoveredRowY - 10, screenH - cardH - 4));

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
        for (int i = 0; i < maxRows; i++) {
            int rowY = contentY + i * 18;

            if (i < inCount) {
                IngredientStack in = rn.getInputs().get(i);
                IngredientRenderer.render(graphics, in, cardX + 8, rowY);
                String amtStr = in.isFluid() ? String.format("%.0f mB", in.getAmount()) : String.format("%.0f", in.getAmount());
                graphics.drawString(font, font.plainSubstrByWidth(amtStr, 70), cardX + 26, rowY + 5, 0xFFE2E8F0, false);
            }

            graphics.drawString(font, "➔", cardX + 102, rowY + 5, 0xFF64748B, false);

            if (i < outCount) {
                IngredientStack out = rn.getOutputs().get(i);
                IngredientRenderer.render(graphics, out, cardX + 118, rowY);
                String amtStr = out.isFluid() ? String.format("%.0f mB", out.getAmount()) : String.format("%.0f", out.getAmount());
                if (out.getChance() < 0.999) {
                    amtStr += String.format(" §d(%.0f%%)", out.getChance() * 100.0);
                }
                graphics.drawString(font, font.plainSubstrByWidth(amtStr, 80), cardX + 136, rowY + 5, 0xFFE2E8F0, false);
            }
        }

        // Stats Footer (Duration & EU/t)
        int footerY = cardY + cardH - 16;
        graphics.fill(cardX, footerY - 2, cardX + cardW, footerY - 1, 0xFF1E293B);
        String statsStr = String.format("§b⏱ %.2fs  §e⚡ %s EU/t (%s)", rn.getBaseDurationTicks() / 20.0, FormatUtil.formatCompactNumber(rn.getBaseEUt()), rn.getRecipeTier().name());
        graphics.drawString(font, statsStr, cardX + 6, footerY + 2, 0xFF94A3B8, false);

        graphics.pose().popPose();
    }
}
