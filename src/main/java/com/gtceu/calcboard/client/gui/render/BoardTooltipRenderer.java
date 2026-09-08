package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.CanvasInteractionHandler;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BoardTooltipRenderer {

    public record VirtualTooltipPositioner(int screenWidth, int screenHeight) implements ClientTooltipPositioner {
        @Override
        public Vector2ic positionTooltip(int ignoredW, int ignoredH, int mouseX, int mouseY, int tooltipWidth, int tooltipHeight) {
            Vector2i pos = new Vector2i(mouseX + 12, mouseY - 12);
            if (pos.x + tooltipWidth > screenWidth) {
                pos.x = Math.max(pos.x - 24 - tooltipWidth, 4);
            }
            int totalH = tooltipHeight + 3;
            if (pos.y + totalH > screenHeight) {
                pos.y = screenHeight - totalH;
            }
            if (pos.y < 4) {
                pos.y = 4;
            }
            return pos;
        }
    }

    private BoardTooltipRenderer() {}

    public static ClientTooltipPositioner createPositioner(int screenWidth, int screenHeight) {
        return new VirtualTooltipPositioner(screenWidth, screenHeight);
    }

    public static void renderComponentTooltip(GuiGraphics graphics, Font font, List<Component> lines, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (lines == null || lines.isEmpty() || font == null || graphics == null) return;
        List<FormattedCharSequence> formatted = lines.stream().map(Component::getVisualOrderText).toList();

        graphics.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.disableDepthTest();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 1000.0f);
        try {
            graphics.renderTooltip(font, formatted, createPositioner(screenWidth, screenHeight), mouseX, mouseY);
        } finally {
            graphics.pose().popPose();
            RenderSystem.enableDepthTest();
        }
    }

    public static void renderComponentTooltip(GuiGraphics graphics, Font font, List<Component> lines, int mouseX, int mouseY) {
        int sw = 800;
        int sh = 600;
        var transform = BoardScreen.getCurrentTransform();
        if (transform != null && transform.isScaled()) {
            sw = transform.getVirtualWidth();
            sh = transform.getVirtualHeight();
        } else if (graphics != null) {
            sw = graphics.guiWidth();
            sh = graphics.guiHeight();
        }
        renderComponentTooltip(graphics, font, lines, mouseX, mouseY, sw, sh);
    }

    public static void renderTooltip(GuiGraphics graphics, Font font, Component component, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (component == null) return;
        renderComponentTooltip(graphics, font, List.of(component), mouseX, mouseY, screenWidth, screenHeight);
    }

    public static void renderTooltip(GuiGraphics graphics, Font font, Component component, int mouseX, int mouseY) {
        if (component == null) return;
        renderComponentTooltip(graphics, font, List.of(component), mouseX, mouseY);
    }

    public static void renderTooltips(BoardScreen screen, GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (screen == null) return;
        renderTooltipsInternal(screen, graphics, font, mouseX, mouseY);
    }

    private static void renderTooltipsInternal(BoardScreen screen, GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        double canvasMouseX = screen.toCanvasX(mouseX);
        double canvasMouseY = screen.toCanvasY(mouseY);
        FlowGraph graph = screen.getGraph();
        List<NodeWidget> nodeWidgets = screen.getNodeWidgets();

        if (renderNodeWidgetsTooltips(screen, graphics, font, nodeWidgets, graph, canvasMouseX, canvasMouseY, mouseX, mouseY)) {
            return;
        }

        if (renderCanvasHandlerTooltips(screen, graphics, font, canvasMouseX, canvasMouseY, mouseX, mouseY)) {
            return;
        }

        if (screen.getSummaryOverlay() != null) {
            screen.getSummaryOverlay().renderTooltips(graphics, font, mouseX, mouseY);
        }
    }

    private static boolean renderNodeWidgetsTooltips(BoardScreen screen, GuiGraphics graphics, Font font, List<NodeWidget> nodeWidgets, FlowGraph graph, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
        for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = nodeWidgets.get(i);
            if (widget.isPointInside(canvasMouseX, canvasMouseY)) {
                if (renderWidgetHoverAction(screen, graphics, font, widget, graph, canvasMouseX, canvasMouseY, mouseX, mouseY)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean renderWidgetHoverAction(BoardScreen screen, GuiGraphics graphics, Font font, NodeWidget widget, FlowGraph graph, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
        if (renderWidgetHeaderButtons(screen, graphics, font, widget, canvasMouseX, canvasMouseY, mouseX, mouseY)) {
            return true;
        }
        if (RerouteTooltipRenderer.renderRerouteTooltip(graphics, font, screen, widget, graph, mouseX, mouseY)) {
            return true;
        }
        int inIdx = widget.getHoveredInputPortIndex(canvasMouseX, canvasMouseY);
        if (NodePortTooltipRenderer.renderInputPortTooltip(graphics, font, screen, widget, inIdx, mouseX, mouseY)) {
            return true;
        }
        int outIdx = widget.getHoveredOutputPortIndex(canvasMouseX, canvasMouseY);
        if (NodePortTooltipRenderer.renderOutputPortTooltip(graphics, font, screen, widget, outIdx, mouseX, mouseY)) {
            return true;
        }
        if (NodeControlsTooltipRenderer.renderNodeInfoTooltip(graphics, font, screen, widget, canvasMouseX, canvasMouseY, mouseX, mouseY)) {
            return true;
        }
        if (renderMachineIconTooltip(screen, graphics, font, widget, canvasMouseX, canvasMouseY, mouseX, mouseY)) {
            return true;
        }
        if (NodeControlsTooltipRenderer.renderTierButtonTooltip(graphics, font, screen, widget, canvasMouseX, canvasMouseY, mouseX, mouseY)) {
            return true;
        }
        if (NodeControlsTooltipRenderer.renderNodeBadgeTooltip(graphics, font, screen, widget, canvasMouseX, canvasMouseY, mouseX, mouseY)) {
            return true;
        }
        if (NodeControlsTooltipRenderer.renderCountBoxTooltip(graphics, font, screen, widget, canvasMouseX, canvasMouseY, mouseX, mouseY)) {
            return true;
        }
        return NodeControlsTooltipRenderer.renderMachineConfigTooltip(graphics, font, screen, widget, canvasMouseX, canvasMouseY, mouseX, mouseY);
    }

    private static boolean renderWidgetHeaderButtons(BoardScreen screen, GuiGraphics graphics, Font font, NodeWidget widget, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
        if (widget.isCloseButtonHovered(canvasMouseX, canvasMouseY)) {
            renderTooltip(graphics, font, Component.literal("§c✕ ").append(Component.translatable("gui.gtcalcboard.tooltip.remove_node")), mouseX, mouseY, screen.width, screen.height);
            return true;
        }
        if (widget.isTargetButtonHovered(canvasMouseX, canvasMouseY)) {
            renderTooltip(graphics, font, Component.literal("§6⌖ ").append(Component.translatable("gui.gtcalcboard.tooltip.target_base")), mouseX, mouseY, screen.width, screen.height);
            return true;
        }
        if (widget.isFlipButtonHovered(canvasMouseX, canvasMouseY)) {
            String flipKey = widget.getNode().isFlipped() ? "gui.gtcalcboard.flip_direction.right_to_left" : "gui.gtcalcboard.flip_direction.left_to_right";
            renderTooltip(graphics, font, Component.literal("§b⇄ ").append(Component.translatable(flipKey)), mouseX, mouseY, screen.width, screen.height);
            return true;
        }
        if (widget.isExpandButtonHovered(canvasMouseX, canvasMouseY)) {
            renderTooltip(graphics, font, Component.literal("§d⤢ ").append(Component.translatable("gui.gtcalcboard.tooltip.expand_module")), mouseX, mouseY, screen.width, screen.height);
            return true;
        }
        if (widget.isSwitchButtonHovered(canvasMouseX, canvasMouseY)) {
            renderTooltip(graphics, font, Component.literal("§e⟲ ").append(Component.translatable("gui.gtcalcboard.switch_recipe.button")), mouseX, mouseY, screen.width, screen.height);
            return true;
        }
        return false;
    }

    private static boolean renderMachineIconTooltip(BoardScreen screen, GuiGraphics graphics, Font font, NodeWidget widget, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
        if (!widget.isMachineIconHovered(canvasMouseX, canvasMouseY)) {
            return false;
        }
        RecipeNode n = widget.getNode();
        List<Component> tooltipLines = new ArrayList<>();
        String mName = n.getMachineDisplayName();
        tooltipLines.add(Component.literal((n.isMultiblock() ? "§e▦ " : "§b⚡ ") + mName));
        tooltipLines.add(Component.literal("§7[Click]: §f" + Component.translatable("gui.gtcalcboard.tooltip.switch_machine_hint").getString()));
        renderComponentTooltip(graphics, font, tooltipLines, mouseX, mouseY, screen.width, screen.height);
        return true;
    }

    private static boolean renderCanvasHandlerTooltips(BoardScreen screen, GuiGraphics graphics, Font font, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
        CanvasInteractionHandler canvasHandler = screen.getCanvasHandler();
        if (canvasHandler == null) return false;

        NodeWidget wireStart = canvasHandler.getWireStartNode();
        if (wireStart != null) {
            renderWireHintTooltip(screen, graphics, font, mouseX, mouseY);
            return true;
        }

        if (mouseY >= screen.getHeaderBottomY() && canvasHandler.hasQuickAddMarker()) {
            double qx = canvasHandler.getQuickAddMarkerCanvasX();
            double qy = canvasHandler.getQuickAddMarkerCanvasY();
            if (QuickAddMarkerTooltipRenderer.renderQuickAddMarkerTooltips(graphics, font, screen, canvasHandler, qx, qy, canvasMouseX, canvasMouseY, mouseX, mouseY)) {
                return true;
            }
        }

        if (mouseY >= screen.getHeaderBottomY()) {
            CanvasGroupFrameRenderer.renderFrameTooltips(graphics, font, screen.getGraph(), canvasMouseX, canvasMouseY, mouseX, mouseY);
            CanvasStickyNoteRenderer.renderNoteTooltips(graphics, font, screen.getGraph(), canvasMouseX, canvasMouseY, mouseX, mouseY);
        }
        return false;
    }

    private static void renderWireHintTooltip(BoardScreen screen, GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        boolean shift = Screen.hasShiftDown();
        String hintKey = shift ? "gui.gtcalcboard.tooltip.wire_mode_shift" : "gui.gtcalcboard.tooltip.wire_mode_normal";
        String raw = Component.translatable(hintKey).getString();
        if (raw.contains("\n")) {
            List<Component> lines = Arrays.stream(raw.split("\n"))
                    .<Component>map(Component::literal)
                    .toList();
            renderComponentTooltip(graphics, font, lines, mouseX, mouseY, screen.width, screen.height);
        } else {
            renderTooltip(graphics, font, Component.translatable(hintKey), mouseX, mouseY, screen.width, screen.height);
        }
    }

    public static String formatPortRate(double rate, IngredientStack stack, boolean showExact, boolean[] hiddenExactRef) {
        if (stack != null && stack.isStressUnit()) {
            String compact = FormatUtil.formatRate(rate, stack);
            String exact = FormatUtil.formatExactRate(rate, stack);
            if (!compact.equals(exact)) {
                if (hiddenExactRef != null && !showExact) {
                    hiddenExactRef[0] = true;
                }
                if (showExact) {
                    return compact + " §8(" + exact + ")";
                }
            }
            return compact;
        }
        return formatPortRate(rate, stack != null && stack.isFluid(), showExact, hiddenExactRef);
    }

    public static String formatPortRate(double rate, boolean isFluid, boolean showExact, boolean[] hiddenExactRef) {
        String compact = NodeCardRenderer.formatRate(rate, isFluid);
        String exact = FormatUtil.formatExactRate(rate, isFluid);
        if (!compact.equals(exact)) {
            if (hiddenExactRef != null && !showExact) {
                hiddenExactRef[0] = true;
            }
            if (showExact) {
                return compact + " §8(" + exact + ")";
            }
        }
        return compact;
    }
}
