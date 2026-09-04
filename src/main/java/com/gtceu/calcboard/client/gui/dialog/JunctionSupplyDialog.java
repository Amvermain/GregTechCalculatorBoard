package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowBalanceMatrixSolver;
import com.gtceu.calcboard.api.type.SupplyMode;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modal dialog for configuring external supply mode, priority flow allocation,
 * and batch accumulation buffer for Junction/Reroute nodes (RFC-020).
 */
public class JunctionSupplyDialog {

    private final BoardScreen parent;
    private RecipeNode targetNode;
    private boolean visible = false;

    private int activeTab = 0;
    private SupplyMode selectedMode = SupplyMode.NONE;
    private EditBox rateEditBox;

    private boolean isBuffer = false;
    private EditBox bufferSizeEditBox;
    private int outgoingScrollOffset = 0;
    private final List<FlowGraph.ConnectionEdge> outgoingEdges = new ArrayList<>();
    private final Map<FlowGraph.ConnectionEdge, EditBox> edgeLimitEditBoxes = new LinkedHashMap<>();

    private static final int DIALOG_WIDTH = 340;
    private static final int DIALOG_HEIGHT = 250;

    public JunctionSupplyDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public void open(RecipeNode node) {
        if (node == null || !node.isReroute()) return;
        this.targetNode = node;
        this.selectedMode = node.getSupplyMode();
        this.isBuffer = node.isJunctionBuffer();
        this.activeTab = 0;
        this.outgoingScrollOffset = 0;
        this.visible = true;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc != null ? mc.font : null;
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;

        if (font != null) {
            int editBoxX = x + DIALOG_WIDTH - 85;
            int editBoxY = y + 74 + SupplyMode.FIXED_RATE.ordinal() * 18;
            this.rateEditBox = new EditBox(font, editBoxX, editBoxY, 75, 14, Component.translatable("gui.gtcalcboard.junction.supply_rate"));
            this.rateEditBox.setMaxLength(16);
            this.rateEditBox.setValue(node.getExternalSupplyRate() > 0 ? String.format("%.2f", node.getExternalSupplyRate()) : "100.0");

            this.bufferSizeEditBox = new EditBox(font, x + DIALOG_WIDTH - 90, y + 84, 75, 14, Component.translatable("gui.gtcalcboard.junction.buffer_size"));
            this.bufferSizeEditBox.setMaxLength(16);
            double curBufSize = node.getJunctionBufferSize();
            this.bufferSizeEditBox.setValue(curBufSize > 0.0 ? String.format("%.2f", curBufSize) : "500.0");
        }

        initOutgoingEdges(font, x, y);
    }

