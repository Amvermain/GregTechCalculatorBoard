package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.history.HistoryManager;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.PageType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SubPageModuleUndoRedoBugTest {

    @BeforeEach
    public void setup() {
        BoardManager.getInstance().getPageManager().resetToDefault();
        BoardManager.getInstance().getPageManager().getPages().get(0).setName("Main Factory");
    }

    @Test
    public void testUndoCollapseRemovesSubPageAndPreventsProliferation() {
        BoardPage mainPage = BoardManager.getInstance().getPageManager().getActivePage();
        FlowGraph graph = mainPage.getGraph();
        HistoryManager historyManager = mainPage.getHistoryManager();

        int initialPageCount = BoardManager.getInstance().getPages().size();
        Assertions.assertEquals(1, initialPageCount);

        RecipeNode n1 = RecipeNode.create("Chemical Reactor 1", 100, 20, GTVoltageTier.MV);
        n1.setPos(100, 100);
        IngredientStack acid = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfuric_acid"), "Sulfuric Acid", 250);
        IngredientStack intermediate = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:diluted_acid"), "Diluted Acid", 250);
        n1.getInputs().add(acid);
        n1.getOutputs().add(intermediate);

        RecipeNode n2 = RecipeNode.create("Chemical Reactor 2", 100, 20, GTVoltageTier.MV);
        n2.setPos(300, 100);
        IngredientStack resin = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:epoxy_resin"), "Epoxy Resin", 1000);
        n2.getInputs().add(intermediate);
        n2.getOutputs().add(resin);

        graph.addNode(n1);
        graph.addNode(n2);
        graph.addConnection(n1.getId(), 0, n2.getId(), 0);

        List<RecipeNode> origNodes = new ArrayList<>(graph.getNodes());
        List<FlowGraph.ConnectionEdge> origEdges = new ArrayList<>(graph.getConnections());

        // First Collapse: Group into Module
        RecipeNode moduleNode1 = graph.groupIntoModule(Set.of(n1.getId(), n2.getId()), "Epoxy Module 1");
        Assertions.assertNotNull(moduleNode1);
        String subPageId1 = moduleNode1.getSubPageId();
        Assertions.assertNotNull(subPageId1);

        List<RecipeNode> groupedNodes1 = new ArrayList<>();
        for (RecipeNode n : origNodes) {
            if (!graph.getNodes().contains(n)) groupedNodes1.add(n);
        }
        List<FlowGraph.ConnectionEdge> rewires1 = new ArrayList<>(graph.getConnections());

        historyManager.record(new BoardCommand.GroupModuleCommand(groupedNodes1, moduleNode1, origEdges, rewires1));

        Assertions.assertEquals(2, BoardManager.getInstance().getPages().size(), "SubPage must be added on collapse");
        Assertions.assertTrue(BoardManager.getInstance().getPage(subPageId1).isPresent());

        // Undo First Collapse
        historyManager.undo(graph);

        // SubPage must be cleaned up from BoardManager on undo, preventing orphan page accumulation
        Assertions.assertTrue(
                BoardManager.getInstance().getPage(subPageId1).isEmpty(),
                "SubPage must be removed from BoardManager upon undoing module collapse"
        );
        Assertions.assertEquals(
                initialPageCount,
                BoardManager.getInstance().getPages().size(),
                "Total page count must return to initial state on undo"
        );

        // Second Collapse and Undo to verify no proliferation
        origNodes = new ArrayList<>(graph.getNodes());
        origEdges = new ArrayList<>(graph.getConnections());
        RecipeNode moduleNode2 = graph.groupIntoModule(Set.of(n1.getId(), n2.getId()), "Epoxy Module 2");
        Assertions.assertNotNull(moduleNode2);
        String subPageId2 = moduleNode2.getSubPageId();

        List<RecipeNode> groupedNodes2 = new ArrayList<>();
        for (RecipeNode n : origNodes) {
            if (!graph.getNodes().contains(n)) groupedNodes2.add(n);
        }
        List<FlowGraph.ConnectionEdge> rewires2 = new ArrayList<>(graph.getConnections());

        historyManager.record(new BoardCommand.GroupModuleCommand(groupedNodes2, moduleNode2, origEdges, rewires2));
        Assertions.assertEquals(2, BoardManager.getInstance().getPages().size());

        historyManager.undo(graph);
        Assertions.assertEquals(
                initialPageCount,
                BoardManager.getInstance().getPages().size(),
                "Repeated undo must prevent subpage proliferation"
        );

        // Redo Second Collapse
        historyManager.redo(graph);
        Assertions.assertEquals(
                2,
                BoardManager.getInstance().getPages().size(),
                "Redo must restore the module SubPage in BoardManager"
        );
        Assertions.assertTrue(
                BoardManager.getInstance().getPage(subPageId2).isPresent(),
                "SubPage must be restored upon redo"
        );
    }
}
