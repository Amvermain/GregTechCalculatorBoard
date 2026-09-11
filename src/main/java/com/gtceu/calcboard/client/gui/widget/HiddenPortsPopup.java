package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Dropdown popup modal displaying hidden input/output ports for a RecipeNode,
 * allowing selective unhiding upon clicking.
 */
public class HiddenPortsPopup {

    public record HiddenPortEntry(boolean isInput, int portIndex, IngredientStack stack) {}

    private final NodeWidget widget;
    private boolean visible = false;

    public HiddenPortsPopup(NodeWidget widget) {
        this.widget = widget;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void toggle() {
        this.visible = !this.visible;
    }

    public void close() {
        this.visible = false;
    }

    public boolean isPointInside(double mouseX, double mouseY) {
        if (!visible) return false;
        int px = getPopupX();
        int py = getPopupY();
        int pw = getPopupWidth();
        int ph = getPopupHeight();
        return mouseX >= px && mouseX <= px + pw && mouseY >= py && mouseY <= py + ph;
    }

    public List<HiddenPortEntry> getHiddenPorts() {
        List<HiddenPortEntry> list = new ArrayList<>();
        RecipeNode node = widget.getNode();
        for (int idx : node.getHiddenInputIndices()) {
            if (idx >= 0 && idx < node.getInputs().size()) {
                list.add(new HiddenPortEntry(true, idx, node.getInputs().get(idx)));
            }
        }
        for (int idx : node.getHiddenOutputIndices()) {
            if (idx >= 0 && idx < node.getOutputs().size()) {
                list.add(new HiddenPortEntry(false, idx, node.getOutputs().get(idx)));
            }
        }
        return list;
    }

    public int getPopupWidth() {
        return 170;
    }

    public int getPopupHeight() {
        List<HiddenPortEntry> list = getHiddenPorts();
        return 22 + Math.max(1, list.size()) * 20 + 4;
    }

    public int getPopupX() {
        int x = (int) widget.getNode().getPosX();
        int w = widget.getWidth();
        return x + w - getPopupWidth() - 4;
    }

    public int getPopupY() {
        int y = (int) widget.getNode().getPosY();
        int h = widget.getHeight();
        return y + h + 2;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!visible) return;
        List<HiddenPortEntry> ports = getHiddenPorts();
        if (ports.isEmpty()) {
            visible = false;
            return;
        }

        int px = getPopupX();
        int py = getPopupY();
        int pw = getPopupWidth();
        int ph = getPopupHeight();

        // Background
        graphics.fill(px, py, px + pw, py + ph, 0xF0101726);
        graphics.renderOutline(px, py, pw, ph, 0xFF4A90E2);

        Font font = Minecraft.getInstance().font;
        String title = Component.translatable("gui.gtcalcboard.hidden_port_popup_title").getString() + " (" + ports.size() + ")";
        graphics.drawString(font, title, px + 6, py + 6, 0xFF55FFFF, false);

        int rowY = py + 20;
        for (int i = 0; i < ports.size(); i++) {
            HiddenPortEntry entry = ports.get(i);
            boolean isHover = mouseX >= px + 2 && mouseX <= px + pw - 2 && mouseY >= rowY && mouseY <= rowY + 18;

            if (isHover) {
                graphics.fill(px + 3, rowY, px + pw - 3, rowY + 18, 0x553388FF);
                graphics.renderOutline(px + 3, rowY, pw - 6, 18, 0xFF66BBFF);
            }

            // [IN] or [OUT] badge
            String ioTag = entry.isInput ? "§9[IN]" : "§a[OUT]";
            graphics.drawString(font, ioTag, px + 6, rowY + 5, 0xFFFFFFFF, false);

            // Icon
            IngredientRenderer.render(graphics, entry.stack, px + 40, rowY + 1);

            // Item/Fluid name
            String name = entry.stack.getDisplayName();
            int maxChars = 14;
            if (name.length() > maxChars) {
                name = name.substring(0, maxChars - 1) + "…";
            }
            graphics.drawString(font, name, px + 60, rowY + 5, isHover ? 0xFFFFFF55 : 0xFFE0E0E0, false);

            // Unhide button / icon [+]
            graphics.drawString(font, "§a●", px + pw - 14, rowY + 5, 0xFF55FF55, false);

            rowY += 20;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        if (button != 0) return false;

        List<HiddenPortEntry> ports = getHiddenPorts();
        if (ports.isEmpty()) {
            visible = false;
            return false;
        }

        int px = getPopupX();
        int py = getPopupY();
        int pw = getPopupWidth();
        int ph = getPopupHeight();

        if (mouseX < px || mouseX > px + pw || mouseY < py || mouseY > py + ph) {
            visible = false;
            return false;
        }

        int rowY = py + 20;
        for (int i = 0; i < ports.size(); i++) {
            HiddenPortEntry entry = ports.get(i);
            if (mouseX >= px + 2 && mouseX <= px + pw - 2 && mouseY >= rowY && mouseY <= rowY + 18) {
                RecipeNode node = widget.getNode();
                if (entry.isInput) {
                    node.unhideInputPort(entry.portIndex);
                } else {
                    node.unhideOutputPort(entry.portIndex);
                }
                widget.invalidateCache();
                if (widget.getParent() != null) {
                    widget.getParent().markSummaryDirty();
                }
                if (node.getTotalHiddenCount() <= 0) {
                    visible = false;
                }
                return true;
            }
            rowY += 20;
        }

        return true;
    }
}
