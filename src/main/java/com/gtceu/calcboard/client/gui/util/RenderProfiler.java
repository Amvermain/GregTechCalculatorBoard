package com.gtceu.calcboard.client.gui.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Real-time rendering and computation profiler for F3 Debug HUD.
 * Tracks per-subsystem frame times using Exponential Moving Average (EMA).
 */
public class RenderProfiler {

    private static final RenderProfiler INSTANCE = new RenderProfiler();

    public static RenderProfiler getInstance() {
        return INSTANCE;
    }

    private final Map<String, SubsystemTimer> timers = new LinkedHashMap<>();
    private long frameStartNano = 0;
    private long currentSectionStartNano = 0;
    private String currentSectionName = null;
    private double totalFrameTimeMs = 0.0;
    private int fps = 60;
    private int frameCounter = 0;
    private long lastFpsUpdateNano = 0;

    private static class SubsystemTimer {
        double emaDurationMs = 0.0;
    }

    public void startFrame() {
        frameStartNano = System.nanoTime();
        currentSectionStartNano = frameStartNano;
        currentSectionName = null;

        frameCounter++;
        if (frameStartNano - lastFpsUpdateNano >= 1_000_000_000L) {
            fps = frameCounter;
            frameCounter = 0;
            lastFpsUpdateNano = frameStartNano;
        }
    }

    public void startSection(String sectionName) {
        long now = System.nanoTime();
        if (currentSectionName != null) {
            recordSection(currentSectionName, now - currentSectionStartNano);
        }
        currentSectionName = sectionName;
        currentSectionStartNano = now;
    }

    public void endSection() {
        long now = System.nanoTime();
        if (currentSectionName != null) {
            recordSection(currentSectionName, now - currentSectionStartNano);
            currentSectionName = null;
        }
        currentSectionStartNano = now;
    }

    public void endFrame() {
        endSection();
        long now = System.nanoTime();
        double frameMs = (now - frameStartNano) / 1_000_000.0;
        totalFrameTimeMs = totalFrameTimeMs == 0.0 ? frameMs : (totalFrameTimeMs * 0.85 + frameMs * 0.15);
    }

    private void recordSection(String name, long elapsedNano) {
        double ms = elapsedNano / 1_000_000.0;
        SubsystemTimer timer = timers.computeIfAbsent(name, k -> new SubsystemTimer());
        timer.emaDurationMs = timer.emaDurationMs == 0.0 ? ms : (timer.emaDurationMs * 0.85 + ms * 0.15);
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int bottomOffset) {
        int panelW = 160;
        int rowH = 11;
        int headerH = 16;
        int panelH = headerH + (timers.size() + 1) * rowH + 6;

        int x = screenWidth - panelW - 8;
        int y = screenHeight - bottomOffset - panelH - 4;

        renderBackground(graphics, x, y, panelW, panelH);
        renderHeader(graphics, font, x, y, panelW);
        renderSectionRows(graphics, font, x, y + headerH + 2, panelW, rowH);
    }

    private void renderBackground(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xDD0B1120);
        graphics.renderOutline(x, y, w, h, 0xFF334155);
    }

    private void renderHeader(GuiGraphics graphics, Font font, int x, int y, int w) {
        graphics.fill(x + 1, y + 1, x + w - 1, y + 15, 0xEE1E293B);
        String title = "⚙ " + Component.translatable("gui.gtcalcboard.debug.profiler_title").getString();
        String fpsStr = fps + " FPS";
        graphics.drawString(font, title, x + 6, y + 4, 0xFF38BDF8, false);
        graphics.drawString(font, fpsStr, x + w - font.width(fpsStr) - 6, y + 4, 0xFF94A3B8, false);
    }

    private void renderSectionRows(GuiGraphics graphics, Font font, int startX, int startY, int w, int rowH) {
        int curY = startY;
        for (Map.Entry<String, SubsystemTimer> entry : timers.entrySet()) {
            renderMetricRow(graphics, font, startX, curY, w, entry.getKey(), entry.getValue().emaDurationMs);
            curY += rowH;
        }

        graphics.fill(startX + 4, curY + 1, startX + w - 4, curY + 2, 0xFF334155);
        curY += 3;
        String totalLabel = Component.translatable("gui.gtcalcboard.debug.total_frame").getString();
        renderMetricRow(graphics, font, startX, curY, w, totalLabel, totalFrameTimeMs);
    }

    private void renderMetricRow(GuiGraphics graphics, Font font, int x, int y, int w, String label, double ms) {
        int color = (ms < 1.0) ? 0xFF4ADE80 : ((ms < 4.0) ? 0xFFFACC15 : 0xFFF87171);
        String valStr = String.format(java.util.Locale.ROOT, "%.2f ms", ms);

        graphics.drawString(font, label, x + 6, y, 0xFFCBD5E1, false);
        graphics.drawString(font, valStr, x + w - font.width(valStr) - 6, y, color, false);
    }
}
