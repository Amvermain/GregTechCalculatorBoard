package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.api.property.NodeBadgeRegistry;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ComprehensiveDivergenceMatrixTest {

    @Test
    @DisplayName("RFC-033: Positive feedback growth loop (ρ > 1.0) without external sink is detected and flagged")
    public void testPositiveFeedbackLoopDetectionAndBadge() {
        FlowGraph graph = new FlowGraph();

        RecipeNode breeder = RecipeNode.create("Breeder", 20.0, 30.0, GTVoltageTier.LV);
        breeder.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:biomass"), "Biomass", 100.0, 1.0));
        breeder.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:enriched_biomass"), "Enriched Biomass", 150.0, 1.0));
        breeder.setMachineCount(1.0);
        breeder.setBaseNode(true);
        graph.addNode(breeder);

        RecipeNode fermenter = RecipeNode.create("Fermenter", 20.0, 30.0, GTVoltageTier.LV);
        fermenter.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:enriched_biomass"), "Enriched Biomass", 100.0, 1.0));
        fermenter.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:biomass"), "Biomass", 100.0, 1.0));
        fermenter.setMachineCount(1.0);
        graph.addNode(fermenter);

        graph.addConnection(breeder.getId(), 0, fermenter.getId(), 0);
        graph.addConnection(fermenter.getId(), 0, breeder.getId(), 0);

        AutoRatioResult result = graph.autoRatioFromAnchor(breeder);

        Assertions.assertTrue(result.hasDivergence());
        Assertions.assertTrue(breeder.getProperties().get(NodeProperties.DIVERGENCE_WARNING));
        Assertions.assertEquals("positive_feedback", breeder.getProperties().get(NodeProperties.DIVERGENCE_REASON));

        List<NodeBadge> badges = NodeBadgeRegistry.getBadgesForNode(breeder);
        Assertions.assertFalse(badges.isEmpty());
        NodeBadge badge = badges.get(0);
        Assertions.assertTrue(badge.text().contains("Growth") || badge.text().contains("positive_feedback"));
        Assertions.assertEquals(0xFF06B6D4, badge.textColor());
        Assertions.assertTrue(badge.isWarning());
        Assertions.assertEquals(5, badge.tooltipLines().size());
    }

    @Test
    @DisplayName("RFC-033: Catalyst/solvent decay loop (0.95 <= ρ < 1.0) is identified and self-heals with makeup line")
    public void testCatalystDecayLoopDetectionAndBadge() {
        FlowGraph graph = new FlowGraph();

        RecipeNode reactor = RecipeNode.create("Chemical Reactor", 20.0, 30.0, GTVoltageTier.LV);
        reactor.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:solvent"), "Solvent", 100.0, 1.0));
        reactor.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:used_solvent"), "Used Solvent", 98.0, 1.0));
        reactor.setMachineCount(1.0);
        reactor.setBaseNode(true);
        graph.addNode(reactor);

        RecipeNode recovery = RecipeNode.create("Solvent Recovery", 20.0, 30.0, GTVoltageTier.LV);
        recovery.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:used_solvent"), "Used Solvent", 98.0, 1.0));
        recovery.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:solvent"), "Solvent", 98.0, 1.0));
        recovery.setMachineCount(1.0);
        graph.addNode(recovery);

        graph.addConnection(reactor.getId(), 0, recovery.getId(), 0);
        graph.addConnection(recovery.getId(), 0, reactor.getId(), 0);

        AutoRatioResult firstResult = graph.autoRatioFromAnchor(reactor);
        Assertions.assertTrue(firstResult.hasDivergence());
        Assertions.assertEquals("catalyst_decay", reactor.getProperties().get(NodeProperties.DIVERGENCE_REASON));

        List<NodeBadge> badges = NodeBadgeRegistry.getBadgesForNode(reactor);
        Assertions.assertFalse(badges.isEmpty());
        NodeBadge badge = badges.get(0);
        Assertions.assertTrue(badge.text().contains("Catalyst") || badge.text().contains("catalyst_decay"));
        Assertions.assertTrue(badge.isWarning());
        Assertions.assertEquals(5, badge.tooltipLines().size());

        RecipeNode makeup = RecipeNode.create("Makeup Tank", 20.0, 30.0, GTVoltageTier.LV);
        makeup.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:solvent"), "Solvent", 2.0, 1.0));
        makeup.setMachineCount(1.0);
        graph.addNode(makeup);
        graph.addConnection(makeup.getId(), 0, reactor.getId(), 0);

        AutoRatioResult healedResult = graph.autoRatioFromAnchor(reactor);
        Assertions.assertFalse(healedResult.hasDivergence());
        Assertions.assertFalse(reactor.getProperties().get(NodeProperties.DIVERGENCE_WARNING));
    }

    @Test
    @DisplayName("RFC-033: Conflicting multiple anchors flag anchor_conflict badge and allow one-click unpin")
    public void testConflictingAnchorsDetectionAndBadge() {
        FlowGraph graph = new FlowGraph();

        RecipeNode producer = RecipeNode.create("Producer", 20.0, 30.0, GTVoltageTier.LV);
        producer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 10.0, 1.0));
        producer.setMachineCount(1.0);
        producer.setBaseNode(true);
        graph.addNode(producer);

        RecipeNode consumer = RecipeNode.create("Consumer", 20.0, 30.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 50.0, 1.0));
        consumer.setMachineCount(1.0);
        consumer.setBaseNode(true);
        graph.addNode(consumer);

        graph.addConnection(producer.getId(), 0, consumer.getId(), 0);

        AutoRatioResult result = graph.autoRatioFromAnchor(producer);

        Assertions.assertTrue(result.hasDivergence());
        Assertions.assertTrue(consumer.getProperties().get(NodeProperties.DIVERGENCE_WARNING));
        Assertions.assertEquals("anchor_conflict", consumer.getProperties().get(NodeProperties.DIVERGENCE_REASON));

        List<NodeBadge> badges = NodeBadgeRegistry.getBadgesForNode(consumer);
        Assertions.assertFalse(badges.isEmpty());
        NodeBadge badge = badges.get(0);
        Assertions.assertTrue(badge.text().contains("Conflict") || badge.text().contains("anchor_conflict"));
        Assertions.assertEquals(0xFFEF4444, badge.textColor());
        Assertions.assertTrue(badge.isWarning());
        Assertions.assertEquals(5, badge.tooltipLines().size());

        badge.onClick().run();
        Assertions.assertFalse(consumer.isBaseNode());
        Assertions.assertFalse(consumer.getProperties().get(NodeProperties.DIVERGENCE_WARNING));
    }

    @Test
    @DisplayName("RFC-033: Extreme micro-yield recipes are differentiated from cascade runaway and assigned micro_yield_clamp")
    public void testMicroYieldClampingDifferentiation() {
        FlowGraph graph = new FlowGraph();

        RecipeNode traceExtractor = RecipeNode.create("Trace Extractor", 20.0, 30.0, GTVoltageTier.LV);
        traceExtractor.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:krypton"), "Krypton", 0.00001, 1.0));
        traceExtractor.setMachineCount(1.0);
        graph.addNode(traceExtractor);

        RecipeNode highDemandConsumer = RecipeNode.create("High Demand Consumer", 20.0, 30.0, GTVoltageTier.LV);
        highDemandConsumer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:krypton"), "Krypton", 100.0, 1.0));
        highDemandConsumer.setMachineCount(1.0);
        highDemandConsumer.setBaseNode(true);
        graph.addNode(highDemandConsumer);

        graph.addConnection(traceExtractor.getId(), 0, highDemandConsumer.getId(), 0);

        AutoRatioResult result = graph.autoRatioFromAnchor(highDemandConsumer);

        Assertions.assertTrue(result.hasDivergence());
        Assertions.assertTrue(traceExtractor.getProperties().get(NodeProperties.DIVERGENCE_WARNING));
        Assertions.assertEquals("micro_yield_clamp", traceExtractor.getProperties().get(NodeProperties.DIVERGENCE_REASON));

        List<NodeBadge> badges = NodeBadgeRegistry.getBadgesForNode(traceExtractor);
        Assertions.assertFalse(badges.isEmpty());
        NodeBadge badge = badges.get(0);
        Assertions.assertTrue(badge.text().contains("Yield") || badge.text().contains("micro_yield"));
        Assertions.assertTrue(badge.isWarning());
        Assertions.assertEquals(5, badge.tooltipLines().size());
    }

    @Test
    @DisplayName("Branching feedback loop with out-degree division is not falsely detected as positive feedback growth")
    public void testBranchingFeedbackLoopNotFalselyDiagnosedAsPositiveFeedback() {
        FlowGraph graph = new FlowGraph();

        RecipeNode provider = RecipeNode.create("Plate Provider", 20.0, 30.0, GTVoltageTier.LV);
        provider.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:plate"), "Plate", 1.0));
        provider.setMachineCount(1.0);
        graph.addNode(provider);

        RecipeNode consumer1 = RecipeNode.create("Autoclave 1", 20.0, 30.0, GTVoltageTier.LV);
        consumer1.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:plate"), "Plate", 1.0));
        consumer1.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:shard"), "Shard", 1.0));
        graph.addNode(consumer1);

        RecipeNode consumer2 = RecipeNode.create("Autoclave 2", 20.0, 30.0, GTVoltageTier.LV);
        consumer2.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:plate"), "Plate", 1.0));
        consumer2.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:shard"), "Shard", 1.0));
        graph.addNode(consumer2);

        RecipeNode consumer3 = RecipeNode.create("Autoclave 3", 20.0, 30.0, GTVoltageTier.LV);
        consumer3.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:plate"), "Plate", 1.0));
        consumer3.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:shard"), "Shard", 1.0));
        graph.addNode(consumer3);

        RecipeNode consumer4 = RecipeNode.create("Autoclave 4", 20.0, 30.0, GTVoltageTier.LV);
        consumer4.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:plate"), "Plate", 1.0));
        consumer4.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:shard"), "Shard", 1.0));
        graph.addNode(consumer4);

        RecipeNode combiner = RecipeNode.create("Forming Press", 20.0, 30.0, GTVoltageTier.LV);
        combiner.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:shard"), "Shard", 1.0));
        combiner.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:shard"), "Shard", 1.0));
        combiner.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:shard"), "Shard", 1.0));
        combiner.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:shard"), "Shard", 1.0));
        combiner.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:star"), "Star", 1.0));
        combiner.setMachineCount(1.0);
        combiner.setBaseNode(true);
        graph.addNode(combiner);

        graph.addConnection(provider.getId(), 0, consumer1.getId(), 0);
        graph.addConnection(provider.getId(), 0, consumer2.getId(), 0);
        graph.addConnection(provider.getId(), 0, consumer3.getId(), 0);
        graph.addConnection(provider.getId(), 0, consumer4.getId(), 0);

        graph.addConnection(consumer1.getId(), 0, combiner.getId(), 0);
        graph.addConnection(consumer2.getId(), 0, combiner.getId(), 1);
        graph.addConnection(consumer3.getId(), 0, combiner.getId(), 2);
        graph.addConnection(consumer4.getId(), 0, combiner.getId(), 3);

        graph.addConnection(combiner.getId(), 0, provider.getId(), 0);

        AutoRatioResult result = graph.autoRatioFromAnchor(combiner);
        Assertions.assertNotEquals("positive_feedback", combiner.getProperties().get(NodeProperties.DIVERGENCE_REASON));
        Assertions.assertFalse(result.hasDivergence(), "Anchored balanced branching loop should not have divergence");
        Assertions.assertFalse(combiner.getProperties().get(NodeProperties.DIVERGENCE_WARNING), "Anchor node should not have divergence warning");
    }

    @Test
    @DisplayName("Anchor node badge displays Anchor Fixed and prevents duplicate anchor creation")
    public void testAnchorNodeBadgeDisplaysAnchorFixedAndPreventsDuplicateAnchors() {
        RecipeNode anchor = RecipeNode.create("Anchor", 20.0, 30.0, GTVoltageTier.LV);
        anchor.setBaseNode(true);
        anchor.getProperties().set(NodeProperties.DIVERGENCE_WARNING, true);
        anchor.getProperties().set(NodeProperties.DIVERGENCE_REASON, "positive_feedback");

        List<NodeBadge> badges = NodeBadgeRegistry.getBadgesForNode(anchor);
        Assertions.assertFalse(badges.isEmpty());
        NodeBadge badge = badges.get(0);
        Assertions.assertEquals(5, badge.tooltipLines().size());

        String line5 = badge.tooltipLines().get(4).getString();
        Assertions.assertTrue(line5.toLowerCase().contains("anchor") || line5.contains("기준 기계"));

        badge.onClick().run();
        Assertions.assertTrue(anchor.isBaseNode());
    }

    @Test
    @DisplayName("Reproduction: User screenshot anchored nether star recirculation loop")
    public void testUserRecirculationLoopScreenshotGraph() {
        FlowGraph graph = new FlowGraph();

        // 4 Extractors
        RecipeNode extBlaze = RecipeNode.create("Extractor (Blaze)", 1.10, 60.0, GTVoltageTier.LV);
        extBlaze.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:blaze"), "Blaze Fluid", 144.0, 1.0));
        graph.addNode(extBlaze);

        RecipeNode extBlizz = RecipeNode.create("Extractor (Blizz)", 1.10, 60.0, GTVoltageTier.LV);
        extBlizz.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:blizz"), "Blizz Fluid", 144.0, 1.0));
        graph.addNode(extBlizz);

        RecipeNode extBlitz = RecipeNode.create("Extractor (Blitz)", 1.10, 60.0, GTVoltageTier.LV);
        extBlitz.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:blitz"), "Blitz Fluid", 144.0, 1.0));
        graph.addNode(extBlitz);

        RecipeNode extBasalz = RecipeNode.create("Extractor (Basalz)", 1.10, 60.0, GTVoltageTier.LV);
        extBasalz.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:basalz"), "Basalz Fluid", 144.0, 1.0));
        graph.addNode(extBasalz);

        // 4 Autoclaves (48s) with dual chanced outputs (50% + 45%) as in GTCEu
        RecipeNode auto1 = RecipeNode.create("Autoclave 1", 48.0, 30.0, GTVoltageTier.IV);
        auto1.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0));
        auto1.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:blaze"), "Blaze Fluid", 200.0, 1.0));
        auto1.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blaze_star"), "Blaze Star", 1.0, 0.50));
        auto1.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blaze_star"), "Blaze Star", 1.0, 0.45));
        graph.addNode(auto1);

        RecipeNode auto2 = RecipeNode.create("Autoclave 2", 48.0, 30.0, GTVoltageTier.IV);
        auto2.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0));
        auto2.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:blizz"), "Blizz Fluid", 200.0, 1.0));
        auto2.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blizz_star"), "Blizz Star", 1.0, 0.50));
        auto2.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blizz_star"), "Blizz Star", 1.0, 0.45));
        graph.addNode(auto2);

        RecipeNode auto3 = RecipeNode.create("Autoclave 3", 48.0, 30.0, GTVoltageTier.IV);
        auto3.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0));
        auto3.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:blitz"), "Blitz Fluid", 200.0, 1.0));
        auto3.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blitz_star"), "Blitz Star", 1.0, 0.50));
        auto3.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blitz_star"), "Blitz Star", 1.0, 0.45));
        graph.addNode(auto3);

        RecipeNode auto4 = RecipeNode.create("Autoclave 4", 48.0, 30.0, GTVoltageTier.IV);
        auto4.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0));
        auto4.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:basalz"), "Basalz Fluid", 200.0, 1.0));
        auto4.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:basalz_star"), "Basalz Star", 1.0, 0.50));
        auto4.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:basalz_star"), "Basalz Star", 1.0, 0.45));
        graph.addNode(auto4);

        // Connections from Extractors to Autoclaves
        graph.addConnection(extBlaze.getId(), 0, auto1.getId(), 1);
        graph.addConnection(extBlizz.getId(), 0, auto2.getId(), 1);
        graph.addConnection(extBlitz.getId(), 0, auto3.getId(), 1);
        graph.addConnection(extBasalz.getId(), 0, auto4.getId(), 1);

        // Forming Press (Anchor = 3)
        RecipeNode formingPress = RecipeNode.create("Forming Press", 15.0, 30.0, GTVoltageTier.IV);
        formingPress.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blaze_star"), "Blaze Star", 1.0));
        formingPress.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blizz_star"), "Blizz Star", 1.0));
        formingPress.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blitz_star"), "Blitz Star", 1.0));
        formingPress.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:basalz_star"), "Basalz Star", 1.0));
        formingPress.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:star_block"), "Star Block", 1.0));
        formingPress.setMachineCount(3.0);
        formingPress.setBaseNode(true);
        graph.addNode(formingPress);

        graph.addConnection(auto1.getId(), 0, formingPress.getId(), 0);
        graph.addConnection(auto1.getId(), 1, formingPress.getId(), 0);
        graph.addConnection(auto2.getId(), 0, formingPress.getId(), 1);
        graph.addConnection(auto2.getId(), 1, formingPress.getId(), 1);
        graph.addConnection(auto3.getId(), 0, formingPress.getId(), 2);
        graph.addConnection(auto3.getId(), 1, formingPress.getId(), 2);
        graph.addConnection(auto4.getId(), 0, formingPress.getId(), 3);
        graph.addConnection(auto4.getId(), 1, formingPress.getId(), 3);

        // Implosion Compressor (10s)
        RecipeNode implosion = RecipeNode.create("Implosion Compressor", 10.0, 30.0, GTVoltageTier.EV);
        implosion.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:star_block"), "Star Block", 1.0));
        implosion.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:compressed_star"), "Compressed Star", 1.0));
        graph.addNode(implosion);
        graph.addConnection(formingPress.getId(), 0, implosion.getId(), 0);

        // Junction between Implosion and Forge Hammer (as in user screenshot)
        RecipeNode junction = RecipeNode.createReroute(0, 0);
        graph.addNode(junction);
        graph.addConnection(implosion.getId(), 0, junction.getId(), 0);

        // Forge Hammer (15s) outputs 5 shards per star
        RecipeNode forgeHammer = RecipeNode.create("Forge Hammer", 15.0, 30.0, GTVoltageTier.MV);
        forgeHammer.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:compressed_star"), "Compressed Star", 1.0));
        forgeHammer.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 5.0));
        graph.addNode(forgeHammer);
        graph.addConnection(junction.getId(), 0, forgeHammer.getId(), 0);

        // Polarizer (20s EV)
        RecipeNode polarizer = RecipeNode.create("Polarizer", 20.0, 30.0, GTVoltageTier.EV);
        polarizer.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0));
        polarizer.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0));
        graph.addNode(polarizer);
        graph.addConnection(forgeHammer.getId(), 0, polarizer.getId(), 0);

        // Polarizer outputs to 4 Autoclaves
        graph.addConnection(polarizer.getId(), 0, auto1.getId(), 0);
        graph.addConnection(polarizer.getId(), 0, auto2.getId(), 0);
        graph.addConnection(polarizer.getId(), 0, auto3.getId(), 0);
        graph.addConnection(polarizer.getId(), 0, auto4.getId(), 0);

        AutoRatioResult result = graph.autoRatioFromAnchor(formingPress, true);

        System.out.println("formingPress count: " + formingPress.getMachineCount());
        System.out.println("implosion count: " + implosion.getMachineCount());
        System.out.println("forgeHammer count: " + forgeHammer.getMachineCount());
        System.out.println("polarizer count: " + polarizer.getMachineCount());
        System.out.println("auto1 count: " + auto1.getMachineCount());
        System.out.println("REASON: " + formingPress.getProperties().get(NodeProperties.DIVERGENCE_REASON));
        System.out.println("ALL DIVERGENT: " + result.divergentNodeIds());
        Assertions.assertFalse(formingPress.getProperties().get(NodeProperties.DIVERGENCE_WARNING), "Forming press (Anchor) must not have divergence warning");
        Assertions.assertFalse(auto1.getProperties().get(NodeProperties.DIVERGENCE_WARNING), "Autoclave 1 must not have divergence warning");
        Assertions.assertFalse(implosion.getProperties().get(NodeProperties.DIVERGENCE_WARNING), "Implosion Compressor must not have divergence warning");
        Assertions.assertFalse(polarizer.getProperties().get(NodeProperties.DIVERGENCE_WARNING), "Polarizer must not have divergence warning");
        Assertions.assertFalse(result.hasDivergence(), "Anchored loop must not have divergence");

        Assertions.assertEquals(3.0, formingPress.getMachineCount(), 1e-4);
        Assertions.assertEquals(2.0, implosion.getMachineCount(), 1e-4);
        Assertions.assertEquals(3.0, forgeHammer.getMachineCount(), 1e-4);
        Assertions.assertEquals(16.0, polarizer.getMachineCount(), 1e-4);
        Assertions.assertEquals(10.0, auto1.getMachineCount(), 1e-4);
        Assertions.assertEquals(10.0, auto2.getMachineCount(), 1e-4);
        Assertions.assertEquals(10.0, auto3.getMachineCount(), 1e-4);
        Assertions.assertEquals(10.0, auto4.getMachineCount(), 1e-4);
    }

    @Test
    @DisplayName("RFC-033: User screenshot Nether star loop converges from count 1 to full scale (75 forming presses) without warnings")
    public void testUserScreenshotFullScaleAnchor75Convergence() {
        FlowGraph graph = new FlowGraph();

        RecipeNode extBlaze = RecipeNode.create("Extractor (Blaze)", 1.10, 60.0, GTVoltageTier.LV);
        extBlaze.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:blaze"), "Blaze Fluid", 144.0, 1.0));
        graph.addNode(extBlaze);

        RecipeNode extBlizz = RecipeNode.create("Extractor (Blizz)", 1.10, 60.0, GTVoltageTier.LV);
        extBlizz.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:blizz"), "Blizz Fluid", 144.0, 1.0));
        graph.addNode(extBlizz);

        RecipeNode extBlitz = RecipeNode.create("Extractor (Blitz)", 1.10, 60.0, GTVoltageTier.LV);
        extBlitz.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:blitz"), "Blitz Fluid", 144.0, 1.0));
        graph.addNode(extBlitz);

        RecipeNode extBasalz = RecipeNode.create("Extractor (Basalz)", 1.10, 60.0, GTVoltageTier.LV);
        extBasalz.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:basalz"), "Basalz Fluid", 144.0, 1.0));
        graph.addNode(extBasalz);

        RecipeNode auto1 = RecipeNode.create("Autoclave 1", 48.0, 30.0, GTVoltageTier.IV);
        auto1.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0));
        auto1.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:blaze"), "Blaze Fluid", 200.0, 1.0));
        auto1.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blaze_star"), "Blaze Star", 1.0, 0.50));
        auto1.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blaze_star"), "Blaze Star", 1.0, 0.45));
        graph.addNode(auto1);

        RecipeNode auto2 = RecipeNode.create("Autoclave 2", 48.0, 30.0, GTVoltageTier.IV);
        auto2.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0));
        auto2.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:blizz"), "Blizz Fluid", 200.0, 1.0));
        auto2.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blizz_star"), "Blizz Star", 1.0, 0.50));
        auto2.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blizz_star"), "Blizz Star", 1.0, 0.45));
        graph.addNode(auto2);

        RecipeNode auto3 = RecipeNode.create("Autoclave 3", 48.0, 30.0, GTVoltageTier.IV);
        auto3.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0));
        auto3.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:blitz"), "Blitz Fluid", 200.0, 1.0));
        auto3.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blitz_star"), "Blitz Star", 1.0, 0.50));
        auto3.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blitz_star"), "Blitz Star", 1.0, 0.45));
        graph.addNode(auto3);

        RecipeNode auto4 = RecipeNode.create("Autoclave 4", 48.0, 30.0, GTVoltageTier.IV);
        auto4.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0));
        auto4.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:basalz"), "Basalz Fluid", 200.0, 1.0));
        auto4.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:basalz_star"), "Basalz Star", 1.0, 0.50));
        auto4.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:basalz_star"), "Basalz Star", 1.0, 0.45));
        graph.addNode(auto4);

        graph.addConnection(extBlaze.getId(), 0, auto1.getId(), 1);
        graph.addConnection(extBlizz.getId(), 0, auto2.getId(), 1);
        graph.addConnection(extBlitz.getId(), 0, auto3.getId(), 1);
        graph.addConnection(extBasalz.getId(), 0, auto4.getId(), 1);

        RecipeNode formingPress = RecipeNode.create("Forming Press", 15.0, 30.0, GTVoltageTier.IV);
        formingPress.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blaze_star"), "Blaze Star", 1.0));
        formingPress.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blizz_star"), "Blizz Star", 1.0));
        formingPress.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:blitz_star"), "Blitz Star", 1.0));
        formingPress.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:basalz_star"), "Basalz Star", 1.0));
        formingPress.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:star_block"), "Star Block", 1.0));
        formingPress.setMachineCount(75.0);
        formingPress.setBaseNode(true);
        graph.addNode(formingPress);

        graph.addConnection(auto1.getId(), 0, formingPress.getId(), 0);
        graph.addConnection(auto1.getId(), 1, formingPress.getId(), 0);
        graph.addConnection(auto2.getId(), 0, formingPress.getId(), 1);
        graph.addConnection(auto2.getId(), 1, formingPress.getId(), 1);
        graph.addConnection(auto3.getId(), 0, formingPress.getId(), 2);
        graph.addConnection(auto3.getId(), 1, formingPress.getId(), 2);
        graph.addConnection(auto4.getId(), 0, formingPress.getId(), 3);
        graph.addConnection(auto4.getId(), 1, formingPress.getId(), 3);

        RecipeNode implosion = RecipeNode.create("Implosion Compressor", 10.0, 30.0, GTVoltageTier.EV);
        implosion.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:star_block"), "Star Block", 1.0));
        implosion.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:compressed_star"), "Compressed Star", 1.0));
        graph.addNode(implosion);
        graph.addConnection(formingPress.getId(), 0, implosion.getId(), 0);

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        graph.addNode(junction);
        graph.addConnection(implosion.getId(), 0, junction.getId(), 0);

        RecipeNode forgeHammer = RecipeNode.create("Forge Hammer", 15.0, 30.0, GTVoltageTier.MV);
        forgeHammer.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:compressed_star"), "Compressed Star", 1.0));
        forgeHammer.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 5.0));
        graph.addNode(forgeHammer);
        graph.addConnection(junction.getId(), 0, forgeHammer.getId(), 0);

        RecipeNode polarizer = RecipeNode.create("Polarizer", 20.0, 30.0, GTVoltageTier.EV);
        polarizer.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0));
        polarizer.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0));
        graph.addNode(polarizer);
        graph.addConnection(forgeHammer.getId(), 0, polarizer.getId(), 0);

        graph.addConnection(polarizer.getId(), 0, auto1.getId(), 0);
        graph.addConnection(polarizer.getId(), 0, auto2.getId(), 0);
        graph.addConnection(polarizer.getId(), 0, auto3.getId(), 0);
        graph.addConnection(polarizer.getId(), 0, auto4.getId(), 0);

        AutoRatioResult result = graph.autoRatioFromAnchor(formingPress, true);

        Assertions.assertFalse(result.hasDivergence(), "Full scale loop must converge cleanly without divergence");
        Assertions.assertFalse(formingPress.getProperties().get(NodeProperties.DIVERGENCE_WARNING));
        Assertions.assertFalse(auto1.getProperties().get(NodeProperties.DIVERGENCE_WARNING));
        Assertions.assertFalse(implosion.getProperties().get(NodeProperties.DIVERGENCE_WARNING));
        Assertions.assertFalse(polarizer.getProperties().get(NodeProperties.DIVERGENCE_WARNING));
        Assertions.assertEquals(75.0, formingPress.getMachineCount(), 1e-4);
        Assertions.assertEquals(50.0, implosion.getMachineCount(), 1e-4);
    }

    @Test
    @DisplayName("RFC-033: Expanding delta across relaxation passes flags divergence and halts cleanly")
    public void testExpandingDeltaDivergenceHalt() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation matA = ResourceLocation.tryParse("gtceu:mat_a");
        ResourceLocation matB = ResourceLocation.tryParse("gtceu:mat_b");

        RecipeNode nodeA = RecipeNode.create("Node A", 10.0, 30.0, GTVoltageTier.LV);
        nodeA.addInput(IngredientStack.item(matA, "Mat A", 1.0));
        nodeA.addOutput(IngredientStack.item(matB, "Mat B", 5.0));
        graph.addNode(nodeA);

        RecipeNode nodeB = RecipeNode.create("Node B", 10.0, 30.0, GTVoltageTier.LV);
        nodeB.addInput(IngredientStack.item(matB, "Mat B", 1.0));
        nodeB.addOutput(IngredientStack.item(matA, "Mat A", 5.0));
        graph.addNode(nodeB);

        graph.addConnection(nodeA.getId(), 0, nodeB.getId(), 0);
        graph.addConnection(nodeB.getId(), 0, nodeA.getId(), 0);

        nodeA.setMachineCount(2.0);
        nodeA.setBaseNode(true);

        AutoRatioResult result = graph.autoRatioFromAnchor(nodeA, true);

        Assertions.assertTrue(result.hasDivergence(), "Expanding loop must flag divergence");
        Assertions.assertTrue(nodeA.getProperties().get(NodeProperties.DIVERGENCE_WARNING), "Node A must have warning");
        Assertions.assertTrue(nodeB.getProperties().get(NodeProperties.DIVERGENCE_WARNING), "Node B must have warning");
    }

    @Test
    @DisplayName("Reproduction: Star Technology Bromine loop with byproduct outlet must detect growth warning")
    public void testStarTechnologyBromineGrowthLoopWithByproductOutlet() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation hotBrine = ResourceLocation.tryParse("gtceu:hot_brine");
        ResourceLocation intermediate1 = ResourceLocation.tryParse("gtceu:intermediate_1");
        ResourceLocation intermediate2 = ResourceLocation.tryParse("gtceu:intermediate_2");
        ResourceLocation intermediate3 = ResourceLocation.tryParse("gtceu:intermediate_3");
        ResourceLocation bromine = ResourceLocation.tryParse("gtceu:bromine");
        ResourceLocation greenLiquid = ResourceLocation.tryParse("gtceu:green_liquid");

        // 100 mB/s input -> 400/3 mB/s output (gain = 4/3)
        // Node 1: 1000 Hot Brine -> 2000 Interm1 (duration = 10s -> in = 100/s, out = 200/s)
        RecipeNode node1 = RecipeNode.create("Node 1", 200.0, 30.0, GTVoltageTier.LV);
        node1.addInput(IngredientStack.fluid(hotBrine, "Hot Brine", 1000.0, 1.0));
        node1.addOutput(IngredientStack.fluid(intermediate1, "Interm 1", 2000.0, 1.0));
        node1.setMachineCount(1.0);
        node1.setBaseNode(true);
        graph.addNode(node1);

        // Node 2: 1000 Interm1 -> 1000 Interm2 + 2000 Bromine (duration = 5s -> in = 200/s, out = 200/s + 400/s)
        RecipeNode node2 = RecipeNode.create("Node 2", 100.0, 30.0, GTVoltageTier.LV);
        node2.addInput(IngredientStack.fluid(intermediate1, "Interm 1", 1000.0, 1.0));
        node2.addOutput(IngredientStack.fluid(intermediate2, "Interm 2", 1000.0, 1.0));
        node2.addOutput(IngredientStack.fluid(bromine, "Bromine", 2000.0, 1.0));
        node2.setMachineCount(1.0);
        graph.addNode(node2);

        // Node 3: 3000 Interm2 -> 2000 Interm3 (duration = 15s -> in = 200/s, out = 133.33/s)
        RecipeNode node3 = RecipeNode.create("Node 3", 300.0, 30.0, GTVoltageTier.LV);
        node3.addInput(IngredientStack.fluid(intermediate2, "Interm 2", 3000.0, 1.0));
        node3.addOutput(IngredientStack.fluid(intermediate3, "Interm 3", 2000.0, 1.0));
        node3.setMachineCount(1.0);
        graph.addNode(node3);

        // Node 4: 1000 Interm3 -> 1000 Hot Brine + 1000 Green Liquid (duration = 7.5s -> in = 133.33/s, out = 133.33/s)
        RecipeNode node4 = RecipeNode.create("Node 4", 150.0, 30.0, GTVoltageTier.LV);
        node4.addInput(IngredientStack.fluid(intermediate3, "Interm 3", 1000.0, 1.0));
        node4.addOutput(IngredientStack.fluid(hotBrine, "Hot Brine", 1000.0, 1.0));
        node4.addOutput(IngredientStack.fluid(greenLiquid, "Green Liquid", 1000.0, 1.0));
        node4.setMachineCount(1.0);
        graph.addNode(node4);

        // External supply for Hot Brine
        RecipeNode supply = RecipeNode.create("Supply", 20.0, 30.0, GTVoltageTier.LV);
        supply.addOutput(IngredientStack.fluid(hotBrine, "Hot Brine", 100.0, 1.0));
        supply.setMachineCount(1.0);
        graph.addNode(supply);

        // External consumer for Green Liquid (Byproduct outlet)
        RecipeNode externalConsumer = RecipeNode.create("External Consumer", 20.0, 30.0, GTVoltageTier.LV);
        externalConsumer.addInput(IngredientStack.fluid(greenLiquid, "Green Liquid", 1000.0, 1.0));
        externalConsumer.setMachineCount(1.0);
        graph.addNode(externalConsumer);

        // Connect loop
        graph.addConnection(supply.getId(), 0, node1.getId(), 0);
        graph.addConnection(node1.getId(), 0, node2.getId(), 0);
        graph.addConnection(node2.getId(), 0, node3.getId(), 0);
        graph.addConnection(node3.getId(), 0, node4.getId(), 0);
        graph.addConnection(node4.getId(), 0, node1.getId(), 0);

        // Connect external byproduct outlet (Green Liquid)
        graph.addConnection(node4.getId(), 1, externalConsumer.getId(), 0);

        AutoRatioResult result = graph.autoRatioFromAnchor(node1, true);

        Assertions.assertTrue(result.hasDivergence(), "Bromine loop without Hot Brine sink must detect growth");
        Assertions.assertTrue(node1.getProperties().get(NodeProperties.DIVERGENCE_WARNING));
        Assertions.assertEquals("positive_feedback", node1.getProperties().get(NodeProperties.DIVERGENCE_REASON));

        // When external sink is added for Hot Brine, growth warning heals cleanly
        RecipeNode voidSink = RecipeNode.createReroute(100, 100);
        voidSink.setSupplyMode(com.gtceu.calcboard.api.type.SupplyMode.VOID_SINK);
        voidSink.getInputs().add(IngredientStack.fluid(hotBrine, "Hot Brine", 1000.0, 1.0));
        voidSink.getOutputs().add(IngredientStack.fluid(hotBrine, "Hot Brine", 1000.0, 1.0));
        graph.addNode(voidSink);
        graph.addConnection(node4.getId(), 0, voidSink.getId(), 0);

        AutoRatioResult healedResult = graph.autoRatioFromAnchor(node1, true);
        Assertions.assertFalse(healedResult.hasDivergence(), "Loop with Hot Brine sink must heal");
        Assertions.assertFalse(node1.getProperties().get(NodeProperties.DIVERGENCE_WARNING));
    }
}
