package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowBalanceMatrixSolver;
import com.gtceu.calcboard.api.solver.FlowEdgeAllocator;
import com.gtceu.calcboard.api.type.FlowSplitMode;
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

import com.gtceu.calcboard.client.gui.dialog.modal.IBoardModal;
import com.gtceu.calcboard.client.gui.dialog.modal.ModalRenderContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modal dialog for configuring external supply mode, priority flow allocation,
 * and batch accumulation buffer for Junction/Reroute nodes (RFC-020).
 */
public class JunctionSupplyDialog implements IBoardModal {

    private final BoardScreen parent;
    private RecipeNode targetNode;
    private boolean visible = false;

    private int activeTab = 0;
    private SupplyMode selectedMode = SupplyMode.NONE;
    private FlowSplitMode splitMode = FlowSplitMode.PROPORTIONAL;
    private EditBox rateEditBox;
    private boolean isAnchor = false;

    private boolean isBuffer = false;
    private EditBox bufferSizeEditBox;
    private int outgoingScrollOffset = 0;
    private final List<FlowGraph.ConnectionEdge> outgoingEdges = new ArrayList<>();
    private final Map<FlowGraph.ConnectionEdge, EditBox> edgeLimitEditBoxes = new LinkedHashMap<>();
    private final Map<FlowGraph.ConnectionEdge, EditBox> edgePriorityEditBoxes = new LinkedHashMap<>();

    private static final int DIALOG_WIDTH = 340;
    private static final int DIALOG_HEIGHT = 250;

