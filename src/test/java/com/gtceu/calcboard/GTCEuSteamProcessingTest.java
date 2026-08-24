package com.gtceu.calcboard;

import com.gtceu.calcboard.api.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.*;

public class GTCEuSteamProcessingTest {

    @BeforeEach
    void setUp() {
        CategoryCapabilityMatrix.getInstance();
    }

    @Test
    void testSteamProcessingLowPressure() {
        RecipeNode node = RecipeNode.create("Steam Macerator", 400.0, 2.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        node.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ore"), "Iron Ore", 1.0));
        node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 2.0));

        assertTrue(node.supportsSteamMode(), "Macerator category should support steam processing mode");
        assertEquals(SteamMode.NONE, node.getSteamMode());

        // Switch to Low Pressure Steam mode
        node.setSteamMode(SteamMode.LOW_PRESSURE);
        assertEquals(SteamMode.LOW_PRESSURE, node.getSteamMode());
        assertEquals(ResourceLocation.tryParse("gtceu:lp_steam_macerator"), node.getMachineIcon());

        // Low pressure steam runs at 0.5x speed (2.0x duration)
        var ocResult = node.getOverclockResult();
        assertEquals(800.0, ocResult.durationTicks(), 0.001);
        assertEquals(0.0, ocResult.eut(), 0.001);
        assertEquals(0.0, node.getTotalEUt(), 0.001);

