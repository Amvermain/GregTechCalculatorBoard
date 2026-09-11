package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.solver.FlowGraphModuleHandler;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.PageType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class DedicatedSubPageModuleTest {

    @BeforeEach
    public void setup() {
        BoardManager.getInstance().getPageManager().resetToDefault();
        BoardManager.getInstance().getPageManager().getPages().get(0).setName("Main Factory");
    }

    @Test
    public void testDedicatedSubPageCreationAndBoundaryPins() {
        BoardPage mainPage = BoardManager.getInstance().getPageManager().getActivePage();
        FlowGraph graph = mainPage.getGraph();

        RecipeNode n1 = RecipeNode.create("Chemical Reactor 1", 100, 20, GTVoltageTier.MV);
        n1.setPos(200, 100);
        IngredientStack acid = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfuric_acid"), "Sulfuric Acid", 250);
        IngredientStack intermediate = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:diluted_acid"), "Diluted Acid", 250);
        n1.getInputs().add(acid);
        n1.getOutputs().add(intermediate);

        RecipeNode n2 = RecipeNode.create("Chemical Reactor 2", 100, 20, GTVoltageTier.MV);
        n2.setPos(200, 300);
        IngredientStack glycerol = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:glycerol"), "Glycerol", 500);
        IngredientStack resin = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:epoxy_resin"), "Epoxy Resin", 1000);
        n2.getInputs().add(intermediate);
        n2.getInputs().add(glycerol);
        n2.getOutputs().add(resin);

        graph.addNode(n1);
        graph.addNode(n2);
        graph.addConnection(n1.getId(), 0, n2.getId(), 0);

        // Group into Module
        RecipeNode moduleNode = graph.groupIntoModule(Set.of(n1.getId(), n2.getId()), "Epoxy Process Module");
        Assertions.assertNotNull(moduleNode);
        Assertions.assertTrue(moduleNode.isModule());

        // 1:1 SubPage Verification (RFC-043)
        String subPageId = moduleNode.getSubPageId();
        Assertions.assertNotNull(subPageId);
        Assertions.assertFalse(subPageId.isEmpty());

        BoardPage subPage = BoardManager.getInstance().getPageManager().getPage(subPageId).orElse(null);
        Assertions.assertNotNull(subPage);
        Assertions.assertEquals(PageType.MODULE, subPage.getPageType());
        Assertions.assertTrue(subPage.isModuleSubPage());
        Assertions.assertEquals(mainPage.getId(), subPage.getParentPageId());
        Assertions.assertEquals(moduleNode.getId(), subPage.getParentModuleNodeId());

        // Boundary Pins Verification
        FlowGraph subGraph = subPage.getGraph();
        List<ModuleInputPin> inPins = subGraph.getNodes().stream()
                .filter(n -> n instanceof ModuleInputPin)
                .map(n -> (ModuleInputPin) n)
                .toList();
        List<ModuleOutputPin> outPins = subGraph.getNodes().stream()
                .filter(n -> n instanceof ModuleOutputPin)
                .map(n -> (ModuleOutputPin) n)
                .toList();

        Assertions.assertEquals(2, inPins.size());
        Assertions.assertEquals(1, outPins.size());

        Assertions.assertEquals(2, moduleNode.getContainedMachineCount());
        com.gtceu.calcboard.api.solver.BalanceSummary subSummary = com.gtceu.calcboard.api.solver.FlowGraphSolver.computeSummaryPreservingEfficiencies(subGraph);
        Assertions.assertEquals(2, subSummary.totalMachineCount());
        Assertions.assertFalse(subSummary.machineBreakdown().containsKey("Input Pin"));
        Assertions.assertFalse(subSummary.machineBreakdown().containsKey("Output Pin"));

        ModuleInputPin pin0 = inPins.get(0);
        Assertions.assertTrue(pin0.isBoundaryPin());
        Assertions.assertEquals(32, pin0.getCardWidth());
        Assertions.assertEquals(32, pin0.getCardHeight());
        Assertions.assertEquals(32, outPins.get(0).getCardWidth());
        Assertions.assertEquals(32, outPins.get(0).getCardHeight());
        pin0.setName("Custom Sulfuric Feed");
        Assertions.assertEquals("Custom Sulfuric Feed", pin0.getPinLabel());
        pin0.setPinLabel("Updated Feed Label");
        Assertions.assertEquals("Updated Feed Label", pin0.getName());

        com.gtceu.calcboard.api.solver.GlobalBalanceSummary globalSummary = com.gtceu.calcboard.api.solver.GlobalBalanceAggregator.compute(List.of(mainPage, subPage));
        Assertions.assertEquals(2, globalSummary.totalMachineCount());

        // Deterministic Y-order sorting
        Assertions.assertTrue(inPins.get(0).getPosY() <= inPins.get(1).getPosY());

        // SubPage scaling
        FlowGraphModuleHandler.scaleModuleSubPage(moduleNode, 2.0);
        for (RecipeNode sn : subGraph.getNodes()) {
            if (!(sn instanceof BoundaryPinNode)) {
                Assertions.assertEquals(2.0, sn.getMachineCount(), 0.001);
            }
        }

        // Expand module: boundary pins must not pollute the main graph
        boolean expanded = graph.expandModule(moduleNode);
        Assertions.assertTrue(expanded);

        for (RecipeNode n : graph.getNodes()) {
            Assertions.assertFalse(n instanceof BoundaryPinNode, "BoundaryPinNode must be excluded when expanding module");
        }
        Assertions.assertTrue(BoardManager.getInstance().getPageManager().getPage(subPageId).isEmpty(), "SubPage must be removed from BoardManager upon expanding");
    }

    @Test
    public void testRecursionGuardMaxDepth() {
        BoardPage page = BoardManager.getInstance().getPageManager().getActivePage();
        RecipeNode n1 = RecipeNode.create("Machine 1", 100, 20, GTVoltageTier.LV);
        page.getGraph().addNode(n1);

        // Simulated nesting depth check
        int depth0 = FlowGraphModuleHandler.calculateNestingDepth(page);
        Assertions.assertEquals(0, depth0);

        RecipeNode m1 = page.getGraph().groupIntoModule(Set.of(n1.getId()), "Level 1 Module");
        BoardPage sub1 = BoardManager.getInstance().getPageManager().getPage(m1.getSubPageId()).orElse(null);
        Assertions.assertNotNull(sub1);
        int depth1 = FlowGraphModuleHandler.calculateNestingDepth(sub1);
        Assertions.assertEquals(1, depth1);
    }
}
