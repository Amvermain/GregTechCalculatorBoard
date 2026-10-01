package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.RateTimeUnit;
import com.gtceu.calcboard.api.type.SupplyMode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.ResourceLocation;
import com.gtceu.calcboard.api.solver.WorkspaceFlowCoordinator;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.team.TeamWorkspacePage;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class JunctionSupplyDialogTest {

    @Test
    @DisplayName("RFC-020: JunctionSupplyDialog scrolls multi-outgoing lines and keeps footer buttons clickable")
    void testOutgoingListScrollingAndFooterButtons() {
        BoardScreen screen = new BoardScreen();
        screen.width = 800;
        screen.height = 600;

        FlowGraph graph = screen.getGraph();
        RecipeNode junction = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Junction", 20, 0, GTVoltageTier.LV);
        junction.setReroute(true);
        junction.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1));
        graph.addNode(junction);

        for (int i = 0; i < 5; i++) {
            RecipeNode consumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:consumer_" + i), "Consumer " + i, 20, 30, GTVoltageTier.LV);
            consumer.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 20));
            graph.addNode(consumer);
            graph.addConnection(new FlowGraph.ConnectionEdge(junction.getId(), 0, consumer.getId(), 0, (i + 1) * 10.0));
        }

        JunctionSupplyDialog dialog = new JunctionSupplyDialog(screen);
        dialog.open(junction);
        Assertions.assertTrue(dialog.isVisible());

        int x = (800 - 340) / 2; // 230
        int y = (600 - 250) / 2; // 175

        // Click tab 1 (Allocation Tab): tabY = y + 46, second tab is around x + 180
        int tab1X = x + 14 + (340 - 24) / 4 * 3;
        int tab1Y = y + 54;
        boolean tabClicked = dialog.mouseClicked(tab1X, tab1Y, 0);
        Assertions.assertTrue(tabClicked, "Should switch to tab 1");

        // Test scrolling down: 5 items, visible 3 -> maxScroll is 2
        boolean scrolledDown1 = dialog.mouseScrolled(x + 50, y + 150, -1.0);
        Assertions.assertTrue(scrolledDown1, "Should scroll down to offset 1");

        boolean scrolledDown2 = dialog.mouseScrolled(x + 50, y + 150, -1.0);
        Assertions.assertTrue(scrolledDown2, "Should scroll down to offset 2");

        // Scrolling further down should be capped at maxScroll (2)
        boolean scrolledDown3 = dialog.mouseScrolled(x + 50, y + 150, -1.0);
        Assertions.assertFalse(scrolledDown3, "Should be at max scroll limit");

        // Test scrolling back up
        boolean scrolledUp1 = dialog.mouseScrolled(x + 50, y + 150, 1.0);
        Assertions.assertTrue(scrolledUp1, "Should scroll back up to offset 1");

        // Test clicking Cancel button at footer (y + DIALOG_HEIGHT - 24 = y + 226)
        int cancelBtnX = x + 340 - (75 * 2) - 14 + 10;
        int btnY = y + 250 - 24 + 5;
        boolean cancelClicked = dialog.mouseClicked(cancelBtnX, btnY, 0);
        Assertions.assertTrue(cancelClicked, "Cancel button must be clickable and not intercepted by hidden edit boxes");
        Assertions.assertFalse(dialog.isVisible(), "Dialog should be closed after clicking cancel");
    }

    @Test
    @DisplayName("RFC-020: JunctionSupplyDialog toggles buffer mode and applies changes")
    void testApplyChangesAndBufferToggle() {
        BoardScreen screen = new BoardScreen();
        screen.width = 800;
        screen.height = 600;

        FlowGraph graph = screen.getGraph();
        RecipeNode junction = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction2"), "Junction 2", 20, 0, GTVoltageTier.LV);
        junction.setReroute(true);
        junction.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper Ingot", 1));
        graph.addNode(junction);

        RecipeNode consumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:consumer"), "Consumer", 20, 30, GTVoltageTier.LV);
        consumer.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper Ingot", 10));
        graph.addNode(consumer);
        graph.addConnection(new FlowGraph.ConnectionEdge(junction.getId(), 0, consumer.getId(), 0, 0.0));

        JunctionSupplyDialog dialog = new JunctionSupplyDialog(screen);
        dialog.open(junction);

        int x = (800 - 340) / 2;
        int y = (600 - 250) / 2;

        // Switch to tab 1 (Allocation Tab)
        int tab1X = x + 14 + (340 - 24) / 4 * 3;
        int tab1Y = y + 54;
        dialog.mouseClicked(tab1X, tab1Y, 0);

        // Click buffer toggle (radioX = x + 14, radioY = y + 85, 10x10)
        int toggleX = x + 18;
        int toggleY = y + 88;
        boolean toggleClicked = dialog.mouseClicked(toggleX, toggleY, 0);
        Assertions.assertTrue(toggleClicked, "Buffer toggle radio should be clicked");

        // Click Apply button: applyBtnX = x + 340 - 75 - 8 = x + 257
        int applyBtnX = x + 340 - 75 - 8 + 10;
        int btnY = y + 250 - 24 + 5;
        boolean applyClicked = dialog.mouseClicked(applyBtnX, btnY, 0);
        Assertions.assertTrue(applyClicked, "Apply button should be clickable");
        Assertions.assertFalse(dialog.isVisible(), "Dialog should close after apply");

        // Verify that junction now has buffer enabled
        Assertions.assertTrue(junction.isJunctionBuffer(), "Junction should have buffer enabled after apply");
    }

    @Test
    @DisplayName("RFC-042: JunctionSupplyDialog selects WEIGHTED split mode and applies changes")
    void testSplitModeSelectionAndApply() {
        BoardScreen screen = new BoardScreen();
        screen.width = 800;
        screen.height = 600;

        FlowGraph graph = screen.getGraph();
        RecipeNode junction = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction_split"), "Junction Split", 20, 0, GTVoltageTier.LV);
        junction.setReroute(true);
        junction.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1));
        graph.addNode(junction);

        RecipeNode consumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:consumer"), "Consumer", 20, 30, GTVoltageTier.LV);
        consumer.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 10));
        graph.addNode(consumer);
        graph.addConnection(new FlowGraph.ConnectionEdge(junction.getId(), 0, consumer.getId(), 0, 0.0, 0, 1.0));

        JunctionSupplyDialog dialog = new JunctionSupplyDialog(screen);
        dialog.open(junction);

        int x = (800 - 340) / 2;
        int y = (600 - 250) / 2;

        int tab1X = x + 14 + (340 - 24) / 4 * 3;
        int tab1Y = y + 54;
        dialog.mouseClicked(tab1X, tab1Y, 0);

        int modeY = y + 102;
        int btn3X = x + 72 + (82 + 5) * 2;
        boolean weightedClicked = dialog.mouseClicked(btn3X + 10, modeY + 5, 0);
        Assertions.assertTrue(weightedClicked, "WEIGHTED button should be clicked");

        int applyBtnX = x + 340 - 75 - 8 + 10;
        int btnY = y + 250 - 24 + 5;
        boolean applyClicked = dialog.mouseClicked(applyBtnX, btnY, 0);
        Assertions.assertTrue(applyClicked);

        Assertions.assertEquals(com.gtceu.calcboard.api.type.FlowSplitMode.WEIGHTED, junction.getJunctionSplitMode());
    }

    @Test
    @DisplayName("Regression: JunctionSupplyDialog preserves weight 0.0 without converting to 1.0")
    void testZeroWeightPreservationInDialog() {
        BoardScreen screen = new BoardScreen();
        screen.width = 800;
        screen.height = 600;

        FlowGraph graph = screen.getGraph();
        RecipeNode junction = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction_zero_w"), "Junction Zero", 20, 0, GTVoltageTier.LV);
        junction.setReroute(true);
        junction.setJunctionSplitMode(com.gtceu.calcboard.api.type.FlowSplitMode.WEIGHTED);
        junction.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1));
        graph.addNode(junction);

        RecipeNode consumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:consumer"), "Consumer", 20, 30, GTVoltageTier.LV);
        consumer.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 10));
        graph.addNode(consumer);

        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge(junction.getId(), 0, consumer.getId(), 0, 0.0, 0, 0.0);
        graph.addConnection(edge);
        Assertions.assertEquals(0.0, graph.getConnections().get(0).weight(), 0.001);

        JunctionSupplyDialog dialog = new JunctionSupplyDialog(screen);
        dialog.open(junction);

        int x = (800 - 340) / 2;
        int y = (600 - 250) / 2;

        int tab1X = x + 14 + (340 - 24) / 4 * 3;
        int tab1Y = y + 54;
        dialog.mouseClicked(tab1X, tab1Y, 0);

        int applyBtnX = x + 340 - 75 - 8 + 10;
        int btnY = y + 250 - 24 + 5;
        boolean applyClicked = dialog.mouseClicked(applyBtnX, btnY, 0);
        Assertions.assertTrue(applyClicked);

        FlowGraph.ConnectionEdge afterApply = graph.getConnections().get(0);
        Assertions.assertEquals(0.0, afterApply.weight(), 0.001, "Weight 0.0 must be preserved upon apply");
    }

    @Test
    @DisplayName("JunctionSupplyDialog scales external rate by active time unit and supports smart parsing")
    void testFixedSupplyRateScalingAndSmartParsing() throws Exception {
        FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_TICK);
        try {
            BoardScreen screen = new BoardScreen();
            screen.width = 800;
            screen.height = 600;

            FlowGraph graph = screen.getGraph();
            RecipeNode junction = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction_rate"), "Junction Rate", 20, 0, GTVoltageTier.LV);
            junction.setReroute(true);
            junction.setSupplyMode(SupplyMode.FIXED_RATE);
            junction.setExternalSupplyRate(2000.0);
            junction.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:hot_brine"), "Hot Brine", 1000));
            graph.addNode(junction);

            JunctionSupplyDialog dialog = new JunctionSupplyDialog(screen);
            dialog.open(junction);

            Field rateEditBoxField = JunctionSupplyDialog.class.getDeclaredField("rateEditBox");
            rateEditBoxField.setAccessible(true);
            EditBox rateEditBox = (EditBox) rateEditBoxField.get(dialog);
            Assertions.assertNotNull(rateEditBox);
            Assertions.assertEquals("100", rateEditBox.getValue());

            int x = (800 - 340) / 2;
            int y = (600 - 250) / 2;
            int applyBtnX = x + 340 - 75 - 8 + 10;
            int btnY = y + 250 - 24 + 5;

            rateEditBox.setValue("2000");
            dialog.mouseClicked(applyBtnX, btnY, 0);
            Assertions.assertEquals(40000.0, junction.getExternalSupplyRate(), 0.001);

            dialog.open(junction);
            rateEditBox = (EditBox) rateEditBoxField.get(dialog);
            Assertions.assertEquals("2000", rateEditBox.getValue());

            rateEditBox.setValue("2B");
            dialog.mouseClicked(applyBtnX, btnY, 0);
            Assertions.assertEquals(40000.0, junction.getExternalSupplyRate(), 0.001);

            dialog.open(junction);
            rateEditBox = (EditBox) rateEditBoxField.get(dialog);
            rateEditBox.setValue("2B/s");
            dialog.mouseClicked(applyBtnX, btnY, 0);
            Assertions.assertEquals(2000.0, junction.getExternalSupplyRate(), 0.001);

            dialog.open(junction);
            rateEditBox = (EditBox) rateEditBoxField.get(dialog);
            rateEditBox.setValue("100mB/t");
            dialog.mouseClicked(applyBtnX, btnY, 0);
            Assertions.assertEquals(2000.0, junction.getExternalSupplyRate(), 0.001);

            dialog.open(junction);
            rateEditBox = (EditBox) rateEditBoxField.get(dialog);
            rateEditBox.setValue("0");
            dialog.mouseClicked(applyBtnX, btnY, 0);
            Assertions.assertEquals(0.0, junction.getExternalSupplyRate(), 0.001);
        } finally {
            FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_SECOND);
        }
    }

    @Test
    @DisplayName("Candidate pages and junctions are strictly isolated between personal and team workspaces")
    void testWorkspaceCandidatePagesIsolation() throws Exception {
        BoardManager bm = BoardManager.getInstance();
        List<BoardPage> personalPages = bm.getPages();
        personalPages.clear();
        BoardPage localA = new BoardPage("local_page_a", "Local Page A", new FlowGraph());
        BoardPage localB = new BoardPage("local_page_b", "Local Page B", new FlowGraph());
        personalPages.add(localA);
        personalPages.add(localB);
        bm.openPage("local_page_a");

        RecipeNode localJuncA = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Local Junction A", 0, 0, GTVoltageTier.LV);
        localJuncA.setReroute(true);
        localA.getGraph().addNode(localJuncA);

        RecipeNode localJuncB = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Local Junction B", 0, 0, GTVoltageTier.LV);
        localJuncB.setReroute(true);
        localB.getGraph().addNode(localJuncB);

        ClientWorkspaceState teamState = ClientWorkspaceState.getInstance();
        teamState.clear();
        teamState.setCurrentTeamId(java.util.UUID.randomUUID());
        teamState.setCurrentTeamName("Test Team");

        TeamWorkspacePage teamP1 = new TeamWorkspacePage("team_page_1", "Team Page 1");
        TeamWorkspacePage teamP2 = new TeamWorkspacePage("team_page_2", "Team Page 2");
        teamState.updateRemotePages(List.of(teamP1, teamP2));
        teamState.setActiveTeamPageId("team_page_1");

        RecipeNode teamJunc1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Team Junction 1", 0, 0, GTVoltageTier.LV);
        teamJunc1.setReroute(true);
        teamState.getTeamGraph("team_page_1").addNode(teamJunc1);

        RecipeNode teamJunc2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Team Junction 2", 0, 0, GTVoltageTier.LV);
        teamJunc2.setReroute(true);
        teamState.getTeamGraph("team_page_2").addNode(teamJunc2);

        BoardScreen screen = new BoardScreen();
        screen.width = 800;
        screen.height = 600;
        JunctionSupplyDialog dialog = new JunctionSupplyDialog(screen);

        Method getCandidatePagesMethod = JunctionSupplyDialog.class.getDeclaredMethod("getCandidatePages");
        getCandidatePagesMethod.setAccessible(true);

        try {
            teamState.setCurrentMode(ClientWorkspaceState.WorkspaceMode.LOCAL);
            dialog.open(localJuncA);
            @SuppressWarnings("unchecked")
            List<BoardPage> localCandidates = (List<BoardPage>) getCandidatePagesMethod.invoke(dialog);
            Assertions.assertEquals(1, localCandidates.size());
            Assertions.assertEquals("local_page_b", localCandidates.get(0).getId());

            teamState.setCurrentMode(ClientWorkspaceState.WorkspaceMode.TEAM);
            dialog.open(teamJunc1);
            @SuppressWarnings("unchecked")
            List<BoardPage> teamCandidates = (List<BoardPage>) getCandidatePagesMethod.invoke(dialog);
            Assertions.assertEquals(1, teamCandidates.size());
            Assertions.assertEquals("team_page_2", teamCandidates.get(0).getId());

            BoardPage resolvedTeamP2 = ClientWorkspaceState.resolveActiveWorkspacePage("team_page_2");
            Assertions.assertNotNull(resolvedTeamP2);
            Assertions.assertEquals("Team Page 2", resolvedTeamP2.getName());

            teamState.setCurrentMode(ClientWorkspaceState.WorkspaceMode.LOCAL);
            BoardPage resolvedLocalB = ClientWorkspaceState.resolveActiveWorkspacePage("local_page_b");
            Assertions.assertNotNull(resolvedLocalB);
            Assertions.assertEquals("Local Page B", resolvedLocalB.getName());
        } finally {
            teamState.clear();
            teamState.setCurrentMode(ClientWorkspaceState.WorkspaceMode.LOCAL);
        }
    }

    @Test
    @DisplayName("CrossPageSourceSearchDialog respects active workspace mode and isolates candidate junctions")
    void testCrossPageSourceSearchDialogIsolation() throws Exception {
        BoardManager bm = BoardManager.getInstance();
        List<BoardPage> personalPages = bm.getPages();
        personalPages.clear();
        BoardPage localA = new BoardPage("local_search_a", "Local A", new FlowGraph());
        BoardPage localB = new BoardPage("local_search_b", "Local B", new FlowGraph());
        personalPages.add(localA);
        personalPages.add(localB);
        bm.openPage("local_search_a");

        RecipeNode localConsumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Local Consumer", 0, 0, GTVoltageTier.LV);
        localConsumer.setReroute(true);
        localA.getGraph().addNode(localConsumer);

        RecipeNode localSrc = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Local Source", 0, 0, GTVoltageTier.LV);
        localSrc.setReroute(true);
        localB.getGraph().addNode(localSrc);

        ClientWorkspaceState teamState = ClientWorkspaceState.getInstance();
        teamState.clear();
        teamState.setCurrentTeamId(java.util.UUID.randomUUID());
        teamState.setCurrentTeamName("Test Team");

        TeamWorkspacePage teamP1 = new TeamWorkspacePage("team_search_1", "Team 1");
        TeamWorkspacePage teamP2 = new TeamWorkspacePage("team_search_2", "Team 2");
        teamState.updateRemotePages(List.of(teamP1, teamP2));
        teamState.setActiveTeamPageId("team_search_1");

        RecipeNode teamConsumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Team Consumer", 0, 0, GTVoltageTier.LV);
        teamConsumer.setReroute(true);
        teamState.getTeamGraph("team_search_1").addNode(teamConsumer);

        RecipeNode teamSrc = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Team Source", 0, 0, GTVoltageTier.LV);
        teamSrc.setReroute(true);
        teamState.getTeamGraph("team_search_2").addNode(teamSrc);

        BoardScreen screen = new BoardScreen();
        screen.width = 800;
        screen.height = 600;
        CrossPageSourceSearchDialog searchDialog = new CrossPageSourceSearchDialog(screen);

        Field allCandidatesField = CrossPageSourceSearchDialog.class.getDeclaredField("allCandidates");
        allCandidatesField.setAccessible(true);

        try {
            teamState.setCurrentMode(ClientWorkspaceState.WorkspaceMode.LOCAL);
            searchDialog.open(localConsumer, null, null, (p, n) -> {});
            @SuppressWarnings("unchecked")
            List<CrossPageSourceSearchDialog.SourceCandidate> localList =
                    (List<CrossPageSourceSearchDialog.SourceCandidate>) allCandidatesField.get(searchDialog);
            Assertions.assertEquals(1, localList.size());
            Assertions.assertEquals("local_search_b", localList.get(0).page().getId());
            searchDialog.close();

            teamState.setCurrentMode(ClientWorkspaceState.WorkspaceMode.TEAM);
            searchDialog.open(teamConsumer, null, null, (p, n) -> {});
            @SuppressWarnings("unchecked")
            List<CrossPageSourceSearchDialog.SourceCandidate> teamList =
                    (List<CrossPageSourceSearchDialog.SourceCandidate>) allCandidatesField.get(searchDialog);
            Assertions.assertEquals(1, teamList.size());
            Assertions.assertEquals("team_search_2", teamList.get(0).page().getId());
            searchDialog.close();
        } finally {
            teamState.clear();
            teamState.setCurrentMode(ClientWorkspaceState.WorkspaceMode.LOCAL);
        }
    }

    @Test
    @DisplayName("Cross-page junction flow coordination works seamlessly in team workspace")
    void testCrossPageFlowCoordinationInTeamWorkspace() {
        ClientWorkspaceState teamState = ClientWorkspaceState.getInstance();
        teamState.clear();
        teamState.setCurrentTeamId(java.util.UUID.randomUUID());
        teamState.setCurrentTeamName("Engineering Team");

        TeamWorkspacePage producerPage = new TeamWorkspacePage("team_prod", "Producer Page");
        TeamWorkspacePage consumerPage = new TeamWorkspacePage("team_cons", "Consumer Page");
        teamState.updateRemotePages(List.of(producerPage, consumerPage));
        teamState.setActiveTeamPageId("team_prod");

        FlowGraph prodGraph = teamState.getTeamGraph("team_prod");
        FlowGraph consGraph = teamState.getTeamGraph("team_cons");

        RecipeNode prodJunction = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Team Sulfuric Acid Producer", 0, 0, GTVoltageTier.LV);
        prodJunction.setReroute(true);
        IngredientStack acid = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfuric_acid"), "Sulfuric Acid", 1000);
        prodJunction.bindRerouteIngredient(acid);
        prodJunction.setSupplyMode(SupplyMode.FIXED_RATE);
        prodJunction.setExternalSupplyRate(500.0);
        prodGraph.addNode(prodJunction);

        RecipeNode consJunction = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Team Sulfuric Acid Consumer", 0, 0, GTVoltageTier.LV);
        consJunction.setReroute(true);
        consJunction.bindRerouteIngredient(acid);
        consJunction.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        consJunction.asJunction().setLinkedSource("team_prod", prodJunction.getId());
        consGraph.addNode(consJunction);

        RecipeNode consumerMachine = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Reactor", 20, 0, GTVoltageTier.LV);
        consumerMachine.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfuric_acid"), "Sulfuric Acid", 200));
        consGraph.addNode(consumerMachine);
        consGraph.addConnection(new FlowGraph.ConnectionEdge(consJunction.getId(), 0, consumerMachine.getId(), 0, 200.0));

        teamState.setCurrentMode(ClientWorkspaceState.WorkspaceMode.TEAM);

        try {
            List<BoardPage> teamBoardPages = teamState.getTeamPagesAsBoardPages();
            WorkspaceFlowCoordinator.WorkspaceFlowResult result = WorkspaceFlowCoordinator.coordinate(teamBoardPages);

            Assertions.assertFalse(result.hasCycles());
            Assertions.assertEquals(200.0, consJunction.getAllocatedInputRate(), 0.001);
            Assertions.assertEquals(200.0, prodJunction.getAllocatedExportRate(), 0.001);

            WorkspaceFlowCoordinator.SourceJunctionMetrics metrics =
                    WorkspaceFlowCoordinator.calculateSourceJunctionMetrics(teamBoardPages.get(0), prodJunction);
            Assertions.assertEquals(500.0, metrics.totalProduction(), 0.001);
            Assertions.assertEquals(200.0, metrics.remoteExport(), 0.001);
            Assertions.assertEquals(300.0, metrics.availableSurplus(), 0.001);
        } finally {
            teamState.clear();
            teamState.setCurrentMode(ClientWorkspaceState.WorkspaceMode.LOCAL);
            WorkspaceFlowCoordinator.invalidate();
        }
    }
}
