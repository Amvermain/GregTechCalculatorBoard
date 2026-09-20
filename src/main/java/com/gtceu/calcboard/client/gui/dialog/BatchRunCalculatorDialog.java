package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.solver.BatchRunResult;
import com.gtceu.calcboard.api.solver.BatchRunSolver;
import com.gtceu.calcboard.api.solver.FlowSummaryAggregator;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.modal.IBoardModal;
import com.gtceu.calcboard.client.gui.dialog.modal.ModalRenderContext;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interactive modal dialog for computing finite batch processing runs.
 * Supports input-driven limiting reagent and output-driven target production modes.
 */
public class BatchRunCalculatorDialog implements IBoardModal {

    private static final int DIALOG_WIDTH = 480;
    private static final int DIALOG_HEIGHT = 290;
    private static final int VISIBLE_CANDIDATE_COUNT = 3;
    private static final int ROW_HEIGHT = 18;

    private final BoardScreen parent;
    private boolean visible = false;
    private boolean isInputMode = true;

    private IngredientStack selectedIngredient = null;
    private EditBox amountEditBox;
    private BatchRunResult currentResult = BatchRunResult.empty();

    private int candidateOffset = 0;
    private double leftScrollY = 0.0;
    private double rightScrollY = 0.0;
    private double maxLeftScrollY = 0.0;
    private double maxRightScrollY = 0.0;

    private IngredientStack hoveredStack = null;
    private double hoveredAmount = 0.0;
    private IngredientStack hoveredCandidate = null;

