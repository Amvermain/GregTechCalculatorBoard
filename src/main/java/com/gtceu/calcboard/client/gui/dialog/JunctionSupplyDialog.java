package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.SupplyMode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * Modal dialog for configuring external supply mode and flow rate of a Junction/Reroute Node.
 */
public class JunctionSupplyDialog {

    private final BoardScreen parent;
    private RecipeNode targetNode;
    private boolean visible = false;

    private SupplyMode selectedMode = SupplyMode.NONE;
    private EditBox rateEditBox;

    private static final int DIALOG_WIDTH = 300;
    private static final int DIALOG_HEIGHT = 185;

    public JunctionSupplyDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public void open(RecipeNode node) {
        if (node == null || !node.isReroute()) return;
        this.targetNode = node;
        this.selectedMode = node.getSupplyMode();
        this.visible = true;

        Font font = Minecraft.getInstance().font;
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;
        int editBoxX = x + DIALOG_WIDTH - 85;
        int editBoxY = y + 50 + SupplyMode.FIXED_RATE.ordinal() * 20;

        this.rateEditBox = new EditBox(font, editBoxX, editBoxY, 75, 16, Component.translatable("gui.gtcalcboard.junction.supply_rate"));
        this.rateEditBox.setMaxLength(16);
        this.rateEditBox.setValue(node.getExternalSupplyRate() > 0 ? String.format("%.2f", node.getExternalSupplyRate()) : "100.0");
        this.rateEditBox.setFocused(this.selectedMode == SupplyMode.FIXED_RATE);
    }

    public void close() {
        this.visible = false;
        this.targetNode = null;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible || targetNode == null) return;

        Font font = Minecraft.getInstance().font;
        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;

        graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);
        graphics.fill(x, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xFF181A22);
        graphics.renderOutline(x, y, DIALOG_WIDTH, DIALOG_HEIGHT, 0xFF4F5B73);
        graphics.fill(x + 1, y + 1, x + DIALOG_WIDTH - 1, y + 20, 0xFF232734);

        // Header Title
        String title = "🌐 " + Component.translatable("gui.gtcalcboard.junction.dialog_title").getString();
        graphics.drawString(font, title, x + 8, y + 6, 0xFFE0E6F0, false);

        // Close [X] Button
        int closeX = x + DIALOG_WIDTH - 18;
        int closeY = y + 4;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= closeY && mouseY <= closeY + 14;
        graphics.fill(closeX, closeY, closeX + 14, closeY + 14, closeHover ? 0xFF882222 : 0xFF442222);
        graphics.drawCenteredString(font, "✕", closeX + 7, closeY + 3, 0xFFFFFFFF);

        // Bound Ingredient preview
        IngredientStack boundStack = targetNode.getRerouteIngredient();
        int previewY = y + 26;
        if (boundStack != null) {
            IngredientRenderer.render(graphics, boundStack, x + 10, previewY);
            String boundText = "§f" + boundStack.getDisplayName();
            graphics.drawString(font, font.plainSubstrByWidth(boundText, DIALOG_WIDTH - 36), x + 32, previewY + 4, 0xFFFFFFFF, false);
        } else {
            graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.junction.no_bound_ingredient").getString(), x + 10, previewY + 4, 0xFF888888, false);
        }

        // Radio Options
        int optStartY = y + 50;
        int optH = 20;

        SupplyMode[] modes = SupplyMode.values();
        for (int i = 0; i < modes.length; i++) {
            SupplyMode mode = modes[i];
            int optY = optStartY + i * optH;
            boolean isSelected = (selectedMode == mode);
            boolean isHover = mouseX >= x + 10 && mouseX <= x + DIALOG_WIDTH - 10 && mouseY >= optY && mouseY <= optY + 16;

            int radioX = x + 12;
            int radioY = optY + 2;
            graphics.fill(radioX, radioY, radioX + 10, radioY + 10, isSelected ? 0xFF38BDF8 : (isHover ? 0xFF35445C : 0xFF232B3A));
            graphics.renderOutline(radioX, radioY, 10, 10, isSelected ? 0xFF7DD3FC : 0xFF475569);

            String modeLabel = Component.translatable(mode.getTranslationKey()).getString();
            int textColor = isSelected ? 0xFFFFFFFF : (isHover ? 0xFFCBD5E1 : 0xFF94A3B8);
            graphics.drawString(font, modeLabel, x + 28, optY + 3, textColor, false);

            if (mode == SupplyMode.FIXED_RATE && isSelected) {
                rateEditBox.setX(x + DIALOG_WIDTH - 85);
                rateEditBox.setY(optY);
                rateEditBox.render(graphics, mouseX, mouseY, 0);
            }
        }

        // Footer Action Buttons: Cancel and Apply
        int btnW = 70;
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

        if (selectedMode == SupplyMode.FIXED_RATE && checkEditBoxClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (checkRadioSelection(x, y, mouseX, mouseY)) {
            return true;
        }

        if (checkFooterButtons(x, y, mouseX, mouseY)) {
            return true;
        }

        return true;
    }

    private boolean checkCloseClicked(int x, int y, double mouseX, double mouseY) {
        int closeX = x + DIALOG_WIDTH - 18;
        int closeY = y + 4;
        return mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= closeY && mouseY <= closeY + 14;
    }

    private boolean checkEditBoxClicked(double mouseX, double mouseY, int button) {
        if (rateEditBox == null) return false;
        if (isMouseOverEditBox(mouseX, mouseY)) {
            rateEditBox.setFocused(true);
            rateEditBox.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return false;
    }

    private boolean isMouseOverEditBox(double mouseX, double mouseY) {
        if (rateEditBox == null) return false;
        return mouseX >= rateEditBox.getX() && mouseX <= rateEditBox.getX() + rateEditBox.getWidth()
                && mouseY >= rateEditBox.getY() && mouseY <= rateEditBox.getY() + rateEditBox.getHeight();
    }

    private boolean checkRadioSelection(int x, int y, double mouseX, double mouseY) {
        int optStartY = y + 50;
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
                if (mode == SupplyMode.FIXED_RATE && rateEditBox != null) {
                    rateEditBox.setFocused(true);
                } else if (rateEditBox != null) {
                    rateEditBox.setFocused(false);
                }
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F));
                return true;
            }
        }
        return false;
    }

    private boolean checkFooterButtons(int x, int y, double mouseX, double mouseY) {
        int btnW = 70;
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
        targetNode.setSupplyMode(selectedMode);
        if (selectedMode == SupplyMode.FIXED_RATE && rateEditBox != null) {
            try {
                double rate = Double.parseDouble(rateEditBox.getValue().trim());
                targetNode.setExternalSupplyRate(Math.max(0.0, rate));
            } catch (NumberFormatException ignored) {}
        }

        if (parent != null) {
            parent.markSummaryDirty();
            parent.rebuildWidgets();
        }

        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        close();
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
        if (selectedMode == SupplyMode.FIXED_RATE && rateEditBox != null && rateEditBox.isFocused()) {
            return rateEditBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (selectedMode == SupplyMode.FIXED_RATE && rateEditBox != null && rateEditBox.isFocused()) {
            return rateEditBox.charTyped(codePoint, modifiers);
        }
        return false;
    }

    private int getScreenWidth() {
        return parent != null && parent.width > 0 ? parent.width : Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private int getScreenHeight() {
        return parent != null && parent.height > 0 ? parent.height : Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }
}
