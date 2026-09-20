package com.gtceu.calcboard.client.gui.dialog.config;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Configuration dialog view that allows players to manually override recipe durations,
 * base power (EU/t), and ingredient quantities on machine nodes.
 */
public class RecipeOverrideView {

    private final MachineConfigDialog dialog;
    private RecipeNode node;

    private EditBox durationBox;
    private EditBox powerBox;
    private EditBox amountBox;

    private boolean isInputSelected = true;
    private int selectedIngredientIndex = 0;
    private int inputScroll = 0;
    private int outputScroll = 0;
    private boolean isSyncing = false;

    public RecipeOverrideView(MachineConfigDialog dialog) {
        this.dialog = dialog;
    }

    public void init(RecipeNode node) {
        this.node = node;
        this.inputScroll = 0;
        this.outputScroll = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;

        initDurationBox(mc.font);
        initPowerBox(mc.font);
        initAmountBox(mc.font);

        selectDefaultIngredient();
        syncBoxesFromNode();
    }

    private void initDurationBox(Font font) {
        this.durationBox = new EditBox(font, 0, 0, 34, 14, Component.translatable("gui.gtcalcboard.config.override.duration"));
        this.durationBox.setMaxLength(8);
        this.durationBox.setResponder(text -> {
            if (isSyncing || node == null) return;
            try {
                double val = Double.parseDouble(text.trim());
                if (val > 0.0) {
                    node.overrideDurationTicks(val);
                    notifyGraphChanged();
                }
            } catch (NumberFormatException ignored) {}
        });
    }

    private void initPowerBox(Font font) {
        this.powerBox = new EditBox(font, 0, 0, 36, 14, Component.translatable("gui.gtcalcboard.config.override.power"));
        this.powerBox.setMaxLength(10);
        this.powerBox.setResponder(text -> {
            if (isSyncing || node == null) return;
            try {
                double val = Double.parseDouble(text.trim());
                if (val >= 0.0) {
                    node.overrideBaseEUt(val, node.isGenerator());
                    notifyGraphChanged();
                }
            } catch (NumberFormatException ignored) {}
        });
    }

    private void initAmountBox(Font font) {
        this.amountBox = new EditBox(font, 0, 0, 42, 14, Component.translatable("gui.gtcalcboard.config.override.amount"));
        this.amountBox.setMaxLength(10);
        this.amountBox.setResponder(text -> {
            if (isSyncing || node == null) return;
            try {
                double val = Double.parseDouble(text.trim());
                if (val > 0.0) {
                    applySelectedAmount(val);
                    notifyGraphChanged();
                }
            } catch (NumberFormatException ignored) {}
        });
    }

    private void selectDefaultIngredient() {
        if (node == null) return;
        if (!node.getInputs().isEmpty()) {
            this.isInputSelected = true;
            this.selectedIngredientIndex = 0;
        } else if (!node.getOutputs().isEmpty()) {
            this.isInputSelected = false;
            this.selectedIngredientIndex = 0;
        } else {
            this.selectedIngredientIndex = -1;
        }
    }

    private void syncBoxesFromNode() {
        if (node == null) return;
        isSyncing = true;
        try {
            if (durationBox != null) {
                durationBox.setValue(formatNumber(node.getBaseDurationTicks()));
            }
            if (powerBox != null) {
                powerBox.setValue(formatNumber(node.getBaseEUt()));
            }
            syncAmountBox();
        } finally {
            isSyncing = false;
        }
    }

    private void syncAmountBox() {
        if (amountBox == null || node == null) return;
        IngredientStack stack = getSelectedStack();
        boolean isAux = isSelectedAuxiliary();
        if (stack != null && !isAux) {
            amountBox.setValue(formatNumber(stack.getAmount()));
        } else {
            amountBox.setValue("");
        }
    }

    private IngredientStack getSelectedStack() {
        if (node == null || selectedIngredientIndex < 0) return null;
        List<IngredientStack> list = isInputSelected ? node.getInputs() : node.getOutputs();
        if (selectedIngredientIndex < list.size()) {
            return list.get(selectedIngredientIndex);
        }
        return null;
    }

    private boolean isSelectedAuxiliary() {
        if (node == null || selectedIngredientIndex < 0) return false;
        return isInputSelected
                ? node.isAuxiliaryInputPort(selectedIngredientIndex)
                : node.isAuxiliaryOutputPort(selectedIngredientIndex);
    }

