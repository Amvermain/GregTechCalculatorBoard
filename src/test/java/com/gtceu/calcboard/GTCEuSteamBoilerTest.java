package com.gtceu.calcboard;

import com.gtceu.calcboard.api.EnergyType;
import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.RecipeNode;
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

        Assertions.assertTrue(adapter.supportsAddons(mbBoiler), "Multiblock boiler must support hardware addons (Maintenance)");
        var cats = adapter.getApplicableAddonCategories(mbBoiler);
        Assertions.assertEquals(2, cats.size());
        Assertions.assertTrue(cats.contains(com.gtceu.calcboard.api.AddonCategory.MAINTENANCE));
        Assertions.assertTrue(cats.contains(com.gtceu.calcboard.api.AddonCategory.CUSTOM));

        // Test addon compatibility
        var maintAddon = new com.gtceu.calcboard.api.MachineAddon("gtceu:maintenance_hatch", "Maintenance Hatch", com.gtceu.calcboard.api.AddonCategory.MAINTENANCE, "", null);
        var parallelAddon = new com.gtceu.calcboard.api.MachineAddon("gtceu:parallel_hatch", "Parallel Hatch", com.gtceu.calcboard.api.AddonCategory.PARALLEL, "", null);
        var coilAddon = new com.gtceu.calcboard.api.MachineAddon("gtceu:cupronickel_coil", "Cupronickel Coil", com.gtceu.calcboard.api.AddonCategory.COIL, "", null);

        Assertions.assertTrue(adapter.isAddonCompatible(mbBoiler, maintAddon), "Maintenance hatch must be compatible with multiblock boiler");
        Assertions.assertFalse(adapter.isAddonCompatible(mbBoiler, parallelAddon), "Parallel hatch must NOT be compatible with boiler");
        Assertions.assertFalse(adapter.isAddonCompatible(mbBoiler, coilAddon), "Coil must NOT be compatible with boiler");
    }
}
