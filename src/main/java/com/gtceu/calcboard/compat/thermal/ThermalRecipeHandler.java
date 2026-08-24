package com.gtceu.calcboard.compat.thermal;

import com.gtceu.calcboard.api.EnergyType;
import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Handles Thermal recipe reflection, energy (RF) extraction, and dynamo/machine classification.
 */
public class ThermalRecipeHandler {

    public static boolean adaptRecipeDetails(Object emiRecipeObj, Object backing, EmiRecipeConverter.RecipeDetails details) {
        long energyRF = extractEnergyRF(backing);
        if (energyRF <= 0) return false;

        ResourceLocation catId = null;
        if (emiRecipeObj instanceof EmiRecipe recipe && recipe.getCategory() != null) {
            catId = recipe.getCategory().getId();
        }

        // Deductive classification via Recipe class hierarchy and official TagKey
        boolean isDynamo = ThermalAugmentHelper.isDynamoRecipe(backing);
        if (!isDynamo && catId != null) {
            isDynamo = ThermalAugmentHelper.isDynamoItem(catId);
        }

        if (isDynamo) {
            details.isGenerator = true;
            details.energyType = EnergyType.ELECTRIC_FE;
            double basePowerRF = ThermalAugmentHelper.getThermalDynamoBasePowerRF(catId);
            details.eut = basePowerRF; // Base RF/t
            details.durationTicks = Math.max(1.0, (double) energyRF / basePowerRF);
            details.tier = GTVoltageTier.LV;
        } else {
            details.isGenerator = false;
            details.energyType = EnergyType.ELECTRIC_FE;
            double baseMachPowerRF = ThermalAugmentHelper.getThermalMachineBasePowerRF(catId);
            details.eut = baseMachPowerRF; // Base RF/t
            details.durationTicks = Math.max(1.0, (double) energyRF / baseMachPowerRF);
            details.tier = GTVoltageTier.LV;
        }
        return true;
    }

    public static long extractEnergyRF(Object backing) {
        if (backing == null) return 0;
        try {
            Method getEnergyMethod = backing.getClass().getMethod("getEnergy");
            Object res = getEnergyMethod.invoke(backing);
            if (res instanceof Number num) {
                return num.longValue();
            }
        } catch (Throwable ignored) {
            try {
                Field energyField = backing.getClass().getDeclaredField("energy");
                energyField.setAccessible(true);
                Object res = energyField.get(backing);
                if (res instanceof Number num) {
                    return num.longValue();
                }
            } catch (Throwable ignored2) {}
        }
        return 0;
    }
}
