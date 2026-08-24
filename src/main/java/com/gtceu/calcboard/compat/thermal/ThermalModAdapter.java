package com.gtceu.calcboard.compat.thermal;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.client.gui.NodeWidget;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.thermal.addon.ThermalAugmentAddon;
import com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * Mod Adapter facade for Thermal Series (Thermal Expansion, Foundation, Innovation).
 * Manages Thermal augments, upgrade kit discovery, slot validation, and recipe scaling.
 */
public class ThermalModAdapter implements IModAdapter {

    @Override
    public String getModId() {
        return "thermal";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean isLoaded() {
        try {
            Class<?> mlClass = Class.forName("net.minecraftforge.fml.ModList");
            Object ml = mlClass.getMethod("get").invoke(null);
            if (ml != null) {
                var isLoadedMethod = mlClass.getMethod("isLoaded", String.class);
                return (boolean) isLoadedMethod.invoke(ml, "thermal") || (boolean) isLoadedMethod.invoke(ml, "thermal_expansion") || (boolean) isLoadedMethod.invoke(ml, "cofh_core");
            }
        } catch (Throwable ignored) {}
        return true;
    }

    @Override
    public boolean handlesCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        String ns = categoryId.getNamespace().toLowerCase();
        return ns.equals("thermal") || ns.equals("thermal_expansion") || ns.equals("cofh_core");
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        return ThermalAugmentHelper.isThermalMachine(node);
    }

    @Override
    public boolean supportsAddons(RecipeNode node) {
        return true;
    }

    @Override
    public List<AddonCategory> getApplicableAddonCategories(RecipeNode node) {
        return List.of(AddonCategory.THERMAL_AUGMENT, AddonCategory.CUSTOM);
    }

