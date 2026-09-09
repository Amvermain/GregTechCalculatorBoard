package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class GroupCollapsePortBugTest {

    @Test
    public void testCollapseGroupWithCirculatingOrSelfConsumingPort() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation brineId = ResourceLocation.tryParse("gtceu:brine");
        ResourceLocation hiId = ResourceLocation.tryParse("gtceu:hydrogen_iodide");
        ResourceLocation carbonId = ResourceLocation.tryParse("gtceu:carbon_dust");
        ResourceLocation saltId = ResourceLocation.tryParse("gtceu:rock_salt");
        ResourceLocation hotBrineId = ResourceLocation.tryParse("gtceu:hot_brine");
        ResourceLocation debromBrineId = ResourceLocation.tryParse("gtceu:debrominated_brine");
        ResourceLocation otherId = ResourceLocation.tryParse("gtceu:other_fluid");

        // Machine 1: LCR 1
        RecipeNode m1 = RecipeNode.create("LCR 1", 3.0, 100.0, GTVoltageTier.UV);
        m1.addInput(IngredientStack.fluid(brineId, "Brine", 500.0));
        m1.addInput(IngredientStack.fluid(otherId, "Other", 250.0));
        m1.addOutput(IngredientStack.fluid(brineId, "Brine", 500.0));
        m1.addOutput(IngredientStack.fluid(hiId, "Hydrogen Iodide", 250.0));

        // Machine 2: LCR 2
        RecipeNode m2 = RecipeNode.create("LCR 2", 6.0, 100.0, GTVoltageTier.ZPM);
        m2.addInput(IngredientStack.item(carbonId, "Carbon", 1.0));
        m2.addInput(IngredientStack.fluid(brineId, "Brine", 500.0));
        m2.addOutput(IngredientStack.item(saltId, "Rock Salt", 2.0));
        m2.addOutput(IngredientStack.fluid(otherId, "Other In", 1000.0));

        // Machine 3: LCR 3
        RecipeNode m3 = RecipeNode.create("LCR 3", 3.0, 100.0, GTVoltageTier.ZPM);
        m3.addInput(IngredientStack.fluid(otherId, "Other In", 1000.0));
        m3.addInput(IngredientStack.fluid(hotBrineId, "Hot Brine", 1000.0)); // Unconnected internal input
        m3.addOutput(IngredientStack.fluid(debromBrineId, "Debrominated Brine", 1000.0));
        m3.addOutput(IngredientStack.fluid(hotBrineId, "Hot Brine", 1000.0)); // Unconnected internal output

        graph.addNode(m1);
        graph.addNode(m2);
        graph.addNode(m3);

        // Connections:
        // m1 out0 (brine) -> m2 in1 (brine)
        graph.addConnection(m1.getId(), 0, m2.getId(), 1);
        // m2 out1 (other) -> m3 in0 (other)
        graph.addConnection(m2.getId(), 1, m3.getId(), 0);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Group Frame", List.of(m1, m2, m3), CanvasGroupFrame.COLOR_PURPLE);
        graph.addFrame(frame);

        // Collapse into module
        RecipeNode module = graph.groupIntoModule(Set.of(m1.getId(), m2.getId(), m3.getId()), "Group Frame", frame);
        Assertions.assertNotNull(module);

        System.out.println("Module inputs: " + module.getInputs().stream().map(i -> i.getDisplayName() + " " + i.getAmount()).toList());
        System.out.println("Module outputs: " + module.getOutputs().stream().map(i -> i.getDisplayName() + " " + i.getAmount()).toList());

        // Check if Hot Brine is present in outputs
        boolean hasHotBrine = module.getOutputs().stream().anyMatch(i -> i.getId().equals(hotBrineId));
        Assertions.assertTrue(hasHotBrine, "Hot Brine output should be present in module outputs!");
    }
}
