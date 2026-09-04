package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.type.GTVoltageTier;

import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.api.property.NodeBadgeRegistry;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.RecipePropertyExtractorPipeline;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FusionReactorSimulationTest {

    @org.junit.jupiter.api.BeforeAll
    public static void setup() {
        com.gtceu.calcboard.compat.ModAdapterRegistry.init();
    }

    @Test
    @DisplayName("Fusion tier and minimum voltage tier determination based on eu_to_start")
    void testFusionTierDetermination() {
        // Mk1: <= 160M EU (e.g. Helium Plasma, Europium)
        RecipeNode mk1 = RecipeNode.create("Fusion Mk1 - Helium Plasma", 32, 16384, GTVoltageTier.LuV);
        mk1.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:fusion_reactor"));
        mk1.setEuToStart(160_000_000L);

        assertTrue(mk1.isFusion());
        assertEquals(1, mk1.getFusionTier());
        assertEquals(GTVoltageTier.LuV, mk1.getMinFusionVoltageTier());
        assertEquals(160_000_000L, mk1.getEuToStart());

        // Mk2: <= 320M EU (e.g. Oxygen Plasma, Americium)
        RecipeNode mk2 = RecipeNode.create("Fusion Mk2 - Americium", 40, 32768, GTVoltageTier.ZPM);
        mk2.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:fusion_reactor"));
        mk2.setEuToStart(320_000_000L);

        assertTrue(mk2.isFusion());
        assertEquals(2, mk2.getFusionTier());
        assertEquals(GTVoltageTier.ZPM, mk2.getMinFusionVoltageTier());
        assertEquals(320_000_000L, mk2.getEuToStart());

        // Mk3: > 320M EU (e.g. Iron Plasma, Duranium)
        RecipeNode mk3 = RecipeNode.create("Fusion Mk3 - Iron Plasma", 64, 65536, GTVoltageTier.UV);
        mk3.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:fusion_reactor"));
        mk3.setEuToStart(640_000_000L);

        assertTrue(mk3.isFusion());
        assertEquals(3, mk3.getFusionTier());
        assertEquals(GTVoltageTier.UV, mk3.getMinFusionVoltageTier());
        assertEquals(640_000_000L, mk3.getEuToStart());
    }

    @Test
    @DisplayName("Voltage tier clamping and overclock tier delta for Fusion Reactors")
    void testVoltageTierClampingAndOverclocking() {
        RecipeNode mk2 = RecipeNode.create("Fusion Mk2", 40, 32768, GTVoltageTier.ZPM);
        mk2.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:fusion_reactor"));
        mk2.setEuToStart(320_000_000L); // Mk2 min voltage is ZPM

        // Initial target tier should automatically elevate to ZPM
        assertEquals(GTVoltageTier.ZPM, mk2.getTargetTier());

        // Attempting to set lower tier (LuV or HV) should clamp to ZPM
        mk2.setTargetTier(GTVoltageTier.LuV);
        assertEquals(GTVoltageTier.ZPM, mk2.getTargetTier());
        assertEquals(0, mk2.getTierDelta());

        // Overclocking to UV (1 tier above ZPM)
        mk2.setTargetTier(GTVoltageTier.UV);
        assertEquals(GTVoltageTier.UV, mk2.getTargetTier());
        assertEquals(1, mk2.getTierDelta());

        // Overclocking to UHV (2 tiers above ZPM)
        mk2.setTargetTier(GTVoltageTier.UHV);
        assertEquals(GTVoltageTier.UHV, mk2.getTargetTier());
        assertEquals(2, mk2.getTierDelta());
    }

    @Test
    @DisplayName("Fusion IV recipe correctly overclocks to LuV with 2:2 rule (double EU, half duration)")
    void testFusionEvRecipeOverclocksToLuV() {
        // Base IV fusion recipe (4096 EU/t): 144 ticks (7.20s), 40M EU ignition
        RecipeNode node = RecipeNode.create("Fusion Mk1 - D-T", 144, 4096, GTVoltageTier.IV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:fusion_reactor"));
        node.setEuToStart(40_000_000L);

        assertEquals(GTVoltageTier.IV, node.getRecipeTier());
        assertEquals(GTVoltageTier.LuV, node.getTargetTier()); // Target clamped to min fusion tier LuV
        assertEquals(1, node.getTierDelta()); // LuV (6) - IV (5) = 1

        var oc = node.getOverclockResult();
        assertEquals(72.0, oc.durationTicks(), 0.001); // 72 ticks = 3.60s (half the base duration)
        assertEquals(8192.0, oc.eut(), 0.001); // 8,192 EU/t (2x base energy cost under 2:2 fusion rule)
        assertEquals(1, oc.overclocks());
    }

    @Test
    @DisplayName("Fusion Reactor enforces matching Energy Hatch tier")
    void testFusionEnergyHatchTierRestriction() {
        RecipeNode mk1 = RecipeNode.create("Fusion Mk1", 144, 4096, GTVoltageTier.IV);
        mk1.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:fusion_reactor"));
        mk1.setEuToStart(40_000_000L); // targetTier is LuV

        var adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(mk1);

        // LuV energy hatch on LuV fusion reactor -> Compatible
        var luvHatch = new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon("gtceu:energy_hatch_luv", "LuV Energy Hatch", "", null, GTVoltageTier.LuV, 2);
        assertTrue(adapter.isAddonCompatible(mk1, luvHatch));

        // ZPM energy hatch on LuV fusion reactor -> Incompatible (Must build Mk2 for ZPM)
        var zpmHatch = new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon("gtceu:energy_hatch_zpm", "ZPM Energy Hatch", "", null, GTVoltageTier.ZPM, 2);
        assertFalse(adapter.isAddonCompatible(mk1, zpmHatch));
    }

    @Test
    @DisplayName("Switching Fusion Controller model to Mk2 (ZPM) updates targetTier, badges, 2:2 overclock, and energy hatch compatibility")
    void testSwitchingFusionControllerToMk2UpdatesTierAndOverclock() {
        RecipeNode node = RecipeNode.create("Fusion D-T", 144, 4096, GTVoltageTier.IV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:fusion_reactor"));
        node.setEuToStart(40_000_000L);
        node.setMachineIcon(ResourceLocation.tryParse("start_core:luv_fusion_reactor"));

        assertEquals(GTVoltageTier.LuV, node.getTargetTier());
        assertEquals(1, node.getTierDelta()); // LuV - IV = 1
        var oc1 = node.getOverclockResult();
        assertEquals(72.0, oc1.durationTicks(), 0.001); // 3.60s (1 OC)
        assertEquals(8192.0, oc1.eut(), 0.001);

        // Switch controller to Mk2 (start_core:zpm_fusion_reactor)
        node.setMachineIcon(ResourceLocation.tryParse("start_core:zpm_fusion_reactor"));

        assertEquals(GTVoltageTier.ZPM, node.getTargetTier());
        assertEquals(2, node.getTierDelta()); // ZPM - IV = 2

        var oc2 = node.getOverclockResult();
        assertEquals(36.0, oc2.durationTicks(), 0.001); // 1.80s (2 OCs under 2:2 rule)
        assertEquals(16384.0, oc2.eut(), 0.001); // 4096 * 2 * 2 = 16384 EU/t
        assertEquals(2, oc2.overclocks());

        // Check badge reflects Mk2
        var badges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(node);
        assertTrue(badges.stream().anyMatch(b -> b.text().contains("Mk2")));

        // Check ZPM energy hatch is now compatible, while LuV is incompatible
        var adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        var luvHatch = new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon("gtceu:energy_hatch_luv", "LuV Energy Hatch", "", null, GTVoltageTier.LuV, 2);
        var zpmHatch = new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon("gtceu:energy_hatch_zpm", "ZPM Energy Hatch", "", null, GTVoltageTier.ZPM, 2);

        assertFalse(adapter.isAddonCompatible(node, luvHatch));
        assertTrue(adapter.isAddonCompatible(node, zpmHatch));
    }

    @Test
    @DisplayName("FlowGraph total ignition buffer aggregation across multiple fusion reactors")
    void testFusionGraphIgnitionBufferAggregation() {
        FlowGraph graph = new FlowGraph();

        // 2 x Mk1 Reactors (each 160M EU)
        RecipeNode mk1 = RecipeNode.create("Fusion Mk1", 32, 16384, GTVoltageTier.LuV);
        mk1.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:fusion_reactor"));
        mk1.setEuToStart(160_000_000L);
        mk1.setMachineCount(2.0);
        mk1.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:deuterium"), "Deuterium", 125));
        mk1.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:helium_plasma"), "Helium Plasma", 125));

        // 1 x Mk2 Reactor with machineCount=2.0 (each 320M EU -> 640M EU total)
        RecipeNode mk2 = RecipeNode.create("Fusion Mk2", 40, 32768, GTVoltageTier.ZPM);
        mk2.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:fusion_reactor"));
        mk2.setEuToStart(320_000_000L);
        mk2.setMachineCount(2.0);
        mk2.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:lutetium"), "Lutetium", 16));
        mk2.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:americium"), "Americium", 16));

        graph.addNode(mk1);
        graph.addNode(mk2);

        BalanceSummary summary = graph.computeSummary();

        // Total Startup EU: 2 * 160M + 2 * 320M = 320M + 640M = 960M EU
        assertEquals(960_000_000L, summary.totalFusionStartupEU());

        // Tier counts
        assertEquals(2, summary.fusionTierCounts().get(1)); // 2 Mk1 units
        assertEquals(2, summary.fusionTierCounts().get(2)); // 2 Mk2 units

        // Tier startup energy breakdown
        assertEquals(320_000_000L, summary.fusionTierStartupEU().get(1));
        assertEquals(640_000_000L, summary.fusionTierStartupEU().get(2));
    }

    @Test
    @DisplayName("Pipeline extraction, NBT serialization, and declarative UI badge verification")
    void testExtractorAndBadges() {
        RecipeNode node = RecipeNode.create("Fusion Reactor Test", 32, 16384, GTVoltageTier.LuV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:fusion_reactor"));

        CompoundTag tag = new CompoundTag();
        tag.putLong("eu_to_start", 160_000_000L);

        RecipePropertyExtractorPipeline.extractAll(null, tag, ResourceLocation.tryParse("gtceu:fusion_reactor"), node.getProperties());
        assertEquals(160_000_000L, node.getEuToStart());

        // Verify badges
        List<NodeBadge> badges = NodeBadgeRegistry.getBadgesForNode(node);
        assertNotNull(badges);
        assertTrue(badges.stream().anyMatch(b -> b.text().contains("Mk1")));
        assertTrue(badges.stream().anyMatch(b -> b.text().contains("160M")));

        // NBT roundtrip test
        CompoundTag saved = node.serializeNBT();
        RecipeNode loaded = RecipeNode.deserializeNBT(saved);

        assertEquals(160_000_000L, loaded.getEuToStart());
        assertEquals(1, loaded.getFusionTier());
        assertEquals(GTVoltageTier.LuV, loaded.getMinFusionVoltageTier());
        assertTrue(loaded.isFusion());
    }

    @Test
    @DisplayName("Star Technology Reflector Fusion Reactor extraction and badge determination")
    void testStarTechnologyReflectorFusionReactor() {
        RecipeNode node = RecipeNode.create(
                ResourceLocation.tryParse("start_core:reflector_fusion_reactor_i"),
                "Reflector Fusion Reactor I",
                72.0,
                32768.0,
                GTVoltageTier.LuV
        );
        node.setRecipeCategoryId(ResourceLocation.tryParse("start_core:reflector_fusion_reactor_i"));
        node.setMachineIcon(ResourceLocation.tryParse("start_core:reflector_fusion_reactor_i"));

        // Must be recognized as Fusion Reactor
        assertTrue(node.isFusion());

        // Extract properties from Star Technology recipe tag
        CompoundTag tag = new CompoundTag();
        tag.putLong("euToStart", 160_000_000L);
        tag.putInt("min_reflector_tier", 1);

        RecipePropertyExtractorPipeline.extractAll(null, tag, ResourceLocation.tryParse("start_core:reflector_fusion_reactor_i"), node.getProperties());

        assertEquals(160_000_000L, node.getEuToStart());
        assertEquals(1, node.getRequiredReflectorTier());

        // Verify Badges when reflector not yet installed (warning badge)
        List<NodeBadge> badgesUninstalled = NodeBadgeRegistry.getBadgesForNode(node);
        assertNotNull(badgesUninstalled);
        assertTrue(badgesUninstalled.stream().anyMatch(b -> b.text().contains("Mk1")));
        assertTrue(badgesUninstalled.stream().anyMatch(b -> b.text().contains("160M")));
        assertTrue(badgesUninstalled.stream().anyMatch(NodeBadge::isWarning));

        // Verify Badges when reflector T1 installed
        node.getAddons().add(new com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon("gtceu:iridium_reflector", "Iridium Reflector", "", null, 1));
        List<NodeBadge> badgesInstalled = NodeBadgeRegistry.getBadgesForNode(node);
        assertNotNull(badgesInstalled);
        assertTrue(badgesInstalled.stream().anyMatch(b -> b.text().contains("T1")));
    }

    @Test
    @DisplayName("Verify preferred fusion workstation matches recipe minimum voltage tier upon creation")
    void testFusionPreferredWorkstationMatching() {
        List<ResourceLocation> wsList = List.of(
                ResourceLocation.tryParse("start_core:luv_fusion_reactor"),
                ResourceLocation.tryParse("start_core:zpm_fusion_reactor"),
                ResourceLocation.tryParse("start_core:uv_fusion_reactor"),
                ResourceLocation.tryParse("start_core:auxiliary_booster_fusion_reactor")
        );

        RecipeNode mk3Node = RecipeNode.create("Fusion Mk3 Recipe", 64, 65536, GTVoltageTier.UV);
        mk3Node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:fusion_reactor"));
        mk3Node.setEuToStart(640_000_000L);
        mk3Node.getAvailableWorkstations().addAll(wsList);

        var adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(mk3Node);
        ResourceLocation preferredMk3 = adapter.getPreferredMultiblockWorkstation(mk3Node, wsList);
        assertEquals(ResourceLocation.tryParse("start_core:uv_fusion_reactor"), preferredMk3);

        RecipeNode mk2Node = RecipeNode.create("Fusion Mk2 Recipe", 144, 24576, GTVoltageTier.LuV);
        mk2Node.setRecipeCategoryId(ResourceLocation.tryParse("start_core:reflector_fusion_reactor"));
        mk2Node.setEuToStart(180_000_000L);
        mk2Node.getAvailableWorkstations().addAll(wsList);

        ResourceLocation preferredMk2 = adapter.getPreferredMultiblockWorkstation(mk2Node, wsList);
        assertEquals(ResourceLocation.tryParse("start_core:zpm_fusion_reactor"), preferredMk2);
    }

    @Test
    @DisplayName("Verify multiblock fusion workstations are sorted in ascending power/voltage order (LuV -> ZPM -> UV -> UHV -> UEV -> UIV)")
    void testFusionWorkstationsSortedByVoltageTier() {
        RecipeNode node = RecipeNode.create("Fusion Recipe", 32, 16384, GTVoltageTier.LuV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("start_core:reflector_fusion_reactor"));
        node.setMultiblock(true);

        // Add in scrambled order like in EMI catalysts
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("start_core:luv_fusion_reactor"));
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("start_core:uev_fusion_reactor"));
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("start_core:uhv_auxiliary_fusion_reactor"));
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("start_core:uiv_auxiliary_fusion_reactor"));
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("start_core:uv_fusion_reactor"));
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("start_core:zpm_fusion_reactor"));

        var adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        List<ResourceLocation> sorted = adapter.getMultiblockWorkstations(node);

        assertEquals(6, sorted.size());
        assertEquals(ResourceLocation.tryParse("start_core:luv_fusion_reactor"), sorted.get(0)); // LuV (Mk1)
        assertEquals(ResourceLocation.tryParse("start_core:zpm_fusion_reactor"), sorted.get(1)); // ZPM (Mk2)
        assertEquals(ResourceLocation.tryParse("start_core:uv_fusion_reactor"), sorted.get(2));  // UV (Mk3)
        assertEquals(ResourceLocation.tryParse("start_core:uhv_auxiliary_fusion_reactor"), sorted.get(3)); // UHV (Aux 1)
        assertEquals(ResourceLocation.tryParse("start_core:uev_fusion_reactor"), sorted.get(4)); // UEV (Mk4)
        assertEquals(ResourceLocation.tryParse("start_core:uiv_auxiliary_fusion_reactor"), sorted.get(5)); // UIV (Aux 2)
    }

    @Test
    @DisplayName("Reflector Fusion Reactor applies 2:2 perfect overclock boost when installed reflector tier exceeds recipe requirement")
    void testReflectorFusionReactorOverclockBoost() {
        RecipeNode node = RecipeNode.create("Duranium Recipe", 288.0, 32768.0, GTVoltageTier.ZPM);
        node.setRecipeCategoryId(ResourceLocation.tryParse("start_core:reflector_fusion_reactor"));
        node.setMachineIcon(ResourceLocation.tryParse("start_core:zpm_fusion_reactor"));
        node.setRequiredReflectorTier(2);
        node.setEuToStart(320_000_000L);
        node.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:duranium"), "Duranium", 144));

        var t2Reflector = new com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon("gtceu:reflector_tier_2", "T2 Reflector", "", null, 2);
        var t3Reflector = new com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon("gtceu:reflector_tier_3", "T3 Reflector", "", null, 3);
        var t4Reflector = new com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon("gtceu:reflector_tier_4", "T4 Reflector", "", null, 4);

        var adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);

        adapter.onAddonInstalled(node, t2Reflector);
        assertEquals(2, node.getInstalledReflectorTier());
        assertEquals(288.0, node.getOverclockResult().durationTicks(), 0.001);
        assertEquals(14.40, node.getEffectiveDurationSeconds(), 0.001);
        assertEquals(32768.0, node.getOverclockResult().eut(), 0.001);
        assertEquals(0, node.getOverclockResult().overclocks());
        assertEquals(10.0, node.getOutputSlotRate(0, true), 0.001);

        adapter.onAddonInstalled(node, t3Reflector);
        assertEquals(3, node.getInstalledReflectorTier());
        assertEquals(144.0, node.getOverclockResult().durationTicks(), 0.001);
        assertEquals(7.20, node.getEffectiveDurationSeconds(), 0.001);
        assertEquals(65536.0, node.getOverclockResult().eut(), 0.001);
        assertEquals(1, node.getOverclockResult().overclocks());
        assertEquals(20.0, node.getOutputSlotRate(0, true), 0.001);

        adapter.onAddonInstalled(node, t4Reflector);
        assertEquals(4, node.getInstalledReflectorTier());
        assertEquals(72.0, node.getOverclockResult().durationTicks(), 0.001);
        assertEquals(3.60, node.getEffectiveDurationSeconds(), 0.001);
        assertEquals(131072.0, node.getOverclockResult().eut(), 0.001);
        assertEquals(2, node.getOverclockResult().overclocks());
        assertEquals(40.0, node.getOutputSlotRate(0, true), 0.001);
    }
}


