package com.gtceu.calcboard.client.tutorial;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.tutorial.ContextualNudgeManager;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.tutorial.model.ContextualNudge;
import com.gtceu.calcboard.client.gui.tutorial.model.NudgeTriggerType;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MinecraftBootstrapExtension.class)
public class ContextualNudgeTest {

    private ContextualNudgeManager nudgeMgr;
    private BoardManager bm;

    @BeforeEach
    public void setUp() {
        TutorialManager.getInstance().stopTutorial();
        nudgeMgr = ContextualNudgeManager.getInstance();
        nudgeMgr.resetSeenNudges();
        nudgeMgr.setNudgesEnabled(true);
        bm = BoardManager.getInstance();
        bm.resetToDefault();
    }

    @AfterEach
    public void tearDown() {
        if (nudgeMgr != null) {
            nudgeMgr.resetSeenNudges();
            nudgeMgr.setNudgesEnabled(true);
        }
        if (bm != null) {
            bm.resetToDefault();
        }
    }

    @Test
    public void testRegistryDefaults() {
        Assertions.assertTrue(nudgeMgr.isNudgesEnabled());
        Assertions.assertNull(nudgeMgr.getActiveNudge());
        Assertions.assertFalse(nudgeMgr.hasSeenNudge("nudge_auto_connect"));
        Assertions.assertFalse(nudgeMgr.hasSeenNudge("nudge_wire_reroute"));
        Assertions.assertFalse(nudgeMgr.hasSeenNudge("nudge_loop_damped"));
        Assertions.assertFalse(nudgeMgr.hasSeenNudge("nudge_byproduct_void"));
        Assertions.assertFalse(nudgeMgr.hasSeenNudge("nudge_module_subpage"));
    }

    @Test
    public void testAutoConnectTrigger() {
        BoardPage page = bm.getActivePage();
        RecipeNode n1 = RecipeNode.create("Node 1", 20.0, 32.0, GTVoltageTier.LV);
        RecipeNode n2 = RecipeNode.create("Node 2", 20.0, 32.0, GTVoltageTier.LV);
        page.getGraph().addNode(n1);
        page.getGraph().addNode(n2);

        nudgeMgr.checkTriggers(page);
        ContextualNudge active = nudgeMgr.getActiveNudge();
        Assertions.assertNotNull(active);
        Assertions.assertEquals("nudge_auto_connect", active.nudgeId());
        Assertions.assertEquals(NudgeTriggerType.AUTO_CONNECT, active.triggerType());
        Assertions.assertTrue(nudgeMgr.hasSeenNudge("nudge_auto_connect"));

        // Second check must not duplicate
        nudgeMgr.dismissActiveNudge();
        nudgeMgr.checkTriggers(page);
        Assertions.assertNull(nudgeMgr.getActiveNudge());
    }

    @Test
    public void testWireRerouteTrigger() {
        BoardPage page = bm.getActivePage();
        RecipeNode n1 = RecipeNode.create("Node Left", 20.0, 32.0, GTVoltageTier.LV);
        n1.setPos(0, 0);
        RecipeNode n2 = RecipeNode.create("Node Right", 20.0, 32.0, GTVoltageTier.LV);
        n2.setPos(300, 0); // distance 300 >= 250
        page.getGraph().addNode(n1);
        page.getGraph().addNode(n2);
        page.getGraph().addConnection(n1.getId(), 0, n2.getId(), 0);

        nudgeMgr.checkTriggers(page);
        ContextualNudge active = nudgeMgr.getActiveNudge();
        Assertions.assertNotNull(active);
        Assertions.assertEquals("nudge_wire_reroute", active.nudgeId());
        Assertions.assertEquals(NudgeTriggerType.WIRE_REROUTE, active.triggerType());
    }

    @Test
    public void testLoopDampedTrigger() {
        BoardPage page = bm.getActivePage();
        RecipeNode reactor = RecipeNode.create("Reactor", 20.0, 32.0, GTVoltageTier.LV);
        reactor.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:water"), "Water", 1000.0, 1.0));
        reactor.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:acid"), "Acid", 1000.0, 1.0));

        RecipeNode washer = RecipeNode.create("Washer", 20.0, 32.0, GTVoltageTier.LV);
        washer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:acid"), "Acid", 1000.0, 1.0));
        washer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:water"), "Water", 800.0, 1.0));

        page.getGraph().addNode(reactor);
        page.getGraph().addNode(washer);
        page.getGraph().addConnection(reactor.getId(), 0, washer.getId(), 0);
        page.getGraph().addConnection(washer.getId(), 0, reactor.getId(), 0);

        nudgeMgr.checkTriggers(page);
        ContextualNudge active = nudgeMgr.getActiveNudge();
        Assertions.assertNotNull(active);
        Assertions.assertEquals("nudge_loop_damped", active.nudgeId());
        Assertions.assertEquals(NudgeTriggerType.LOOP_DAMPED, active.triggerType());
    }

