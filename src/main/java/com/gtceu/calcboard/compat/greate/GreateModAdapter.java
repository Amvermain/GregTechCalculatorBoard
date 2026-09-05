package com.gtceu.calcboard.compat.greate;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.CompoundRecipeBuilder;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.create.AbstractKineticModAdapter;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Dedicated Mod Adapter for Greate (Create + GTCEu Modern tiered kinetic machinery).
 * Handles 10-tier machine mapping, Programmed Circuits, and deterministic stress capacity validation.
 */
public class GreateModAdapter extends AbstractKineticModAdapter {

    public static final String MOD_ID = "greate";

    private static final Method GET_RECIPE_TIER_METHOD;
    private static final Method GET_CIRCUIT_NUMBER_METHOD;
    private static final Method GET_PROCESSING_DURATION_METHOD;
    private static final Method GET_REQUIRED_HEAT_METHOD;

    private static final Set<String> HIGH_IMPACT_CATEGORIES = Set.of(
            "pressing", "cutting", "sawing"
    );

    static {
        GreateProperties.init();

        Method getTier = null;
        Method getCircuit = null;
        Method getDuration = null;
        Method getHeat = null;

        try {
            Class<?> tieredRecipeClass = Class.forName("electrolyte.greate.content.processing.recipe.TieredProcessingRecipe");
            getTier = tieredRecipeClass.getMethod("getRecipeTier");
            getCircuit = tieredRecipeClass.getMethod("getCircuitNumber");
            getDuration = tieredRecipeClass.getMethod("getProcessingDuration");
            getHeat = tieredRecipeClass.getMethod("getRequiredHeat");
        } catch (Throwable ignored) {
            // Optional runtime dependency fallback
        }

        GET_RECIPE_TIER_METHOD = getTier;
        GET_CIRCUIT_NUMBER_METHOD = getCircuit;
        GET_PROCESSING_DURATION_METHOD = getDuration;
        GET_REQUIRED_HEAT_METHOD = getHeat;

        GreateBadgeProvider.registerAll();
    }

    @Override
    public String getModId() {
        return MOD_ID;
    }

    @Override
    public int getPriority() {
        return 95;
    }

    @Override
    public boolean isLoaded() {
        try {
            if (ModList.get() != null) {
                return ModList.get().isLoaded(MOD_ID) || !FMLLoader.isProduction();
            }
        } catch (Throwable t) {
            return true;
        }
        return true;
    }

