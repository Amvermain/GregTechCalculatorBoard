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

import java.util.ArrayList;
import java.util.List;

/**
 * Handles canvas background grid dots, loading card status, modal dialog rendering, and quick-add marker HUD.
 */
public class BoardHudRenderer {

    public static void renderGridBackground(GuiGraphics graphics, int width, int height, double panX, double panY, double zoom) {
        int dotSpacing = (int) (24 * zoom);
        if (dotSpacing < 10) return;

        int startX = (int) (panX % dotSpacing);
        int startY = (int) (panY % dotSpacing);

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

        float a = 0x22 / 255.0f;
        float r = 1.0f, g = 1.0f, b = 1.0f;

        for (int x = startX; x < width; x += dotSpacing) {
            for (int y = startY; y < height; y += dotSpacing) {
                buffer.vertex(pose, x, y, 0.0f).color(r, g, b, a).endVertex();
                buffer.vertex(pose, x + 1, y, 0.0f).color(r, g, b, a).endVertex();
                buffer.vertex(pose, x + 1, y + 1, 0.0f).color(r, g, b, a).endVertex();
                buffer.vertex(pose, x, y + 1, 0.0f).color(r, g, b, a).endVertex();
            }
        }

        tesselator.end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static void renderQuickAddMarker(GuiGraphics graphics, Font font, double qx, double qy, double canvasMouseX, double canvasMouseY) {
        boolean searchHovered = canvasMouseX >= qx - 44 && canvasMouseX <= qx - 24 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;
        boolean junctionHovered = canvasMouseX >= qx - 21 && canvasMouseX <= qx - 1 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;
        boolean frameHovered = canvasMouseX >= qx + 2 && canvasMouseX <= qx + 22 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;
        boolean noteHovered = canvasMouseX >= qx + 25 && canvasMouseX <= qx + 45 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;

        // Outer capsule background
        graphics.fill((int)(qx - 47), (int)(qy - 13), (int)(qx + 48), (int)(qy + 13), 0xDD0F172A);
        graphics.renderOutline((int)(qx - 47), (int)(qy - 13), 95, 26, 0x88334155);

        // 1. [🔍] Search Recipe (Emerald)
        int searchBg = searchHovered ? 0xEE10B981 : 0xBB059669;
        int searchBorder = searchHovered ? 0xFF6EE7B7 : 0xCC10B981;
        graphics.fill((int)(qx - 44), (int)(qy - 10), (int)(qx - 24), (int)(qy + 10), searchBg);
        graphics.renderOutline((int)(qx - 44), (int)(qy - 10), 20, 20, searchBorder);
        String searchIcon = "🔍";
        graphics.drawString(font, searchIcon, (int)(qx - 34 - font.width(searchIcon) / 2.0f), (int)(qy - 4), 0xFFFFFFFF, false);

        // 2. [🔀] Insert Junction (Sky/Cyan)
        int juncBg = junctionHovered ? 0xEE0284C7 : 0xBB0369A1;
        int juncBorder = junctionHovered ? 0xFF7DD3FC : 0xCC0284C7;
        graphics.fill((int)(qx - 21), (int)(qy - 10), (int)(qx - 1), (int)(qy + 10), juncBg);
        graphics.renderOutline((int)(qx - 21), (int)(qy - 10), 20, 20, juncBorder);
        String juncIcon = "🔀";
        graphics.drawString(font, juncIcon, (int)(qx - 11 - font.width(juncIcon) / 2.0f), (int)(qy - 4), 0xFFFFFFFF, false);

        // 3. [🖼] Group Frame (Purple/Violet)
        int frameBg = frameHovered ? 0xEE8B5CF6 : 0xBB6D28D9;
        int frameBorder = frameHovered ? 0xFFC4B5FD : 0xCC7C3AED;
        graphics.fill((int)(qx + 2), (int)(qy - 10), (int)(qx + 22), (int)(qy + 10), frameBg);
        graphics.renderOutline((int)(qx + 2), (int)(qy - 10), 20, 20, frameBorder);
        String frameIcon = "🖼";
        graphics.drawString(font, frameIcon, (int)(qx + 12 - font.width(frameIcon) / 2.0f), (int)(qy - 4), 0xFFFFFFFF, false);

        // 4. [📝] Sticky Note / Memo (Amber/Gold)
        int noteBg = noteHovered ? 0xEEF59E0B : 0xBBB45309;
        int noteBorder = noteHovered ? 0xFFFDE68A : 0xCCD97706;
        graphics.fill((int)(qx + 25), (int)(qy - 10), (int)(qx + 45), (int)(qy + 10), noteBg);
        graphics.renderOutline((int)(qx + 25), (int)(qy - 10), 20, 20, noteBorder);
        String noteIcon = "📝";
        graphics.drawString(font, noteIcon, (int)(qx + 35 - font.width(noteIcon) / 2.0f), (int)(qy - 4), 0xFFFFFFFF, false);
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
