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
    }
}
