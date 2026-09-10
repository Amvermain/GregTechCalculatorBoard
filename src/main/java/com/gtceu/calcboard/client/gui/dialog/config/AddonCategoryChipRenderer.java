package com.gtceu.calcboard.client.gui.dialog.config;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Renderer and layout coordinator for category filter chips in the addon catalog.
 */
public final class AddonCategoryChipRenderer {

    private AddonCategoryChipRenderer() {}

    public static String getCategoryLabel(AddonCategory cat) {
        if (cat == null) return Component.translatable("gui.gtcalcboard.addon_cat.all").getString();
        return Component.translatable(cat.getTranslatableKey()).getString();
    }

    public static List<AddonCategory> getAllCategoriesForFilter(RecipeNode node) {
        List<AddonCategory> list = new ArrayList<>();
        list.add(null);
        List<AddonCategory> relCats = MachineAddon.getRelevantCategories(node);
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        List<MachineAddon> allAddons = MachineAddonCatalog.getInstance().getAllAddons();

        java.util.Set<AddonCategory> activeCategories = new java.util.HashSet<>();
        if (adapter != null) {
            for (MachineAddon r : adapter.getResetAddonCards(node)) {
                if (r != null && adapter.isAddonCompatible(node, r)) {
                    activeCategories.add(r.getCategory());
                }
            }
        }

        for (MachineAddon a : allAddons) {
            if (a != null && a.isCompatibleWith(node)) {
                activeCategories.add(a.getCategory());
            }
        }

        for (AddonCategory cat : relCats) {
            if (cat != null && !cat.equals(AddonCategory.CUSTOM) && !list.contains(cat)) {
                if (cat.equals(AddonCategory.THREADING) || activeCategories.contains(cat)) {
                    list.add(cat);
                }
            }
        }
        list.add(AddonCategory.CUSTOM);
        return list;
    }

    public static double ensureCategoryVisible(RecipeNode node, AddonCategory targetCat, int dialogWidth, double currentScrollX, List<AddonCategory> allCats) {
        if (node == null) return currentScrollX;
        int targetIdx = (targetCat != null && targetCat.equals(AddonCategory.CUSTOM)) ? (allCats.size() - 1) : allCats.indexOf(targetCat);
        if (targetIdx < 0) return currentScrollX;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return currentScrollX;
        Font font = mc.font;
        int chipLeft = 0;
        for (int i = 0; i < targetIdx; i++) {
            chipLeft += font.width(getCategoryLabel(allCats.get(i))) + 12 + 4;
        }
        int chipW = font.width(getCategoryLabel(allCats.get(targetIdx))) + 12;
        int availW = dialogWidth - 20;
        if (chipLeft < currentScrollX) {
            return chipLeft;
        } else if (chipLeft + chipW > currentScrollX + availW) {
            return chipLeft + chipW - availW;
        }
        return currentScrollX;
    }

    public static double renderCategoryFilterChips(
            GuiGraphics graphics, Font font, RecipeNode node, int startX, int startY, int dialogW,
            int mouseX, int mouseY, double categoryScrollX, MachineConfigDialog dialog, List<AddonCategory> allCats) {
        int totalCats = allCats.size();
        int availW = dialogW - 20;

        int totalWidth = 0;
        List<Integer> chipWidths = new ArrayList<>();
        for (AddonCategory cat : allCats) {
            int w = font.width(getCategoryLabel(cat)) + 12;
            chipWidths.add(w);
            totalWidth += w + 4;
        }
        totalWidth = Math.max(0, totalWidth - 4);

        boolean needsScroll = totalWidth > availW;
        int scrollAreaX = startX + (needsScroll ? 8 : 0);
        int scrollAreaW = availW - (needsScroll ? 16 : 0);

        double maxCategoryScrollX = Math.max(0, totalWidth - scrollAreaW);
        categoryScrollX = Math.max(0, Math.min(maxCategoryScrollX, categoryScrollX));

        if (maxCategoryScrollX > 0 && categoryScrollX > 2) {
            graphics.drawString(font, "◀", startX, startY + 4, 0xFFFFAA00, false);
        }

        dialog.enableScaledScissor(graphics, scrollAreaX, startY - 1, scrollAreaX + scrollAreaW, startY + 17);

        int cx = scrollAreaX - (int) categoryScrollX;
        for (int i = 0; i < totalCats; i++) {
            AddonCategory cat = allCats.get(i);
            int bw = chipWidths.get(i);
            boolean active = dialog.isCustomBuilderActive() ? (cat != null && cat.equals(AddonCategory.CUSTOM))
                    : ((dialog.getSelectedCategory() == null && cat == null) || (dialog.getSelectedCategory() != null && dialog.getSelectedCategory().equals(cat)));
            if (cx + bw >= scrollAreaX && cx <= scrollAreaX + scrollAreaW) {
                renderChip(graphics, font, getCategoryLabel(cat), active, cx, startY, bw, mouseX, mouseY, scrollAreaX, scrollAreaX + scrollAreaW);
            }
            cx += bw + 4;
        }

        BoardScissorHelper.disableScissor(graphics);

        if (maxCategoryScrollX > 0 && categoryScrollX < maxCategoryScrollX - 2) {
            graphics.drawString(font, "▶", startX + availW - 6, startY + 4, 0xFFFFAA00, false);
        }

        return categoryScrollX;
    }

    private static void renderChip(GuiGraphics graphics, Font font, String label, boolean active, int bx, int by, int bw, int mouseX, int mouseY, int clipMinX, int clipMaxX) {
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 16 && mouseX >= clipMinX && mouseX <= clipMaxX;
        graphics.fill(bx, by, bx + bw, by + 16, active ? 0xFF1B1E28 : (hover ? 0xFF2E3544 : 0xFF222733));
        graphics.renderOutline(bx, by, bw, 16, active ? 0xFF58D3FF : 0xFF333A48);
        graphics.drawCenteredString(font, label, bx + bw / 2, by + 4, active ? 0xFF58D3FF : 0xFF9CA5B8);
    }
}
