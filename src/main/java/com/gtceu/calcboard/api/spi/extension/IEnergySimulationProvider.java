package com.gtceu.calcboard.api.spi.extension;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.PowerDisplayMode;
import com.gtceu.calcboard.api.type.SteamMode;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Provider interface for energy forms (EU, FE, SU, Steam), overclocking, parallels, and machine power physics.
 */
public interface IEnergySimulationProvider extends IModExtension {

    default EnergyType getEnergyType(RecipeNode node) {
        return EnergyType.ELECTRIC_EU;
    }

    default OverclockMode.OverclockResult computeOverclock(RecipeNode node, GTVoltageTier targetTier, boolean isGenerator) {
        if (node == null) return new OverclockMode.OverclockResult(0, 0, 1.0, 0);
        if (isGenerator) {
            return new OverclockMode.OverclockResult(node.getBaseDurationTicks(), node.getBaseEUt(), 1.0, 0);
        }
        return node.getOverclockMode().calculate(node.getBaseDurationTicks(), node.getBaseEUt(), node.getTierDelta());
    }

    default double computeSingleMachinePower(RecipeNode node) {
        if (node == null || !node.isOperational()) return 0.0;
        if (node.isGenerator()) {
            return computeOverclock(node, node.getTargetTier(), true).eut() * computeEffectiveParallel(node);
        }
        return computeOverclock(node, node.getTargetTier(), false).eut() * node.getCombinedEutMultiplier();
    }

    default int computeEffectiveParallel(RecipeNode node) {
        if (node == null) return 1;
        return Math.max(1, node.getParallel() * node.getCombinedParallelMultiplier());
    }

    default int getDefaultParallel(RecipeNode node) {
        return 1;
    }

    default void autoTuneParallel(RecipeNode node) {
    }

    default boolean supportsSteamMode(RecipeNode node) {
        return false;
    }

    default void onSteamModeChanged(RecipeNode node, SteamMode oldMode, SteamMode newMode) {
    }

    default double computeEffectiveIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        return defaultRate;
    }

    default double computeSingleMachineIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        return defaultRate;
    }

    default int getMaxParallelCapacity(RecipeNode node) {
        return 1;
    }

    default double computeEffectiveOutputChance(RecipeNode node, int outputIndex, double defaultChance) {
        return defaultChance;
    }

    default double computeEffectiveInputChance(RecipeNode node, int inputIndex, double defaultChance) {
        return defaultChance;
    }

    default boolean isBoilerRecipe(RecipeNode node) {
        return false;
    }

    default boolean isLiquidBoilerRecipe(RecipeNode node) {
        return false;
    }

    default boolean isTurbine(RecipeNode node) {
        return false;
    }

    default boolean isLargeTurbine(RecipeNode node) {
        return false;
    }

    default boolean isGenerator(RecipeNode node) {
        return false;
    }

    default double getGeneratorMaxPower(RecipeNode node) {
        return 0.0;
    }

    default String formatEnergyStats(RecipeNode node, PowerDisplayMode displayMode) {
        if (node == null) return "";
        if (node.getEnergyType() == EnergyType.NONE) {
            return Component.translatable("gui.gtcalcboard.energy_passive_stat").getString();
        }
        if (node.getEnergyType() == EnergyType.HEAT_OR_SELF) {
            return (node.getEfficiency() < 0.999)
                    ? String.format(java.util.Locale.ROOT, "§e♨%.0f%%", node.getEfficiency() * 100.0)
                    : "§6♨";
        }
        String eutStr = displayMode != null ? displayMode.formatNodePower(node) : "";
        return (node.getEfficiency() < 0.999)
                ? String.format(java.util.Locale.ROOT, "§e⚡%.0f%% %s", node.getEfficiency() * 100.0, eutStr)
                : eutStr;
    }

    default List<Component> buildEnergyTooltip(RecipeNode node) {
        return List.of();
    }

    default boolean isFusion(RecipeNode node) {
        return false;
    }

    default int getFusionTier(RecipeNode node) {
        return 0;
    }

    default GTVoltageTier getMinFusionVoltageTier(RecipeNode node) {
        return null;
    }

    default GTVoltageTier sanitizeTargetTier(RecipeNode node, GTVoltageTier requestedTier) {
        return requestedTier;
    }
}
