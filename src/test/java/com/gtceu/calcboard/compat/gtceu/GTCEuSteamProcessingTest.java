package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.NodeRateCalculator;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTBoilerTier;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.ModAdapterRegistry;

import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
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
        MultiblockDetector.reinitialize();
        com.gtceu.calcboard.testutil.TestMultiblockFixtures.initTestEnvironmentDefaults();
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
        Assertions.assertTrue(cats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.ROTOR));
        Assertions.assertTrue(cats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.MAINTENANCE));
        Assertions.assertTrue(cats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.CUSTOM));
        Assertions.assertFalse(cats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.PARALLEL));
    }

    @Test
    public void testSupremeAndNyinsanePlasmaTurbines() {
        RecipeNode plasma = RecipeNode.create("Plasma Turbine (Helium)", 20.0, 16384.0, GTVoltageTier.IV);
        plasma.setGenerator(true);
        plasma.setMultiblock(true);
        plasma.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:plasma_turbine"));
        plasma.setMachineIcon(ResourceLocation.tryParse("gtceu:large_plasma_turbine"));

        // 1. Standard LPT: 16,384 EU/t base
        Assertions.assertEquals(16384.0, GTTurbineHelper.getTurbineBaseProduction(plasma), 0.001);
        Assertions.assertEquals(16384.0, plasma.getSingleMachineEUt(), 0.001);

        // 2. Supreme Plasma Turbine (SPT): 6x base parallel capacity (98,304 EU/t base, 0.9x unboosted penalty)
        plasma.setMachineIcon(ResourceLocation.tryParse("start_core:supreme_plasma_turbine"));
        Assertions.assertEquals(98304.0, GTTurbineHelper.getTurbineBaseProduction(plasma), 0.001);
        plasma.setParallel(6);
        Assertions.assertEquals(98304.0 * 0.9, plasma.getSingleMachineEUt(), 0.001);

        // Cycle to SPT Passive Boost (+25%, 1.25x)
        GTTurbineHelper.cycleTurbineBoost(plasma, 1);
        Assertions.assertEquals(98304.0 * 1.25, plasma.getSingleMachineEUt(), 0.001);
        Assertions.assertTrue(plasma.getInputs().stream().anyMatch(in -> in.isFluid() && in.getId().getPath().contains("tungsten_disulfide")));

        // Cycle to SPT Full Boost (Active Full Boost: 2.0x)
        GTTurbineHelper.cycleTurbineBoost(plasma, 1);
        Assertions.assertEquals(98304.0 * 2.0, plasma.getSingleMachineEUt(), 0.001);
        Assertions.assertTrue(plasma.getInputs().stream().anyMatch(in -> in.isFluid() && in.getId().getPath().contains("helium_3")));

        // 3. Nyinsane Plasma Turbine (NPT): 12x base parallel capacity (196,608 EU/t base, 0.8x unboosted penalty)
        plasma.getAddons().clear();
        GTTurbineHelper.setLubricantBoost(plasma, false);
        GTTurbineHelper.setCoolantBoost(plasma, false);
        plasma.setMachineIcon(ResourceLocation.tryParse("start_core:nyinsane_plasma_turbine"));
        Assertions.assertEquals(196608.0, GTTurbineHelper.getTurbineBaseProduction(plasma), 0.001);
        plasma.setParallel(12);
        Assertions.assertEquals(196608.0 * 0.8, plasma.getSingleMachineEUt(), 0.001);
    }

    @Test
    void testUlvToSteamTierTransition() {
        RecipeNode node = RecipeNode.create("Tin Dust", 72.2 * 20.0, 2.0, GTVoltageTier.ULV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        node.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:huge_restrictive_tin_item_pipe"), "Pipe", 1.0));
        node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:tin_dust"), "Tin Dust", 1.0));

        com.gtceu.calcboard.client.gui.widget.NodeWidget widget = new com.gtceu.calcboard.client.gui.widget.NodeWidget(node);

        assertEquals(GTVoltageTier.ULV, node.getTargetTier());
        assertEquals(SteamMode.NONE, node.getSteamMode());

        // 1. Changing tier down (-1) from ULV should transition to HP Steam
        boolean changed = widget.changeTier(-1);
        assertTrue(changed, "Tier change down from ULV should succeed");
        assertEquals(SteamMode.HIGH_PRESSURE, node.getSteamMode());

        // 2. Changing tier down (-1) from HP Steam should transition to LP Steam
        changed = widget.changeTier(-1);
        assertTrue(changed, "Tier change down from HP Steam should succeed");
        assertEquals(SteamMode.LOW_PRESSURE, node.getSteamMode());

        // 3. Changing tier up (+1) from LP Steam should transition to HP Steam
        changed = widget.changeTier(1);
        assertTrue(changed, "Tier change up from LP Steam should succeed");
        assertEquals(SteamMode.HIGH_PRESSURE, node.getSteamMode());

        // 4. Changing tier up (+1) from HP Steam should transition to ULV (since recipe tier is ULV)
        changed = widget.changeTier(1);
        assertTrue(changed, "Tier change up from HP Steam should succeed");
        assertEquals(SteamMode.NONE, node.getSteamMode());
        assertEquals(GTVoltageTier.ULV, node.getTargetTier());

        // 5. Changing tier up (+1) from ULV should transition to LV
        changed = widget.changeTier(1);
        assertTrue(changed, "Tier change up from ULV should succeed");
        assertEquals(SteamMode.NONE, node.getSteamMode());
        assertEquals(GTVoltageTier.LV, node.getTargetTier());
    }

    @Test
    void testSteamDefinitionDetection() {
        assertTrue(com.gtceu.calcboard.compat.gtceu.helper.GTCEuCapabilityScanner.isSteamDefinition(null, ResourceLocation.tryParse("gtceu:steam_grinder")));
        assertTrue(com.gtceu.calcboard.compat.gtceu.helper.GTCEuCapabilityScanner.isHighPressureDefinition(null, ResourceLocation.tryParse("gtceu:steam_grinder")));
        assertTrue(MultiblockDetector.isSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_grinder")));

        RecipeNode grinderNode = RecipeNode.create("Steam Grinder", 200.0, 2.0, GTVoltageTier.ULV);
        grinderNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        grinderNode.setMachineIcon(ResourceLocation.tryParse("gtceu:steam_grinder"));

        assertTrue(grinderNode.isMultiblock());
        assertEquals(SteamMode.HIGH_PRESSURE, grinderNode.getSteamMode());
        assertEquals(8, grinderNode.getParallel());
    }

    @Test
    void testMaceratorByproductTierGating() {
        RecipeNode macerator = RecipeNode.create("Pure Gold Dust", 400.0, 2.0, GTVoltageTier.ULV);
        macerator.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        macerator.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:purified_gold_ore"), "Purified Gold Ore", 1.0));

        IngredientStack pureGold = IngredientStack.item(ResourceLocation.tryParse("gtceu:pure_gold_dust"), "Pure Gold Dust", 1.0, 1.0);
        IngredientStack stoneDust = IngredientStack.item(ResourceLocation.tryParse("gtceu:stone_dust"), "Stone Dust", 1.0, 0.14);
        stoneDust.setTierChanceBoost(0.085);

        macerator.getOutputs().add(pureGold);
        macerator.getOutputs().add(stoneDust);

        // At ULV / LV / MV (below HV): primary output is 100%, Stone Dust byproduct chance is 0%
        macerator.setTargetTier(GTVoltageTier.ULV);
        assertEquals(1.0, macerator.getEffectiveOutputChance(0), 0.001);
        assertEquals(0.0, macerator.getEffectiveOutputChance(1), 0.001);
        assertEquals(0.0, macerator.getOutputSlotRate(1, false), 0.001);

        macerator.setTargetTier(GTVoltageTier.LV);
        assertEquals(0.0, macerator.getEffectiveOutputChance(1), 0.001);

        macerator.setTargetTier(GTVoltageTier.MV);
        assertEquals(0.0, macerator.getEffectiveOutputChance(1), 0.001);

        // At HV: 1st byproduct is unlocked at base 14%
        macerator.setTargetTier(GTVoltageTier.HV);
        assertEquals(0.14, macerator.getEffectiveOutputChance(1), 0.001);
        assertTrue(macerator.getOutputSlotRate(1, false) > 0.0);

        // At EV: extra tier boost +8.5% -> 22.5%
        macerator.setTargetTier(GTVoltageTier.EV);
        assertEquals(0.225, macerator.getEffectiveOutputChance(1), 0.001);

        // In Steam Mode: byproducts are always 0%
        macerator.setSteamMode(SteamMode.HIGH_PRESSURE);
        assertEquals(0.0, macerator.getEffectiveOutputChance(1), 0.001);
        assertEquals(0.0, macerator.getOutputSlotRate(1, false), 0.001);
    }

    @Test
    void testMultiProductMaceratorByproductTierGating() {
        // Recipe: 2 Plant Ball -> 1 Bio Chaff (100%), 1 Bio Chaff (100%), 1 Bio Chaff (50%, +5%/tier), 1 Bio Chaff (25%, +2.5%/tier), 1 Stone Dust (10%, +1%/tier)
        RecipeNode macerator = RecipeNode.create("Plant Ball Macerating", 200.0, 4.0, GTVoltageTier.ULV);
        macerator.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        macerator.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:plant_ball"), "Plant Ball", 2.0));

        IngredientStack bioChaff1 = IngredientStack.item(ResourceLocation.tryParse("gtceu:bio_chaff"), "Bio Chaff", 1.0, 1.0);
        IngredientStack bioChaff2 = IngredientStack.item(ResourceLocation.tryParse("gtceu:bio_chaff"), "Bio Chaff", 1.0, 1.0);
        IngredientStack bioChaff3 = IngredientStack.item(ResourceLocation.tryParse("gtceu:bio_chaff"), "Bio Chaff", 1.0, 0.50);
        bioChaff3.setTierChanceBoost(0.05);
        IngredientStack bioChaff4 = IngredientStack.item(ResourceLocation.tryParse("gtceu:bio_chaff"), "Bio Chaff", 1.0, 0.25);
        bioChaff4.setTierChanceBoost(0.025);
        IngredientStack stoneDust = IngredientStack.item(ResourceLocation.tryParse("gtceu:stone_dust"), "Stone Dust", 1.0, 0.10);
        stoneDust.setTierChanceBoost(0.01);

        macerator.getOutputs().add(bioChaff1);
        macerator.getOutputs().add(bioChaff2);
        macerator.getOutputs().add(bioChaff3);
        macerator.getOutputs().add(bioChaff4);
        macerator.getOutputs().add(stoneDust);

        // 1. At MV (below HV): only the primary output (Slot 0) is active (1.0). ALL secondary outputs (Slot 1..4) are 0% (Requires HV+)
        macerator.setTargetTier(GTVoltageTier.MV);
        assertEquals(1.0, macerator.getEffectiveOutputChance(0), 0.001, "Slot 0 (primary output) must be 1.0 at MV");
        assertEquals(0.0, macerator.getEffectiveOutputChance(1), 0.001, "Slot 1 (1st byproduct) must be 0.0 at MV (Requires HV+)");
        assertEquals(0.0, macerator.getEffectiveOutputChance(2), 0.001, "Slot 2 (2nd byproduct) must be 0.0 at MV (Requires HV+)");
        assertEquals(0.0, macerator.getEffectiveOutputChance(3), 0.001, "Slot 3 (3rd byproduct) must be 0.0 at MV (Requires HV+)");
        assertEquals(0.0, macerator.getEffectiveOutputChance(4), 0.001, "Slot 4 (4th byproduct) must be 0.0 at MV (Requires HV+)");

        // 2. At HV: all byproducts (Slot 1..4) unlock at their base chances
        macerator.setTargetTier(GTVoltageTier.HV);
        assertEquals(1.0, macerator.getEffectiveOutputChance(0), 0.001);
        assertEquals(1.0, macerator.getEffectiveOutputChance(1), 0.001, "Slot 1 unlocks at 100% at HV");
        assertEquals(0.50, macerator.getEffectiveOutputChance(2), 0.001, "Slot 2 unlocks at base 50% at HV");
        assertEquals(0.25, macerator.getEffectiveOutputChance(3), 0.001, "Slot 3 unlocks at base 25% at HV");
        assertEquals(0.10, macerator.getEffectiveOutputChance(4), 0.001, "Slot 4 unlocks at base 10% at HV");

        // 3. At EV (HV + 1): all byproducts get boosted
        macerator.setTargetTier(GTVoltageTier.EV);
        assertEquals(1.0, macerator.getEffectiveOutputChance(0), 0.001);
        assertEquals(1.0, macerator.getEffectiveOutputChance(1), 0.001);
        assertEquals(0.55, macerator.getEffectiveOutputChance(2), 0.001, "Slot 2 boosted to 55% at EV");
        assertEquals(0.275, macerator.getEffectiveOutputChance(3), 0.001, "Slot 3 boosted to 27.5% at EV");
        assertEquals(0.11, macerator.getEffectiveOutputChance(4), 0.001, "Slot 4 boosted to 11% at EV");

        // 4. Effective output rates integration check
        var rates = NodeRateCalculator.calculateEffectiveOutputRates(macerator);
        double totalBioChaffRate = rates.entrySet().stream()
                .filter(e -> e.getKey().getDisplayName().equals("Bio Chaff"))
                .mapToDouble(java.util.Map.Entry::getValue)
                .sum();
        assertTrue(totalBioChaffRate > 0.0);
    }

    @Test
    void testSteamToElectricTransitionDoesNotSwitchToMultiblock() {
        RecipeNode macerator = RecipeNode.create("Macerator", 100.0, 30.0, GTVoltageTier.ULV);
        macerator.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        macerator.setAvailableWorkstations(List.of(
            ResourceLocation.tryParse("gtceu:large_macerator"),
            ResourceLocation.tryParse("gtceu:lv_macerator"),
            ResourceLocation.tryParse("gtceu:mv_macerator")
        ));

        // 1. Enter HP Steam
        macerator.setSteamMode(SteamMode.HIGH_PRESSURE);
        assertFalse(macerator.isMultiblock());

        // 2. Transition from HP Steam to ULV
        macerator.setSteamMode(SteamMode.NONE);
        macerator.setTargetTier(GTVoltageTier.ULV);

        // 3. Must stay Singleblock
        assertFalse(macerator.isMultiblock(), "Node must not automatically switch to multiblock when transitioning from Steam to ULV");
        assertNotEquals(ResourceLocation.tryParse("gtceu:large_macerator"), macerator.getMachineIcon());
    }

    @Test
    void testThermalCentrifugeAndCentrifugeDoNotSupportSteamMode() {
        // 1. Thermal Centrifuge
        RecipeNode tcNode = RecipeNode.create("Thermal Centrifuge", 20.0, 30.0, GTVoltageTier.LV);
        tcNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:thermal_centrifuge"));
        tcNode.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_thermal_centrifuge"));
        tcNode.setAvailableWorkstations(List.of(
                ResourceLocation.tryParse("gtceu:lv_thermal_centrifuge"),
                ResourceLocation.tryParse("gtceu:mv_thermal_centrifuge"),
                ResourceLocation.tryParse("gtceu:hv_thermal_centrifuge")
        ));

        assertFalse(tcNode.supportsSteamMode(), "Thermal Centrifuge must NOT support steam processing mode");
        assertEquals(SteamMode.NONE, tcNode.getSteamMode());

        com.gtceu.calcboard.client.gui.widget.NodeWidget tcWidget = new com.gtceu.calcboard.client.gui.widget.NodeWidget(tcNode);
        boolean tcDown = tcWidget.changeTier(-1);
        assertFalse(tcDown, "Tier change down from min tier (LV) on non-steam node must fail/not enter steam mode");
        assertEquals(SteamMode.NONE, tcNode.getSteamMode());
        assertEquals(GTVoltageTier.LV, tcNode.getTargetTier());

        // 2. Standard Centrifuge
        RecipeNode cNode = RecipeNode.create("Centrifuge", 20.0, 30.0, GTVoltageTier.MV);
        cNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:centrifuge"));
        cNode.setMachineIcon(ResourceLocation.tryParse("gtceu:mv_centrifuge"));
        cNode.setAvailableWorkstations(List.of(
                ResourceLocation.tryParse("gtceu:mv_centrifuge"),
                ResourceLocation.tryParse("gtceu:hv_centrifuge")
        ));

        assertFalse(cNode.supportsSteamMode(), "Centrifuge must NOT support steam processing mode");
        assertEquals(SteamMode.NONE, cNode.getSteamMode());

        com.gtceu.calcboard.client.gui.widget.NodeWidget cWidget = new com.gtceu.calcboard.client.gui.widget.NodeWidget(cNode);
        boolean cDown = cWidget.changeTier(-1);
        assertFalse(cDown, "Tier change down from min tier (MV) on non-steam node must fail/not enter steam mode");
        assertEquals(SteamMode.NONE, cNode.getSteamMode());
        assertEquals(GTVoltageTier.MV, cNode.getTargetTier());
    }

    @Test
    void testMaceratorSteamAndTierCycling() {
        RecipeNode macerator = RecipeNode.create("Macerator (Naquadah Alloy)", 400.0, 30.0, GTVoltageTier.LV);
        macerator.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        macerator.setMachineIcon(ResourceLocation.tryParse("gtceu:lp_steam_macerator"));
        macerator.setSteamMode(SteamMode.LOW_PRESSURE);
        macerator.setAvailableWorkstations(List.of(
                ResourceLocation.tryParse("gtceu:lp_steam_macerator"),
                ResourceLocation.tryParse("gtceu:hp_steam_macerator"),
                ResourceLocation.tryParse("gtceu:lv_macerator"),
                ResourceLocation.tryParse("gtceu:mv_macerator"),
                ResourceLocation.tryParse("gtceu:hv_macerator"),
                ResourceLocation.tryParse("gtceu:ev_macerator"),
                ResourceLocation.tryParse("gtceu:advanced_large_miner_iii")
        ));

        com.gtceu.calcboard.client.gui.widget.NodeWidget widget = new com.gtceu.calcboard.client.gui.widget.NodeWidget(macerator);

        // 1. Changing tier down (-1) from LP Steam should NOT change to multiblock or corrupt icon
        boolean downFromLp = widget.changeTier(-1);
        assertFalse(downFromLp, "Tier down from LP Steam on standard GT machine must return false");
        assertEquals(SteamMode.LOW_PRESSURE, macerator.getSteamMode());
        assertEquals(ResourceLocation.tryParse("gtceu:lp_steam_macerator"), macerator.getMachineIcon());
        assertFalse(macerator.isMultiblock());

        // 2. Changing tier up (+1) to HP Steam
        boolean up1 = widget.changeTier(1);
        assertTrue(up1);
        assertEquals(SteamMode.HIGH_PRESSURE, macerator.getSteamMode());
        assertEquals(ResourceLocation.tryParse("gtceu:hp_steam_macerator"), macerator.getMachineIcon());

        // 3. Changing tier up (+1) to LV electric
        boolean up2 = widget.changeTier(1);
        assertTrue(up2);
        assertEquals(SteamMode.NONE, macerator.getSteamMode());
        assertEquals(GTVoltageTier.LV, macerator.getTargetTier());
        assertEquals(ResourceLocation.tryParse("gtceu:lv_macerator"), macerator.getMachineIcon());

        // 4. Changing tier up (+1) to MV electric
        boolean up3 = widget.changeTier(1);
        assertTrue(up3);
        assertEquals(GTVoltageTier.MV, macerator.getTargetTier());
        assertEquals(ResourceLocation.tryParse("gtceu:mv_macerator"), macerator.getMachineIcon());

        // 5. Changing tier down (-1) from MV -> LV -> HP Steam -> LP Steam
        assertTrue(widget.changeTier(-1));
        assertEquals(GTVoltageTier.LV, macerator.getTargetTier());
        assertEquals(ResourceLocation.tryParse("gtceu:lv_macerator"), macerator.getMachineIcon());

        assertTrue(widget.changeTier(-1));
        assertEquals(SteamMode.HIGH_PRESSURE, macerator.getSteamMode());
        assertEquals(ResourceLocation.tryParse("gtceu:hp_steam_macerator"), macerator.getMachineIcon());

        assertTrue(widget.changeTier(-1));
        assertEquals(SteamMode.LOW_PRESSURE, macerator.getSteamMode());
        assertEquals(ResourceLocation.tryParse("gtceu:lp_steam_macerator"), macerator.getMachineIcon());

        // 6. Final check: cannot decrease below LP Steam
        assertFalse(widget.changeTier(-1));
        assertEquals(SteamMode.LOW_PRESSURE, macerator.getSteamMode());
        assertEquals(ResourceLocation.tryParse("gtceu:lp_steam_macerator"), macerator.getMachineIcon());
    }

    @Test
    void testMultiblockSwitchResetsSteamMode() {
        RecipeNode node = RecipeNode.create("Steam Turbine", 20.0, 1024.0, GTVoltageTier.HV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_turbine"));
        node.setGenerator(true);
        node.setSteamMode(SteamMode.HIGH_PRESSURE);
        assertEquals(SteamMode.HIGH_PRESSURE, node.getSteamMode());

        // Switch to multiblock: steam mode must be reset to NONE
        node.setMultiblock(true);
        assertEquals(SteamMode.NONE, node.getSteamMode());
        assertTrue(node.isMultiblock());
    }
}





