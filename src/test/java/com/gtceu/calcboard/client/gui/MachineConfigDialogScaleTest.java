package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MachineConfigDialogScaleTest {

    @Test
    public void testEffectiveDimensionsWithVirtualScale() {
        // High resolution scaled virtual viewport (1280x720)
        int virtualWidth = 1280;
        int virtualHeight = 720;

        int effW = MachineConfigDialog.getEffectiveDialogWidth(virtualWidth);
        int effH = MachineConfigDialog.getEffectiveDialogHeight(virtualHeight);

        Assertions.assertEquals(680, effW);
        Assertions.assertEquals(480, effH);

        int expectedX = (virtualWidth - effW) / 2;
        int expectedY = (virtualHeight - effH) / 2;

        Assertions.assertEquals(300, expectedX);
        Assertions.assertEquals(120, expectedY);
    }

    @Test
    public void testCloseButtonHitboxAtVirtualCoordinates() {
        BoardScreen boardScreen = new BoardScreen();
        boardScreen.width = 1280;
        boardScreen.height = 720;

        MachineConfigDialog dialog = new MachineConfigDialog(boardScreen);
        RecipeNode node = RecipeNode.create("Steam Dynamo", 100, 30, GTVoltageTier.LV);
        dialog.open(node);

        Assertions.assertTrue(dialog.isVisible());

        int effW = MachineConfigDialog.getEffectiveDialogWidth(1280);
        int effH = MachineConfigDialog.getEffectiveDialogHeight(720);
        int x = (1280 - effW) / 2;
        int y = (720 - effH) / 2;

        int closeBtnX = x + effW - 20; // 300 + 680 - 20 = 960
        int closeBtnY = y + 4;        // 120 + 4 = 124

        // Click close button at virtual coordinates (965, 128)
        boolean handled = dialog.mouseClicked(closeBtnX + 5, closeBtnY + 4, 0, 1280, 720);
        Assertions.assertTrue(handled);
        Assertions.assertFalse(dialog.isVisible());
    }
}
