package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.CanvasInteractionHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasQuickAddMarkerHandler;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class QuickAddMarkerTooltipRenderer {

    private QuickAddMarkerTooltipRenderer() {}

    public static boolean renderQuickAddMarkerTooltips(
            GuiGraphics graphics,
            Font font,
            BoardScreen screen,
            CanvasInteractionHandler canvasHandler,
            double qx,
            double qy,
            double canvasMouseX,
            double canvasMouseY,
            int mouseX,
            int mouseY
    ) {
        var markerHandler = canvasHandler.getQuickAddMarkerHandler();
        boolean inJunctionCol = canvasMouseX >= qx - 21 && canvasMouseX <= qx - 1;

        if (inJunctionCol && markerHandler.hasQuickAddWireContext()) {
            boolean inSub1 = canvasMouseY >= qy - 34 && canvasMouseY <= qy - 14;
            boolean inSub2 = canvasMouseY >= qy - 58 && canvasMouseY <= qy - 38;
            if (inSub1 || inSub2) {
                renderFlyoutTooltip(graphics, font, screen, markerHandler, inSub1, inSub2, mouseX, mouseY);
                return true;
            }
        }

        boolean searchHovered = canvasMouseX >= qx - 44 && canvasMouseX <= qx - 24 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;
        boolean junctionHovered = inJunctionCol && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;
        boolean frameHovered = canvasMouseX >= qx + 2 && canvasMouseX <= qx + 22 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;
        boolean noteHovered = canvasMouseX >= qx + 25 && canvasMouseX <= qx + 45 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;

        if (searchHovered) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§a? ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_search")), mouseX, mouseY, screen.width, screen.height);
            return true;
        } else if (junctionHovered) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§b↔ ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_junction")), mouseX, mouseY, screen.width, screen.height);
            return true;
        } else if (frameHovered) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§d▦ ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_frame")), mouseX, mouseY, screen.width, screen.height);
            return true;
        } else if (noteHovered) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§e▪ ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_note")), mouseX, mouseY, screen.width, screen.height);
            return true;
        }
        return false;
    }

    private static void renderFlyoutTooltip(
            GuiGraphics graphics,
            Font font,
            BoardScreen screen,
            CanvasQuickAddMarkerHandler markerHandler,
            boolean inSub1,
            boolean inSub2,
            int mouseX,
            int mouseY
    ) {
        RecipeNode srcNode = markerHandler.getQuickAddWireSourceNode();
        int portIdx = markerHandler.getQuickAddWirePortIdx();
        boolean isInput = markerHandler.isQuickAddWireInput();
        FlowGraph graph = screen.getGraph();
        IngredientStack stack = markerHandler.getQuickAddWireStack();
        String stackName = stack != null ? stack.getDisplayName() : "Ingredient";
        boolean isFluid = stack != null && stack.isFluid();

        if (isInput) {
            renderInputFlyoutTooltip(graphics, font, screen, srcNode, portIdx, inSub1, inSub2, stackName, isFluid, graph, mouseX, mouseY);
        } else {
            renderOutputFlyoutTooltip(graphics, font, screen, srcNode, portIdx, inSub1, inSub2, stackName, isFluid, graph, mouseX, mouseY);
        }
    }

    private static void renderOutputFlyoutTooltip(
            GuiGraphics graphics,
            Font font,
            BoardScreen screen,
            RecipeNode srcNode,
            int portIdx,
            boolean inSub1,
            boolean inSub2,
            String stackName,
            boolean isFluid,
            FlowGraph graph,
            int mouseX,
            int mouseY
    ) {
        FlowGraphSolver.PortFlowStats stats = (graph != null && srcNode != null) ? graph.getOutputPortStats(srcNode, portIdx) : null;
        double surplus = stats != null ? Math.max(0.0, stats.requiredOrProducedRate() - stats.connectedRate()) : 0.0;

        if (surplus > 0.0001) {
            if (inSub1) {
                String rateStr = FormatUtil.formatRate(surplus, isFluid);
                BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§6↓ ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_drain", stackName, rateStr)), mouseX, mouseY, screen.width, screen.height);
            } else if (inSub2) {
                BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§d✕ ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_void_sink", stackName)), mouseX, mouseY, screen.width, screen.height);
            }
        } else if (inSub1) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§d✕ ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_void_sink", stackName)), mouseX, mouseY, screen.width, screen.height);
        }
    }

    private static void renderInputFlyoutTooltip(
            GuiGraphics graphics,
            Font font,
            BoardScreen screen,
            RecipeNode srcNode,
            int portIdx,
            boolean inSub1,
            boolean inSub2,
            String stackName,
            boolean isFluid,
            FlowGraph graph,
            int mouseX,
            int mouseY
    ) {
        FlowGraphSolver.PortFlowStats stats = (graph != null && srcNode != null) ? graph.getInputPortStats(srcNode, portIdx) : null;
        double deficit = stats != null ? Math.max(0.0, stats.requiredOrProducedRate() - stats.connectedRate()) : 0.0;

        if (deficit > 0.0001) {
            if (inSub1) {
                String rateStr = FormatUtil.formatRate(deficit, isFluid);
                BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§a↑ ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_supply", stackName, rateStr)), mouseX, mouseY, screen.width, screen.height);
            } else if (inSub2) {
                BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§b∞ ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_infinite", stackName)), mouseX, mouseY, screen.width, screen.height);
            }
        } else if (inSub1) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.literal("§b∞ ").append(Component.translatable("gui.gtcalcboard.tooltip.quick_infinite", stackName)), mouseX, mouseY, screen.width, screen.height);
        }
    }
}
