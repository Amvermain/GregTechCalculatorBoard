package com.gtceu.calcboard;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtceu.calcboard.compat.systeams.SysteamsGuiHandler;
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
        MachineAddon hvKit = DynamicAddonCrawler.parseThermalAugmentTag(hvKitTag, "HV Upgrade Kit", ResourceLocation.tryParse("kubejs:hv_upgrade_kit"));
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
        evBoiler.addAddon(DynamicAddonCrawler.parseThermalAugmentTag(evKitTag, "EV Upgrade Kit", ResourceLocation.tryParse("kubejs:ev_upgrade_kit")));

        CompoundTag arcTag = new CompoundTag();
        CompoundTag arcAug = new CompoundTag();
        arcAug.putFloat("DynamoPower", 3.0f); // 1.0 + 3.0 = 4.0x Power
        arcAug.putFloat("DynamoEnergy", 0.8f);
        arcTag.put("AugmentData", arcAug);
        evBoiler.addAddon(DynamicAddonCrawler.parseThermalAugmentTag(arcTag, "EV ARC Kit", ResourceLocation.tryParse("kubejs:ev_arc_kit")));

        CompoundTag mciTag = new CompoundTag();
        CompoundTag mciAug = new CompoundTag();
        mciAug.putFloat("DynamoEnergy", 1.6f);
        mciTag.put("AugmentData", mciAug);
        evBoiler.addAddon(DynamicAddonCrawler.parseThermalAugmentTag(mciTag, "EV MCI Kit 1", ResourceLocation.tryParse("kubejs:ev_mci_kit")));
        evBoiler.addAddon(DynamicAddonCrawler.parseThermalAugmentTag(mciTag, "EV MCI Kit 2", ResourceLocation.tryParse("kubejs:ev_mci_kit")));

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
        com.gtceu.calcboard.client.gui.FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_TICK);
        try {
            String steamTickStr = com.gtceu.calcboard.client.gui.FormatUtil.formatRate(evBoiler.getOutputSlotRate(0, true), true);
            Assertions.assertEquals("28.8 B/t", steamTickStr); // Exact match with in-game 28800 mB/t!

            String waterTickStr = com.gtceu.calcboard.client.gui.FormatUtil.formatRate(evBoiler.getInputSlotRate(1, true), true);
            Assertions.assertEquals("7.2 B/t", waterTickStr); // Exact match with in-game 7200 mB/t!

            String diamondTickStr = com.gtceu.calcboard.client.gui.FormatUtil.formatRate(evBoiler.getInputSlotRate(0, true), false);
            Assertions.assertEquals("0.094/t", diamondTickStr); // 1.875 / 20 = 0.09375 -> 0.094/t!
        } finally {
            com.gtceu.calcboard.client.gui.FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_SECOND);
        }

        // 4. Verify GUI Header and Tooltip match the in-game display
        String formattedStats = SysteamsGuiHandler.formatEnergyStats(evBoiler, PowerDisplayMode.EUT);
        Assertions.assertTrue(formattedStats.contains("576") && formattedStats.contains("Steam"), "Header should format 576k/s Steam: " + formattedStats);

        List<net.minecraft.network.chat.Component> tooltip = SysteamsGuiHandler.buildEnergyTooltip(evBoiler);
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
        dynamo.addAddon(DynamicAddonCrawler.parseThermalAugmentTag(evKitTag, "EV Upgrade Kit", ResourceLocation.tryParse("kubejs:ev_upgrade_kit")));

        CompoundTag arcTag = new CompoundTag();
        CompoundTag arcAug = new CompoundTag();
        arcAug.putFloat("DynamoPower", 3.0f); // 1.0 + 3.0 = 4.0x Power
        arcAug.putFloat("DynamoEnergy", 0.8f);
        arcTag.put("AugmentData", arcAug);
        dynamo.addAddon(DynamicAddonCrawler.parseThermalAugmentTag(arcTag, "EV ARC Kit", ResourceLocation.tryParse("kubejs:ev_arc_kit")));

        CompoundTag mciTag = new CompoundTag();
        CompoundTag mciAug = new CompoundTag();
        mciAug.putFloat("DynamoEnergy", 1.6f);
        mciTag.put("AugmentData", mciAug);
        dynamo.addAddon(DynamicAddonCrawler.parseThermalAugmentTag(mciTag, "EV MCI Kit 1", ResourceLocation.tryParse("kubejs:ev_mci_kit")));
        dynamo.addAddon(DynamicAddonCrawler.parseThermalAugmentTag(mciTag, "EV MCI Kit 2", ResourceLocation.tryParse("kubejs:ev_mci_kit")));

        // Generation: 400 RF/t * 48 * 4.0 = 76,800 RF/t
        Assertions.assertEquals(76800.0, dynamo.getSingleMachineEUt(), 0.001);

        // Raw duration is sub-tick: 5.0 * 2.048 / 192 = 0.05333 ticks -> batchesPerTick = 18.75
        // CPS = 20 * 18.75 = 375.0 cycles/s
        Assertions.assertEquals(375.0, dynamo.getCyclesPerSecond(), 0.1);

        // Steam consumption: 1,000 mB * 375.0 = 375,000 mB/s = 375 B/s = 18,750 mB/t = 18.75 B/t!
        Assertions.assertEquals(375000.0, dynamo.getInputSlotRate(0, true), 0.1);

        com.gtceu.calcboard.client.gui.FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_TICK);
        try {
            String steamTickStr = com.gtceu.calcboard.client.gui.FormatUtil.formatRate(dynamo.getInputSlotRate(0, true), true);
            Assertions.assertEquals("18.75 B/t", steamTickStr); // Exact match 18,750 mB/t -> 18.75 B/t
        } finally {
            com.gtceu.calcboard.client.gui.FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_SECOND);
        }
    }
}
