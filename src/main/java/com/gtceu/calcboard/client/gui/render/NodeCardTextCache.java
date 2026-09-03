package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.api.property.NodeBadgeRegistry;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.util.NumberFormatUtil;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NodeCardTextCache {

    public record PortText(String text, int width, int textColor, int portColor) {}

    private boolean dirty = true;

    private String title = "";
    private int titleColor = 0xFFFFFFFF;

    private String rightInfoStr = "";
    private int rightInfoW = 0;
    private String fittedPowerStr = "";

    private final List<PortText> leftPortTexts = new ArrayList<>();
    private final List<PortText> rightPortTexts = new ArrayList<>();
    private List<NodeBadge> badges = List.of();

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public String getTitle() {
        return title;
    }

    public int getTitleColor() {
        return titleColor;
    }

    public String getRightInfoStr() {
        return rightInfoStr;
    }

    public int getRightInfoW() {
        return rightInfoW;
    }

    public String getFittedPowerStr() {
        return fittedPowerStr;
    }

    public List<PortText> getLeftPortTexts() {
        return leftPortTexts;
    }

    public List<PortText> getRightPortTexts() {
        return rightPortTexts;
    }

    public List<NodeBadge> getBadges() {
        return badges;
    }

    public void update(NodeWidget widget, Font font, FlowGraph graph, RecipeNode node, int cardW, int titleX, int x, int headerBtnMargin) {
        if (!dirty) return;

        boolean isOperational = node.isOperational(graph);

        updateTitle(node, cardW, titleX, x, headerBtnMargin, isOperational);
        updatePowerAndDuration(widget, font, node, cardW, isOperational);
        updateBadges(node);
        updatePorts(widget, font, graph, node, cardW, isOperational);

        this.dirty = false;
    }

    private void updateTitle(RecipeNode node, int cardW, int titleX, int x, int headerBtnMargin, boolean isOperational) {
        String baseTitle = (!isOperational ? "§c⚠ " : "")
                + (node.isModule() ? "§d📦 " : (node.isFusion() ? "§d⚛ " : (node.isBaseNode() ? "§6★ " : (node.isGenerator() ? "§a⚡ " : ""))))
                + node.getName();
        int maxTitleChars = Math.max(8, (cardW - (titleX - x) - headerBtnMargin) / 6);
        if (baseTitle.length() > maxTitleChars) {
            baseTitle = baseTitle.substring(0, Math.max(2, maxTitleChars - 2)) + "...";
        }
        this.title = baseTitle;
        this.titleColor = !isOperational ? 0xFFFF7777 : (node.isModule() ? 0xFFFFB3FF : (node.isFusion() ? 0xFFFFB3FF : (node.isBaseNode() ? 0xFFFFE066 : (node.isGenerator() ? 0xFF77FFAA : 0xFFE0E0E0))));
    }

    private void updatePowerAndDuration(NodeWidget widget, Font font, RecipeNode node, int cardW, boolean isOperational) {
        double durationSec = node.getEffectiveDurationSeconds();
        double effCps = node.getEffectiveCyclesPerSecond();

        if (!isOperational) {
            this.rightInfoStr = String.format(Locale.ROOT, "§c%.2fs §7(§c0/s§7)", durationSec);
        } else if (node.isModule()) {
            this.rightInfoStr = "§d§l[📦 " + Component.translatable("gui.gtcalcboard.module").getString() + "]";
        } else {
            this.rightInfoStr = String.format(Locale.ROOT, "§b%.2fs §7(§f%s/s§7)", durationSec, NumberFormatUtil.formatCompactNumber(effCps));
        }
        this.rightInfoW = font.width(this.rightInfoStr);

        int maxPowerW = Math.max(20, (cardW - 12) - this.rightInfoW - 4);
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        String powerStr;
        if (node.getEnergyType() == EnergyType.NONE) {
            powerStr = Component.translatable("gui.gtcalcboard.energy_passive_stat").getString();
        } else if (!isOperational) {
            GTVoltageTier tier = node.getTargetTier();
            String tierName = tier != null ? tier.getName() : "LV";
            powerStr = "§c0.0 EU/t §7(0A " + tierName + ")";
        } else {
            powerStr = adapter.formatEnergyStats(node, BoardManager.getInstance().getPowerDisplayMode());
        }
        this.fittedPowerStr = font.plainSubstrByWidth(powerStr, maxPowerW);
    }

    private void updateBadges(RecipeNode node) {
        this.badges = NodeBadgeRegistry.getBadgesForNode(node);
    }

    private void updatePorts(NodeWidget widget, Font font, FlowGraph graph, RecipeNode node, int cardW, boolean isOperational) {
        leftPortTexts.clear();
        rightPortTexts.clear();

        List<IngredientStack> inputs = node.getInputs();
        List<IngredientStack> outputs = node.getOutputs();
        List<Integer> visInputs = node.getVisibleInputIndices();
        List<Integer> visOutputs = node.getVisibleOutputIndices();
        int maxRows = Math.max(visInputs.size(), visOutputs.size());
        boolean isFlipped = node.isFlipped();

        for (int r = 0; r < maxRows; r++) {
            boolean hasInput = (r < visInputs.size());
            boolean hasOutput = (r < visOutputs.size());
            int inOrigIdx = hasInput ? visInputs.get(r) : -1;
            int outOrigIdx = hasOutput ? visOutputs.get(r) : -1;

            if (!isFlipped && hasInput) {
                leftPortTexts.add(createInputPortText(widget, font, graph, node, inputs.get(inOrigIdx), inOrigIdx, cardW, hasOutput, isOperational));
            } else if (isFlipped && hasOutput) {
                leftPortTexts.add(createOutputPortText(widget, font, graph, node, outputs.get(outOrigIdx), outOrigIdx, cardW, hasInput, isOperational));
            } else {
                leftPortTexts.add(null);
            }

            if (!isFlipped && hasOutput) {
                rightPortTexts.add(createOutputPortText(widget, font, graph, node, outputs.get(outOrigIdx), outOrigIdx, cardW, hasInput, isOperational));
            } else if (isFlipped && hasInput) {
                rightPortTexts.add(createInputPortText(widget, font, graph, node, inputs.get(inOrigIdx), inOrigIdx, cardW, hasOutput, isOperational));
            } else {
                rightPortTexts.add(null);
            }
        }
    }

    private PortText createInputPortText(NodeWidget widget, Font font, FlowGraph graph, RecipeNode node, IngredientStack in, int inOrigIdx, int cardW, boolean hasOther, boolean isOperational) {
        double rate = widget.getInputRate(inOrigIdx);
        FlowGraphSolver.PortFlowStats stats = graph != null ? graph.getInputPortStats(node, inOrigIdx) : null;
        boolean isConnected = stats != null && stats.isConnected();
        boolean isBalanced = stats != null && stats.isBalanced();
        boolean isDeficit = stats != null && stats.isInputDeficit();

        int portColor = !isOperational ? 0xFF77333B : (!isConnected ? 0xFF5599FF : (isBalanced ? 0xFF55FF88 : (isDeficit ? 0xFFFFAA33 : 0xFF55FFFF)));

        String rateStr;
        int textColor;
        if (!isOperational) {
            rateStr = "§c-" + FormatUtil.formatRate(0.0, in);
            textColor = 0xFFFF7777;
        } else if (!isConnected) {
            rateStr = "§7-" + FormatUtil.formatRate(rate, in);
            textColor = 0xFFFFAAAA;
        } else if (isBalanced) {
            rateStr = "§a" + FormatUtil.formatRate(rate, in) + " §2✔";
            textColor = 0xFFFFFFFF;
        } else if (isDeficit) {
            rateStr = FormatUtil.formatConnectedInput(stats.connectedRate(), rate, in, true);
            textColor = 0xFFFFFFFF;
        } else {
            rateStr = FormatUtil.formatConnectedInput(stats.connectedRate(), rate, in, false);
            textColor = 0xFFFFFFFF;
        }

        int maxTextW = hasOther ? Math.max(20, (cardW / 2) - 34) : (cardW - 36);
        int textW = font.width(rateStr);
        if (textW > maxTextW) {
            rateStr = font.plainSubstrByWidth(rateStr, maxTextW);
            textW = font.width(rateStr);
        }

        return new PortText(rateStr, textW, textColor, portColor);
    }

    private PortText createOutputPortText(NodeWidget widget, Font font, FlowGraph graph, RecipeNode node, IngredientStack out, int outOrigIdx, int cardW, boolean hasOther, boolean isOperational) {
        double rate = widget.getOutputRate(outOrigIdx);
        FlowGraphSolver.PortFlowStats stats = graph != null ? graph.getOutputPortStats(node, outOrigIdx) : null;
        boolean isConnected = stats != null && stats.isConnected();
        boolean isBalanced = stats != null && stats.isBalanced();
        boolean isSurplus = stats != null && stats.isOutputSurplus();
        boolean isDeficit = stats != null && stats.isOutputDeficit();
        boolean isInactive = rate <= 0.00001;

        int portColor = !isOperational ? 0xFF77333B : (!isConnected ? (isInactive ? 0xFF444B5A : 0xFF55FF88) : (isBalanced ? 0xFF55FF88 : (isDeficit ? 0xFFFFAA33 : 0xFF55FFFF)));

        String rateStr;
        int textColor;
        if (!isOperational) {
            rateStr = "§c" + FormatUtil.formatRate(0.0, out) + " §4⏸";
            textColor = 0xFFFF7777;
        } else if (!isConnected) {
            if (isInactive) {
                rateStr = "§8+0/s §7(0%)";
                textColor = 0xFF778092;
            } else {
                rateStr = "§a+" + FormatUtil.formatRate(rate, out);
                textColor = 0xFFAAFFAA;
            }
        } else if (isBalanced) {
            rateStr = "§a" + FormatUtil.formatRate(rate, out) + " §2✔";
            textColor = 0xFFFFFFFF;
        } else if (isSurplus) {
            rateStr = FormatUtil.formatConnectedOutput(rate, stats.connectedRate(), out, false);
            textColor = 0xFFFFFFFF;
        } else {
            rateStr = FormatUtil.formatConnectedOutput(rate, stats.connectedRate(), out, true);
            textColor = 0xFFFFFFFF;
        }

        int maxTextW = hasOther ? Math.max(20, (cardW / 2) - 34) : (cardW - 36);
        int textW = font.width(rateStr);
        if (textW > maxTextW) {
            rateStr = font.plainSubstrByWidth(rateStr, maxTextW);
            textW = font.width(rateStr);
        }

        return new PortText(rateStr, textW, textColor, portColor);
    }
}
