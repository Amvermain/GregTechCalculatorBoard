package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.client.gui.util.TargetRateParser;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.OptionalDouble;

public class TargetOutputRateDialog {

    private static final int DIALOG_WIDTH = 280;
    private static final int DIALOG_HEIGHT = 155;

    private final BoardScreen parent;
    private RecipeNode targetNode;
    private int outputIndex = -1;
    private boolean visible = false;
    private EditBox rateEditBox;

    public TargetOutputRateDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public boolean isVisible() {
        return visible;
    }

    public void open(RecipeNode node, int outputIndex) {
        if (node == null || outputIndex < 0 || outputIndex >= node.getOutputs().size()) return;
        this.targetNode = node;
        this.outputIndex = outputIndex;
        this.visible = true;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc != null ? mc.font : null;
        int screenWidth = parent != null ? parent.width : 400;
        int screenHeight = parent != null ? parent.height : 300;
        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;

        if (font != null) {
            this.rateEditBox = new EditBox(font, x + 16, y + 80, DIALOG_WIDTH - 32, 16,
                    Component.translatable("gui.gtcalcboard.editor.target_rate_title"));
            this.rateEditBox.setMaxLength(32);
            this.rateEditBox.setFocused(true);

            IngredientStack outStack = node.getOutputs().get(outputIndex);
            double currentRate = calculateSingleOutputRate(node, outStack) * node.getMachineCount();
            this.rateEditBox.setValue(formatInitialRate(currentRate, outStack));
        }
    }

    public void close() {
        this.visible = false;
        this.targetNode = null;
        this.outputIndex = -1;
        this.rateEditBox = null;
    }

    private String formatInitialRate(double currentRate, IngredientStack stack) {
        if (currentRate <= 0.0001) return "1.0/s";
        return FormatUtil.formatRate(currentRate, stack).replace("§r", "").trim();
    }

    private double calculateSingleOutputRate(RecipeNode node, IngredientStack stack) {
        double effCps = node.getEffectiveCyclesPerSecond();
        double count = Math.max(0.0001, node.getMachineCount());
        double singleCps = effCps / count;
        return stack.getAmount() * stack.getChance() * singleCps;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!visible || targetNode == null || outputIndex < 0) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int x = (parent.width - DIALOG_WIDTH) / 2;
        int y = (parent.height - DIALOG_HEIGHT) / 2;

        renderDialogBackground(graphics, x, y);
        renderHeader(graphics, font, x, y);

        IngredientStack outStack = targetNode.getOutputs().get(outputIndex);
        renderTargetInfo(graphics, font, x, y, outStack);

        if (rateEditBox != null) {
            rateEditBox.render(graphics, mouseX, mouseY, 0);
        }

