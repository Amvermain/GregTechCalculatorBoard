package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import org.joml.Vector2ic;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VirtualTooltipPositionerTest {

    @Test
    public void testTooltipPositionsInsideVirtualBounds() {
        int virtualW = 1280;
        int virtualH = 720;
        BoardTooltipRenderer.VirtualTooltipPositioner positioner = new BoardTooltipRenderer.VirtualTooltipPositioner(virtualW, virtualH);

        int mouseX = 640;
        int mouseY = 600;
        int tooltipW = 200;
        int tooltipH = 100;

        int ignoredVanillaGuiW = 854;
        int ignoredVanillaGuiH = 480;

        Vector2ic pos = positioner.positionTooltip(ignoredVanillaGuiW, ignoredVanillaGuiH, mouseX, mouseY, tooltipW, tooltipH);

        Assertions.assertEquals(mouseX + 12, pos.x());
        Assertions.assertTrue(pos.y() > ignoredVanillaGuiH, "Tooltip Y must exceed vanilla guiHeight when mouse is at the bottom of the virtual viewport: " + pos.y());
        Assertions.assertTrue(pos.y() + tooltipH + 3 <= virtualH, "Tooltip must remain within virtual screen height: " + (pos.y() + tooltipH + 3));
    }

    @Test
    public void testTooltipClampingAtRightAndBottomEdges() {
        int virtualW = 1280;
        int virtualH = 720;
        BoardTooltipRenderer.VirtualTooltipPositioner positioner = new BoardTooltipRenderer.VirtualTooltipPositioner(virtualW, virtualH);

        int mouseX = 1250;
        int mouseY = 710;
        int tooltipW = 200;
        int tooltipH = 100;

        Vector2ic pos = positioner.positionTooltip(854, 480, mouseX, mouseY, tooltipW, tooltipH);

        Assertions.assertTrue(pos.x() + tooltipW <= virtualW, "Tooltip right edge must be <= virtualW");
        Assertions.assertTrue(pos.x() >= 4, "Tooltip left edge must be >= 4");
        Assertions.assertEquals(virtualH - tooltipH - 3, pos.y(), "Tooltip bottom edge must be clamped to virtualH");
    }
}
