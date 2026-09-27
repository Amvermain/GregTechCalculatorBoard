package com.gtceu.calcboard.client.gui.inspector.section;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.editor.NodeCountEditor;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.client.gui.widget.NodeWidgetInteractionHandler;
import com.gtceu.calcboard.client.util.ClientSafetyHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class MachineCountSection implements IInspectorSection {

    private NodeWidget widget;
    private RecipeNode node;
    private IBoardScreenContext screen;

    @Override
    public void bind(NodeWidget widget, RecipeNode node, IBoardScreenContext screen) {
        this.widget = widget;
        this.node = node;
        this.screen = screen;
    }

    @Override
    public boolean isApplicable(RecipeNode node) {
        return node != null;
    }

    @Override
    public int getHeight(RecipeNode node) {
        return 34;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        if (node == null) return;

        String countLabel = Component.translatable("gui.gtcalcboard.inspector.count").getString();
        graphics.drawString(font, countLabel, x, y, 0xFF94A3B8, false);

        int ctrlY = y + 12;
        renderMinusButton(graphics, font, x, ctrlY, mouseX, mouseY);
        renderCountBox(graphics, font, x + 18, ctrlY);
        int plusX = x + 18 + 56 + 2;
        renderPlusButton(graphics, font, plusX, ctrlY, mouseX, mouseY);
        int halfX = plusX + 18;
        renderHalfButton(graphics, font, halfX, ctrlY, mouseX, mouseY);
        int doubleX = halfX + 24;
        renderDoubleButton(graphics, font, doubleX, ctrlY, mouseX, mouseY);
        int anchorX = doubleX + 24;
        renderAnchorButton(graphics, font, anchorX, ctrlY, mouseX, mouseY);
    }

    private void renderMinusButton(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX <= x + 16 && mouseY >= y && mouseY <= y + 16;
        graphics.fill(x, y, x + 16, y + 16, hov ? 0xFF334155 : 0xFF1E293B);
        graphics.renderOutline(x, y, 16, 16, hov ? 0xFF64748B : 0xFF334155);
        graphics.drawCenteredString(font, "-", x + 8, y + 4, hov ? 0xFFFFFFFF : 0xFFCBD5E1);
    }

    private void renderCountBox(GuiGraphics graphics, Font font, int x, int y) {
        graphics.fill(x, y, x + 56, y + 16, 0xFF0F172A);
        graphics.renderOutline(x, y, 56, 16, 0xFF38BDF8);
        String countStr = (widget != null && widget.getCountEditor() != null && widget.getCountEditor().isEditing())
                ? widget.getCountEditor().getDisplayText()
                : NodeCountEditor.formatCount(node.getMachineCount());
        graphics.drawCenteredString(font, countStr, x + 28, y + 4, 0xFFFCD34D);
    }

    private void renderPlusButton(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX <= x + 16 && mouseY >= y && mouseY <= y + 16;
        graphics.fill(x, y, x + 16, y + 16, hov ? 0xFF334155 : 0xFF1E293B);
        graphics.renderOutline(x, y, 16, 16, hov ? 0xFF64748B : 0xFF334155);
        graphics.drawCenteredString(font, "+", x + 8, y + 4, hov ? 0xFFFFFFFF : 0xFFCBD5E1);
    }

    private void renderHalfButton(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX <= x + 22 && mouseY >= y && mouseY <= y + 16;
        graphics.fill(x, y, x + 22, y + 16, hov ? 0xFF334155 : 0xFF1E293B);
        graphics.renderOutline(x, y, 22, 16, hov ? 0xFF64748B : 0xFF334155);
        graphics.drawCenteredString(font, "/2", x + 11, y + 4, hov ? 0xFFFFFFFF : 0xFF94A3B8);
    }

    private void renderDoubleButton(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX <= x + 22 && mouseY >= y && mouseY <= y + 16;
        graphics.fill(x, y, x + 22, y + 16, hov ? 0xFF334155 : 0xFF1E293B);
        graphics.renderOutline(x, y, 22, 16, hov ? 0xFF64748B : 0xFF334155);
        graphics.drawCenteredString(font, "x2", x + 11, y + 4, hov ? 0xFFFFFFFF : 0xFF94A3B8);
    }

    private void renderAnchorButton(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        boolean isBase = node.isBaseNode();
        boolean hov = mouseX >= x && mouseX <= x + 18 && mouseY >= y && mouseY <= y + 16;
        int bg = isBase ? 0xFF78350F : (hov ? 0xFF334155 : 0xFF1E293B);
        int border = isBase ? 0xFFF59E0B : (hov ? 0xFF64748B : 0xFF334155);
        graphics.fill(x, y, x + 18, y + 16, bg);
        graphics.renderOutline(x, y, 18, 16, border);
        graphics.drawCenteredString(font, "⌖", x + 9, y + 4, isBase ? 0xFFFDE68A : 0xFF94A3B8);
    }

    @Override
    public boolean mouseClicked(int x, int y, int w, double mouseX, double mouseY, int button) {
        if (button != 0 || node == null || widget == null) return false;

        int ctrlY = y + 12;
        if (mouseX >= x && mouseX <= x + 16 && mouseY >= ctrlY && mouseY <= ctrlY + 16) {
            return NodeWidgetInteractionHandler.adjustMachineCount(widget, screen, node, -1, ClientSafetyHelper.isShiftDown());
        }

        int boxX = x + 18;
        if (mouseX >= boxX && mouseX <= boxX + 56 && mouseY >= ctrlY && mouseY <= ctrlY + 16) {
            if (widget.getCountEditor() != null) {
                if (!widget.getCountEditor().isEditing()) {
                    widget.getCountEditor().startEditing();
                } else {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.font != null) {
                        widget.getCountEditor().onClick(mc.font, mouseX, boxX + 2, ClientSafetyHelper.isShiftDown());
                    }
                }
            }
            return true;
        }

        int plusX = boxX + 56 + 2;
        if (mouseX >= plusX && mouseX <= plusX + 16 && mouseY >= ctrlY && mouseY <= ctrlY + 16) {
            return NodeWidgetInteractionHandler.adjustMachineCount(widget, screen, node, 1, ClientSafetyHelper.isShiftDown());
        }

        int halfX = plusX + 18;
        if (mouseX >= halfX && mouseX <= halfX + 22 && mouseY >= ctrlY && mouseY <= ctrlY + 16) {
            return NodeWidgetInteractionHandler.scaleMachineCount(widget, screen, node, 0.5);
        }

        int doubleX = halfX + 24;
        if (mouseX >= doubleX && mouseX <= doubleX + 22 && mouseY >= ctrlY && mouseY <= ctrlY + 16) {
            return NodeWidgetInteractionHandler.scaleMachineCount(widget, screen, node, 2.0);
        }

        int anchorX = doubleX + 24;
        if (mouseX >= anchorX && mouseX <= anchorX + 18 && mouseY >= ctrlY && mouseY <= ctrlY + 16) {
            return NodeWidgetInteractionHandler.handleTargetBaseToggle(screen, node);
        }
        return false;
    }
}
