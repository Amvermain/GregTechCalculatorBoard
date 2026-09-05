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
                + (node.isModule() ? "§d▦ " : (node.isFusion() ? "§d⚛ " : (node.isBaseNode() ? "§6★ " : (node.isGenerator() ? "§a⚡ " : ""))))
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

        boolean isBatch = FormatUtil.getActiveTimeUnit().isRecipeBatchMode();
        if (!isOperational) {
            this.rightInfoStr = isBatch
                    ? String.format(Locale.ROOT, "§c%.2fs §7(§c1x§7)", durationSec)
                    : String.format(Locale.ROOT, "§c%.2fs §7(§c0/s§7)", durationSec);
        } else if (node.isModule()) {
            this.rightInfoStr = "§d§l[▦ " + Component.translatable("gui.gtcalcboard.module").getString() + "]";
        } else {
            this.rightInfoStr = isBatch
                    ? String.format(Locale.ROOT, "§b%.2fs §7(§f1x§7)", durationSec)
                    : String.format(Locale.ROOT, "§b%.2fs §7(§f%s/s§7)", durationSec, NumberFormatUtil.formatCompactNumber(effCps));
        }
        this.rightInfoW = font.width(this.rightInfoStr);

        int maxPowerW = Math.max(20, (cardW - 12) - this.rightInfoW - 4);
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        String powerStr;
        if (node.getEnergyType() == EnergyType.NONE) {
            powerStr = Component.translatable("gui.gtcalcboard.energy_passive_stat").getString();
        } else if (!isOperational) {
            powerStr = formatUnoperationalPower(node);
        } else {
            powerStr = adapter.formatEnergyStats(node, BoardManager.getInstance().getPowerDisplayMode());
        }
        this.fittedPowerStr = font.plainSubstrByWidth(powerStr, maxPowerW);
    }

    private String formatUnoperationalPower(RecipeNode node) {
        EnergyType energyType = node.getEnergyType();
        if (energyType == EnergyType.KINETIC_SU) {
            if (node.getProperties().get(com.gtceu.calcboard.compat.greate.GreateProperties.IS_GREATE)) {
                int mTier = Math.max(0, node.getProperties().get(com.gtceu.calcboard.compat.greate.GreateProperties.MACHINE_TIER));
                int rTier = Math.max(0, node.getProperties().get(com.gtceu.calcboard.compat.greate.GreateProperties.REQUIRED_RECIPE_TIER));
                double singleCap = com.gtceu.calcboard.compat.greate.GreateProperties.getShaftCapacityForTier(mTier);
                double totalCap = singleCap * Math.max(1.0, node.getMachineCount());
                IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
                double singleStress = adapter.computeOverclock(node, node.getTargetTier(), false).eut() * node.getCombinedEutMultiplier();
                double totalStress = singleStress * node.getMachineCount();
                if (singleStress > singleCap) {
                    return String.format(Locale.ROOT, "§c%,.0f / %,.0f SU", totalStress, totalCap);
                }
                if (mTier < rTier) {
                    return "§c0 SU §7(Req: " + com.gtceu.calcboard.compat.greate.GreateProperties.getTierName(rTier) + ")";
                }
                return String.format(Locale.ROOT, "§c0 / %,.0f SU", totalCap);
            }
            return "§c0 SU";
        }
        if (energyType == EnergyType.ELECTRIC_FE) {
            return "§c0 FE/t";
        }
        if (energyType == EnergyType.HEAT_OR_SELF) {
            return "§c0 mB/t Steam";
        }
        GTVoltageTier tier = node.getTargetTier();
        String tierName = tier != null ? tier.getName() : "LV";
        return "§c0.0 EU/t §7(0A " + tierName + ")";
    }

    private void updateBadges(RecipeNode node) {
        this.badges = NodeBadgeRegistry.getBadgesForNode(node);
    }

    private record RawPortData(String rateStr, int textColor, int portColor) {}

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
            buildRowPortTexts(widget, font, graph, node, cardW, isOperational, inputs, outputs, visInputs, visOutputs, isFlipped, r);
        }
    }

    private void buildRowPortTexts(NodeWidget widget, Font font, FlowGraph graph, RecipeNode node, int cardW, boolean isOperational,
                                  List<IngredientStack> inputs, List<IngredientStack> outputs,
                                  List<Integer> visInputs, List<Integer> visOutputs, boolean isFlipped, int r) {
        boolean hasInput = (r < visInputs.size());
        boolean hasOutput = (r < visOutputs.size());
        int inOrigIdx = hasInput ? visInputs.get(r) : -1;
        int outOrigIdx = hasOutput ? visOutputs.get(r) : -1;

        boolean hasLeft = !isFlipped ? hasInput : hasOutput;
        boolean hasRight = !isFlipped ? hasOutput : hasInput;

        RawPortData leftData = null;
        if (hasLeft) {
            leftData = !isFlipped
                    ? computeInputPortData(widget, graph, node, inputs.get(inOrigIdx), inOrigIdx, isOperational)
                    : computeOutputPortData(widget, graph, node, outputs.get(outOrigIdx), outOrigIdx, isOperational);
        }

        RawPortData rightData = null;
        if (hasRight) {
            rightData = !isFlipped
                    ? computeOutputPortData(widget, graph, node, outputs.get(outOrigIdx), outOrigIdx, isOperational)
                    : computeInputPortData(widget, graph, node, inputs.get(inOrigIdx), inOrigIdx, isOperational);
        }

        int[] allocatedW = allocatePortWidths(font, leftData, rightData, cardW);
        leftPortTexts.add(createPortText(font, leftData, allocatedW[0]));
        rightPortTexts.add(createPortText(font, rightData, allocatedW[1]));
    }

    private int[] allocatePortWidths(Font font, RawPortData leftData, RawPortData rightData, int cardW) {
        if (leftData == null && rightData == null) {
            return new int[]{0, 0};
        }
        if (leftData != null && rightData == null) {
            return new int[]{cardW - 36, 0};
        }
        if (leftData == null) {
            return new int[]{0, cardW - 36};
        }

        int totalAvailableW = Math.max(40, cardW - 68);
        int rawLeftW = font.width(leftData.rateStr());
        int rawRightW = font.width(rightData.rateStr());

        if (rawLeftW + rawRightW <= totalAvailableW) {
            return new int[]{rawLeftW, rawRightW};
        }

        int halfW = totalAvailableW / 2;
        if (rawLeftW <= halfW) {
            return new int[]{rawLeftW, totalAvailableW - rawLeftW};
        }
        if (rawRightW <= halfW) {
            return new int[]{totalAvailableW - rawRightW, rawRightW};
        }

        double ratio = (double) rawLeftW / (rawLeftW + rawRightW);
        int maxLeftW = (int) Math.round(totalAvailableW * ratio);
        return new int[]{maxLeftW, totalAvailableW - maxLeftW};
    }

    private PortText createPortText(Font font, RawPortData data, int maxTextW) {
        if (data == null) return null;
        String text = data.rateStr();
        int textW = font.width(text);
        if (textW > maxTextW) {
            text = font.plainSubstrByWidth(text, maxTextW);
            textW = font.width(text);
        }
        return new PortText(text, textW, data.textColor(), data.portColor());
    }

    private RawPortData computeInputPortData(NodeWidget widget, FlowGraph graph, RecipeNode node, IngredientStack in, int inOrigIdx, boolean isOperational) {
        if (FormatUtil.getActiveTimeUnit().isRecipeBatchMode()) {
            return computeBatchInputPortData(graph, node, in, inOrigIdx, isOperational);
        }
        double rate = widget.getInputRate(inOrigIdx);
        FlowGraphSolver.PortFlowStats stats = graph != null ? graph.getInputPortStats(node, inOrigIdx) : null;
        boolean isConnected = stats != null && stats.isConnected();
        boolean isBalanced = stats != null && stats.isBalanced();
        boolean isDeficit = stats != null && stats.isInputDeficit();
        boolean isThrottled = stats != null && stats.isUpstreamThrottled();
        boolean isBuffered = graph != null && graph.findConnectedBufferNode(node, inOrigIdx) != null;

        int portColor = !isOperational ? 0xFF77333B
                : (!isConnected ? 0xFF5599FF
                : (isBalanced ? 0xFF55FF88
                : (isDeficit ? (isBuffered ? 0xFFFFD700 : 0xFFFFAA33)
                : (isThrottled ? 0xFF5599FF : 0xFF55FFFF))));

        if (!isOperational) {
            return new RawPortData("§c-" + FormatUtil.formatRate(0.0, in), 0xFFFF7777, portColor);
        }
        if (!isConnected) {
            return new RawPortData("§7-" + FormatUtil.formatRate(rate, in), 0xFFFFAAAA, portColor);
        }
        if (isBalanced) {
            return new RawPortData("§a" + FormatUtil.formatRate(rate, in) + " §2✔", 0xFFFFFFFF, portColor);
        }
        return new RawPortData(FormatUtil.formatConnectedInput(stats.connectedRate(), rate, in, isDeficit, isBuffered, isThrottled), 0xFFFFFFFF, portColor);
    }

    private RawPortData computeBatchInputPortData(FlowGraph graph, RecipeNode node, IngredientStack in, int inOrigIdx, boolean isOperational) {
        FlowGraphSolver.PortFlowStats stats = graph != null ? graph.getBatchInputPortStats(node, inOrigIdx) : null;
        boolean isConnected = stats != null && stats.isConnected();
        boolean isBalanced = stats != null && stats.isBalanced();
        boolean isDeficit = stats != null && stats.isInputDeficit();

        int portColor = !isOperational ? 0xFF77333B
                : (!isConnected ? 0xFF5599FF
                : (isBalanced ? 0xFF55FF88
                : (isDeficit ? 0xFFFFAA33 : 0xFF55FFFF)));

        if (!isOperational) {
            return new RawPortData("§c-" + FormatUtil.formatRecipeBatchAmount(0.0, in), 0xFFFF7777, portColor);
        }
        double reqAmount = in.getAmount();
        if (!isConnected) {
            return new RawPortData("§7-" + FormatUtil.formatRecipeBatchAmount(reqAmount, in), 0xFFFFAAAA, portColor);
        }
        if (isBalanced) {
            return new RawPortData("§a" + FormatUtil.formatRecipeBatchAmount(reqAmount, in) + " §2✔", 0xFFFFFFFF, portColor);
        }
        return new RawPortData(FormatUtil.formatBatchConnectedInput(stats.connectedRate(), reqAmount, in, isDeficit), 0xFFFFFFFF, portColor);
    }

    private RawPortData computeOutputPortData(NodeWidget widget, FlowGraph graph, RecipeNode node, IngredientStack out, int outOrigIdx, boolean isOperational) {
        if (FormatUtil.getActiveTimeUnit().isRecipeBatchMode()) {
            return computeBatchOutputPortData(graph, node, out, outOrigIdx, isOperational);
        }
        double rate = widget.getOutputRate(outOrigIdx);
        FlowGraphSolver.PortFlowStats stats = graph != null ? graph.getOutputPortStats(node, outOrigIdx) : null;
        boolean isConnected = stats != null && stats.isConnected();
        boolean isBalanced = stats != null && stats.isBalanced();
        boolean isSurplus = stats != null && stats.isOutputSurplus();
        boolean isDeficit = stats != null && stats.isOutputDeficit();
        boolean isInactive = rate <= 0.00001;

        int portColor = !isOperational ? 0xFF77333B : (!isConnected ? (isInactive ? 0xFF444B5A : 0xFF55FF88) : (isBalanced ? 0xFF55FF88 : (isDeficit ? 0xFFFFAA33 : 0xFF55FFFF)));

        if (!isOperational) {
            return new RawPortData("§c" + FormatUtil.formatRate(0.0, out) + " §4⏸", 0xFFFF7777, portColor);
        }
        if (!isConnected) {
            if (isInactive) {
                return new RawPortData("§8+0/s §7(0%)", 0xFF778092, portColor);
            }
            return new RawPortData("§a+" + FormatUtil.formatRate(rate, out), 0xFFAAFFAA, portColor);
        }
        if (isBalanced) {
            return new RawPortData("§a" + FormatUtil.formatRate(rate, out) + " §2✔", 0xFFFFFFFF, portColor);
        }
        return new RawPortData(FormatUtil.formatConnectedOutput(rate, stats.connectedRate(), out, isDeficit), 0xFFFFFFFF, portColor);
    }

    private RawPortData computeBatchOutputPortData(FlowGraph graph, RecipeNode node, IngredientStack out, int outOrigIdx, boolean isOperational) {
        FlowGraphSolver.PortFlowStats stats = graph != null ? graph.getBatchOutputPortStats(node, outOrigIdx) : null;
        boolean isConnected = stats != null && stats.isConnected();
        boolean isBalanced = stats != null && stats.isBalanced();
        boolean isDeficit = stats != null && stats.isOutputDeficit();

        int portColor = !isOperational ? 0xFF77333B
                : (!isConnected ? 0xFF55FF88
                : (isBalanced ? 0xFF55FF88
                : (isDeficit ? 0xFFFFAA33 : 0xFF55FFFF)));

        if (!isOperational) {
            return new RawPortData("§c" + FormatUtil.formatRecipeBatchAmount(0.0, out) + " §4⏸", 0xFFFF7777, portColor);
        }
        double prodAmount = out.getAmount() * out.getChance();
        if (!isConnected) {
            return new RawPortData("§a+" + FormatUtil.formatRecipeBatchAmount(prodAmount, out), 0xFFAAFFAA, portColor);
        }
        if (isBalanced) {
            return new RawPortData("§a" + FormatUtil.formatRecipeBatchAmount(prodAmount, out) + " §2✔", 0xFFFFFFFF, portColor);
        }
        return new RawPortData(FormatUtil.formatBatchConnectedOutput(prodAmount, stats.connectedRate(), out, isDeficit), 0xFFFFFFFF, portColor);
    }
}
