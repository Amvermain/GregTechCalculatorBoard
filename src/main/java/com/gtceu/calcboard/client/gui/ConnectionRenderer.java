package com.gtceu.calcboard.client.gui;

import net.minecraft.client.gui.GuiGraphics;

public class ConnectionRenderer {

    public static void renderBezier(GuiGraphics graphics, float x1, float y1, float x2, float y2, int color, float thickness) {
        float dx = Math.abs(x2 - x1) * 0.5f;
        dx = Math.max(dx, 40f);

        float cx1 = x1 + dx;
        float cy1 = y1;
        float cx2 = x2 - dx;
        float cy2 = y2;

        // Approximate arc length
        float chord = (float) Math.hypot(x2 - x1, y2 - y1);
        float net = (float) (Math.hypot(cx1 - x1, cy1 - y1) + Math.hypot(cx2 - cx1, cy2 - cy1) + Math.hypot(x2 - cx2, y2 - cy2));
        float arcLen = (chord + net) * 0.5f;

        // Sample points along the smooth bezier curve
        int numPoints = Math.max(16, (int) (arcLen / 4.0f));
        float[] px = new float[numPoints + 1];
        float[] py = new float[numPoints + 1];

        for (int i = 0; i <= numPoints; i++) {
            float t = (float) i / numPoints;
            float it = 1.0f - t;
            px[i] = it * it * it * x1 + 3 * it * it * t * cx1 + 3 * it * t * t * cx2 + t * t * t * x2;
            py[i] = it * it * it * y1 + 3 * it * it * t * cy1 + 3 * it * t * t * cy2 + t * t * t * y2;
        }

        int halfThick = Math.max(1, Math.round(thickness * 0.5f));

        // Draw solid continuous line strip by interpolating 1-pixel steps between points (zero gaps)
        int lastIx = Integer.MIN_VALUE;
        int lastIy = Integer.MIN_VALUE;

        for (int i = 0; i < numPoints; i++) {
            float segDx = px[i + 1] - px[i];
            float segDy = py[i + 1] - py[i];
            float dist = (float) Math.hypot(segDx, segDy);
            int steps = Math.max(1, (int) Math.ceil(dist));

            float stepX = segDx / steps;
            float stepY = segDy / steps;

            float curX = px[i];
            float curY = py[i];

            for (int s = 0; s <= steps; s++) {
                int ix = Math.round(curX);
                int iy = Math.round(curY);

                if (ix != lastIx || iy != lastIy) {
                    graphics.fill(ix - halfThick, iy - halfThick, ix + halfThick + 1, iy + halfThick + 1, color);
                    lastIx = ix;
                    lastIy = iy;
                }

                curX += stepX;
                curY += stepY;
            }
        }

        // Draw animated flow pulse dot traveling along the line
        float pulseTime = (System.currentTimeMillis() % 1600L) / 1600.0f;
        float it = 1.0f - pulseTime;
        float dotX = it * it * it * x1 + 3 * it * it * pulseTime * cx1 + 3 * it * pulseTime * pulseTime * cx2 + pulseTime * pulseTime * pulseTime * x2;
        float dotY = it * it * it * y1 + 3 * it * it * pulseTime * cy1 + 3 * it * pulseTime * pulseTime * cy2 + pulseTime * pulseTime * pulseTime * y2;

        int ix = Math.round(dotX);
        int iy = Math.round(dotY);

        graphics.fill(ix - 3, iy - 3, ix + 4, iy + 4, 0x88FFFFFF);
        graphics.fill(ix - 2, iy - 2, ix + 3, iy + 3, 0xFFFFFFFF);
    }

    public static boolean isPointNearBezier(float x1, float y1, float x2, float y2, double px, double py, double maxDist) {
        float dx = Math.abs(x2 - x1) * 0.5f;
        dx = Math.max(dx, 40f);
        float cx1 = x1 + dx;
        float cy1 = y1;
        float cx2 = x2 - dx;
        float cy2 = y2;

        int steps = 24;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float it = 1.0f - t;
            float bx = it * it * it * x1 + 3 * it * it * t * cx1 + 3 * it * t * t * cx2 + t * t * t * x2;
            float by = it * it * it * y1 + 3 * it * it * t * cy1 + 3 * it * t * t * cy2 + t * t * t * y2;

            if (Math.hypot(bx - px, by - py) <= maxDist) {
                return true;
            }
        }
        return false;
    }
}
