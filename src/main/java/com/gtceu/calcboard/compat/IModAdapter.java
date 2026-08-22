package com.gtceu.calcboard.compat;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Service Provider Interface (SPI) contract for external mod compatibilities.
 * Encapsulates mod-specific recipe parsing, hardware addons, capabilities, overclocking, and composite recipes.
 */
public interface IModAdapter {

    /**
     * Unique mod identifier (e.g. "gtceu", "thermal", "systeams", "mekanism", "minecraft").
     */
    String getModId();

    /**
     * Priority of this adapter (higher runs earlier, default 100).
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Returns true if the backing mod is present and active in the runtime.
     */
    boolean isLoaded();

    /**
     * Returns true if this adapter handles the given recipe category.
     */
    boolean handlesCategory(ResourceLocation categoryId);

    /**
     * Returns true if this adapter handles the given RecipeNode.
     */
    boolean handlesNode(RecipeNode node);

    /**
     * Discovers all hardware addons, coils, rotors, augment items provided by this mod.
     */
    void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks);

    /**
     * Injects mod-specific capability definitions into the capability matrix.
     */
    void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager);

    /**
     * Adapts or transforms recipe details (e.g. composite recipes, water+fuel -> steam, custom power/duration).
     * Returns true if this adapter processed and customized the details.
     */
    default boolean adaptRecipeDetails(Object emiRecipe, Object backingRecipe, EmiRecipeConverter.RecipeDetails details) {
        return false;
    }

    /**
     * Returns whether the given machine node supports installing hardware addons.
     */
    default boolean supportsAddons(RecipeNode node) {
        return !getApplicableAddonCategories(node).isEmpty();
    }

    /**
     * Returns the list of applicable addon categories for the given machine node.
     */
    default List<MachineAddon.Category> getApplicableAddonCategories(RecipeNode node) {
        return List.of();
    }

    /**
     * Validates whether a specific addon can be installed on the given machine node.
     */
    default boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory() == MachineAddon.Category.CUSTOM) return true;
        return getApplicableAddonCategories(node).contains(addon.getCategory());
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
     * Formats the power / energy / stress summary string for the bottom stats line of a node card.
     */
    default String formatEnergyStats(RecipeNode node, PowerDisplayMode displayMode) {
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
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        return mouseX >= x + 6 && mouseX <= x + 38 && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    /**
     * Checks if the secondary mode button (e.g. Overclock Mode STD/PERF) is hovered.
     */
    default boolean isSecondaryControlHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node.isGenerator()) return false;
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
}