    public BatchRunCalculatorDialog(BoardScreen parent) {
        this.parent = parent;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    public void open(IngredientStack preselected, boolean isInput) {
        this.visible = true;
        this.isInputMode = isInput;
        this.candidateOffset = 0;
        this.leftScrollY = 0.0;
        this.rightScrollY = 0.0;

        BalanceSummary summary = getEffectiveSummary();
        List<IngredientStack> candidates = getCandidates(summary);

        if (preselected != null) {
            this.selectedIngredient = findMatchingStack(candidates, preselected);
            if (this.selectedIngredient == null) {
                this.selectedIngredient = preselected;
            }
        } else {
            this.selectedIngredient = !candidates.isEmpty() ? candidates.get(0) : null;
        }

        adjustCandidateOffsetToSelected(candidates);
        initEditBox();
        recalculate();
    }

    @Override
    public void close() {
        this.visible = false;
        this.amountEditBox = null;
        this.hoveredStack = null;
        this.hoveredCandidate = null;
    }

    @Override
    public boolean requiresBackdropDim() {
        return true;
    }

    @Override
    public boolean closesOnOutsideClick() {
        return true;
    }

    @Override
    public GuiEventListener getFocusedWidget() {
        return amountEditBox;
    }

    private void initEditBox() {
        Font font = Minecraft.getInstance().font;
        int dialogX = (parent.width - DIALOG_WIDTH) / 2;
        int dialogY = (parent.height - DIALOG_HEIGHT) / 2;
        int boxX = dialogX + 322;
        int boxY = dialogY + 62;

        this.amountEditBox = new EditBox(font, boxX, boxY, 146, 18, Component.empty());
        this.amountEditBox.setMaxLength(32);
        this.amountEditBox.setValue(getDefaultAmountString(selectedIngredient));
        this.amountEditBox.setResponder(text -> recalculate());
        this.amountEditBox.setFocused(true);
    }

    private String getDefaultAmountString(IngredientStack stack) {
        if (stack == null) {
            return "100";
        }
        if (stack.isFluid()) {
            return stack.getAmount() >= 1000.0 ? FormatUtil.formatEditAmount(stack.getAmount(), true) : "10000mB";
        }
        return stack.getAmount() > 1.0 ? FormatUtil.formatEditAmount(stack.getAmount(), false) : "64";
    }

    private void recalculate() {
        BalanceSummary summary = getEffectiveSummary();
        if (summary == null || selectedIngredient == null || amountEditBox == null) {
            this.currentResult = BatchRunResult.empty();
            return;
        }

        double parsedAmount = FormatUtil.parseBatchAmount(amountEditBox.getValue(), selectedIngredient.isFluid());
        this.currentResult = BatchRunSolver.calculate(summary, selectedIngredient, parsedAmount, isInputMode);
    }

    private void switchMode(boolean inputMode) {
        if (this.isInputMode == inputMode) {
            return;
        }
        this.isInputMode = inputMode;
        this.candidateOffset = 0;
        this.leftScrollY = 0.0;
        this.rightScrollY = 0.0;

        BalanceSummary summary = getEffectiveSummary();
        List<IngredientStack> candidates = getCandidates(summary);

        if (!containsCandidate(candidates, selectedIngredient)) {
            this.selectedIngredient = !candidates.isEmpty() ? candidates.get(0) : null;
            if (amountEditBox != null) {
                amountEditBox.setValue(getDefaultAmountString(selectedIngredient));
            }
        }
        adjustCandidateOffsetToSelected(candidates);
        recalculate();
        playClickSound();
    }

    private void selectCandidate(IngredientStack stack) {
        if (stack == null || stack.equals(selectedIngredient)) {
            return;
        }
        this.selectedIngredient = stack;
        if (amountEditBox != null) {
            amountEditBox.setValue(getDefaultAmountString(stack));
        }
        recalculate();
        playClickSound();
    }

    @Override
    public void renderModal(ModalRenderContext context) {
        if (!visible) return;

        GuiGraphics graphics = context.graphics();
        Font font = Minecraft.getInstance().font;
        int mouseX = context.mouseX();
        int mouseY = context.mouseY();

        int dialogX = (context.screenWidth() - DIALOG_WIDTH) / 2;
        int dialogY = (context.screenHeight() - DIALOG_HEIGHT) / 2;

        hoveredStack = null;
        hoveredCandidate = null;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600.0f);

        renderBackground(graphics, dialogX, dialogY);
        renderHeader(graphics, font, dialogX, dialogY, mouseX, mouseY);
        renderModeTabs(graphics, font, dialogX, dialogY, mouseX, mouseY);

        BalanceSummary summary = getEffectiveSummary();
        List<IngredientStack> candidates = getCandidates(summary);
        renderCandidateSelector(graphics, font, dialogX, dialogY, candidates, summary, mouseX, mouseY);
        renderAmountInput(graphics, font, dialogX, dialogY, mouseX, mouseY);

        renderSummaryBar(graphics, font, dialogX, dialogY);
        renderBreakdownColumns(graphics, font, dialogX, dialogY, mouseX, mouseY);

        graphics.pose().popPose();

        renderTooltips(graphics, font, mouseX, mouseY);
    }

