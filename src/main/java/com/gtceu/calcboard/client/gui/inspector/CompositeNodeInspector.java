package com.gtceu.calcboard.client.gui.inspector;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.inspector.section.IInspectorSection;
import com.gtceu.calcboard.client.gui.widget.NodeInspectorPanel;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class CompositeNodeInspector implements INodeSubInspector {

    protected final IBoardScreenContext screen;
    protected final List<IInspectorSection> sections;
    protected NodeWidget targetWidget;
    protected Component pendingTooltip;

    public CompositeNodeInspector(IBoardScreenContext screen) {
        this.screen = screen;
        this.sections = NodeInspectorRegistry.createSections();
    }

    public CompositeNodeInspector(IBoardScreenContext screen, List<IInspectorSection> sections) {
        this.screen = screen;
        this.sections = new ArrayList<>(sections);
    }

    @Override
    public void bind(NodeWidget targetWidget) {
        this.targetWidget = targetWidget;
        RecipeNode node = targetWidget != null ? targetWidget.getNode() : null;
        for (IInspectorSection section : sections) {
            section.bind(targetWidget, node, screen);
        }
    }

    public NodeWidget getTargetWidget() {
        return targetWidget;
    }

    public List<IInspectorSection> getSections() {
        return sections;
    }

    public List<IInspectorSection> getApplicableSections(RecipeNode node) {
        if (node == null) return List.of();
        List<IInspectorSection> applicable = new ArrayList<>();
        for (IInspectorSection s : sections) {
            if (s.isApplicable(node)) {
                applicable.add(s);
            }
        }
        return applicable;
    }

    @Override
    public int getContentHeight() {
        if (targetWidget == null || targetWidget.getNode() == null) {
            return 0;
        }
        return calculateContentHeight(targetWidget.getNode());
    }

    public int calculateContentHeight(RecipeNode node) {
        if (node == null) return 0;
        int total = 0;
        for (IInspectorSection s : sections) {
            if (s.isApplicable(node)) {
                total += s.getHeight(node);
            }
        }
        return total;
    }

    @Override
    public Component getPendingTooltip() {
        return pendingTooltip;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int px, int py, int ph, int mouseX, int mouseY) {
        this.pendingTooltip = null;
        if (targetWidget == null || targetWidget.getNode() == null) {
            return;
        }
        RecipeNode node = targetWidget.getNode();
        int curY = py;

        for (IInspectorSection section : sections) {
            if (!section.isApplicable(node)) continue;

            int sectionH = section.getHeight(node);
            int x = section.isFullWidth() ? px : px + 8;
            int w = section.isFullWidth() ? NodeInspectorPanel.PANEL_WIDTH : NodeInspectorPanel.PANEL_WIDTH - 16;

            section.render(graphics, font, x, curY, w, mouseX, mouseY);
            Component tip = section.getPendingTooltip();
            if (tip != null) {
                this.pendingTooltip = tip;
            }
            curY += sectionH;
        }
    }

    @Override
    public boolean mouseClicked(int px, int py, double mouseX, double mouseY, int button) {
        if (button != 0 || targetWidget == null || targetWidget.getNode() == null) {
            return false;
        }
        RecipeNode node = targetWidget.getNode();
        int curY = py;

        for (IInspectorSection section : sections) {
            if (!section.isApplicable(node)) continue;

            int sectionH = section.getHeight(node);
            int x = section.isFullWidth() ? px : px + 8;
            int w = section.isFullWidth() ? NodeInspectorPanel.PANEL_WIDTH : NodeInspectorPanel.PANEL_WIDTH - 16;

            if (section.mouseClicked(x, curY, w, mouseX, mouseY, button)) {
                return true;
            }
            curY += sectionH;
        }
        return true;
    }
}
