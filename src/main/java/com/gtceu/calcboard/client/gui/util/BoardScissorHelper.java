package com.gtceu.calcboard.client.gui.util;

import com.gtceu.calcboard.client.gui.BoardScreen;
import net.minecraft.client.gui.GuiGraphics;

public final class BoardScissorHelper {

    private BoardScissorHelper() {}

    public static void enableScissor(GuiGraphics graphics, int minX, int minY, int maxX, int maxY) {
        if (graphics == null) return;

        BoardViewportTransform transform = BoardScreen.getCurrentTransform();
        if (transform != null && transform.isScaled()) {
            int gx1 = (int) Math.floor(transform.toGameX(minX));
            int gy1 = (int) Math.floor(transform.toGameY(minY));
            int gx2 = (int) Math.ceil(transform.toGameX(maxX));
            int gy2 = (int) Math.ceil(transform.toGameY(maxY));

            int clampedX2 = Math.max(gx1, gx2);
            int clampedY2 = Math.max(gy1, gy2);

            graphics.enableScissor(gx1, gy1, clampedX2, clampedY2);
            return;
        }

        int clampedMaxX = Math.max(minX, maxX);
        int clampedMaxY = Math.max(minY, maxY);
        graphics.enableScissor(minX, minY, clampedMaxX, clampedMaxY);
    }

    public static void disableScissor(GuiGraphics graphics) {
        if (graphics == null) return;
        graphics.disableScissor();
    }
}
