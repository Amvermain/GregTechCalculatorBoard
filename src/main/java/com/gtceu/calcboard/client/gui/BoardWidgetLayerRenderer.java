package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.canvas.BoardHudRenderer;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.tutorial.TutorialOverlay;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.client.gui.widget.LeftActivityBarWidget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;

/**
 * Handles rendering of top-level screen widgets, docks, HUD elements, overlays, and tooltips.
 */
public class BoardWidgetLayerRenderer {
    private final BoardScreen screen;

    public BoardWidgetLayerRenderer(BoardScreen screen) {
        this.screen = screen;
    }

    public void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        screen.getWorkspaceTabBar().render(graphics, mouseX, mouseY, partialTicks);
        screen.getPageTabBar().render(graphics, mouseX, mouseY, partialTicks);
        screen.getToolbarWidget().render(graphics, mouseX, mouseY);
        screen.getLeftActivityBar().render(graphics, mouseX, mouseY, partialTicks);
        if (BoardManager.getInstance().isShowHotkeyHud()) {
            screen.getHotkeyHudWidget().render(graphics, mouseX, mouseY, partialTicks);
        }
        screen.getFavoritesDockWidget().render(graphics, mouseX, mouseY, partialTicks);
        screen.getPageBrowserDrawer().render(graphics, mouseX, mouseY, partialTicks);

        screen.updateGraphSummaryIfDirty();
        screen.getSummaryOverlay().setRightOffset(screen.getSummaryRightOffset());
        screen.getSummaryOverlay().render(graphics, screen.width, screen.height, screen.getCachedSummary(), mouseX, mouseY);

        screen.getNodeInspectorPanel().render(graphics, mouseX, mouseY, partialTicks);
        screen.getStatusBar().render(graphics, mouseX, mouseY, partialTicks);
        screen.getSelectionToolbarWidget().render(graphics, screen.getMinecraftFont(), mouseX, mouseY);
        BoardHudRenderer.renderCentralLoadingCard(graphics, screen.getMinecraftFont(), screen.width, screen.height, screen.isAnyModalOpen(), screen.getFavoritesDockWidget());

        renderTooltipsIfAppropriate(graphics, mouseX, mouseY);
    }

    private void renderTooltipsIfAppropriate(GuiGraphics graphics, int mouseX, int mouseY) {
        if (screen.isAnyModalOpen()) return;

        if (!screen.getPageBrowserDrawer().isOpen()) {
            graphics.flush();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.disableDepthTest();
            BoardTooltipRenderer.renderTooltips(screen, graphics, screen.getMinecraftFont(), mouseX, mouseY);
            screen.getFavoritesDockWidget().renderTooltips(graphics, screen.getMinecraftFont(), mouseX, mouseY);
            screen.getWorkspaceTabBar().renderTooltips(graphics, screen.getMinecraftFont(), mouseX, mouseY);
            screen.getLeftActivityBar().renderTooltips(graphics, screen.getMinecraftFont(), mouseX, mouseY);
            screen.getSelectionToolbarWidget().renderTooltips(graphics, screen.getMinecraftFont(), mouseX, mouseY);
        } else if (mouseX >= 0 && mouseX <= LeftActivityBarWidget.BAR_WIDTH) {
            graphics.flush();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.disableDepthTest();
            screen.getLeftActivityBar().renderTooltips(graphics, screen.getMinecraftFont(), mouseX, mouseY);
        }
    }

    public void renderTopOverlays(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (screen.isAnyModalOpen()) {
            graphics.flush();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.disableDepthTest();
            screen.getDialogManager().renderModals(graphics, screen.width, screen.height, mouseX, mouseY, partialTicks);
        }
        if (screen.getCanvasHandler() != null && screen.getCanvasHandler().getContextMenuManager() != null) {
            screen.getCanvasHandler().getContextMenuManager().render(graphics, screen.getMinecraftFont(), mouseX, mouseY);
        }
        TutorialOverlay.render(graphics, screen.getMinecraftFont(), screen, screen.width, screen.height, mouseX, mouseY);
        BoardToast.render(graphics, screen.getMinecraftFont(), screen.width, screen.height);
    }
}
