package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.RecipeNode;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

public class EmiRecipeConverter {

    public static RecipeNode convert(EmiRecipe recipe) {
        String name = recipe.getId() != null ? recipe.getId().getPath() : "Recipe";
        // Clean name
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf('/') + 1);
        }
        name = formatName(name);

        double baseDurationTicks = 100.0; // Default 5s
        double baseEUt = 30.0;           // Default LV
        GTVoltageTier tier = GTVoltageTier.LV;

        // Try extracting real GTRecipe details
        try {
            var backing = recipe.getBackingRecipe();
            if (backing != null) {
                // If it's a GTRecipe
                if (backing.getClass().getName().contains("GTRecipe")) {
                    // Extract duration
                    var durationField = backing.getClass().getField("duration");
                    baseDurationTicks = durationField.getInt(backing);

                    // Extract input EU/t
                    var getInputEUtMethod = backing.getClass().getMethod("getInputEUt");
                    Object energyStack = getInputEUtMethod.invoke(backing);
                    if (energyStack != null) {
                        var voltageMethod = energyStack.getClass().getMethod("voltage");
                        var amperageMethod = energyStack.getClass().getMethod("amperage");
                        long voltage = (long) voltageMethod.invoke(energyStack);
                        long amperage = (long) amperageMethod.invoke(energyStack);
                        baseEUt = Math.max(1.0, voltage * Math.max(1L, amperage));
                        tier = GTVoltageTier.getTierForVoltage(voltage);
                    }
                }
            }
        } catch (Throwable t) {
            // Fallback
        }

        RecipeNode node = RecipeNode.create(name, baseDurationTicks, baseEUt, tier);

        // Convert Inputs
        for (EmiIngredient input : recipe.getInputs()) {
            for (EmiStack stack : input.getEmiStacks()) {
                IngredientStack is = convertEmiStack(stack, stack.getAmount(), stack.getChance());
                if (is != null) {
                    node.addInput(is);
                    break;
                }
            }
        }

        // Convert Outputs
        for (EmiStack outStack : recipe.getOutputs()) {
            IngredientStack os = convertEmiStack(outStack, outStack.getAmount(), outStack.getChance());
            if (os != null) {
                node.addOutput(os);
            }
        }

        return node;
    }

    private static IngredientStack convertEmiStack(EmiStack stack, long amount, float chance) {
        if (stack.isEmpty()) return null;

        ResourceLocation id = stack.getId();
        String displayName = stack.getName().getString();

        Object key = stack.getKey();
        if (key instanceof Fluid fluid) {
            ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluid);
            return IngredientStack.fluid(fluidId != null ? fluidId : id, displayName, amount, chance);
        } else {
            return IngredientStack.item(id, displayName, amount, chance);
        }
    }

    private static String formatName(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown Machine";
        String[] parts = raw.split("[_\\-.]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
