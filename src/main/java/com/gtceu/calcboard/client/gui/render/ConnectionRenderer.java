package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.type.WireAnimationMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class ConnectionRenderer {

    private static BufferBuilder ACTIVE_BATCH_BUFFER = null;
    private static Matrix4f ACTIVE_BATCH_POSE = null;

    private static final float[] SCRATCH_CP = new float[4];
    private static final float[] SCRATCH_RGB = new float[3];

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

        computeControlPoints(x1, y1, x2, y2, dir1X, dir2X, SCRATCH_CP);
        float cx1 = SCRATCH_CP[0], cy1 = SCRATCH_CP[1], cx2 = SCRATCH_CP[2], cy2 = SCRATCH_CP[3];

        float chord = (float) Math.hypot(x2 - x1, y2 - y1);
        float net = (float) (Math.hypot(cx1 - x1, cy1 - y1) + Math.hypot(cx2 - cx1, cy2 - cy1) + Math.hypot(x2 - cx2, y2 - cy2));
        float arcLen = (chord + net) * 0.5f;

        int numPoints = Math.max(16, Math.min(32, (int) (arcLen / 16.0f)));
        float halfThick = thickness * 0.5f;
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        float prevX = x1;
        float prevY = y1;

        for (int i = 1; i <= numPoints; i++) {
            float t = (float) i / numPoints;
            float it = 1.0f - t;
            float currX = it * it * it * x1 + 3 * it * it * t * cx1 + 3 * it * t * t * cx2 + t * t * t * x2;
            float currY = it * it * it * y1 + 3 * it * it * t * cy1 + 3 * it * t * t * cy2 + t * t * t * y2;

            float segDx = currX - prevX;
            float segDy = currY - prevY;
            float len = (float) Math.hypot(segDx, segDy);
            if (len < 0.001f) len = 1.0f;
            float nx = -segDy / len * halfThick;
            float ny = segDx / len * halfThick;

            ACTIVE_BATCH_BUFFER.vertex(ACTIVE_BATCH_POSE, prevX + nx, prevY + ny, 0.0f).color(r, g, b, a).endVertex();
            ACTIVE_BATCH_BUFFER.vertex(ACTIVE_BATCH_POSE, currX + nx, currY + ny, 0.0f).color(r, g, b, a).endVertex();
            ACTIVE_BATCH_BUFFER.vertex(ACTIVE_BATCH_POSE, currX - nx, currY - ny, 0.0f).color(r, g, b, a).endVertex();
            ACTIVE_BATCH_BUFFER.vertex(ACTIVE_BATCH_POSE, prevX - nx, prevY - ny, 0.0f).color(r, g, b, a).endVertex();

            prevX = currX;
            prevY = currY;
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

    public static void renderPulseDotsBatch(GuiGraphics graphics, it.unimi.dsi.fastutil.floats.FloatList wires) {
        renderPulseDotsBatch(graphics, wires, WireAnimationMode.RATE_MODULATED);
    }

    public static void renderPulseDotsBatch(GuiGraphics graphics, it.unimi.dsi.fastutil.floats.FloatList wires, WireAnimationMode mode) {
        if (wires == null || wires.isEmpty() || mode == WireAnimationMode.DISABLED) return;

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

        long now = System.currentTimeMillis();
        int stride = (wires.size() % 9 == 0) ? 9 : ((wires.size() % 7 == 0) ? 7 : 6);
        int totalWires = wires.size() / stride;
        int count = Math.min(totalWires, 80);

        List<float[]> badgePositions = new ArrayList<>();
        List<String> badgeTexts = new ArrayList<>();

        for (int w = 0; w < count; w++) {
            int offset = w * stride;
            float x1 = wires.getFloat(offset);
            float y1 = wires.getFloat(offset + 1);
            float x2 = wires.getFloat(offset + 2);
            float y2 = wires.getFloat(offset + 3);
            float dir1X = wires.getFloat(offset + 4);
            float dir2X = wires.getFloat(offset + 5);
            float ratio = stride >= 7 ? wires.getFloat(offset + 6) : 1.0f;
            float fromEff = stride >= 9 ? wires.getFloat(offset + 7) : 1.0f;
            float badgeCode = stride >= 9 ? wires.getFloat(offset + 8) : 1.0f;

            float periodMs;
            if (badgeCode == -1.0f) {
                periodMs = ParticleBatchingEngine.computeBufferAnimationPeriodMs(fromEff);
            } else if (fromEff < 0.999f && fromEff > 0.0f) {
                periodMs = ParticleBatchingEngine.computeDeratedTravelPeriodMs(fromEff);
            } else {
                periodMs = (float) ParticleBatchingEngine.BASE_TRAVEL_PERIOD_MS;
            }
            float wirePulseTime = (now % (long) periodMs) / periodMs;

            float pulseTime = computeEffectivePulseTime(wirePulseTime, ratio, mode);
            if (pulseTime < 0.0f) continue;

            computeControlPoints(x1, y1, x2, y2, dir1X, dir2X, SCRATCH_CP);
            float cx1 = SCRATCH_CP[0], cy1 = SCRATCH_CP[1], cx2 = SCRATCH_CP[2], cy2 = SCRATCH_CP[3];

            float it = 1.0f - pulseTime;
            float dotX = it * it * it * x1 + 3 * it * it * pulseTime * cx1 + 3 * it * pulseTime * pulseTime * cx2 + pulseTime * pulseTime * pulseTime * x2;
            float dotY = it * it * it * y1 + 3 * it * it * pulseTime * cy1 + 3 * it * pulseTime * pulseTime * cy2 + pulseTime * pulseTime * pulseTime * y2;

            computePulseRgb(ratio, mode, SCRATCH_RGB);
            renderPulseQuad(buffer, pose, dotX, dotY, 3.5f, SCRATCH_RGB[0], SCRATCH_RGB[1], SCRATCH_RGB[2], 0.53f);
            renderPulseQuad(buffer, pose, dotX, dotY, 2.0f, Math.min(1.0f, SCRATCH_RGB[0] + 0.3f), Math.min(1.0f, SCRATCH_RGB[1] + 0.3f), Math.min(1.0f, SCRATCH_RGB[2] + 0.3f), 1.0f);

            if (badgeCode > 1.0f) {
                badgePositions.add(new float[]{dotX, dotY});
                badgeTexts.add(((int) badgeCode) + "x");
            } else if (badgeCode == -1.0f) {
                badgePositions.add(new float[]{dotX, dotY});
                badgeTexts.add("Bx");
            }
        }

        tesselator.end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        renderBadges(graphics, badgePositions, badgeTexts);
    }

    public static void renderPulseDotsBatch(GuiGraphics graphics, java.util.List<float[]> wires) {
        renderPulseDotsBatch(graphics, wires, WireAnimationMode.RATE_MODULATED);
    }

    public static void renderPulseDotsBatch(GuiGraphics graphics, java.util.List<float[]> wires, WireAnimationMode mode) {
        if (wires == null || wires.isEmpty() || mode == WireAnimationMode.DISABLED) return;

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

        long now = System.currentTimeMillis();
        int count = Math.min(wires.size(), 80);

        List<float[]> badgePositions = new ArrayList<>();
        List<String> badgeTexts = new ArrayList<>();

        for (int w = 0; w < count; w++) {
            float[] wire = wires.get(w);
            float x1 = wire[0], y1 = wire[1], x2 = wire[2], y2 = wire[3];
            float dir1X = wire.length >= 6 ? wire[4] : 1.0f;
            float dir2X = wire.length >= 6 ? wire[5] : -1.0f;
            float ratio = wire.length >= 7 ? wire[6] : 1.0f;
            float fromEff = wire.length >= 8 ? wire[7] : 1.0f;
            float badgeCode = wire.length >= 9 ? wire[8] : 1.0f;

            float periodMs;
            if (badgeCode == -1.0f) {
                periodMs = ParticleBatchingEngine.computeBufferAnimationPeriodMs(fromEff);
            } else if (fromEff < 0.999f && fromEff > 0.0f) {
                periodMs = ParticleBatchingEngine.computeDeratedTravelPeriodMs(fromEff);
            } else {
                periodMs = (float) ParticleBatchingEngine.BASE_TRAVEL_PERIOD_MS;
            }
            float wirePulseTime = (now % (long) periodMs) / periodMs;

            float pulseTime = computeEffectivePulseTime(wirePulseTime, ratio, mode);
            if (pulseTime < 0.0f) continue;

            computeControlPoints(x1, y1, x2, y2, dir1X, dir2X, SCRATCH_CP);
            float cx1 = SCRATCH_CP[0], cy1 = SCRATCH_CP[1], cx2 = SCRATCH_CP[2], cy2 = SCRATCH_CP[3];

            float it = 1.0f - pulseTime;
            float dotX = it * it * it * x1 + 3 * it * it * pulseTime * cx1 + 3 * it * pulseTime * pulseTime * cx2 + pulseTime * pulseTime * pulseTime * x2;
            float dotY = it * it * it * y1 + 3 * it * it * pulseTime * cy1 + 3 * it * pulseTime * pulseTime * cy2 + pulseTime * pulseTime * pulseTime * y2;

            computePulseRgb(ratio, mode, SCRATCH_RGB);
            renderPulseQuad(buffer, pose, dotX, dotY, 3.5f, SCRATCH_RGB[0], SCRATCH_RGB[1], SCRATCH_RGB[2], 0.53f);
            renderPulseQuad(buffer, pose, dotX, dotY, 2.0f, Math.min(1.0f, SCRATCH_RGB[0] + 0.3f), Math.min(1.0f, SCRATCH_RGB[1] + 0.3f), Math.min(1.0f, SCRATCH_RGB[2] + 0.3f), 1.0f);

            if (badgeCode > 1.0f) {
                badgePositions.add(new float[]{dotX, dotY});
                badgeTexts.add(((int) badgeCode) + "x");
            } else if (badgeCode == -1.0f) {
                badgePositions.add(new float[]{dotX, dotY});
                badgeTexts.add("Bx");
            }
        }

        tesselator.end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        renderBadges(graphics, badgePositions, badgeTexts);
    }

    private static void renderBadges(GuiGraphics graphics, List<float[]> positions, List<String> texts) {
        if (positions.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;
        Font font = mc.font;
        for (int i = 0; i < positions.size(); i++) {
            float[] pos = positions.get(i);
            graphics.pose().pushPose();
            graphics.pose().translate(pos[0] + 5.0f, pos[1] - 4.0f, 0.0f);
            graphics.pose().scale(0.6f, 0.6f, 1.0f);
            graphics.drawString(font, texts.get(i), 0, 0, 0xFFFFD700, true);
            graphics.pose().popPose();
        }
    }

    public static float computeEffectivePulseTime(float baseTime, float ratio, WireAnimationMode mode) {
        if (mode != WireAnimationMode.RATE_MODULATED) {
            return baseTime;
        }
        if (ratio <= 0.001f) {
            return -1.0f;
        }
        if (baseTime < ratio) {
            return baseTime / ratio;
        }
        return 1.0f;
    }

    public static void computePulseRgb(float ratio, WireAnimationMode mode, float[] outRgb) {
        if (mode != WireAnimationMode.RATE_MODULATED) {
            outRgb[0] = 1.0f;
            outRgb[1] = 1.0f;
            outRgb[2] = 1.0f;
            return;
        }
        if (ratio >= 1.0f) {
            outRgb[0] = 0.22f;
            outRgb[1] = 0.74f;
            outRgb[2] = 0.97f;
            return;
        }
        if (ratio >= 0.5f) {
            float t = (ratio - 0.5f) / 0.5f;
            outRgb[0] = 0.96f + t * (0.22f - 0.96f);
            outRgb[1] = 0.62f + t * (0.74f - 0.62f);
            outRgb[2] = 0.04f + t * (0.97f - 0.04f);
            return;
        }
        float t = Math.max(0.0f, ratio / 0.5f);
        outRgb[0] = 0.94f + t * (0.96f - 0.94f);
        outRgb[1] = 0.27f + t * (0.62f - 0.27f);
        outRgb[2] = 0.27f + t * (0.04f - 0.27f);
    }

    private static void renderPulseQuad(BufferBuilder buffer, Matrix4f pose, float cx, float cy, float radius, float r, float g, float b, float a) {
        buffer.vertex(pose, cx - radius, cy - radius, 0.0f).color(r, g, b, a).endVertex();
        buffer.vertex(pose, cx + radius, cy - radius, 0.0f).color(r, g, b, a).endVertex();
        buffer.vertex(pose, cx + radius, cy + radius, 0.0f).color(r, g, b, a).endVertex();
        buffer.vertex(pose, cx - radius, cy + radius, 0.0f).color(r, g, b, a).endVertex();
    }

    public static boolean isPointNearBezier(float x1, float y1, float x2, float y2, double px, double py, double maxDist) {
        return isPointNearBezier(x1, y1, x2, y2, 1.0f, -1.0f, px, py, maxDist);
    }

    public static boolean isPointNearBezier(float x1, float y1, float x2, float y2, float dir1X, float dir2X, double px, double py, double maxDist) {
        computeControlPoints(x1, y1, x2, y2, dir1X, dir2X, SCRATCH_CP);
        float cx1 = SCRATCH_CP[0], cy1 = SCRATCH_CP[1], cx2 = SCRATCH_CP[2], cy2 = SCRATCH_CP[3];

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