    @Override
    public boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory().equals(AddonCategory.CUSTOM)) return true;
        return addon.getCategory().equals(AddonCategory.THERMAL_AUGMENT);
    }

    @Override
    public boolean canInstallAddon(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (!isAddonCompatible(node, addon)) return false;
        if (addon instanceof ThermalAugmentAddon ta && ta.isUpgradeKit()) {
            return true; // Replaces existing kit
        }
        if (addon.getParallelMultiplier() > 1) {
            return true; // Kit scale replacement
        }
        // General augments limited to 3 slots
        long nonKitCount = node.getAddons().stream().filter(a -> !(a instanceof ThermalAugmentAddon ta && ta.isUpgradeKit()) && a.getParallelMultiplier() <= 1).count();
        return nonKitCount < 3;
    }

    @Override
    public void onAddonInstalled(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        boolean isKit = (addon instanceof ThermalAugmentAddon ta && ta.isUpgradeKit()) || addon.getParallelMultiplier() > 1;
        if (isKit) {
            node.getAddons().removeIf(a -> (a instanceof ThermalAugmentAddon ta && ta.isUpgradeKit()) || a.getParallelMultiplier() > 1);
        }
        node.getAddons().add(addon);
    }

    @Override
    public void handleInstallAddon(RecipeNode node, MachineAddon addon, boolean shiftClick) {
        if (node == null || addon == null) return;
        boolean isKit = (addon instanceof ThermalAugmentAddon ta && ta.isUpgradeKit()) || addon.getParallelMultiplier() > 1;
        if (isKit) {
            node.getAddons().removeIf(a -> (a instanceof ThermalAugmentAddon ta && ta.isUpgradeKit()) || a.getParallelMultiplier() > 1);
            node.addAddon(addon.copy());
        } else {
            long nonKitCount = node.getAddons().stream().filter(a -> !(a instanceof ThermalAugmentAddon ta && ta.isUpgradeKit()) && a.getParallelMultiplier() <= 1).count();
            int targetCount = (int) node.getAddons().stream().filter(a -> a.getId().equals(addon.getId())).count();
            if (shiftClick) {
                int toAdd = (int) (3 - nonKitCount);
                for (int k = 0; k < toAdd; k++) {
                    node.addAddon(addon.copy());
                }
            } else {
                if (nonKitCount < 3) {
                    node.addAddon(addon.copy());
                } else if (targetCount > 0) {
                    node.removeSingleAddon(addon.getId());
                }
            }
        }
    }

    @Override
    public void handleUninstallAddon(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        boolean isKit = (addon instanceof ThermalAugmentAddon ta && ta.isUpgradeKit()) || addon.getParallelMultiplier() > 1;
        if (isKit) {
            node.removeAddon(addon.getId());
        } else {
            node.removeSingleAddon(addon.getId());
        }
    }

    @Override
    public void buildAddonTooltip(RecipeNode node, MachineAddon addon, boolean isActiveAddon, List<Component> tooltip) {
        if (addon == null || tooltip == null) return;
        boolean isKit = (addon instanceof ThermalAugmentAddon ta && ta.isUpgradeKit()) || addon.getParallelMultiplier() > 1;
        if (isKit) {
            tooltip.add(Component.literal("§6").append(Component.translatable("gui.gtcalcboard.addon.thermal.upgrade_desc", addon.getParallelMultiplier())));
            if (isActiveAddon) {
                tooltip.add(Component.literal("§c").append(Component.translatable("gui.gtcalcboard.addon.thermal.remove_kit")));
            } else {
                boolean isInst = node.getAddons().stream().anyMatch(a -> a.getId().equals(addon.getId()));
                if (isInst) {
                    tooltip.add(Component.literal("§c").append(Component.translatable("gui.gtcalcboard.addon.thermal.remove_kit")));
                } else {
                    tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.addon.thermal.install_kit")));
                }
            }
        } else {
            long totalRegAugs = node.getAddons().stream().filter(a -> !(a instanceof ThermalAugmentAddon ta && ta.isUpgradeKit()) && a.getParallelMultiplier() <= 1).count();
            int targetCount = (int) node.getAddons().stream().filter(a -> a.getId().equals(addon.getId())).count();

            tooltip.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.addon.thermal.slots", totalRegAugs)));
            if (targetCount > 0) {
                tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.addon.thermal.installed", targetCount)));
                if (totalRegAugs < 3) {
                    tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.addon.thermal.add_copy")));
                }
                tooltip.add(Component.literal("§c").append(Component.translatable("gui.gtcalcboard.addon.thermal.remove_copy")));
            } else {
                if (totalRegAugs < 3) {
                    tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.addon.thermal.install")));
                } else {
                    tooltip.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.addon.thermal.slots_full")));
                }
            }
        }
    }

    @Override
    public void onAddonRemoved(RecipeNode node, MachineAddon addon) {
        // Lifecycle hook
    }

    @Override
    public String formatAddonBadge(RecipeNode node, MachineAddon addon) {
        if (addon == null) return "";
        boolean isKit = (addon instanceof ThermalAugmentAddon ta && ta.isUpgradeKit()) || addon.getParallelMultiplier() > 1;
        if (isKit) {
            return String.format("§d⚡ %dx Scale", addon.getParallelMultiplier());
        }
        if (addon.getDurationMultiplier() != 1.0 && addon.getEutMultiplier() != 1.0) {
            return String.format("§e⚡ %.1fx ⏱ %.1fx", addon.getEutMultiplier(), addon.getDurationMultiplier());
        }
        if (addon.getEutMultiplier() != 1.0) {
            return String.format("§e⚡ %.2fx", addon.getEutMultiplier());
        }
        if (addon.getDurationMultiplier() != 1.0) {
            return String.format("§a⏱ %.2fx", addon.getDurationMultiplier());
        }
        return "";
    }

    @Override
    public void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
        ThermalAddonCrawler.discoverAddons(collector, recipeOutputStacks);
    }

    @Override
    public void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
        // Enriched through CategoryCapabilityMatrix Thermal dynamo/machine detection
    }

    @Override
    public boolean adaptRecipeDetails(Object emiRecipeObj, Object backing, EmiRecipeConverter.RecipeDetails details) {
        return ThermalRecipeHandler.adaptRecipeDetails(emiRecipeObj, backing, details);
    }

    public static long extractEnergyRF(Object backing) {
        return ThermalRecipeHandler.extractEnergyRF(backing);
    }

    @Override
    public OverclockMode.OverclockResult computeOverclock(RecipeNode node, GTVoltageTier targetTier, boolean isGenerator) {
        OverclockMode.OverclockResult baseRes = new OverclockMode.OverclockResult(node.getBaseDurationTicks(), node.getBaseEUt(), 1.0, 0);
        double scaleFactor = Math.max(1, node.getCombinedParallelMultiplier());
        double powerMult = Math.max(0.01, node.getCombinedEutMultiplier());
        double fuelEnergyMult = Math.max(0.01, node.getCombinedDurationMultiplier());

        if (isGenerator) {
            double rawDuration = baseRes.durationTicks() * fuelEnergyMult / (scaleFactor * powerMult);
            double finalDuration = Math.max(1.0, rawDuration);
            double batchesPerTick = (rawDuration < 1.0 && rawDuration > 0.0) ? (1.0 / rawDuration) : 1.0;
            double finalEut = Math.max(1.0, baseRes.eut() * scaleFactor * powerMult);
            return new OverclockMode.OverclockResult(finalDuration, finalEut, batchesPerTick, 0);
        } else {
            double rawDuration = baseRes.durationTicks() / (scaleFactor * node.getCombinedDurationMultiplier());
            double finalDuration = Math.max(1.0, rawDuration);
            double batchesPerTick = (rawDuration < 1.0 && rawDuration > 0.0) ? (1.0 / rawDuration) : 1.0;
            double finalEut = Math.max(1.0, baseRes.eut() * scaleFactor * powerMult);
            return new OverclockMode.OverclockResult(finalDuration, finalEut, batchesPerTick, 0);
        }
    }

    @Override
    public String formatEnergyStats(RecipeNode node, PowerDisplayMode displayMode) {
        return ThermalGuiHandler.formatEnergyStats(node, displayMode);
    }

    @Override
    public List<Component> buildEnergyTooltip(RecipeNode node) {
        return ThermalGuiHandler.buildEnergyTooltip(node);
    }

    @Override
    public void renderCardControls(GuiGraphics graphics, Font font,
                                   RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY,
                                   boolean isGlowing) {
        ThermalGuiHandler.renderCardControls(graphics, font, node, x, row2Y, cardW, mouseX, mouseY, isGlowing);
    }

    @Override
    public boolean isTierOrSpeedControlHovered(RecipeNode node, double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        return ThermalGuiHandler.isMachineConfigHovered(node, mouseX, mouseY);
    }

    @Override
    public boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        return ThermalGuiHandler.handleControlClick(widget, node, mouseX, mouseY, button);
    }
}
