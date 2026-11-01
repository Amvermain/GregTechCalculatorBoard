package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FractionalAutoRatioTest {

    @Test
    public void testFractionalAutoRatioExactDecimals() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation plateId = ResourceLocation.tryParse("gtceu:aerogel_sheet");

        // Fast supplier (1 plate per second: 20 ticks duration, 1.0 amount)
        RecipeNode extractor = RecipeNode.create(ResourceLocation.tryParse("gtceu:extractor"), "Extractor", 20, 30, GTVoltageTier.LV);
        extractor.getOutputs().add(IngredientStack.item(plateId, "Aerogel Sheet", 1.0));
        graph.addNode(extractor);

        // Slow consumer (1 plate per 12 seconds: 240 ticks duration, 1.0 amount) -> ~0.0833 demand/s
        RecipeNode reactor = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Reactor", 240, 30, GTVoltageTier.LV);
        reactor.setBaseNode(true);
        reactor.setMachineCount(1.0);
        reactor.getInputs().add(IngredientStack.item(plateId, "Aerogel Sheet", 1.0));
        graph.addNode(reactor);

        graph.addConnection(extractor.getId(), 0, reactor.getId(), 0);

        // 1. Fractional Auto-Ratio
        graph.autoRatioFractional(reactor);

        // Extractor should be ~0.08333 machines, not rounded up to 1.0!
        double expectedCount = 1.0 / 12.0;
        Assertions.assertEquals(expectedCount, extractor.getMachineCount(), 0.005,
                "Extractor machine count must be ~0.0833 without integer ceil! Actual: " + extractor.getMachineCount());

        // 2. Compare with regular integer Auto-Ratio
        graph.autoRatioFromAnchor(reactor);
        Assertions.assertEquals(1.0, extractor.getMachineCount(), 0.001,
                "Standard autoRatioFromAnchor must round up to integer 1.0! Actual: " + extractor.getMachineCount());
    }

    @Test
    public void testPreserveFractionalAnchorWhenEnabled() {
        BoardManager bm = BoardManager.getInstance();
        boolean origPreserve = bm.isPreserveFractionalAnchor();
        try {
            bm.setPreserveFractionalAnchor(true);

            FlowGraph graph = new FlowGraph();
            ResourceLocation itemId = ResourceLocation.tryParse("gtceu:silicon_ingot");

            RecipeNode supplier = RecipeNode.create(ResourceLocation.tryParse("gtceu:ebf"), "EBF", 20, 120, GTVoltageTier.MV);
            supplier.addAddon(new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon("gtceu:mv_energy_hatch", "MV Energy Hatch", "", ResourceLocation.tryParse("gtceu:mv_energy_hatch"), GTVoltageTier.MV, 1, false, false, false));
            supplier.getOutputs().add(IngredientStack.item(itemId, "Silicon Ingot", 1.0));
            graph.addNode(supplier);

            RecipeNode anchor = RecipeNode.create(ResourceLocation.tryParse("gtceu:cutter"), "Cutter", 20, 30, GTVoltageTier.LV);
            anchor.setBaseNode(true);
            anchor.setMachineCount(0.125);
            anchor.getInputs().add(IngredientStack.item(itemId, "Silicon Ingot", 1.0));
            graph.addNode(anchor);

            graph.addConnection(supplier.getId(), 0, anchor.getId(), 0);

            // Execute standard autoRatioFromAnchor
            graph.autoRatioFromAnchor(anchor);

            // Anchor's fractional count (0.125) must be preserved!
            Assertions.assertEquals(0.125, anchor.getMachineCount(), 0.0001,
                    "Anchor fractional count must be preserved when preserveFractionalAnchor is true!");

            // Supplier is ceil(0.125) = 1.0 under standard integer quantization
            Assertions.assertEquals(1.0, supplier.getMachineCount(), 0.0001);
        } finally {
            bm.setPreserveFractionalAnchor(origPreserve);
        }
    }

    @Test
    public void testPreserveFractionalAnchorWhenDisabled() {
        BoardManager bm = BoardManager.getInstance();
        boolean origPreserve = bm.isPreserveFractionalAnchor();
        try {
            bm.setPreserveFractionalAnchor(false);

            FlowGraph graph = new FlowGraph();
            ResourceLocation itemId = ResourceLocation.tryParse("gtceu:silicon_ingot");

            RecipeNode supplier = RecipeNode.create(ResourceLocation.tryParse("gtceu:ebf"), "EBF", 20, 120, GTVoltageTier.MV);
            supplier.addAddon(new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon("gtceu:mv_energy_hatch", "MV Energy Hatch", "", ResourceLocation.tryParse("gtceu:mv_energy_hatch"), GTVoltageTier.MV, 1, false, false, false));
            supplier.getOutputs().add(IngredientStack.item(itemId, "Silicon Ingot", 1.0));
            graph.addNode(supplier);

            RecipeNode anchor = RecipeNode.create(ResourceLocation.tryParse("gtceu:cutter"), "Cutter", 20, 30, GTVoltageTier.LV);
            anchor.setBaseNode(true);
            anchor.setMachineCount(0.125);
            anchor.getInputs().add(IngredientStack.item(itemId, "Silicon Ingot", 1.0));
            graph.addNode(anchor);

            graph.addConnection(supplier.getId(), 0, anchor.getId(), 0);

            // Execute standard autoRatioFromAnchor
            graph.autoRatioFromAnchor(anchor);

            // When disabled, anchor count must be rounded up to ceil(0.125) = 1.0!
            Assertions.assertEquals(1.0, anchor.getMachineCount(), 0.0001,
                    "Anchor fractional count should be rounded up to 1.0 when preserveFractionalAnchor is false!");
        } finally {
            bm.setPreserveFractionalAnchor(origPreserve);
        }
    }

    @Test
    public void testDownstreamContinuousFlowPreservationInIntegerAutoRatio() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation slurry = ResourceLocation.tryParse("gtceu:apatite_slurry");
        ResourceLocation distilled = ResourceLocation.tryParse("gtceu:apatite_distilled");
        ResourceLocation diamond = ResourceLocation.tryParse("minecraft:diamond");

        RecipeNode brewery = RecipeNode.create(ResourceLocation.tryParse("gtceu:brewery"), "Brewery", 200, 30, GTVoltageTier.HV);
        brewery.setBaseNode(true);
        brewery.setMachineCount(1.0);
        brewery.getOutputs().add(IngredientStack.fluid(slurry, "Slurry", 1000));
        graph.addNode(brewery);

        RecipeNode dt = RecipeNode.create(ResourceLocation.tryParse("gtceu:distillation_tower"), "DT", 12, 120, GTVoltageTier.ZPM);
        dt.getInputs().add(IngredientStack.fluid(slurry, "Slurry", 1000));
        dt.getOutputs().add(IngredientStack.fluid(distilled, "Distilled", 750));
        graph.addNode(dt);

        RecipeNode solidifier = RecipeNode.create(ResourceLocation.tryParse("gtceu:fluid_solidifier"), "Solidifier", 200, 30, GTVoltageTier.HV);
        solidifier.getInputs().add(IngredientStack.fluid(distilled, "Distilled", 250));
        solidifier.getOutputs().add(IngredientStack.item(diamond, "Diamond", 1.0));
        graph.addNode(solidifier);

        graph.addConnection(brewery.getId(), 0, dt.getId(), 0);
        graph.addConnection(dt.getId(), 0, solidifier.getId(), 0);

        RecipeNode independentMacerator = RecipeNode.create(ResourceLocation.tryParse("gtceu:macerator"), "Macerator", 20, 30, GTVoltageTier.LV);
        graph.addNode(independentMacerator);

        graph.autoRatioFractional(brewery);
        Assertions.assertEquals(1.0, brewery.getMachineCount(), 0.001);
        Assertions.assertEquals(0.06, dt.getMachineCount(), 0.005);
        Assertions.assertEquals(3.0, solidifier.getMachineCount(), 0.001);

        graph.autoRatioFromAnchor(brewery);
        Assertions.assertEquals(1.0, brewery.getMachineCount(), 0.001);
        Assertions.assertEquals(1.0, dt.getMachineCount(), 0.001);
        Assertions.assertEquals(3.0, solidifier.getMachineCount(), 0.001);
    }

    @Test
    public void testDownstreamMachineCountGreaterThanOneCeilsInIntegerAutoRatio() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation dustA = ResourceLocation.tryParse("gtceu:dust_a");
        ResourceLocation dustB = ResourceLocation.tryParse("gtceu:dust_b");
        ResourceLocation dustC = ResourceLocation.tryParse("gtceu:dust_c");
        ResourceLocation dustD = ResourceLocation.tryParse("gtceu:dust_d");
        ResourceLocation dustE = ResourceLocation.tryParse("gtceu:dust_e");
        ResourceLocation dustF = ResourceLocation.tryParse("gtceu:dust_f");
        ResourceLocation ingotFinal = ResourceLocation.tryParse("gtceu:ingot_final");

        RecipeNode electromagnet = RecipeNode.create(ResourceLocation.tryParse("gtceu:electromagnet"), "Large Electromagnet", 20, 30, GTVoltageTier.LuV);
        electromagnet.setBaseNode(true);
        electromagnet.setMachineCount(1.0);
        electromagnet.getOutputs().add(IngredientStack.item(dustA, "Dust A", 2.4));
        electromagnet.getOutputs().add(IngredientStack.item(dustB, "Dust B", 1.2));
        graph.addNode(electromagnet);

        RecipeNode centrifugal = RecipeNode.create(ResourceLocation.tryParse("gtceu:centrifugal"), "Large Centrifugal Unit", 20, 30, GTVoltageTier.ZPM);
        centrifugal.getInputs().add(IngredientStack.item(dustA, "Dust A", 9.6));
        centrifugal.getOutputs().add(IngredientStack.item(dustC, "Dust C", 4.2667));
        graph.addNode(centrifugal);

        RecipeNode electrolysis = RecipeNode.create(ResourceLocation.tryParse("gtceu:electrolysis"), "Large Electrolysis Chamber", 20, 30, GTVoltageTier.ZPM);
        electrolysis.getInputs().add(IngredientStack.item(dustC, "Dust C", 4.2667));
        electrolysis.getOutputs().add(IngredientStack.item(dustD, "Dust D", 2.1333));
        graph.addNode(electrolysis);

        RecipeNode ecrBottom = RecipeNode.create(ResourceLocation.tryParse("gtceu:ecr_bottom"), "Extreme Chemical Reactor 1", 20, 30, GTVoltageTier.ZPM);
        ecrBottom.getInputs().add(IngredientStack.item(dustB, "Dust B", 5.0));
        ecrBottom.getOutputs().add(IngredientStack.item(dustD, "Dust D", 2.0));
        graph.addNode(ecrBottom);

        RecipeNode ecrMid = RecipeNode.create(ResourceLocation.tryParse("gtceu:ecr_mid"), "Extreme Chemical Reactor 2", 20, 30, GTVoltageTier.ZPM);
        ecrMid.getInputs().add(IngredientStack.item(dustD, "Dust D", 2.9804));
        ecrMid.getOutputs().add(IngredientStack.item(dustE, "Dust E", 4.4706));
        graph.addNode(ecrMid);

        RecipeNode ecrRight = RecipeNode.create(ResourceLocation.tryParse("gtceu:ecr_right"), "Extreme Chemical Reactor 3", 20, 30, GTVoltageTier.ZPM);
        ecrRight.getInputs().add(IngredientStack.item(dustE, "Dust E", 4.6061));
        ecrRight.getOutputs().add(IngredientStack.item(dustF, "Dust F", 7.6757));
        graph.addNode(ecrRight);

        RecipeNode rotaryHearth = RecipeNode.create(ResourceLocation.tryParse("gtceu:rotary_hearth"), "Rotary Hearth Furnace", 20, 30, GTVoltageTier.UV);
        rotaryHearth.getInputs().add(IngredientStack.item(dustF, "Dust F", 1.5445));
        rotaryHearth.getOutputs().add(IngredientStack.item(ingotFinal, "Final Ingot", 0.3089));
        graph.addNode(rotaryHearth);

        graph.addConnection(electromagnet.getId(), 0, centrifugal.getId(), 0);
        graph.addConnection(centrifugal.getId(), 0, electrolysis.getId(), 0);
        graph.addConnection(electromagnet.getId(), 1, ecrBottom.getId(), 0);
        graph.addConnection(electrolysis.getId(), 0, ecrMid.getId(), 0);
        graph.addConnection(ecrBottom.getId(), 0, ecrMid.getId(), 0);
        graph.addConnection(ecrMid.getId(), 0, ecrRight.getId(), 0);
        graph.addConnection(ecrRight.getId(), 0, rotaryHearth.getId(), 0);

        graph.autoRatioFractional(electromagnet);
        Assertions.assertEquals(0.25, centrifugal.getMachineCount(), 0.01);
        Assertions.assertEquals(0.25, electrolysis.getMachineCount(), 0.01);
        Assertions.assertEquals(0.24, ecrBottom.getMachineCount(), 0.01);
        Assertions.assertEquals(0.34, ecrMid.getMachineCount(), 0.01);
        Assertions.assertEquals(0.33, ecrRight.getMachineCount(), 0.01);
        Assertions.assertEquals(1.64, rotaryHearth.getMachineCount(), 0.02);

        graph.autoRatioFromAnchor(electromagnet);
        Assertions.assertEquals(1.0, electromagnet.getMachineCount(), 0.001);
        Assertions.assertEquals(1.0, centrifugal.getMachineCount(), 0.001);
        Assertions.assertEquals(1.0, electrolysis.getMachineCount(), 0.001);
        Assertions.assertEquals(1.0, ecrBottom.getMachineCount(), 0.001);
        Assertions.assertEquals(1.0, ecrMid.getMachineCount(), 0.001);
        Assertions.assertEquals(1.0, ecrRight.getMachineCount(), 0.001);
        Assertions.assertEquals(2.0, rotaryHearth.getMachineCount(), 0.001,
                "Downstream machine requiring 1.64 machines must be rounded up (CEIL) to 2.0, not floored to 1.0!");
    }

    @Test
    public void testStarTechDeionizedWaterDualLoopFractionalAutoRatio() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation impureWater = ResourceLocation.tryParse("gtceu:impure_water");
        ResourceLocation filteredWater = ResourceLocation.tryParse("gtceu:filtered_water");
        ResourceLocation deionizedWaterFeed = ResourceLocation.tryParse("gtceu:deionized_water_feed");
        ResourceLocation pureDeionizedWater = ResourceLocation.tryParse("gtceu:pure_deionized_water");
        ResourceLocation dirtyResin = ResourceLocation.tryParse("gtceu:dirty_ion_exchange_resin");
        ResourceLocation regenResin = ResourceLocation.tryParse("gtceu:regenerated_resin");
        ResourceLocation activeResin = ResourceLocation.tryParse("gtceu:active_ion_exchange_resin");
        ResourceLocation polyMatrix = ResourceLocation.tryParse("gtceu:polystyrene_sulfonate_matrix");
        ResourceLocation acid1 = ResourceLocation.tryParse("gtceu:sulfuric_acid");
        ResourceLocation acid2 = ResourceLocation.tryParse("gtceu:nitric_acid");
        ResourceLocation styrene = ResourceLocation.tryParse("gtceu:styrene");
        ResourceLocation divinyl = ResourceLocation.tryParse("gtceu:divinylbenzene");

        RecipeNode topLcb = RecipeNode.create(ResourceLocation.tryParse("gtceu:top_lcb"), "Large Chemical Bath (Regen)", 20, 30, GTVoltageTier.UV);
        topLcb.getInputs().add(IngredientStack.item(dirtyResin, "Dirty Ion Exchange Resin", 32.0));
        topLcb.getInputs().add(IngredientStack.fluid(acid1, "Sulfuric Acid", 1000));
        topLcb.getOutputs().add(IngredientStack.item(regenResin, "Regenerated Resin", 32.0));
        graph.addNode(topLcb);

        RecipeNode topEcr = RecipeNode.create(ResourceLocation.tryParse("gtceu:top_ecr"), "Extreme Chemical Reactor (Matrix)", 20, 30, GTVoltageTier.LuV);
        topEcr.getInputs().add(IngredientStack.fluid(styrene, "Styrene", 692.3));
        topEcr.getInputs().add(IngredientStack.fluid(divinyl, "Divinylbenzene", 1384.6));
        topEcr.getOutputs().add(IngredientStack.fluid(polyMatrix, "Polystyrene Sulfonate Matrix", 692.3));
        graph.addNode(topEcr);

        RecipeNode botEcr = RecipeNode.create(ResourceLocation.tryParse("gtceu:bot_ecr"), "Extreme Chemical Reactor (Resin)", 20, 30, GTVoltageTier.ZPM);
        botEcr.getInputs().add(IngredientStack.fluid(acid2, "Nitric Acid", 2307.7));
        botEcr.getInputs().add(IngredientStack.fluid(polyMatrix, "Polystyrene Sulfonate Matrix", 230.77));
        botEcr.getOutputs().add(IngredientStack.item(regenResin, "Regenerated Resin", 9.2308));
        graph.addNode(botEcr);

        RecipeNode midLcb = RecipeNode.create(ResourceLocation.tryParse("gtceu:mid_lcb"), "Large Chemical Bath (Activate)", 20, 30, GTVoltageTier.UV);
        midLcb.getInputs().add(IngredientStack.item(regenResin, "Regenerated Resin", 68.0));
        midLcb.getOutputs().add(IngredientStack.item(activeResin, "Active Ion Exchange Resin", 68.0));
        graph.addNode(midLcb);

        RecipeNode sifter = RecipeNode.create(ResourceLocation.tryParse("gtceu:cyclonic_sifter"), "Cyclonic Sifter", 20, 30, GTVoltageTier.UV);
        sifter.getInputs().add(IngredientStack.fluid(impureWater, "Impure Water", 63750));
        sifter.getOutputs().add(IngredientStack.fluid(filteredWater, "Filtered Water", 42500));
        graph.addNode(sifter);

        RecipeNode dcv = RecipeNode.create(ResourceLocation.tryParse("gtceu:dcv"), "Dual-Chambered Vacuum", 20, 30, GTVoltageTier.UEV);
        dcv.getInputs().add(IngredientStack.item(activeResin, "Active Ion Exchange Resin", 31.25));
        dcv.getInputs().add(IngredientStack.fluid(filteredWater, "Filtered Water", 78125));
        dcv.getOutputs().add(IngredientStack.item(dirtyResin, "Dirty Ion Exchange Resin", 15.625, 1.0));
        dcv.getOutputs().add(IngredientStack.item(dirtyResin, "Dirty Ion Exchange Resin", 15.625, 0.8));
        dcv.getOutputs().add(IngredientStack.fluid(deionizedWaterFeed, "Deionized Water Feed", 78125));
        graph.addNode(dcv);

        RecipeNode dt = RecipeNode.create(ResourceLocation.tryParse("gtceu:dt"), "Distillation Tower", 20, 30, GTVoltageTier.UV);
        dt.getInputs().add(IngredientStack.fluid(deionizedWaterFeed, "Deionized Water Feed", 6000));
        dt.getOutputs().add(IngredientStack.fluid(pureDeionizedWater, "Deionized Water", 4500));
        dt.getOutputs().add(IngredientStack.fluid(filteredWater, "Filtered Water", 1000));
        dt.setMachineCount(20.0);
        graph.addNode(dt);

        graph.addConnection(dcv.getId(), 2, dt.getId(), 0);
        graph.addConnection(dt.getId(), 1, dcv.getId(), 1);
        graph.addConnection(sifter.getId(), 0, dcv.getId(), 1);

        graph.addConnection(dcv.getId(), 0, topLcb.getId(), 0);
        graph.addConnection(dcv.getId(), 1, topLcb.getId(), 0);
        graph.addConnection(topLcb.getId(), 0, midLcb.getId(), 0);
        graph.addConnection(topEcr.getId(), 0, botEcr.getId(), 1);
        graph.addConnection(botEcr.getId(), 0, midLcb.getId(), 0);
        graph.addConnection(midLcb.getId(), 0, dcv.getId(), 0);

        graph.autoRatioFractional(dt);

        Assertions.assertEquals(20.0, dt.getMachineCount(), 0.01);
        Assertions.assertEquals(1.536, dcv.getMachineCount(), 0.02);
        Assertions.assertEquals(2.353, sifter.getMachineCount(), 0.02);
        Assertions.assertEquals(0.706, midLcb.getMachineCount(), 0.02);
        Assertions.assertEquals(1.35, topLcb.getMachineCount(), 0.02,
                "Top LCB regenerating dirty resin (100% + 80% output slots = 43.2/s) should require 1.35 machines, not 1.5!");
        Assertions.assertEquals(0.52, botEcr.getMachineCount(), 0.02,
                "Bottom ECR supplying 10% makeup resin (4.8/s) should require ~0.52 machines, not 0.01!");
        Assertions.assertEquals(0.173, topEcr.getMachineCount(), 0.02,
                "Top ECR supplying polymer matrix should require ~0.17 machines, not 0.01!");

        Assertions.assertEquals(1.0, dt.getEfficiency(), 0.01,
                "Anchored Distillation Tower must run at 100% efficiency (90 B/s output), not throttled to 60%!");
        Assertions.assertEquals(90000.0, dt.getOutputSlotRate(0, true), 100.0);
    }
}
