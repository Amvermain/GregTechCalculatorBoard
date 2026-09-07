package com.gtceu.calcboard.client.render;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

@ExtendWith(MinecraftBootstrapExtension.class)
public class ScreenRenderingLagBenchmarkTest {

    @Test
    public void benchmarkScreenRenderCycle() {
        FlowGraph graph = new FlowGraph();

        RecipeNode pyrolyse = RecipeNode.create(ResourceLocation.tryParse("gtceu:pyrolyse_oven"), "Pyrolyse Oven", 384.0, 15.0, GTVoltageTier.HV);
        pyrolyse.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:dark_oak_log"), "Dark Oak Log", 16));
        pyrolyse.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:charcoal"), "Charcoal", 20));
        pyrolyse.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_vinegar"), "Wood Vinegar", 1000));
        pyrolyse.setMachineCount(1.0);
        graph.addNode(pyrolyse);

        RecipeNode dt = RecipeNode.create(ResourceLocation.tryParse("gtceu:distillation_tower"), "Distillation Tower", 288.0, 1.0, GTVoltageTier.HV);
        dt.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_vinegar"), "Wood Vinegar", 1000));
        dt.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 60));
        dt.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:acetic_acid"), "Acetic Acid", 250));
        dt.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:acetone"), "Acetone", 45));
        dt.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:methanol"), "Methanol", 15));
        dt.setMachineCount(1.0);
        dt.setBaseNode(true);
        dt.setPosX(200);
        dt.setPosY(100);
        graph.addNode(dt);

        CanvasGroupFrame frame1 = CanvasGroupFrame.createFromNodes("Shared Machine Pool 1", List.of(dt), CanvasGroupFrame.COLOR_CYAN);
        frame1.setSharedMachineFrame(true);
        frame1.setPosX(180); frame1.setPosY(50); frame1.setWidth(200); frame1.setHeight(300);
        graph.addFrame(frame1);

        RecipeNode lcr1 = createLcr("lcr1", 0.03, "minecraft:water", "Water", 60);
        lcr1.setPosX(500); lcr1.setPosY(50);
        RecipeNode lcr2 = createLcr("lcr2", 0.25, "gtceu:acetic_acid", "Acetic Acid", 250);
        lcr2.setPosX(500); lcr2.setPosY(150);
        RecipeNode lcr3 = createLcr("lcr3", 0.03, "gtceu:acetone", "Acetone", 45);
        lcr3.setPosX(500); lcr3.setPosY(250);
        RecipeNode lcr4 = createLcr("lcr4", 0.01, "gtceu:methanol", "Methanol", 15);
        lcr4.setPosX(500); lcr4.setPosY(350);

        graph.addNode(lcr1);
        graph.addNode(lcr2);
        graph.addNode(lcr3);
        graph.addNode(lcr4);

        CanvasGroupFrame frame2 = CanvasGroupFrame.createFromNodes("Shared Machine Pool 2", List.of(lcr1, lcr2, lcr3, lcr4), CanvasGroupFrame.COLOR_CYAN);
        frame2.setSharedMachineFrame(true);
        frame2.setPosX(480); frame2.setPosY(30); frame2.setWidth(220); frame2.setHeight(450);
        graph.addFrame(frame2);

        graph.addConnection(new FlowGraph.ConnectionEdge(pyrolyse.getId(), 1, dt.getId(), 0));
        graph.addConnection(new FlowGraph.ConnectionEdge(dt.getId(), 0, lcr1.getId(), 0));
        graph.addConnection(new FlowGraph.ConnectionEdge(dt.getId(), 1, lcr2.getId(), 0));
        graph.addConnection(new FlowGraph.ConnectionEdge(dt.getId(), 2, lcr3.getId(), 0));
        graph.addConnection(new FlowGraph.ConnectionEdge(dt.getId(), 3, lcr4.getId(), 0));

        List<RecipeNode> nodes = List.of(pyrolyse, dt, lcr1, lcr2, lcr3, lcr4);

        int iterations = 1000;
        long tDuty1 = 0, tReq1 = 0, tComp1 = 0, tEnclosed1 = 0;
        long tNodeOp = 0, tNodeStats = 0;

        for (int i = 0; i < iterations; i++) {
            long s = System.nanoTime();
            frame1.getEnclosedNodes(graph);
            tEnclosed1 += System.nanoTime() - s;

            s = System.nanoTime();
            frame1.computeTotalMachineDuty(graph);
            tDuty1 += System.nanoTime() - s;

            s = System.nanoTime();
            frame1.computeRequiredMachines(graph);
            tReq1 += System.nanoTime() - s;

            s = System.nanoTime();
            frame1.isMachineCompatible(graph);
            tComp1 += System.nanoTime() - s;

            s = System.nanoTime();
            for (RecipeNode n : nodes) {
                n.isOperational(graph);
            }
            tNodeOp += System.nanoTime() - s;

            s = System.nanoTime();
            for (RecipeNode n : nodes) {
                for (int p = 0; p < n.getInputs().size(); p++) {
                    graph.getInputPortStats(n, p);
                }
            }
            tNodeStats += System.nanoTime() - s;
        }

        System.out.printf("--- Fine-grained Breakdown (over %d runs) ---%n", iterations);
        System.out.printf("Frame 1 getEnclosedNodes: %.4f ms%n", (tEnclosed1 / (double) iterations) / 1_000_000.0);
        System.out.printf("Frame 1 computeTotalMachineDuty: %.4f ms%n", (tDuty1 / (double) iterations) / 1_000_000.0);
        System.out.printf("Frame 1 computeRequiredMachines: %.4f ms%n", (tReq1 / (double) iterations) / 1_000_000.0);
        System.out.printf("Frame 1 isMachineCompatible: %.4f ms%n", (tComp1 / (double) iterations) / 1_000_000.0);
        System.out.printf("All Nodes isOperational: %.4f ms%n", (tNodeOp / (double) iterations) / 1_000_000.0);
        System.out.printf("All Nodes inputPortStats: %.4f ms%n", (tNodeStats / (double) iterations) / 1_000_000.0);

        // Addon Catalog benchmark with representative addons
        List<com.gtceu.calcboard.api.catalog.MachineAddon> sampleAddons = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            sampleAddons.add(new com.gtceu.calcboard.api.catalog.MachineAddon("gtceu:coil_" + i, "Coil " + i, com.gtceu.calcboard.api.catalog.AddonCategory.COIL, "Coil", null));
            sampleAddons.add(new com.gtceu.calcboard.api.catalog.MachineAddon("gtceu:input_hatch_" + i, "Input Hatch " + i, com.gtceu.calcboard.api.catalog.AddonCategory.HATCH_BUS, "Input Hatch", null));
            sampleAddons.add(new com.gtceu.calcboard.api.catalog.MachineAddon("gtceu:output_hatch_" + i, "Output Hatch " + i, com.gtceu.calcboard.api.catalog.AddonCategory.HATCH_BUS, "Output Hatch", null));
            sampleAddons.add(new com.gtceu.calcboard.api.catalog.MachineAddon("gtceu:muffler_hatch_" + i, "Muffler Hatch " + i, com.gtceu.calcboard.api.catalog.AddonCategory.MAINTENANCE, "Muffler", null));
            sampleAddons.add(new com.gtceu.calcboard.api.catalog.MachineAddon("gtceu:rotor_" + i, "Rotor " + i, com.gtceu.calcboard.api.catalog.AddonCategory.ROTOR, "Rotor", null));
        }

        int addonRuns = 10;
        long tAddonFilter = 0;
        for (int i = 0; i < addonRuns; i++) {
            long s = System.nanoTime();
            for (com.gtceu.calcboard.api.catalog.MachineAddon a : sampleAddons) {
                a.isCompatibleWith(dt);
            }
            tAddonFilter += System.nanoTime() - s;
        }
        System.out.printf("Addon Catalog Compatibility Check (%d addons, %d runs): %.4f ms / check%n",
                sampleAddons.size(), addonRuns, (tAddonFilter / (double) addonRuns) / 1_000_000.0);
    }

    private RecipeNode createLcr(String id, double count, String inFluid, String fluidName, double amount) {
        RecipeNode lcr = RecipeNode.create(ResourceLocation.tryParse("gtceu:large_chemical_reactor"), "Large Chemical Reactor", 30.0, 8.0, GTVoltageTier.LV);
        lcr.addInput(IngredientStack.fluid(ResourceLocation.tryParse(inFluid), fluidName, amount));
        lcr.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", amount * 0.1));
        lcr.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:diluted_acid"), "Diluted Acid", amount * 0.05));
        lcr.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:output_fluid"), "Output Fluid", amount));
        lcr.setMachineCount(count);
        return lcr;
    }
}
