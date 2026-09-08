package com.gtceu.calcboard.client.gui.dialog.settings;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.BoardSettingsDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Settings tab for configuring auto ratio solver preferences, harmonize limits, and tolerances.
 */
public class RatioSettingsTab extends AbstractSettingsTab {

    private EditBox maxScaleInput;

    public RatioSettingsTab(BoardSettingsDialog dialog, BoardScreen parent) {
        super(dialog, parent);
    }

    @Override
    public void onOpen() {
        initInputs();
    }

    @Override
    public void onClose() {
        if (maxScaleInput != null) {
            maxScaleInput.setFocused(false);
        }
    }

    private void initInputs() {
        if (maxScaleInput == null) {
            Font font = Minecraft.getInstance().font;
            maxScaleInput = new EditBox(font, 0, 0, 48, 18, Component.literal("Max Scale"));
            maxScaleInput.setFilter(s -> s.isEmpty() || (s.matches("\\d+") && s.length() <= 4));
            maxScaleInput.setResponder(s -> {
                if (!s.isEmpty()) {
                    try {
                        int val = Integer.parseInt(s);
                        if (val > 0) {
                            BoardManager.getInstance().setMaxHarmonizeScale(val);
                            BoardManager.getInstance().saveForCurrentContext();
                        }
                    } catch (NumberFormatException ignored) {}
                }
            });
        }
        maxScaleInput.setValue(String.valueOf(BoardManager.getInstance().getMaxHarmonizeScale()));
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        BoardManager bm = BoardManager.getInstance();
        graphics.drawString(font, "§6" + Component.translatable("gui.gtcalcboard.settings.ratio_desc").getString(), x, y, 0xFFFFFFFF, false);

        int rowY = y + 14;
        rowY = renderAutoRatioModeRow(graphics, font, x, rowY, w, mouseX, mouseY, bm);
        rowY = renderPreserveAnchorRow(graphics, font, x, rowY, w, mouseX, mouseY, bm);
        rowY = renderHarmonizeScaleSection(graphics, font, x, rowY, w, mouseX, mouseY, bm);
        rowY = renderSurplusToleranceRow(graphics, font, x, rowY, w, mouseX, mouseY, bm);
        renderRatioHintBox(graphics, font, x, rowY, w);
    }

    private int renderAutoRatioModeRow(GuiGraphics graphics, Font font, int x, int rowY, int w, int mouseX, int mouseY, BoardManager bm) {
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.settings.auto_ratio_default_mode").getString(), x, rowY + 4, 0xFFCCCCCC, false);
        boolean frac = bm.isAutoRatioFractionalDefault();
        String modeTxt = frac
                ? "§b⚡ " + Component.translatable("gui.gtcalcboard.settings.auto_ratio_mode_fractional").getString() + " ▼"
                : "⚖ " + Component.translatable("gui.gtcalcboard.settings.auto_ratio_mode_integer").getString() + " ▼";
        int btnW = 150;
        int btnX = x + w - btnW - 4;
        drawButton(graphics, font, modeTxt, btnX, rowY, btnW, 18, mouseX, mouseY, frac ? 0xFF66E5FF : 0xFFFFFFFF, 0xFF222834, 0xFF35445E);
        return rowY + 22;
    }

    private int renderPreserveAnchorRow(GuiGraphics graphics, Font font, int x, int rowY, int w, int mouseX, int mouseY, BoardManager bm) {
        drawCheckbox(graphics, font, x, rowY, w, 18, mouseX, mouseY,
                Component.translatable("gui.gtcalcboard.settings.preserve_fractional_anchor").getString(),
                bm.isPreserveFractionalAnchor());
        return rowY + 20;
    }

    private int renderHarmonizeScaleSection(GuiGraphics graphics, Font font, int x, int rowY, int w, int mouseX, int mouseY, BoardManager bm) {
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.settings.max_harmonize_scale_label").getString(), x, rowY + 4, 0xFFCCCCCC, false);

        int inputW = 44;
        int stepW = 16;
        int rightX = x + w - 4;
        int plusBtnX = rightX - stepW;
        int inputX = plusBtnX - inputW - 2;
        int minusBtnX = inputX - stepW - 2;

        drawButton(graphics, font, "-", minusBtnX, rowY, stepW, 16, mouseX, mouseY, 0xFFFFFFFF, 0xFF222834, 0xFF35445E);

        if (maxScaleInput != null) {
            maxScaleInput.setX(inputX);
            maxScaleInput.setY(rowY);
            maxScaleInput.setWidth(inputW);
            maxScaleInput.render(graphics, mouseX, mouseY, 0);
        }

        drawButton(graphics, font, "+", plusBtnX, rowY, stepW, 16, mouseX, mouseY, 0xFFFFFFFF, 0xFF222834, 0xFF35445E);

        rowY += 20;
        int[] presets = {4, 8, 16, 32, 64, 128};
        int presetW = 28;
        int curScale = bm.getMaxHarmonizeScale();
        int preX = x + w - (presets.length * (presetW + 3)) - 4;
        for (int p : presets) {
            boolean isCur = (p == curScale);
            int textCol = isCur ? 0xFF55FF88 : 0xFFCCCCCC;
            int bgCol = isCur ? 0xFF1C3524 : 0xFF1F2533;
            int borderCol = isCur ? 0xFF3B774E : 0xFF35445E;
            drawButton(graphics, font, p + "x", preX, rowY, presetW, 14, mouseX, mouseY, textCol, bgCol, borderCol);
            preX += presetW + 3;
        }
        return rowY + 20;
    }