    private void renderBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xF50F172A);
        graphics.renderOutline(x, y, DIALOG_WIDTH, DIALOG_HEIGHT, 0xFF334155);
    }

    private void renderHeader(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        graphics.fill(x, y, x + DIALOG_WIDTH, y + 22, 0xFF1E293B);
        graphics.renderOutline(x, y, DIALOG_WIDTH, 22, 0xFF334155);

        String title = "⏱ " + Component.translatable("gui.gtcalcboard.dialog.batch_run.title").getString();
        graphics.drawString(font, "§6" + title, x + 8, y + 7, 0xFFFFFFFF, false);

        int closeX = x + DIALOG_WIDTH - 18;
        int closeY = y + 3;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 15 && mouseY >= closeY && mouseY <= closeY + 15;
        graphics.fill(closeX, closeY, closeX + 15, closeY + 15, closeHover ? 0xFF7F1D1D : 0xFF1E293B);
        graphics.drawCenteredString(font, "✕", closeX + 8, closeY + 4, closeHover ? 0xFFFFFFFF : 0xFF94A3B8);
    }

    private void renderModeTabs(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int tabY = y + 26;
        int tabW = 150;
        int tabH = 18;

        int tab1X = x + 12;
        boolean tab1Hover = mouseX >= tab1X && mouseX <= tab1X + tabW && mouseY >= tabY && mouseY <= tabY + tabH;
        renderTab(graphics, font, tab1X, tabY, tabW, tabH, "⬇ " + Component.translatable("gui.gtcalcboard.dialog.batch_run.tab_input").getString(), isInputMode, tab1Hover);

        int tab2X = tab1X + tabW + 6;
        boolean tab2Hover = mouseX >= tab2X && mouseX <= tab2X + tabW && mouseY >= tabY && mouseY <= tabY + tabH;
        renderTab(graphics, font, tab2X, tabY, tabW, tabH, "⬆ " + Component.translatable("gui.gtcalcboard.dialog.batch_run.tab_output").getString(), !isInputMode, tab2Hover);
    }

    private void renderTab(GuiGraphics graphics, Font font, int tx, int ty, int tw, int th, String text, boolean active, boolean hover) {
        int bg = active ? 0xFF1E3A8A : (hover ? 0xFF334155 : 0xFF1E293B);
        int border = active ? 0xFF38BDF8 : (hover ? 0xFF94A3B8 : 0xFF334155);
        int textColor = active ? 0xFFFFFFFF : 0xFF94A3B8;

        graphics.fill(tx, ty, tx + tw, ty + th, bg);
        graphics.renderOutline(tx, ty, tw, th, border);
        graphics.drawCenteredString(font, text, tx + tw / 2, ty + 5, textColor);
    }

    private void renderCandidateSelector(GuiGraphics graphics, Font font, int x, int y, List<IngredientStack> candidates, BalanceSummary summary, int mouseX, int mouseY) {
        int areaX = x + 12;
        int areaY = y + 48;
        String label = Component.translatable("gui.gtcalcboard.dialog.batch_run.select_resource").getString();
        graphics.drawString(font, "§7" + label, areaX, areaY, 0xFFAAAAAA, false);

        int stripY = areaY + 14;
        if (candidates.isEmpty()) {
            String emptyKey = isInputMode ? "gui.gtcalcboard.dialog.batch_run.no_inputs" : "gui.gtcalcboard.dialog.batch_run.no_outputs";
            graphics.drawString(font, "§8" + Component.translatable(emptyKey).getString(), areaX + 4, stripY + 10, 0xFF777777, false);
            return;
        }

        boolean hasPagination = candidates.size() > VISIBLE_CANDIDATE_COUNT;
        int startX = areaX;

        if (hasPagination) {
            renderNavButton(graphics, font, areaX, stripY, "◀", candidateOffset > 0, mouseX, mouseY);
            startX += 16;
        }

        int chipW = 88;
        int chipH = 30;
        int limit = Math.min(VISIBLE_CANDIDATE_COUNT, candidates.size() - candidateOffset);

        for (int i = 0; i < limit; i++) {
            int index = candidateOffset + i;
            IngredientStack candidate = candidates.get(index);
            int chipX = startX + i * (chipW + 4);
            renderCandidateChip(graphics, font, chipX, stripY, chipW, chipH, candidate, summary, mouseX, mouseY);
        }

        if (hasPagination) {
            int nextX = startX + VISIBLE_CANDIDATE_COUNT * (chipW + 4);
            boolean canNext = candidateOffset + VISIBLE_CANDIDATE_COUNT < candidates.size();
            renderNavButton(graphics, font, nextX, stripY, "▶", canNext, mouseX, mouseY);
        }
    }

    private void renderNavButton(GuiGraphics graphics, Font font, int bx, int by, String symbol, boolean enabled, int mouseX, int mouseY) {
        int bw = 12;
        int bh = 30;
        boolean hover = enabled && mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        int bg = hover ? 0xFF334155 : 0xFF1E293B;
        int border = hover ? 0xFF38BDF8 : 0xFF334155;

        graphics.fill(bx, by, bx + bw, by + bh, bg);
        graphics.renderOutline(bx, by, bw, bh, border);
        graphics.drawCenteredString(font, symbol, bx + bw / 2, by + 11, enabled ? 0xFFE2E8F0 : 0xFF475569);
    }

    private void renderCandidateChip(GuiGraphics graphics, Font font, int cx, int cy, int cw, int ch, IngredientStack candidate, BalanceSummary summary, int mouseX, int mouseY) {
        boolean selected = candidate.equals(selectedIngredient);
        boolean hover = mouseX >= cx && mouseX <= cx + cw && mouseY >= cy && mouseY <= cy + ch;

        if (hover) {
            hoveredCandidate = candidate;
        }

        int bg = selected ? 0xFF1E3A8A : (hover ? 0xFF1E293B : 0xFF0F172A);
        int border = selected ? 0xFFF59E0B : (hover ? 0xFF38BDF8 : 0xFF334155);

        graphics.fill(cx, cy, cx + cw, cy + ch, bg);
        graphics.renderOutline(cx, cy, cw, ch, border);

        IngredientRenderer.render(graphics, candidate, cx + 4, cy + 7);

        String name = font.plainSubstrByWidth(candidate.getDisplayName(), cw - 24);
        graphics.drawString(font, name, cx + 22, cy + 5, selected ? 0xFFF59E0B : 0xFFFFFFFF, false);

        double rate = isInputMode ? summary.rawInputs().getOrDefault(candidate, 0.0) : summary.netOutputs().getOrDefault(candidate, 0.0);
        String rateStr = font.plainSubstrByWidth(FormatUtil.formatRate(rate, candidate), cw - 24);
        graphics.drawString(font, "§7" + rateStr, cx + 22, cy + 16, 0xFF94A3B8, false);
    }

    private void renderAmountInput(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int areaX = x + 322;
        int areaY = y + 48;

        String label = Component.translatable("gui.gtcalcboard.dialog.batch_run.amount_label").getString();
        graphics.drawString(font, "§7" + label, areaX, areaY, 0xFFAAAAAA, false);

        if (amountEditBox != null) {
            amountEditBox.setX(areaX);
            amountEditBox.setY(areaY + 14);
            amountEditBox.render(graphics, mouseX, mouseY, 0);
        }
    }

    private void renderSummaryBar(GuiGraphics graphics, Font font, int x, int y) {
        int barX = x + 12;
        int barY = y + 96;
        int barW = DIALOG_WIDTH - 24;
        int barH = 22;

        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF131B2A);
        graphics.renderOutline(barX, barY, barW, barH, 0xFF1E293B);

        String durStr = formatDurationDisplay(currentResult);
        graphics.drawString(font, durStr, barX + 8, barY + 7, 0xFFFFFFFF, false);

        String energyRight = formatEnergyDisplay(currentResult, font);
        int rightW = font.width(energyRight);
        graphics.drawString(font, energyRight, barX + barW - rightW - 8, barY + 7, 0xFFFFFFFF, false);
    }

    private String formatDurationDisplay(BatchRunResult result) {
        String durLabel = Component.translatable("gui.gtcalcboard.dialog.batch_run.duration").getString();
        if (result == null || result.durationSeconds() <= 0.0) {
            return "⏱ " + durLabel + ": §70s";
        }
        if (result.isInfinite()) {
            return "⏱ " + durLabel + ": §c" + Component.translatable("gui.gtcalcboard.dialog.batch_run.duration_infinite").getString();
        }
        String etaStr = FormatUtil.formatETA(result.durationSeconds());
        String details = String.format(Locale.ROOT, "%.1fs / %,dt", result.durationSeconds(), result.durationTicks());
        return "⏱ " + durLabel + ": §e" + etaStr + " §7(" + details + ")";
    }

    private String formatEnergyDisplay(BatchRunResult result, Font font) {
        if (result == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (Math.abs(result.totalEnergyEU()) > 0.01) {
            String euVal = FormatUtil.formatCompactNumber(Math.abs(result.totalEnergyEU()));
            sb.append(result.totalEnergyEU() < 0 ? "§a⚡ +" : "§e⚡ -").append(euVal).append(" EU  ");
        }
        if (Math.abs(result.totalEnergyFE()) > 0.01) {
            String feVal = FormatUtil.formatCompactNumber(Math.abs(result.totalEnergyFE()));
            sb.append(result.totalEnergyFE() > 0 ? "§a⚡ +" : "§c⚡ -").append(feVal).append(" FE  ");
        }
        if (Math.abs(result.totalEnergySU()) > 0.01) {
            String suVal = FormatUtil.formatCompactNumber(Math.abs(result.totalEnergySU()));
            sb.append(result.totalEnergySU() > 0 ? "§a⚙ +" : "§c⚙ -").append(suVal).append(" SU·s  ");
        }
        sb.append("§7▦ ").append(result.totalMachineCount()).append(Component.translatable("gui.gtcalcboard.machine_unit").getString());
        return sb.toString();
    }

    private void renderBreakdownColumns(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int colY = y + 122;
        int colW = (DIALOG_WIDTH - 30) / 2;
        int colH = DIALOG_HEIGHT - 130;

        int leftX = x + 12;
        renderColumnHeader(graphics, font, leftX, colY, "§c« " + Component.translatable("gui.gtcalcboard.dialog.batch_run.required_inputs").getString());
        renderInputList(graphics, font, leftX, colY + 14, colW, colH - 14, mouseX, mouseY);

        int rightX = leftX + colW + 6;
        renderColumnHeader(graphics, font, rightX, colY, "§a» " + Component.translatable("gui.gtcalcboard.dialog.batch_run.produced_outputs").getString());
        renderOutputList(graphics, font, rightX, colY + 14, colW, colH - 14, mouseX, mouseY);
    }

    private void renderColumnHeader(GuiGraphics graphics, Font font, int cx, int cy, String title) {
        graphics.drawString(font, title, cx, cy, 0xFFFFFFFF, false);
    }

    private void renderInputList(GuiGraphics graphics, Font font, int cx, int cy, int cw, int ch, int mouseX, int mouseY) {
        graphics.fill(cx, cy, cx + cw, cy + ch, 0xEE0A0F1A);
        graphics.renderOutline(cx, cy, cw, ch, 0xFF1E293B);

        if (currentResult.isInfinite()) {
            graphics.drawString(font, "§c" + Component.translatable("gui.gtcalcboard.dialog.batch_run.duration_infinite").getString(), cx + 8, cy + 8, 0xFFEF4444, false);
            return;
        }

        Map<IngredientStack, Double> inputs = currentResult.requiredInputs();
        if (inputs.isEmpty()) {
            graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.dialog.batch_run.no_inputs").getString(), cx + 8, cy + 8, 0xFF777777, false);
            return;
        }

        int totalH = inputs.size() * ROW_HEIGHT;
        maxLeftScrollY = Math.max(0, totalH - ch);
        leftScrollY = Math.max(0, Math.min(maxLeftScrollY, leftScrollY));

        BoardScissorHelper.enableScissor(graphics, cx + 1, cy + 1, cx + cw - 1, cy + ch - 1);
        int itemY = cy + 2 - (int) leftScrollY;

        for (Map.Entry<IngredientStack, Double> entry : inputs.entrySet()) {
            if (itemY >= cy - ROW_HEIGHT && itemY <= cy + ch) {
                renderMaterialRow(graphics, font, cx, itemY, cw, entry.getKey(), entry.getValue(), true, mouseX, mouseY, cy, ch);
            }
            itemY += ROW_HEIGHT;
        }

        BoardScissorHelper.disableScissor(graphics);
        renderScrollbar(graphics, cx + cw - 4, cy, ch, leftScrollY, maxLeftScrollY, totalH);
    }

    private void renderOutputList(GuiGraphics graphics, Font font, int cx, int cy, int cw, int ch, int mouseX, int mouseY) {
        graphics.fill(cx, cy, cx + cw, cy + ch, 0xEE0A0F1A);
        graphics.renderOutline(cx, cy, cw, ch, 0xFF1E293B);

        if (currentResult.isInfinite()) {
            graphics.drawString(font, "§c" + Component.translatable("gui.gtcalcboard.dialog.batch_run.duration_infinite").getString(), cx + 8, cy + 8, 0xFFEF4444, false);
            return;
        }

        Map<IngredientStack, Double> outputs = currentResult.producedOutputs();
        Map<IngredientStack, Double> voided = currentResult.voidedOutputs();
        if (outputs.isEmpty() && voided.isEmpty()) {
            graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.dialog.batch_run.no_outputs").getString(), cx + 8, cy + 8, 0xFF777777, false);
            return;
        }

        int totalH = (outputs.size() + voided.size()) * ROW_HEIGHT;
        maxRightScrollY = Math.max(0, totalH - ch);
        rightScrollY = Math.max(0, Math.min(maxRightScrollY, rightScrollY));

        BoardScissorHelper.enableScissor(graphics, cx + 1, cy + 1, cx + cw - 1, cy + ch - 1);
        int itemY = cy + 2 - (int) rightScrollY;

        for (Map.Entry<IngredientStack, Double> entry : outputs.entrySet()) {
            if (itemY >= cy - ROW_HEIGHT && itemY <= cy + ch) {
                renderMaterialRow(graphics, font, cx, itemY, cw, entry.getKey(), entry.getValue(), false, mouseX, mouseY, cy, ch);
            }
            itemY += ROW_HEIGHT;
        }

        for (Map.Entry<IngredientStack, Double> entry : voided.entrySet()) {
            if (itemY >= cy - ROW_HEIGHT && itemY <= cy + ch) {
                renderVoidedRow(graphics, font, cx, itemY, cw, entry.getKey(), entry.getValue(), mouseX, mouseY, cy, ch);
            }
            itemY += ROW_HEIGHT;
        }

        BoardScissorHelper.disableScissor(graphics);
        renderScrollbar(graphics, cx + cw - 4, cy, ch, rightScrollY, maxRightScrollY, totalH);
    }

    private void renderMaterialRow(GuiGraphics graphics, Font font, int rx, int ry, int rw, IngredientStack stack, double amount, boolean isInput, int mouseX, int mouseY, int clipY, int clipH) {
        boolean isRef = stack.matchesOrAlternative(selectedIngredient) || (selectedIngredient != null && selectedIngredient.matchesOrAlternative(stack));

        if (isRef) {
            graphics.fill(rx + 2, ry, rx + rw - 2, ry + ROW_HEIGHT - 2, 0x33F59E0B);
            graphics.renderOutline(rx + 2, ry, rw - 4, ROW_HEIGHT - 2, 0x66F59E0B);
        }

        IngredientRenderer.render(graphics, stack, rx + 4, ry - 1);

        String amtStr = (isInput ? "§c-" : "§a+") + FormatUtil.formatRecipeBatchAmount(amount, stack);
        int amtW = font.width(amtStr);
        graphics.drawString(font, amtStr, rx + rw - amtW - 6, ry + 4, 0xFFFFFFFF, false);

        int maxNameW = Math.max(20, rw - amtW - 32);
        String name = font.plainSubstrByWidth(stack.getDisplayName(), maxNameW);
        graphics.drawString(font, isRef ? "§e" + name : "§f" + name, rx + 24, ry + 4, 0xFFFFFFFF, false);

        if (mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY <= ry + ROW_HEIGHT && mouseY >= clipY && mouseY <= clipY + clipH) {
            hoveredStack = stack;
            hoveredAmount = amount;
        }
    }

    private void renderVoidedRow(GuiGraphics graphics, Font font, int rx, int ry, int rw, IngredientStack stack, double amount, int mouseX, int mouseY, int clipY, int clipH) {
        IngredientRenderer.render(graphics, stack, rx + 4, ry - 1);

        String amtStr = "§d\uD83D\uDDD1 " + FormatUtil.formatRecipeBatchAmount(amount, stack);
        int amtW = font.width(amtStr);
        graphics.drawString(font, amtStr, rx + rw - amtW - 6, ry + 4, 0xFFC084FC, false);

        int maxNameW = Math.max(20, rw - amtW - 32);
        String name = font.plainSubstrByWidth(stack.getDisplayName(), maxNameW);
        graphics.drawString(font, "§7" + name, rx + 24, ry + 4, 0xFF94A3B8, false);

        if (mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY <= ry + ROW_HEIGHT && mouseY >= clipY && mouseY <= clipY + clipH) {
            hoveredStack = stack;
            hoveredAmount = amount;
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int sx, int sy, int sh, double scroll, double maxScroll, int totalH) {
        if (maxScroll <= 0) return;
        graphics.fill(sx, sy, sx + 3, sy + sh, 0x55000000);
        int thumbH = Math.max(16, (int) ((double) sh / totalH * sh));
        int thumbY = sy + (int) ((scroll / maxScroll) * (sh - thumbH));
        graphics.fill(sx, thumbY, sx + 3, thumbY + thumbH, 0xFFAAAAAA);
    }

    private void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (hoveredStack != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(hoveredStack.getDisplayName()));
            String exact = FormatUtil.formatExactBatchAmount(hoveredAmount, hoveredStack.isFluid());
            tooltip.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.amount").getString() + " §f" + exact));
            BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltip, mouseX, mouseY);
            return;
        }

        if (hoveredCandidate != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(hoveredCandidate.getDisplayName()));
            BalanceSummary summary = getEffectiveSummary();
            if (summary != null) {
                double rate = isInputMode ? summary.rawInputs().getOrDefault(hoveredCandidate, 0.0) : summary.netOutputs().getOrDefault(hoveredCandidate, 0.0);
                tooltip.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.summary.rate", FormatUtil.formatExactRate(rate, hoveredCandidate)).getString()));
            }
            BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int screenWidth = parent != null ? parent.width : Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = parent != null ? parent.height : Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return mouseClicked(mouseX, mouseY, button, screenWidth, screenHeight);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!visible) return false;

        int dialogX = (screenWidth - DIALOG_WIDTH) / 2;
        int dialogY = (screenHeight - DIALOG_HEIGHT) / 2;

        if (mouseX < dialogX || mouseX > dialogX + DIALOG_WIDTH || mouseY < dialogY || mouseY > dialogY + DIALOG_HEIGHT) {
            close();
            return false;
        }

        int closeX = dialogX + DIALOG_WIDTH - 18;
        int closeY = dialogY + 3;
        if (mouseX >= closeX && mouseX <= closeX + 15 && mouseY >= closeY && mouseY <= closeY + 15 && button == 0) {
            close();
            playClickSound();
            return true;
        }

        int tabY = dialogY + 26;
        int tabW = 150;
        int tabH = 18;
        if (mouseY >= tabY && mouseY <= tabY + tabH && button == 0) {
            int tab1X = dialogX + 12;
            if (mouseX >= tab1X && mouseX <= tab1X + tabW) {
                switchMode(true);
                return true;
            }
            int tab2X = tab1X + tabW + 6;
            if (mouseX >= tab2X && mouseX <= tab2X + tabW) {
                switchMode(false);
                return true;
            }
        }

        if (handleCandidateClick(mouseX, mouseY, button, dialogX, dialogY)) {
            return true;
        }

        if (amountEditBox != null) {
            amountEditBox.setX(dialogX + 322);
            amountEditBox.setY(dialogY + 62);
            if (amountEditBox.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        return true;
    }

    private boolean handleCandidateClick(double mouseX, double mouseY, int button, int dialogX, int dialogY) {
        if (button != 0) return false;

        BalanceSummary summary = getEffectiveSummary();
        List<IngredientStack> candidates = getCandidates(summary);
        if (candidates.isEmpty()) return false;

        int areaX = dialogX + 12;
        int stripY = dialogY + 62;
        int chipH = 30;
        if (mouseY < stripY || mouseY > stripY + chipH) return false;

        boolean hasPagination = candidates.size() > VISIBLE_CANDIDATE_COUNT;
        int startX = areaX;

        if (hasPagination) {
            if (mouseX >= areaX && mouseX <= areaX + 12 && candidateOffset > 0) {
                candidateOffset = Math.max(0, candidateOffset - 1);
                playClickSound();
                return true;
            }
            startX += 16;
        }

        int chipW = 88;
        int limit = Math.min(VISIBLE_CANDIDATE_COUNT, candidates.size() - candidateOffset);
        for (int i = 0; i < limit; i++) {
            int chipX = startX + i * (chipW + 4);
            if (mouseX >= chipX && mouseX <= chipX + chipW) {
                selectCandidate(candidates.get(candidateOffset + i));
                return true;
            }
        }

        if (hasPagination) {
            int nextX = startX + VISIBLE_CANDIDATE_COUNT * (chipW + 4);
            if (mouseX >= nextX && mouseX <= nextX + 12 && candidateOffset + VISIBLE_CANDIDATE_COUNT < candidates.size()) {
                candidateOffset = Math.min(candidates.size() - VISIBLE_CANDIDATE_COUNT, candidateOffset + 1);
                playClickSound();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;

        int dialogX = (parent.width - DIALOG_WIDTH) / 2;
        int dialogY = (parent.height - DIALOG_HEIGHT) / 2;

        int colY = dialogY + 136;
        int colW = (DIALOG_WIDTH - 30) / 2;
        int colH = DIALOG_HEIGHT - 144;

        int leftX = dialogX + 12;
        if (mouseX >= leftX && mouseX <= leftX + colW && mouseY >= colY && mouseY <= colY + colH) {
            leftScrollY = Math.max(0, Math.min(maxLeftScrollY, leftScrollY - delta * 18));
            return true;
        }

        int rightX = leftX + colW + 6;
        if (mouseX >= rightX && mouseX <= rightX + colW && mouseY >= colY && mouseY <= colY + colH) {
            rightScrollY = Math.max(0, Math.min(maxRightScrollY, rightScrollY - delta * 18));
            return true;
        }

        int candidateY = dialogY + 62;
        if (mouseX >= dialogX + 12 && mouseX <= dialogX + 318 && mouseY >= candidateY && mouseY <= candidateY + 30) {
            BalanceSummary summary = getEffectiveSummary();
            List<IngredientStack> candidates = getCandidates(summary);
            int maxOffset = Math.max(0, candidates.size() - VISIBLE_CANDIDATE_COUNT);
            if (delta < 0 && candidateOffset < maxOffset) {
                candidateOffset++;
                return true;
            } else if (delta > 0 && candidateOffset > 0) {
                candidateOffset--;
                return true;
            }
        }

        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        if (amountEditBox != null && amountEditBox.isFocused()) {
            return amountEditBox.keyPressed(keyCode, scanCode, modifiers);
        }

        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;

        if (amountEditBox != null && amountEditBox.isFocused()) {
            return amountEditBox.charTyped(codePoint, modifiers);
        }

        return false;
    }

    private BalanceSummary getEffectiveSummary() {
        if (parent == null) return null;
        BalanceSummary cached = parent.getCachedSummary();
        if (cached != null) return cached;
        if (parent.getGraph() != null) {
            return FlowSummaryAggregator.computeSummary(parent.getGraph());
        }
        return null;
    }

    private List<IngredientStack> getCandidates(BalanceSummary summary) {
        if (summary == null) {
            return List.of();
        }
        Map<IngredientStack, Double> pool = isInputMode ? summary.rawInputs() : summary.netOutputs();
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(pool.keySet());
    }

    private boolean containsCandidate(List<IngredientStack> candidates, IngredientStack target) {
        return findMatchingStack(candidates, target) != null;
    }

    private IngredientStack findMatchingStack(List<IngredientStack> candidates, IngredientStack target) {
        if (candidates == null || target == null) return null;
        for (IngredientStack c : candidates) {
            if (c.equals(target) || c.matchesOrAlternative(target) || target.matchesOrAlternative(c)) {
                return c;
            }
        }
        return null;
    }

    private void adjustCandidateOffsetToSelected(List<IngredientStack> candidates) {
        if (candidates == null || selectedIngredient == null) return;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).equals(selectedIngredient) || candidates.get(i).matchesOrAlternative(selectedIngredient)) {
                if (i < candidateOffset) {
                    candidateOffset = i;
                } else if (i >= candidateOffset + VISIBLE_CANDIDATE_COUNT) {
                    candidateOffset = Math.max(0, i - VISIBLE_CANDIDATE_COUNT + 1);
                }
                return;
            }
        }
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }
}
