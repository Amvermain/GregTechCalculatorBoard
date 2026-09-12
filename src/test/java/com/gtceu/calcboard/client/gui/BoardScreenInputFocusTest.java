package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.client.gui.dialog.modal.IBoardModal;
import com.gtceu.calcboard.client.gui.dialog.modal.ModalRenderContext;
import com.gtceu.calcboard.client.gui.search.RecipeSearchCacheManager;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BoardScreenInputFocusTest {

    private static class DummyFocusWidget implements GuiEventListener {
        private boolean focused = true;

        @Override
        public void setFocused(boolean focused) {
            this.focused = focused;
        }

        @Override
        public boolean isFocused() {
            return focused;
        }
    }

    private static class DummyModalWithWidget implements IBoardModal {
        private boolean visible = true;
        private final GuiEventListener widget;

        public DummyModalWithWidget(GuiEventListener widget) {
            this.widget = widget;
        }

        @Override
        public boolean isVisible() {
            return visible;
        }

        @Override
        public void close() {
            this.visible = false;
        }

        @Override
        public GuiEventListener getFocusedWidget() {
            return (widget != null && widget.isFocused()) ? widget : null;
        }

        @Override
        public void renderModal(ModalRenderContext context) {}
    }

    private static boolean simulateEmiHasFocusedTextField(ContainerEventHandler screen, int depth) {
        if (depth <= 0) return false;
        for (GuiEventListener child : screen.children()) {
            if (child != null && child.isFocused()) {
                return true;
            }
            if (child instanceof ContainerEventHandler ch && simulateEmiHasFocusedTextField(ch, depth - 1)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testActiveFocusedWidgetExposedToScreenChildrenAndFocused() {
        BoardScreen screen = new BoardScreen();

        Assertions.assertNull(screen.getFocused());
        Assertions.assertTrue(screen.children().isEmpty());
        Assertions.assertFalse(simulateEmiHasFocusedTextField(screen, 10));

        DummyFocusWidget focusWidget = new DummyFocusWidget();
        DummyModalWithWidget modal = new DummyModalWithWidget(focusWidget);
        screen.getDialogManager().getModalStack().push(modal);

        Assertions.assertSame(focusWidget, screen.getFocused());
        Assertions.assertTrue(screen.children().contains(focusWidget));
        Assertions.assertTrue(simulateEmiHasFocusedTextField(screen, 10));

        screen.clearForeignWidgets();

        Assertions.assertSame(focusWidget, screen.getFocused());
        Assertions.assertTrue(screen.children().contains(focusWidget));

        focusWidget.setFocused(false);
        Assertions.assertNull(screen.getFocused());
        Assertions.assertFalse(screen.children().contains(focusWidget));
        Assertions.assertFalse(simulateEmiHasFocusedTextField(screen, 10));
    }

    @Test
    public void testCacheManagerBakingGuardPreventsLoop() {
        RecipeSearchCacheManager.clearGlobalCache();
        Assertions.assertFalse(RecipeSearchCacheManager.isGlobalCached());
        Assertions.assertFalse(RecipeSearchCacheManager.isCaching());

        int[] runCount = new int[]{0};
        Runnable cb = () -> runCount[0]++;

        RecipeSearchCacheManager.ensureGlobalRecipesCachedAsync(cb);
        Assertions.assertTrue(RecipeSearchCacheManager.isCaching());

        RecipeSearchCacheManager.ensureGlobalRecipesCachedAsync(cb);
        Assertions.assertTrue(RecipeSearchCacheManager.isCaching());

        RecipeSearchCacheManager.clearGlobalCache();
        Assertions.assertFalse(RecipeSearchCacheManager.isCaching());
        Assertions.assertFalse(RecipeSearchCacheManager.isGlobalCached());
    }
}