    private void applySelectedAmount(double amount) {
        if (node == null || selectedIngredientIndex < 0 || isSelectedAuxiliary()) return;
        if (isInputSelected) {
            node.overrideInputAmount(selectedIngredientIndex, amount);
        } else {
            node.overrideOutputAmount(selectedIngredientIndex, amount);
        }
    }

    private void notifyGraphChanged() {
        BoardScreen parent = dialog.getParent();
        if (parent != null) {
            parent.markSummaryDirty();
        }
    }

    public void render(GuiGraphics graphics, Font font, int startX, int startY, int width, int height, int mouseX, int mouseY) {
        if (node == null) return;

        renderHeaderControls(graphics, font, startX, startY, width, mouseX, mouseY);
        renderIngredientColumns(graphics, font, startX, startY + 24, width, mouseX, mouseY);
        renderBottomBar(graphics, font, startX, startY + height - 26, width, mouseX, mouseY);
    }

    private HeaderLayout computeHeaderLayout(Font font, int startX, int width) {
        String durLabel = "§f⏱ " + Component.translatable("gui.gtcalcboard.config.override.duration").getString();
        String secStr = String.format(Locale.ROOT, "§7%.2fs", node.getBaseDurationTicks() / 20.0);
        String pwrLabel = "§e⚡ " + Component.translatable("gui.gtcalcboard.config.override.power").getString();
        return new HeaderLayout(font, startX, width, durLabel, secStr, pwrLabel);
    }

    private void renderHeaderControls(GuiGraphics graphics, Font font, int startX, int startY, int width, int mouseX, int mouseY) {
        HeaderLayout layout = computeHeaderLayout(font, startX, width);

        graphics.drawString(font, layout.durLabel, startX + 4, startY + 4, 0xFFFFFFFF, false);
        if (durationBox != null) {
            durationBox.setX(layout.durBoxX);
            durationBox.setY(startY + 2);
            durationBox.setWidth(layout.durBoxW);
            durationBox.render(graphics, mouseX, mouseY, 0);
        }
        graphics.drawString(font, layout.secStr, layout.secX, startY + 4, 0xFFB0C0D8, false);

        renderMiniButton(graphics, font, "-20t", layout.durBtnStartX, startY + 2, 24, mouseX, mouseY);
        renderMiniButton(graphics, font, "+20t", layout.durBtnStartX + 26, startY + 2, 24, mouseX, mouseY);
        renderMiniButton(graphics, font, "1s", layout.durBtnStartX + 52, startY + 2, 18, mouseX, mouseY);
        renderMiniButton(graphics, font, "5s", layout.durBtnStartX + 72, startY + 2, 18, mouseX, mouseY);

        graphics.drawString(font, layout.pwrLabel, layout.pwrStartX, startY + 4, 0xFFFFFFFF, false);
        if (powerBox != null) {
            powerBox.setX(layout.pwrBoxX);
            powerBox.setY(startY + 2);
            powerBox.setWidth(layout.pwrBoxW);
            powerBox.render(graphics, mouseX, mouseY, 0);
        }

        boolean isGen = node.isGenerator();
        boolean genHover = mouseX >= layout.genX && mouseX <= layout.genX + layout.genW && mouseY >= startY + 2 && mouseY <= startY + 16;
        graphics.fill(layout.genX, startY + 2, layout.genX + layout.genW, startY + 16, isGen ? (genHover ? 0xFF285078 : 0xFF1C3D60) : (genHover ? 0xFF353C4D : 0xFF252A36));
        graphics.renderOutline(layout.genX, startY + 2, layout.genW, 14, isGen ? 0xFF58D3FF : 0xFF454E62);
        String genLabel = isGen ? Component.translatable("gui.gtcalcboard.config.override.generator").getString()
                : Component.translatable("gui.gtcalcboard.config.override.consumer").getString();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(genLabel, layout.genW - 4), layout.genX + layout.genW / 2, startY + 4, isGen ? 0xFF58D3FF : 0xFFB0C0D8);

