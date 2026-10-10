package com.gtceu.calcboard.client.gui.dialog;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class MachineSelectorDialogLayoutTest {

    @Test
    @DisplayName("Verify deductive coordinate derivations across different screen resolutions")
    void testDeductiveCoordinateDerivations() {
        MachineSelectorDialog dialog = new MachineSelectorDialog(null);

        int[] testWidths = {800, 1024, 1280, 1920};
        int[] testHeights = {600, 768, 720, 1080};

        for (int i = 0; i < testWidths.length; i++) {
            int screenW = testWidths[i];
            int screenH = testHeights[i];

            int dialogX = dialog.getDialogX(screenW);
            int dialogY = dialog.getDialogY(screenH);

            Assertions.assertEquals((screenW - 540) / 2, dialogX);
            Assertions.assertEquals((screenH - 280) / 2, dialogY);

            int closeBtnX = dialog.getCloseButtonX(dialogX);
            int closeBtnY = dialog.getCloseButtonY(dialogY);
            Assertions.assertEquals(dialogX + 540 - 12 - 12, closeBtnX);
            Assertions.assertEquals(dialogY + 8, closeBtnY);

            int searchBoxX = dialog.getSearchBoxX(dialogX);
            int searchBoxY = dialog.getSearchBoxY(dialogY);
            Assertions.assertEquals(closeBtnX - 6 - 140, searchBoxX);
            Assertions.assertEquals(dialogY + 24, searchBoxY);

            int listX = dialog.getListX(dialogX);
            int listY = dialog.getListY(dialogY);
            int listW = dialog.getListWidth();
            int listH = dialog.getListHeight(dialogY);

            Assertions.assertEquals(dialogX + 10, listX);
            Assertions.assertEquals(searchBoxY + 14 + 6, listY);
            Assertions.assertEquals(540 - 20, listW);
            Assertions.assertEquals(280 - (listY - dialogY) - 10, listH);
        }
    }

    private static class TestableEditBox extends EditBox {
        private boolean focusedState = false;

        TestableEditBox(int x, int y, int width, int height) {
            super(null, x, y, width, height, Component.empty());
        }

        @Override
        public boolean isFocused() {
            return focusedState;
        }

        @Override
        public void setFocused(boolean focused) {
            this.focusedState = focused;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            boolean inBounds = mouseX >= getX() && mouseX <= getX() + width
                    && mouseY >= getY() && mouseY <= getY() + height;
            if (inBounds) {
                setFocused(true);
                return true;
            }
            return false;
        }
    }

    @Test
    @DisplayName("Verify search box focus management and focused widget retrieval")
    void testSearchBoxFocusAndFocusedWidget() {
        MachineSelectorDialog dialog = new MachineSelectorDialog(null);
        dialog.setVisibleForTesting(true);

        int screenW = 1024;
        int screenH = 768;
        int dialogX = dialog.getDialogX(screenW);
        int dialogY = dialog.getDialogY(screenH);

        TestableEditBox editBox = new TestableEditBox(
                dialog.getSearchBoxX(dialogX),
                dialog.getSearchBoxY(dialogY),
                140,
                14
        );
        dialog.setSearchBoxForTesting(editBox);

        Assertions.assertNull(dialog.getFocusedWidget());

        double clickInsideX = dialog.getSearchBoxX(dialogX) + 10;
        double clickInsideY = dialog.getSearchBoxY(dialogY) + 5;
        boolean consumed = dialog.mouseClicked(clickInsideX, clickInsideY, 0, screenW, screenH);

        Assertions.assertTrue(consumed);
        Assertions.assertTrue(editBox.isFocused());
        Assertions.assertSame(editBox, dialog.getFocusedWidget());

        double clickOutsideX = dialogX + 20;
        double clickOutsideY = dialogY + 20;
        dialog.mouseClicked(clickOutsideX, clickOutsideY, 0, screenW, screenH);

        Assertions.assertFalse(editBox.isFocused());
        Assertions.assertNull(dialog.getFocusedWidget());
    }
}
