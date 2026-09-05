package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class CanvasContextMenuManager {

    private final BoardScreen screen;
    private boolean open = false;
    private int menuX = 0;
    private int menuY = 0;
    private final List<ContextMenuItem> items = new ArrayList<>();

    public CanvasContextMenuManager(BoardScreen screen) {
        this.screen = screen;
    }

    public record ContextMenuItem(
            String labelKey,
            String icon,
            String shortcut,
            Runnable action,
            boolean isSeparator,
            boolean isDanger
    ) {
        public static ContextMenuItem item(String labelKey, String icon, String shortcut, Runnable action) {
            return new ContextMenuItem(labelKey, icon, shortcut, action, false, false);
        }

        public static ContextMenuItem danger(String labelKey, String icon, String shortcut, Runnable action) {
            return new ContextMenuItem(labelKey, icon, shortcut, action, false, true);
        }

        public static ContextMenuItem separator() {
            return new ContextMenuItem(null, null, null, null, true, false);
        }
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        this.open = false;
        this.items.clear();
    }

    public void openForCanvas(double screenX, double screenY, double canvasX, double canvasY) {
        this.items.clear();
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.add_recipe", "+", "Space", () -> {
            if (screen.getSearchDialog() != null) {
                screen.getSearchDialog().openAt(canvasX, canvasY);
            }
        }));
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.add_junction", "J", "J", () -> {
            screen.addRerouteNodeAt(canvasX, canvasY);
        }));
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.paste", "📋", "Ctrl+V", () -> {
            screen.pasteSelection(canvasX, canvasY);
        }));
        this.items.add(ContextMenuItem.separator());
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.fit_view", "⌖", "Home", screen::fitToView));
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.auto_connect", "↔", "Shift+C", () -> {
            screen.getToolbarWidget().performAutoConnect();
        }));
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.auto_ratio", "⚖", "Alt+R", () -> {
            screen.getToolbarWidget().performAutoRatio(false, false);
        }));

        this.menuX = (int) screenX;
        this.menuY = (int) screenY;
        this.open = true;
    }

    public void openForNode(double screenX, double screenY, NodeWidget widget) {
        if (widget != null && widget.getNode() != null && widget.getNode().isReroute()) {
            openForJunctionNode(screenX, screenY, widget);
            return;
        }

        this.items.clear();
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.inspect_node", "⚙", null, () -> {
            screen.selectNode(widget.getNode().getId(), false);
            screen.openNodeInspector(widget);
        }));
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.switch_recipe", "⟲", null, () -> {
            screen.openRecipeSwitchDialog(widget.getNode());
        }));
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.flip_node", "➔", "F", () -> {
            boolean oldFlipped = widget.getNode().isFlipped();
            widget.getNode().setFlipped(!oldFlipped);
            widget.invalidateCache();
            screen.markSummaryDirty();
        }));
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.toggle_base_anchor", "⌖", null, () -> {
            boolean nowBase = !widget.getNode().isBaseNode();
            screen.getGraph().setBaseNode(nowBase ? widget.getNode() : null);
            screen.rebuildWidgets();
            screen.markSummaryDirty();
        }));
        this.items.add(ContextMenuItem.separator());
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.duplicate_node", "⎘", "Ctrl+D", () -> {
            screen.selectNode(widget.getNode().getId(), false);
            screen.duplicateSelection();
        }));
        this.items.add(ContextMenuItem.danger("gui.gtcalcboard.menu.delete_node", "✕", "Del", () -> {
            screen.removeNode(widget);
        }));

        this.menuX = (int) screenX;
        this.menuY = (int) screenY;
        this.open = true;
    }

    public void openForJunctionNode(double screenX, double screenY, NodeWidget widget) {
        this.items.clear();
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.configure_junction", "⚙", null, () -> {
            screen.openJunctionSupplyDialog(widget.getNode());
        }));
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.inspect_node", "ℹ", null, () -> {
            screen.selectNode(widget.getNode().getId(), false);
            screen.openNodeInspector(widget);
        }));
        if (widget.getNode().hasTargetBatch()) {
            this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.reset_target_batch", "↺", null, () -> {
                widget.getNode().setTargetBatchAmount(0.0);
                widget.getTargetBatchEditor().updateBuffer();
                widget.invalidateCache();
                screen.markSummaryDirty();
            }));
        }
        this.items.add(ContextMenuItem.separator());
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.duplicate_node", "⎘", "Ctrl+D", () -> {
            screen.selectNode(widget.getNode().getId(), false);
            screen.duplicateSelection();
        }));
        this.items.add(ContextMenuItem.danger("gui.gtcalcboard.menu.delete_node", "✕", "Del", () -> {
            screen.removeNode(widget);
        }));

        this.menuX = (int) screenX;
        this.menuY = (int) screenY;
        this.open = true;
    }

    public void openForSelection(double screenX, double screenY) {
        this.items.clear();
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.create_frame", "▤", "Ctrl+G", screen::createFrameFromSelection));
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.group_module", "📦", "Ctrl+Shift+G", screen::performGroupIntoModule));
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.shared_frame", "⧉", "Ctrl+Shift+S", screen::createSharedMachineFrameFromSelection));
        this.items.add(ContextMenuItem.separator());
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.copy_selection", "📋", "Ctrl+C", screen::copySelection));
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.duplicate_selection", "⎘", "Ctrl+D", screen::duplicateSelection));
        this.items.add(ContextMenuItem.danger("gui.gtcalcboard.menu.delete_selection", "✕", "Del", screen::deleteSelection));

        this.menuX = (int) screenX;
        this.menuY = (int) screenY;
        this.open = true;
    }

    public void openForPort(double screenX, double screenY, NodeWidget widget, boolean isInput, int portIndex) {
        this.items.clear();
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.hide_port", "👁", "R-Click", () -> {
            widget.hidePortAndDisconnectWires(isInput, portIndex);
        }));
        if (!isInput) {
            boolean isVoided = widget.getNode().isOutputPortVoided(portIndex);
            String voidLabelKey = isVoided ? "gui.gtcalcboard.menu.unvoid_port" : "gui.gtcalcboard.menu.void_port";
            this.items.add(ContextMenuItem.item(voidLabelKey, "🗑", "Shift+R-Click", () -> {
                widget.toggleOutputPortVoid(portIndex);
            }));
            this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.target_rate", "⚡", "Ctrl+L-Click", () -> {
                screen.openTargetOutputRateDialog(widget.getNode(), portIndex);
            }));
        }
        this.items.add(ContextMenuItem.item("gui.gtcalcboard.menu.cycle_alternative", "🔄", "Wheel", () -> {
            if (isInput && portIndex >= 0 && portIndex < widget.getNode().getInputs().size()) {
                var in = widget.getNode().getInputs().get(portIndex);
                if (in.hasAlternatives()) {
                    in.cycleAlternative(1);
                    widget.invalidateCache();
                    screen.markSummaryDirty();
                }
            }
        }));

        this.menuX = (int) screenX;
        this.menuY = (int) screenY;
        this.open = true;
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!open || items.isEmpty()) return;

        int menuW = 160;
        int menuH = 6;
        for (ContextMenuItem it : items) {
            menuH += it.isSeparator ? 5 : 18;
        }

        int mx = Math.min(menuX, screen.width - menuW - 8);
        int my = Math.min(menuY, screen.height - menuH - 8);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 450.0f);

        graphics.fill(mx, my, mx + menuW, my + menuH, 0xF5111827);
        graphics.renderOutline(mx, my, menuW, menuH, 0xFF374151);

        int curY = my + 3;
        for (ContextMenuItem it : items) {
            if (it.isSeparator) {
                graphics.fill(mx + 4, curY + 2, mx + menuW - 4, curY + 3, 0xFF1F2937);
                curY += 5;
                continue;
            }

            boolean hovered = mouseX >= mx + 2 && mouseX <= mx + menuW - 2 && mouseY >= curY && mouseY <= curY + 16;
            if (hovered) {
                int hoverBg = it.isDanger ? 0xFF5A1C1C : 0xFF1E293B;
                graphics.fill(mx + 2, curY, mx + menuW - 2, curY + 16, hoverBg);
            }

            int iconCol = it.isDanger ? 0xFFEF4444 : 0xFF38BDF8;
            graphics.drawString(font, it.icon, mx + 6, curY + 4, iconCol, false);

            String label = Component.translatable(it.labelKey).getString();
            int labelCol = hovered ? 0xFFFFFFFF : (it.isDanger ? 0xFFFCA5A5 : 0xFFE2E8F0);
            graphics.drawString(font, label, mx + 18, curY + 4, labelCol, false);

            if (it.shortcut != null) {
                int scW = font.width(it.shortcut);
                graphics.drawString(font, it.shortcut, mx + menuW - scW - 6, curY + 4, 0xFF64748B, false);
            }

            curY += 18;
        }

        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;

        int menuW = 160;
        int menuH = calculateMenuHeight();
        int mx = Math.min(menuX, screen.width - menuW - 8);
        int my = Math.min(menuY, screen.height - menuH - 8);

        if (mouseX < mx || mouseX > mx + menuW || mouseY < my || mouseY > my + menuH) {
            close();
            return false;
        }

        if (button != 0) {
            return true;
        }

        return handleItemClick(mouseY, my);
    }

    private int calculateMenuHeight() {
        int menuH = 6;
        for (ContextMenuItem it : items) {
            menuH += it.isSeparator ? 5 : 18;
        }
        return menuH;
    }

    private boolean handleItemClick(double mouseY, int my) {
        int curY = my + 3;
        for (ContextMenuItem it : items) {
            if (it.isSeparator) {
                curY += 5;
                continue;
            }
            if (mouseY >= curY && mouseY <= curY + 16) {
                Runnable act = it.action;
                close();
                if (act != null) {
                    act.run();
                }
                return true;
            }
            curY += 18;
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }
}
