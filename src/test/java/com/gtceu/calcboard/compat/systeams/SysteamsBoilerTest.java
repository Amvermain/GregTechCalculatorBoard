package com.gtceu.calcboard.compat.systeams;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.PowerDisplayMode;
import com.gtceu.calcboard.api.type.RateTimeUnit;
import com.gtceu.calcboard.client.gui.util.FormatUtil;

import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Unit tests for Thermal Systeams composite boiler recipes and cross-mod GTCEu Steam Turbine balance.
 */
public class SysteamsBoilerTest {

    @BeforeAll
    public static void setup() {
        ModAdapterRegistry.init();
    }

    @Test
    public void testLapidaryBoilerCompositeRecipeAndScaling() {
        // Lapidary Boiler: Diamond (300,000 RF)
        // In Systeams: 0.5 mB Steam/RF -> 150,000 mB Steam, 0.25 Water/Steam -> 37,500 mB Water
        // Base rate: 150 mB/t Steam (3,000 mB/s), 37.5 mB/t Water (750 mB/s)
        // Base duration: 150,000 / 150 = 1,000 ticks = 50.0 seconds (eut = 0.0)
        RecipeNode boiler = RecipeNode.create("Lapidary Boiler (Diamond)", 1000.0, 0.0, GTVoltageTier.LV);
        boiler.setRecipeCategoryId(ResourceLocation.tryParse("systeams:boiling"));
        boiler.setMachineIcon(ResourceLocation.tryParse("systeams:lapidary_boiler"));
        boiler.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:diamond"), "Diamond", 1.0));
        boiler.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 37500.0));
        boiler.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 150000.0));
        boiler.setEnergyType(EnergyType.HEAT_OR_SELF);

        Assertions.assertEquals(EnergyType.HEAT_OR_SELF, boiler.getEnergyType());

        // 0. Base un-augmented rates:
        // Steam rate: 150,000 mB / 50.0s = 3,000 mB/s (= 150 mB/t) -> Exactly matches in-game GUI!
        // Water rate: 37,500 mB / 50.0s = 750 mB/s (= 37.5 mB/t) -> Exactly matches in-game GUI!
        Map<IngredientStack, Double> baseOutRates = boiler.calculateOutputRates();
        Assertions.assertEquals(3000.0, baseOutRates.values().iterator().next(), 0.001);

        Map<IngredientStack, Double> baseInRates = boiler.calculateInputRates();
        double waterRate = baseInRates.entrySet().stream()
                .filter(e -> e.getKey().getDisplayName().contains("Water"))
                .map(Map.Entry::getValue).findFirst().orElse(0.0);
        Assertions.assertEquals(750.0, waterRate, 0.001);

        // 1. Install HV Upgrade Kit (24x scale)
        CompoundTag hvKitTag = new CompoundTag();
        CompoundTag hvKitAug = new CompoundTag();
        hvKitAug.putInt("Scale", 24);
        hvKitTag.put("AugmentData", hvKitAug);
        MachineAddon hvKit = ThermalAugmentHelper.parseThermalAugmentTag(hvKitTag, "HV Upgrade Kit", ResourceLocation.tryParse("kubejs:hv_upgrade_kit"));
        boiler.addAddon(hvKit);

        Assertions.assertEquals(1, boiler.getTotalParallel()); // Thermal machines process 1 item per machine, scale is applied to duration/rate
        Assertions.assertEquals(0.0, boiler.getSingleMachineEUt(), 0.001);

        // Effective duration divided by 24: 1000 / 24 = 41.6667 ticks (2.0833s)
        // CPS: 20 / 41.6667 = 0.48 cycles/sec (28.8 diamonds/min)
        Assertions.assertEquals(0.48, boiler.getCyclesPerSecond(), 0.001);

        // Production rate across 24x scale: 150,000 mB * 0.48 = 72,000 mB/s Steam (= 3,600 mB/t)
        Map<IngredientStack, Double> outRates = boiler.calculateOutputRates();
        Assertions.assertEquals(1, outRates.size());
        double steamPerSec = outRates.values().iterator().next();
        Assertions.assertEquals(72000.0, steamPerSec, 0.001);

        // Input consumption: 1 * 0.48 = 0.48 diamonds/sec (28.8/min), 37,500 * 0.48 = 18,000 mB/s Water (= 900 mB/t)
        Map<IngredientStack, Double> inRates = boiler.calculateInputRates();
        Assertions.assertEquals(2, inRates.size());

        // 2. Test Lapidary Boiler with EV Upgrade Kit (48x) + 1x ARC (4.0x) + 205% Efficiency (2x MCI)
        RecipeNode evBoiler = RecipeNode.create("Lapidary Boiler (Diamond)", 1000.0, 0.0, GTVoltageTier.LV);
        evBoiler.setRecipeCategoryId(ResourceLocation.tryParse("systeams:boiling"));
        evBoiler.setMachineIcon(ResourceLocation.tryParse("systeams:lapidary_boiler"));
        evBoiler.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:diamond"), "Diamond", 1.0));
        evBoiler.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 37500.0));
        evBoiler.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 150000.0));

        CompoundTag evKitTag = new CompoundTag();
        CompoundTag evKitAug = new CompoundTag();
        evKitAug.putInt("Scale", 48);
        evKitTag.put("AugmentData", evKitAug);
        evBoiler.addAddon(ThermalAugmentHelper.parseThermalAugmentTag(evKitTag, "EV Upgrade Kit", ResourceLocation.tryParse("kubejs:ev_upgrade_kit")));

        CompoundTag arcTag = new CompoundTag();
        CompoundTag arcAug = new CompoundTag();
        arcAug.putFloat("DynamoPower", 3.0f); // 1.0 + 3.0 = 4.0x Power
        arcAug.putFloat("DynamoEnergy", 0.8f);
        arcTag.put("AugmentData", arcAug);
        evBoiler.addAddon(ThermalAugmentHelper.parseThermalAugmentTag(arcTag, "EV ARC Kit", ResourceLocation.tryParse("kubejs:ev_arc_kit")));

        CompoundTag mciTag = new CompoundTag();
        CompoundTag mciAug = new CompoundTag();
        mciAug.putFloat("DynamoEnergy", 1.6f);
        mciTag.put("AugmentData", mciAug);
        evBoiler.addAddon(ThermalAugmentHelper.parseThermalAugmentTag(mciTag, "EV MCI Kit 1", ResourceLocation.tryParse("kubejs:ev_mci_kit")));
        evBoiler.addAddon(ThermalAugmentHelper.parseThermalAugmentTag(mciTag, "EV MCI Kit 2", ResourceLocation.tryParse("kubejs:ev_mci_kit")));

        // Fuel Energy Mult: 0.8 * 1.6 * 1.6 = 2.048 (205% Efficiency)
        Assertions.assertEquals(2.048, evBoiler.getCombinedDurationMultiplier(), 0.001);

        // Duration: 1000 ticks * (2.048 / (48 * 4.0)) = 10.6667 ticks (0.5333s)
        Assertions.assertEquals(10.6667, evBoiler.getOverclockResult().durationTicks(), 0.001);
        Assertions.assertEquals(1.875, evBoiler.getCyclesPerSecond(), 0.001); // 1.875 diamonds/s

        // Steam prod: 28,800 mB/t = 576,000 mB/s = 576 B/s!
        // Water cons: 7,200 mB/t = 144,000 mB/s = 144 B/s!
        Map<IngredientStack, Double> evOutRates = evBoiler.calculateOutputRates();
        double evSteamRate = evOutRates.values().iterator().next();
        Assertions.assertEquals(576000.0, evSteamRate, 0.001); // 28,800 mB/t = 576,000 mB/s = 576 B/s

        Map<IngredientStack, Double> evInRates = evBoiler.calculateInputRates();
        double evWaterRate = evInRates.entrySet().stream()
                .filter(e -> e.getKey().getDisplayName().contains("Water"))
                .map(Map.Entry::getValue).findFirst().orElse(0.0);
        Assertions.assertEquals(144000.0, evWaterRate, 0.001); // 7,200 mB/t = 144,000 mB/s = 144 B/s

        // 3. Verify Per-Slot Output/Input Rates & Per-Tick Formatting (Image 1 & Image 2 Verification)
        Assertions.assertEquals(576000.0, evBoiler.getOutputSlotRate(0, true), 0.001); // 576,000 mB/s = 28,800 mB/t
        Assertions.assertEquals(144000.0, evBoiler.getInputSlotRate(1, true), 0.001); // 144,000 mB/s = 7,200 mB/t
        Assertions.assertEquals(1.875, evBoiler.getInputSlotRate(0, true), 0.001); // 1.875 diamonds/s

        // Test in PER_TICK mode:
        com.gtceu.calcboard.client.gui.util.FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_TICK);
        try {
            String steamTickStr = com.gtceu.calcboard.client.gui.util.FormatUtil.formatRate(evBoiler.getOutputSlotRate(0, true), true);
            Assertions.assertEquals("28.8 B/t", steamTickStr); // Exact match with in-game 28800 mB/t!

            String waterTickStr = com.gtceu.calcboard.client.gui.util.FormatUtil.formatRate(evBoiler.getInputSlotRate(1, true), true);
            Assertions.assertEquals("7.2 B/t", waterTickStr); // Exact match with in-game 7200 mB/t!

            String diamondTickStr = com.gtceu.calcboard.client.gui.util.FormatUtil.formatRate(evBoiler.getInputSlotRate(0, true), false);
            Assertions.assertEquals("0.094/t", diamondTickStr); // 1.875 / 20 = 0.09375 -> 0.094/t!
        } finally {
            com.gtceu.calcboard.client.gui.util.FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_SECOND);
        }

        // 4. Verify GUI Header and Tooltip match the in-game display
        var adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(evBoiler);
        String formattedStats = adapter.formatEnergyStats(evBoiler, PowerDisplayMode.EUT);
        Assertions.assertTrue(formattedStats.contains("576") && formattedStats.contains("Steam"), "Header should format 576k/s Steam: " + formattedStats);

        List<net.minecraft.network.chat.Component> tooltip = adapter.buildEnergyTooltip(evBoiler);
        String tooltipFullText = tooltip.stream().map(net.minecraft.network.chat.Component::getString).reduce("", (a, b) -> a + "\n" + b);
        Assertions.assertTrue(tooltipFullText.contains("28,800.00 mB/t"), "Tooltip should contain exact 28,800.00 mB/t: " + tooltipFullText);
        Assertions.assertTrue(tooltipFullText.contains("576,000.00 mB/s"), "Tooltip should contain exact 576,000.00 mB/s: " + tooltipFullText);
        Assertions.assertTrue(tooltipFullText.contains("0.5333s") || tooltipFullText.contains("1.8750 cycles/s"), "Tooltip should contain exact duration & CPS: " + tooltipFullText);
    }

    @Test
    public void testSysteamsBoilerConnectedToGTCEuSteamTurbine() {
        FlowGraph graph = new FlowGraph();

        // 1. Lapidary Boiler producing Steam
        RecipeNode boiler = RecipeNode.create("Lapidary Boiler (Diamond)", 1500.0, 0.0, GTVoltageTier.LV);
        boiler.setRecipeCategoryId(ResourceLocation.tryParse("systeams:boiling"));
        boiler.setMachineIcon(ResourceLocation.tryParse("systeams:lapidary_boiler"));
        IngredientStack diamondIn = IngredientStack.item(ResourceLocation.tryParse("minecraft:diamond"), "Diamond", 1.0);
        IngredientStack waterIn = IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 300000.0);
        IngredientStack steamOut = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 300000.0);
        boiler.addInput(diamondIn);
        boiler.addInput(waterIn);
        boiler.addOutput(steamOut);

        // 2. GTCEu Steam Turbine consuming Steam
        RecipeNode turbine = RecipeNode.create("Steam Turbine", 20.0, 32.0, GTVoltageTier.LV);
        turbine.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_turbine"));
        turbine.setGenerator(true);
        IngredientStack steamIn = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 640.0);
        turbine.addInput(steamIn);

        graph.addNode(boiler);
        graph.addNode(turbine);

        // Connect Boiler Steam -> Turbine Steam
        graph.addConnection(boiler.getId(), 0, turbine.getId(), 0);

        // Solve graph
        BalanceSummary summary = graph.computeSummary();
        Assertions.assertNotNull(summary);

        // Raw inputs should require Diamond & Water, no net EU consumed (only generated!)
        Assertions.assertTrue(summary.rawInputs().keySet().stream().anyMatch(s -> s.getDisplayName().contains("Diamond") || (s.getId() != null && s.getId().getPath().contains("diamond"))));
        Assertions.assertTrue(summary.rawInputs().keySet().stream().anyMatch(s -> s.getDisplayName().contains("Water") || (s.getId() != null && s.getId().getPath().contains("water"))));
    }

    @Test
    public void testSteamDynamoGenerator() {
        // Steam Dynamo: 1,000 mB Steam -> 2,000 RF energy (500 EU)
        // Base Power: 400 RF/t = 100 EU/t (MV tier equivalent)
        // Duration: 2,000 / 400 = 5 ticks = 0.25s
        RecipeNode dynamo = RecipeNode.create("Steam Dynamo (Steam)", 5.0, 400.0, GTVoltageTier.LV);
        dynamo.setEnergyType(EnergyType.ELECTRIC_FE);
        dynamo.setRecipeCategoryId(ResourceLocation.tryParse("systeams:steam"));
        dynamo.setMachineIcon(ResourceLocation.tryParse("systeams:steam_dynamo"));
        dynamo.setGenerator(true);
        dynamo.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 1000.0));

        Assertions.assertTrue(dynamo.isGenerator());
        Assertions.assertEquals(EnergyType.ELECTRIC_FE, dynamo.getEnergyType());
        Assertions.assertEquals(400.0, dynamo.getSingleMachineEUt(), 0.001); // 400 RF/t
        Assertions.assertEquals(4.0, dynamo.getCyclesPerSecond(), 0.001);

        // Net power: +400 RF/t (= +100 EU/t)
        // Net Steam consumption: 1,000 mB * 4.0 = 4,000 mB/s (= 200 mB/t Steam)
        Map<IngredientStack, Double> inRates = dynamo.calculateInputRates();
        Assertions.assertEquals(4000.0, inRates.values().iterator().next(), 0.001);

        // 2. Install EV Upgrade Kit (48x) + 1x ARC (4.0x) + 2x MCI (2.048x efficiency)
        CompoundTag evKitTag = new CompoundTag();
        CompoundTag evKitAug = new CompoundTag();
        evKitAug.putInt("Scale", 48);
        evKitTag.put("AugmentData", evKitAug);
        dynamo.addAddon(ThermalAugmentHelper.parseThermalAugmentTag(evKitTag, "EV Upgrade Kit", ResourceLocation.tryParse("kubejs:ev_upgrade_kit")));

        CompoundTag arcTag = new CompoundTag();
        CompoundTag arcAug = new CompoundTag();
        arcAug.putFloat("DynamoPower", 3.0f); // 1.0 + 3.0 = 4.0x Power
        arcAug.putFloat("DynamoEnergy", 0.8f);
        arcTag.put("AugmentData", arcAug);
        dynamo.addAddon(ThermalAugmentHelper.parseThermalAugmentTag(arcTag, "EV ARC Kit", ResourceLocation.tryParse("kubejs:ev_arc_kit")));

        CompoundTag mciTag = new CompoundTag();
        CompoundTag mciAug = new CompoundTag();
        mciAug.putFloat("DynamoEnergy", 1.6f);
        mciTag.put("AugmentData", mciAug);
        dynamo.addAddon(ThermalAugmentHelper.parseThermalAugmentTag(mciTag, "EV MCI Kit 1", ResourceLocation.tryParse("kubejs:ev_mci_kit")));
        dynamo.addAddon(ThermalAugmentHelper.parseThermalAugmentTag(mciTag, "EV MCI Kit 2", ResourceLocation.tryParse("kubejs:ev_mci_kit")));

        // Generation: 400 RF/t * 48 * 4.0 = 76,800 RF/t
        Assertions.assertEquals(76800.0, dynamo.getSingleMachineEUt(), 0.001);

        // Raw duration is sub-tick: 5.0 * 2.048 / 192 = 0.05333 ticks -> batchesPerTick = 18.75
        // CPS = 20 * 18.75 = 375.0 cycles/s
        Assertions.assertEquals(375.0, dynamo.getCyclesPerSecond(), 0.1);

        // Steam consumption: 1,000 mB * 375.0 = 375,000 mB/s = 375 B/s = 18,750 mB/t = 18.75 B/t!
        Assertions.assertEquals(375000.0, dynamo.getInputSlotRate(0, true), 0.1);

        com.gtceu.calcboard.client.gui.util.FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_TICK);
        try {
            String steamTickStr = com.gtceu.calcboard.client.gui.util.FormatUtil.formatRate(dynamo.getInputSlotRate(0, true), true);
            Assertions.assertEquals("18.75 B/t", steamTickStr); // Exact match 18,750 mB/t -> 18.75 B/t
        } finally {
            com.gtceu.calcboard.client.gui.util.FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_SECOND);
        }
    }

    @Test
    public void testLapidaryDynamoToBoilerToggle() {
        // 1. Create Lapidary Dynamo (Diamond: 300,000 RF, 200 RF/t -> 1500 ticks)
        RecipeNode dynamo = RecipeNode.create("Lapidary Dynamo (Diamond)", 1500.0, 200.0, GTVoltageTier.LV);
        dynamo.setRecipeCategoryId(ResourceLocation.tryParse("thermal:lapidary_fuel"));
        dynamo.setMachineIcon(ResourceLocation.tryParse("thermal:dynamo_lapidary"));
        dynamo.setEnergyType(EnergyType.ELECTRIC_FE);
        dynamo.setGenerator(true);
        dynamo.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:diamond"), "Diamond", 1.0));
        dynamo.getProperties().set(com.gtceu.calcboard.compat.thermal.ThermalProperties.THERMAL_BASE_ENERGY_RF, 300000.0);

        Assertions.assertTrue(com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.isDynamoToBoilerConvertible(dynamo));

        com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.toggleDynamoBoilerMode(dynamo);

        Assertions.assertFalse(dynamo.isGenerator());
        Assertions.assertEquals(EnergyType.HEAT_OR_SELF, dynamo.getEnergyType());
        Assertions.assertEquals(0.0, dynamo.getBaseEUt(), 0.001);
        Assertions.assertEquals(ResourceLocation.tryParse("systeams:lapidary_boiler"), dynamo.getMachineIcon());
        Assertions.assertEquals(ResourceLocation.tryParse("thermal:lapidary_fuel"), dynamo.getRecipeCategoryId());
        Assertions.assertEquals(2, dynamo.getInputs().size());
        Assertions.assertEquals(1, dynamo.getOutputs().size());

        IngredientStack steamOut = dynamo.getOutputs().get(0);
        Assertions.assertEquals(150000.0, steamOut.getAmount(), 0.001);

        IngredientStack waterIn = dynamo.getInputs().stream().filter(IngredientStack::isFluid).findFirst().orElse(null);
        Assertions.assertNotNull(waterIn);
        Assertions.assertEquals(37500.0, waterIn.getAmount(), 0.001);
        Assertions.assertTrue(waterIn.hasAlternatives());
        Assertions.assertTrue(waterIn.getAlternatives().contains(ResourceLocation.tryParse("minecraft:water")));

        com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.toggleDynamoBoilerMode(dynamo);

        Assertions.assertTrue(dynamo.isGenerator());
        Assertions.assertEquals(EnergyType.ELECTRIC_FE, dynamo.getEnergyType());
        Assertions.assertEquals(200.0, dynamo.getBaseEUt(), 0.001);
        Assertions.assertEquals(ResourceLocation.tryParse("thermal:dynamo_lapidary"), dynamo.getMachineIcon());
        Assertions.assertEquals(ResourceLocation.tryParse("thermal:lapidary_fuel"), dynamo.getRecipeCategoryId());
        Assertions.assertEquals(1, dynamo.getInputs().size());
        Assertions.assertTrue(dynamo.getOutputs().isEmpty());
        Assertions.assertEquals(1500.0, dynamo.getBaseDurationTicks(), 0.001);
    }

    @Test
    public void testBoilerFluidAlternativesSync() {
        com.gtceu.calcboard.client.gui.search.RecipeSearchCacheManager.setGlobalRecipesForTesting(List.of(
                new com.gtceu.calcboard.api.model.SearchableRecipe(
                        "mock_boil_1", null, "Steam", "systeams", "systeams:boiling", "Boiling", "", "",
                        new ResourceLocation[]{ResourceLocation.tryParse("minecraft:water")},
                        new ResourceLocation[]{ResourceLocation.tryParse("gtceu:steam")},
                        new String[]{"Water"}, new String[]{"Steam"}, true
                ),
                new com.gtceu.calcboard.api.model.SearchableRecipe(
                        "mock_boil_2", null, "Warm Steam", "systeams", "systeams:boiling", "Boiling", "", "",
                        new ResourceLocation[]{ResourceLocation.tryParse("gtceu:steam")},
                        new ResourceLocation[]{ResourceLocation.tryParse("systeams:warm_steam")},
                        new String[]{"Steam"}, new String[]{"Warm Steam"}, true
                ),
                new com.gtceu.calcboard.api.model.SearchableRecipe(
                        "mock_boil_3", null, "Hot Steam", "systeams", "systeams:boiling", "Boiling", "", "",
                        new ResourceLocation[]{ResourceLocation.tryParse("systeams:warm_steam")},
                        new ResourceLocation[]{ResourceLocation.tryParse("systeams:hot_steam")},
                        new String[]{"Warm Steam"}, new String[]{"Hot Steam"}, true
                ),
                new com.gtceu.calcboard.api.model.SearchableRecipe(
                        "mock_boil_4", null, "Superhot Steam", "systeams", "systeams:boiling", "Boiling", "", "",
                        new ResourceLocation[]{ResourceLocation.tryParse("systeams:hot_steam")},
                        new ResourceLocation[]{ResourceLocation.tryParse("systeams:superhot_steam")},
                        new String[]{"Hot Steam"}, new String[]{"Superhot Steam"}, true
                )
        ));

        RecipeNode boiler = RecipeNode.create("Lapidary Boiler (Diamond)", 1000.0, 0.0, GTVoltageTier.LV);
        boiler.setRecipeCategoryId(ResourceLocation.tryParse("thermal:lapidary_fuel"));
        boiler.setMachineIcon(ResourceLocation.tryParse("systeams:lapidary_boiler"));
        boiler.setEnergyType(EnergyType.HEAT_OR_SELF);
        boiler.setGenerator(false);
        boiler.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:diamond"), "Diamond", 1.0));

        IngredientStack waterIn = IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 37500.0);
        waterIn.setAlternatives(List.of(
                ResourceLocation.tryParse("minecraft:water"),
                ResourceLocation.tryParse("gtceu:steam"),
                ResourceLocation.tryParse("systeams:warm_steam"),
                ResourceLocation.tryParse("systeams:hot_steam")
        ));
        boiler.addInput(waterIn);
        boiler.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 150000.0));
        boiler.getProperties().set(com.gtceu.calcboard.compat.thermal.ThermalProperties.THERMAL_BASE_ENERGY_RF, 300000.0);

        com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.updateBoilerFluidRecipe(boiler, ResourceLocation.tryParse("gtceu:steam"));

        IngredientStack currentFluidIn = boiler.getInputs().stream().filter(IngredientStack::isFluid).findFirst().orElse(null);
        Assertions.assertNotNull(currentFluidIn);
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:steam"), currentFluidIn.getId());

        IngredientStack currentOut = boiler.getOutputs().get(0);
        Assertions.assertNotNull(currentOut);
        Assertions.assertEquals(ResourceLocation.tryParse("systeams:warm_steam"), currentOut.getId());
        Assertions.assertEquals("Warm Steam", currentOut.getDisplayName());
        Assertions.assertTrue(currentOut.getAmount() > 0);

        com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.updateBoilerFluidRecipe(boiler, ResourceLocation.tryParse("systeams:warm_steam"));

        currentOut = boiler.getOutputs().get(0);
        Assertions.assertEquals(ResourceLocation.tryParse("systeams:hot_steam"), currentOut.getId());
        Assertions.assertEquals("Hot Steam", currentOut.getDisplayName());

        com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.updateBoilerFluidRecipe(boiler, ResourceLocation.tryParse("systeams:hot_steam"));

        currentOut = boiler.getOutputs().get(0);
        Assertions.assertEquals(ResourceLocation.tryParse("systeams:superhot_steam"), currentOut.getId());
        Assertions.assertEquals("Superhot Steam", currentOut.getDisplayName());
    }

    @Test
    public void testSysteamsBoilerUpgradeKitExclusivityAndLimits() {
        RecipeNode boiler = RecipeNode.create("Lapidary Boiler", 1500.0, 0.0, GTVoltageTier.LV);
        boiler.setRecipeCategoryId(ResourceLocation.tryParse("systeams:boiling"));
        boiler.setMachineIcon(ResourceLocation.tryParse("systeams:lapidary_boiler"));

        var adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(boiler);
        Assertions.assertTrue(adapter instanceof com.gtceu.calcboard.compat.systeams.SysteamsModAdapter);

        CompoundTag lvTag = new CompoundTag();
        lvTag.putFloat("Scale", 6.0f);
        MachineAddon lvKit = ThermalAugmentHelper.parseThermalAugmentTag(lvTag, "LV Upgrade Kit", ResourceLocation.tryParse("kubejs:lv_upgrade_kit"));

        CompoundTag hvTag = new CompoundTag();
        hvTag.putFloat("Scale", 24.0f);
        MachineAddon hvKit = ThermalAugmentHelper.parseThermalAugmentTag(hvTag, "HV Upgrade Kit", ResourceLocation.tryParse("kubejs:hv_upgrade_kit"));

        CompoundTag evTag = new CompoundTag();
        evTag.putFloat("Scale", 48.0f);
        MachineAddon evKit = ThermalAugmentHelper.parseThermalAugmentTag(evTag, "EV Upgrade Kit", ResourceLocation.tryParse("kubejs:ev_upgrade_kit"));

        CompoundTag augTag = new CompoundTag();
        CompoundTag augData = new CompoundTag();
        augData.putString("Type", "Dynamo");
        augData.putFloat("DynamoEnergy", 0.9f);
        augTag.put("AugmentData", augData);
        MachineAddon aug = ThermalAugmentHelper.parseThermalAugmentTag(augTag, "Auxiliary Reaction Chamber", ResourceLocation.tryParse("thermal:dynamo_output_augment"));

        // 1. Install LV Kit -> 1 addon
        adapter.handleInstallAddon(boiler, lvKit, false);
        Assertions.assertEquals(1, boiler.getAddons().size());
        Assertions.assertEquals(6, boiler.getCombinedParallelMultiplier());

        // 2. Install HV Kit -> Replaces LV Kit (still 1 addon)
        adapter.handleInstallAddon(boiler, hvKit, false);
        Assertions.assertEquals(1, boiler.getAddons().size());
        Assertions.assertEquals(24, boiler.getCombinedParallelMultiplier());

        // 3. Install EV Kit -> Replaces HV Kit (still 1 addon)
        adapter.handleInstallAddon(boiler, evKit, false);
        Assertions.assertEquals(1, boiler.getAddons().size());
        Assertions.assertEquals(48, boiler.getCombinedParallelMultiplier());

        // 4. Installing exact same EV Kit again is not allowed (toggles/uninstalls)
        Assertions.assertFalse(adapter.canInstallAddon(boiler, evKit));

        // 5. Install 3 regular augments -> 4 total addons (1 kit + 3 augs)
        adapter.handleInstallAddon(boiler, aug, true);
        Assertions.assertEquals(4, boiler.getAddons().size());
        Assertions.assertEquals(48, boiler.getCombinedParallelMultiplier());

        // 6. Cannot exceed 3 regular augments
        Assertions.assertFalse(adapter.canInstallAddon(boiler, aug));

        // 7. Replacing EV Kit with LV Kit works cleanly without disrupting the 3 augments
        Assertions.assertTrue(adapter.canInstallAddon(boiler, lvKit));
        adapter.handleInstallAddon(boiler, lvKit, false);
        Assertions.assertEquals(4, boiler.getAddons().size());
        Assertions.assertEquals(6, boiler.getCombinedParallelMultiplier());
    }

    @Test
    public void testCascadedBoilerSummaryDoesNotTreatIntermediateSteamsAsRawInputs() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation diamondId = ResourceLocation.tryParse("minecraft:diamond");
        ResourceLocation steamId = ResourceLocation.tryParse("gtceu:steam");
        ResourceLocation warmSteamId = ResourceLocation.tryParse("systeams:warm_steam");
        ResourceLocation hotSteamId = ResourceLocation.tryParse("systeams:hot_steam");
        ResourceLocation superhotSteamId = ResourceLocation.tryParse("systeams:superhot_steam");

        // Boiler 1: [Diamond, Water] -> [Steam] (Count: 1)
        RecipeNode b1 = new RecipeNode("b1", "Boiler 1", 20.0, 0.0, GTVoltageTier.LV);
        b1.getInputs().add(IngredientStack.item(diamondId, "Diamond", 1.0));
        b1.getOutputs().add(IngredientStack.fluid(steamId, "Steam", 576.0));
        b1.setMachineCount(1.0);
        graph.addNode(b1);

        // Boiler 2: [Diamond, Steam] -> [Warm Steam] (Count: 4)
        RecipeNode b2 = new RecipeNode("b2", "Boiler 2", 20.0, 0.0, GTVoltageTier.LV);
        b2.getInputs().add(IngredientStack.item(diamondId, "Diamond", 1.0));
        IngredientStack b2InFluid = IngredientStack.fluid(steamId, "Steam", 140.0);
        b2InFluid.addAlternative(warmSteamId);
        b2InFluid.addAlternative(hotSteamId);
        b2.getInputs().add(b2InFluid);
        b2.getOutputs().add(IngredientStack.fluid(warmSteamId, "Warm Steam", 576.0));
        b2.setMachineCount(4.0);
        graph.addNode(b2);

        // Boiler 3: [Diamond, Warm Steam] -> [Hot Steam] (Count: 16)
        RecipeNode b3 = new RecipeNode("b3", "Boiler 3", 20.0, 0.0, GTVoltageTier.LV);
        b3.getInputs().add(IngredientStack.item(diamondId, "Diamond", 1.0));
        IngredientStack b3InFluid = IngredientStack.fluid(warmSteamId, "Warm Steam", 140.0);
        b3InFluid.addAlternative(steamId);
        b3InFluid.addAlternative(hotSteamId);
        b3.getInputs().add(b3InFluid);
        b3.getOutputs().add(IngredientStack.fluid(hotSteamId, "Hot Steam", 576.0));
        b3.setMachineCount(16.0);
        graph.addNode(b3);

        // Connect pipeline: b1 -> b2 -> b3
        graph.addConnection(b1.getId(), 0, b2.getId(), 1);
        graph.addConnection(b2.getId(), 0, b3.getId(), 1);

        BalanceSummary summary = graph.computeSummary();

        // Raw inputs should ONLY contain Diamond (and Water if unconnected), NEVER Steam or Warm Steam!
        IngredientStack steamKey = IngredientStack.fluid(steamId, "Steam", 1.0);
        IngredientStack warmSteamKey = IngredientStack.fluid(warmSteamId, "Warm Steam", 1.0);
        IngredientStack diamondKey = IngredientStack.item(diamondId, "Diamond", 1.0);

        Assertions.assertTrue(summary.rawInputs().containsKey(diamondKey), "Diamond must be recognized as raw input");
        Assertions.assertFalse(summary.rawInputs().containsKey(steamKey), "Intermediate Steam must NOT be listed in raw inputs");
        Assertions.assertFalse(summary.rawInputs().containsKey(warmSteamKey), "Intermediate Warm Steam must NOT be listed in raw inputs");
    }
}



