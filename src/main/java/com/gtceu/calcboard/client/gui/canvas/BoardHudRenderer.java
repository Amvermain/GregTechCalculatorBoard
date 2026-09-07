package com.gtceu.calcboard.client.gui.canvas;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.RecipeSearchDialog;
import com.gtceu.calcboard.client.gui.widget.FavoritesDockWidget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.client.gui.interaction.CanvasQuickAddMarkerHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles canvas background grid dots, loading card status, modal dialog rendering, and quick-add marker HUD.
 */
public class BoardHudRenderer {

    public static void renderGridBackground(GuiGraphics graphics, int width, int height, double panX, double panY, double zoom) {
        int baseGridSize = com.gtceu.calcboard.api.storage.BoardManager.getInstance().getGridSnapSize();
        if (baseGridSize <= 0) baseGridSize = 16;

        int visualStep = baseGridSize;
        while (visualStep * zoom < 36.0) {
            visualStep *= 2;
        }

        double minCanvasX = -panX / zoom;
        double minCanvasY = -panY / zoom;
        double maxCanvasX = (width - panX) / zoom;
        double maxCanvasY = (height - panY) / zoom;

        long startX = (long) Math.floor(minCanvasX / visualStep) * visualStep;
        long endX = (long) Math.ceil(maxCanvasX / visualStep) * visualStep;
        long startY = (long) Math.floor(minCanvasY / visualStep) * visualStep;
        long endY = (long) Math.ceil(maxCanvasY / visualStep) * visualStep;

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

        float a = 0x2E / 255.0f;
        float r = 0.85f, g = 0.90f, b = 1.0f;

        for (long cx = startX; cx <= endX; cx += visualStep) {
            float sx = (float) (cx * zoom + panX);
            if (sx < -2 || sx > width + 2) continue;

            boolean isMajorX = (cx % (baseGridSize * 4) == 0);

            for (long cy = startY; cy <= endY; cy += visualStep) {
                float sy = (float) (cy * zoom + panY);
                if (sy < -2 || sy > height + 2) continue;

                boolean majorDot = isMajorX && (cy % (baseGridSize * 4) == 0);
                float dotAlpha = majorDot ? (0x55 / 255.0f) : a;
                float dotSize = majorDot ? 1.5f : 1.0f;

                buffer.vertex(pose, sx, sy, 0.0f).color(r, g, b, dotAlpha).endVertex();
                buffer.vertex(pose, sx + dotSize, sy, 0.0f).color(r, g, b, dotAlpha).endVertex();
                buffer.vertex(pose, sx + dotSize, sy + dotSize, 0.0f).color(r, g, b, dotAlpha).endVertex();
                buffer.vertex(pose, sx, sy + dotSize, 0.0f).color(r, g, b, dotAlpha).endVertex();
            }
        }

        tesselator.end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static void renderEmptyCanvasWatermark(GuiGraphics graphics, Font font, int width, int height, int nodeCount) {
        if (nodeCount > 0) return;
        String title = "GREGTECH CALCULATOR BOARD";
        String hint = Component.translatable("gui.gtcalcboard.canvas.empty_watermark_hint").getString();

        int cx = width / 2;
        int cy = height / 2 - 10;

        int titleW = font.width(title);
        int hintW = font.width(hint);

        graphics.drawString(font, title, cx - titleW / 2, cy - 8, 0x4494A3B8, false);
        graphics.drawString(font, hint, cx - hintW / 2, cy + 6, 0x4464748B, false);
    }

    public static void renderQuickAddMarker(GuiGraphics graphics, Font font, double qx, double qy, double canvasMouseX, double canvasMouseY) {
        renderQuickAddMarker(graphics, font, qx, qy, canvasMouseX, canvasMouseY, null, null);
    }

    public static void renderQuickAddMarker(
            GuiGraphics graphics,
            Font font,
            double qx,
            double qy,
            double canvasMouseX,
            double canvasMouseY,
            CanvasQuickAddMarkerHandler markerHandler,
            FlowGraph graph
    ) {
        renderQuickAddBaseCapsule(graphics, font, qx, qy, canvasMouseX, canvasMouseY);
        renderQuickAddFlyout(graphics, font, qx, qy, canvasMouseX, canvasMouseY, markerHandler, graph);
    }

    private static void renderQuickAddBaseCapsule(GuiGraphics graphics, Font font, double qx, double qy, double canvasMouseX, double canvasMouseY) {
        boolean searchHovered = canvasMouseX >= qx - 44 && canvasMouseX <= qx - 24 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;
        boolean junctionHovered = canvasMouseX >= qx - 21 && canvasMouseX <= qx - 1 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;
        boolean frameHovered = canvasMouseX >= qx + 2 && canvasMouseX <= qx + 22 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;
        boolean noteHovered = canvasMouseX >= qx + 25 && canvasMouseX <= qx + 45 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;

        graphics.fill((int)(qx - 47), (int)(qy - 13), (int)(qx + 48), (int)(qy + 13), 0xDD0F172A);
        graphics.renderOutline((int)(qx - 47), (int)(qy - 13), 95, 26, 0x88334155);

        renderButton(graphics, font, (int)(qx - 44), (int)(qy - 10), searchHovered, "?", 0xEE10B981, 0xBB059669, 0xFF6EE7B7, 0xCC10B981);
        renderButton(graphics, font, (int)(qx - 21), (int)(qy - 10), junctionHovered, "↔", 0xEE0284C7, 0xBB0369A1, 0xFF7DD3FC, 0xCC0284C7);
        renderButton(graphics, font, (int)(qx + 2), (int)(qy - 10), frameHovered, "▦", 0xEE8B5CF6, 0xBB6D28D9, 0xFFC4B5FD, 0xCC7C3AED);
        renderButton(graphics, font, (int)(qx + 25), (int)(qy - 10), noteHovered, "▪", 0xEEF59E0B, 0xBBB45309, 0xFFFDE68A, 0xCCD97706);
    }

    private static void renderButton(GuiGraphics graphics, Font font, int bx, int by, boolean hover, String icon, int hoverBg, int baseBg, int hoverBorder, int baseBorder) {
        int bg = hover ? hoverBg : baseBg;
        int border = hover ? hoverBorder : baseBorder;
        graphics.fill(bx, by, bx + 20, by + 20, bg);
        graphics.renderOutline(bx, by, 20, 20, border);
        graphics.drawString(font, icon, (int)(bx + 10 - font.width(icon) / 2.0f), by + 6, 0xFFFFFFFF, false);
    }

    private static void renderQuickAddFlyout(
            GuiGraphics graphics,
            Font font,
            double qx,
            double qy,
            double canvasMouseX,
            double canvasMouseY,
            CanvasQuickAddMarkerHandler markerHandler,
            FlowGraph graph
    ) {
        if (markerHandler == null || !markerHandler.hasQuickAddWireContext()) return;
        boolean isHoverArea = canvasMouseX >= qx - 23 && canvasMouseX <= qx + 1 && canvasMouseY >= qy - 62 && canvasMouseY <= qy + 12;
        if (!isHoverArea) return;

        RecipeNode srcNode = markerHandler.getQuickAddWireSourceNode();
        int portIdx = markerHandler.getQuickAddWirePortIdx();
        boolean isInput = markerHandler.isQuickAddWireInput();

        if (isInput) {
            renderInputWireFlyout(graphics, font, qx, qy, canvasMouseX, canvasMouseY, srcNode, portIdx, graph);
        } else {
            renderOutputWireFlyout(graphics, font, qx, qy, canvasMouseX, canvasMouseY, srcNode, portIdx, graph);
        }
    }

    private static void renderOutputWireFlyout(
            GuiGraphics graphics,
            Font font,
            double qx,
            double qy,
            double canvasMouseX,
            double canvasMouseY,
            RecipeNode srcNode,
            int portIdx,
            FlowGraph graph
    ) {
        FlowGraphSolver.PortFlowStats stats = (graph != null && srcNode != null) ? graph.getOutputPortStats(srcNode, portIdx) : null;
        double surplus = stats != null ? Math.max(0.0, stats.requiredOrProducedRate() - stats.connectedRate()) : 0.0;

        if (surplus > 0.0001) {
            graphics.fill((int)(qx - 23), (int)(qy - 60), (int)(qx + 1), (int)(qy - 11), 0xDD0F172A);
            graphics.renderOutline((int)(qx - 23), (int)(qy - 60), 24, 49, 0x88334155);

            boolean drainHover = canvasMouseX >= qx - 21 && canvasMouseX <= qx - 1 && canvasMouseY >= qy - 34 && canvasMouseY <= qy - 14;
            renderButton(graphics, font, (int)(qx - 21), (int)(qy - 34), drainHover, "↓", 0xEEEA580C, 0xBBF97316, 0xFFFDBA74, 0xCCF97316);

            boolean voidHover = canvasMouseX >= qx - 21 && canvasMouseX <= qx - 1 && canvasMouseY >= qy - 58 && canvasMouseY <= qy - 38;
            renderButton(graphics, font, (int)(qx - 21), (int)(qy - 58), voidHover, "✕", 0xEE9333EA, 0xBBA855F7, 0xFFD8B4FE, 0xCCA855F7);
        } else {
            graphics.fill((int)(qx - 23), (int)(qy - 36), (int)(qx + 1), (int)(qy - 11), 0xDD0F172A);
            graphics.renderOutline((int)(qx - 23), (int)(qy - 36), 24, 25, 0x88334155);

            boolean voidHover = canvasMouseX >= qx - 21 && canvasMouseX <= qx - 1 && canvasMouseY >= qy - 34 && canvasMouseY <= qy - 14;
            renderButton(graphics, font, (int)(qx - 21), (int)(qy - 34), voidHover, "✕", 0xEE9333EA, 0xBBA855F7, 0xFFD8B4FE, 0xCCA855F7);
        }
    }

    private static void renderInputWireFlyout(
            GuiGraphics graphics,
            Font font,
            double qx,
            double qy,
            double canvasMouseX,
            double canvasMouseY,
            RecipeNode srcNode,
            int portIdx,
            FlowGraph graph
    ) {
        FlowGraphSolver.PortFlowStats stats = (graph != null && srcNode != null) ? graph.getInputPortStats(srcNode, portIdx) : null;
        double deficit = stats != null ? Math.max(0.0, stats.requiredOrProducedRate() - stats.connectedRate()) : 0.0;

        if (deficit > 0.0001) {
            graphics.fill((int)(qx - 23), (int)(qy - 60), (int)(qx + 1), (int)(qy - 11), 0xDD0F172A);
            graphics.renderOutline((int)(qx - 23), (int)(qy - 60), 24, 49, 0x88334155);

            boolean supplyHover = canvasMouseX >= qx - 21 && canvasMouseX <= qx - 1 && canvasMouseY >= qy - 34 && canvasMouseY <= qy - 14;
            renderButton(graphics, font, (int)(qx - 21), (int)(qy - 34), supplyHover, "↑", 0xEE059669, 0xBB10B981, 0xFF6EE7B7, 0xCC10B981);

            boolean infHover = canvasMouseX >= qx - 21 && canvasMouseX <= qx - 1 && canvasMouseY >= qy - 58 && canvasMouseY <= qy - 38;
            renderButton(graphics, font, (int)(qx - 21), (int)(qy - 58), infHover, "∞", 0xEE0369A1, 0xBB0284C7, 0xFF7DD3FC, 0xCC0284C7);
        } else {
            graphics.fill((int)(qx - 23), (int)(qy - 36), (int)(qx + 1), (int)(qy - 11), 0xDD0F172A);
            graphics.renderOutline((int)(qx - 23), (int)(qy - 36), 24, 25, 0x88334155);

            boolean infHover = canvasMouseX >= qx - 21 && canvasMouseX <= qx - 1 && canvasMouseY >= qy - 34 && canvasMouseY <= qy - 14;
            renderButton(graphics, font, (int)(qx - 21), (int)(qy - 34), infHover, "∞", 0xEE0369A1, 0xBB0284C7, 0xFF7DD3FC, 0xCC0284C7);
        }
    }

    public record BackgroundLoadingTask(String title, int percent, boolean indeterminate, String statusText, String subtitle) {}

    public static void renderCentralLoadingCard(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, boolean anyModalOpen, FavoritesDockWidget favoritesDockWidget) {
        if (anyModalOpen) return;

        List<BackgroundLoadingTask> tasks = new ArrayList<>();

        if (RecipeSearchDialog.isCaching()) {
            var prog = RecipeSearchDialog.getCachingProgress();
            int pct = (prog != null && prog.totalPhases() > 0) ? (int) (prog.currentPhase() * 100.0 / prog.totalPhases()) : 0;
            String sub = (prog != null && prog.phaseKey() != null) ? Component.translatable(prog.phaseKey()).getString() : "";
            tasks.add(new BackgroundLoadingTask(
                    Component.translatable("gui.gtcalcboard.status.task_recipes").getString(),
                    pct,
                    false,
                    pct + "%",
                    sub
            ));
        } else if (favoritesDockWidget != null && favoritesDockWidget.isEmiLoading()) {
            tasks.add(new BackgroundLoadingTask(
                    Component.translatable("gui.gtcalcboard.status.task_recipes").getString(),
                    30,
                    true,
                    Component.translatable("gui.gtcalcboard.status.in_progress").getString(),
                    Component.translatable("gui.gtcalcboard.favorites_dock.loading_emi").getString()
            ));
        }

        if (com.gtceu.calcboard.api.catalog.MachineAddonCatalog.getInstance().isExhaustiveScanRunning()) {
            double prog = com.gtceu.calcboard.api.catalog.MachineAddonCatalog.getInstance().getExhaustiveProgress();
            int pct = (int) Math.round(prog * 100.0);
            tasks.add(new BackgroundLoadingTask(
                    Component.translatable("gui.gtcalcboard.status.task_addons").getString(),
                    pct,
                    false,
                    pct + "%",
                    Component.translatable("gui.gtcalcboard.status.indexing_addons", pct).getString()
            ));
        }

        var teamState = com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance();
        if (teamState.isCollaborationEnabled() && teamState.isTeamMode() && teamState.isPageLoading(teamState.getActiveTeamPageId())) {
            tasks.add(new BackgroundLoadingTask(
                    Component.translatable("gui.gtcalcboard.status.task_page_sync").getString(),
                    50,
                    true,
                    Component.translatable("gui.gtcalcboard.status.in_progress").getString(),
                    Component.translatable("gui.gtcalcboard.status.syncing_page_data").getString()
            ));
        }

        if (!com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.isInitialized()) {
            tasks.add(new BackgroundLoadingTask(
                    Component.translatable("gui.gtcalcboard.status.task_multiblocks").getString(),
                    50,
                    true,
                    Component.translatable("gui.gtcalcboard.status.in_progress").getString(),
                    Component.translatable("gui.gtcalcboard.status.indexing_multiblocks").getString()
            ));
        }

        if (tasks.isEmpty()) return;

        int cardW = 340;
        int rowH = 34;
        int cardH = 26 + (tasks.size() * rowH) + 4;
        int cardX = (screenWidth - cardW) / 2;
        int cardY = screenHeight - cardH - 24;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 450);

        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xF0101520);
        graphics.renderOutline(cardX, cardY, cardW, cardH, 0xFF35445E);

