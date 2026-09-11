package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CanvasContextMenuManagerTest {

    @Test
    public void testJunctionContextMenuContainsFlipAction() {
        CanvasContextMenuManager menuManager = new CanvasContextMenuManager(null);
        RecipeNode reroute = RecipeNode.createReroute(100.0, 100.0);
        NodeWidget widget = new NodeWidget(reroute);
        menuManager.openForJunctionNode(100, 100, widget);

        var flipItemOpt = menuManager.getItems().stream()
                .filter(item -> "gui.gtcalcboard.menu.flip_node".equals(item.labelKey()) && "F".equals(item.shortcut()))
                .findFirst();
        Assertions.assertTrue(flipItemOpt.isPresent());

        var flipItem = flipItemOpt.get();
        Assertions.assertFalse(reroute.isFlipped());
        flipItem.action().run();
        Assertions.assertTrue(reroute.isFlipped());
        flipItem.action().run();
        Assertions.assertFalse(reroute.isFlipped());
    }

    @Test
    public void testStandardNodeContextMenuContainsFlipAction() {
        CanvasContextMenuManager menuManager = new CanvasContextMenuManager(null);
        RecipeNode node = RecipeNode.create("Centrifuge", 20.0, 32.0, GTVoltageTier.LV);
        NodeWidget widget = new NodeWidget(node);
        menuManager.openForNode(100, 100, widget);

        var flipItemOpt = menuManager.getItems().stream()
                .filter(item -> "gui.gtcalcboard.menu.flip_node".equals(item.labelKey()) && "F".equals(item.shortcut()))
                .findFirst();
        Assertions.assertTrue(flipItemOpt.isPresent());

        var flipItem = flipItemOpt.get();
        Assertions.assertFalse(node.isFlipped());
        flipItem.action().run();
        Assertions.assertTrue(node.isFlipped());
        flipItem.action().run();
        Assertions.assertFalse(node.isFlipped());
    }

    @Test
    public void testFrameContextMenuContainsConfigureAndDeleteActions() {
        CanvasContextMenuManager menuManager = new CanvasContextMenuManager(null);
        com.gtceu.calcboard.api.model.CanvasGroupFrame frame = new com.gtceu.calcboard.api.model.CanvasGroupFrame(
                "f1", "Test Frame", com.gtceu.calcboard.api.model.CanvasGroupFrame.COLOR_CYAN, 0, 0, 200, 200
        );
        menuManager.openForFrame(100, 100, frame);

        var configOpt = menuManager.getItems().stream()
                .filter(item -> "gui.gtcalcboard.menu.configure_frame".equals(item.labelKey()))
                .findFirst();
        Assertions.assertTrue(configOpt.isPresent(), "Configure frame menu item should be present");

        var deleteOpt = menuManager.getItems().stream()
                .filter(item -> "gui.gtcalcboard.menu.delete_frame".equals(item.labelKey()) && item.isDanger())
                .findFirst();
        Assertions.assertTrue(deleteOpt.isPresent(), "Delete frame menu item should be present");
        Assertions.assertEquals("Del", deleteOpt.get().shortcut());
    }
}
