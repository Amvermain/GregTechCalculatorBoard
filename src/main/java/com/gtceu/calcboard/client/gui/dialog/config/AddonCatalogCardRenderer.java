package com.gtceu.calcboard.client.gui.dialog.config;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renderer for addon catalog grid and list view cards, status badges, and deep scan pill.
 */
public final class AddonCatalogCardRenderer {

    private AddonCatalogCardRenderer() {}

    public record CachedCardData(
            MachineAddon addon,
            ItemStack sample,
            String gridName,
            String listName,
            String badgeText,
            String subtitleText,
            boolean isInstalled,
            int installedCount,
            boolean isThermal,
            boolean isUpgradeKit,
            boolean isThermalFull,
            int maxSlots,
            int sameTypeTotal
    ) {}

    public static CachedCardData buildCardData(Font font, RecipeNode node, MachineAddon addon, int cardW, boolean isListView, MachineConfigDialog dialog) {
        boolean isResetCard = "gtceu:rotor_standard".equals(addon.getId()) || "gtceu:reflector_none".equals(addon.getId());
        int installedCount = 0;
        for (MachineAddon a : node.getAddons()) {
            if (a.getId().equals(addon.getId())) installedCount++;
        }
        boolean isInstalled = !isResetCard && installedCount > 0;
        if (isResetCard) {
            isInstalled = checkResetCardInstalled(node, addon);
        }

        boolean isThermal = addon.getCategory() == MachineAddon.Category.THERMAL_AUGMENT;
        boolean isUpgradeKit = addon.isThermalUpgradeKit();
        boolean isThermalFull = isThermal && !isUpgradeKit && countThermalAugments(node) >= 3;

        ItemStack sample = addon.getRenderItemStack();
        String badgeText = dialog.formatAddonBadge(addon);

        String listName = "";
        String gridName = "";
        String subtitleText = "";

        if (isListView) {
            listName = font.plainSubstrByWidth(addon.getName(), cardW - 100);
        } else {
            gridName = formatGridCardName(font, addon.getName(), cardW);
            String sub = MachineConfigDialog.getAddonSubtitle(addon, node);
            int maxSubW = cardW - 28;
            if (font.width(sub) > maxSubW) {
                subtitleText = font.plainSubstrByWidth(sub, Math.max(16, maxSubW - font.width("..."))) + "...";
            } else {
                subtitleText = sub;
            }
        }

        int maxSlots = 0;
        int sameTypeTotal = 0;
        if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
            maxSlots = AddonCatalogView.getMaxHatchSlotsAllowed(node, addon);
            sameTypeTotal = AddonCatalogView.getTotalInstalledHatchesOfSameType(node, addon);
        }