    @Test
    public void testByproductVoidTrigger() {
        BoardPage page = bm.getActivePage();
        RecipeNode producer = RecipeNode.create("Producer", 20.0, 32.0, GTVoltageTier.LV);
        producer.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 100.0));
        page.getGraph().addNode(producer);

        RecipeNode consumer = RecipeNode.create("Consumer", 20.0, 32.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 40.0));
        page.getGraph().addNode(consumer);

        page.getGraph().addConnection(producer.getId(), 0, consumer.getId(), 0);

        nudgeMgr.checkTriggers(page);
        ContextualNudge active = nudgeMgr.getActiveNudge();
        Assertions.assertNotNull(active);
        Assertions.assertEquals("nudge_byproduct_void", active.nudgeId());

        // When voided, it should not trigger if reset
        producer.setOutputPortVoided(0, true);
        nudgeMgr.resetSeenNudges();
        nudgeMgr.checkTriggers(page);
        Assertions.assertNull(nudgeMgr.getActiveNudge());
    }

    @Test
    public void testModuleSubpageTrigger() {
        BoardPage page = bm.getActivePage();
        RecipeNode module = RecipeNode.create("Module Card", 20.0, 32.0, GTVoltageTier.LV);
        module.setModule(true);
        module.setSubPageId("sub_page_123");
        page.getGraph().addNode(module);

        nudgeMgr.checkTriggers(page);
        ContextualNudge active = nudgeMgr.getActiveNudge();
        Assertions.assertNotNull(active);
        Assertions.assertEquals("nudge_module_subpage", active.nudgeId());
    }

    @Test
    public void testNudgeToggleAndDismissal() {
        nudgeMgr.setNudgesEnabled(false);
        Assertions.assertFalse(nudgeMgr.isNudgesEnabled());

        boolean triggered = nudgeMgr.triggerNudge("nudge_auto_connect");
        Assertions.assertFalse(triggered, "Disabled manager must refuse trigger");
        Assertions.assertNull(nudgeMgr.getActiveNudge());

        nudgeMgr.setNudgesEnabled(true);
        triggered = nudgeMgr.triggerNudge("nudge_auto_connect");
        Assertions.assertTrue(triggered);
        Assertions.assertNotNull(nudgeMgr.getActiveNudge());

        nudgeMgr.dismissActiveNudge();
        Assertions.assertNull(nudgeMgr.getActiveNudge());
    }

    @Test
    public void testConcurrentTriggersDoNotOverwriteOrBurnUnseen() {
        BoardPage page = bm.getActivePage();
        // Condition 1: 2 nodes without wire (auto-connect)
        RecipeNode n1 = RecipeNode.create("Node 1", 20.0, 32.0, GTVoltageTier.LV);
        RecipeNode n2 = RecipeNode.create("Node 2", 20.0, 32.0, GTVoltageTier.LV);
        page.getGraph().addNode(n1);
        page.getGraph().addNode(n2);

        // Condition 2: Module node (module subpage hint)
        RecipeNode module = RecipeNode.create("Module", 20.0, 32.0, GTVoltageTier.LV);
        module.setModule(true);
        module.setSubPageId("sub_999");
        page.getGraph().addNode(module);

        // First scan: triggers auto_connect
        nudgeMgr.checkTriggers(page);
        ContextualNudge active = nudgeMgr.getActiveNudge();
        Assertions.assertNotNull(active);
        Assertions.assertEquals("nudge_auto_connect", active.nudgeId(), "First eligible nudge must be presented");
        Assertions.assertTrue(nudgeMgr.hasSeenNudge("nudge_auto_connect"));
        Assertions.assertFalse(nudgeMgr.hasSeenNudge("nudge_module_subpage"), "Secondary nudge must NOT be marked seen prematurely");

        // Dismiss first nudge: second scan should now present secondary nudge
        nudgeMgr.dismissActiveNudge();
        nudgeMgr.checkTriggers(page);
        ContextualNudge secondActive = nudgeMgr.getActiveNudge();
        Assertions.assertNotNull(secondActive);
        Assertions.assertEquals("nudge_module_subpage", secondActive.nudgeId(), "Secondary nudge must be presented on subsequent interval");
        Assertions.assertTrue(nudgeMgr.hasSeenNudge("nudge_module_subpage"));
    }

    @Test
    public void testToastClickActions() {
        nudgeMgr.triggerNudge("nudge_auto_connect");
        Assertions.assertNotNull(nudgeMgr.getActiveNudge());

        // Toast at screenW=800, screenH=600:
        // TOAST_WIDTH = 300, TOAST_HEIGHT = 48
        // x = 800 - 300 - 16 = 484, y = 600 - 48 - 16 = 536
        // Close button: mouseX >= 484 + 300 - 20 = 764, mouseY <= 536 + 18 = 554

        // 1. Close button click: dismisses without starting chapter
        boolean handled = com.gtceu.calcboard.client.gui.tutorial.ContextualNudgeToast.mouseClicked(null, 800, 600, 770, 545, 0);
        Assertions.assertTrue(handled);
        Assertions.assertNull(nudgeMgr.getActiveNudge(), "Close click must dismiss toast");
        Assertions.assertFalse(TutorialManager.getInstance().isActive(), "Close click must not start tutorial");

        // 2. Toast body click: dismisses and shortcuts to related chapter
        nudgeMgr.resetSeenNudges();
        nudgeMgr.triggerNudge("nudge_auto_connect");
        Assertions.assertNotNull(nudgeMgr.getActiveNudge());

        handled = com.gtceu.calcboard.client.gui.tutorial.ContextualNudgeToast.mouseClicked(null, 800, 600, 500, 550, 0);
        Assertions.assertTrue(handled);
        Assertions.assertNull(nudgeMgr.getActiveNudge(), "Body click must dismiss toast");
        Assertions.assertTrue(TutorialManager.getInstance().isChapterActive(), "Body click must shortcut to related chapter");
        Assertions.assertEquals("ch2_wiring", TutorialManager.getInstance().getActiveChapterId());
    }
}
