package com.gtceu.calcboard.compat.vanilla;

import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.PowerDisplayMode;

import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Generic fallback compatibility adapter for Vanilla Minecraft and non-specialized mods.
 */
public class VanillaModAdapter implements IModAdapter {

    @Override
    public String getModId() {
        return "minecraft";
    }

    @Override
    public int getPriority() {
        return 0; // Lowest priority, acts as universal fallback
    }

    @Override
    public boolean isGenericFallback() {
        return true;
    }

    @Override
    public boolean isLoaded() {
        return true;
    }

    private static final Set<String> VANILLA_CATEGORIES = Set.of(
            "crafting", "smelting", "blasting", "smoking", "campfire_cooking",
            "stonecutting", "smithing", "brewing", "composting", "anvil"
    );

    @Override
    public boolean handlesCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        String ns = categoryId.getNamespace().toLowerCase(Locale.ROOT);
        String path = categoryId.getPath().toLowerCase(Locale.ROOT);
        return ns.equals("minecraft") || VANILLA_CATEGORIES.contains(path);
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        return true;
    }

    @Override
    public EnergyType getEnergyType(RecipeNode node) {
        return EnergyType.NONE;
    }

    @Override
    public boolean supportsAddons(RecipeNode node) {
        return false;
    }

    @Override
    public void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
        // Vanilla has no hardware addons
    }

    @Override
    public void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
        // Standard singleblock capability
    }

    @Override
    public boolean adaptRecipeDetails(Object emiRecipe, Object backingRecipe, EmiRecipeConverter.RecipeDetails details) {
        return false;
    }

    @Override
    public MachineAddon tailorAddon(MachineAddon addon, RecipeNode targetNode) {
        return addon;
    }

    @Override
    public OverclockMode.OverclockResult computeOverclock(RecipeNode node, GTVoltageTier targetTier, boolean isGenerator) {
        if (isGenerator) {
            return new OverclockMode.OverclockResult(node.getBaseDurationTicks(), node.getBaseEUt(), 1.0, 0);
        }
        return node.getOverclockMode().calculate(node.getBaseDurationTicks(), node.getBaseEUt(), node.getTierDelta());
    }

    @Override
    public String formatEnergyStats(RecipeNode node, PowerDisplayMode displayMode) {
        return Component.translatable("gui.gtcalcboard.energy_passive_stat").getString();
    }

    @Override
    public List<Component> buildEnergyTooltip(RecipeNode node) {
        List<Component> tooltipLines = new ArrayList<>();
        tooltipLines.add(Component.literal("§a🍃 " + Component.translatable("gui.gtcalcboard.energy_passive_stat").getString()));
        tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.2fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
        return tooltipLines;
    }
}



