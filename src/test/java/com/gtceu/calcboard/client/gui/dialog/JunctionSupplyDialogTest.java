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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

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
}
