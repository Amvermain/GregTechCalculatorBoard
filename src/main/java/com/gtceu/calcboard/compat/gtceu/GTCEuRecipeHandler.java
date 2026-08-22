package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Handles GTCEu recipe reflection and detail extraction (EUt, duration, voltage tier, ebf temp).
 */
public class GTCEuRecipeHandler {

    public static boolean adaptRecipeDetails(Object emiRecipeObj, Object backing, EmiRecipeConverter.RecipeDetails details) {
        if (backing == null) return false;
        String clName = backing.getClass().getName();
        if (!clName.contains("GTRecipe")) return false;

        extractGTRecipeDetails(backing, details);
        return true;
    }

    public static void extractGTRecipeDetails(Object backing, EmiRecipeConverter.RecipeDetails details) {
        try {
            var durationField = backing.getClass().getField("duration");
            details.durationTicks = durationField.getInt(backing);

            try {
                var getOutputEUtMethod = backing.getClass().getMethod("getOutputEUt");
                Object outEnergy = getOutputEUtMethod.invoke(backing);
                if (outEnergy != null) {
                    var voltageMethod = outEnergy.getClass().getMethod("voltage");
                    var amperageMethod = outEnergy.getClass().getMethod("amperage");
                    long voltage = (long) voltageMethod.invoke(outEnergy);
                    long amperage = (long) amperageMethod.invoke(outEnergy);
                    if (voltage > 0) {
                        details.eut = Math.max(1.0, voltage * Math.max(1L, amperage));
                        details.tier = GTVoltageTier.getTierForVoltage(voltage);
                        details.isGenerator = true;
                    }
                }
            } catch (Throwable ignored) {}

            if (!details.isGenerator) {
                try {
                    var getInputEUtMethod = backing.getClass().getMethod("getInputEUt");
                    Object energyStack = getInputEUtMethod.invoke(backing);
                    if (energyStack != null) {
                        var voltageMethod = energyStack.getClass().getMethod("voltage");
                        var amperageMethod = energyStack.getClass().getMethod("amperage");
                        long voltage = (long) voltageMethod.invoke(energyStack);
                        long amperage = (long) amperageMethod.invoke(energyStack);
                        if (voltage > 0) {
                            details.eut = Math.max(1.0, voltage * Math.max(1L, amperage));
                            details.tier = GTVoltageTier.getTierForVoltage(voltage);
                        }
                    }
                } catch (Throwable ignored) {}
            }

            int recipeTemp = 0;
            try {
                Field dataField = backing.getClass().getField("data");
                Object dataObj = dataField.get(backing);
                if (dataObj instanceof CompoundTag tag) {
                    if (tag.contains("ebf_temp")) recipeTemp = tag.getInt("ebf_temp");
                    else if (tag.contains("temp")) recipeTemp = tag.getInt("temp");
                    else if (tag.contains("temperature")) recipeTemp = tag.getInt("temperature");
                }
            } catch (Throwable ignored) {
                try {
                    Method dataMethod = backing.getClass().getMethod("data");
                    Object dataObj = dataMethod.invoke(backing);
                    if (dataObj instanceof CompoundTag tag) {
                        if (tag.contains("ebf_temp")) recipeTemp = tag.getInt("ebf_temp");
                        else if (tag.contains("temp")) recipeTemp = tag.getInt("temp");
                        else if (tag.contains("temperature")) recipeTemp = tag.getInt("temperature");
                    }
                } catch (Throwable ignored2) {}
            }
            if (recipeTemp > 0) {
                details.backingRecipeTemp = recipeTemp;
            }
        } catch (Throwable ignored) {}
    }
}
