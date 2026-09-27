package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowBalanceMatrixSolver;
import com.gtceu.calcboard.api.solver.FlowEdgeAllocator;
import com.gtceu.calcboard.api.type.FlowSplitMode;
import com.gtceu.calcboard.api.type.RateTimeUnit;
import com.gtceu.calcboard.api.type.SupplyMode;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.client.gui.util.TargetRateParser;
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

import com.gtceu.calcboard.api.model.CrossPageExportTarget;
import com.gtceu.calcboard.api.solver.WorkspaceFlowCoordinator;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final Map<FlowGraph.ConnectionEdge, EditBox> edgeWeightEditBoxes = new LinkedHashMap<>();

    private String linkedSourcePageId;
    private String linkedSourceNodeId;
    private final List<CrossPageExportTarget> exportTargets = new ArrayList<>();
    private final Map<CrossPageExportTarget, EditBox> targetPriorityEditBoxes = new LinkedHashMap<>();
    private final Map<CrossPageExportTarget, EditBox> targetLimitEditBoxes = new LinkedHashMap<>();

    private static final int DIALOG_WIDTH = 340;
    private static final int DIALOG_HEIGHT = 250;
    private static final int SRC_SELECTOR_LEFT_X = 104;
    private static final int SRC_SELECTOR_RIGHT_X = 224;

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

        int editBoxX = x + DIALOG_WIDTH - 85;
        int editBoxY = y + 74 + SupplyMode.FIXED_RATE.ordinal() * 18;
        this.rateEditBox = new EditBox(font, editBoxX, editBoxY, 75, 14, Component.translatable("gui.gtcalcboard.junction.supply_rate"));
        this.rateEditBox.setMaxLength(16);
        double curRate = (selectedMode == SupplyMode.FIXED_DRAIN) ? node.getExternalDrainRate() : node.getExternalSupplyRate();
        RateTimeUnit timeUnit = FormatUtil.getActiveTimeUnit();
        double factor = (timeUnit != null && !timeUnit.isRecipeBatchMode()) ? timeUnit.getFactor() : 1.0;
        this.rateEditBox.setValue(curRate > 0 ? formatRateForEditBox(curRate * factor) : "100.0");

        this.bufferSizeEditBox = new EditBox(font, x + DIALOG_WIDTH - 90, y + 84, 75, 14, Component.translatable("gui.gtcalcboard.junction.buffer_size"));
        this.bufferSizeEditBox.setMaxLength(16);
        double curBufSize = node.getJunctionBufferSize();
        this.bufferSizeEditBox.setValue(curBufSize > 0.0 ? String.format("%.2f", curBufSize) : "500.0");

        initOutgoingEdges(font, x, y);
        this.linkedSourcePageId = node.getLinkedSourcePageId();
        this.linkedSourceNodeId = node.getLinkedSourceNodeId();
        this.exportTargets.clear();
        this.exportTargets.addAll(node.getExportTargets());
        initExportTargets(font, x, y);
    }

    private void initExportTargets(Font font, int x, int y) {
        this.targetPriorityEditBoxes.clear();
        this.targetLimitEditBoxes.clear();
        if (font == null) return;
        for (CrossPageExportTarget target : exportTargets) {
            EditBox ebPri = new EditBox(font, x + 115, -1000, 24, 14, Component.translatable("gui.gtcalcboard.junction.priority_label"));
            ebPri.setMaxLength(6);
            ebPri.setValue(String.valueOf(target.priority()));
            targetPriorityEditBoxes.put(target, ebPri);

            EditBox ebLimit = new EditBox(font, x + 172, -1000, DIALOG_WIDTH - 210, 14, Component.translatable("gui.gtcalcboard.junction.fixed_limit"));
            ebLimit.setMaxLength(16);
            ebLimit.setValue(target.hasLimit() ? String.format(java.util.Locale.ROOT, "%.2f", target.fixedLimit()) : "0.0");
            targetLimitEditBoxes.put(target, ebLimit);
        }
    }

    private void initOutgoingEdges(Font font, int x, int y) {
        this.outgoingEdges.clear();
        this.edgeLimitEditBoxes.clear();
        this.edgePriorityEditBoxes.clear();
        this.edgeWeightEditBoxes.clear();
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

                EditBox ebWeight = new EditBox(font, x + 158, -1000, 34, 14, Component.translatable("gui.gtcalcboard.junction.weight_label"));
                ebWeight.setMaxLength(8);
                ebWeight.setValue(formatWeightForEditBox(edge.weight()));
                edgeWeightEditBoxes.put(edge, ebWeight);
            }
        }
    }

    public void close() {
        this.visible = false;
        this.targetNode = null;
        this.outgoingScrollOffset = 0;
        this.edgeLimitEditBoxes.clear();
        this.edgePriorityEditBoxes.clear();
        this.edgeWeightEditBoxes.clear();
        this.outgoingEdges.clear();
        this.exportTargets.clear();
        this.targetPriorityEditBoxes.clear();
        this.targetLimitEditBoxes.clear();
        this.linkedSourcePageId = null;
        this.linkedSourceNodeId = null;
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
        int optStartY = y + 68;
        int optH = 17;

        SupplyMode[] modes = SupplyMode.values();
        for (int i = 0; i < modes.length; i++) {
            SupplyMode mode = modes[i];
            int optY = optStartY + i * optH;
            boolean isSelected = (selectedMode == mode);
            boolean isHover = mouseX >= x + 10 && mouseX <= x + DIALOG_WIDTH - 10 && mouseY >= optY && mouseY <= optY + 14;

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
            renderAnchorCheckbox(graphics, font, x, y + 176, mouseX, mouseY);
        } else if (selectedMode == SupplyMode.LINKED_JUNCTION) {
            renderLinkedSourceSelectors(graphics, font, x, y + 174, mouseX, mouseY);
        }
    }

    private void renderRateEditBoxWithMatchButton(GuiGraphics graphics, Font font, int x, int optY, SupplyMode mode, int mouseX, int mouseY) {
        int btnW = 16;
        int matchX = x + DIALOG_WIDTH - 24;
        int matchY = optY;

        String unitLabel = getRateUnitLabel();
        int unitW = font.width(unitLabel);
        int unitX = matchX - 4 - unitW;

        int boxW = 56;
        int boxX = unitX - 4 - boxW;
        rateEditBox.setX(boxX);
        rateEditBox.setWidth(boxW);
        rateEditBox.setY(optY);
        rateEditBox.render(graphics, mouseX, mouseY, 0);

        graphics.drawString(font, unitLabel, unitX, optY + 3, 0xFF94A3B8, false);

        boolean matchHover = mouseX >= matchX && mouseX <= matchX + btnW && mouseY >= matchY && mouseY <= matchY + 14;
        graphics.fill(matchX, matchY, matchX + btnW, matchY + 14, matchHover ? 0xFF0284C7 : 0xFF0369A1);
        graphics.renderOutline(matchX, matchY, btnW, 14, matchHover ? 0xFF38BDF8 : 0xFF0284C7);
        graphics.drawString(font, "⚡", matchX + 4, matchY + 3, 0xFFFFFFFF, false);

        boolean unitHover = mouseX >= unitX && mouseX <= unitX + unitW && mouseY >= optY && mouseY <= optY + 14;
        if (unitHover) {
            Component tip = Component.translatable("gui.gtcalcboard.junction.supply_rate_unit_tooltip");
            graphics.renderTooltip(font, tip, mouseX, mouseY);
        } else if (matchHover) {
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

    private String getRateUnitLabel() {
        IngredientStack stack = (targetNode != null) ? targetNode.getRerouteIngredient() : null;
        if (stack != null && stack.isStressUnit()) {
            return "SU";
        }
        RateTimeUnit timeUnit = FormatUtil.getActiveTimeUnit();
        String suffix = timeUnit.getSuffix();
        if (stack != null && stack.isFluid()) {
            com.gtceu.calcboard.api.type.FluidUnitMode fluidMode = FormatUtil.getActiveFluidUnitMode();
            return (fluidMode == com.gtceu.calcboard.api.type.FluidUnitMode.ALWAYS_B ? "B" : "mB") + suffix;
        }
        return suffix;
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

    private void renderLinkedSourceSelectors(GuiGraphics graphics, Font font, int x, int startY, int mouseX, int mouseY) {
        List<BoardPage> pages = getCandidatePages();
        BoardPage curPage = findCurrentSelectedPage(pages);
        List<RecipeNode> junctions = getCandidateJunctions(curPage);
        RecipeNode curJunction = findCurrentSelectedJunction(junctions);

        int row1Y = startY + 2;
        String pageLabel = Component.translatable("gui.gtcalcboard.junction.source_page_label").getString() + ":";
        graphics.drawString(font, pageLabel, x + 14, row1Y + 3, 0xFF94A3B8, false);

        int pBtnLeftX = x + SRC_SELECTOR_LEFT_X;
        int pBtnRightX = x + SRC_SELECTOR_RIGHT_X;
        int pBoxW = pBtnRightX - (pBtnLeftX + 16) - 4;
        renderNavArrow(graphics, font, pBtnLeftX, row1Y, "◀", mouseX, mouseY);
        String pName = curPage != null ? (curPage.getName() != null && !curPage.getName().isEmpty() ? curPage.getName() : curPage.getId()) : "None";
        renderSelectorBox(graphics, font, pBtnLeftX + 16, row1Y, pBoxW, pName);
        renderNavArrow(graphics, font, pBtnRightX, row1Y, "▶", mouseX, mouseY);

        renderJunctionRowAndRate(graphics, font, x, startY + 20, pBtnLeftX, pBtnRightX, pBoxW, curPage, curJunction, mouseX, mouseY);
    }

    private void renderJunctionRowAndRate(GuiGraphics graphics, Font font, int x, int row2Y, int pBtnLeftX, int pBtnRightX, int pBoxW, BoardPage curPage, RecipeNode curJunction, int mouseX, int mouseY) {
        String nodeLabel = Component.translatable("gui.gtcalcboard.junction.source_junction_label").getString() + ":";
        graphics.drawString(font, nodeLabel, x + 14, row2Y + 3, 0xFF94A3B8, false);

        renderNavArrow(graphics, font, pBtnLeftX, row2Y, "◀", mouseX, mouseY);
        String jName = curJunction != null ? (curJunction.getName() != null && !curJunction.getName().isEmpty() ? curJunction.getName() : curJunction.getId()) : "None";
        renderSelectorBox(graphics, font, pBtnLeftX + 16, row2Y, pBoxW, jName);
        renderNavArrow(graphics, font, pBtnRightX, row2Y, "▶", mouseX, mouseY);

        renderSourceJunctionStatus(graphics, font, pBtnRightX + 18, row2Y + 3, curPage, curJunction, mouseX, mouseY);
    }

    private void renderSourceJunctionStatus(GuiGraphics graphics, Font font, int sx, int sy, BoardPage page, RecipeNode junction, int mouseX, int mouseY) {
        if (page == null || junction == null || page.getGraph() == null) {
            graphics.drawString(font, "§7--", sx, sy, 0xFF94A3B8, false);
            return;
        }

        IngredientStack rStack = junction.getRerouteIngredient();
        WorkspaceFlowCoordinator.SourceJunctionMetrics metrics = WorkspaceFlowCoordinator.calculateSourceJunctionMetrics(page, junction);
        double supply = metrics.totalProduction();
        double demand = metrics.totalUsage();
        double net = supply - demand;

        String statusText;
        int statusColor;
        if (Math.abs(net) < 0.0001 && supply < 0.0001 && demand < 0.0001) {
            statusText = "0/s";
            statusColor = 0xFF94A3B8;
        } else if (net > 0.0001) {
            statusText = "+" + FormatUtil.formatRate(net, rStack);
            statusColor = 0xFF34D399;
        } else if (net < -0.0001) {
            statusText = "-" + FormatUtil.formatRate(-net, rStack);
            statusColor = 0xFFF87171;
        } else {
            statusText = "✔ " + FormatUtil.formatRate(supply, rStack);
            statusColor = 0xFFFBBF24;
        }

        graphics.drawString(font, statusText, sx, sy, statusColor, false);

        int textW = font.width(statusText);
        if (mouseX >= sx && mouseX <= sx + textW && mouseY >= sy - 2 && mouseY <= sy + 12) {
            renderSourceJunctionTooltip(graphics, font, junction, metrics, rStack, mouseX, mouseY);
        }
    }

    private void renderSourceJunctionTooltip(
            GuiGraphics graphics,
            Font font,
            RecipeNode junction,
            WorkspaceFlowCoordinator.SourceJunctionMetrics metrics,
            IngredientStack rStack,
            int mouseX,
            int mouseY
    ) {
        List<Component> tipLines = new ArrayList<>();
        String name = junction.getName() != null && !junction.getName().isEmpty() ? junction.getName() : junction.getId();
        tipLines.add(Component.literal("§6" + name));

        String prodStr = Double.isInfinite(metrics.totalProduction()) ? "∞" : ("+" + FormatUtil.formatRate(metrics.totalProduction(), rStack));
        String usageStr = "-" + FormatUtil.formatRate(metrics.totalUsage(), rStack);
        String surplusStr = metrics.availableSurplus() > 0.0001 ? ("§a+" + FormatUtil.formatRate(metrics.availableSurplus(), rStack)) : "§70/s";

        tipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.junction.source_total_production", "§a" + prodStr).getString()));
        tipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.junction.source_total_usage", "§c" + usageStr).getString()));
        tipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.junction.source_available_surplus", surplusStr).getString()));

        graphics.renderComponentTooltip(font, tipLines, mouseX, mouseY);
    }

    private static void renderNavArrow(GuiGraphics graphics, Font font, int ax, int ay, String arrow, int mouseX, int mouseY) {
        boolean hover = mouseX >= ax && mouseX <= ax + 14 && mouseY >= ay && mouseY <= ay + 14;
        graphics.fill(ax, ay, ax + 14, ay + 14, hover ? 0xFF334155 : 0xFF1E293B);
        graphics.renderOutline(ax, ay, 14, 14, hover ? 0xFF64748B : 0xFF475569);
        graphics.drawCenteredString(font, arrow, ax + 7, ay + 3, hover ? 0xFFFFFFFF : 0xFF94A3B8);
    }

    private static void renderSelectorBox(GuiGraphics graphics, Font font, int bx, int by, int bw, String text) {
        graphics.fill(bx, by, bx + bw, by + 14, 0xFF0F172A);
        graphics.renderOutline(bx, by, bw, 14, 0xFF334155);
        String trimmed = font.plainSubstrByWidth(text, bw - 8);
        graphics.drawString(font, trimmed, bx + 4, by + 3, 0xFFE2E8F0, false);
    }

    private List<BoardPage> getCandidatePages() {
        BoardManager bm = BoardManager.getInstance();
        if (bm == null || bm.getPages() == null) return Collections.emptyList();
        BoardPage active = bm.getActivePage();
        String activeId = active != null ? active.getId() : "";
        List<BoardPage> list = new ArrayList<>();
        for (BoardPage p : bm.getPages()) {
            if (!p.getId().equals(activeId)) {
                list.add(p);
            }
        }
        return list;
    }

    private List<RecipeNode> getCandidateJunctions(BoardPage page) {
        if (page == null || page.getGraph() == null) return Collections.emptyList();
        List<RecipeNode> list = new ArrayList<>();
        IngredientStack bound = targetNode != null ? targetNode.getRerouteIngredient() : null;
        for (RecipeNode n : page.getGraph().getNodes()) {
            if (!n.isReroute()) continue;
            if (bound != null && n.getRerouteIngredient() != null) {
                if (bound.matches(n.getRerouteIngredient())) {
                    list.add(n);
                }
            } else {
                list.add(n);
            }
        }
        return list;
    }

    private BoardPage findCurrentSelectedPage(List<BoardPage> pages) {
        if (pages.isEmpty()) return null;
        if (linkedSourcePageId != null) {
            for (BoardPage p : pages) {
                if (p.getId().equals(linkedSourcePageId)) return p;
            }
        }
        BoardPage first = pages.get(0);
        linkedSourcePageId = first.getId();
        return first;
    }

    private RecipeNode findCurrentSelectedJunction(List<RecipeNode> junctions) {
        if (junctions.isEmpty()) return null;
        if (linkedSourceNodeId != null) {
            for (RecipeNode n : junctions) {
                if (n.getId().equals(linkedSourceNodeId)) return n;
            }
        }
        RecipeNode first = junctions.get(0);
        linkedSourceNodeId = first.getId();
        return first;
    }

    private void syncBoundIngredientFromLinkedSource() {
        if (targetNode == null) return;
        List<BoardPage> pages = getCandidatePages();
        BoardPage curPage = findCurrentSelectedPage(pages);
        if (curPage == null) return;
        List<RecipeNode> junctions = getCandidateJunctions(curPage);
        RecipeNode srcNode = findCurrentSelectedJunction(junctions);
        if (srcNode != null && srcNode.getRerouteIngredient() != null) {
            targetNode.bindRerouteIngredient(srcNode.getRerouteIngredient().copy());
        }
    }

    private void cycleSourcePage(int dir) {
        List<BoardPage> pages = getCandidatePages();
        if (pages.isEmpty()) return;
        int idx = 0;
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).getId().equals(linkedSourcePageId)) {
                idx = i;
                break;
            }
        }
        int next = (idx + dir + pages.size()) % pages.size();
        linkedSourcePageId = pages.get(next).getId();
        List<RecipeNode> junctions = getCandidateJunctions(pages.get(next));
        linkedSourceNodeId = !junctions.isEmpty() ? junctions.get(0).getId() : null;
        playClickSound();
    }

    private void cycleSourceJunction(int dir) {
        List<BoardPage> pages = getCandidatePages();
        BoardPage curPage = findCurrentSelectedPage(pages);
        List<RecipeNode> junctions = getCandidateJunctions(curPage);
        if (junctions.isEmpty()) return;
        int idx = 0;
        for (int i = 0; i < junctions.size(); i++) {
            if (junctions.get(i).getId().equals(linkedSourceNodeId)) {
                idx = i;
                break;
            }
        }
        int next = (idx + dir + junctions.size()) % junctions.size();
        linkedSourceNodeId = junctions.get(next).getId();
        playClickSound();
    }

    private boolean checkLinkedSourceSelectorsClicked(int x, int startY, double mouseX, double mouseY) {
        int row1Y = startY + 2;
        int pBtnLeftX = x + SRC_SELECTOR_LEFT_X;
        int pBtnRightX = x + SRC_SELECTOR_RIGHT_X;
        if (mouseY >= row1Y && mouseY <= row1Y + 14) {
            if (mouseX >= pBtnLeftX && mouseX <= pBtnLeftX + 14) {
                cycleSourcePage(-1);
                return true;
            }
            if (mouseX >= pBtnRightX && mouseX <= pBtnRightX + 14) {
                cycleSourcePage(1);
                return true;
            }
        }
        int row2Y = startY + 20;
        if (mouseY >= row2Y && mouseY <= row2Y + 14) {
            if (mouseX >= pBtnLeftX && mouseX <= pBtnLeftX + 14) {
                cycleSourceJunction(-1);
                return true;
            }
            if (mouseX >= pBtnRightX && mouseX <= pBtnRightX + 14) {
                cycleSourceJunction(1);
                return true;
            }
        }
        return false;
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
        graphics.drawString(font, font.plainSubstrByWidth(modeLabel, 54), x + 14, modeY + 3, 0xFFCBD5E1, false);

        int btnW = 82;
        int gap = 5;
        int btn1X = x + 72;
        int btn2X = btn1X + btnW + gap;
        int btn3X = btn2X + btnW + gap;

        renderSplitModeButton(graphics, font, btn1X, modeY, btnW, FlowSplitMode.PROPORTIONAL, "gui.gtcalcboard.junction.split_mode.proportional", "gui.gtcalcboard.junction.proportional_tooltip", mouseX, mouseY);
        renderSplitModeButton(graphics, font, btn2X, modeY, btnW, FlowSplitMode.EQUAL, "gui.gtcalcboard.junction.split_mode.equal", "gui.gtcalcboard.junction.equal_tooltip", mouseX, mouseY);
        renderSplitModeButton(graphics, font, btn3X, modeY, btnW, FlowSplitMode.WEIGHTED, "gui.gtcalcboard.junction.split_mode.weighted", "gui.gtcalcboard.junction.weighted_tooltip", mouseX, mouseY);
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
        renderAddExportTargetButton(graphics, font, x, listHeaderY, mouseX, mouseY);

        int totalItems = outgoingEdges.size() + exportTargets.size();
        int maxScroll = Math.max(0, totalItems - 3);
        if (maxScroll > 0) {
            String pageStr = String.format("§7(%d/%d)", outgoingScrollOffset + 1, maxScroll + 1);
            graphics.drawString(font, pageStr, x + DIALOG_WIDTH - 55, listHeaderY, 0xFF94A3B8, false);
        }

        hideAllAllocationEditBoxes();

        int listStartY = y + 135;
        if (totalItems == 0) {
            graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.junction.no_outgoing").getString(), x + 14, listStartY + 4, 0xFF64748B, false);
            return;
        }

        FlowGraph graph = parent != null ? parent.getGraph() : null;
        int visibleCount = Math.min(3, totalItems - outgoingScrollOffset);
        boolean isWeighted = (splitMode == FlowSplitMode.WEIGHTED);

        for (int i = 0; i < visibleCount; i++) {
            int itemIdx = outgoingScrollOffset + i;
            int rowY = listStartY + i * 22;
            if (itemIdx < outgoingEdges.size()) {
                FlowGraph.ConnectionEdge edge = outgoingEdges.get(itemIdx);
                renderEdgeRow(graphics, font, graph, edge, x, rowY, isWeighted, mouseX, mouseY);
            } else {
                int targetIdx = itemIdx - outgoingEdges.size();
                CrossPageExportTarget target = exportTargets.get(targetIdx);
                renderExportTargetRow(graphics, font, x, rowY, target, mouseX, mouseY);
            }
        }
    }

    private void hideAllAllocationEditBoxes() {
        for (EditBox eb : edgeLimitEditBoxes.values()) eb.setY(-1000);
        for (EditBox eb : edgePriorityEditBoxes.values()) eb.setY(-1000);
        for (EditBox eb : edgeWeightEditBoxes.values()) eb.setY(-1000);
        for (EditBox eb : targetPriorityEditBoxes.values()) eb.setY(-1000);
        for (EditBox eb : targetLimitEditBoxes.values()) eb.setY(-1000);
    }

    private void renderEdgeRow(GuiGraphics graphics, Font font, FlowGraph graph, FlowGraph.ConnectionEdge edge, int x, int rowY, boolean isWeighted, int mouseX, int mouseY) {
        RecipeNode toNode = graph != null ? graph.findNodeById(edge.toNodeId()) : null;
        String toName = (toNode != null && toNode.getName() != null && !toNode.getName().isBlank()) ? toNode.getName() : edge.toNodeId();
        String portLabel = String.format("#%d → %s", edge.outputIndex() + 1, toName);
        EditBox ebLimit = edgeLimitEditBoxes.get(edge);
        EditBox ebPri = edgePriorityEditBoxes.get(edge);
        EditBox ebWeight = edgeWeightEditBoxes.get(edge);

        if (isWeighted) {
            renderWeightedEdgeControls(graphics, font, x, rowY, portLabel, ebPri, ebWeight, ebLimit, mouseX, mouseY);
        } else {
            renderStandardEdgeControls(graphics, font, x, rowY, portLabel, ebPri, ebLimit, mouseX, mouseY);
        }
    }

    private void renderWeightedEdgeControls(GuiGraphics graphics, Font font, int x, int rowY, String portLabel, EditBox ebPri, EditBox ebWeight, EditBox ebLimit, int mouseX, int mouseY) {
        graphics.drawString(font, font.plainSubstrByWidth(portLabel, 85), x + 14, rowY + 3, 0xFFE2E8F0, false);
        graphics.drawString(font, "P:", x + 103, rowY + 3, 0xFF94A3B8, false);
        if (ebPri != null) {
            ebPri.setX(x + 115);
            ebPri.setWidth(24);
            ebPri.setY(rowY);
            ebPri.render(graphics, mouseX, mouseY, 0);
        }
        graphics.drawString(font, "W:", x + 144, rowY + 3, 0xFF94A3B8, false);
        if (ebWeight != null) {
            ebWeight.setX(x + 158);
            ebWeight.setWidth(34);
            ebWeight.setY(rowY);
            ebWeight.render(graphics, mouseX, mouseY, 0);
        }
        graphics.drawString(font, "Cap:", x + 197, rowY + 3, 0xFF94A3B8, false);
        if (ebLimit != null) {
            ebLimit.setX(x + 224);
            ebLimit.setWidth(DIALOG_WIDTH - 238);
            ebLimit.setY(rowY);
            ebLimit.render(graphics, mouseX, mouseY, 0);
        }
    }

    private void renderStandardEdgeControls(GuiGraphics graphics, Font font, int x, int rowY, String portLabel, EditBox ebPri, EditBox ebLimit, int mouseX, int mouseY) {
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

    private void renderExportTargetRow(GuiGraphics graphics, Font font, int x, int rowY, CrossPageExportTarget target, int mouseX, int mouseY) {
        BoardPage targetPage = BoardManager.getInstance().getPage(target.targetPageId()).orElse(null);
        String pageName = targetPage != null && targetPage.getName() != null && !targetPage.getName().isEmpty() ? targetPage.getName() : target.targetPageId();
        String label = "\uD83D\uDD17 " + pageName;
        boolean labelHover = mouseX >= x + 14 && mouseX <= x + 99 && mouseY >= rowY && mouseY <= rowY + 14;
        int labelCol = labelHover ? 0xFF7DD3FC : 0xFF38BDF8;
        graphics.drawString(font, font.plainSubstrByWidth(label, 85), x + 14, rowY + 3, labelCol, false);

        graphics.drawString(font, "P:", x + 103, rowY + 3, 0xFF94A3B8, false);
        EditBox ebPri = targetPriorityEditBoxes.get(target);
        if (ebPri != null) {
            ebPri.setX(x + 115);
            ebPri.setWidth(24);
            ebPri.setY(rowY);
            ebPri.render(graphics, mouseX, mouseY, 0);
        }

        graphics.drawString(font, "Cap:", x + 144, rowY + 3, 0xFF94A3B8, false);
        EditBox ebLimit = targetLimitEditBoxes.get(target);
        if (ebLimit != null) {
            ebLimit.setX(x + 168);
            ebLimit.setWidth(DIALOG_WIDTH - 200);
            ebLimit.setY(rowY);
            ebLimit.render(graphics, mouseX, mouseY, 0);
        }

        int delX = x + DIALOG_WIDTH - 24;
        boolean delHover = mouseX >= delX && mouseX <= delX + 14 && mouseY >= rowY && mouseY <= rowY + 12;
        graphics.fill(delX, rowY + 1, delX + 14, rowY + 13, delHover ? 0xFF882222 : 0xFF3B1515);
        graphics.renderOutline(delX, rowY + 1, 14, 12, delHover ? 0xFFEF4444 : 0xFF6B2121);
        graphics.drawCenteredString(font, "✕", delX + 7, rowY + 3, 0xFFFFFFFF);
    }

    private void renderAddExportTargetButton(GuiGraphics graphics, Font font, int x, int listHeaderY, int mouseX, int mouseY) {
        int btnX = x + 160;
        int btnY = listHeaderY - 2;
        int btnW = 80;
        int btnH = 12;
        boolean hover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, hover ? 0xFF0284C7 : 0xFF1E293B);
        graphics.renderOutline(btnX, btnY, btnW, btnH, hover ? 0xFF38BDF8 : 0xFF475569);
        String label = "+ " + Component.translatable("gui.gtcalcboard.junction.add_export_target").getString();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(label, btnW - 4), btnX + btnW / 2, btnY + 2, hover ? 0xFFFFFFFF : 0xFF94A3B8);
    }

    private void addExportTarget(Font font, int x, int y) {
        List<BoardPage> pages = getCandidatePages();
        if (pages.isEmpty()) return;
        Set<String> existing = new HashSet<>();
        for (CrossPageExportTarget t : exportTargets) {
            existing.add(t.targetPageId());
        }
        String chosenId = null;
        for (BoardPage p : pages) {
            if (!existing.contains(p.getId())) {
                chosenId = p.getId();
                break;
            }
        }
        if (chosenId == null) {
            return;
        }
        exportTargets.add(new CrossPageExportTarget(chosenId, 0, 0.0));
        initExportTargets(font, x, y);
        playClickSound();
    }

    private void cycleExportTargetPage(int targetIdx) {
        if (targetIdx < 0 || targetIdx >= exportTargets.size()) return;
        List<BoardPage> pages = getCandidatePages();
        if (pages.size() <= 1) return;
        CrossPageExportTarget current = exportTargets.get(targetIdx);

        Set<String> otherExisting = new HashSet<>();
        for (int i = 0; i < exportTargets.size(); i++) {
            if (i != targetIdx) {
                otherExisting.add(exportTargets.get(i).targetPageId());
            }
        }

        int curIdx = -1;
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).getId().equals(current.targetPageId())) {
                curIdx = i;
                break;
            }
        }

        for (int step = 1; step <= pages.size(); step++) {
            int nextIdx = (curIdx + step) % pages.size();
            String candidateId = pages.get(nextIdx).getId();
            if (!otherExisting.contains(candidateId)) {
                EditBox ebPri = targetPriorityEditBoxes.remove(current);
                EditBox ebLimit = targetLimitEditBoxes.remove(current);
                CrossPageExportTarget updated = new CrossPageExportTarget(candidateId, current.priority(), current.fixedLimit());
                exportTargets.set(targetIdx, updated);
                if (ebPri != null) targetPriorityEditBoxes.put(updated, ebPri);
                if (ebLimit != null) targetLimitEditBoxes.put(updated, ebLimit);
                playClickSound();
                return;
            }
        }
    }

    private void removeExportTarget(int idx) {
        if (idx >= 0 && idx < exportTargets.size()) {
            exportTargets.remove(idx);
            Minecraft mc = Minecraft.getInstance();
            int x = (getScreenWidth() - DIALOG_WIDTH) / 2;
            int y = (getScreenHeight() - DIALOG_HEIGHT) / 2;
            initExportTargets(mc != null ? mc.font : null, x, y);
            playClickSound();
        }
    }

    private boolean checkAddExportTargetClicked(int x, int listHeaderY, double mouseX, double mouseY) {
        int btnX = x + 160;
        int btnY = listHeaderY - 2;
        int btnW = 80;
        int btnH = 12;
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            Minecraft mc = Minecraft.getInstance();
            int y = (getScreenHeight() - DIALOG_HEIGHT) / 2;
            addExportTarget(mc != null ? mc.font : null, x, y);
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible || activeTab != 1) return false;
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;
        if (mouseX >= x && mouseX <= x + DIALOG_WIDTH && mouseY >= y && mouseY <= y + DIALOG_HEIGHT) {
            int totalItems = outgoingEdges.size() + exportTargets.size();
            int maxScroll = Math.max(0, totalItems - 3);
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
            } else if (selectedMode == SupplyMode.LINKED_JUNCTION) {
                if (checkLinkedSourceSelectorsClicked(x, y + 174, mouseX, mouseY)) return true;
            }
            if (checkRadioSelection(x, y, mouseX, mouseY)) return true;
        } else {
            if (isBuffer && checkEditBoxClicked(bufferSizeEditBox, mouseX, mouseY, button)) return true;
            if (checkBufferToggleClicked(x, y, mouseX, mouseY)) return true;
            if (checkSplitModeClicked(x, y, mouseX, mouseY)) return true;
            if (checkAddExportTargetClicked(x, y + 120, mouseX, mouseY)) return true;

            if (checkOutgoingRowsClicked(x, y, mouseX, mouseY, button)) return true;
        }

        return checkFooterButtons(x, y, mouseX, mouseY);
    }

    private boolean checkOutgoingRowsClicked(int x, int y, double mouseX, double mouseY, int button) {
        int totalItems = outgoingEdges.size() + exportTargets.size();
        int visibleCount = Math.min(3, totalItems - outgoingScrollOffset);
        for (int i = 0; i < visibleCount; i++) {
            int itemIdx = outgoingScrollOffset + i;
            int rowY = y + 135 + i * 22;
            if (itemIdx < outgoingEdges.size()) {
                if (checkEdgeRowClicked(outgoingEdges.get(itemIdx), mouseX, mouseY, button)) return true;
            } else {
                int targetIdx = itemIdx - outgoingEdges.size();
                if (checkExportTargetRowClicked(x, rowY, targetIdx, mouseX, mouseY, button)) return true;
            }
        }
        return false;
    }

    private boolean checkEdgeRowClicked(FlowGraph.ConnectionEdge edge, double mouseX, double mouseY, int button) {
        EditBox eb = edgeLimitEditBoxes.get(edge);
        if (eb != null && checkEditBoxClicked(eb, mouseX, mouseY, button)) return true;
        EditBox ebPri = edgePriorityEditBoxes.get(edge);
        if (ebPri != null && checkEditBoxClicked(ebPri, mouseX, mouseY, button)) return true;
        if (splitMode == FlowSplitMode.WEIGHTED) {
            EditBox ebWeight = edgeWeightEditBoxes.get(edge);
            return ebWeight != null && checkEditBoxClicked(ebWeight, mouseX, mouseY, button);
        }
        return false;
    }

    private boolean checkExportTargetRowClicked(int x, int rowY, int targetIdx, double mouseX, double mouseY, int button) {
        if (mouseX >= x + 14 && mouseX <= x + 99 && mouseY >= rowY && mouseY <= rowY + 14) {
            cycleExportTargetPage(targetIdx);
            return true;
        }
        CrossPageExportTarget target = exportTargets.get(targetIdx);
        EditBox ebPri = targetPriorityEditBoxes.get(target);
        if (ebPri != null && checkEditBoxClicked(ebPri, mouseX, mouseY, button)) return true;
        EditBox ebLimit = targetLimitEditBoxes.get(target);
        if (ebLimit != null && checkEditBoxClicked(ebLimit, mouseX, mouseY, button)) return true;

        int delX = x + DIALOG_WIDTH - 24;
        if (mouseX >= delX && mouseX <= delX + 14 && mouseY >= rowY && mouseY <= rowY + 12) {
            removeExportTarget(targetIdx);
            return true;
        }
        return false;
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

        int btnW = 82;
        int gap = 5;
        int btn1X = x + 72;
        int btn2X = btn1X + btnW + gap;
        int btn3X = btn2X + btnW + gap;

        if (mouseX >= btn1X && mouseX <= btn1X + btnW) {
            this.splitMode = FlowSplitMode.PROPORTIONAL;
            unfocusWeightEditBoxes();
            playClickSound();
            return true;
        }
        if (mouseX >= btn2X && mouseX <= btn2X + btnW) {
            this.splitMode = FlowSplitMode.EQUAL;
            unfocusWeightEditBoxes();
            playClickSound();
            return true;
        }
        if (mouseX >= btn3X && mouseX <= btn3X + btnW) {
            this.splitMode = FlowSplitMode.WEIGHTED;
            playClickSound();
            return true;
        }
        return false;
    }

    private void unfocusWeightEditBoxes() {
        for (EditBox eb : edgeWeightEditBoxes.values()) {
            eb.setFocused(false);
        }
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
            RateTimeUnit timeUnit = FormatUtil.getActiveTimeUnit();
            double factor = (timeUnit != null && !timeUnit.isRecipeBatchMode()) ? timeUnit.getFactor() : 1.0;
            rateEditBox.setValue(formatRateForEditBox(rate * factor));
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
        int optStartY = y + 68;
        int optH = 17;
        SupplyMode[] modes = SupplyMode.values();
        for (int i = 0; i < modes.length; i++) {
            SupplyMode mode = modes[i];
            int optY = optStartY + i * optH;
            boolean hasRateBox = (mode == SupplyMode.FIXED_RATE || mode == SupplyMode.FIXED_DRAIN)
                    && selectedMode == mode && rateEditBox != null;
            int rightBound = hasRateBox ? rateEditBox.getX() - 4 : x + DIALOG_WIDTH - 10;
            if (mouseX < x + 10 || mouseX > rightBound || mouseY < optY || mouseY > optY + 14) continue;

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
                RateTimeUnit timeUnit = FormatUtil.getActiveTimeUnit();
                double factor = (timeUnit != null && !timeUnit.isRecipeBatchMode()) ? timeUnit.getFactor() : 1.0;
                rateEditBox.setValue(formatRateForEditBox(cur * factor));
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
            targetNode.setExternalSupplyRate(parseRateInput(rateEditBox.getValue()));
        } else if (selectedMode == SupplyMode.FIXED_DRAIN && rateEditBox != null) {
            targetNode.setExternalDrainRate(parseRateInput(rateEditBox.getValue()));
        } else if (selectedMode == SupplyMode.LINKED_JUNCTION) {
            targetNode.asJunction().setLinkedSource(linkedSourcePageId, linkedSourceNodeId);
            syncBoundIngredientFromLinkedSource();
        } else {
            targetNode.asJunction().setLinkedSource("", "");
            targetNode.asJunction().setAllocatedInputRate(0.0);
        }

        targetNode.setJunctionBuffer(isBuffer);
        if (bufferSizeEditBox != null) {
            targetNode.setJunctionBufferSize(Math.max(0.0, parseDoubleSafe(bufferSizeEditBox.getValue())));
        }

        List<CrossPageExportTarget> updatedTargets = new ArrayList<>();
        for (CrossPageExportTarget target : exportTargets) {
            EditBox pBox = targetPriorityEditBoxes.get(target);
            EditBox lBox = targetLimitEditBoxes.get(target);
            int pri = pBox != null ? Math.max(0, Math.min(99, parseIntSafe(pBox.getValue()))) : target.priority();
            double cap = lBox != null ? Math.max(0.0, parseDoubleSafe(lBox.getValue())) : target.fixedLimit();
            updatedTargets.add(new CrossPageExportTarget(target.targetPageId(), pri, cap));
        }
        targetNode.asJunction().setExportTargets(updatedTargets);

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
                EditBox priBox = edgePriorityEditBoxes.get(edge);
                int pri = priBox != null ? Math.max(0, Math.min(99, parseIntSafe(priBox.getValue()))) : edge.priority();
                EditBox weightBox = edgeWeightEditBoxes.get(edge);
                double weight = edge.weight();
                if (weightBox != null && !weightBox.getValue().isBlank()) {
                    weight = Math.max(0.0, parseDoubleSafe(weightBox.getValue()));
                }
                graph.setConnectionProperties(edge.fromNodeId(), edge.outputIndex(), edge.toNodeId(), edge.inputIndex(), limit, pri, weight);
            }
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new com.gtceu.calcboard.api.event.FlowGraphEvent.JunctionConfigured(graph, targetNode, selectedMode)
            );
        }

        BoardManager bm = BoardManager.getInstance();
        if (bm != null && bm.getPages() != null && !bm.getPages().isEmpty()) {
            WorkspaceFlowCoordinator.coordinate(bm.getPages());
        }

        if (parent != null) {
            parent.markSummaryDirty();
            parent.rebuildWidgets();
        }

        playClickSound();
        close();
    }

    private double parseRateInput(String text) {
        if (text == null || text.isBlank()) return 0.0;
        String trimmed = text.trim();

        IngredientStack boundStack = (targetNode != null) ? targetNode.getRerouteIngredient() : null;
        boolean isFluid = (boundStack != null && boundStack.isFluid());
        RateTimeUnit defaultUnit = FormatUtil.getActiveTimeUnit();

        java.util.OptionalDouble parsed = TargetRateParser.parseRate(trimmed, isFluid, defaultUnit);
        if (parsed.isPresent()) {
            return Math.max(0.0, parsed.getAsDouble());
        }

        try {
            double val = Double.parseDouble(trimmed);
            if (val <= 0.0) return 0.0;
            double factor = (defaultUnit != null && !defaultUnit.isRecipeBatchMode()) ? defaultUnit.getFactor() : 1.0;
            return Math.max(0.0, val / factor);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static String formatWeightForEditBox(double weight) {
        if (!Double.isFinite(weight) || weight < 0.0) return "1";
        if (weight == Math.floor(weight)) {
            return String.valueOf((long) weight);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", weight).replaceAll("\\.?0+$", "");
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
            double val = Double.parseDouble(s.trim());
            return Double.isFinite(val) ? val : 0.0;
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
            if (forwardKeyPressed(edgeLimitEditBoxes.values(), keyCode, scanCode, modifiers)) return true;
            if (forwardKeyPressed(edgePriorityEditBoxes.values(), keyCode, scanCode, modifiers)) return true;
            if (forwardKeyPressed(targetPriorityEditBoxes.values(), keyCode, scanCode, modifiers)) return true;
            if (forwardKeyPressed(targetLimitEditBoxes.values(), keyCode, scanCode, modifiers)) return true;
            if (splitMode == FlowSplitMode.WEIGHTED && forwardKeyPressed(edgeWeightEditBoxes.values(), keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return true;
    }

    private boolean forwardKeyPressed(Collection<EditBox> boxes, int keyCode, int scanCode, int modifiers) {
        for (EditBox eb : boxes) {
            if (eb.isFocused()) {
                return eb.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return false;
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
            if (forwardCharTyped(edgeLimitEditBoxes.values(), codePoint, modifiers)) return true;
            if (forwardCharTyped(edgePriorityEditBoxes.values(), codePoint, modifiers)) return true;
            if (forwardCharTyped(targetPriorityEditBoxes.values(), codePoint, modifiers)) return true;
            if (forwardCharTyped(targetLimitEditBoxes.values(), codePoint, modifiers)) return true;
            if (splitMode == FlowSplitMode.WEIGHTED && forwardCharTyped(edgeWeightEditBoxes.values(), codePoint, modifiers)) {
                return true;
            }
        }
        return false;
    }

    private boolean forwardCharTyped(Collection<EditBox> boxes, char codePoint, int modifiers) {
        for (EditBox eb : boxes) {
            if (eb.isFocused()) {
                return eb.charTyped(codePoint, modifiers);
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
