package com.gtceu.calcboard.compat.create;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.PowerDisplayMode;

import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mod Adapter facade for Create kinetic generators and processing machinery.
 */
public class CreateModAdapter implements IModAdapter {

    public static final String MOD_ID = "create";
    public static final String MOD_ID_ADDITION = "createaddition";

    @Override
    public String getModId() {
        return MOD_ID;
    }

    @Override
    public int getPriority() {
        return 90;
    }

    @Override
    public boolean isLoaded() {
        try {
            if (ModList.get() != null) {
                return ModList.get().isLoaded(MOD_ID)
                        || ModList.get().isLoaded(MOD_ID_ADDITION)
                        || !FMLLoader.isProduction();
            }
        } catch (Throwable t) {
            return true; // Test environment fallback
        }
        return true;
    }

    @Override
    public boolean handlesCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        String ns = categoryId.getNamespace();
        if (ns.equals("create_new_age")) return false; // Dedicated CreateNewAgeModAdapter handles this
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isCreateFamilyNamespace(ns)) return true;
        return "gtcalcboard".equals(ns) && "kinetic_generation".equals(categoryId.getPath());
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        if (node == null) return false;
        return node.isCreateMachine();
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
        // Create kinetic machines do not use GUI addon slots
    }

    @Override
    public boolean adaptRecipeDetails(Object emiRecipe, Object backingRecipe, EmiRecipeConverter.RecipeDetails details) {
        return CreateRecipeHandler.adaptRecipeDetails(emiRecipe, backingRecipe, details);
    }

    @Override
    public void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
    }

    public static boolean isFanProcessingRecipe(RecipeNode node) {
        if (node == null) return false;
        ResourceLocation catId = node.getRecipeCategoryId();
        if (catId != null) {
            String path = catId.getPath().toLowerCase(Locale.ROOT);
            if (path.contains("splashing") || path.contains("washing") || path.contains("haunting") || path.contains("smoking") || path.contains("blasting")) {
                return true;
            }
        }
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null) {
            String path = icon.getPath().toLowerCase(Locale.ROOT);
            if (path.contains("encased_fan") || path.contains("fan")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public OverclockMode.OverclockResult computeOverclock(RecipeNode node, GTVoltageTier targetTier, boolean isGenerator) {
        int rpm = node.getRpm();
        double baseDuration = node.getBaseDurationTicks();
        double basePower = node.getBaseEUt();

        boolean isFanProcessing = isFanProcessingRecipe(node);

        // 32 RPM is the baseline standard speed (1.0x)
        double speedFactor = Math.max(0.01, rpm / 32.0);

        double durationTicks;
        double batchesPerTick;
        if (isFanProcessing) {
            // In Create mod, Fan processing (blasting/washing/smoking/haunting) duration is fixed in-world.
            // RPM only affects airflow distance/range, NOT processing speed!
            durationTicks = Math.max(1.0, baseDuration);
            batchesPerTick = 1.0;
        } else {
            double rawDuration = baseDuration / speedFactor;
            durationTicks = Math.max(1.0, rawDuration);
            batchesPerTick = (rawDuration < 1.0 && rawDuration > 0.0) ? (1.0 / rawDuration) : 1.0;
        }

        double effectivePower;
        if (isGenerator) {
            effectivePower = basePower;
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

    @Override
    public EnergyType getEnergyType(RecipeNode node) {
        return EnergyType.KINETIC_SU;
    }

    @Override
    public void registerSyntheticEmiRecipes(Object emiRegistry, Object emiCategory, java.util.Set<net.minecraft.world.item.Item> activeRecipeItems) {
        CreateRecipeHandler.registerSyntheticEmiRecipes(emiRegistry, emiCategory, activeRecipeItems);
    }

    public static RecipeNode createKineticGeneratorNode(ItemStack stack) {
        RecipeNode node = CreateRecipeHandler.createKineticGeneratorNode(stack);
        if (node != null) return node;
        return com.gtceu.calcboard.compat.createnewage.CreateNewAgeRecipeHandler.createKineticGeneratorNode(stack);
    }

    public static RecipeNode createKineticGeneratorNode(ResourceLocation itemId, String displayName) {
        RecipeNode node = CreateRecipeHandler.createKineticGeneratorNode(itemId, displayName);
        if (node != null) return node;
        return com.gtceu.calcboard.compat.createnewage.CreateNewAgeRecipeHandler.createKineticGeneratorNode(itemId, displayName);
    }

    public static List<RecipeSearchEngine.SearchableRecipe> getVirtualKineticSearchRecipes() {
        List<RecipeSearchEngine.SearchableRecipe> list = new ArrayList<>(CreateRecipeHandler.getVirtualKineticSearchRecipes());
        list.addAll(com.gtceu.calcboard.compat.createnewage.CreateNewAgeRecipeHandler.getVirtualSearchRecipes());
        return list;
    }
}




