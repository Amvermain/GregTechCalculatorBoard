package com.gtceu.calcboard.client.gui.dialog.config;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ActiveAddonsView {

    private final MachineConfigDialog dialog;
    private int scrollOffset = 0;

    public ActiveAddonsView(MachineConfigDialog dialog) {
        this.dialog = dialog;
    }

    public void resetScroll() {
        this.scrollOffset = 0;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public void setScrollOffset(int scrollOffset) {
        this.scrollOffset = scrollOffset;
    }

    public void render(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int dialogW, int virtualMouseX, int virtualMouseY, BoardScreen parent) {
        graphics.fill(x + 6, y + 70, x + dialogW - 6, y + 124, 0xFF1A1C25);
        graphics.renderOutline(x + 6, y + 70, dialogW - 12, 54, 0xFF353C4D);

        List<MachineAddon> activeAddons = node.getAddons();
        int maxActiveScroll = Math.max(0, activeAddons.size() - 10);
        if (scrollOffset > maxActiveScroll) scrollOffset = maxActiveScroll;

        String activeCountLabel = "§b" + Component.translatable("gui.gtcalcboard.config.active_addons", String.valueOf(activeAddons.size())).getString();
        graphics.drawString(font, activeCountLabel, x + 10, y + 73, 0xFFD0D6E4, false);

        if (!activeAddons.isEmpty()) {
            int clearAllX = x + dialogW - 80;
            boolean clearHover = virtualMouseX >= clearAllX && virtualMouseX <= clearAllX + 72 && virtualMouseY >= y + 72 && virtualMouseY <= y + 84;
            graphics.fill(clearAllX, y + 72, clearAllX + 72, y + 84, clearHover ? 0xFF772222 : 0xFF3D2020);
            graphics.renderOutline(clearAllX, y + 72, 72, 12, clearHover ? 0xFFA03333 : 0xFF553030);
            graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.config.clear_all").getString(), clearAllX + 36, y + 74, 0xFFFFFFFF);
        }

        if (activeAddons.size() > 10) {
            int navX = x + dialogW - 150;
            boolean leftHover = virtualMouseX >= navX && virtualMouseX <= navX + 16 && virtualMouseY >= y + 72 && virtualMouseY <= y + 84;
            boolean rightHover = virtualMouseX >= navX + 40 && virtualMouseX <= navX + 56 && virtualMouseY >= y + 72 && virtualMouseY <= y + 84;

            graphics.fill(navX, y + 72, navX + 16, y + 84, leftHover ? 0xFF3E485A : 0xFF242A35);
            graphics.renderOutline(navX, y + 72, 16, 12, 0xFF4A556B);
            graphics.drawCenteredString(font, "◀", navX + 8, y + 74, scrollOffset > 0 ? 0xFFFFFFFF : 0xFF666666);

            String pageStr = (scrollOffset + 1) + "/" + (maxActiveScroll + 1);
            graphics.drawCenteredString(font, pageStr, navX + 28, y + 74, 0xFFAAAAAA);

            graphics.fill(navX + 40, y + 72, navX + 56, y + 84, rightHover ? 0xFF3E485A : 0xFF242A35);
            graphics.renderOutline(navX + 40, y + 72, 16, 12, 0xFF4A556B);
            graphics.drawCenteredString(font, "▶", navX + 48, y + 74, scrollOffset < maxActiveScroll ? 0xFFFFFFFF : 0xFF666666);
        }

        MachineAddon hoveredActiveAddon = null;

        if (activeAddons.isEmpty()) {
            graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.config.no_addons_installed").getString(), x + 10, y + 96, 0xFF888888, false);
        } else {
            int slotSize = 32;
            int slotSpacing = 36;
            int maxShow = Math.min(activeAddons.size(), scrollOffset + 10);
            for (int i = scrollOffset; i < maxShow; i++) {
                int sx = x + 10 + (i - scrollOffset) * slotSpacing;
                int sy = y + 86;
                boolean h = virtualMouseX >= sx && virtualMouseX <= sx + slotSize && virtualMouseY >= sy && virtualMouseY <= sy + slotSize;

                MachineAddon addon = activeAddons.get(i);
                graphics.fill(sx, sy, sx + slotSize, sy + slotSize, h ? 0xFF352B35 : 0xFF202430);
                graphics.renderOutline(sx, sy, slotSize, slotSize, h ? 0xFFFF5555 : 0xFF384052);

                ItemStack sample = addon.getRenderItemStack();
                if (sample != null && !sample.isEmpty()) {
                    graphics.renderItem(sample, sx + 8, sy + 4);
                    if (sample.getCount() > 1) {
                        graphics.renderItemDecorations(font, sample, sx + 8, sy + 4);
                    }
                }

                String badge = dialog.formatAddonBadge(addon);
                if (!badge.isEmpty()) {
                    graphics.drawCenteredString(font, font.plainSubstrByWidth(badge, slotSize + 6), sx + slotSize / 2, sy + 22, 0xFFCCCCCC);
                }

                if (h) {
                    hoveredActiveAddon = addon;
                }
            }
        }

        if (hoveredActiveAddon != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§f" + hoveredActiveAddon.getName()));
            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
            adapter.buildAddonTooltip(node, hoveredActiveAddon, true, tooltip);
            tooltip.add(Component.literal("§c").append(Component.translatable("gui.gtcalcboard.config.remove")));
            MachineConfigDialog.appendAdvancedTooltipDebugInfo(tooltip, hoveredActiveAddon);
            dialog.setDeferredTooltip(tooltip);
        }
    }

    public boolean mouseClicked(double mX, double mY, int button, RecipeNode node, int x, int y, int dialogW, BoardScreen parent) {
        List<MachineAddon> activeAddons = node.getAddons();
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);

        if (!activeAddons.isEmpty()) {
            int clearAllX = x + dialogW - 80;
            if (mX >= clearAllX && mX <= clearAllX + 72 && mY >= y + 72 && mY <= y + 84) {
                List<MachineAddon> toRemove = new ArrayList<>(node.getAddons());
                for (MachineAddon a : toRemove) {
                    adapter.handleUninstallAddon(node, a);
                }
                if (node.getThreadingConfig() != null) {
                    node.getThreadingConfig().getHelixCounts().clear();
                    node.getThreadingConfig().reset();
                }
                node.getAddons().clear();
                node.setRotorEfficiency(100);
                node.setRotorPower(100);
                node.setRotorName("Standard (100%)");
                if (node.isGenerator()) {
                    node.autoCalculateTurbineParallel();
                }
                scrollOffset = 0;
                dialog.invalidateFilteredCatalog();
                if (parent != null) parent.markSummaryDirty();
                return true;
            }
        }

        int maxActiveScroll = Math.max(0, activeAddons.size() - 10);
        if (activeAddons.size() > 10) {
            int navX = x + dialogW - 150;
            if (mX >= navX && mX <= navX + 16 && mY >= y + 72 && mY <= y + 84) {
                if (scrollOffset > 0) scrollOffset--;
                return true;
            }
            if (mX >= navX + 40 && mX <= navX + 56 && mY >= y + 72 && mY <= y + 84) {
                if (scrollOffset < maxActiveScroll) scrollOffset++;
                return true;
            }
        }

        int slotSize = 32;
        int slotSpacing = 36;
        int maxShow = Math.min(activeAddons.size(), scrollOffset + 10);
        for (int i = scrollOffset; i < maxShow; i++) {
            int sx = x + 10 + (i - scrollOffset) * slotSpacing;
            if (mX >= sx && mX <= sx + slotSize && mY >= y + 86 && mY <= y + 118) {
                MachineAddon addon = activeAddons.get(i);
                adapter.handleUninstallAddon(node, addon);
                if (addon.getCategory() == MachineAddon.Category.ROTOR) {
                    node.setRotorEfficiency(100);
                    node.setRotorPower(100);
                    node.setRotorName("Standard (100%)");
                    node.setParallel(1);
                }
                maxActiveScroll = Math.max(0, node.getAddons().size() - 10);
                if (scrollOffset > maxActiveScroll) scrollOffset = maxActiveScroll;
                dialog.invalidateFilteredCatalog();
                if (parent != null) parent.markSummaryDirty();
                return true;
            }
        }

        return false;
    }
}
