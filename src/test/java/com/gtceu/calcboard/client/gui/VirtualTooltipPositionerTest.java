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

    @Test
    public void testFormatPortRateShiftToggle() {
        boolean[] hiddenRefNoShift = new boolean[]{false};
        String noShift = BoardTooltipRenderer.formatPortRate(3_670_016.0, true, false, hiddenRefNoShift);
        Assertions.assertTrue(hiddenRefNoShift[0], "hiddenRef must be true when exact rate is minimized");
        Assertions.assertFalse(noShift.contains("§8("), "Compact rate without shift must not contain exact parentheses");

        boolean[] hiddenRefShift = new boolean[]{false};
        String withShift = BoardTooltipRenderer.formatPortRate(3_670_016.0, true, true, hiddenRefShift);
        String expectedExact = com.gtceu.calcboard.client.gui.util.FormatUtil.formatExactRate(3_670_016.0, true);
        Assertions.assertTrue(withShift.contains("§8(" + expectedExact + ")"), "Detailed rate with shift must contain exact formatted rate");

        boolean[] hiddenRefSimple = new boolean[]{false};
        String simpleNoShift = BoardTooltipRenderer.formatPortRate(5.0, false, false, hiddenRefSimple);
        Assertions.assertFalse(hiddenRefSimple[0], "hiddenRef must remain false when compact equals exact");
        Assertions.assertFalse(simpleNoShift.contains("§8("));

        String simpleShift = BoardTooltipRenderer.formatPortRate(5.0, false, true, hiddenRefSimple);
        Assertions.assertEquals(simpleNoShift, simpleShift, "When compact equals exact, shift must not add redundant parentheses");
    }
}