        graphics.fill(cardX, cardY, cardX + cardW, cardY + 20, 0xEE182232);
        graphics.fill(cardX, cardY + 19, cardX + cardW, cardY + 20, 0xFF2A364D);
        String headerTitle = "§e⏳ " + Component.translatable("gui.gtcalcboard.status.loading_header").getString();
        graphics.drawString(font, headerTitle, cardX + 8, cardY + 6, 0xFFFFFFFF, false);

        int curY = cardY + 24;
        for (BackgroundLoadingTask task : tasks) {
            graphics.drawString(font, "§f" + task.title, cardX + 10, curY, 0xFFE0E0E0, false);
            String status = "§7" + task.statusText;
            int statusW = font.width(status);
            graphics.drawString(font, status, cardX + cardW - 10 - statusW, curY, 0xFFAAAAAA, false);

            if (task.subtitle != null && !task.subtitle.isEmpty()) {
                String sub = font.plainSubstrByWidth("§8" + task.subtitle, cardW - 20);
                graphics.drawString(font, sub, cardX + 10, curY + 10, 0xFF888888, false);
            }

            int barX = cardX + 10;
            int barY = curY + 22;
            int barW = cardW - 20;
            int barH = 3;

            graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF222733);
            graphics.renderOutline(barX, barY, barW, barH, 0xFF3D4659);

            if (task.indeterminate) {
                long time = System.currentTimeMillis() % 1500;
                float pos = (time / 1500.0f);
                int segW = 45;
                int segX = barX + (int) (pos * (barW - segW));
                graphics.fill(segX, barY, segX + segW, barY + barH, 0xFF4A90E2);
            } else {
                float fillRatio = Math.max(0.05f, Math.min(1.0f, task.percent / 100.0f));
                int fillW = (int) (barW * fillRatio);
                graphics.fill(barX, barY, barX + fillW, barY + barH, 0xFF4A90E2);
            }

            curY += rowH;
        }

        graphics.pose().popPose();
    }
}
