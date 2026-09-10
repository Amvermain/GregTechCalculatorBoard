package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.client.gui.BoardScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

public class FrameEditDialogTest {

    static class TestEditBox extends EditBox {
        private boolean focused;

        public TestEditBox(int x, int y, int width, int height) {
            super(null, x, y, width, height, Component.empty());
        }

        @Override
        public void setFocused(boolean focused) {
            this.focused = focused;
        }

        @Override
        public boolean isFocused() {
            return this.focused;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (mouseX >= getX() && mouseX <= getX() + width && mouseY >= getY() && mouseY <= getY() + height) {
                this.focused = true;
                return true;
            }
            return false;
        }
    }

    @Test
    @DisplayName("FrameEditDialog: switchFocus enforces mutual exclusivity between title and capacity inputs")
    void testSwitchFocusMutualExclusivity() {
        BoardScreen screen = new BoardScreen();
        FrameEditDialog dialog = new FrameEditDialog(screen);

        TestEditBox titleInput = new TestEditBox(100, 100, 200, 20);
        TestEditBox capInput = new TestEditBox(100, 150, 50, 20);

        dialog.setInputsForTest(titleInput, capInput);

        dialog.switchFocus(titleInput);
        Assertions.assertTrue(dialog.isTitleFocused(), "Title input should be focused");
        Assertions.assertFalse(dialog.isTargetCapacityFocused(), "Capacity input should lose focus");

        dialog.switchFocus(capInput);
        Assertions.assertFalse(dialog.isTitleFocused(), "Title input should lose focus");
        Assertions.assertTrue(dialog.isTargetCapacityFocused(), "Capacity input should be focused");
    }

    @Test
    @DisplayName("FrameEditDialog: Tab key toggles focus between title and target capacity inputs")
    void testTabKeyTogglesFocus() {
        BoardScreen screen = new BoardScreen();
        FrameEditDialog dialog = new FrameEditDialog(screen);

        TestEditBox titleInput = new TestEditBox(100, 100, 200, 20);
        TestEditBox capInput = new TestEditBox(100, 150, 50, 20);

        dialog.setInputsForTest(titleInput, capInput);

        // Initially focus titleInput
        dialog.switchFocus(titleInput);
        Assertions.assertTrue(dialog.isTitleFocused());
        Assertions.assertFalse(dialog.isTargetCapacityFocused());

        // Press TAB -> should switch to capInput
        boolean handledTab1 = dialog.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
        Assertions.assertTrue(handledTab1, "TAB key should be handled");
        Assertions.assertFalse(dialog.isTitleFocused(), "Title input should lose focus after TAB");
        Assertions.assertTrue(dialog.isTargetCapacityFocused(), "Capacity input should be focused after TAB");

        // Press TAB again -> should switch back to titleInput
        boolean handledTab2 = dialog.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
        Assertions.assertTrue(handledTab2, "TAB key should be handled");
        Assertions.assertTrue(dialog.isTitleFocused(), "Title input should be focused after second TAB");
        Assertions.assertFalse(dialog.isTargetCapacityFocused(), "Capacity input should lose focus after second TAB");
    }

    @Test
    @DisplayName("FrameEditDialog: Mouse click switches focus to clicked input exclusively")
    void testMouseClickSwitchesFocusExclusively() {
        BoardScreen screen = new BoardScreen();
        screen.width = 800;
        screen.height = 600;

        CanvasGroupFrame frame = new CanvasGroupFrame("f1", "Shared Test", 0xFF22C55E, 0, 0, 200, 200);
        frame.setSharedMachineFrame(true);

        FrameEditDialog dialog = new FrameEditDialog(screen);
        dialog.open(frame);

        int dialogW = 300;
        int dialogH = 186;
        int x = (screen.width - dialogW) / 2;
        int y = (screen.height - dialogH) / 2;

        TestEditBox titleInput = new TestEditBox(x + 16, y + 42, dialogW - 32, 18);
        int capInputX = Math.max(x + 16 + 90 + 6, x + 120);
        TestEditBox capInput = new TestEditBox(capInputX, y + 125, 42, 16);

        dialog.setInputsForTest(titleInput, capInput);
        dialog.switchFocus(titleInput);

        Assertions.assertTrue(dialog.isTitleFocused());
        Assertions.assertFalse(dialog.isTargetCapacityFocused());

        // Click inside targetCapacityInput
        boolean capClicked = dialog.mouseClicked(capInputX + 5, y + 125 + 5, 0);
        Assertions.assertTrue(capClicked, "Clicking capacity input should be handled");
        Assertions.assertTrue(dialog.isTargetCapacityFocused(), "Capacity input should be focused on click");
        Assertions.assertFalse(dialog.isTitleFocused(), "Title input must lose focus when capacity is clicked");

        // Click inside titleInput
        boolean titleClicked = dialog.mouseClicked(x + 20, y + 45, 0);
        Assertions.assertTrue(titleClicked, "Clicking title input should be handled");
        Assertions.assertTrue(dialog.isTitleFocused(), "Title input should be focused on click");
        Assertions.assertFalse(dialog.isTargetCapacityFocused(), "Capacity input must lose focus when title is clicked");
    }
}
