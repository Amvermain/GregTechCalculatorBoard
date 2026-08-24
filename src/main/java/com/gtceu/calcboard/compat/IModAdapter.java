package com.gtceu.calcboard.compat;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Service Provider Interface (SPI) for external mod integration.
 * Encapsulates recipe parsing, hardware addons, capability matrices, overclocking, and compound recipe handling.
 */
public interface IModAdapter {

    /**
     * Unique mod identifier (e.g. "gtceu", "thermal", "systeams", "mekanism", "minecraft").
     */
    String getModId();

    /**
     * Adapter priority (higher values evaluated first, default 100).
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Checks if the target mod is currently loaded and active in the runtime environment.
     */
    boolean isLoaded();

    /**
     * Checks if this adapter handles the specified recipe category.
     */
    boolean handlesCategory(ResourceLocation categoryId);

    /**
     * Checks if this adapter handles the specified recipe node.
     */
    boolean handlesNode(RecipeNode node);

    /**
     * Discovers and registers hardware addons (coils, rotors, reflectors, augments) provided by the mod.
     */
    void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks);

    /**
     * Injects mod-specific capability matrix definitions.
     */
    void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager);

    /**
     * Adapts compound recipes (e.g. water+fuel -> steam) or mod-specific energy/duration rules.
     * Returns true if custom handling was applied.
     */
    default boolean adaptRecipeDetails(Object emiRecipe, Object backingRecipe, EmiRecipeConverter.RecipeDetails details) {
        return false;
    }

    /**
     * Checks if the specified machine node supports installing hardware addons.
     */
    default boolean supportsAddons(RecipeNode node) {
        return !getApplicableAddonCategories(node).isEmpty();
    }

    /**
     * Returns the list of applicable addon categories for the specified node.
     */
    default List<AddonCategory> getApplicableAddonCategories(RecipeNode node) {
        return List.of();
    }

    /**
     * Checks if a specific addon is compatible with the target machine node.
     */
    default boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory().equals(AddonCategory.CUSTOM)) return true;
        return getApplicableAddonCategories(node).contains(addon.getCategory());
    }

    /**
     * Checks if the addon can be installed considering slot limits and duplicate rules.
     */
    default boolean canInstallAddon(RecipeNode node, MachineAddon addon) {
        return isAddonCompatible(node, addon);
    }

    /**
     * Lifecycle hook when an addon is installed onto a node (e.g. replacing existing coil/rotor/reflector).
     */
    default void onAddonInstalled(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        if (addon.getCategory() == MachineAddon.Category.COIL ||
            addon.getCategory() == MachineAddon.Category.ROTOR ||
            addon.getCategory() == MachineAddon.Category.REFLECTOR) {
            node.getAddons().removeIf(a -> a.getCategory() == addon.getCategory());
        }
        node.getAddons().add(addon);
    }

    /**
     * Lifecycle hook when an addon is removed from a node.
     */
    default void onAddonRemoved(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
    }

    /**
     * Returns special reset/default cards in the machine config dialog for the node (e.g. standard 100% rotor).
     */
    default List<MachineAddon> getResetAddonCards(RecipeNode node) {
        return List.of();
    }

    /**
     * Installs an addon onto the machine node (handles slots, replacement, shift-click filling).
     */
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

    /**
     * Uninstalls an addon or removes a copy from the machine node.
     */
    default void handleUninstallAddon(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        if (addon.getCategory().equals(AddonCategory.THREADING)) {
            com.gtceu.calcboard.api.GTThreadingHelix helix = com.gtceu.calcboard.api.GTThreadingHelix.fromId(addon.getId());
            if (helix != null && node.getThreadingConfig() != null) {
                node.getThreadingConfig().setHelixCount(helix, 0);
            }
        }
        onAddonRemoved(node, addon);
        node.removeAddon(addon.getId());
    }

    /**
     * Checks if the addon is considered installed on the node.
     */
    default boolean isAddonInstalled(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        return node.getAddons().stream().anyMatch(a -> a.getId().equals(addon.getId()));
    }

    /**
     * Gets the installed count of this addon on the node.
     */
    default int getAddonInstalledCount(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return 0;
        return (int) node.getAddons().stream().filter(a -> a.getId().equals(addon.getId())).count();
    }

    /**
     * Formats the subtitle text for an addon in the catalog grid (e.g. "Magnetic Force: 24x", "Parallel: 4x").
     */
    default String formatAddonSubtitle(RecipeNode node, MachineAddon addon) {
        if (addon == null) return "";
        if (addon.getCategory().equals(AddonCategory.MAGNET)) {
            return String.format("Magnetic Force: %dx", addon.getMagneticForce());
        }
        if (addon.getCategory().equals(AddonCategory.PARALLEL)) {
            return String.format("Parallel: %dx", addon.getParallelMultiplier());
        }
        if (addon.getCategory().equals(AddonCategory.COIL)) {
            return String.format("Coil: %d K", addon.getCoilTemperature());
        }
        if (addon.getCategory().equals(AddonCategory.ROTOR)) {
            return String.format("Power: %d%%", addon.getRotorPower());
        }
        return addon.getName();
    }

    /**
     * Builds the hover tooltip lines for an addon in the MachineConfigDialog (both active and catalog views).
     */
    default void buildAddonTooltip(RecipeNode node, MachineAddon addon, boolean isActiveAddon, List<net.minecraft.network.chat.Component> tooltip) {
        if (addon == null || tooltip == null) return;
        if (addon.getParallelMultiplier() > 1) {
            tooltip.add(net.minecraft.network.chat.Component.literal(String.format("§b⚡ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.addon.parallel", addon.getParallelMultiplier()).getString())));
        }
        if (addon.getDurationMultiplier() != 1.0) {
            tooltip.add(net.minecraft.network.chat.Component.literal(String.format("§a⏳ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.addon.speed_boost", String.format(java.util.Locale.ROOT, "%.2fx", 1.0 / addon.getDurationMultiplier())).getString())));
        }
        if (addon.getEutMultiplier() != 1.0 && !addon.getCategory().equals(AddonCategory.MAGNET)) {
            tooltip.add(net.minecraft.network.chat.Component.literal(String.format("§e⚡ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.addon.eut_multiplier", String.format(java.util.Locale.ROOT, "%.2fx", addon.getEutMultiplier())).getString())));
        }
    }

    /**
     * Formats the badge text displayed on the top/bottom corner of an addon card in the catalog grid.
     */
    default String formatAddonBadge(RecipeNode node, MachineAddon addon) {
        if (addon == null) return "";
        if (addon.getCategory() == MachineAddon.Category.REFLECTOR) {
            return String.format("§b🪞 Tier %d", addon.getReflectorTier());
        }
        if (addon.getCategory() == MachineAddon.Category.COIL) {
            return String.format("§6♨ %d K", addon.getCoilTemperature());
        }
        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            return String.format("§a⚙ %d%%", addon.getRotorEfficiency());
        }
        if (addon.getCategory() == MachineAddon.Category.PARALLEL) {
            return String.format("§b%dx Par", addon.getParallelMultiplier());
        }
        return "";
    }

    /**
     * Validates node operating conditions (reflector tier, coil temperature, etc.) and fills warning list.
     * Returns true if all prerequisites are met.
     */
    default boolean validateNode(RecipeNode node, List<net.minecraft.network.chat.Component> warnings) {
        return true;
    }

    /**
     * Validates node operating conditions including graph flow state.
     */
    default boolean validateNode(RecipeNode node, FlowGraph graph, List<net.minecraft.network.chat.Component> warnings) {
        return validateNode(node, warnings);
    }

    /**
     * Tailors an addon for a specific machine node (e.g. coil energy discounts, thermal augment scaling).
     */
    default MachineAddon tailorAddon(MachineAddon addon, RecipeNode targetNode) {
        return addon;
    }

    /**
     * Calculates overclock and effective duration/power for the given node.
     */
    default OverclockMode.OverclockResult computeOverclock(RecipeNode node, GTVoltageTier targetTier, boolean isGenerator) {
        if (isGenerator) {
            return new OverclockMode.OverclockResult(node.getBaseDurationTicks(), node.getBaseEUt(), 1.0, 0);
        }
        return node.getOverclockMode().calculate(node.getBaseDurationTicks(), node.getBaseEUt(), node.getTierDelta());
    }

    /**
     * Computes the single machine operating power (EU/t, FE/t, SU) while running.
     */
    default double computeSingleMachinePower(RecipeNode node) {
        if (!node.isOperational()) return 0.0;
        if (node.isGenerator()) {
            return computeOverclock(node, node.getTargetTier(), true).eut() * computeEffectiveParallel(node);
        }
        return computeOverclock(node, node.getTargetTier(), false).eut() * node.getCombinedEutMultiplier();
    }

    /**
     * Computes total effective parallel execution multiplier for this machine node.
     */
    default int computeEffectiveParallel(RecipeNode node) {
        return Math.max(1, node.getParallel() * node.getCombinedParallelMultiplier());
    }

    /**
     * Automatically calculates and tunes optimal parallel count (e.g. for turbines/generators).
     */
    default void autoTuneParallel(RecipeNode node) {
        // Default no-op
    }

    /**
     * Checks if this node supports steam operational modes (LP/HP steam).
     */
    default boolean supportsSteamMode(RecipeNode node) {
        return false;
    }

    /**
     * Handles steam mode lifecycle changes (slot injection/removal and workstation icon sync).
     */
    default void onSteamModeChanged(RecipeNode node, SteamMode oldMode, SteamMode newMode) {
        // Default no-op
    }

    /**
     * Checks if this recipe node represents a boiler recipe.
     */
    default boolean isBoilerRecipe(RecipeNode node) {
        return false;
    }

    /**
     * Checks if this recipe node represents a liquid-fueled boiler recipe.
     */
    default boolean isLiquidBoilerRecipe(RecipeNode node) {
        return false;
    }

    /**
     * Formats the power / energy / stress summary string for the bottom stats line of a node card.
     */
    default String formatEnergyStats(RecipeNode node, PowerDisplayMode displayMode) {
        if (node.getEnergyType() == EnergyType.NONE) {
            return net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.energy_passive_stat").getString();
        }
        if (node.getEnergyType() == EnergyType.HEAT_OR_SELF) {
            return "§6♨";
        }
        String eutStr = displayMode.formatNodePower(node);
        return (node.getEfficiency() < 0.999)
                ? String.format("§e⚡%.0f%% %s", node.getEfficiency() * 100.0, eutStr)
                : eutStr;
    }

    /**
     * Builds the detailed hover tooltip for the bottom energy / stats line of a node card.
     */
    default List<net.minecraft.network.chat.Component> buildEnergyTooltip(RecipeNode node) {
        List<net.minecraft.network.chat.Component> tooltipLines = new java.util.ArrayList<>();
        if (node.isGenerator()) {
            tooltipLines.add(net.minecraft.network.chat.Component.literal("§a⚡ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.total_gen").getString()));
            double totEUt = node.getEffectiveTotalEUt();
            var tier = node.getTargetTier();
            if (tier == null) tier = com.gtceu.calcboard.api.GTVoltageTier.LV;
            double amps = totEUt / (double) tier.getVoltage();
            tooltipLines.add(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Total Generation: §a+%,.2f EU/t", totEUt)));
            tooltipLines.add(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Current: §a+%,.4fA %s", amps, tier.getName())));
            tooltipLines.add(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
            if (node.getEfficiency() < 0.999) {
                tooltipLines.add(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§e⚡ Rotor Efficiency: §f%.1f%%", node.getEfficiency() * 100.0)));
            }
        } else {
            tooltipLines.add(net.minecraft.network.chat.Component.literal("§e⚡ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.total_power").getString()));
            double totEUt = node.getEffectiveTotalEUt();
            var tier = node.getTargetTier();
            if (tier == null) tier = com.gtceu.calcboard.api.GTVoltageTier.LV;
            double amps = totEUt / (double) tier.getVoltage();
            tooltipLines.add(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Total Consumption: §c%,.2f EU/t", totEUt)));
            tooltipLines.add(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Current: §e%,.4fA %s", amps, tier.getName())));
            tooltipLines.add(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
        }
        return tooltipLines;
    }

    /**
     * Renders row 2 machine controls (tiers, speeds, overclock modes, generator badges, parallel/hardware buttons)
     * for nodes handled by this mod adapter.
     */
    default void renderCardControls(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                   RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY,
                                   boolean isGlowing) {
        // Default GT/Electric Controls
        GTVoltageTier tier = node.getTargetTier();
        com.gtceu.calcboard.client.gui.NodeCardRenderer.drawBtn(graphics, font, tier.getName(), x + 6, row2Y, 32, 14, mouseX, mouseY, tier.getColor());

        int nextCtrlX = x + 42;
        if (node.isGenerator()) {
            String genBadge = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.gen_badge").getString();
            int genW = Math.max(28, font.width(genBadge) + 4);
            com.gtceu.calcboard.client.gui.NodeCardRenderer.drawBtn(graphics, font, genBadge, nextCtrlX, row2Y, genW, 14, mouseX, mouseY, 0xFF55FF88);
            nextCtrlX += genW + 3;

            String dynamoPar = "⚙ " + node.getParallel() + "x";
            if (!node.getAddons().isEmpty()) {
                dynamoPar += " (+" + node.getAddons().size() + ")";
            }
            int parW = Math.max(46, (x + cardW - 6) - nextCtrlX);
            com.gtceu.calcboard.client.gui.NodeCardRenderer.drawBtn(graphics, font, dynamoPar, nextCtrlX, row2Y, parW, 14, mouseX, mouseY, !node.getAddons().isEmpty() ? 0xFF55FFFF : 0xFF58D3FF, isGlowing);
        } else {
            String ocKey = node.getOverclockMode() == OverclockMode.PERFECT ? "gui.gtcalcboard.oc_perf" : "gui.gtcalcboard.oc_std";
            String ocText = net.minecraft.network.chat.Component.translatable(ocKey).getString();
            int ocColor = node.getOverclockMode() == OverclockMode.PERFECT ? 0xFF55FF55 : 0xFFAAAAAA;
            int ocW = Math.max(50, font.width(ocText) + 6);
            com.gtceu.calcboard.client.gui.NodeCardRenderer.drawBtn(graphics, font, ocText, nextCtrlX, row2Y, ocW, 14, mouseX, mouseY, ocColor);
            nextCtrlX += ocW + 3;

            String parLabel = "⚙ " + node.getTotalParallel() + "x";
            if (!node.getAddons().isEmpty()) {
                parLabel += " (+" + node.getAddons().size() + ")";
            }
            int parW = Math.max(46, (x + cardW - 6) - nextCtrlX);
            com.gtceu.calcboard.client.gui.NodeCardRenderer.drawBtn(graphics, font, parLabel, nextCtrlX, row2Y, parW, 14, mouseX, mouseY, !node.getAddons().isEmpty() ? 0xFF55FFFF : 0xFF58D3FF, isGlowing);
        }
    }

    /**
     * Checks if the primary tier / speed selector button on row 2 is hovered.
     */
    default boolean isTierOrSpeedControlHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node.getEnergyType() == com.gtceu.calcboard.api.EnergyType.NONE) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        int btnW = 32;
        if (node.getEnergyType() == com.gtceu.calcboard.api.EnergyType.HEAT_OR_SELF) {
            com.gtceu.calcboard.api.GTBoilerTier bTier = com.gtceu.calcboard.api.GTBoilerTier.getBoilerTier(node);
            btnW = Math.max(54, net.minecraft.client.Minecraft.getInstance().font.width(bTier.getDisplayName()) + 8);
        } else if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            btnW = Math.max(48, net.minecraft.client.Minecraft.getInstance().font.width(node.getSteamMode().getDisplayName()) + 8);
        }
        return mouseX >= x + 6 && mouseX <= x + 6 + btnW && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    /**
     * Checks if the secondary mode button (e.g. Overclock Mode STD/PERF) is hovered.
     */
    default boolean isSecondaryControlHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node.isGenerator() || node.isFusion() || node.getEnergyType() == com.gtceu.calcboard.api.EnergyType.HEAT_OR_SELF || node.getEnergyType() == com.gtceu.calcboard.api.EnergyType.NONE || (node.getSteamMode() != null && node.getSteamMode().isSteam())) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        int ocX = x + 42;
        int ocW = Math.max(50, net.minecraft.client.Minecraft.getInstance().font.width("STD OC") + 6);
        return mouseX >= ocX && mouseX <= ocX + ocW && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    /**
     * Checks if the machine configuration / parallel / hardware button on row 2 is hovered.
     */
    default boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node.getEnergyType() == com.gtceu.calcboard.api.EnergyType.NONE) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        int configStartX;
        if (node.isGenerator()) {
            String genBadge = net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.gen_badge").getString();
            int genW = Math.max(28, net.minecraft.client.Minecraft.getInstance().font.width(genBadge) + 4);
            configStartX = x + 42 + genW + 3;
        } else {
            String ocKey = node.getOverclockMode() == OverclockMode.PERFECT ? "gui.gtcalcboard.oc_perf" : "gui.gtcalcboard.oc_std";
            String ocText = net.minecraft.network.chat.Component.translatable(ocKey).getString();
            int ocW = Math.max(50, net.minecraft.client.Minecraft.getInstance().font.width(ocText) + 6);
            configStartX = x + 42 + ocW + 3;
        }
        return mouseX >= configStartX && mouseX <= x + node.getCardWidth() - 6 && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    /**
     * Handles clicking on row 2 controls for this node.
     * Returns true if handled.
     */
    default boolean handleControlClick(com.gtceu.calcboard.client.gui.NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        if (isTierOrSpeedControlHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            int direction = (button == 1) ? -1 : 1;
            return widget.changeTier(direction);
        }

        if (isSecondaryControlHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            OverclockMode oldOc = node.getOverclockMode();
            OverclockMode newOc = (oldOc == OverclockMode.STANDARD) ? OverclockMode.PERFECT : OverclockMode.STANDARD;
            node.setOverclockMode(newOc);
            if (widget.getParent() != null) {
                widget.getParent().recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.overclockMode(node.getId(), oldOc, newOc));
            }
            widget.invalidateCache();
            return true;
        }

        if (isMachineConfigHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            if (widget.getParent() != null) {
                widget.getParent().openMachineConfigDialog(node);
            }
            return true;
        }

        return false;
    }

    /**
     * Handles mouse wheel scrolling on row 2 controls for this node.
     * Returns true if handled.
     */
    default boolean handleControlScroll(com.gtceu.calcboard.client.gui.NodeWidget widget, RecipeNode node, double mouseX, double mouseY, double delta) {
        if (isTierOrSpeedControlHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            return widget.changeTier(delta > 0 ? 1 : -1);
        }
        return false;
    }

    /**
     * Renders Section 1 (Top Header / Machine Base Settings) inside MachineConfigDialog.
     */
    default void renderDialogHeader(net.minecraft.client.gui.GuiGraphics graphics,
                                    net.minecraft.client.gui.Font font,
                                    RecipeNode node, int x, int y, int dialogW,
                                    int mouseX, int mouseY, float partialTicks,
                                    net.minecraft.client.gui.components.EditBox parallelBox,
                                    com.gtceu.calcboard.client.gui.BoardScreen parent) {
        if (!node.isMultiblock() && !supportsAddons(node)) {
            graphics.drawString(font, "§b" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.config.singleblock_parallel_fixed").getString(), x + 10, y + 32, 0xFFFFFFFF, false);
            graphics.drawString(font, "§8" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.config.singleblock_parallel_desc").getString(), x + 10, y + 48, 0xFF888888, false);
        } else {
            int curPar = node.getParallel();
            String parLabel = "§b" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.config.base_parallel", String.valueOf(curPar)).getString()
                    + " §7(" + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.config.total_effective", String.valueOf(node.getTotalParallel())).getString() + "§7)";
            graphics.drawString(font, parLabel, x + 10, y + 30, 0xFFFFFFFF, false);

            if (parallelBox != null) {
                parallelBox.setX(x + 10);
                parallelBox.setY(y + 44);
                parallelBox.render(graphics, mouseX, mouseY, partialTicks);
            }

            int[] quickPars = {1, 4, 16, 64, 256};
            int btnX = x + 64;
            for (int qp : quickPars) {
                boolean active = curPar == qp;
                boolean hov = mouseX >= btnX && mouseX <= btnX + 34 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(btnX, y + 44, btnX + 34, y + 60, active ? 0xFF285078 : (hov ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, y + 44, 34, 16, active ? 0xFF589CFF : 0xFF3F4658);
                graphics.drawCenteredString(font, qp + "x", btnX + 17, y + 48, active ? 0xFF58D3FF : 0xFFB0B8C8);
                btnX += 38;
            }

            if (node.isGenerator()) {
                int autoBtnX = x + dialogW - 98;
                boolean autoHov = mouseX >= autoBtnX && mouseX <= autoBtnX + 90 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(autoBtnX, y + 44, autoBtnX + 90, y + 60, autoHov ? 0xFF285078 : 0xFF282D3B);
                graphics.renderOutline(autoBtnX, y + 44, 90, 16, autoHov ? 0xFF589CFF : 0xFF3F4658);
                graphics.drawCenteredString(font, "⚡ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.config.auto_parallel").getString(), autoBtnX + 45, y + 48, 0xFF58D3FF);
            }
        }
    }

    /**
     * Handles clicks on Section 1 controls inside MachineConfigDialog.
     */
    default boolean handleDialogHeaderClick(com.gtceu.calcboard.client.gui.MachineConfigDialog dialog,
                                           RecipeNode node, int x, int y, int dialogW,
                                           double mouseX, double mouseY, int button,
                                           net.minecraft.client.gui.components.EditBox parallelBox,
                                           com.gtceu.calcboard.client.gui.BoardScreen parent) {
        if (!node.isMultiblock() && !supportsAddons(node)) {
            return false;
        }
        int[] quickPars = {1, 4, 16, 64, 256};
        int btnX = x + 64;
        for (int qp : quickPars) {
            if (mouseX >= btnX && mouseX <= btnX + 34 && mouseY >= y + 44 && mouseY <= y + 60) {
                node.setParallel(qp);
                if (parallelBox != null) parallelBox.setValue(String.valueOf(qp));
                if (parent != null) parent.markSummaryDirty();
                net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                );
                return true;
            }
            btnX += 38;
        }
        if (node.isGenerator()) {
            int autoBtnX = x + dialogW - 98;
            if (mouseX >= autoBtnX && mouseX <= autoBtnX + 90 && mouseY >= y + 44 && mouseY <= y + 60) {
                node.autoCalculateTurbineParallel();
                if (parallelBox != null) parallelBox.setValue(String.valueOf(node.getParallel()));
                if (parent != null) parent.markSummaryDirty();
                net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                );
                return true;
            }
        }
        return false;
    }
}