    private int renderSurplusToleranceRow(GuiGraphics graphics, Font font, int x, int rowY, int w, int mouseX, int mouseY, BoardManager bm) {
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.settings.surplus_tolerance_label").getString(), x, rowY + 4, 0xFFCCCCCC, false);
        double curTol = bm.getHarmonizeSurplusTolerance();
        String tolPercent = String.format(Locale.ROOT, "%.0f%%", curTol * 100.0);
        String tolBtnTxt = tolPercent + " (" + (curTol <= 0.0001 ? Component.translatable("gui.gtcalcboard.settings.exact_match_only").getString() : Component.translatable("gui.gtcalcboard.settings.tolerance_allowed").getString()) + ") ▼";
        int btnW = 150;
        int btnX = x + w - btnW - 4;
        drawButton(graphics, font, tolBtnTxt, btnX, rowY, btnW, 18, mouseX, mouseY, 0xFF66E5FF, 0xFF222834, 0xFF35445E);
        return rowY + 22;
    }

    private void renderRatioHintBox(GuiGraphics graphics, Font font, int x, int rowY, int w) {
        graphics.fill(x, rowY, x + w - 4, rowY + 52, 0x55111822);
        graphics.renderOutline(x, rowY, w - 4, 52, 0xFF2C394F);
        graphics.drawString(font, "§e★ " + Component.translatable("gui.gtcalcboard.settings.ratio_hint_title").getString(), x + 8, rowY + 5, 0xFFFFF176, false);
        graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.settings.ratio_hint_line1").getString(), x + 8, rowY + 18, 0xFFAABBCC, false);
        graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.settings.ratio_hint_line2").getString(), x + 8, rowY + 30, 0xFFAABBCC, false);
        graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.settings.ratio_hint_line3").getString(), x + 8, rowY + 42, 0xFFAABBCC, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, int h, int button) {
        BoardManager bm = BoardManager.getInstance();
        int rowY = y + 14;
        if (handleAutoRatioModeClick(mouseX, mouseY, x, rowY, w, bm)) return true;
        rowY += 22;
        if (handlePreserveAnchorClick(mouseX, mouseY, x, rowY, w, bm)) return true;
        rowY += 20;
        rowY = handleHarmonizeScaleClick(mouseX, mouseY, x, rowY, w, bm);
        handleSurplusToleranceClick(mouseX, mouseY, x, rowY, w, bm);
        return true;
    }

    private boolean handleAutoRatioModeClick(double mouseX, double mouseY, int x, int rowY, int w, BoardManager bm) {
        int btnW = 150;
        int btnX = x + w - btnW - 4;
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= rowY && mouseY <= rowY + 18) {
            bm.setAutoRatioFractionalDefault(!bm.isAutoRatioFractionalDefault());
            onSettingsChanged();
            return true;
        }
        return false;
    }

    private boolean handlePreserveAnchorClick(double mouseX, double mouseY, int x, int rowY, int w, BoardManager bm) {
        if (isInsideRow(mouseX, mouseY, x, rowY, w, 18)) {
            bm.setPreserveFractionalAnchor(!bm.isPreserveFractionalAnchor());
            onSettingsChanged();
            return true;
        }
        return false;
    }

    private int handleHarmonizeScaleClick(double mouseX, double mouseY, int x, int rowY, int w, BoardManager bm) {
        int inputW = 44;
        int stepW = 16;
        int rightX = x + w - 4;
        int plusBtnX = rightX - stepW;
        int inputX = plusBtnX - inputW - 2;
        int minusBtnX = inputX - stepW - 2;

        if (mouseX >= minusBtnX && mouseX <= minusBtnX + stepW && mouseY >= rowY && mouseY <= rowY + 16) {
            int next = Math.max(1, bm.getMaxHarmonizeScale() - 1);
            bm.setMaxHarmonizeScale(next);
            if (maxScaleInput != null) maxScaleInput.setValue(String.valueOf(next));
            onSettingsChanged();
            return rowY + 40;
        }
        if (maxScaleInput != null && mouseX >= inputX && mouseX <= inputX + inputW && mouseY >= rowY && mouseY <= rowY + 16) {
            maxScaleInput.mouseClicked(mouseX, mouseY, 0);
            return rowY + 40;
        }
        if (mouseX >= plusBtnX && mouseX <= plusBtnX + stepW && mouseY >= rowY && mouseY <= rowY + 16) {
            int next = Math.min(256, bm.getMaxHarmonizeScale() + 1);
            bm.setMaxHarmonizeScale(next);
            if (maxScaleInput != null) maxScaleInput.setValue(String.valueOf(next));
            onSettingsChanged();
            return rowY + 40;
        }

        rowY += 20;
        int[] presets = {4, 8, 16, 32, 64, 128};
        int presetW = 28;
        int preX = x + w - (presets.length * (presetW + 3)) - 4;
        for (int p : presets) {
            if (mouseX >= preX && mouseX <= preX + presetW && mouseY >= rowY && mouseY <= rowY + 14) {
                bm.setMaxHarmonizeScale(p);
                if (maxScaleInput != null) maxScaleInput.setValue(String.valueOf(p));
                onSettingsChanged();
                break;
            }
            preX += presetW + 3;
        }
        return rowY + 20;
    }

    private void handleSurplusToleranceClick(double mouseX, double mouseY, int x, int rowY, int w, BoardManager bm) {
        int btnW = 150;
        int btnX = x + w - btnW - 4;
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= rowY && mouseY <= rowY + 18) {
            bm.cycleHarmonizeSurplusTolerance();
            onSettingsChanged();
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (maxScaleInput != null && maxScaleInput.isFocused()) {
            return maxScaleInput.charTyped(codePoint, modifiers);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (maxScaleInput != null && maxScaleInput.isFocused()) {
            maxScaleInput.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return false;
    }
}
