package com.gtceu.calcboard.client.gui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
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

        ACTIVE_BATCH_BUFFER = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    public static void computeControlPoints(float x1, float y1, float x2, float y2, float dir1X, float dir2X, float[] out4) {
        float proj1 = (x2 - x1) * dir1X;
        float offset1;
        if (proj1 >= 0.0f) {
            offset1 = Math.max(30.0f, proj1 * 0.5f);
        } else {
            offset1 = Math.min(65.0f, Math.max(30.0f, 20.0f + (float) Math.sqrt(-proj1) * 2.2f));
        }

        float proj2 = (x1 - x2) * dir2X;
        float offset2;
        if (proj2 >= 0.0f) {
            offset2 = Math.max(30.0f, proj2 * 0.5f);
        } else {
            offset2 = Math.min(65.0f, Math.max(30.0f, 20.0f + (float) Math.sqrt(-proj2) * 2.2f));
        }

        out4[0] = x1 + offset1 * dir1X;
        out4[1] = y1;
        out4[2] = x2 + offset2 * dir2X;
        out4[3] = y2;
    }

    public static void addBezierToBatch(float x1, float y1, float x2, float y2, int color, float thickness) {
        addBezierToBatch(x1, y1, x2, y2, 1.0f, -1.0f, color, thickness);
    }

    public static void addBezierToBatch(float x1, float y1, float x2, float y2, float dir1X, float dir2X, int color, float thickness) {
        if (ACTIVE_BATCH_BUFFER == null || ACTIVE_BATCH_POSE == null) return;

        float[] cp = new float[4];
        computeControlPoints(x1, y1, x2, y2, dir1X, dir2X, cp);
        float cx1 = cp[0], cy1 = cp[1], cx2 = cp[2], cy2 = cp[3];

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

            ACTIVE_BATCH_BUFFER.addVertex(ACTIVE_BATCH_POSE, px[i] + nx, py[i] + ny, 0.0f).setColor(r, g, b, a);
            ACTIVE_BATCH_BUFFER.addVertex(ACTIVE_BATCH_POSE, px[i + 1] + nx, py[i + 1] + ny, 0.0f).setColor(r, g, b, a);
            ACTIVE_BATCH_BUFFER.addVertex(ACTIVE_BATCH_POSE, px[i + 1] - nx, py[i + 1] - ny, 0.0f).setColor(r, g, b, a);
            ACTIVE_BATCH_BUFFER.addVertex(ACTIVE_BATCH_POSE, px[i] - nx, py[i] - ny, 0.0f).setColor(r, g, b, a);
        }
    }

    public static void endBatch() {
        if (ACTIVE_BATCH_BUFFER != null) {
            var mesh = ACTIVE_BATCH_BUFFER.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
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

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float pulseTime = (System.currentTimeMillis() % 1600L) / 1600.0f;
        float it = 1.0f - pulseTime;

        float[] cp = new float[4];
        int count = Math.min(wires.size(), 80);
        for (int w = 0; w < count; w++) {
            float[] wire = wires.get(w);
            float x1 = wire[0], y1 = wire[1], x2 = wire[2], y2 = wire[3];
            float dir1X = wire.length >= 6 ? wire[4] : 1.0f;
            float dir2X = wire.length >= 6 ? wire[5] : -1.0f;

            computeControlPoints(x1, y1, x2, y2, dir1X, dir2X, cp);
            float cx1 = cp[0], cy1 = cp[1], cx2 = cp[2], cy2 = cp[3];

            float dotX = it * it * it * x1 + 3 * it * it * pulseTime * cx1 + 3 * it * pulseTime * pulseTime * cx2 + pulseTime * pulseTime * pulseTime * x2;
            float dotY = it * it * it * y1 + 3 * it * it * pulseTime * cy1 + 3 * it * pulseTime * pulseTime * cy2 + pulseTime * pulseTime * pulseTime * y2;

            // Outer glow quad (0x88FFFFFF)
            buffer.addVertex(pose, dotX - 3.5f, dotY - 3.5f, 0.0f).setColor(1.0f, 1.0f, 1.0f, 0.53f);
            buffer.addVertex(pose, dotX + 3.5f, dotY - 3.5f, 0.0f).setColor(1.0f, 1.0f, 1.0f, 0.53f);
            buffer.addVertex(pose, dotX + 3.5f, dotY + 3.5f, 0.0f).setColor(1.0f, 1.0f, 1.0f, 0.53f);
            buffer.addVertex(pose, dotX - 3.5f, dotY + 3.5f, 0.0f).setColor(1.0f, 1.0f, 1.0f, 0.53f);

            // Inner core quad (0xFFFFFFFF)
            buffer.addVertex(pose, dotX - 2.0f, dotY - 2.0f, 0.0f).setColor(1.0f, 1.0f, 1.0f, 1.0f);
            buffer.addVertex(pose, dotX + 2.0f, dotY - 2.0f, 0.0f).setColor(1.0f, 1.0f, 1.0f, 1.0f);
            buffer.addVertex(pose, dotX + 2.0f, dotY + 2.0f, 0.0f).setColor(1.0f, 1.0f, 1.0f, 1.0f);
            buffer.addVertex(pose, dotX - 2.0f, dotY + 2.0f, 0.0f).setColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        var mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static boolean isPointNearBezier(float x1, float y1, float x2, float y2, double px, double py, double maxDist) {
        return isPointNearBezier(x1, y1, x2, y2, 1.0f, -1.0f, px, py, maxDist);
    }

    public static boolean isPointNearBezier(float x1, float y1, float x2, float y2, float dir1X, float dir2X, double px, double py, double maxDist) {
        float[] cp = new float[4];
        computeControlPoints(x1, y1, x2, y2, dir1X, dir2X, cp);
        float cx1 = cp[0], cy1 = cp[1], cx2 = cp[2], cy2 = cp[3];

        float minX = Math.min(Math.min(x1, x2), Math.min(cx1, cx2)) - (float) maxDist - 8f;
        float maxX = Math.max(Math.max(x1, x2), Math.max(cx1, cx2)) + (float) maxDist + 8f;
        float minY = Math.min(Math.min(y1, y2), Math.min(cy1, cy2)) - (float) maxDist - 8f;
        float maxY = Math.max(Math.max(y1, y2), Math.max(cy1, cy2)) + (float) maxDist + 8f;

        if (px < minX || px > maxX || py < minY || py > maxY) {
            return false;
        }

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