        renderMiniButton(graphics, font, "0", layout.pwrBtnStartX, startY + 2, 14, mouseX, mouseY);
        renderMiniButton(graphics, font, "32", layout.pwrBtnStartX + 16, startY + 2, 18, mouseX, mouseY);
        renderMiniButton(graphics, font, "128", layout.pwrBtnStartX + 36, startY + 2, 22, mouseX, mouseY);
        renderMiniButton(graphics, font, "512", layout.pwrBtnStartX + 60, startY + 2, 22, mouseX, mouseY);
    }

    private void renderIngredientColumns(GuiGraphics graphics, Font font, int startX, int startY, int width, int mouseX, int mouseY) {
        int colW = (width - 8) / 2;
        renderIngredientList(graphics, font, startX, startY, colW, true, mouseX, mouseY);
        renderIngredientList(graphics, font, startX + colW + 8, startY, colW, false, mouseX, mouseY);
    }

    private void renderIngredientList(GuiGraphics graphics, Font font, int x, int y, int width, boolean isInput, int mouseX, int mouseY) {
        String title = isInput ? ("§b⬇ " + Component.translatable("gui.gtcalcboard.config.override.inputs").getString())
                : ("§a⬆ " + Component.translatable("gui.gtcalcboard.config.override.outputs").getString());
        graphics.drawString(font, title, x + 2, y + 2, 0xFFFFFFFF, false);

        List<IngredientStack> list = isInput ? node.getInputs() : node.getOutputs();
        int scroll = isInput ? inputScroll : outputScroll;

        if (list.isEmpty()) {
            String empty = isInput ? Component.translatable("gui.gtcalcboard.config.override.no_inputs").getString()
                    : Component.translatable("gui.gtcalcboard.config.override.no_outputs").getString();
            graphics.drawString(font, "§7" + empty, x + 4, y + 18, 0xFF777777, false);
            return;
        }

        int rowH = 19;
        int maxRows = 3;
        for (int i = 0; i < maxRows; i++) {
            int idx = scroll + i;
            if (idx >= list.size()) break;
            IngredientStack stack = list.get(idx);
            int rowY = y + 14 + i * rowH;
            renderIngredientRow(graphics, font, x, rowY, width, stack, idx, isInput, mouseX, mouseY);
        }
    }

    private void renderIngredientRow(GuiGraphics graphics, Font font, int x, int y, int width,
                                     IngredientStack stack, int index, boolean isInput, int mouseX, int mouseY) {
        boolean selected = (this.isInputSelected == isInput && this.selectedIngredientIndex == index);
        boolean hover = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 18;
        boolean isAux = isInput ? node.isAuxiliaryInputPort(index) : node.isAuxiliaryOutputPort(index);

        int bgCol = selected ? 0xFF2A364F : (hover ? 0xFF202634 : 0xFF181C26);
        int borderCol = selected ? 0xFF58D3FF : (hover ? 0xFF4F5B73 : 0xFF2D3546);
        graphics.fill(x, y, x + width, y + 18, bgCol);
        graphics.renderOutline(x, y, width, 18, borderCol);

        IngredientRenderer.render(graphics, stack, x + 1, y + 1);

        String name = stack.getDisplayName();
        int nameMaxW = width - 96;
        graphics.drawString(font, font.plainSubstrByWidth(name, nameMaxW), x + 20, y + 5, selected ? 0xFFFFFFFF : 0xFFD0D8E6, false);

        String amtStr = formatStackAmount(stack);
        graphics.drawString(font, amtStr, x + width - 72, y + 5, 0xFF58D3FF, false);

        if (isAux) {
            int tagW = 32;
            int tagX = x + width - tagW - 4;
            graphics.fill(tagX, y + 2, tagX + tagW, y + 16, 0xFF222634);
            graphics.renderOutline(tagX, y + 2, tagW, 14, 0xFF3D4658);
            graphics.drawCenteredString(font, "Auto", tagX + tagW / 2, y + 5, 0xFF8E99AC);
        } else {
            renderMiniButton(graphics, font, "-", x + width - 36, y + 2, 14, mouseX, mouseY);
            renderMiniButton(graphics, font, "+", x + width - 18, y + 2, 14, mouseX, mouseY);
        }

        if (hover && mouseX <= x + width - 40) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§f" + stack.getDisplayName()));
            tooltip.add(Component.literal("§7ID: §8" + (stack.getId() != null ? stack.getId().toString() : "unknown")));
            tooltip.add(Component.literal("§bAmount: §f" + formatNumber(stack.getAmount()) + (stack.isFluid() ? " mB" : "")));
            if (isAux) {
                tooltip.add(Component.literal("§e⚠ ").append(Component.translatable("gui.gtcalcboard.config.override.auxiliary_tooltip")));
            }
            dialog.setDeferredTooltip(tooltip);
        }
    }

    private BottomBarLayout computeBottomBarLayout(Font font, int startX, int width) {
        String prefix = Component.translatable("gui.gtcalcboard.config.override.amount").getString();
        return new BottomBarLayout(font, startX, width, prefix);
    }

    private void renderBottomBar(GuiGraphics graphics, Font font, int startX, int startY, int width, int mouseX, int mouseY) {
        BottomBarLayout layout = computeBottomBarLayout(font, startX, width);
        IngredientStack selected = getSelectedStack();
        boolean isAux = isSelectedAuxiliary();

        if (selected != null) {
            IngredientRenderer.render(graphics, selected, startX + 4, startY + 2);
            if (isAux) {
                String auxNotice = Component.translatable("gui.gtcalcboard.config.override.auxiliary_notice").getString();
                graphics.drawString(font, "§e⚙ §7" + auxNotice, startX + 24, startY + 6, 0xFFB0C0D8, false);
            } else {
                graphics.drawString(font, "§f" + layout.prefix, startX + 24, startY + 6, 0xFFFFFFFF, false);

                if (amountBox != null) {
                    amountBox.setX(layout.amtBoxX);
                    amountBox.setY(startY + 3);
                    amountBox.setWidth(layout.amtBoxW);
                    amountBox.render(graphics, mouseX, mouseY, 0);
                }

                renderMiniButton(graphics, font, "-1", layout.btnStartX, startY + 3, 16, mouseX, mouseY);
                renderMiniButton(graphics, font, "+1", layout.btnStartX + 18, startY + 3, 16, mouseX, mouseY);
                renderMiniButton(graphics, font, "/2", layout.btnStartX + 36, startY + 3, 16, mouseX, mouseY);
                renderMiniButton(graphics, font, "x2", layout.btnStartX + 54, startY + 3, 16, mouseX, mouseY);
            }
        }

        boolean overridden = node.isManualOverride();
        boolean resetHover = mouseX >= layout.resetX && mouseX <= layout.resetX + layout.resetW && mouseY >= startY + 2 && mouseY <= startY + 18;

        graphics.fill(layout.resetX, startY + 2, layout.resetX + layout.resetW, startY + 18, overridden ? (resetHover ? 0xFF6B2A2A : 0xFF4A1E1E) : 0xFF252A36);
        graphics.renderOutline(layout.resetX, startY + 2, layout.resetW, 16, overridden ? 0xFFE05252 : 0xFF3D4658);
        String resetText = Component.translatable("gui.gtcalcboard.config.override.reset").getString();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(resetText, layout.resetW - 6), layout.resetX + layout.resetW / 2, startY + 6, overridden ? 0xFFFFFFFF : 0xFF7A8499);

        if (resetHover) {
            dialog.setDeferredTooltip(List.of(
                    Component.literal("§c↺ ").append(Component.translatable("gui.gtcalcboard.config.override.reset")),
                    Component.translatable("gui.gtcalcboard.config.override.reset_tooltip")
            ));
        }

        if (overridden) {
            int statusX = layout.resetX - 140;
            if (statusX > startX + 220) {
                graphics.drawString(font, Component.translatable("gui.gtcalcboard.config.override.active_status").getString(), statusX, startY + 6, 0xFF38BDF8, false);
            }
        }
    }

    private void renderMiniButton(GuiGraphics graphics, Font font, String label, int bx, int by, int bw, int mouseX, int mouseY) {
        boolean h = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 14;
        graphics.fill(bx, by, bx + bw, by + 14, h ? 0xFF3F4E6B : 0xFF283142);
        graphics.renderOutline(bx, by, bw, 14, h ? 0xFF58D3FF : 0xFF3E4B63);
        graphics.drawCenteredString(font, label, bx + bw / 2, by + 3, h ? 0xFFFFFFFF : 0xFFD0D8E6);
    }

    public boolean mouseClicked(double mX, double mY, int button, int startX, int startY, int width, int height, RecipeNode node, BoardScreen parent) {
        if (this.node == null) return false;

        if (handleHeaderClick(mX, mY, startX, startY, width)) return true;
        if (handleIngredientColumnsClick(mX, mY, startX, startY + 24, width)) return true;
        if (handleBottomBarClick(mX, mY, startX, startY + height - 26, width)) return true;

        return false;
    }

    private boolean handleHeaderClick(double mX, double mY, int startX, int startY, int width) {
        Font font = Minecraft.getInstance().font;
        HeaderLayout layout = computeHeaderLayout(font, startX, width);

        if (durationBox != null) {
            durationBox.setX(layout.durBoxX);
            durationBox.setY(startY + 2);
            durationBox.setWidth(layout.durBoxW);
            boolean click = durationBox.mouseClicked(mX, mY, 0);
            durationBox.setFocused(click);
            if (click) return true;
        }

        if (mX >= layout.durBtnStartX && mX <= layout.durBtnStartX + 24 && mY >= startY + 2 && mY <= startY + 16) {
            adjustDuration(-20.0);
            return true;
        }
        if (mX >= layout.durBtnStartX + 26 && mX <= layout.durBtnStartX + 50 && mY >= startY + 2 && mY <= startY + 16) {
            adjustDuration(20.0);
            return true;
        }
        if (mX >= layout.durBtnStartX + 52 && mX <= layout.durBtnStartX + 70 && mY >= startY + 2 && mY <= startY + 16) {
            setDuration(20.0);
            return true;
        }
        if (mX >= layout.durBtnStartX + 72 && mX <= layout.durBtnStartX + 90 && mY >= startY + 2 && mY <= startY + 16) {
            setDuration(100.0);
            return true;
        }

        if (powerBox != null) {
            powerBox.setX(layout.pwrBoxX);
            powerBox.setY(startY + 2);
            powerBox.setWidth(layout.pwrBoxW);
            boolean click = powerBox.mouseClicked(mX, mY, 0);
            powerBox.setFocused(click);
            if (click) return true;
        }

        if (mX >= layout.genX && mX <= layout.genX + layout.genW && mY >= startY + 2 && mY <= startY + 16) {
            node.overrideBaseEUt(node.getBaseEUt(), !node.isGenerator());
            notifyGraphChanged();
            playClickSound();
            return true;
        }

        if (mX >= layout.pwrBtnStartX && mX <= layout.pwrBtnStartX + 14 && mY >= startY + 2 && mY <= startY + 16) {
            setPower(0.0);
            return true;
        }
        if (mX >= layout.pwrBtnStartX + 16 && mX <= layout.pwrBtnStartX + 34 && mY >= startY + 2 && mY <= startY + 16) {
            setPower(32.0);
            return true;
        }
        if (mX >= layout.pwrBtnStartX + 36 && mX <= layout.pwrBtnStartX + 58 && mY >= startY + 2 && mY <= startY + 16) {
            setPower(128.0);
            return true;
        }
        if (mX >= layout.pwrBtnStartX + 60 && mX <= layout.pwrBtnStartX + 82 && mY >= startY + 2 && mY <= startY + 16) {
            setPower(512.0);
            return true;
        }

        return false;
    }

    private void adjustDuration(double delta) {
        double next = Math.max(1.0, node.getBaseDurationTicks() + delta);
        setDuration(next);
    }

    private void setDuration(double ticks) {
        node.overrideDurationTicks(ticks);
        if (durationBox != null) {
            isSyncing = true;
            durationBox.setValue(formatNumber(ticks));
            isSyncing = false;
        }
        notifyGraphChanged();
        playClickSound();
    }

    private void setPower(double eut) {
        node.overrideBaseEUt(eut, node.isGenerator());
        if (powerBox != null) {
            isSyncing = true;
            powerBox.setValue(formatNumber(eut));
            isSyncing = false;
        }
        notifyGraphChanged();
        playClickSound();
    }

    private boolean handleIngredientColumnsClick(double mX, double mY, int startX, int startY, int width) {
        int colW = (width - 8) / 2;
        if (handleIngredientListClick(mX, mY, startX, startY, colW, true)) return true;
        if (handleIngredientListClick(mX, mY, startX + colW + 8, startY, colW, false)) return true;
        return false;
    }

    private boolean handleIngredientListClick(double mX, double mY, int x, int y, int width, boolean isInput) {
        List<IngredientStack> list = isInput ? node.getInputs() : node.getOutputs();
        if (list.isEmpty()) return false;

        int scroll = isInput ? inputScroll : outputScroll;
        int rowH = 19;
        int maxRows = 3;

        for (int i = 0; i < maxRows; i++) {
            int idx = scroll + i;
            if (idx >= list.size()) break;
            int rowY = y + 14 + i * rowH;
            if (mX >= x && mX <= x + width && mY >= rowY && mY <= rowY + 18) {
                return handleRowClick(mX, x, width, isInput, idx, list.get(idx));
            }
        }
        return false;
    }

    private boolean handleRowClick(double mX, int x, int width, boolean isInput, int idx, IngredientStack stack) {
        boolean isAux = isInput ? node.isAuxiliaryInputPort(idx) : node.isAuxiliaryOutputPort(idx);
        if (!isAux && mX >= x + width - 36 && mX <= x + width - 22) {
            adjustIngredientAmount(isInput, idx, stack, -1.0);
            return true;
        }
        if (!isAux && mX >= x + width - 18 && mX <= x + width - 4) {
            adjustIngredientAmount(isInput, idx, stack, 1.0);
            return true;
        }
        this.isInputSelected = isInput;
        this.selectedIngredientIndex = idx;
        syncAmountBox();
        playClickSound();
        return true;
    }

    private void adjustIngredientAmount(boolean isInput, int idx, IngredientStack stack, double step) {
        boolean isAux = isInput ? node.isAuxiliaryInputPort(idx) : node.isAuxiliaryOutputPort(idx);
        if (isAux) return;

        double delta = stack.isFluid() ? step * 100.0 : step;
        double next = Math.max(stack.isFluid() ? 1.0 : 0.01, stack.getAmount() + delta);
        if (isInput) {
            node.overrideInputAmount(idx, next);
        } else {
            node.overrideOutputAmount(idx, next);
        }
        this.isInputSelected = isInput;
        this.selectedIngredientIndex = idx;
        syncAmountBox();
        notifyGraphChanged();
        playClickSound();
    }

    private boolean handleBottomBarClick(double mX, double mY, int startX, int startY, int width) {
        Font font = Minecraft.getInstance().font;
        BottomBarLayout layout = computeBottomBarLayout(font, startX, width);
        IngredientStack sel = getSelectedStack();
        boolean isAux = isSelectedAuxiliary();

        if (sel != null && !isAux && amountBox != null) {
            amountBox.setX(layout.amtBoxX);
            amountBox.setY(startY + 3);
            amountBox.setWidth(layout.amtBoxW);
            boolean click = amountBox.mouseClicked(mX, mY, 0);
            amountBox.setFocused(click);
            if (click) return true;

            int btnX = layout.btnStartX;
            if (mX >= btnX && mX <= btnX + 16 && mY >= startY + 3 && mY <= startY + 17) {
                adjustIngredientAmount(isInputSelected, selectedIngredientIndex, sel, -1.0);
                return true;
            }
            if (mX >= btnX + 18 && mX <= btnX + 34 && mY >= startY + 3 && mY <= startY + 17) {
                adjustIngredientAmount(isInputSelected, selectedIngredientIndex, sel, 1.0);
                return true;
            }
            if (mX >= btnX + 36 && mX <= btnX + 52 && mY >= startY + 3 && mY <= startY + 17) {
                scaleIngredientAmount(isInputSelected, selectedIngredientIndex, sel, 0.5);
                return true;
            }
            if (mX >= btnX + 54 && mX <= btnX + 70 && mY >= startY + 3 && mY <= startY + 17) {
                scaleIngredientAmount(isInputSelected, selectedIngredientIndex, sel, 2.0);
                return true;
            }
        }

        if (mX >= layout.resetX && mX <= layout.resetX + layout.resetW && mY >= startY + 2 && mY <= startY + 18) {
            if (node.resetToOriginalRecipe()) {
                selectDefaultIngredient();
                syncBoxesFromNode();
                notifyGraphChanged();
                playClickSound();
                return true;
            }
        }

        return false;
    }

    private void scaleIngredientAmount(boolean isInput, int idx, IngredientStack stack, double factor) {
        boolean isAux = isInput ? node.isAuxiliaryInputPort(idx) : node.isAuxiliaryOutputPort(idx);
        if (isAux) return;

        double next = Math.max(0.0001, stack.getAmount() * factor);
        if (isInput) {
            node.overrideInputAmount(idx, next);
        } else {
            node.overrideOutputAmount(idx, next);
        }
        syncAmountBox();
        notifyGraphChanged();
        playClickSound();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta, int startX, int startY, int width, int height) {
        int colW = (width - 8) / 2;
        int listY = startY + 24;
        int listH = 64;

        if (mouseY >= listY && mouseY <= listY + listH) {
            if (mouseX >= startX && mouseX <= startX + colW) {
                int maxScroll = Math.max(0, node.getInputs().size() - 3);
                inputScroll = Math.max(0, Math.min(maxScroll, inputScroll - (int) Math.signum(delta)));
                return true;
            }
            if (mouseX >= startX + colW + 8 && mouseX <= startX + width) {
                int maxScroll = Math.max(0, node.getOutputs().size() - 3);
                outputScroll = Math.max(0, Math.min(maxScroll, outputScroll - (int) Math.signum(delta)));
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (durationBox != null && durationBox.isFocused()) {
            return durationBox.keyPressed(keyCode, scanCode, modifiers);
        }
        if (powerBox != null && powerBox.isFocused()) {
            return powerBox.keyPressed(keyCode, scanCode, modifiers);
        }
        if (amountBox != null && amountBox.isFocused()) {
            return amountBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (durationBox != null && durationBox.isFocused()) {
            return durationBox.charTyped(codePoint, modifiers);
        }
        if (powerBox != null && powerBox.isFocused()) {
            return powerBox.charTyped(codePoint, modifiers);
        }
        if (amountBox != null && amountBox.isFocused()) {
            return amountBox.charTyped(codePoint, modifiers);
        }
        return false;
    }

    private String formatStackAmount(IngredientStack stack) {
        if (stack.isFluid()) {
            return String.format(Locale.ROOT, "%.0fmB", stack.getAmount());
        }
        double amt = stack.getAmount();
        return (amt == Math.floor(amt)) ? String.format(Locale.ROOT, "%.0fx", amt) : String.format(Locale.ROOT, "%.2fx", amt);
    }

    private String formatNumber(double val) {
        return (val == Math.floor(val)) ? String.format(Locale.ROOT, "%.0f", val) : String.format(Locale.ROOT, "%.2f", val);
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
        );
    }

    private static final class HeaderLayout {
        final String durLabel;
        final int durBoxX;
        final int durBoxW = 34;
        final String secStr;
        final int secX;
        final int durBtnStartX;
        final String pwrLabel;
        final int pwrStartX;
        final int pwrBoxX;
        final int pwrBoxW = 36;
        final int genX;
        final int genW = 44;
        final int pwrBtnStartX;

        HeaderLayout(Font font, int startX, int width, String durLabel, String secStr, String pwrLabel) {
            this.durLabel = durLabel;
            this.secStr = secStr;
            this.pwrLabel = pwrLabel;

            int halfW = (width - 8) / 2;
            int durLabelW = font.width(durLabel);
            this.durBoxX = startX + 4 + durLabelW + 4;
            this.secX = durBoxX + durBoxW + 4;
            int secW = font.width(secStr);
            this.durBtnStartX = secX + secW + 6;

            this.pwrStartX = startX + halfW + 8;
            int pwrLabelW = font.width(pwrLabel);
            this.pwrBoxX = pwrStartX + 2 + pwrLabelW + 4;
            this.genX = pwrBoxX + pwrBoxW + 4;
            this.pwrBtnStartX = genX + genW + 4;
        }
    }

    private static final class BottomBarLayout {
        final String prefix;
        final int amtBoxX;
        final int amtBoxW = 42;
        final int btnStartX;
        final int resetX;
        final int resetW = 104;

        BottomBarLayout(Font font, int startX, int width, String prefix) {
            this.prefix = prefix;
            int prefixW = font.width(prefix);
            this.amtBoxX = startX + 24 + prefixW + 6;
            this.btnStartX = amtBoxX + amtBoxW + 6;
            this.resetX = startX + width - resetW - 2;
        }
    }
}
