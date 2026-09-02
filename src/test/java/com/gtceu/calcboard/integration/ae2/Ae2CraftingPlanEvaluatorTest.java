package com.gtceu.calcboard.integration.ae2;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.integration.ae2.evaluator.Ae2CraftingPlanEvaluator;
import com.gtceu.calcboard.integration.ae2.model.Ae2PlanEvaluationResult;
import com.gtceu.calcboard.integration.ae2.model.PatternId;
import com.gtceu.calcboard.integration.ae2.registry.PatternGraphRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class Ae2CraftingPlanEvaluatorTest {

    @BeforeEach
    public void setup() {
        BoardManager.getInstance().resetToDefault();
        PatternGraphRegistry.getInstance().clear();
    }

    @Test
    public void testEmptyPlanReturnsEmptyResult() {
        Ae2PlanEvaluationResult result = Ae2CraftingPlanEvaluator.evaluatePatternCounts(Map.of(), 4);
        Assertions.assertEquals(0L, result.totalDurationTicks());
        Assertions.assertEquals("0s", result.formattedEta());
    }

    @Test
    public void testSingleStepBatchAndDurationEvaluation() {
        BoardPage titaniumPage = BoardManager.getInstance().addPage("Titanium Page", "ae2");
        RecipeNode ebf = RecipeNode.create("Electric Blast Furnace", 200.0, 120.0, GTVoltageTier.HV);
        ebf.setParallel(4);
        ebf.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:titanium_ingot"), "Titanium Ingot", 1.0, 1.0));
        titaniumPage.getGraph().addNode(ebf);

        PatternId patternId = PatternId.ofKey("gtceu:titanium_ingot", "Titanium Ingot", ItemStack.EMPTY);
        PatternGraphRegistry.getInstance().bind(patternId, titaniumPage);

        Map<PatternId, Long> counts = Map.of(patternId, 100L);
        Ae2PlanEvaluationResult result = Ae2CraftingPlanEvaluator.evaluatePatternCounts(counts, 0);

        Assertions.assertEquals(5000L, result.totalDurationTicks());
        Assertions.assertEquals(250.0, result.totalDurationSeconds(), 0.01);
        Assertions.assertEquals("Titanium Ingot", result.bottleneckName());
        Assertions.assertEquals(titaniumPage.getId(), result.bottleneckPageId());
        Assertions.assertEquals("~4m 10s", result.formattedEta());
    }

    @Test
    public void testConcurrentParallelBranchesCalculation() {
        BoardPage wirePage = BoardManager.getInstance().addPage("Wire Page", "ae2");
        RecipeNode wireNode = RecipeNode.create("Wiremill", 10.0, 32.0, GTVoltageTier.LV);
        wireNode.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:copper_wire"), "Copper Wire", 1.0, 1.0));
        wirePage.getGraph().addNode(wireNode);
        PatternId wirePattern = PatternId.ofKey("gtceu:copper_wire", "Copper Wire", ItemStack.EMPTY);
        PatternGraphRegistry.getInstance().bind(wirePattern, wirePage);

        BoardPage ingotPage = BoardManager.getInstance().addPage("Ingot Page", "ae2");
        RecipeNode ingotNode = RecipeNode.create("Blast Furnace", 200.0, 120.0, GTVoltageTier.MV);
        ingotNode.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:aluminium_ingot"), "Aluminium Ingot", 1.0, 1.0));
        ingotPage.getGraph().addNode(ingotNode);
        PatternId ingotPattern = PatternId.ofKey("gtceu:aluminium_ingot", "Aluminium Ingot", ItemStack.EMPTY);
        PatternGraphRegistry.getInstance().bind(ingotPattern, ingotPage);

        Map<PatternId, Long> counts = new LinkedHashMap<>();
        counts.put(wirePattern, 100L);
        counts.put(ingotPattern, 20L);

        Ae2PlanEvaluationResult res = Ae2CraftingPlanEvaluator.evaluatePatternCounts(counts, 0);
        Assertions.assertEquals(4000L, res.totalDurationTicks());
        Assertions.assertEquals("Aluminium Ingot", res.bottleneckName());
        Assertions.assertEquals("~3m 20s", res.formattedEta());
    }

    @Test
    public void testPipelinedSerialChainCalculation() {
        BoardPage ebfPage = BoardManager.getInstance().addPage("EBF Hot Ingot Page", "ae2");
        RecipeNode ebf = RecipeNode.create("Electric Blast Furnace", 600.0, 120.0, GTVoltageTier.UV);
        ebf.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:hot_hyper_ingot"), "Hot Ingot", 1.0, 1.0));
        ebfPage.getGraph().addNode(ebf);
        PatternId hotIngotPattern = PatternId.ofKey("gtceu:hot_hyper_ingot", "Hot Ingot", ItemStack.EMPTY);
        PatternGraphRegistry.getInstance().bind(hotIngotPattern, ebfPage);

        BoardPage freezerPage = BoardManager.getInstance().addPage("Vacuum Freezer Page", "ae2");
        RecipeNode freezer = RecipeNode.create("Vacuum Freezer", 94.0, 491520.0, GTVoltageTier.UV);
        IngredientStack inHot = IngredientStack.item(ResourceLocation.tryParse("gtceu:hot_hyper_ingot"), "Hot Ingot", 1.0);
        freezer.getInputs().add(inHot);
        freezer.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:hyper_voidic_ingot"), "Hyper Voidic Ingot", 1.0, 1.0));
        freezerPage.getGraph().addNode(freezer);

        RecipeNode inJunc = RecipeNode.createReroute(60, 60);
        inJunc.bindRerouteIngredient(inHot);
        inJunc.setOutputPort(false);
        freezerPage.getGraph().addNode(inJunc);
        freezerPage.getGraph().addConnection(inJunc.getId(), 0, freezer.getId(), 0);

        RecipeNode outJunc = RecipeNode.createReroute(560, 60);
        outJunc.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("gtceu:hyper_voidic_ingot"), "Hyper Voidic Ingot", 1.0));
        outJunc.setOutputPort(true);
        outJunc.setTargetBatchAmount(1.0);
        freezerPage.getGraph().addNode(outJunc);
        freezerPage.getGraph().addConnection(freezer.getId(), 0, outJunc.getId(), 0);

        PatternId primaryPattern = PatternId.ofKey("gtceu:hyper_voidic_ingot", "Hyper Voidic Ingot", ItemStack.EMPTY);
        PatternGraphRegistry.getInstance().bind(primaryPattern, freezerPage);

        Map<PatternId, Long> planCounts = new LinkedHashMap<>();
        planCounts.put(hotIngotPattern, 30L);
        planCounts.put(primaryPattern, 30L);

        Ae2PlanEvaluationResult res = Ae2CraftingPlanEvaluator.evaluatePatternCounts(planCounts, 0);

        Assertions.assertEquals(2, res.steps().size());
        Assertions.assertEquals(18094L, res.totalDurationTicks());
        Assertions.assertEquals(904.7, res.totalDurationSeconds(), 0.01);
        Assertions.assertEquals("~15m 5s", res.formattedEta());
        Assertions.assertEquals("Hot Ingot", res.bottleneckName());
    }

    @Test
    public void testMultiOutputByproductDeduplicationAndFluidScaling() {
        BoardPage freezerPage = BoardManager.getInstance().addPage("Vacuum Freezer Page", "ae2");
        RecipeNode freezer = RecipeNode.create("Vacuum Freezer", 375.0, 491520.0, GTVoltageTier.UV);
        freezer.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:hyper_voidic_ingot"), "Hyper Voidic Ingot", 1.0, 1.0));
        freezer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:helium_3"), "Helium 3", 250, 1.0));
        freezerPage.getGraph().addNode(freezer);

        RecipeNode outJunction1 = RecipeNode.createReroute(500, 100);
        outJunction1.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("gtceu:hyper_voidic_ingot"), "Hyper Voidic Ingot", 1.0, 1.0));
        outJunction1.setOutputPort(true);
        outJunction1.setTargetBatchAmount(1.0);
        freezerPage.getGraph().addNode(outJunction1);
        freezerPage.getGraph().addConnection(freezer.getId(), 0, outJunction1.getId(), 0);

        RecipeNode outJunction2 = RecipeNode.createReroute(500, 150);
        outJunction2.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:helium_3"), "Helium 3", 250, 1.0));
        outJunction2.setOutputPort(true);
        outJunction2.setTargetBatchAmount(250.0);
        freezerPage.getGraph().addNode(outJunction2);
        freezerPage.getGraph().addConnection(freezer.getId(), 1, outJunction2.getId(), 0);

        PatternId primaryPattern = PatternId.ofKey("gtceu:hyper_voidic_ingot", "Hyper Voidic Ingot", ItemStack.EMPTY);
        PatternGraphRegistry.getInstance().bind(primaryPattern, freezerPage);

        PatternId byproductPattern = PatternId.ofKey("gtceu:helium_3", "Helium 3", ItemStack.EMPTY);

        Map<PatternId, Long> planCounts = new LinkedHashMap<>();
        planCounts.put(primaryPattern, 1L);
        planCounts.put(byproductPattern, 250L);

        Ae2PlanEvaluationResult res = Ae2CraftingPlanEvaluator.evaluatePatternCounts(planCounts, 0);

        Assertions.assertEquals(1, res.steps().size());
        Assertions.assertEquals(375L, res.totalDurationTicks());
        Assertions.assertEquals(18.75, res.totalDurationSeconds(), 0.01);
        Assertions.assertEquals("~18.8s", res.formattedEta());
    }

    @Test
    public void testMultipleDistinctProcessesEmittingSameByproduct() {
        BoardPage processAPage = BoardManager.getInstance().addPage("Process A Page", "ae2");
        RecipeNode nodeA = RecipeNode.create("Freezer A", 375.0, 100.0, GTVoltageTier.UV);
        nodeA.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:ingot_a"), "Ingot A", 1.0, 1.0));
        nodeA.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:helium_3"), "Helium 3", 250, 1.0));
        processAPage.getGraph().addNode(nodeA);

        RecipeNode juncA1 = RecipeNode.createReroute(500, 100);
        juncA1.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("gtceu:ingot_a"), "Ingot A", 1.0, 1.0));
        juncA1.setOutputPort(true);
        juncA1.setTargetBatchAmount(1.0);
        processAPage.getGraph().addNode(juncA1);
        processAPage.getGraph().addConnection(nodeA.getId(), 0, juncA1.getId(), 0);

        RecipeNode juncA2 = RecipeNode.createReroute(500, 150);
        juncA2.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:helium_3"), "Helium 3", 250, 1.0));
        juncA2.setOutputPort(true);
        juncA2.setTargetBatchAmount(250.0);
        processAPage.getGraph().addNode(juncA2);
        processAPage.getGraph().addConnection(nodeA.getId(), 1, juncA2.getId(), 0);

        PatternId patternA = PatternId.ofKey("gtceu:ingot_a", "Ingot A", ItemStack.EMPTY);
        PatternGraphRegistry.getInstance().bind(patternA, processAPage);

        BoardPage processBPage = BoardManager.getInstance().addPage("Process B Page", "ae2");
        RecipeNode nodeB = RecipeNode.create("Centrifuge B", 200.0, 100.0, GTVoltageTier.MV);
        nodeB.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:ingot_b"), "Ingot B", 1.0, 1.0));
        nodeB.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:helium_3"), "Helium 3", 500, 1.0));
        processBPage.getGraph().addNode(nodeB);

        RecipeNode juncB1 = RecipeNode.createReroute(500, 100);
        juncB1.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("gtceu:ingot_b"), "Ingot B", 1.0, 1.0));
        juncB1.setOutputPort(true);
        juncB1.setTargetBatchAmount(1.0);
        processBPage.getGraph().addNode(juncB1);
        processBPage.getGraph().addConnection(nodeB.getId(), 0, juncB1.getId(), 0);

        RecipeNode juncB2 = RecipeNode.createReroute(500, 150);
        juncB2.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:helium_3"), "Helium 3", 500, 1.0));
        juncB2.setOutputPort(true);
        juncB2.setTargetBatchAmount(500.0);
        processBPage.getGraph().addNode(juncB2);
        processBPage.getGraph().addConnection(nodeB.getId(), 1, juncB2.getId(), 0);

        PatternId patternB = PatternId.ofKey("gtceu:ingot_b", "Ingot B", ItemStack.EMPTY);
        PatternGraphRegistry.getInstance().bind(patternB, processBPage);

        PatternId heliumPattern = PatternId.ofKey("gtceu:helium_3", "Helium 3", ItemStack.EMPTY);

        Map<PatternId, Long> planCounts = new LinkedHashMap<>();
        planCounts.put(patternA, 1L);
        planCounts.put(patternB, 1L);
        planCounts.put(heliumPattern, 750L);

        Ae2PlanEvaluationResult res = Ae2CraftingPlanEvaluator.evaluatePatternCounts(planCounts, 0);

        Assertions.assertEquals(2, res.steps().size());
        Assertions.assertEquals(375L, res.totalDurationTicks());
        Assertions.assertEquals(18.75, res.totalDurationSeconds(), 0.01);
        Assertions.assertEquals("~18.8s", res.formattedEta());
    }

    @Test
    public void testExcessDemandPartiallyCoveredByByproduct() {
        BoardPage freezerPage = BoardManager.getInstance().addPage("Vacuum Freezer Page", "ae2");
        RecipeNode freezer = RecipeNode.create("Vacuum Freezer", 375.0, 491520.0, GTVoltageTier.UV);
        freezer.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:hyper_voidic_ingot"), "Hyper Voidic Ingot", 1.0, 1.0));
        freezer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:helium_3"), "Helium 3", 250, 1.0));
        freezerPage.getGraph().addNode(freezer);

        RecipeNode juncF1 = RecipeNode.createReroute(500, 100);
        juncF1.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("gtceu:hyper_voidic_ingot"), "Hyper Voidic Ingot", 1.0, 1.0));
        juncF1.setOutputPort(true);
        juncF1.setTargetBatchAmount(1.0);
        freezerPage.getGraph().addNode(juncF1);
        freezerPage.getGraph().addConnection(freezer.getId(), 0, juncF1.getId(), 0);

        RecipeNode juncF2 = RecipeNode.createReroute(500, 150);
        juncF2.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:helium_3"), "Helium 3", 250, 1.0));
        juncF2.setOutputPort(true);
        juncF2.setTargetBatchAmount(250.0);
        freezerPage.getGraph().addNode(juncF2);
        freezerPage.getGraph().addConnection(freezer.getId(), 1, juncF2.getId(), 0);

        PatternId freezerPattern = PatternId.ofKey("gtceu:hyper_voidic_ingot", "Hyper Voidic Ingot", ItemStack.EMPTY);
        PatternGraphRegistry.getInstance().bind(freezerPattern, freezerPage);

        BoardPage heliumDedicatedPage = BoardManager.getInstance().addPage("Helium Dedicated Page", "ae2");
        RecipeNode distiller = RecipeNode.create("Distillation Tower", 100.0, 100.0, GTVoltageTier.HV);
        distiller.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:helium_3"), "Helium 3", 250, 1.0));
        heliumDedicatedPage.getGraph().addNode(distiller);

        RecipeNode juncD = RecipeNode.createReroute(500, 100);
        juncD.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:helium_3"), "Helium 3", 250, 1.0));
        juncD.setOutputPort(true);
        juncD.setTargetBatchAmount(250.0);
        heliumDedicatedPage.getGraph().addNode(juncD);
        heliumDedicatedPage.getGraph().addConnection(distiller.getId(), 0, juncD.getId(), 0);

        PatternId heliumPattern = PatternId.ofKey("gtceu:helium_3", "Helium 3", ItemStack.EMPTY);
        PatternGraphRegistry.getInstance().bind(heliumPattern, heliumDedicatedPage);

        Map<PatternId, Long> planCounts = new LinkedHashMap<>();
        planCounts.put(freezerPattern, 1L);
        planCounts.put(heliumPattern, 1000L);

        Ae2PlanEvaluationResult res = Ae2CraftingPlanEvaluator.evaluatePatternCounts(planCounts, 0);

        Assertions.assertEquals(2, res.steps().size());
        Assertions.assertEquals(375L, res.totalDurationTicks());
        Assertions.assertEquals(18.75, res.totalDurationSeconds(), 0.01);
        Assertions.assertEquals("~18.8s", res.formattedEta());
    }

    @Test
    public void testMultiTierIntermediateIngredientCrafting() {
        BoardPage yellowPowderPage = BoardManager.getInstance().addPage("Yellow Powder Page", "ae2");
        RecipeNode mixer = RecipeNode.create("Mixer", 20.0, 30.0, GTVoltageTier.LV);
        mixer.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:yellow_powder"), "Yellow Powder", 1.0, 1.0));
        yellowPowderPage.getGraph().addNode(mixer);
        PatternId yellowPattern = PatternId.ofKey("gtceu:yellow_powder", "Yellow Powder", ItemStack.EMPTY);
        PatternGraphRegistry.getInstance().bind(yellowPattern, yellowPowderPage);

        BoardPage hotIngotPage = BoardManager.getInstance().addPage("Hot Ingot Page", "ae2");
        RecipeNode ebf = RecipeNode.create("Electric Blast Furnace", 40.0, 120.0, GTVoltageTier.MV);
        ebf.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:hot_ingot"), "Hot Ingot", 1.0, 1.0));
        hotIngotPage.getGraph().addNode(ebf);
        PatternId hotIngotPattern = PatternId.ofKey("gtceu:hot_ingot", "Hot Ingot", ItemStack.EMPTY);
        PatternGraphRegistry.getInstance().bind(hotIngotPattern, hotIngotPage);

        BoardPage finalIngotPage = BoardManager.getInstance().addPage("Final Ingot Page", "ae2");
        RecipeNode assemblyNode = RecipeNode.create("Precision Assembler", 94.0, 480.0, GTVoltageTier.HV);
        IngredientStack inYellow = IngredientStack.item(ResourceLocation.tryParse("gtceu:yellow_powder"), "Yellow Powder", 25.0);
        IngredientStack inHot = IngredientStack.item(ResourceLocation.tryParse("gtceu:hot_ingot"), "Hot Ingot", 1.0);
        assemblyNode.getInputs().add(inYellow);
        assemblyNode.getInputs().add(inHot);
        assemblyNode.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:hyper_steel"), "Hyper Steel", 1.0, 1.0));
        finalIngotPage.getGraph().addNode(assemblyNode);

        RecipeNode inJuncYellow = RecipeNode.createReroute(60, 60);
        inJuncYellow.bindRerouteIngredient(inYellow);
        inJuncYellow.setOutputPort(false);
        finalIngotPage.getGraph().addNode(inJuncYellow);
        finalIngotPage.getGraph().addConnection(inJuncYellow.getId(), 0, assemblyNode.getId(), 0);

        RecipeNode inJuncHot = RecipeNode.createReroute(60, 120);
        inJuncHot.bindRerouteIngredient(inHot);
        inJuncHot.setOutputPort(false);
        finalIngotPage.getGraph().addNode(inJuncHot);
        finalIngotPage.getGraph().addConnection(inJuncHot.getId(), 0, assemblyNode.getId(), 1);

        RecipeNode outJunc = RecipeNode.createReroute(560, 60);
        outJunc.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("gtceu:hyper_steel"), "Hyper Steel", 1.0));
        outJunc.setOutputPort(true);
        outJunc.setTargetBatchAmount(1.0);
        finalIngotPage.getGraph().addNode(outJunc);
        finalIngotPage.getGraph().addConnection(assemblyNode.getId(), 0, outJunc.getId(), 0);

        PatternId finalPattern = PatternId.ofKey("gtceu:hyper_steel", "Hyper Steel", ItemStack.EMPTY);
        PatternGraphRegistry.getInstance().bind(finalPattern, finalIngotPage);

        Map<PatternId, Long> planCounts = new LinkedHashMap<>();
        planCounts.put(yellowPattern, 25L);
        planCounts.put(hotIngotPattern, 1L);
        planCounts.put(finalPattern, 1L);

        Ae2PlanEvaluationResult res = Ae2CraftingPlanEvaluator.evaluatePatternCounts(planCounts, 0);

        Assertions.assertEquals(3, res.steps().size());
        Assertions.assertEquals(594L, res.totalDurationTicks());
        Assertions.assertEquals(29.7, res.totalDurationSeconds(), 0.01);
        Assertions.assertEquals("~29.7s", res.formattedEta());
    }

    @Test
    public void testMultipleDistinctRecipesOnSingleSharedMachinePage() {
        BoardPage maceratorPage = BoardManager.getInstance().addPage("Macerator Page", "ae2");
        RecipeNode macerator1 = RecipeNode.create("Macerator", 20.0, 30.0, GTVoltageTier.LV);
        macerator1.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:iron_dust"), "Iron Dust", 1.0, 1.0));
        maceratorPage.getGraph().addNode(macerator1);

        RecipeNode macerator2 = RecipeNode.create("Macerator", 20.0, 30.0, GTVoltageTier.LV);
        macerator2.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:copper_dust"), "Copper Dust", 1.0, 1.0));
        maceratorPage.getGraph().addNode(macerator2);

        PatternId patternIron = PatternId.ofKey("gtceu:iron_dust", "Iron Dust", ItemStack.EMPTY);
        PatternId patternCopper = PatternId.ofKey("gtceu:copper_dust", "Copper Dust", ItemStack.EMPTY);
        PatternGraphRegistry.getInstance().bind(patternIron, maceratorPage);

        Map<PatternId, Long> planCounts = new LinkedHashMap<>();
        planCounts.put(patternIron, 10L);
        planCounts.put(patternCopper, 10L);

        Ae2PlanEvaluationResult res = Ae2CraftingPlanEvaluator.evaluatePatternCounts(planCounts, 0);

        Assertions.assertEquals(1, res.steps().size());
        Assertions.assertEquals(400L, res.totalDurationTicks());
        Assertions.assertEquals(20.0, res.totalDurationSeconds(), 0.01);
        Assertions.assertEquals("~20.0s", res.formattedEta());
    }

    @Test
    public void testEtaDurationStringFormatting() {
        Assertions.assertEquals("< 1s", Ae2CraftingPlanEvaluator.formatEtaDuration(0.04));
        Assertions.assertEquals("~12.5s", Ae2CraftingPlanEvaluator.formatEtaDuration(12.5));
        Assertions.assertEquals("~2m 45s", Ae2CraftingPlanEvaluator.formatEtaDuration(165.0));
        Assertions.assertEquals("~1h 15m 30s", Ae2CraftingPlanEvaluator.formatEtaDuration(4530.0));
    }
}