        // Verify steam fluid consumption rate (2 EU/t * 2 mB/EU = 4 mB/t = 80 mB/s)
        Map<IngredientStack, Double> inRates = node.calculateInputRates();
        boolean foundSteam = false;
        for (Map.Entry<IngredientStack, Double> entry : inRates.entrySet()) {
            if (entry.getKey().isFluid() && entry.getKey().getId().equals(ResourceLocation.tryParse("gtceu:steam"))) {
                foundSteam = true;
                assertEquals(80.0, entry.getValue(), 0.001);
            }
        }
        assertTrue(foundSteam, "Steam input stack must be present in input rates");
    }

    @Test
    void testSteamProcessingHighPressure() {
        RecipeNode node = RecipeNode.create("Steam Macerator", 400.0, 2.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        node.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ore"), "Iron Ore", 1.0));
        node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 2.0));

        // Switch to High Pressure Steam mode
        node.setSteamMode(SteamMode.HIGH_PRESSURE);
        assertEquals(SteamMode.HIGH_PRESSURE, node.getSteamMode());
        assertEquals(ResourceLocation.tryParse("gtceu:hp_steam_macerator"), node.getMachineIcon());

        // High pressure steam runs at 1.0x speed (1.0x duration)
        var ocResult = node.getOverclockResult();
        assertEquals(400.0, ocResult.durationTicks(), 0.001);
        assertEquals(0.0, ocResult.eut(), 0.001);
        assertEquals(0.0, node.getTotalEUt(), 0.001);

        // Steam rate: 4 mB/t * 20 t/s = 80 mB/s
        Map<IngredientStack, Double> inRates = node.calculateInputRates();
        boolean foundSteam = false;
        for (Map.Entry<IngredientStack, Double> entry : inRates.entrySet()) {
            if (entry.getKey().isFluid() && entry.getKey().getId().equals(ResourceLocation.tryParse("gtceu:steam"))) {
                foundSteam = true;
                assertEquals(80.0, entry.getValue(), 0.001);
            }
        }
        assertTrue(foundSteam, "Steam input stack must be present in input rates");
    }

    @Test
    void testBoilerToSteamProcessingAutoRatio() {
        FlowGraph graph = new FlowGraph();

        // Boiler producing 120 L/s Steam
        RecipeNode boiler = RecipeNode.create("Small Bronze Boiler", 32000.0, 0.0, GTVoltageTier.LV);
        boiler.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_boiler"));
        boiler.setEnergyType(EnergyType.HEAT_OR_SELF);
        boiler.setMachineIcon(ResourceLocation.tryParse("gtceu:bronze_boiler"));
        boiler.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:coal"), "Coal", 1.0));
        boiler.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1200.0));
        boiler.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 192000.0));

        // Steam Macerator consuming 80 L/s Steam
        RecipeNode macerator = RecipeNode.create("Steam Macerator", 400.0, 2.0, GTVoltageTier.LV);
        macerator.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        macerator.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ore"), "Iron Ore", 1.0));
        macerator.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 2.0));
        macerator.setSteamMode(SteamMode.HIGH_PRESSURE);

        graph.addNode(boiler);
        graph.addNode(macerator);

        // Find steam output slot on boiler and steam input slot on macerator
        int boilerSteamPort = -1;
        for (int i = 0; i < boiler.getOutputs().size(); i++) {
            if (boiler.getOutputs().get(i).getId().equals(ResourceLocation.tryParse("gtceu:steam"))) {
                boilerSteamPort = i;
                break;
            }
        }
        int maceratorSteamPort = -1;
        for (int i = 0; i < macerator.getInputs().size(); i++) {
            if (macerator.getInputs().get(i).getId().equals(ResourceLocation.tryParse("gtceu:steam"))) {
                maceratorSteamPort = i;
                break;
            }
        }

        assertTrue(boilerSteamPort >= 0, "Boiler steam output port found");
        assertTrue(maceratorSteamPort >= 0, "Macerator steam input port found");

        graph.addConnection(boiler.getId(), boilerSteamPort, macerator.getId(), maceratorSteamPort);

        // Boiler is anchor -> Auto ratio propagation
        boiler.setBaseNode(true);
        graph.autoRatioFromAnchor(boiler, false);

        // Boiler produces 120 L/s steam, Macerator consumes 80 L/s steam per machine
        // Required machine count = 120 / 80 = 1.5
        assertEquals(1.5, macerator.getMachineCount(), 0.001);
    }

    @Test
    void testNbtSerialization() {
        RecipeNode node = RecipeNode.create("Steam Macerator", 400.0, 2.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        node.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ore"), "Iron Ore", 1.0));
        node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 2.0));
        node.setSteamMode(SteamMode.LOW_PRESSURE);

        CompoundTag tag = node.serializeNBT();
        RecipeNode loaded = RecipeNode.deserializeNBT(tag);

        assertEquals(SteamMode.LOW_PRESSURE, loaded.getSteamMode());
        assertEquals(ResourceLocation.tryParse("gtceu:lp_steam_macerator"), loaded.getMachineIcon());
    }

    @Test
    void testBoilerTierSwitchingAndSpeedScaling() {
        // 1. Solid Fuel Boiler (Coal)
        RecipeNode solidBoiler = RecipeNode.create("Steam Boiler (Coal)", 1600.0, 0.0, GTVoltageTier.ULV);
        solidBoiler.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_boiler"));
        solidBoiler.setEnergyType(EnergyType.HEAT_OR_SELF);
        solidBoiler.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:coal"), "Coal", 1.0));
        solidBoiler.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 9600.0));

        // Default LP Bronze (1.0x speed, 6 mB/t = 120 L/s)
        assertEquals(GTBoilerTier.LP_BRONZE, GTBoilerTier.getBoilerTier(solidBoiler));
        assertEquals(1600.0, solidBoiler.getOverclockResult().durationTicks(), 0.001);

        // Switch to HP Steel (2.5x speed, 15 mB/t = 300 L/s)
        solidBoiler.setMachineIcon(GTBoilerTier.HP_STEEL.getDefaultIcon());
        assertEquals(GTBoilerTier.HP_STEEL, GTBoilerTier.getBoilerTier(solidBoiler));
        assertEquals(1600.0 / 2.5, solidBoiler.getOverclockResult().durationTicks(), 0.001);

        // 2. Liquid Fuel Boiler (Lava)
        RecipeNode liquidBoiler = RecipeNode.create("Steam Boiler (Lava)", 1000.0, 0.0, GTVoltageTier.ULV);
        liquidBoiler.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_boiler"));
        liquidBoiler.setEnergyType(EnergyType.HEAT_OR_SELF);
        liquidBoiler.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:lava"), "Lava", 100.0));
        liquidBoiler.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 15000.0));

        // LP Bronze (1.0x speed, 50s duration, 15 mB/t = 300 L/s)
        assertEquals(GTBoilerTier.LP_BRONZE, GTBoilerTier.getBoilerTier(liquidBoiler));
        assertEquals(1000.0, liquidBoiler.getOverclockResult().durationTicks(), 0.001);

        // HP Steel (2.0x speed, 25s duration, 30 mB/t = 600 L/s)
        liquidBoiler.setMachineIcon(GTBoilerTier.HP_STEEL.getDefaultIcon());
        assertEquals(GTBoilerTier.HP_STEEL, GTBoilerTier.getBoilerTier(liquidBoiler));
        assertEquals(500.0, liquidBoiler.getOverclockResult().durationTicks(), 0.001);
        assertEquals(25.0, liquidBoiler.getEffectiveDurationSeconds(), 0.001);
    }

    @Test
    void testMachineWithMultiblockOptionSupportsSteamInSingleblock() {
        RecipeNode node = RecipeNode.create("Alloy Smelter", 50.0, 16.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:alloy_smelter"));
        node.setAvailableWorkstations(List.of(
                ResourceLocation.tryParse("gtceu:lp_steam_alloy_smelter"),
                ResourceLocation.tryParse("gtceu:hp_steam_alloy_smelter"),
                ResourceLocation.tryParse("gtceu:lv_alloy_smelter"),
                ResourceLocation.tryParse("gtceu:combination_smelter")
        ));

        // Has both steam and multiblock options
        assertTrue(node.supportsSteamMode(), "Node should support steam mode via available workstations");
        assertTrue(node.hasMultiblockOption(), "Node should have multiblock option");

        // When in singleblock mode, steam mode toggle can be activated
        node.setMultiblock(false);
        node.setSteamMode(SteamMode.HIGH_PRESSURE);
        assertEquals(SteamMode.HIGH_PRESSURE, node.getSteamMode());
        assertEquals(ResourceLocation.tryParse("gtceu:hp_steam_alloy_smelter"), node.getMachineIcon());
    }

    @Test
    void testSteamToElectricDisconnectsInvalidSteamWire() {
        FlowGraph graph = new FlowGraph();

        // 1. Steam Boiler
        RecipeNode boiler = RecipeNode.create("Steam Boiler", 1000.0, 0.0, GTVoltageTier.ULV);
        boiler.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_boiler"));
        boiler.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 15000.0));
        graph.addNode(boiler);

        // 2. Alloy Smelter with Copper + Redstone inputs
        RecipeNode smelter = RecipeNode.create("Alloy Smelter", 50.0, 16.0, GTVoltageTier.LV);
        smelter.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:alloy_smelter"));
        smelter.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper Ingot", 1.0));
        smelter.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:redstone"), "Redstone", 4.0));
        smelter.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:red_alloy_ingot"), "Red Alloy Ingot", 1.0));
        graph.addNode(smelter);

        // Switch to HP Steam mode -> Injects Steam input at index 2
        smelter.setSteamMode(SteamMode.HIGH_PRESSURE);
        assertEquals(3, smelter.getInputs().size());
        assertEquals(ResourceLocation.tryParse("gtceu:steam"), smelter.getInputs().get(2).getId());

        // Connect Boiler Steam (out 0) -> Smelter Steam (in 2)
        graph.addConnection(boiler.getId(), 0, smelter.getId(), 2);
        assertEquals(1, graph.getConnections().size());

        // Now switch Smelter back to Electric (SteamMode.NONE) -> Injected steam slot removed
        smelter.setSteamMode(SteamMode.NONE);
        assertEquals(2, smelter.getInputs().size());

        // Cleanup invalid connections
        boolean removed = graph.cleanupInvalidConnections();
        assertTrue(removed, "Invalid connection to removed steam slot must be cleaned up");
        assertEquals(0, graph.getConnections().size(), "No connections should remain on the smelter");
    }

    @Test
    void testSteamNodeSaveLoadDoesNotDuplicateSteamInput() {
        RecipeNode smelter = RecipeNode.create("Alloy Smelter", 50.0, 16.0, GTVoltageTier.LV);
        smelter.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:alloy_smelter"));
        smelter.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper Ingot", 1.0));
        smelter.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:redstone"), "Redstone", 4.0));
        smelter.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:red_alloy_ingot"), "Red Alloy Ingot", 1.0));

        // Switch to HP Steam mode
        smelter.setSteamMode(SteamMode.HIGH_PRESSURE);
        assertEquals(3, smelter.getInputs().size());

        // Repeatedly serialize and deserialize 5 times (simulating opening/closing world / main menu)
        RecipeNode current = smelter;
        for (int i = 0; i < 5; i++) {
            CompoundTag tag = current.serializeNBT();
            current = RecipeNode.deserializeNBT(tag);
            assertEquals(3, current.getInputs().size(), "Inputs size must remain 3 after save/load cycle " + (i + 1));
            long steamCount = current.getInputs().stream()
                    .filter(in -> in.isFluid() && in.getId().equals(ResourceLocation.tryParse("gtceu:steam")))
                    .count();
            assertEquals(1, steamCount, "There must be exactly ONE steam input stack after save/load cycle " + (i + 1));
        }
    }

    @Test
    public void testSteamTurbineSingleblockAndMultiblockTransitions() {
        RecipeNode turbine = RecipeNode.create("Steam Turbine", 160.0, 2.0, GTVoltageTier.LV);
        turbine.setGenerator(true);
        turbine.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_turbine"));

        // 1. Singleblock LV Steam Turbine
        turbine.setTargetTier(GTVoltageTier.LV);
        turbine.setMultiblock(false);
        com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter.syncTurbineMachineIcon(turbine);

        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:lv_steam_turbine"), turbine.getMachineIcon());
        Assertions.assertFalse(turbine.isMultiblock());
        Assertions.assertFalse(com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.isLargeTurbine(turbine));
        Assertions.assertFalse(com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(turbine).supportsAddons(turbine));
        Assertions.assertTrue(com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(turbine).getApplicableAddonCategories(turbine).isEmpty());

        // 2. Singleblock MV & HV Steam Turbine
        turbine.setTargetTier(GTVoltageTier.MV);
        com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter.syncTurbineMachineIcon(turbine);
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:mv_steam_turbine"), turbine.getMachineIcon());

        turbine.setTargetTier(GTVoltageTier.HV);
        com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter.syncTurbineMachineIcon(turbine);
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:hv_steam_turbine"), turbine.getMachineIcon());

        // 3. Switch to Multiblock Large Steam Turbine
        turbine.setMultiblock(true);
        turbine.setTargetTier(GTVoltageTier.EV);
        com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter.syncTurbineMachineIcon(turbine);

        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:large_steam_turbine"), turbine.getMachineIcon());
        Assertions.assertTrue(turbine.isMultiblock());
        Assertions.assertTrue(com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.isLargeTurbine(turbine));
        Assertions.assertTrue(com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(turbine).supportsAddons(turbine));

        var cats = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(turbine).getApplicableAddonCategories(turbine);
        Assertions.assertTrue(cats.contains(com.gtceu.calcboard.api.AddonCategory.ROTOR));
        Assertions.assertTrue(cats.contains(com.gtceu.calcboard.api.AddonCategory.MAINTENANCE));
        Assertions.assertTrue(cats.contains(com.gtceu.calcboard.api.AddonCategory.CUSTOM));
        Assertions.assertFalse(cats.contains(com.gtceu.calcboard.api.AddonCategory.PARALLEL));
    }

    @Test
    public void testSupremeAndNyinsanePlasmaTurbines() {
        RecipeNode plasma = RecipeNode.create("Plasma Turbine (Helium)", 20.0, 16384.0, GTVoltageTier.IV);
        plasma.setGenerator(true);
        plasma.setMultiblock(true);
        plasma.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:plasma_turbine"));
        plasma.setMachineIcon(ResourceLocation.tryParse("gtceu:large_plasma_turbine"));

        // 1. Standard LPT: 16,384 EU/t base
        Assertions.assertEquals(16384.0, plasma.getTurbineBaseProduction(), 0.001);
        Assertions.assertEquals(16384.0, plasma.getSingleMachineEUt(), 0.001);

        // 2. Supreme Plasma Turbine (SPT): 6x base parallel capacity (98,304 EU/t)
        plasma.setMachineIcon(ResourceLocation.tryParse("gtceu:supreme_plasma_turbine"));
        Assertions.assertEquals(98304.0, plasma.getTurbineBaseProduction(), 0.001);
        plasma.setParallel(6);
        Assertions.assertEquals(98304.0, plasma.getSingleMachineEUt(), 0.001);

        // Install SPT Lubricant Boosting (+25%)
        MachineAddon sptLub = new MachineAddon("gtceu:spt_lubricant_boosting", "SPT Lubricant", MachineAddon.Category.MULTIBLOCK_TRAIT, "", null);
        sptLub.setEutMultiplier(1.25);
        com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(plasma).onAddonInstalled(plasma, sptLub);

        Assertions.assertEquals(98304.0 * 1.25, plasma.getSingleMachineEUt(), 0.001);
        Assertions.assertTrue(plasma.getInputs().stream().anyMatch(in -> in.isFluid() && in.getId().getPath().contains("tungsten_disulfide")));

        // Install SPT Coolant Boosting (+75%)
        MachineAddon sptCool = new MachineAddon("gtceu:spt_coolant_boosting", "SPT Coolant", MachineAddon.Category.MULTIBLOCK_TRAIT, "", null);
        sptCool.setEutMultiplier(1.75);
        com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(plasma).onAddonInstalled(plasma, sptCool);

        Assertions.assertTrue(plasma.getInputs().stream().anyMatch(in -> in.isFluid() && in.getId().getPath().contains("helium_3")));

        // 3. Nyinsane Plasma Turbine (NPT): 12x base parallel capacity (196,608 EU/t)
        plasma.getAddons().clear();
        plasma.setMachineIcon(ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine"));
        Assertions.assertEquals(196608.0, plasma.getTurbineBaseProduction(), 0.001);
        plasma.setParallel(12);
        Assertions.assertEquals(196608.0, plasma.getSingleMachineEUt(), 0.001);
    }
}


