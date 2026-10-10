package com.gtceu.calcboard.client.gui.widget;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

public class SelectionFloatingToolbarTest {

    @Test
    public void testInitializationHeadlessSafety() {
        SelectionFloatingToolbarWidget widget = new SelectionFloatingToolbarWidget(null);
        Assertions.assertNotNull(widget);
        Assertions.assertFalse(widget.isVisible());
        Assertions.assertEquals(SelectionFloatingToolbarWidget.BAR_HEIGHT, widget.getBarHeight());
    }

    @Test
    public void testMouseClickedOutsideWhenNotVisible() {
        SelectionFloatingToolbarWidget widget = new SelectionFloatingToolbarWidget(null);
        boolean handled = widget.mouseClicked(100, 100, 0);
        Assertions.assertFalse(handled);
    }

    @Test
    public void testActionTriggerSafety() {
        AtomicBoolean ran = new AtomicBoolean(false);
        SelectionFloatingToolbarWidget.ToolbarAction action = new SelectionFloatingToolbarWidget.ToolbarAction(
                "▤",
                net.minecraft.network.chat.Component.literal("Frame"),
                net.minecraft.network.chat.Component.literal("Tooltip"),
                () -> ran.set(true),
                false
        );

        Assertions.assertEquals("▤", action.icon());
        Assertions.assertFalse(action.isDanger());
        action.action().run();
        Assertions.assertTrue(ran.get());
        Assertions.assertTrue(action.available().getAsBoolean());
    }

    @Test
    public void testConnectActionAvailableOnlyWhenAtLeastTwoNodesSelected() {
        com.gtceu.calcboard.client.gui.BoardScreen screen = new com.gtceu.calcboard.client.gui.BoardScreen();
        SelectionFloatingToolbarWidget widget = new SelectionFloatingToolbarWidget(screen);

        SelectionFloatingToolbarWidget.ToolbarAction connectAction = widget.getActions().stream()
                .filter(a -> "↔".equals(a.icon()))
                .findFirst()
                .orElseThrow();

        screen.getSelectedNodeIds().clear();
        screen.getSelectedFrameIds().clear();
        screen.getSelectedNodeIds().add("n1");
        Assertions.assertFalse(connectAction.available().getAsBoolean());

        screen.getSelectedNodeIds().add("n2");
        Assertions.assertTrue(connectAction.available().getAsBoolean());

        screen.getSelectedNodeIds().clear();
        screen.getSelectedNodeIds().add("n1");
        com.gtceu.calcboard.api.model.CanvasGroupFrame frame = new com.gtceu.calcboard.api.model.CanvasGroupFrame(
                "frame_1", "Shared Pool", 0, 0, 200, 200, 0xFF222222
        );
        com.gtceu.calcboard.api.model.RecipeNode frameNode = new com.gtceu.calcboard.api.model.RecipeNode(
                "fn1", "Frame Machine", 10, 10, com.gtceu.calcboard.api.type.GTVoltageTier.LV
        );
        screen.getGraph().addNode(frameNode);
        frame.getContainedNodeIds().add("fn1");
        screen.getGraph().addFrame(frame);
        screen.getSelectedFrameIds().add("frame_1");

        Assertions.assertTrue(connectAction.available().getAsBoolean());
    }

    @Test
    public void testButtonClickedHeadlessSoundSafety() throws Exception {
        com.gtceu.calcboard.client.gui.BoardScreen screen = new com.gtceu.calcboard.client.gui.BoardScreen();
        SelectionFloatingToolbarWidget widget = new SelectionFloatingToolbarWidget(screen);

        java.lang.reflect.Method playClick = SelectionFloatingToolbarWidget.class.getDeclaredMethod("playClickSound");
        playClick.setAccessible(true);
        Assertions.assertDoesNotThrow(() -> playClick.invoke(widget));
    }
}
