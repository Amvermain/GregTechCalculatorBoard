package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
            Bounds bounds = widget.getBounds();
            if (bounds == null || !bounds.contains(localMouseX, localMouseY)) {
                continue;
            }
            if (widget instanceof SlotWidget slot) {
                return slot.getStack();
            }
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
            renderWidget(graphics, widget, localMouseX, localMouseY, partialTick);
        }

        graphics.pose().popPose();
    }

    private void renderWidget(GuiGraphics graphics, Widget widget, int localMouseX, int localMouseY, float partialTick) {
        try {
            widget.render(graphics, localMouseX, localMouseY, partialTick);
            Bounds bounds = widget.getBounds();
            if (widget instanceof SlotWidget && bounds != null && bounds.contains(localMouseX, localMouseY)) {
                graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), 0x55FFFFFF);
            }
        } catch (Throwable ignored) {
            // Safeguard against third-party custom widget render anomalies
        }
    }

    /**
     * Renders tooltips for widgets under the mouse cursor.
     */
    public void renderTooltips(GuiGraphics graphics, Font font, int originX, int originY, int mouseX, int mouseY) {
        int sw = 800;
        int sh = 600;
        com.gtceu.calcboard.client.gui.util.BoardViewportTransform transform = com.gtceu.calcboard.client.gui.BoardScreen.getCurrentTransform();
        if (transform != null && transform.isScaled()) {
            sw = transform.getVirtualWidth();
            sh = transform.getVirtualHeight();
        } else if (graphics != null) {
            sw = graphics.guiWidth();
            sh = graphics.guiHeight();
        }
        renderTooltips(graphics, font, originX, originY, mouseX, mouseY, sw, sh);
    }

    /**
     * Renders tooltips for widgets under the mouse cursor with virtual screen bounds.
     */
    public void renderTooltips(GuiGraphics graphics, Font font, int originX, int originY, int mouseX, int mouseY, int screenW, int screenH) {
        int localMouseX = mouseX - originX;
        int localMouseY = mouseY - originY;

        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget widget = widgets.get(i);
            Bounds bounds = widget.getBounds();
            if (bounds == null || !bounds.contains(localMouseX, localMouseY)) {
                continue;
            }
            List<Component> tooltip = resolveWidgetTooltip(widget);
            if (!tooltip.isEmpty()) {
                BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltip, mouseX, mouseY, screenW, screenH);
                return;
            }
        }
    }

    private List<Component> resolveWidgetTooltip(Widget widget) {
        if (!(widget instanceof SlotWidget slot)) {
            return Collections.emptyList();
        }
        EmiIngredient stack = slot.getStack();
        if (stack == null || stack.isEmpty()) {
            return Collections.emptyList();
        }
        List<EmiStack> emiStacks = stack.getEmiStacks();
        if (emiStacks == null || emiStacks.isEmpty()) {
            return Collections.emptyList();
        }
        return resolveEmiStackTooltip(emiStacks.get(0));
    }

    private List<Component> resolveEmiStackTooltip(EmiStack emiStack) {
        List<Component> tooltip = new ArrayList<>();
        resolveItemStackTooltip(emiStack, tooltip);
        if (!tooltip.isEmpty()) {
            return tooltip;
        }
        resolveEmiTextTooltip(emiStack, tooltip);
        if (!tooltip.isEmpty()) {
            return tooltip;
        }
        resolveFallbackTooltip(emiStack, tooltip);
        return tooltip;
    }

    private void resolveItemStackTooltip(EmiStack emiStack, List<Component> out) {
        ItemStack itemStack = emiStack.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }
        var player = Minecraft.getInstance().player;
        var lines = itemStack.getTooltipLines(player, TooltipFlag.Default.NORMAL);
        if (lines != null && !lines.isEmpty()) {
            out.addAll(lines);
        }
    }

    private void resolveEmiTextTooltip(EmiStack emiStack, List<Component> out) {
        List<Component> text = emiStack.getTooltipText();
        if (text != null && !text.isEmpty()) {
            out.addAll(text);
        }
    }

    private void resolveFallbackTooltip(EmiStack emiStack, List<Component> out) {
        Component name = emiStack.getName();
        if (name != null) {
            out.add(name);
        }
        if (emiStack.getAmount() > 0) {
            out.add(Component.literal("§7" + emiStack.getAmount() + " mB"));
        }
    }
}
