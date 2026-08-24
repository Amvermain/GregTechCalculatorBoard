package com.gtceu.calcboard;

import com.gtceu.calcboard.api.*;
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
        RecipeNode mk2 = RecipeNode.create("Fusion Mk2", 40, 32768, GTVoltageTier.HV);
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
}
