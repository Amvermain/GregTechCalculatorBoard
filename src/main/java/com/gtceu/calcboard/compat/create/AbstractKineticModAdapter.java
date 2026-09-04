package com.gtceu.calcboard.compat.create;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.PowerDisplayMode;
import com.gtceu.calcboard.compat.IModAdapter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Abstract base mod adapter for kinetic rotational energy (KINETIC_SU) machines and generators.
 * Encapsulates RPM-based overclock calculations, stress unit formatting, and tooltip generation.
 */
public abstract class AbstractKineticModAdapter implements IModAdapter {

    private static final Set<String> FAN_PROCESSING_PATHS = Set.of(
            "splashing", "washing", "haunting", "smoking", "blasting"
    );

    private static final Set<String> FAN_ICON_PATHS = Set.of(
            "encased_fan", "fan"
    );

    public static boolean isFanProcessingRecipe(RecipeNode node) {
        if (node == null) return false;
        ResourceLocation catId = node.getRecipeCategoryId();
        if (catId != null && FAN_PROCESSING_PATHS.contains(catId.getPath().toLowerCase(Locale.ROOT))) {
            return true;
        }
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null && FAN_ICON_PATHS.contains(icon.getPath().toLowerCase(Locale.ROOT))) {
            return true;
        }
        return false;
    }

    public static ResourceLocation getEmiCategoryId(Object emiRecipe) {
        if (emiRecipe == null || !com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) return null;
        return EmiHelper.extractCategoryId(emiRecipe);
    }

    private static class EmiHelper {
        private static ResourceLocation extractCategoryId(Object emiRecipe) {
            if (emiRecipe instanceof dev.emi.emi.api.recipe.EmiRecipe recipe && recipe.getCategory() != null) {
                return recipe.getCategory().getId();
            }
            return null;
        }
    }

    @Override
    public EnergyType getEnergyType(RecipeNode node) {
        return EnergyType.KINETIC_SU;
    }

    @Override
    public OverclockMode.OverclockResult computeOverclock(RecipeNode node, GTVoltageTier targetTier, boolean isGenerator) {
        int rpm = node.getRpm();
        double baseDuration = node.getBaseDurationTicks();
        double basePower = node.getBaseEUt();

        boolean isFanProcessing = isFanProcessingRecipe(node);
        double speedFactor = Math.max(0.01, rpm / 32.0);

        double durationTicks;
        double batchesPerTick;
        if (isFanProcessing || isGenerator) {
            durationTicks = Math.max(1.0, baseDuration);
            batchesPerTick = 1.0;
        } else {
            double rawDuration = baseDuration / speedFactor;
            durationTicks = Math.max(1.0, rawDuration);
            batchesPerTick = (rawDuration < 1.0 && rawDuration > 0.0) ? (1.0 / rawDuration) : 1.0;
        }

        double effectivePower;
        if (isGenerator) {
            int baseGenRpm = node.getProperties().get(CreateProperties.BASE_GENERATOR_RPM);
            if (baseGenRpm > 0 && rpm > 0) {
                effectivePower = basePower * ((double) rpm / baseGenRpm);
            } else {
                effectivePower = basePower;
            }
        } else {
            effectivePower = basePower * speedFactor;
        }

        return new OverclockMode.OverclockResult(durationTicks, effectivePower, batchesPerTick, 0);
    }

    @Override
    public String formatEnergyStats(RecipeNode node, PowerDisplayMode displayMode) {
        if (node == null) return "";
        double suRate = node.getEffectiveTotalEUt();
        return node.isGenerator()
                ? String.format(Locale.ROOT, "§6+%,.0f SU", suRate)
                : String.format(Locale.ROOT, "§e%,.0f SU", suRate);
    }

    @Override
    public List<Component> buildEnergyTooltip(RecipeNode node) {
        List<Component> tooltipLines = new ArrayList<>();
        if (node == null) return tooltipLines;
        double totSU = node.getEffectiveTotalEUt();
        if (node.isGenerator()) {
            tooltipLines.add(Component.literal("§6⚙ " + Component.translatable("gui.gtcalcboard.total_gen").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Capacity: §6+%,.0f SU", totSU)));
        } else {
            tooltipLines.add(Component.literal("§e⚙ " + Component.translatable("gui.gtcalcboard.total_power").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Stress Impact: §e%,.0f SU", totSU)));
        }
        tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Rotation Speed: §6%d RPM", node.getRpm())));
        tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
        if (isFanProcessingRecipe(node)) {
            tooltipLines.add(Component.translatable("gui.gtcalcboard.tooltip.fan_fixed_duration_hint"));
        }
        return tooltipLines;
    }
}
