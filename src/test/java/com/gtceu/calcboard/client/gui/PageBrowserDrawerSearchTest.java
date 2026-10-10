package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.widget.PageBrowserDrawer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Proxy;

public class PageBrowserDrawerSearchTest {

    private IBoardScreenContext mockScreen;

    @BeforeEach
    public void setup() {
        BoardManager.getInstance().getPageManager().resetToDefault();
        mockScreen = (IBoardScreenContext) Proxy.newProxyInstance(
                IBoardScreenContext.class.getClassLoader(),
                new Class<?>[]{IBoardScreenContext.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getHeaderBottomY".equals(name)) return 44;
                    if ("getScreenHeight".equals(name)) return 600;
                    if ("getScreenWidth".equals(name)) return 800;
                    return null;
                }
        );
    }

    @Test
    public void testSearchBoxClickGainsFocusAndConsumesInput() {
        PageBrowserDrawer drawer = new PageBrowserDrawer(mockScreen);
        drawer.setOpen(true);

        Assertions.assertNotNull(drawer.getSearchBox(), "Search box must be initialized");
        Assertions.assertFalse(drawer.getSearchBox().isFocused(), "Search box should initially be unfocused");
        Assertions.assertNull(drawer.getFocusedEditBox(), "Focused edit box should be null initially");

        int clickX = PageBrowserDrawer.DRAWER_X + 20;
        int clickY = 44 + 2 + 24 + 5;

        boolean handled = drawer.mouseClicked(clickX, clickY, 0);
        Assertions.assertTrue(handled, "Click within search box must be handled");
        Assertions.assertTrue(drawer.getSearchBox().isFocused(), "Search box must be focused after click");
        Assertions.assertEquals(drawer.getSearchBox(), drawer.getFocusedEditBox(), "Search box must be the focused edit box");

        int outsideX = PageBrowserDrawer.DRAWER_X + 20;
        int outsideY = 44 + 2 + 44 + 10;
        drawer.mouseClicked(outsideX, outsideY, 0);
        Assertions.assertFalse(drawer.getSearchBox().isFocused(), "Clicking outside search box must unfocus it");
        Assertions.assertNull(drawer.getFocusedEditBox());
    }

    @Test
    public void testSearchBoxRightClickClearsText() {
        PageBrowserDrawer drawer = new PageBrowserDrawer(mockScreen);
        drawer.setOpen(true);

        drawer.getSearchBox().setValue("filter query");
        Assertions.assertEquals("filter query", drawer.getSearchBox().getValue());

        int clickX = PageBrowserDrawer.DRAWER_X + 20;
        int clickY = 44 + 2 + 24 + 5;

        boolean handled = drawer.mouseClicked(clickX, clickY, 1);
        Assertions.assertTrue(handled);
        Assertions.assertTrue(drawer.getSearchBox().isFocused());
        Assertions.assertTrue(drawer.getSearchBox().getValue().isEmpty(), "Right click on search box should clear text");
    }

    @Test
    public void testEscapeKeyUnfocusesSearchBoxBeforeClosingDrawer() {
        PageBrowserDrawer drawer = new PageBrowserDrawer(mockScreen);
        drawer.setOpen(true);

        int clickX = PageBrowserDrawer.DRAWER_X + 20;
        int clickY = 44 + 2 + 24 + 5;
        drawer.mouseClicked(clickX, clickY, 0);
        Assertions.assertTrue(drawer.getSearchBox().isFocused());

        boolean handledEsc1 = drawer.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        Assertions.assertTrue(handledEsc1);
        Assertions.assertFalse(drawer.getSearchBox().isFocused(), "First Escape should unfocus search box");
        Assertions.assertTrue(drawer.isOpen(), "Drawer should remain open on first Escape");

        boolean handledEsc2 = drawer.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        Assertions.assertTrue(handledEsc2);
        Assertions.assertFalse(drawer.isOpen(), "Second Escape should close drawer");
    }

    @Test
    public void testEnterKeyUnfocusesSearchBox() {
        PageBrowserDrawer drawer = new PageBrowserDrawer(mockScreen);
        drawer.setOpen(true);

        int clickX = PageBrowserDrawer.DRAWER_X + 20;
        int clickY = 44 + 2 + 24 + 5;
        drawer.mouseClicked(clickX, clickY, 0);
        Assertions.assertTrue(drawer.getSearchBox().isFocused());

        boolean handledEnter = drawer.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        Assertions.assertTrue(handledEnter);
        Assertions.assertFalse(drawer.getSearchBox().isFocused(), "Enter should unfocus search box");
        Assertions.assertTrue(drawer.isOpen(), "Drawer should stay open after Enter");
    }

    @Test
    public void testTickDoesNotThrowWhenOpen() {
        PageBrowserDrawer drawer = new PageBrowserDrawer(mockScreen);
        drawer.setOpen(true);
        Assertions.assertDoesNotThrow(drawer::tick);
    }
}