    @Override
    public boolean handlesCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        return MOD_ID.equals(categoryId.getNamespace());
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        if (node == null) return false;
        if (node.getProperties().get(GreateProperties.IS_GREATE)) {
            return true;
        }
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null && MOD_ID.equals(icon.getNamespace())) {
            return true;
        }
        ResourceLocation catId = node.getRecipeCategoryId();
        if (catId != null && MOD_ID.equals(catId.getNamespace())) {
            return true;
        }
        return node.getProperties().has(GreateProperties.REQUIRED_RECIPE_TIER);
    }

    @Override
    public boolean supportsAddons(RecipeNode node) {
        return false;
    }

    @Override
    public List<AddonCategory> getApplicableAddonCategories(RecipeNode node) {
        return List.of();
    }

    @Override
    public boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        return false;
    }

    @Override
    public void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
    }

    @Override
    public boolean adaptRecipeDetails(Object emiRecipe, Object backingRecipe, EmiRecipeConverter.RecipeDetails details) {
        ResourceLocation categoryId = getEmiCategoryId(emiRecipe);
        boolean isGreateCategory = categoryId != null && MOD_ID.equals(categoryId.getNamespace());

        if (backingRecipe == null && !isGreateCategory) {
            return false;
        }

        int recipeTier = backingRecipe != null ? extractRecipeTier(backingRecipe) : 0;
        int circuitNumber = backingRecipe != null ? extractCircuitNumber(backingRecipe) : -1;
        int duration = backingRecipe != null ? extractProcessingDuration(backingRecipe) : 100;
        String heatCondition = backingRecipe != null ? extractHeatCondition(backingRecipe) : "NONE";

        int clampedTier = Math.max(0, Math.min(recipeTier, 9));
        GTVoltageTier voltageTier = GTVoltageTier.getByIndex(clampedTier);

        details.energyType = EnergyType.KINETIC_SU;
        details.tier = voltageTier;
        details.durationTicks = Math.max(1, duration);
        details.circuitNumber = circuitNumber;
        details.heatCondition = heatCondition;

        double baseStress = calculateBaseStress(categoryId, clampedTier);
        details.eut = baseStress;

        double durationSec = details.durationTicks / 20.0;
        double suPerBatch = baseStress * durationSec;
        details.extraInputs.add(IngredientStack.stressUnit(suPerBatch));

        return true;
    }

    @Override
    public boolean validateNode(RecipeNode node, List<Component> warnings) {
        if (node == null) return true;
        boolean valid = true;

        int machineTier = Math.max(0, node.getProperties().get(GreateProperties.MACHINE_TIER));
        int requiredTier = Math.max(0, node.getProperties().get(GreateProperties.REQUIRED_RECIPE_TIER));
        if (machineTier < requiredTier) {
            valid = false;
            if (warnings != null) {
                warnings.add(Component.translatable(
                        "gui.gtcalcboard.greate.tier_mismatch_warning",
                        GreateProperties.getTierName(machineTier),
                        GreateProperties.getTierName(requiredTier)
                ));
            }
        }

        double shaftCapacity = GreateProperties.getShaftCapacityForTier(machineTier);
        double singleMachineStress = computeOverclock(node, node.getTargetTier(), false).eut() * node.getCombinedEutMultiplier();
        if (singleMachineStress > shaftCapacity) {
            valid = false;
            if (warnings != null) {
                warnings.add(Component.translatable(
                        "gui.gtcalcboard.greate.shaft_capacity_warning",
                        String.format(Locale.ROOT, "%,.0f", singleMachineStress),
                        String.format(Locale.ROOT, "%,.0f", shaftCapacity)
                ));
            }
        }

        return valid;
    }

    @Override
    public String formatEnergyStats(RecipeNode node, com.gtceu.calcboard.api.type.PowerDisplayMode displayMode) {
        if (node == null) return "";
        double suRate = node.getEffectiveTotalEUt();
        int tier = Math.max(0, node.getProperties().get(GreateProperties.MACHINE_TIER));
        double capacity = GreateProperties.getShaftCapacityForTier(tier) * Math.max(1.0, node.getMachineCount());

        if (node.isGenerator()) {
            return String.format(Locale.ROOT, "§6+%,.0f / %,.0f SU", suRate, capacity);
        }
        return String.format(Locale.ROOT, "§e%,.0f / %,.0f SU", suRate, capacity);
    }

    @Override
    public List<Component> buildEnergyTooltip(RecipeNode node) {
        List<Component> tooltipLines = new ArrayList<>();
        if (node == null) return tooltipLines;
        double totSU = node.getEffectiveTotalEUt();
        int tier = Math.max(0, node.getProperties().get(GreateProperties.MACHINE_TIER));
        String tierName = GreateProperties.getTierName(tier);
        double cap = GreateProperties.getShaftCapacityForTier(tier) * Math.max(1.0, node.getMachineCount());

        if (node.isGenerator()) {
            tooltipLines.add(Component.literal("§6⚙ " + Component.translatable("gui.gtcalcboard.total_gen").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Output: §6+%,.0f SU", totSU)));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Shaft Limit: §f%,.0f SU §7(%s)", cap, tierName)));
        } else {
            tooltipLines.add(Component.literal("§e⚙ " + Component.translatable("gui.gtcalcboard.total_power").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Stress Impact: §e%,.0f SU", totSU)));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Shaft Capacity: §f%,.0f SU §7(%s)", cap, tierName)));
            if (totSU > cap) {
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§c⚠ " + Component.translatable("gui.gtcalcboard.greate.stress_deficit", String.format(Locale.ROOT, "%,.0f", totSU - cap)).getString())));
            } else {
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§a✔ " + Component.translatable("gui.gtcalcboard.greate.stress_headroom", String.format(Locale.ROOT, "%,.0f", cap - totSU)).getString())));
            }
        }
        tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Rotation Speed: §6%d RPM", node.getRpm())));
        tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
        if (isFanProcessingRecipe(node)) {
            tooltipLines.add(Component.translatable("gui.gtcalcboard.tooltip.fan_fixed_duration_hint"));
        }
        return tooltipLines;
    }

    @Override
    public CompoundRecipeBuilder.CompoundCluster buildCompoundRecipe(
            Object recipeObj,
            Object backingRecipe,
            ResourceLocation preferredWorkstation,
            double startX,
            double startY
    ) {
        return null;
    }

    @Override
    public void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
    }

    private static int extractRecipeTier(Object backingRecipe) {
        if (GET_RECIPE_TIER_METHOD != null) {
            try {
                return (int) GET_RECIPE_TIER_METHOD.invoke(backingRecipe);
            } catch (Throwable ignored) {
            }
        }
        try {
            Method m = backingRecipe.getClass().getMethod("getRecipeTier");
            return (int) m.invoke(backingRecipe);
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Field f = backingRecipe.getClass().getDeclaredField("recipeTier");
            f.setAccessible(true);
            return f.getInt(backingRecipe);
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static int extractCircuitNumber(Object backingRecipe) {
        if (GET_CIRCUIT_NUMBER_METHOD != null) {
            try {
                return (int) GET_CIRCUIT_NUMBER_METHOD.invoke(backingRecipe);
            } catch (Throwable ignored) {
            }
        }
        try {
            Method m = backingRecipe.getClass().getMethod("getCircuitNumber");
            return (int) m.invoke(backingRecipe);
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Field f = backingRecipe.getClass().getDeclaredField("circuitNumber");
            f.setAccessible(true);
            return f.getInt(backingRecipe);
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static int extractProcessingDuration(Object backingRecipe) {
        if (GET_PROCESSING_DURATION_METHOD != null) {
            try {
                return (int) GET_PROCESSING_DURATION_METHOD.invoke(backingRecipe);
            } catch (Throwable ignored) {
            }
        }
        try {
            Method m = backingRecipe.getClass().getMethod("getProcessingDuration");
            return (int) m.invoke(backingRecipe);
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Field f = backingRecipe.getClass().getDeclaredField("processingDuration");
            f.setAccessible(true);
            return f.getInt(backingRecipe);
        } catch (Throwable ignored) {
        }
        return 100;
    }

    private static String extractHeatCondition(Object backingRecipe) {
        if (GET_REQUIRED_HEAT_METHOD != null) {
            try {
                Object heat = GET_REQUIRED_HEAT_METHOD.invoke(backingRecipe);
                if (heat != null) {
                    if (heat instanceof Enum<?> e) {
                        return e.name();
                    }
                    return heat.toString();
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Method m = backingRecipe.getClass().getMethod("getRequiredHeat");
            Object heat = m.invoke(backingRecipe);
            if (heat != null) {
                if (heat instanceof Enum<?> e) {
                    return e.name();
                }
                return heat.toString();
            }
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Field f = backingRecipe.getClass().getDeclaredField("requiredHeat");
            f.setAccessible(true);
            Object heat = f.get(backingRecipe);
            if (heat != null) {
                if (heat instanceof Enum<?> e) {
                    return e.name();
                }
                return heat.toString();
            }
        } catch (Throwable ignored) {
        }
        return "NONE";
    }

    private static double calculateBaseStress(ResourceLocation categoryId, int tier) {
        double multiplier = 0.5;
        if (categoryId != null && HIGH_IMPACT_CATEGORIES.contains(categoryId.getPath().toLowerCase(Locale.ROOT))) {
            multiplier = 1.0;
        }
        return (tier + 1) * multiplier * 32.0;
    }
}
