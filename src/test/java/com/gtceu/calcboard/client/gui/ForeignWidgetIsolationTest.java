package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.client.event.ClientForgeEvents;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

public class ForeignWidgetIsolationTest {

    private static class TestBoardScreen extends BoardScreen {
        public <T extends net.minecraft.client.gui.components.events.GuiEventListener & net.minecraft.client.gui.components.Renderable & net.minecraft.client.gui.narration.NarratableEntry> void injectWidget(T widget) {
            this.addRenderableWidget(widget);
        }
    }

    @Test
    public void testClearForeignWidgetsRemovesInjectedWidgets() {
        TestBoardScreen boardScreen = new TestBoardScreen();

        Button foreignButton = Button.builder(Component.literal("Foreign IPN Button"), b -> {}).bounds(10, 10, 80, 20).build();
        boardScreen.injectWidget(foreignButton);

        Assertions.assertFalse(boardScreen.children().isEmpty());

        boardScreen.clearForeignWidgets();

        Assertions.assertTrue(boardScreen.children().isEmpty());
    }

    @Test
    public void testScreenInitPostListenerPriorityIsLowest() throws NoSuchMethodException {
        Method method = ClientForgeEvents.class.getMethod("onScreenInitPost", ScreenEvent.Init.Post.class);
        Assertions.assertNotNull(method);

        SubscribeEvent annotation = method.getAnnotation(SubscribeEvent.class);
        Assertions.assertNotNull(annotation);
        Assertions.assertEquals(EventPriority.LOWEST, annotation.priority());
    }

    @Test
    public void testIpnIntegrationHintStructure() {
        String json = com.gtceu.calcboard.client.gui.compat.InventoryProfilesNextCompat.HINT_JSON;
        Assertions.assertTrue(json.contains("com.gtceu.calcboard.client.gui.BoardScreen"));
        Assertions.assertTrue(json.contains("\"ignore\": true"));
        Assertions.assertDoesNotThrow(com.gtceu.calcboard.client.gui.compat.InventoryProfilesNextCompat::ensureIntegrationHintInstalled);
    }
}