    private void initOutgoingEdges(Font font, int x, int y) {
        this.outgoingEdges.clear();
        this.edgeLimitEditBoxes.clear();
        FlowGraph graph = parent != null ? parent.getGraph() : null;
        if (graph == null || targetNode == null) return;

        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(targetNode.getId())) {
                this.outgoingEdges.add(edge);
            }
        }

        if (font != null) {
            for (int i = 0; i < outgoingEdges.size(); i++) {
                FlowGraph.ConnectionEdge edge = outgoingEdges.get(i);
                EditBox eb = new EditBox(font, x + DIALOG_WIDTH - 85, -1000, 70, 14, Component.translatable("gui.gtcalcboard.junction.fixed_limit"));
                eb.setMaxLength(16);
                eb.setValue(edge.hasFixedLimit() ? String.format("%.2f", edge.fixedFlowLimit()) : "0.0");
                edgeLimitEditBoxes.put(edge, eb);
            }
        }
    }

    public void close() {
        this.visible = false;
        this.targetNode = null;
        this.outgoingScrollOffset = 0;
        this.edgeLimitEditBoxes.clear();
        this.outgoingEdges.clear();
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible || targetNode == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;
        Font font = mc.font;
        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;

        graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);
        graphics.fill(x, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xFF181A22);
        graphics.renderOutline(x, y, DIALOG_WIDTH, DIALOG_HEIGHT, 0xFF4F5B73);
        graphics.fill(x + 1, y + 1, x + DIALOG_WIDTH - 1, y + 20, 0xFF232734);

        renderHeader(graphics, font, x, y, mouseX, mouseY);
        renderTabs(graphics, font, x, y, mouseX, mouseY);

        if (activeTab == 0) {
            renderSupplyModeTab(graphics, font, x, y, mouseX, mouseY);
        } else {
            renderAllocationTab(graphics, font, x, y, mouseX, mouseY);
        }

        renderFooterButtons(graphics, font, x, y, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        String title = "↔ " + Component.translatable("gui.gtcalcboard.junction.dialog_title").getString();
        graphics.drawString(font, title, x + 8, y + 6, 0xFFE0E6F0, false);

        int closeX = x + DIALOG_WIDTH - 18;
        int closeY = y + 4;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= closeY && mouseY <= closeY + 14;
        graphics.fill(closeX, closeY, closeX + 14, closeY + 14, closeHover ? 0xFF882222 : 0xFF442222);
        graphics.drawCenteredString(font, "✕", closeX + 7, closeY + 3, 0xFFFFFFFF);

        IngredientStack boundStack = targetNode.getRerouteIngredient();
        int previewY = y + 24;
        if (boundStack != null) {
            IngredientRenderer.render(graphics, boundStack, x + 10, previewY);
            String boundText = "§f" + boundStack.getDisplayName();
            graphics.drawString(font, font.plainSubstrByWidth(boundText, DIALOG_WIDTH - 36), x + 32, previewY + 4, 0xFFFFFFFF, false);
        } else {
            graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.junction.no_bound_ingredient").getString(), x + 10, previewY + 4, 0xFF888888, false);
        }
    }

    private void renderTabs(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int tabY = y + 46;
        int tabW = (DIALOG_WIDTH - 24) / 2;

        renderSingleTab(graphics, font, x + 10, tabY, tabW, 0, "gui.gtcalcboard.junction.tab_supply_mode", mouseX, mouseY);
        renderSingleTab(graphics, font, x + 14 + tabW, tabY, tabW, 1, "gui.gtcalcboard.junction.tab_flow_allocation", mouseX, mouseY);
    }

    private void renderSingleTab(GuiGraphics graphics, Font font, int tx, int ty, int tw, int tabIdx, String key, int mouseX, int mouseY) {
        boolean selected = (activeTab == tabIdx);
        boolean hover = mouseX >= tx && mouseX <= tx + tw && mouseY >= ty && mouseY <= ty + 16;
        int bg = selected ? 0xFF283548 : (hover ? 0xFF202634 : 0xFF181C26);
        int border = selected ? 0xFF38BDF8 : (hover ? 0xFF64748B : 0xFF334155);

        graphics.fill(tx, ty, tx + tw, ty + 16, bg);
        graphics.renderOutline(tx, ty, tw, 16, border);
        int textCol = selected ? 0xFF7DD3FC : (hover ? 0xFFE2E8F0 : 0xFF94A3B8);
        graphics.drawCenteredString(font, Component.translatable(key).getString(), tx + tw / 2, ty + 4, textCol);
    }

    private void renderSupplyModeTab(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int optStartY = y + 72;
        int optH = 20;

        SupplyMode[] modes = SupplyMode.values();
        for (int i = 0; i < modes.length; i++) {
            SupplyMode mode = modes[i];
            int optY = optStartY + i * optH;
            boolean isSelected = (selectedMode == mode);
            boolean isHover = mouseX >= x + 10 && mouseX <= x + DIALOG_WIDTH - 10 && mouseY >= optY && mouseY <= optY + 16;

            int radioX = x + 14;
            int radioY = optY + 2;
            graphics.fill(radioX, radioY, radioX + 10, radioY + 10, isSelected ? 0xFF38BDF8 : (isHover ? 0xFF35445C : 0xFF232B3A));
            graphics.renderOutline(radioX, radioY, 10, 10, isSelected ? 0xFF7DD3FC : 0xFF475569);

            String modeLabel = Component.translatable(mode.getTranslationKey()).getString();
            int textColor = isSelected ? 0xFFFFFFFF : (isHover ? 0xFFCBD5E1 : 0xFF94A3B8);
            graphics.drawString(font, modeLabel, x + 30, optY + 3, textColor, false);

            if (mode == SupplyMode.FIXED_RATE && isSelected) {
                rateEditBox.setX(x + DIALOG_WIDTH - 85);
                rateEditBox.setY(optY);
                rateEditBox.render(graphics, mouseX, mouseY, 0);
            }
        }
    }

    private void renderAllocationTab(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        FlowGraph graph = parent != null ? parent.getGraph() : null;
        double inflow = (graph != null && targetNode != null)
                ? FlowBalanceMatrixSolver.getEffectiveProducerOutputRate(graph, targetNode, 0)
                : 0.0;
        IngredientStack rStack = targetNode.getRerouteIngredient();
        String inflowStr = FormatUtil.formatRate(inflow, rStack);
        graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.junction.total_inflow", "§b" + inflowStr).getString(), x + 14, y + 68, 0xFFE2E8F0, false);

        renderBufferToggle(graphics, font, x, y, inflow, mouseX, mouseY);
        renderOutgoingList(graphics, font, x, y, mouseX, mouseY);
    }

    private void renderBufferToggle(GuiGraphics graphics, Font font, int x, int y, double inflow, int mouseX, int mouseY) {
        int toggleY = y + 84;
        int radioX = x + 14;
        int radioY = toggleY + 1;

        graphics.fill(radioX, radioY, radioX + 10, radioY + 10, isBuffer ? 0xFF38BDF8 : 0xFF232B3A);
        graphics.renderOutline(radioX, radioY, 10, 10, isBuffer ? 0xFF7DD3FC : 0xFF475569);
        String label = Component.translatable(isBuffer ? "gui.gtcalcboard.junction.mode_buffer" : "gui.gtcalcboard.junction.mode_passthrough").getString();
        graphics.drawString(font, label, x + 30, toggleY + 2, isBuffer ? 0xFFFFFFFF : 0xFF94A3B8, false);

        if (isBuffer) {
            bufferSizeEditBox.setX(x + DIALOG_WIDTH - 90);
            bufferSizeEditBox.setY(toggleY - 1);
            bufferSizeEditBox.render(graphics, mouseX, mouseY, 0);

            double bufSize = parseDoubleSafe(bufferSizeEditBox.getValue());
            double chargeDur = (inflow > 0.0001 && bufSize > 0.0) ? bufSize / inflow : 0.0;
            String durStr = String.format("%.2fs", chargeDur);
            graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.junction.charge_duration", "§e" + durStr).getString(), x + 14, y + 103, 0xFFCBD5E1, false);
        }
    }

    private void renderOutgoingList(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int listHeaderY = y + 120;
        graphics.drawString(font, "§6── " + Component.translatable("gui.gtcalcboard.junction.port_allocation").getString() + " ──", x + 14, listHeaderY, 0xFFD97706, false);

        int maxScroll = Math.max(0, outgoingEdges.size() - 3);
        if (maxScroll > 0) {
            String pageStr = String.format("§7(%d/%d)", outgoingScrollOffset + 1, maxScroll + 1);
            graphics.drawString(font, pageStr, x + DIALOG_WIDTH - 55, listHeaderY, 0xFF94A3B8, false);
        }

        for (EditBox eb : edgeLimitEditBoxes.values()) {
            eb.setY(-1000);
        }

        int listStartY = y + 135;
        if (outgoingEdges.isEmpty()) {
            graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.junction.no_outgoing").getString(), x + 14, listStartY + 4, 0xFF64748B, false);
            return;
        }

        FlowGraph graph = parent != null ? parent.getGraph() : null;
        int visibleCount = Math.min(3, outgoingEdges.size() - outgoingScrollOffset);
        for (int i = 0; i < visibleCount; i++) {
            FlowGraph.ConnectionEdge edge = outgoingEdges.get(outgoingScrollOffset + i);
            int rowY = listStartY + i * 22;
            RecipeNode toNode = graph != null ? graph.findNodeById(edge.toNodeId()) : null;
            String toName = (toNode != null && toNode.getName() != null && !toNode.getName().isBlank()) ? toNode.getName() : edge.toNodeId();
            String portLabel = String.format("#%d → %s", edge.outputIndex() + 1, toName);
            graphics.drawString(font, font.plainSubstrByWidth(portLabel, DIALOG_WIDTH - 110), x + 14, rowY + 3, 0xFFE2E8F0, false);

            EditBox eb = edgeLimitEditBoxes.get(edge);
            if (eb != null) {
                eb.setX(x + DIALOG_WIDTH - 85);
                eb.setY(rowY);
                eb.render(graphics, mouseX, mouseY, 0);
            }
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible || activeTab != 1) return false;
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;
        if (mouseX >= x && mouseX <= x + DIALOG_WIDTH && mouseY >= y && mouseY <= y + DIALOG_HEIGHT) {
            int maxScroll = Math.max(0, outgoingEdges.size() - 3);
            if (maxScroll > 0) {
                int oldOffset = outgoingScrollOffset;
                outgoingScrollOffset = Math.max(0, Math.min(maxScroll, outgoingScrollOffset - (int) Math.signum(delta)));
                return outgoingScrollOffset != oldOffset;
            }
        }
        return false;
    }

    private void renderFooterButtons(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int btnW = 75;
        int btnH = 18;
        int btnY = y + DIALOG_HEIGHT - 24;

        int cancelBtnX = x + DIALOG_WIDTH - (btnW * 2) - 14;
        boolean cancelHover = mouseX >= cancelBtnX && mouseX <= cancelBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(cancelBtnX, btnY, cancelBtnX + btnW, btnY + btnH, cancelHover ? 0xFF475569 : 0xFF334155);
        graphics.renderOutline(cancelBtnX, btnY, btnW, btnH, 0xFF64748B);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.cancel_btn").getString(), cancelBtnX + btnW / 2, btnY + 5, 0xFFFFFFFF);

        int applyBtnX = x + DIALOG_WIDTH - btnW - 8;
        boolean applyHover = mouseX >= applyBtnX && mouseX <= applyBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(applyBtnX, btnY, applyBtnX + btnW, btnY + btnH, applyHover ? 0xFF2A6840 : 0xFF1E4D2F);
        graphics.renderOutline(applyBtnX, btnY, btnW, btnH, 0xFF359050);
        graphics.drawCenteredString(font, "✔ " + Component.translatable("gui.gtcalcboard.apply_btn").getString(), applyBtnX + btnW / 2, btnY + 5, 0xFFFFFFFF);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || targetNode == null) return false;

        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;

        if (checkCloseClicked(x, y, mouseX, mouseY)) {
            close();
            return true;
        }

        if (checkTabClicked(x, y, mouseX, mouseY)) {
            return true;
        }

        if (activeTab == 0) {
            if (selectedMode == SupplyMode.FIXED_RATE && checkEditBoxClicked(rateEditBox, mouseX, mouseY, button)) return true;
            if (checkRadioSelection(x, y, mouseX, mouseY)) return true;
        } else {
            if (isBuffer && checkEditBoxClicked(bufferSizeEditBox, mouseX, mouseY, button)) return true;
            if (checkBufferToggleClicked(x, y, mouseX, mouseY)) return true;
            int visibleCount = Math.min(3, outgoingEdges.size() - outgoingScrollOffset);
            for (int i = 0; i < visibleCount; i++) {
                FlowGraph.ConnectionEdge edge = outgoingEdges.get(outgoingScrollOffset + i);
                EditBox eb = edgeLimitEditBoxes.get(edge);
                if (eb != null && checkEditBoxClicked(eb, mouseX, mouseY, button)) return true;
            }
        }

        return checkFooterButtons(x, y, mouseX, mouseY);
    }

    private boolean checkTabClicked(int x, int y, double mouseX, double mouseY) {
        int tabY = y + 46;
        int tabW = (DIALOG_WIDTH - 24) / 2;
        if (mouseY >= tabY && mouseY <= tabY + 16) {
            if (mouseX >= x + 10 && mouseX <= x + 10 + tabW) {
                activeTab = 0;
                playClickSound();
                return true;
            }
            if (mouseX >= x + 14 + tabW && mouseX <= x + 14 + (tabW * 2)) {
                activeTab = 1;
                playClickSound();
                return true;
            }
        }
        return false;
    }

    private boolean checkBufferToggleClicked(int x, int y, double mouseX, double mouseY) {
        int toggleY = y + 84;
        if (mouseY >= toggleY && mouseY <= toggleY + 16 && mouseX >= x + 10 && mouseX <= x + 200) {
            isBuffer = !isBuffer;
            playClickSound();
            return true;
        }
        return false;
    }

    private boolean checkCloseClicked(int x, int y, double mouseX, double mouseY) {
        int closeX = x + DIALOG_WIDTH - 18;
        int closeY = y + 4;
        return mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= closeY && mouseY <= closeY + 14;
    }

    private boolean checkEditBoxClicked(EditBox box, double mouseX, double mouseY, int button) {
        if (box == null) return false;
        boolean over = mouseX >= box.getX() && mouseX <= box.getX() + box.getWidth()
                && mouseY >= box.getY() && mouseY <= box.getY() + box.getHeight();
        box.setFocused(over);
        if (over) {
            box.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return false;
    }

    private boolean checkRadioSelection(int x, int y, double mouseX, double mouseY) {
        int optStartY = y + 72;
        int optH = 20;
        SupplyMode[] modes = SupplyMode.values();
        for (int i = 0; i < modes.length; i++) {
            SupplyMode mode = modes[i];
            int optY = optStartY + i * optH;
            int rightBound = (mode == SupplyMode.FIXED_RATE && selectedMode == mode && rateEditBox != null)
                    ? rateEditBox.getX() - 4
                    : x + DIALOG_WIDTH - 10;

            if (mouseX >= x + 10 && mouseX <= rightBound && mouseY >= optY && mouseY <= optY + 16) {
                this.selectedMode = mode;
                if (rateEditBox != null) {
                    rateEditBox.setFocused(mode == SupplyMode.FIXED_RATE);
                }
                playClickSound();
                return true;
            }
        }
        return false;
    }

    private boolean checkFooterButtons(int x, int y, double mouseX, double mouseY) {
        int btnW = 75;
        int btnH = 18;
        int btnY = y + DIALOG_HEIGHT - 24;
        int cancelBtnX = x + DIALOG_WIDTH - (btnW * 2) - 14;
        int applyBtnX = x + DIALOG_WIDTH - btnW - 8;

        if (mouseX >= cancelBtnX && mouseX <= cancelBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            close();
            return true;
        }

        if (mouseX >= applyBtnX && mouseX <= applyBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            applyChanges();
            return true;
        }
        return false;
    }

    private void applyChanges() {
        if (targetNode == null) return;
        FlowGraph graph = parent != null ? parent.getGraph() : null;

        targetNode.setSupplyMode(selectedMode);
        if (selectedMode == SupplyMode.FIXED_RATE && rateEditBox != null) {
            targetNode.setExternalSupplyRate(Math.max(0.0, parseDoubleSafe(rateEditBox.getValue())));
        }

        targetNode.setJunctionBuffer(isBuffer);
        if (bufferSizeEditBox != null) {
            targetNode.setJunctionBufferSize(Math.max(0.0, parseDoubleSafe(bufferSizeEditBox.getValue())));
        }

        if (graph != null) {
            for (Map.Entry<FlowGraph.ConnectionEdge, EditBox> entry : edgeLimitEditBoxes.entrySet()) {
                FlowGraph.ConnectionEdge edge = entry.getKey();
                double limit = Math.max(0.0, parseDoubleSafe(entry.getValue().getValue()));
                graph.setConnectionFixedLimit(edge.fromNodeId(), edge.outputIndex(), edge.toNodeId(), edge.inputIndex(), limit);
            }
        }

        if (parent != null) {
            parent.markSummaryDirty();
            parent.rebuildWidgets();
        }

        playClickSound();
        close();
    }

    private double parseDoubleSafe(String s) {
        if (s == null || s.isBlank()) return 0.0;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private void playClickSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            applyChanges();
            return true;
        }

        if (activeTab == 0 && rateEditBox != null && rateEditBox.isFocused()) {
            return rateEditBox.keyPressed(keyCode, scanCode, modifiers);
        }
        if (activeTab == 1) {
            if (bufferSizeEditBox != null && bufferSizeEditBox.isFocused()) {
                return bufferSizeEditBox.keyPressed(keyCode, scanCode, modifiers);
            }
            for (EditBox eb : edgeLimitEditBoxes.values()) {
                if (eb.isFocused()) {
                    return eb.keyPressed(keyCode, scanCode, modifiers);
                }
            }
        }
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (activeTab == 0 && rateEditBox != null && rateEditBox.isFocused()) {
            return rateEditBox.charTyped(codePoint, modifiers);
        }
        if (activeTab == 1) {
            if (bufferSizeEditBox != null && bufferSizeEditBox.isFocused()) {
                return bufferSizeEditBox.charTyped(codePoint, modifiers);
            }
            for (EditBox eb : edgeLimitEditBoxes.values()) {
                if (eb.isFocused()) {
                    return eb.charTyped(codePoint, modifiers);
                }
            }
        }
        return false;
    }

    private int getScreenWidth() {
        if (parent != null && parent.width > 0) return parent.width;
        Minecraft mc = Minecraft.getInstance();
        return (mc != null && mc.getWindow() != null) ? mc.getWindow().getGuiScaledWidth() : 800;
    }

    private int getScreenHeight() {
        if (parent != null && parent.height > 0) return parent.height;
        Minecraft mc = Minecraft.getInstance();
        return (mc != null && mc.getWindow() != null) ? mc.getWindow().getGuiScaledHeight() : 600;
    }
}
