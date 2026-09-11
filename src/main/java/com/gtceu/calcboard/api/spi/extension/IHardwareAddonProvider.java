package com.gtceu.calcboard.api.spi.extension;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Provider interface for modular hardware addons (coils, rotors, reflectors, hatches, augments).
 */
public interface IHardwareAddonProvider extends IModExtension {

    default void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
    }

    default boolean supportsAddons(RecipeNode node) {
        return !getApplicableAddonCategories(node).isEmpty();
    }

    default List<AddonCategory> getApplicableAddonCategories(RecipeNode node) {
        return List.of();
    }

    default boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory().equals(AddonCategory.CUSTOM)) return true;
        return getApplicableAddonCategories(node).contains(addon.getCategory());
    }

    default boolean canInstallAddon(RecipeNode node, MachineAddon addon) {
        return isAddonCompatible(node, addon);
    }

    default void onAddonInstalled(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        if (addon.getCategory() == MachineAddon.Category.COIL ||
                addon.getCategory() == MachineAddon.Category.ROTOR ||
                addon.getCategory() == MachineAddon.Category.REFLECTOR ||
                addon.getCategory() == MachineAddon.Category.PARALLEL ||
                addon.getCategory() == MachineAddon.Category.MAINTENANCE ||
                addon.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT ||
                addon.isThermalUpgradeKit()) {
            node.getAddons().removeIf(a -> a.getCategory() == addon.getCategory() || (addon.isThermalUpgradeKit() && a.isThermalUpgradeKit()) || a.getId().equals(addon.getId()));
        } else if (addon.getCategory() != MachineAddon.Category.ENERGY_HATCH &&
                addon.getCategory() != MachineAddon.Category.THERMAL_AUGMENT &&
                !addon.getCategory().equals(AddonCategory.MAGNET)) {
            node.getAddons().removeIf(a -> a.getId().equals(addon.getId()));
        }
        node.getAddons().add(addon);
        node.markOverclockDirty();
    }

    default void onAddonRemoved(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        node.markOverclockDirty();
    }

    default List<MachineAddon> getResetAddonCards(RecipeNode node) {
        return List.of();
    }

    default void handleInstallAddon(RecipeNode node, MachineAddon addon, boolean shiftClick) {
        if (node == null || addon == null) return;
        if (!node.isMultiblock() && node.hasMultiblockOption() && addon.getCategory() != AddonCategory.CUSTOM && addon.getCategory() != AddonCategory.THERMAL_AUGMENT) {
            node.setMultiblock(true);
            ResourceLocation mbWs = node.getMultiblockWorkstation();
            if (mbWs != null) {
                node.setMachineIcon(mbWs);
            }
        }
        onAddonInstalled(node, addon.copy());
    }

    default void handleUninstallAddon(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        node.removeOneAddon(addon.getId());
        onAddonRemoved(node, addon);
    }

    default boolean isAddonInstalled(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        return node.getAddons().stream().anyMatch(a -> a.getId().equals(addon.getId()));
    }

    default int getAddonInstalledCount(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return 0;
        return (int) node.getAddons().stream().filter(a -> a.getId().equals(addon.getId())).count();
    }

    default String formatAddonSubtitle(RecipeNode node, MachineAddon addon) {
        if (addon == null) return "";
        if (addon.getCategory().equals(AddonCategory.MAGNET)) {
            return String.format("Magnetic Force: %dx", addon.getMagneticForce());
        }
        if (addon.getCategory().equals(AddonCategory.PARALLEL)) {
            return String.format("Parallel: %dx", addon.getParallelMultiplier());
        }
        return "";
    }

    default void buildAddonTooltip(RecipeNode node, MachineAddon addon, boolean isActiveAddon, List<Component> tooltip) {
    }

    default String formatAddonBadge(RecipeNode node, MachineAddon addon) {
        if (addon == null) return "";
        if (addon.getCategory() == MachineAddon.Category.REFLECTOR) {
            return String.format("§b⚡Tier %d", addon.getReflectorTier());
        }
        if (addon.getCategory().equals(AddonCategory.THREADING)) {
            return "§a⚡Thread";
        }
        if (addon.getCategory().equals(AddonCategory.MAGNET)) {
            return String.format("§b⚡%dx", addon.getMagneticForce());
        }
        if (addon.getCategory().equals(AddonCategory.PARALLEL)) {
            return String.format("§a⚡%dx", addon.getParallelMultiplier());
        }
        return "";
    }

    default MachineAddon tailorAddon(MachineAddon addon, RecipeNode targetNode) {
        return addon;
    }
}
