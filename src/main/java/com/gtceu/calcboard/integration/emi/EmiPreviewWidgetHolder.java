package com.gtceu.calcboard.integration.emi;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Lightweight in-memory WidgetHolder implementation for rendering floating EMI recipe previews.
 * Supports rendering interactive tooltips on hover and ingredient querying.
 */
public class EmiPreviewWidgetHolder implements WidgetHolder {
    private final int width;
    private final int height;
    private final List<Widget> widgets = new ArrayList<>();

    public EmiPreviewWidgetHolder(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public <T extends Widget> T add(T widget) {
        if (widget != null) {
            widgets.add(widget);
        }
        return widget;
    }

    public List<Widget> getWidgets() {
        return Collections.unmodifiableList(widgets);
    }

    /**
     * Finds the hovered EMI ingredient stack under mouse cursor.
     */
    public EmiIngredient getHoveredIngredient(int originX, int originY, int mouseX, int mouseY) {
        int localMouseX = mouseX - originX;
        int localMouseY = mouseY - originY;

        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget widget = widgets.get(i);
            try {
                var bounds = widget.getBounds();
                if (bounds != null && bounds.contains(localMouseX, localMouseY)) {
                    if (widget instanceof SlotWidget slot) {
                        return slot.getStack();
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Renders all captured EMI widgets at the specified screen position.
     */
    public void render(GuiGraphics graphics, int originX, int originY, int mouseX, int mouseY, float partialTick) {
        int localMouseX = mouseX - originX;
        int localMouseY = mouseY - originY;

        graphics.pose().pushPose();
        graphics.pose().translate(originX, originY, 0);

        for (Widget widget : widgets) {
            try {
                widget.render(graphics, localMouseX, localMouseY, partialTick);
            } catch (Throwable ignored) {
                // Safeguard against third-party custom widget render anomalies
            }
        }

        graphics.pose().popPose();
    }

    /**
     * Renders tooltips for widgets under the mouse cursor.
     */
    public void renderTooltips(GuiGraphics graphics, Font font, int originX, int originY, int mouseX, int mouseY) {
        int localMouseX = mouseX - originX;
        int localMouseY = mouseY - originY;

        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget widget = widgets.get(i);
            try {
                var bounds = widget.getBounds();
                if (bounds != null && bounds.contains(localMouseX, localMouseY)) {
                    if (widget instanceof SlotWidget slot) {
                        var stack = slot.getStack();
                        if (stack != null && !stack.isEmpty()) {
                            var emiStacks = stack.getEmiStacks();
                            if (emiStacks != null && !emiStacks.isEmpty()) {
                                List<Component> text = emiStacks.get(0).getTooltipText();
                                if (text != null && !text.isEmpty()) {
                                    graphics.renderComponentTooltip(font, text, mouseX, mouseY);
                                    return;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }
}
