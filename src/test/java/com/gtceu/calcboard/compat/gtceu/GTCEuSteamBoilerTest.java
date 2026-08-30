package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;

import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler;
import com.gtceu.calcboard.compat.systeams.SysteamsModAdapter;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GTCEuSteamBoilerTest {

    @Test
    public void testAdapterRoutingForGTBoiler() {
        ResourceLocation gtBoilerCat = ResourceLocation.tryParse("gtceu:steam_boiler");
        IModAdapter adapter = ModAdapterRegistry.getAdapterForCategory(gtBoilerCat);

        Assertions.assertInstanceOf(GTCEuModAdapter.class, adapter, "gtceu:steam_boiler MUST be handled by GTCEuModAdapter, NOT SysteamsModAdapter");
    }

    @Test
    public void testGTBoilerRecipeDetails() {
        // 1. Liquid Fuel (Lava)
        RecipeNode lavaNode = RecipeNode.create("Steam Boiler (Lava)", 10000.0, 0.0, GTVoltageTier.ULV);
        lavaNode.setEnergyType(EnergyType.HEAT_OR_SELF);
        lavaNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_boiler"));
        lavaNode.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:lava"), "Lava", 1000.0));
        lavaNode.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 937.5));
        lavaNode.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 150000.0));

        // Small Bronze Boiler (baseline 1.0x): 10000 ticks = 500s -> 0.002 cycles/s
        Assertions.assertEquals(500.0, lavaNode.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(0.002, lavaNode.getNominalCyclesPerSecond(), 0.00001);

        // Steam production: 150000 * 0.002 = 300.0 mB/s (15 mB/t)
        Assertions.assertEquals(300.0, lavaNode.getOutputSlotRate(0, false), 0.01);

        // HP Steel Boiler (2.0x speed -> 30 mB/t = 600 L/s)
        lavaNode.setMachineIcon(ResourceLocation.tryParse("gtceu:steel_boiler"));
        Assertions.assertEquals(250.0, lavaNode.getEffectiveDurationSeconds(), 0.01);
        Assertions.assertEquals(600.0, lavaNode.getOutputSlotRate(0, false), 0.01);

        // 2. Solid Fuel (Coal)
        RecipeNode coalNode = RecipeNode.create("Steam Boiler (Coal)", 1600.0, 0.0, GTVoltageTier.ULV);
        coalNode.setEnergyType(EnergyType.HEAT_OR_SELF);
        coalNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_boiler"));
        coalNode.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:coal"), "Coal", 1.0));
        coalNode.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 60.0));
        coalNode.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 9600.0));

        // Small Bronze Boiler (baseline 1.0x): 1600 ticks = 80s -> 120 L/s steam (6 mB/t)
        Assertions.assertEquals(80.0, coalNode.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(120.0, coalNode.getOutputSlotRate(0, false), 0.01);

        // HP Steel Boiler (Solid 2.5x speed -> 15 mB/t = 300 L/s)
        coalNode.setMachineIcon(ResourceLocation.tryParse("gtceu:steel_boiler"));
        Assertions.assertEquals(80.0 / 2.5, coalNode.getEffectiveDurationSeconds(), 0.01);
        Assertions.assertEquals(300.0, coalNode.getOutputSlotRate(0, false), 0.01);
    }

    @Test
    public void testGTBoilerAddonRestrictions() {
        IModAdapter adapter = ModAdapterRegistry.getAdapterForCategory(ResourceLocation.tryParse("gtceu:steam_boiler"));

        // 1. Singleblock Boiler (LP Bronze Boiler)
        RecipeNode singleBoiler = RecipeNode.create("Small Bronze Boiler", 10000.0, 0.0, GTVoltageTier.ULV);
        singleBoiler.setEnergyType(EnergyType.HEAT_OR_SELF);
        singleBoiler.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_boiler"));
        singleBoiler.setMultiblock(false);

        Assertions.assertFalse(adapter.supportsAddons(singleBoiler), "Singleblock boiler must not support hardware addons");
        Assertions.assertTrue(adapter.getApplicableAddonCategories(singleBoiler).isEmpty());

        // 2. Multiblock Boiler (Large Bronze Boiler)
        RecipeNode mbBoiler = RecipeNode.create("Large Bronze Boiler", 10000.0, 0.0, GTVoltageTier.ULV);
        mbBoiler.setEnergyType(EnergyType.HEAT_OR_SELF);
        mbBoiler.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_boiler"));
        mbBoiler.setMultiblock(true);

        Assertions.assertTrue(adapter.supportsAddons(mbBoiler), "Multiblock boiler must support hardware addons (Maintenance & Hatches)");
        var cats = adapter.getApplicableAddonCategories(mbBoiler);
        Assertions.assertEquals(3, cats.size());
        Assertions.assertTrue(cats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.MAINTENANCE));
        Assertions.assertTrue(cats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.HATCH_BUS));
        Assertions.assertTrue(cats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.CUSTOM));

        // Test addon compatibility
        var maintAddon = new com.gtceu.calcboard.api.catalog.MachineAddon("gtceu:maintenance_hatch", "Maintenance Hatch", com.gtceu.calcboard.api.catalog.AddonCategory.MAINTENANCE, "", null);
        var hatchAddon = new com.gtceu.calcboard.api.catalog.MachineAddon("gtceu:lv_input_hatch", "Input Hatch (LV)", com.gtceu.calcboard.api.catalog.AddonCategory.HATCH_BUS, "", null);
        var parallelAddon = new com.gtceu.calcboard.api.catalog.MachineAddon("gtceu:parallel_hatch", "Parallel Hatch", com.gtceu.calcboard.api.catalog.AddonCategory.PARALLEL, "", null);
        var coilAddon = new com.gtceu.calcboard.api.catalog.MachineAddon("gtceu:cupronickel_coil", "Cupronickel Coil", com.gtceu.calcboard.api.catalog.AddonCategory.COIL, "", null);

        Assertions.assertTrue(adapter.isAddonCompatible(mbBoiler, maintAddon), "Maintenance hatch must be compatible with multiblock boiler");
        Assertions.assertTrue(adapter.isAddonCompatible(mbBoiler, hatchAddon), "Input hatch must be compatible with multiblock boiler");
        Assertions.assertFalse(adapter.isAddonCompatible(mbBoiler, parallelAddon), "Parallel hatch must NOT be compatible with boiler");
        Assertions.assertFalse(adapter.isAddonCompatible(mbBoiler, coilAddon), "Coil must NOT be compatible with boiler");
    }

    @Test
    public void testMultiblockBoilerThrottleScaling() {
        RecipeNode lavaNode = RecipeNode.create("Large Bronze Boiler", 10000.0, 0.0, GTVoltageTier.ULV);
        lavaNode.setEnergyType(EnergyType.HEAT_OR_SELF);
        lavaNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_boiler"));
        lavaNode.setMachineIcon(ResourceLocation.tryParse("gtceu:bronze_large_boiler"));
        lavaNode.setMultiblock(true);

        lavaNode.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:lava"), "Lava", 1000.0));
        lavaNode.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 937.5));
        lavaNode.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 150000.0));

        // 1. Default Throttle = 100% (800 mB/t = 16000 mB/s)
        Assertions.assertEquals(100, lavaNode.getBoilerThrottle());
        Assertions.assertEquals(16000.0, lavaNode.getOutputSlotRate(0, false), 0.01);

        // 2. Set Throttle = 25% (200 mB/t = 4000 mB/s)
        lavaNode.setBoilerThrottle(25);
        Assertions.assertEquals(25, lavaNode.getBoilerThrottle());
        Assertions.assertEquals(4000.0, lavaNode.getOutputSlotRate(0, false), 0.01);

        // 3. Set Throttle = 50% (400 mB/t = 8000 mB/s)
        lavaNode.setBoilerThrottle(50);
        Assertions.assertEquals(50, lavaNode.getBoilerThrottle());
        Assertions.assertEquals(8000.0, lavaNode.getOutputSlotRate(0, false), 0.01);

        // 4. Minimum Clamp: Throttle below 25% must clamp to 25%
        lavaNode.setBoilerThrottle(10);
        Assertions.assertEquals(25, lavaNode.getBoilerThrottle());

        // 5. Maximum Clamp: Throttle above 100% must clamp to 100%
        lavaNode.setBoilerThrottle(150);
        Assertions.assertEquals(100, lavaNode.getBoilerThrottle());
    }

    @Test
    public void testLiquidBoilerRecipeExtractionAndRouting() {
        RecipeNode node = RecipeNode.create("Steam Boiler (용암)", 1000.0, 0.0, GTVoltageTier.ULV);
        node.setEnergyType(EnergyType.HEAT_OR_SELF);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_liquid_boiler"));
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:lp_steam_liquid_boiler"));

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        Assertions.assertInstanceOf(GTCEuModAdapter.class, adapter, "GT boiler with gtceu icon must be handled by GTCEuModAdapter");
        Assertions.assertTrue(adapter.isBoilerRecipe(node));
        Assertions.assertTrue(node.isLiquidBoilerRecipe());

        // Test fuel and steam rates
        node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:lava"), "Lava", 100.0));
        node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 93.75));
        node.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 15000.0));

        // 1000 ticks = 50s -> 0.02 cycles/s -> 300.0 mB/s steam (15 mB/t)
        Assertions.assertEquals(50.0, node.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(0.02, node.getNominalCyclesPerSecond(), 0.00001);
        Assertions.assertEquals(300.0, node.getOutputSlotRate(0, false), 0.01);
        Assertions.assertEquals(2.0, node.getInputSlotRate(0, false), 0.01); // 2 mB/s Lava
        Assertions.assertEquals(1.875, node.getInputSlotRate(1, false), 0.01); // 1.875 mB/s Water
    }

    @Test
    public void testLargeBoilerRecipeCalculations() {
        RecipeNode node = RecipeNode.create("Large Boiler (Lava)", 100.0, 0.0, GTVoltageTier.ULV);
        node.setEnergyType(EnergyType.HEAT_OR_SELF);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:large_boiler"));
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:bronze_large_boiler"));
        node.setMultiblock(true);

        // 100 ticks (5s) for 100 mB Lava, 80000 mB Steam, 500 mB Water
        node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:lava"), "Lava", 100.0));
        node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 500.0));
        node.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 80000.0));

        // 1. Large Bronze Boiler (baseline 1.0x, 800 mB/t = 16000 mB/s)
        Assertions.assertEquals(5.0, node.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(0.2, node.getNominalCyclesPerSecond(), 0.00001);
        Assertions.assertEquals(16000.0, node.getOutputSlotRate(0, false), 0.01); // 16000 mB/s Steam (800 mB/t)
        Assertions.assertEquals(20.0, node.getInputSlotRate(0, false), 0.01); // 20 mB/s Lava (1.0 mB/t)
        Assertions.assertEquals(100.0, node.getInputSlotRate(1, false), 0.01); // 100 mB/s Water (5.0 mB/t)

        // 2. Large Bronze Boiler with 25% Throttle (200 mB/t = 4000 mB/s)
        node.setBoilerThrottle(25);
        Assertions.assertEquals(20.0, node.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(0.05, node.getNominalCyclesPerSecond(), 0.00001);
        Assertions.assertEquals(4000.0, node.getOutputSlotRate(0, false), 0.01); // 4000 mB/s Steam (200 mB/t)
        Assertions.assertEquals(5.0, node.getInputSlotRate(0, false), 0.01); // 5 mB/s Lava (0.25 mB/t)
        Assertions.assertEquals(25.0, node.getInputSlotRate(1, false), 0.01); // 25 mB/s Water (1.25 mB/t)

        // Reset throttle to 100%
        node.setBoilerThrottle(100);

        // 3. Large Steel Boiler (1800 mB/t = 36000 mB/s -> 2.25x speed)
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:steel_large_boiler"));
        Assertions.assertEquals(5.0 / 2.25, node.getEffectiveDurationSeconds(), 0.01);
        Assertions.assertEquals(36000.0, node.getOutputSlotRate(0, false), 0.01);
        Assertions.assertEquals(45.0, node.getInputSlotRate(0, false), 0.01); // 45 mB/s Lava (2.25 mB/t)
        Assertions.assertEquals(225.0, node.getInputSlotRate(1, false), 0.01); // 225 mB/s Water (11.25 mB/t)
    }
}



