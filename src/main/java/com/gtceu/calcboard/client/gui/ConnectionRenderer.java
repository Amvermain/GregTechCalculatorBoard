package com.gtceu.calcboard.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public class ConnectionRenderer {

    private static BufferBuilder ACTIVE_BATCH_BUFFER = null;
    private static Matrix4f ACTIVE_BATCH_POSE = null;

    public static void beginBatch(GuiGraphics graphics) {
        ACTIVE_BATCH_POSE = graphics.pose().last().pose();
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        ACTIVE_BATCH_BUFFER = tesselator.getBuilder();
        ACTIVE_BATCH_BUFFER.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    public static void addBezierToBatch(float x1, float y1, float x2, float y2, int color, float thickness) {
        if (ACTIVE_BATCH_BUFFER == null || ACTIVE_BATCH_POSE == null) return;

        float dx = Math.abs(x2 - x1) * 0.5f;
        dx = Math.max(dx, 40f);

        float cx1 = x1 + dx;
        float cy1 = y1;
        float cx2 = x2 - dx;
        float cy2 = y2;

        float chord = (float) Math.hypot(x2 - x1, y2 - y1);
        float net = (float) (Math.hypot(cx1 - x1, cy1 - y1) + Math.hypot(cx2 - cx1, cy2 - cy1) + Math.hypot(x2 - cx2, y2 - cy2));
        float arcLen = (chord + net) * 0.5f;

        int numPoints = Math.max(16, Math.min(32, (int) (arcLen / 16.0f)));
        float[] px = new float[numPoints + 1];
        float[] py = new float[numPoints + 1];

        for (int i = 0; i <= numPoints; i++) {
            float t = (float) i / numPoints;
            float it = 1.0f - t;
            px[i] = it * it * it * x1 + 3 * it * it * t * cx1 + 3 * it * t * t * cx2 + t * t * t * x2;
            py[i] = it * it * it * y1 + 3 * it * it * t * cy1 + 3 * it * t * t * cy2 + t * t * t * y2;
        }

        float halfThick = thickness * 0.5f;
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        for (int i = 0; i < numPoints; i++) {
            float segDx = px[i + 1] - px[i];
            float segDy = py[i + 1] - py[i];
            float len = (float) Math.hypot(segDx, segDy);
            if (len < 0.001f) len = 1.0f;
            float nx = -segDy / len * halfThick;
            float ny = segDx / len * halfThick;

            ACTIVE_BATCH_BUFFER.vertex(ACTIVE_BATCH_POSE, px[i] + nx, py[i] + ny, 0.0f).color(r, g, b, a).endVertex();
            ACTIVE_BATCH_BUFFER.vertex(ACTIVE_BATCH_POSE, px[i + 1] + nx, py[i + 1] + ny, 0.0f).color(r, g, b, a).endVertex();
            ACTIVE_BATCH_BUFFER.vertex(ACTIVE_BATCH_POSE, px[i + 1] - nx, py[i + 1] - ny, 0.0f).color(r, g, b, a).endVertex();
            ACTIVE_BATCH_BUFFER.vertex(ACTIVE_BATCH_POSE, px[i] - nx, py[i] - ny, 0.0f).color(r, g, b, a).endVertex();
        }
    }

    public static void endBatch() {
        if (ACTIVE_BATCH_BUFFER != null) {
            Tesselator.getInstance().end();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            ACTIVE_BATCH_BUFFER = null;
            ACTIVE_BATCH_POSE = null;
        }
    }

    public static void renderBezier(GuiGraphics graphics, float x1, float y1, float x2, float y2, int color, float thickness) {
        beginBatch(graphics);
        addBezierToBatch(x1, y1, x2, y2, color, thickness);
        endBatch();
    }

    public static void renderPulseDotsBatch(GuiGraphics graphics, java.util.List<float[]> wires) {
        if (wires == null || wires.isEmpty()) return;

        Matrix4f pose = graphics.pose().last().pose();
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float pulseTime = (System.currentTimeMillis() % 1600L) / 1600.0f;
        float it = 1.0f - pulseTime;

        int count = Math.min(wires.size(), 80);
        for (int w = 0; w < count; w++) {
            float[] wire = wires.get(w);
            float x1 = wire[0], y1 = wire[1], x2 = wire[2], y2 = wire[3];
            float dx = Math.max(Math.abs(x2 - x1) * 0.5f, 40f);
            float cx1 = x1 + dx;
            float cy1 = y1;
            float cx2 = x2 - dx;
            float cy2 = y2;

            float dotX = it * it * it * x1 + 3 * it * it * pulseTime * cx1 + 3 * it * pulseTime * pulseTime * cx2 + pulseTime * pulseTime * pulseTime * x2;
            float dotY = it * it * it * y1 + 3 * it * it * pulseTime * cy1 + 3 * it * pulseTime * pulseTime * cy2 + pulseTime * pulseTime * pulseTime * y2;

            // Outer glow quad (0x88FFFFFF)
            buffer.vertex(pose, dotX - 3.5f, dotY - 3.5f, 0.0f).color(1.0f, 1.0f, 1.0f, 0.53f).endVertex();
            buffer.vertex(pose, dotX + 3.5f, dotY - 3.5f, 0.0f).color(1.0f, 1.0f, 1.0f, 0.53f).endVertex();
            buffer.vertex(pose, dotX + 3.5f, dotY + 3.5f, 0.0f).color(1.0f, 1.0f, 1.0f, 0.53f).endVertex();
            buffer.vertex(pose, dotX - 3.5f, dotY + 3.5f, 0.0f).color(1.0f, 1.0f, 1.0f, 0.53f).endVertex();

            // Inner core quad (0xFFFFFFFF)
            buffer.vertex(pose, dotX - 2.0f, dotY - 2.0f, 0.0f).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
            buffer.vertex(pose, dotX + 2.0f, dotY - 2.0f, 0.0f).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
            buffer.vertex(pose, dotX + 2.0f, dotY + 2.0f, 0.0f).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
            buffer.vertex(pose, dotX - 2.0f, dotY + 2.0f, 0.0f).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
        }

        tesselator.end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static boolean isPointNearBezier(float x1, float y1, float x2, float y2, double px, double py, double maxDist) {
        float minX = Math.min(x1, x2) - (float) maxDist - 40f;
        float maxX = Math.max(x1, x2) + (float) maxDist + 40f;
        float minY = Math.min(y1, y2) - (float) maxDist - 20f;
        float maxY = Math.max(y1, y2) + (float) maxDist + 20f;

        if (px < minX || px > maxX || py < minY || py > maxY) {
            return false;
        }

        float dx = Math.abs(x2 - x1) * 0.5f;
        dx = Math.max(dx, 40f);
        float cx1 = x1 + dx;
        float cy1 = y1;
        float cx2 = x2 - dx;
        float cy2 = y2;

        float chord = (float) Math.hypot(x2 - x1, y2 - y1);
        float net = (float) (Math.hypot(cx1 - x1, cy1 - y1) + Math.hypot(cx2 - cx1, cy2 - cy1) + Math.hypot(x2 - cx2, y2 - cy2));
        float arcLen = (chord + net) * 0.5f;
        int steps = Math.max(20, Math.min(64, (int) (arcLen / 10.0f)));

        double maxDistSq = maxDist * maxDist;
        float prevX = x1;
        float prevY = y1;

        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            float it = 1.0f - t;
            float bx = it * it * it * x1 + 3 * it * it * t * cx1 + 3 * it * t * t * cx2 + t * t * t * x2;
            float by = it * it * it * y1 + 3 * it * it * t * cy1 + 3 * it * t * t * cy2 + t * t * t * y2;

            if (distanceToSegmentSquared(px, py, prevX, prevY, bx, by) <= maxDistSq) {
                return true;
            }
            prevX = bx;
            prevY = by;
        }
        return false;
    }

    private static double distanceToSegmentSquared(double px, double py, double x1, double y1, double x2, double y2) {
        double segDx = x2 - x1;
        double segDy = y2 - y1;
        double l2 = segDx * segDx + segDy * segDy;
        if (l2 == 0.0) {
            double dx = px - x1;
            double dy = py - y1;
            return dx * dx + dy * dy;
        }
        double t = ((px - x1) * segDx + (py - y1) * segDy) / l2;
        t = Math.max(0.0, Math.min(1.0, t));
        double projX = x1 + t * segDx;
        double projY = y1 + t * segDy;
        double dx = px - projX;
        double dy = py - projY;
        return dx * dx + dy * dy;
    }
}