    public JunctionSupplyDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public void open(RecipeNode node) {
        if (node == null || !node.isReroute()) return;
        this.targetNode = node;
        this.selectedMode = node.getSupplyMode();
        this.splitMode = node.getJunctionSplitMode();
        this.isBuffer = node.isJunctionBuffer();
        this.isAnchor = node.isBaseNode();
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
            double curRate = (selectedMode == SupplyMode.FIXED_DRAIN) ? node.getExternalDrainRate() : node.getExternalSupplyRate();
            this.rateEditBox.setValue(curRate > 0 ? formatRateForEditBox(curRate) : "100.0");

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
        this.edgePriorityEditBoxes.clear();
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
                EditBox eb = new EditBox(font, x + 226, -1000, DIALOG_WIDTH - 240, 14, Component.translatable("gui.gtcalcboard.junction.fixed_limit"));
                eb.setMaxLength(16);
                eb.setValue(edge.hasFixedLimit() ? String.format(java.util.Locale.ROOT, "%.2f", edge.fixedFlowLimit()) : "0.0");
                edgeLimitEditBoxes.put(edge, eb);

                EditBox ebPri = new EditBox(font, x + 168, -1000, 28, 14, Component.translatable("gui.gtcalcboard.junction.priority_label"));
                ebPri.setMaxLength(6);
                ebPri.setValue(String.valueOf(edge.priority()));
                edgePriorityEditBoxes.put(edge, ebPri);
            }
        }
    }

    public void close() {
        this.visible = false;
        this.targetNode = null;
        this.outgoingScrollOffset = 0;
        this.edgeLimitEditBoxes.clear();
        this.edgePriorityEditBoxes.clear();
        this.outgoingEdges.clear();
    }

    public boolean isVisible() {
        return visible;
    }

    @Override
    public void renderModal(ModalRenderContext context) {
        render(context.graphics(), context.screenWidth(), context.screenHeight(), context.mouseX(), context.mouseY());
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

            if ((mode == SupplyMode.FIXED_RATE || mode == SupplyMode.FIXED_DRAIN) && isSelected) {
                renderRateEditBoxWithMatchButton(graphics, font, x, optY, mode, mouseX, mouseY);
            }
        }

        if (selectedMode == SupplyMode.FIXED_RATE || selectedMode == SupplyMode.FIXED_DRAIN) {
            renderAnchorCheckbox(graphics, font, x, y + 178, mouseX, mouseY);
        }
    }

    private void renderRateEditBoxWithMatchButton(GuiGraphics graphics, Font font, int x, int optY, SupplyMode mode, int mouseX, int mouseY) {
        int boxW = 56;
        int btnW = 16;
        rateEditBox.setX(x + DIALOG_WIDTH - 24 - boxW - 4);
        rateEditBox.setWidth(boxW);
        rateEditBox.setY(optY);
        rateEditBox.render(graphics, mouseX, mouseY, 0);

        int matchX = x + DIALOG_WIDTH - 24;
        int matchY = optY;
        boolean matchHover = mouseX >= matchX && mouseX <= matchX + btnW && mouseY >= matchY && mouseY <= matchY + 14;
        graphics.fill(matchX, matchY, matchX + btnW, matchY + 14, matchHover ? 0xFF0284C7 : 0xFF0369A1);
        graphics.renderOutline(matchX, matchY, btnW, 14, matchHover ? 0xFF38BDF8 : 0xFF0284C7);
        graphics.drawString(font, "⚡", matchX + 4, matchY + 3, 0xFFFFFFFF, false);

        if (matchHover) {
            double rate = (mode == SupplyMode.FIXED_RATE) ? calculateConnectedDownstreamDemand() : calculateConnectedUpstreamInflow();
            IngredientStack rStack = targetNode.getRerouteIngredient();
            boolean isFluid = rStack != null && rStack.isFluid();
            String formatted = FormatUtil.formatRate(rate, isFluid);
            Component tip = Component.translatable(
                    mode == SupplyMode.FIXED_RATE
                            ? "gui.gtcalcboard.junction.match_demand_tooltip"
                            : "gui.gtcalcboard.junction.match_inflow_tooltip",
                    formatted
            );
            graphics.renderTooltip(font, tip, mouseX, mouseY);
        }
    }

    private void renderAnchorCheckbox(GuiGraphics graphics, Font font, int x, int anchorY, int mouseX, int mouseY) {
        int cbX = x + 14;
        int cbY = anchorY + 2;
        boolean isHover = mouseX >= x + 10 && mouseX <= x + DIALOG_WIDTH - 10 && mouseY >= anchorY && mouseY <= anchorY + 16;
        graphics.fill(cbX, cbY, cbX + 10, cbY + 10, isAnchor ? 0xFF886600 : (isHover ? 0xFF35445C : 0xFF232B3A));
        graphics.renderOutline(cbX, cbY, 10, 10, isAnchor ? 0xFFFFD700 : 0xFF475569);
        if (isAnchor) {
            graphics.drawString(font, "✔", cbX + 2, cbY + 1, 0xFFFFEE55, false);
        }
        String label = "⌖ " + Component.translatable("gui.gtcalcboard.junction.anchor_checkbox").getString();
        int textColor = isAnchor ? 0xFFFFD700 : (isHover ? 0xFFCBD5E1 : 0xFF94A3B8);
        graphics.drawString(font, label, x + 30, anchorY + 3, textColor, false);

        if (isHover) {
            Component tip = Component.translatable("gui.gtcalcboard.junction.anchor_tooltip");
            graphics.renderTooltip(font, tip, mouseX, mouseY);
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
        renderSplitModeSelector(graphics, font, x, y, mouseX, mouseY);
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
            String durStr = String.format(java.util.Locale.ROOT, "%.2fs", chargeDur);
            graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.junction.charge_duration", "§e" + durStr).getString(), x + 175, y + 68, 0xFFCBD5E1, false);
        }
    }

    private void renderSplitModeSelector(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int modeY = y + 102;
        String modeLabel = Component.translatable("gui.gtcalcboard.junction.split_mode_label").getString();
        graphics.drawString(font, modeLabel, x + 14, modeY + 3, 0xFFCBD5E1, false);

        renderSplitModeButton(graphics, font, x + 75, modeY, 122, FlowSplitMode.PROPORTIONAL, "gui.gtcalcboard.junction.split_mode.proportional", "gui.gtcalcboard.junction.proportional_tooltip", mouseX, mouseY);
        renderSplitModeButton(graphics, font, x + 203, modeY, 122, FlowSplitMode.EQUAL, "gui.gtcalcboard.junction.split_mode.equal", "gui.gtcalcboard.junction.equal_tooltip", mouseX, mouseY);
    }

    private void renderSplitModeButton(
            GuiGraphics graphics,
            Font font,
            int btnX,
            int btnY,
            int btnW,
            FlowSplitMode mode,
            String textKey,
            String tipKey,
            int mouseX,
            int mouseY
    ) {
        boolean selected = (this.splitMode == mode);
        boolean hover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + 14;
        int bg = selected ? 0xFF0284C7 : (hover ? 0xFF334155 : 0xFF1E293B);
        int border = selected ? 0xFF38BDF8 : (hover ? 0xFF64748B : 0xFF475569);
        graphics.fill(btnX, btnY, btnX + btnW, btnY + 14, bg);
        graphics.renderOutline(btnX, btnY, btnW, 14, border);

        int textCol = selected ? 0xFFFFFFFF : (hover ? 0xFFF1F5F9 : 0xFF94A3B8);
        graphics.drawCenteredString(font, Component.translatable(textKey).getString(), btnX + btnW / 2, btnY + 3, textCol);

        if (hover) {
            graphics.renderTooltip(font, Component.translatable(tipKey), mouseX, mouseY);
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
        for (EditBox eb : edgePriorityEditBoxes.values()) {
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

            EditBox ebLimit = edgeLimitEditBoxes.get(edge);
            EditBox ebPri = edgePriorityEditBoxes.get(edge);

            graphics.drawString(font, font.plainSubstrByWidth(portLabel, 135), x + 14, rowY + 3, 0xFFE2E8F0, false);

            graphics.drawString(font, "P:", x + 155, rowY + 3, 0xFF94A3B8, false);
            if (ebPri != null) {
                ebPri.setX(x + 168);
                ebPri.setWidth(28);
                ebPri.setY(rowY);
                ebPri.render(graphics, mouseX, mouseY, 0);
            }

            graphics.drawString(font, "Cap:", x + 202, rowY + 3, 0xFF94A3B8, false);
            if (ebLimit != null) {
                ebLimit.setX(x + 226);
                ebLimit.setWidth(DIALOG_WIDTH - 240);
                ebLimit.setY(rowY);
                ebLimit.render(graphics, mouseX, mouseY, 0);
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
            boolean isRateMode = (selectedMode == SupplyMode.FIXED_RATE || selectedMode == SupplyMode.FIXED_DRAIN);
            if (isRateMode) {
                if (checkEditBoxClicked(rateEditBox, mouseX, mouseY, button)) return true;
                if (checkMatchFlowClicked(x, y, mouseX, mouseY)) return true;
                if (checkAnchorCheckboxClicked(x, y, mouseX, mouseY)) return true;
            }
            if (checkRadioSelection(x, y, mouseX, mouseY)) return true;
        } else {
            if (isBuffer && checkEditBoxClicked(bufferSizeEditBox, mouseX, mouseY, button)) return true;
            if (checkBufferToggleClicked(x, y, mouseX, mouseY)) return true;
            if (checkSplitModeClicked(x, y, mouseX, mouseY)) return true;
            int visibleCount = Math.min(3, outgoingEdges.size() - outgoingScrollOffset);
            for (int i = 0; i < visibleCount; i++) {
                FlowGraph.ConnectionEdge edge = outgoingEdges.get(outgoingScrollOffset + i);
                EditBox eb = edgeLimitEditBoxes.get(edge);
                if (eb != null && checkEditBoxClicked(eb, mouseX, mouseY, button)) return true;
                EditBox ebPri = edgePriorityEditBoxes.get(edge);
                if (ebPri != null && checkEditBoxClicked(ebPri, mouseX, mouseY, button)) return true;
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
            if (mouseX >= x + 14 + tabW && mouseX <= x + 14 + tabW * 2) {
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

    private boolean checkSplitModeClicked(int x, int y, double mouseX, double mouseY) {
        int modeY = y + 102;
        if (mouseY < modeY || mouseY > modeY + 14) return false;

        if (mouseX >= x + 75 && mouseX <= x + 197) {
            this.splitMode = FlowSplitMode.PROPORTIONAL;
            playClickSound();
            return true;
        }
        if (mouseX >= x + 203 && mouseX <= x + 325) {
            this.splitMode = FlowSplitMode.EQUAL;
            playClickSound();
            return true;
        }
        return false;
    }

    private boolean checkMatchFlowClicked(int x, int y, double mouseX, double mouseY) {
        int optStartY = y + 72;
        int optH = 20;
        int modeIdx = selectedMode.ordinal();
        int optY = optStartY + modeIdx * optH;
        int matchX = x + DIALOG_WIDTH - 24;
        int matchY = optY;
        if (mouseX >= matchX && mouseX <= matchX + 16 && mouseY >= matchY && mouseY <= matchY + 14) {
            double rate = (selectedMode == SupplyMode.FIXED_RATE)
                    ? calculateConnectedDownstreamDemand()
                    : calculateConnectedUpstreamInflow();
            rateEditBox.setValue(formatRateForEditBox(rate));
            playClickSound();
            return true;
        }
        return false;
    }

    private boolean checkAnchorCheckboxClicked(int x, int y, double mouseX, double mouseY) {
        int anchorY = y + 178;
        if (mouseX >= x + 10 && mouseX <= x + DIALOG_WIDTH - 10 && mouseY >= anchorY && mouseY <= anchorY + 16) {
            this.isAnchor = !isAnchor;
            playClickSound();
            return true;
        }
        return false;
    }

    private double calculateConnectedDownstreamDemand() {
        FlowGraph graph = parent != null ? parent.getGraph() : null;
        if (graph == null || targetNode == null) return 0.0;
        double totalDeficit = 0.0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (!edge.fromNodeId().equals(targetNode.getId()) || edge.outputIndex() != 0) continue;
            RecipeNode consumer = graph.findNodeById(edge.toNodeId());
            if (consumer == null) continue;
            double demand = FlowEdgeAllocator.getConnectedConsumerDemand(graph, consumer, edge.inputIndex());
            double otherSupply = calculateOtherProducersSupply(graph, consumer.getId(), edge.inputIndex(), targetNode.getId());
            totalDeficit += Math.max(0.0, demand - otherSupply);
        }
        return totalDeficit;
    }

    private static double calculateOtherProducersSupply(FlowGraph graph, String consumerId, int inputIndex, String excludeNodeId) {
        double otherSupply = 0.0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (!edge.toNodeId().equals(consumerId) || edge.inputIndex() != inputIndex) continue;
            if (edge.fromNodeId().equals(excludeNodeId)) continue;
            otherSupply += FlowEdgeAllocator.getEdgeAllocatedFlow(graph, edge, null);
        }
        return otherSupply;
    }

    private double calculateConnectedUpstreamInflow() {
        FlowGraph graph = parent != null ? parent.getGraph() : null;
        if (graph == null || targetNode == null) return 0.0;
        double totalAvailable = 0.0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (!edge.toNodeId().equals(targetNode.getId()) || edge.inputIndex() != 0) continue;
            RecipeNode producer = graph.findNodeById(edge.fromNodeId());
            if (producer == null) continue;
            double prodRate = FlowEdgeAllocator.getEffectiveProducerOutputRate(graph, producer, edge.outputIndex());
            double otherDemand = calculateOtherConsumersDemand(graph, producer.getId(), edge.outputIndex(), targetNode.getId());
            totalAvailable += Math.max(0.0, prodRate - otherDemand);
        }
        return totalAvailable;
    }

    private static double calculateOtherConsumersDemand(FlowGraph graph, String producerId, int outputIndex, String excludeNodeId) {
        double otherDemand = 0.0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (!edge.fromNodeId().equals(producerId) || edge.outputIndex() != outputIndex) continue;
            if (edge.toNodeId().equals(excludeNodeId)) continue;
            RecipeNode consumer = graph.findNodeById(edge.toNodeId());
            if (consumer != null) {
                otherDemand += FlowEdgeAllocator.getConnectedConsumerDemand(graph, consumer, edge.inputIndex());
            }
        }
        return otherDemand;
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
            boolean hasRateBox = (mode == SupplyMode.FIXED_RATE || mode == SupplyMode.FIXED_DRAIN)
                    && selectedMode == mode && rateEditBox != null;
            int rightBound = hasRateBox ? rateEditBox.getX() - 4 : x + DIALOG_WIDTH - 10;
            if (mouseX < x + 10 || mouseX > rightBound || mouseY < optY || mouseY > optY + 16) continue;

            selectMode(mode);
            playClickSound();
            return true;
        }
        return false;
    }

    private void selectMode(SupplyMode mode) {
        this.selectedMode = mode;
        if (rateEditBox == null) return;
        boolean isRateMode = (mode == SupplyMode.FIXED_RATE || mode == SupplyMode.FIXED_DRAIN);
        rateEditBox.setFocused(isRateMode);
        if (isRateMode && targetNode != null) {
            double cur = (mode == SupplyMode.FIXED_DRAIN) ? targetNode.getExternalDrainRate() : targetNode.getExternalSupplyRate();
            if (cur > 0.0) {
                rateEditBox.setValue(formatRateForEditBox(cur));
            }
        }
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
        targetNode.setJunctionSplitMode(splitMode);
        if (selectedMode == SupplyMode.FIXED_RATE && rateEditBox != null) {
            targetNode.setExternalSupplyRate(Math.max(0.0, parseDoubleSafe(rateEditBox.getValue())));
        } else if (selectedMode == SupplyMode.FIXED_DRAIN && rateEditBox != null) {
            targetNode.setExternalDrainRate(Math.max(0.0, parseDoubleSafe(rateEditBox.getValue())));
        }

        targetNode.setJunctionBuffer(isBuffer);
        if (bufferSizeEditBox != null) {
            targetNode.setJunctionBufferSize(Math.max(0.0, parseDoubleSafe(bufferSizeEditBox.getValue())));
        }

        if (graph != null) {
            boolean isRateMode = (selectedMode == SupplyMode.FIXED_RATE || selectedMode == SupplyMode.FIXED_DRAIN);
            if (isAnchor && isRateMode) {
                graph.setBaseNode(targetNode);
            } else if (targetNode.isBaseNode()) {
                graph.setBaseNode(null);
            }

            for (Map.Entry<FlowGraph.ConnectionEdge, EditBox> entry : edgeLimitEditBoxes.entrySet()) {
                FlowGraph.ConnectionEdge edge = entry.getKey();
                double limit = Math.max(0.0, parseDoubleSafe(entry.getValue().getValue()));
                graph.setConnectionFixedLimit(edge.fromNodeId(), edge.outputIndex(), edge.toNodeId(), edge.inputIndex(), limit);
                EditBox priBox = edgePriorityEditBoxes.get(edge);
                int pri = priBox != null ? Math.max(0, Math.min(99, parseIntSafe(priBox.getValue()))) : edge.priority();
                graph.setConnectionPriority(edge.fromNodeId(), edge.outputIndex(), edge.toNodeId(), edge.inputIndex(), pri);
            }
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new com.gtceu.calcboard.api.event.FlowGraphEvent.JunctionConfigured(graph, targetNode, selectedMode)
            );
        }

        if (parent != null) {
            parent.markSummaryDirty();
            parent.rebuildWidgets();
        }

        playClickSound();
        close();
    }

    private static String formatRateForEditBox(double rate) {
        if (rate <= 0.0) return "0.0";
        if (rate >= 100.0) {
            return String.format(java.util.Locale.ROOT, "%.2f", rate).replaceAll("\\.?0+$", "");
        } else if (rate >= 1.0) {
            return String.format(java.util.Locale.ROOT, "%.3f", rate).replaceAll("\\.?0+$", "");
        } else {
            return String.format(java.util.Locale.ROOT, "%.4f", rate).replaceAll("\\.?0+$", "");
        }
    }

    private double parseDoubleSafe(String s) {
        if (s == null || s.isBlank()) return 0.0;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private int parseIntSafe(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ignored) {
            return 0;
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
            for (EditBox eb : edgePriorityEditBoxes.values()) {
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
            for (EditBox eb : edgePriorityEditBoxes.values()) {
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