        return new CachedCardData(
                addon, sample, gridName, listName, badgeText, subtitleText,
                isInstalled, installedCount, isThermal, isUpgradeKit, isThermalFull,
                maxSlots, sameTypeTotal
        );
    }

    private static boolean checkResetCardInstalled(RecipeNode node, MachineAddon addon) {
        if ("gtceu:rotor_standard".equals(addon.getId())) {
            for (MachineAddon a : node.getAddons()) {
                if (a.getCategory() == MachineAddon.Category.ROTOR) return false;
            }
            return true;
        }
        if ("gtceu:reflector_none".equals(addon.getId())) {
            for (MachineAddon a : node.getAddons()) {
                if (a.getCategory() == MachineAddon.Category.REFLECTOR) return false;
            }
            return true;
        }
        return false;
    }

    private static int countThermalAugments(RecipeNode node) {
        int count = 0;
        for (MachineAddon a : node.getAddons()) {
            if (a.getCategory() == MachineAddon.Category.THERMAL_AUGMENT && !a.isThermalUpgradeKit()) {
                count++;
            }
        }
        return count;
    }

    public static String formatGridCardName(Font font, String rawName, int cardW) {
        String aName = rawName
                .replace("Turbine Rotor", "Rotor")
                .replace("Reflector", "Refl.")
                .replace("Maintenance", "Maint.")
                .replace("Advanced", "Adv.")
                .replace("Borealic", "Boreal.")
                .replace("Complex", "Compl.");
        if (font.width(aName) > cardW - 36) {
            return font.plainSubstrByWidth(aName, Math.max(16, cardW - 36 - font.width("..."))) + "...";
        }
        return aName;
    }

    public static void renderListViewCard(GuiGraphics graphics, Font font, CachedCardData card, ItemStack sample, int bx, int by, int cardW, boolean hover) {
        if (sample != null && !sample.isEmpty()) {
            graphics.renderItem(sample, bx + 2, by + 2);
        }
        graphics.drawString(font, card.listName(), bx + 22, by + 6, card.isInstalled() ? 0xFF55FF88 : 0xFFE0E0E0, false);

        if (!card.badgeText().isEmpty()) {
            graphics.drawString(font, card.badgeText(), bx + cardW - 65, by + 6, 0xFFFFFFFF, false);
        }

        if (card.isInstalled()) {
            graphics.drawString(font, hover ? "§c✖" : (card.installedCount() > 1 ? "§a✔x" + card.installedCount() : "§a✔"), bx + cardW - 16, by + 6, 0xFFFFFFFF, false);
        } else {
            graphics.drawString(font, hover ? "§a+" : "§7+", bx + cardW - 14, by + 6, 0xFFFFFFFF, false);
        }
    }

    public static void renderGridViewCard(GuiGraphics graphics, Font font, CachedCardData card, ItemStack sample, int bx, int by, int cardW, int cardH, boolean hover) {
        if (sample != null && !sample.isEmpty()) {
            graphics.renderItem(sample, bx + 4, by + (cardH - 16) / 2);
        }

        renderCardStatusBadge(graphics, font, card, bx, by, cardW, hover);

        graphics.drawString(font, "§f" + card.gridName(), bx + 24, by + 5, 0xFFFFFFFF, false);
        graphics.drawString(font, card.badgeText(), bx + 24, by + 19, 0xFFCCCCCC, false);
        graphics.drawString(font, card.subtitleText(), bx + 24, by + 33, 0xFF888888, false);
    }

    public static void renderCardStatusBadge(GuiGraphics graphics, Font font, CachedCardData card, int bx, int by, int cardW, boolean hover) {
        int count = card.installedCount();
        MachineAddon addon = card.addon();

        if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
            if (count > 1) {
                graphics.drawString(font, "§a✔x" + count, bx + cardW - 28, by + 4, 0xFFFFFFFF, false);
            } else if (count == 1) {
                graphics.drawString(font, hover ? "§a+§7/§c-" : "§a✔", bx + cardW - (hover ? 18 : 11), by + 4, 0xFFFFFFFF, false);
            }
            return;
        }

        if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
            int maxSlots = card.maxSlots();
            int sameTypeTotal = card.sameTypeTotal();
            if (count > 1) {
                graphics.drawString(font, "§a✔x" + count, bx + cardW - (count >= 10 ? 36 : 28), by + 4, 0xFFFFFFFF, false);
            } else if (count == 1) {
                graphics.drawString(font, hover ? "§a+§7/§c-" : "§a✔", bx + cardW - (hover ? 18 : 11), by + 4, 0xFFFFFFFF, false);
            } else if (sameTypeTotal >= maxSlots) {
                graphics.drawString(font, "§8" + sameTypeTotal + "/" + maxSlots, bx + cardW - 24, by + 4, 0xFF888888, false);
            }
            return;
        }

        if (card.isThermal() && !card.isUpgradeKit()) {
            if (count > 1) {
                graphics.drawString(font, "§a✔x" + count, bx + cardW - 28, by + 4, 0xFFFFFFFF, false);
            } else if (count == 1) {
                graphics.drawString(font, hover ? "§a+§7/§c-" : "§a✔", bx + cardW - (hover ? 18 : 11), by + 4, 0xFFFFFFFF, false);
            } else if (card.isThermalFull()) {
                graphics.drawString(font, "§83/3", bx + cardW - 18, by + 4, 0xFF888888, false);
            }
            return;
        }

        if (card.isInstalled()) {
            graphics.drawString(font, hover ? "§c✖" : "§a✔", bx + cardW - 11, by + 4, 0xFFFFFFFF, false);
        }
    }

    public static void renderIndexerStatusPill(GuiGraphics graphics, Font font, int rightX, int y, int mouseX, int mouseY, MachineConfigDialog dialog) {
        var catalog = MachineAddonCatalog.getInstance();
        boolean running = catalog.isExhaustiveScanRunning();
        boolean complete = catalog.isExhaustiveScanComplete();

        if (!running && !complete) return;

        int pct = (int) Math.round(catalog.getExhaustiveProgress() * 100.0);
        String pillText = running
                ? "§e🔍 " + Component.translatable("gui.gtcalcboard.catalog.deep_scan_running", String.valueOf(pct)).getString()
                : "§a✔ " + Component.translatable("gui.gtcalcboard.catalog.deep_scan_complete").getString();

        int pillW = font.width(font.plainSubstrByWidth(pillText, 200)) + 12;
        int pillX = rightX - pillW;
        boolean hover = mouseX >= pillX && mouseX <= rightX && mouseY >= y && mouseY <= y + 14;

        int bg = running ? (hover ? 0xFF2E2818 : 0xFF221E14) : (hover ? 0xFF182A1E : 0xFF142018);
        int border = running ? (hover ? 0xFFE0C040 : 0xFF8A7320) : (hover ? 0xFF45B074 : 0xFF2D6E49);

        graphics.fill(pillX, y, rightX, y + 14, bg);
        graphics.renderOutline(pillX, y, pillW, 14, border);
        graphics.drawCenteredString(font, pillText, pillX + pillW / 2, y + 3, 0xFFFFFFFF);

        if (hover) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§e🔍 " + Component.translatable("gui.gtcalcboard.catalog.deep_scan_tooltip_title").getString()));
            tooltip.add(Component.literal(Component.translatable("gui.gtcalcboard.catalog.deep_scan_tooltip_track1").getString()));
            if (running) {
                tooltip.add(Component.literal(Component.translatable("gui.gtcalcboard.catalog.deep_scan_tooltip_track2_running", String.valueOf(pct)).getString()));
            } else {
                tooltip.add(Component.literal(Component.translatable("gui.gtcalcboard.catalog.deep_scan_tooltip_track2_complete").getString()));
            }
            dialog.setDeferredTooltip(tooltip);
        }
    }

    public static void renderAddonHoverTooltip(RecipeNode node, MachineAddon hoveredAddon, MachineConfigDialog dialog) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal("§f" + hoveredAddon.getName()));
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        adapter.buildAddonTooltip(node, hoveredAddon, false, tooltip);
        if (tooltip.size() <= 1 && hoveredAddon.getDescription() != null && !hoveredAddon.getDescription().isEmpty()) {
            tooltip.add(Component.literal("§7" + hoveredAddon.getDescription()));
        }

        boolean isReset = "gtceu:rotor_standard".equals(hoveredAddon.getId()) || "gtceu:reflector_none".equals(hoveredAddon.getId());
        boolean isInst = !isReset && adapter.isAddonInstalled(node, hoveredAddon);
        if (hoveredAddon.getCategory() == MachineAddon.Category.HATCH_BUS) {
            int maxSlots = AddonCatalogView.getMaxHatchSlotsAllowed(node, hoveredAddon);
            int sameTypeTotal = AddonCatalogView.getTotalInstalledHatchesOfSameType(node, hoveredAddon);
            tooltip.add(Component.literal(String.format(Locale.ROOT, "§7[Slots: §a%d §7/ §e%d§7]", sameTypeTotal, maxSlots)));
            tooltip.add(Component.literal("§eLeft-Click: §aAdd 1 Hatch"));
            tooltip.add(Component.literal("§eShift + Left-Click: §aFill All (" + maxSlots + "x)"));
            tooltip.add(Component.literal("§eRight-Click: §cRemove 1 Hatch"));
        } else if (!hoveredAddon.isThermalUpgradeKit()) {
            if (isInst) {
                tooltip.add(Component.literal("§c").append(Component.translatable("gui.gtcalcboard.config.remove")));
            } else {
                tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.config.install")));
            }
        }

        MachineConfigDialog.appendAdvancedTooltipDebugInfo(tooltip, hoveredAddon);
        dialog.setDeferredTooltip(tooltip);
    }

    public static void handleCardClick(RecipeNode node, MachineAddon addon, int button, IModAdapter adapter) {
        if (button == 1) {
            adapter.handleUninstallAddon(node, addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
            handleEnergyHatchClick(node, addon, adapter);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
            handleHatchBusClick(node, addon, adapter);
            return;
        }
        boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        if (isMultiInstallCategory(addon.getCategory())) {
            adapter.handleInstallAddon(node, addon, shift);
            return;
        }
        if (adapter.isAddonInstalled(node, addon)) {
            adapter.handleUninstallAddon(node, addon);
        } else {
            adapter.handleInstallAddon(node, addon, shift);
        }
    }

    private static boolean isMultiInstallCategory(com.gtceu.calcboard.api.catalog.AddonCategory cat) {
        return cat.equals(com.gtceu.calcboard.api.catalog.AddonCategory.MAGNET)
                || cat.equals(com.gtceu.calcboard.api.catalog.AddonCategory.THREADING)
                || cat.equals(com.gtceu.calcboard.api.catalog.AddonCategory.THERMAL_AUGMENT);
    }

    private static void handleEnergyHatchClick(RecipeNode node, MachineAddon addon, IModAdapter adapter) {
        int installedCount = adapter.getAddonInstalledCount(node, addon);
        int totalEnergyHatches = (int) node.getAddons().stream().filter(a -> a.getCategory() == MachineAddon.Category.ENERGY_HATCH).count();
        if (installedCount == 0 || (installedCount == 1 && totalEnergyHatches < 2)) {
            adapter.handleInstallAddon(node, addon, false);
        } else {
            adapter.handleUninstallAddon(node, addon);
        }
    }

    private static void handleHatchBusClick(RecipeNode node, MachineAddon addon, IModAdapter adapter) {
        int maxSlots = AddonHatchSlotHelper.getMaxHatchSlotsAllowed(node, addon);
        int sameTypeTotal = AddonHatchSlotHelper.getTotalInstalledHatchesOfSameType(node, addon);
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            installBatchHatches(node, addon, adapter, Math.max(1, maxSlots - sameTypeTotal));
        } else if (sameTypeTotal < maxSlots) {
            adapter.handleInstallAddon(node, addon, false);
        } else {
            adapter.handleUninstallAddon(node, addon);
        }
    }

    private static void installBatchHatches(RecipeNode node, MachineAddon addon, IModAdapter adapter, int toAdd) {
        for (int k = 0; k < toAdd; k++) {
            if (!adapter.canInstallAddon(node, addon)) break;
            adapter.handleInstallAddon(node, addon, false);
        }
    }
}
