package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.inspector.BoundaryPinInspector;
import com.gtceu.calcboard.client.gui.inspector.INodeSubInspector;
import com.gtceu.calcboard.client.gui.inspector.JunctionNodeInspector;
import com.gtceu.calcboard.client.gui.inspector.MachineNodeInspector;
import com.gtceu.calcboard.client.gui.inspector.PageSettingsInspector;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

public class NodeInspectorPanel {

    public static final int PANEL_WIDTH = 195;

    private final IBoardScreenContext screen;
    private NodeWidget targetWidget = null;
    private boolean visible = false;
    private boolean pageSettingsMode = false;
    private Component pendingTooltip = null;

    private final MachineNodeInspector machineInspector;
    private final JunctionNodeInspector junctionInspector;
    private final BoundaryPinInspector boundaryPinInspector;
    private final PageSettingsInspector pageSettingsInspector;

    public NodeInspectorPanel(IBoardScreenContext screen) {
        this.screen = screen;
        this.machineInspector = new MachineNodeInspector(screen);
        this.junctionInspector = new JunctionNodeInspector(screen);
        this.boundaryPinInspector = new BoundaryPinInspector(screen);
        this.pageSettingsInspector = new PageSettingsInspector(screen, this::close);
    }

    public boolean isVisible() {
        if (pageSettingsMode) {
            return visible;
        }
        if (!visible || targetWidget == null) {
            return false;
        }
        if (screen == null || screen.getGraph() == null) {
            return true;
        }
        return screen.getGraph().findNodeById(targetWidget.getNode().getId()) != null;
    }

    public boolean isPageSettingsMode() {
        return pageSettingsMode && visible;
    }

    public void openPageSettings() {
        boolean wasVisible = this.visible;
        unbindAllSubInspectors();
        this.targetWidget = null;
        this.pageSettingsMode = true;
        this.visible = true;
        pageSettingsInspector.bind(null);
        if (!wasVisible && screen != null) {
            screen.onNodeInspectorOpened();
        }
    }

    public void setTargetWidget(NodeWidget widget) {
        boolean wasVisible = this.visible;
        this.targetWidget = widget;
        this.pageSettingsMode = false;
        this.visible = (widget != null);
        if (this.visible) {
            INodeSubInspector active = getActiveInspector();
            if (active != null) {
                active.bind(widget);
            }
        } else {
            unbindAllSubInspectors();
        }
        if (this.visible && !wasVisible && screen != null) {
            screen.onNodeInspectorOpened();
        } else if (!this.visible && wasVisible && screen != null) {
            screen.onNodeInspectorClosed();
        }
    }

    public void close() {
        if (this.visible) {
            this.visible = false;
            this.targetWidget = null;
            this.pageSettingsMode = false;
            this.pendingTooltip = null;
            unbindAllSubInspectors();
            if (screen != null) {
                screen.onNodeInspectorClosed();
            }
        }
    }

    private void unbindAllSubInspectors() {
        machineInspector.bind(null);
        junctionInspector.bind(null);
        boundaryPinInspector.bind(null);
        pageSettingsInspector.bind(null);
    }

    public NodeWidget getTargetWidget() {
        return targetWidget;
    }

    public int getPanelWidth() {
        return isVisible() ? PANEL_WIDTH : 0;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!isVisible() || screen == null) return false;
        int px = screen.getScreenWidth() - PANEL_WIDTH - 6;
        int py = screen.getToolbarY() + 22;
        int ph = getPanelHeight();
        return mouseX >= px && mouseX <= px + PANEL_WIDTH && mouseY >= py && mouseY <= py + ph;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return isMouseOver(mouseX, mouseY);
    }

    public int getPanelHeight() {
        if (screen == null) {
            INodeSubInspector active = getActiveInspector();
            return active != null ? Math.max(160, active.getContentHeight()) : 160;
        }
        int screenH = screen.getScreenHeight();
        int py = screen.getToolbarY() + 22;
        int minH = Math.max(160, screenH - py - 32);
        if (pageSettingsMode) {
            int neededH = pageSettingsInspector.getContentHeight();
            return Math.max(neededH, Math.min(250, minH));
        }
        if (targetWidget == null || targetWidget.getNode() == null) {
            return minH;
        }
        RecipeNode node = targetWidget.getNode();
        if (node.isJunction() || node.isBoundaryPin()) {
            INodeSubInspector active = getActiveInspector();
            int neededH = active != null ? active.getContentHeight() : 240;
            return Math.max(minH, neededH);
        }
        int contentH = machineInspector.calculateContentHeight(node);
        return Math.max(minH, contentH);
    }

    public INodeSubInspector getActiveInspector() {
        if (pageSettingsMode) {
            return pageSettingsInspector;
        }
        if (targetWidget == null || targetWidget.getNode() == null) {
            return null;
        }
        RecipeNode node = targetWidget.getNode();
        if (node.isJunction()) {
            return junctionInspector;
        }
        if (node.isBoundaryPin()) {
            return boundaryPinInspector;
        }
        return machineInspector;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!isVisible() || screen == null) return;
        this.pendingTooltip = null;

        Font font = Minecraft.getInstance().font;
        int screenW = screen.getScreenWidth();
        int px = screenW - PANEL_WIDTH - 6;
        int py = screen.getToolbarY() + 22;
        int ph = getPanelHeight();

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 350.0f);

        graphics.fill(px, py, px + PANEL_WIDTH, py + ph, 0xF5101522);
        graphics.renderOutline(px, py, PANEL_WIDTH, ph, 0xFF334155);

        INodeSubInspector active = getActiveInspector();
        if (active != null) {
            active.bind(targetWidget);
            active.render(graphics, font, px, py, ph, mouseX, mouseY);
            this.pendingTooltip = active.getPendingTooltip();
        }

        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY) || screen == null) return false;

        int screenW = screen.getScreenWidth();
        int px = screenW - PANEL_WIDTH - 6;
        int py = screen.getToolbarY() + 22;

        if (button == 0) {
            int closeX = px + PANEL_WIDTH - 16;
            int closeY = py + 5;
            if (mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12) {
                close();
                return true;
            }

            INodeSubInspector active = getActiveInspector();
            if (active != null) {
                active.bind(targetWidget);
                active.mouseClicked(px, py, mouseX, mouseY, button);
                return true;
            }
        }
        return true;
    }

    public void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!isVisible() || pendingTooltip == null || screen == null) return;
        BoardTooltipRenderer.renderTooltip(
                graphics, font, pendingTooltip, mouseX, mouseY, screen.getScreenWidth(), screen.getScreenHeight()
        );
        pendingTooltip = null;
    }

    public List<GTVoltageTier> getInspectorTiers(RecipeNode node) {
        return machineInspector.getInspectorTiers(node);
    }

    public int getTierControlsHeight(RecipeNode node) {
        return machineInspector.getTierControlsHeight(node);
    }

    public MachineNodeInspector getMachineInspector() {
        return machineInspector;
    }

    public JunctionNodeInspector getJunctionInspector() {
        return junctionInspector;
    }

    public BoundaryPinInspector getBoundaryPinInspector() {
        return boundaryPinInspector;
    }

    public PageSettingsInspector getPageSettingsInspector() {
        return pageSettingsInspector;
    }
}