        renderPreview(graphics, font, x, y, outStack);
        renderButtons(graphics, font, x, y, mouseX, mouseY);
    }

    private void renderDialogBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(0, 0, parent.width, parent.height, 0x77000000);
        graphics.fill(x, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xEE161B22);
        graphics.renderOutline(x, y, DIALOG_WIDTH, DIALOG_HEIGHT, 0xFF35445E);
    }

    private void renderHeader(GuiGraphics graphics, Font font, int x, int y) {
        graphics.fill(x, y, x + DIALOG_WIDTH, y + 20, 0xFF222834);
        graphics.renderOutline(x, y, DIALOG_WIDTH, 20, 0xFF35445E);
        String title = Component.translatable("gui.gtcalcboard.editor.target_rate_title").getString();
        graphics.drawString(font, "§e🎯 " + title, x + 8, y + 6, 0xFFFFFFFF, false);
    }

    private void renderTargetInfo(GuiGraphics graphics, Font font, int x, int y, IngredientStack stack) {
        IngredientRenderer.render(graphics, stack, x + 16, y + 26);
        graphics.drawString(font, stack.getDisplayName(), x + 38, y + 30, 0xFFFFFFFF, false);

        double singleRate = calculateSingleOutputRate(targetNode, stack);
        String singleStr = FormatUtil.formatRate(singleRate, stack);
        String singleInfo = Component.translatable("gui.gtcalcboard.editor.target_rate_single", singleStr).getString();
        graphics.drawString(font, singleInfo, x + 16, y + 48, 0xFFAABBCC, false);

        String currentInfo = Component.translatable("gui.gtcalcboard.editor.target_rate_current",
                String.format(Locale.ROOT, "%.4f", targetNode.getMachineCount())).getString();
        graphics.drawString(font, currentInfo, x + 16, y + 60, 0xFFAABBCC, false);
    }

    private void renderPreview(GuiGraphics graphics, Font font, int x, int y, IngredientStack stack) {
        String input = rateEditBox != null ? rateEditBox.getValue() : "";
        OptionalDouble parsed = TargetRateParser.parseRate(input, stack.isFluid(), FormatUtil.getActiveTimeUnit());

        if (parsed.isPresent()) {
            double singleRate = calculateSingleOutputRate(targetNode, stack);
            if (singleRate > 0.00001) {
                double reqCount = parsed.getAsDouble() / singleRate;
                double finalCount = Math.max(0.0001, Math.round(reqCount * 10000.0) / 10000.0);
                String preview = Component.translatable("gui.gtcalcboard.editor.target_rate_preview",
                        String.format(Locale.ROOT, "%.4f", finalCount)).getString();
                graphics.drawString(font, "§a✔ " + preview, x + 16, y + 102, 0xFF55FF88, false);
                return;
            }
        }
        String hint = Component.translatable("gui.gtcalcboard.editor.target_rate_hint").getString();
        graphics.drawString(font, "§7" + hint, x + 16, y + 102, 0xFF888888, false);
    }

    private void renderButtons(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int applyX = x + DIALOG_WIDTH - 120;
        int cancelX = x + DIALOG_WIDTH - 60;
        int btnY = y + DIALOG_HEIGHT - 26;

        boolean applyHover = mouseX >= applyX && mouseX <= applyX + 54 && mouseY >= btnY && mouseY <= btnY + 18;
        boolean cancelHover = mouseX >= cancelX && mouseX <= cancelX + 48 && mouseY >= btnY && mouseY <= btnY + 18;

        drawButton(graphics, font, Component.translatable("gui.gtcalcboard.apply_btn").getString(), applyX, btnY, 54, 18, applyHover, 0xFF355E3B, 0xFF55FF88);
        drawButton(graphics, font, Component.translatable("gui.gtcalcboard.cancel_btn").getString(), cancelX, btnY, 48, 18, cancelHover, 0xFF4A2B2B, 0xFFFF7777);
    }

    private void drawButton(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, boolean hover, int bg, int border) {
        graphics.fill(bx, by, bx + bw, by + bh, hover ? 0xFF3A475C : bg);
        graphics.renderOutline(bx, by, bw, bh, hover ? 0xFFFFFFFF : border);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + 5, hover ? 0xFFFFFFFF : 0xFFDDDDDD);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        int x = (parent.width - DIALOG_WIDTH) / 2;
        int y = (parent.height - DIALOG_HEIGHT) / 2;

        if (rateEditBox != null && rateEditBox.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int applyX = x + DIALOG_WIDTH - 120;
        int cancelX = x + DIALOG_WIDTH - 60;
        int btnY = y + DIALOG_HEIGHT - 26;

        if (button == 0) {
            if (mouseX >= applyX && mouseX <= applyX + 54 && mouseY >= btnY && mouseY <= btnY + 18) {
                apply();
                return true;
            }
            if (mouseX >= cancelX && mouseX <= cancelX + 48 && mouseY >= btnY && mouseY <= btnY + 18) {
                close();
                return true;
            }
        }

        return mouseX >= x && mouseX <= x + DIALOG_WIDTH && mouseY >= y && mouseY <= y + DIALOG_HEIGHT;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            apply();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (rateEditBox != null) {
            return rateEditBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible || rateEditBox == null) return false;
        return rateEditBox.charTyped(codePoint, modifiers);
    }

    private void apply() {
        if (targetNode == null || outputIndex < 0 || rateEditBox == null) return;

        IngredientStack stack = targetNode.getOutputs().get(outputIndex);
        OptionalDouble parsed = TargetRateParser.parseRate(rateEditBox.getValue(), stack.isFluid(), FormatUtil.getActiveTimeUnit());
        if (parsed.isEmpty()) return;

        double singleRate = calculateSingleOutputRate(targetNode, stack);
        if (singleRate <= 0.00001) return;

        double reqCount = parsed.getAsDouble() / singleRate;
        double finalCount = Math.max(0.0001, Math.round(reqCount * 10000.0) / 10000.0);

        double oldC = targetNode.getMachineCount();
        targetNode.setMachineCount(finalCount);
        targetNode.setBaseNode(true);

        if (parent != null) {
            parent.recordCommand(BoardCommand.ModifyPropertyCommand.machineCount(targetNode.getId(), oldC, finalCount));
            parent.markSummaryDirty();
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
        }

        String msg = Component.translatable("gui.gtcalcboard.editor.target_rate_applied",
                targetNode.getName(), String.format(Locale.ROOT, "%.4f", finalCount)).getString();
        BoardToast.show(Component.literal("§a✔ " + msg));

        close();
    }
}
