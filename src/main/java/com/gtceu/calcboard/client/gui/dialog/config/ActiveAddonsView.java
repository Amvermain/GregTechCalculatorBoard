/*
 * Decompiled with CFR 0.152.
 */
package com.gtceu.calcboard.client.gui.dialog.config;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ActiveAddonsView {
    private final MachineConfigDialog dialog;
    private int scrollOffset = 0;

    public ActiveAddonsView(MachineConfigDialog dialog) {
        this.dialog = dialog;
    }

    public int getScrollOffset() {
        return this.scrollOffset;
    }

    public void setScrollOffset(int scrollOffset) {
        this.scrollOffset = scrollOffset;
    }

    public void resetScroll() {
        this.scrollOffset = 0;
    }

    public void render(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int dialogW, int mouseX, int mouseY, BoardScreen parent) {
        if (node == null) {
            return;
        }
        graphics.fill(x + 6, y + 70, x + dialogW - 6, y + 124, -15066075);
        graphics.renderOutline(x + 6, y + 70, dialogW - 12, 54, -13288371);
        List<MachineAddon> activeAddons = node.getAddons();
        int maxActiveScroll = Math.max(0, activeAddons.size() - 10);
        if (this.scrollOffset > maxActiveScroll) {
            this.scrollOffset = maxActiveScroll;
        }
        String activeCountLabel = "\u00a7b\u26a1 " + Component.translatable((String)"gui.gtcalcboard.config.active_addons", (Object[])new Object[]{String.valueOf(activeAddons.size())}).getString();
        graphics.drawString(font, activeCountLabel, x + 10, y + 73, -3090716, false);
        if (!activeAddons.isEmpty()) {
            int clearAllX = x + dialogW - 80;
            boolean clearHover = mouseX >= clearAllX && mouseX <= clearAllX + 72 && mouseY >= y + 72 && mouseY <= y + 84;
            graphics.fill(clearAllX, y + 72, clearAllX + 72, y + 84, clearHover ? -8969694 : -12771296);
            graphics.renderOutline(clearAllX, y + 72, 72, 12, clearHover ? -6278349 : -11194320);
            graphics.drawCenteredString(font, Component.translatable((String)"gui.gtcalcboard.config.clear_all").getString(), clearAllX + 36, y + 74, -1);
        }
        if (activeAddons.size() > 10) {
            int navX = x + dialogW - 150;
            boolean leftHover = mouseX >= navX && mouseX <= navX + 16 && mouseY >= y + 72 && mouseY <= y + 84;
            boolean rightHover = mouseX >= navX + 40 && mouseX <= navX + 56 && mouseY >= y + 72 && mouseY <= y + 84;
            graphics.fill(navX, y + 72, navX + 16, y + 84, leftHover ? -12695462 : -14407115);
            graphics.renderOutline(navX, y + 72, 16, 12, -11905685);
            graphics.drawCenteredString(font, "\u25c0", navX + 8, y + 74, this.scrollOffset > 0 ? -1 : -10066330);
            String pageStr = this.scrollOffset + 1 + "/" + (maxActiveScroll + 1);
            graphics.drawCenteredString(font, pageStr, navX + 28, y + 74, -5592406);
            graphics.fill(navX + 40, y + 72, navX + 56, y + 84, rightHover ? -12695462 : -14407115);
            graphics.renderOutline(navX + 40, y + 72, 16, 12, -11905685);
            graphics.drawCenteredString(font, "\u25b6", navX + 48, y + 74, this.scrollOffset < maxActiveScroll ? -1 : -10066330);
        }
        MachineAddon hoveredActiveAddon = null;
        if (activeAddons.isEmpty()) {
            graphics.drawString(font, "\u00a78" + Component.translatable((String)"gui.gtcalcboard.config.no_addons_installed").getString(), x + 10, y + 96, -7829368, false);
        } else {
            int slotSize = 32;
            int slotSpacing = 36;
            int maxShow = Math.min(activeAddons.size(), this.scrollOffset + 10);
            for (int i = this.scrollOffset; i < maxShow; ++i) {
                String badge;
                int sx = x + 10 + (i - this.scrollOffset) * slotSpacing;
                int sy = y + 86;
                boolean h = mouseX >= sx && mouseX <= sx + slotSize && mouseY >= sy && mouseY <= sy + slotSize;
                MachineAddon addon = activeAddons.get(i);
                graphics.fill(sx, sy, sx + slotSize, sy + slotSize, h ? -13292747 : -14670800);
                graphics.renderOutline(sx, sy, slotSize, slotSize, h ? -43691 : -13090734);
                ItemStack sample = addon.getRenderItemStack();
                if (sample != null && !sample.isEmpty()) {
                    graphics.renderItem(sample, sx + 8, sy + 4);
                    if (sample.getCount() > 1) {
                        graphics.renderItemDecorations(font, sample, sx + 8, sy + 4);
                    }
                }
                if (!(badge = this.dialog.formatAddonBadge(addon)).isEmpty()) {
                    graphics.drawCenteredString(font, font.plainSubstrByWidth(badge, slotSize + 6), sx + slotSize / 2, sy + 22, -3355444);
                }
                if (!h) continue;
                hoveredActiveAddon = addon;
            }
        }
        if (hoveredActiveAddon != null) {
            ArrayList<Component> tip = new ArrayList<Component>();
            tip.add(Component.literal((String)("\u00a7e" + hoveredActiveAddon.getName())));
            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
            adapter.buildAddonTooltip(node, hoveredActiveAddon, true, tip);
            tip.add(Component.literal((String)("\u00a7c\u2715 " + Component.translatable((String)"gui.gtcalcboard.config.click_to_remove").getString())));
            this.dialog.setDeferredTooltip(tip);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, RecipeNode node, int x, int y, int dialogW, BoardScreen parent) {
        int clearAllX;
        if (node == null) {
            return false;
        }
        List<MachineAddon> activeAddons = node.getAddons();
        int maxActiveScroll = Math.max(0, activeAddons.size() - 10);
        if (!activeAddons.isEmpty() && mouseX >= (double)(clearAllX = x + dialogW - 80) && mouseX <= (double)(clearAllX + 72) && mouseY >= (double)(y + 72) && mouseY <= (double)(y + 84)) {
            activeAddons.clear();
            this.scrollOffset = 0;
            this.dialog.invalidateFilteredCatalog();
            if (parent != null) {
                parent.markSummaryDirty();
            }
            return true;
        }
        if (activeAddons.size() > 10) {
            int navX = x + dialogW - 150;
            if (mouseX >= (double)navX && mouseX <= (double)(navX + 16) && mouseY >= (double)(y + 72) && mouseY <= (double)(y + 84)) {
                if (this.scrollOffset > 0) {
                    --this.scrollOffset;
                }
                return true;
            }
            if (mouseX >= (double)(navX + 40) && mouseX <= (double)(navX + 56) && mouseY >= (double)(y + 72) && mouseY <= (double)(y + 84)) {
                if (this.scrollOffset < maxActiveScroll) {
                    ++this.scrollOffset;
                }
                return true;
            }
        }
        int slotSize = 32;
        int slotSpacing = 36;
        int maxShow = Math.min(activeAddons.size(), this.scrollOffset + 10);
        for (int i = this.scrollOffset; i < maxShow; ++i) {
            int sx = x + 10 + (i - this.scrollOffset) * slotSpacing;
            int sy = y + 86;
            if (!(mouseX >= (double)sx) || !(mouseX <= (double)(sx + slotSize)) || !(mouseY >= (double)sy) || !(mouseY <= (double)(sy + slotSize))) continue;
            MachineAddon removed = activeAddons.remove(i);
            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
            adapter.onAddonRemoved(node, removed);
            if (this.scrollOffset > 0 && this.scrollOffset >= activeAddons.size()) {
                this.scrollOffset = Math.max(0, this.scrollOffset - 1);
            }
            this.dialog.invalidateFilteredCatalog();
            if (parent != null) {
                parent.markSummaryDirty();
            }
            return true;
        }
        return false;
    }
}
