package com.gtceu.calcboard.client.tutorial;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.tutorial.TutorialTrackRegistry;
import com.gtceu.calcboard.client.gui.tutorial.model.TutorialTrackType;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MinecraftBootstrapExtension.class)
public class TutorialTrackTest {

    private TutorialManager mgr;
    private BoardManager bm;

    @BeforeEach
    public void setUp() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.start();
        TutorialManager.registerEventListeners();
        mgr = TutorialManager.getInstance();
        bm = BoardManager.getInstance();
        bm.resetToDefault();
        mgr.stopTutorial();
    }

    @AfterEach
    public void tearDown() {
        if (mgr != null) {
            mgr.stopTutorial();
        }
        if (bm != null) {
            bm.resetToDefault();
        }
    }

    @Test
    public void testTrackRegistryInitialization() {
        TutorialTrackRegistry reg = TutorialTrackRegistry.getInstance();
        Assertions.assertEquals(4, reg.getFastTrackSteps().size(), "Fast-track must contain exactly 4 steps");
        Assertions.assertEquals(4, reg.getChapters().size(), "Academy must register 4 core chapters");

        Assertions.assertNotNull(reg.getChapter("ch1_solver"));
        Assertions.assertNotNull(reg.getChapter("ch2_wiring"));
        Assertions.assertNotNull(reg.getChapter("ch3_packaging"));
        Assertions.assertNotNull(reg.getChapter("ch4_workspace"));

        Assertions.assertEquals(4, reg.getChapterSteps("ch1_solver").size());
        Assertions.assertEquals(4, reg.getChapterSteps("ch2_wiring").size());
        Assertions.assertEquals(4, reg.getChapterSteps("ch3_packaging").size());
        Assertions.assertEquals(3, reg.getChapterSteps("ch4_workspace").size());
    }

    @Test
    public void testFastTrackProgression() {
        mgr.startFastTrack(null);
        Assertions.assertTrue(mgr.isActive());
        Assertions.assertTrue(mgr.isFastTrackActive());
        Assertions.assertEquals(TutorialTrackType.FAST_TRACK, mgr.getTrackType());
        Assertions.assertEquals(0, mgr.getFastTrackStepIndex());

        BoardPage tutPage = mgr.getTutorialPage();
        Assertions.assertNotNull(tutPage);

        RecipeNode boiler = RecipeNode.create("Test Boiler", 20.0, 0.0, GTVoltageTier.LV);
        mgr.onNodeAdded(boiler);
        Assertions.assertEquals(1, mgr.getFastTrackStepIndex(), "Step 0 advances to 1 on node add");

        mgr.onWireConnected(false);
        Assertions.assertEquals(2, mgr.getFastTrackStepIndex(), "Step 1 advances to 2 on wire connected");

        mgr.onAutoRatioTriggered();
        Assertions.assertEquals(3, mgr.getFastTrackStepIndex(), "Step 2 advances to 3 on auto-ratio");

        mgr.onRateUnitToggled();
        Assertions.assertEquals(com.gtceu.calcboard.client.gui.tutorial.TutorialStep.COMPLETED, mgr.getCurrentStep(), "Step 3 rate unit toggle completes fast-track onboarding");
    }

    @Test
    public void testChapter1SolverLifecycle() {
        mgr.startChapter(null, "ch1_solver");
        Assertions.assertTrue(mgr.isActive());
        Assertions.assertTrue(mgr.isChapterActive());
        Assertions.assertEquals("ch1_solver", mgr.getActiveChapterId());
        Assertions.assertEquals(0, mgr.getActiveChapterStepIndex());

        mgr.onAnchorConfigured();
        Assertions.assertEquals(1, mgr.getActiveChapterStepIndex(), "Anchor configuration advances ch1 to step 1");

        mgr.onAutoRatioTriggered();
        Assertions.assertEquals(2, mgr.getActiveChapterStepIndex(), "Auto-ratio advances ch1 to step 2");

        mgr.onFractionalRatioTriggered();
        Assertions.assertEquals(3, mgr.getActiveChapterStepIndex(), "Fractional ratio optimization advances ch1 to step 3");

        mgr.onLoopScaled();
        Assertions.assertEquals(com.gtceu.calcboard.client.gui.tutorial.TutorialStep.COMPLETED, mgr.getCurrentStep(), "Loop steady-state scaling completes ch1");
    }

    @Test
    public void testChapter2WiringLifecycle() {
        mgr.startChapter(null, "ch2_wiring");
        Assertions.assertTrue(mgr.isActive());
        Assertions.assertEquals("ch2_wiring", mgr.getActiveChapterId());
        Assertions.assertEquals(0, mgr.getActiveChapterStepIndex());

        mgr.onJunctionInserted();
        Assertions.assertEquals(1, mgr.getActiveChapterStepIndex());

        mgr.onWirePriorityChanged(new FlowGraph.ConnectionEdge("a", 0, "b", 0, 1), 1);
        Assertions.assertEquals(2, mgr.getActiveChapterStepIndex());

        mgr.onPortVoidConfigured();
        Assertions.assertEquals(3, mgr.getActiveChapterStepIndex());

        mgr.onPortTagCycled();
        Assertions.assertEquals(com.gtceu.calcboard.client.gui.tutorial.TutorialStep.COMPLETED, mgr.getCurrentStep(), "Tag cycling completes ch2");
    }

    @Test
    public void testChapter3PackagingLifecycle() {
        mgr.startChapter(null, "ch3_packaging");
        Assertions.assertTrue(mgr.isActive());
        Assertions.assertEquals("ch3_packaging", mgr.getActiveChapterId());
        Assertions.assertEquals(0, mgr.getActiveChapterStepIndex());

        mgr.onSharedMachineFramed();
        Assertions.assertEquals(1, mgr.getActiveChapterStepIndex());

        mgr.onModuleGrouped();
        Assertions.assertEquals(2, mgr.getActiveChapterStepIndex());

        mgr.onSubpageEntered();
        mgr.onSubpageExited();
        Assertions.assertEquals(3, mgr.getActiveChapterStepIndex());

        mgr.onBOMOpened();
        Assertions.assertEquals(com.gtceu.calcboard.client.gui.tutorial.TutorialStep.COMPLETED, mgr.getCurrentStep(), "BOM inspection completes ch3");
    }

    @Test
    public void testChapter4WorkspaceLifecycle() {
        mgr.startChapter(null, "ch4_workspace");
        Assertions.assertTrue(mgr.isActive());
        Assertions.assertEquals("ch4_workspace", mgr.getActiveChapterId());
        Assertions.assertEquals(0, mgr.getActiveChapterStepIndex());

        mgr.onPageSettingsConfigured();
        Assertions.assertEquals(1, mgr.getActiveChapterStepIndex());

        mgr.onFolderBrowserOpened();
        Assertions.assertEquals(2, mgr.getActiveChapterStepIndex());

        mgr.onGlobalBalanceOpened();
        Assertions.assertEquals(com.gtceu.calcboard.client.gui.tutorial.TutorialStep.COMPLETED, mgr.getCurrentStep(), "Global balance dashboard completes ch4");
    }

    @Test
    public void testPageIsolationDuringTutorial() {
        BoardPage userPage = bm.getActivePage();
        userPage.setName("User Factory Production");
        RecipeNode userNode = RecipeNode.create("User Node", 20.0, 32.0, GTVoltageTier.LV);
        userPage.getGraph().addNode(userNode);
        int initialPageCount = bm.getPages().size();

        mgr.startFastTrack(null);
        Assertions.assertEquals(initialPageCount + 1, bm.getPages().size());
        BoardPage tutPage = mgr.getTutorialPage();
        Assertions.assertNotNull(tutPage);
        Assertions.assertNotEquals(userPage.getId(), tutPage.getId());

        mgr.nextStep();
        mgr.nextStep();
        Assertions.assertEquals(1, userPage.getGraph().getNodes().size(), "User page graph must remain unmutated");
        Assertions.assertEquals("User Node", userPage.getGraph().getNodes().get(0).getName());

        mgr.stopTutorial();
        Assertions.assertFalse(mgr.isActive());
        Assertions.assertEquals(1, userPage.getGraph().getNodes().size());
    }

    @Test
    public void testFastTrackGlowingAndNodeIdTracking() {
        mgr.startFastTrack(null);
        Assertions.assertTrue(mgr.isToolbarButtonGlowing("add_recipe"), "add_recipe button must glow on step 0");

        mgr.nextStep(); // to Step 1 (drag to connect)
        String boilerId = mgr.getBoilerNodeId();
        Assertions.assertNotNull(boilerId, "Boiler node ID must be tracked on step 1");
        Assertions.assertTrue(mgr.isPortGlowing(boilerId, false, 0), "Boiler steam output port must glow on step 1");

        mgr.nextStep(); // to Step 2 (auto-ratio)
        String turbineId = mgr.getTurbineNodeId();
        Assertions.assertNotNull(turbineId, "Turbine node ID must be tracked on step 2");
        Assertions.assertTrue(mgr.isToolbarButtonGlowing("auto_ratio"), "auto_ratio button must glow on step 2");
        Assertions.assertTrue(mgr.isWireGlowing(boilerId, turbineId), "Wire between boiler and turbine must glow on step 2");

        mgr.nextStep(); // to Step 3 (rate unit toggle)
        Assertions.assertTrue(mgr.isWireGlowing(boilerId, turbineId), "Wire must continue glowing on step 3");
    }

    @Test
    public void testAcademyGlowingAndHighlights() {
        mgr.startChapter(null, "ch1_solver");
        BoardPage tutPage = mgr.getTutorialPage();
        RecipeNode bender = tutPage.getGraph().getNodes().stream().filter(n -> "Plate Bender".equals(n.getName())).findFirst().orElse(null);
        Assertions.assertNotNull(bender);
        Assertions.assertTrue(mgr.isNodeBaseTargetButtonGlowing(bender.getId()), "Anchor target must glow in ch1 step 0");

        mgr.nextStep(); // step 1 (auto ratio)
        Assertions.assertTrue(mgr.isToolbarButtonGlowing("auto_ratio"), "auto_ratio button must glow in ch1 step 1");

        mgr.startChapter(null, "ch2_wiring");
        mgr.nextStep(); // step 1
        mgr.nextStep(); // step 2 (byproduct voiding)
        BoardPage ch2Page = mgr.getTutorialPage();
        RecipeNode separator = ch2Page.getGraph().getNodes().stream().filter(n -> "Air Separator".equals(n.getName())).findFirst().orElse(null);
        Assertions.assertNotNull(separator);
        Assertions.assertTrue(mgr.isPortGlowing(separator.getId(), false, 1), "Oxygen byproduct port must glow in ch2 step 2");

        mgr.nextStep(); // step 3 (tag cycling)
        RecipeNode extruder = ch2Page.getGraph().getNodes().stream().filter(n -> "Plate Extruder".equals(n.getName())).findFirst().orElse(null);
        Assertions.assertNotNull(extruder);
        Assertions.assertTrue(mgr.isPortGlowing(extruder.getId(), true, 0), "Iron ingot input port must glow in ch2 step 3");

        mgr.startChapter(null, "ch4_workspace");
        mgr.nextStep(); // step 1 (folder browser)
        Assertions.assertTrue(mgr.isToolbarButtonGlowing("page_browser"), "page_browser button must glow in ch4 step 1");
    }

    @Test
    public void testSubpageDeduplicationInChapter3() {
        mgr.startChapter(null, "ch3_packaging");
        mgr.nextStep(); // step 1
        mgr.nextStep(); // step 2 (subpage setup)

        int pageCount = bm.getPages().size();
        mgr.previousStep();
        mgr.nextStep();
        mgr.previousStep();
        mgr.nextStep();

        Assertions.assertEquals(pageCount, bm.getPages().size(), "Subpage must be reused without leaking new pages");
    }

    @Test
    public void testTutorialLauncherDialogSafeClicks() {
        com.gtceu.calcboard.client.gui.dialog.TutorialLauncherDialog dialog = new com.gtceu.calcboard.client.gui.dialog.TutorialLauncherDialog();
        dialog.open();
        Assertions.assertTrue(dialog.isVisible());

        // Fast-Track click: modalX + 12 to modalX + modalW - 12, modalY + 30
        boolean clicked = dialog.mouseClicked(250, 190, 0, 800, 600);
        Assertions.assertTrue(clicked);
        Assertions.assertFalse(dialog.isVisible(), "Clicking track card must start track and close launcher");
        Assertions.assertTrue(mgr.isFastTrackActive(), "Fast-track must be active after clicking card");
    }

    @Test
    public void testChapter1GraphSetupValidation() {
        BoardPage page = new BoardPage("Test Ch1 Page");

        TutorialTrackRegistry reg = TutorialTrackRegistry.getInstance();
        var steps = reg.getChapterSteps("ch1_solver");
        Assertions.assertEquals(4, steps.size(), "Chapter 1 must have exactly 4 steps");

        // Step 1: Base Anchor Setup
        steps.get(0).setupAction().accept(page);
        Assertions.assertEquals(2, page.getGraph().getNodes().size());
        Assertions.assertEquals(1, page.getGraph().getConnections().size());

        // Step 2: Auto-Ratio Optimization with Plate Bender Anchor
        steps.get(1).setupAction().accept(page);
        RecipeNode bender = page.getGraph().getNodes().stream()
                .filter(n -> "Plate Bender".equals(n.getName()))
                .findFirst().orElse(null);
        Assertions.assertNotNull(bender);
        Assertions.assertTrue(bender.isBaseNode());

        // Step 3: Harmonized Integer Ratio Setup with Wire Mill and Cable Assembler
        steps.get(2).setupAction().accept(page);
        Assertions.assertEquals(2, page.getGraph().getNodes().size());
        Assertions.assertEquals(1, page.getGraph().getConnections().size());
        RecipeNode wiremill = page.getGraph().getNodes().stream()
                .filter(n -> "Wire Mill".equals(n.getName()))
                .findFirst().orElse(null);
        RecipeNode cable = page.getGraph().getNodes().stream()
                .filter(n -> "Cable Assembler".equals(n.getName()))
                .findFirst().orElse(null);
        Assertions.assertNotNull(wiremill);
        Assertions.assertNotNull(cable);
        Assertions.assertFalse(wiremill.getInputs().isEmpty());
        Assertions.assertFalse(wiremill.getOutputs().isEmpty());
        Assertions.assertFalse(cable.getInputs().isEmpty());
        Assertions.assertFalse(cable.getOutputs().isEmpty());
        Assertions.assertEquals(2.0, wiremill.getMachineCount(), 1e-4);
        Assertions.assertEquals(1.0, cable.getMachineCount(), 1e-4);
        Assertions.assertTrue(cable.isBaseNode());

        page.getGraph().autoRatioFractional(cable);
        Assertions.assertEquals(1.5, wiremill.getMachineCount(), 1e-4, "Alt+Shift+R fractional ratio must scale Wire Mill to 1.5");

        // Step 4: Recirculating Loop Setup (Starts in Unfed Damped state)
        steps.get(3).setupAction().accept(page);
        Assertions.assertEquals(3, page.getGraph().getNodes().size());
        Assertions.assertEquals(2, page.getGraph().getConnections().size(), "Initial setup connects bath <-> centrifuge only");
        RecipeNode feedNode = page.getGraph().getNodes().stream()
                .filter(n -> "Acid Supply Tank".equals(n.getName()))
                .findFirst().orElse(null);
        RecipeNode bath = page.getGraph().getNodes().stream()
                .filter(n -> "Chemical Bath".equals(n.getName()))
                .findFirst().orElse(null);
        RecipeNode centrifugeNode = page.getGraph().getNodes().stream()
                .filter(n -> "Acid Centrifuge".equals(n.getName()))
                .findFirst().orElse(null);
        Assertions.assertNotNull(feedNode);
        Assertions.assertNotNull(bath);
        Assertions.assertNotNull(centrifugeNode);

        page.getGraph().computeNodeEfficiencies();
        var unfedStats = page.getGraph().getInputPortStats(bath, 0);
        Assertions.assertTrue(unfedStats.isUnfedDampedLoop(), "Unfed loop must be detected as damped");
        var badges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(bath);
        Assertions.assertTrue(badges.stream().anyMatch(b -> b.text().contains("Damped") || b.text().contains("damped_loop") || b.text().contains("감쇄")), "Bath must display Damped badge");

        // Player connects external supply line
        page.getGraph().addConnection(feedNode.getId(), 0, bath.getId(), 0);
        page.getGraph().computeNodeEfficiencies();
        var healedStats = page.getGraph().getInputPortStats(bath, 0);
        Assertions.assertFalse(healedStats.isUnfedDampedLoop(), "Connecting external feed must heal unfed damped state");
        Assertions.assertTrue(healedStats.isSteadyStateRecirculating(), "Loop must enter steady-state recirculation");

        // Verify [Steady State] badge appears and scales machines on click
        var steadyBadges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(bath);
        var steadyBadge = steadyBadges.stream().filter(b -> b.text().contains("Steady State") || b.text().contains("steady_state_loop") || b.text().contains("정상 상태")).findFirst().orElse(null);
        Assertions.assertNotNull(steadyBadge, "Bath must display Steady State loop badge when operating on feed");
        Assertions.assertNotNull(steadyBadge.onClick(), "Steady state badge must have onClick action");

        steadyBadge.onClick().run();
        Assertions.assertEquals(0.5, bath.getMachineCount(), 1e-3, "Badge onClick must scale Chemical Bath to 50% capacity");
        Assertions.assertEquals(0.5, centrifugeNode.getMachineCount(), 1e-3, "Badge onClick must scale Acid Centrifuge to 50% capacity");

        // Verify Solver [Alt + Shift + R] automatically optimizes loop to steady state
        bath.setMachineCount(1.0);
        centrifugeNode.setMachineCount(1.0);
        feedNode.setBaseNode(true);
        page.getGraph().autoRatioFractional(feedNode);
        Assertions.assertEquals(0.5, bath.getMachineCount(), 1e-3, "Solver Auto-Ratio must scale loop machines to steady state");
        Assertions.assertEquals(0.5, centrifugeNode.getMachineCount(), 1e-3, "Solver Auto-Ratio must scale loop machines to steady state");
    }

    @Test
    public void testStepResultKeysRegistry() {
        TutorialTrackRegistry reg = TutorialTrackRegistry.getInstance();
        for (var step : reg.getFastTrackSteps()) {
            Assertions.assertNotNull(step.getResultDescription(), "Fast-track step result description must not be null");
            Assertions.assertFalse(step.getResultDescription().getString().isEmpty(), "Fast-track step result description must not be empty");
        }

        for (var ch : reg.getChapters()) {
            for (var step : reg.getChapterSteps(ch.getChapterId())) {
                Assertions.assertNotNull(step.getResultDescription(), "Chapter step result description must not be null for " + ch.getChapterId());
                Assertions.assertFalse(step.getResultDescription().getString().isEmpty(), "Chapter step result description must not be empty for " + ch.getChapterId());
            }
        }
    }

    @Test
    public void testStepResultReviewStateInteractive() {
        java.util.concurrent.atomic.AtomicBoolean rebuilt = new java.util.concurrent.atomic.AtomicBoolean(false);
        com.gtceu.calcboard.client.gui.widget.SummaryOverlay dummyOverlay = new com.gtceu.calcboard.client.gui.widget.SummaryOverlay();
        com.gtceu.calcboard.client.gui.api.IBoardScreenContext screen = (com.gtceu.calcboard.client.gui.api.IBoardScreenContext) java.lang.reflect.Proxy.newProxyInstance(
                com.gtceu.calcboard.client.gui.api.IBoardScreenContext.class.getClassLoader(),
                new Class<?>[]{com.gtceu.calcboard.client.gui.api.IBoardScreenContext.class},
                (proxy, method, args) -> {
                    if ("getSummaryOverlay".equals(method.getName())) return dummyOverlay;
                    if ("rebuildBoardWidgets".equals(method.getName())) {
                        rebuilt.set(true);
                        return null;
                    }
                    if (method.getReturnType().equals(double.class)) return 100.0;
                    if (method.getReturnType().equals(int.class)) return 100;
                    return null;
                }
        );

        mgr.startFastTrack(screen);
        Assertions.assertEquals(0, mgr.getFastTrackStepIndex());
        Assertions.assertFalse(mgr.isStepActionCompleted());
        Assertions.assertNull(mgr.getStepResultDescKey());

        RecipeNode boiler = RecipeNode.create("Test Boiler", 20.0, 0.0, GTVoltageTier.LV);
        mgr.onNodeAdded(boiler);

        // Step action completed: must stay on step 0 and show result explanation
        Assertions.assertEquals(0, mgr.getFastTrackStepIndex(), "Fast-track step 0 must stay on step 0 until user continues");
        Assertions.assertTrue(mgr.isStepActionCompleted(), "Step action must be marked completed");
        Assertions.assertEquals("gui.gtcalcboard.tutorial.fast_track.step1_result", mgr.getStepResultDescKey());

        // Calling nextStep advances to step 1 and clears review state
        mgr.nextStep();
        Assertions.assertEquals(1, mgr.getFastTrackStepIndex(), "Fast-track must advance to step 1");
        Assertions.assertFalse(mgr.isStepActionCompleted(), "Review state must be reset on step advance");
        Assertions.assertNull(mgr.getStepResultDescKey());
    }
}
