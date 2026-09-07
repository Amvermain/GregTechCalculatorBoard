package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.AutoRatioMode;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import com.gtceu.calcboard.client.gui.dialog.modal.IBoardModal;
import com.gtceu.calcboard.client.gui.dialog.modal.ModalRenderContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Modal dialog for editing Frame Title and Theme Color.
 */
public class FrameEditDialog implements IBoardModal {
    private final BoardScreen parent;
    private boolean visible = false;
    private CanvasGroupFrame targetFrame = null;

    private EditBox titleInput;
    private EditBox targetCapacityInput;
    private int selectedColor;
    private boolean sharedMachineMode;
    private String initialTitle;
    private int initialColor;
    private boolean initialSharedMode;
    private double initialTargetCapacity;

    public FrameEditDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public void open(CanvasGroupFrame frame) {
        if (frame == null) return;
        this.targetFrame = frame;
        this.selectedColor = frame.getColor();
        this.sharedMachineMode = frame.isSharedMachineFrame();
        this.initialTitle = frame.getTitle() != null ? frame.getTitle() : "";
        this.initialColor = frame.getColor();
        this.initialSharedMode = frame.isSharedMachineFrame();
        this.initialTargetCapacity = frame.getTargetPoolCapacity();
        this.visible = true;

        Font font = Minecraft.getInstance().font;
        int dialogW = 280;
        int dialogH = sharedMachineMode ? 186 : 158;
        int x = (parent.width - dialogW) / 2;
        int y = (parent.height - dialogH) / 2;

        this.titleInput = new EditBox(font, x + 16, y + 42, dialogW - 32, 18, Component.literal("Title"));
        this.titleInput.setMaxLength(64);
        this.titleInput.setCanLoseFocus(true);
        this.titleInput.setValue(frame.getTitle() != null ? frame.getTitle() : "");
        this.titleInput.setFocused(true);

        this.targetCapacityInput = new EditBox(font, x + 110, y + 124, 45, 16, Component.literal("Capacity"));
        this.targetCapacityInput.setMaxLength(8);
        this.targetCapacityInput.setCanLoseFocus(true);
        this.targetCapacityInput.setValue(String.format(Locale.ROOT, "%.1f", frame.getTargetPoolCapacity()));
    }

    public void close() {
        this.visible = false;
        this.targetFrame = null;
        this.titleInput = null;
        this.targetCapacityInput = null;
    }

    public boolean isVisible() {
        return visible;
    }

    @Override
    public void renderModal(ModalRenderContext context) {
        render(context.graphics(), context.screenWidth(), context.screenHeight(), context.mouseX(), context.mouseY());
    }

    public void render(GuiGraphics graphics, int screenW, int screenH, int mouseX, int mouseY) {
        if (!visible || targetFrame == null) return;

        Font font = Minecraft.getInstance().font;
        int dialogW = 280;
        int dialogH = sharedMachineMode ? 186 : 158;
        int x = (screenW - dialogW) / 2;
        int y = (screenH - dialogH) / 2;

        // Modal backdrop
        graphics.fill(0, 0, screenW, screenH, 0x88000000);

        // Dialog body
        graphics.fill(x, y, x + dialogW, y + dialogH, 0xF0181F2A);
        graphics.renderOutline(x, y, dialogW, dialogH, (selectedColor & 0x00FFFFFF) | 0xCC000000);

        // Header bar
        graphics.fill(x, y, x + dialogW, y + 24, (selectedColor & 0x00FFFFFF) | 0x99000000);
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.frame_edit_title").getString(), x + 10, y + 8, 0xFFFFFFFF, true);

        // Field labels
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.frame_label_title").getString(), x + 16, y + 31, 0xFF94A3B8, false);
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.frame_label_color").getString(), x + 16, y + 68, 0xFF94A3B8, false);

        // Color Swatches
        int swatchY = y + 80;
        int swatchSize = 14;
        int swatchSpacing = 5;
        for (int i = 0; i < CanvasGroupFrame.PALETTE.length; i++) {
            int col = CanvasGroupFrame.PALETTE[i];
            int sx = x + 16 + i * (swatchSize + swatchSpacing);
            boolean isCur = (col == selectedColor);
            boolean hover = mouseX >= sx && mouseX <= sx + swatchSize && mouseY >= swatchY && mouseY <= swatchY + swatchSize;

            graphics.fill(sx, swatchY, sx + swatchSize, swatchY + swatchSize, col);
            graphics.renderOutline(sx, swatchY, swatchSize, swatchSize, isCur ? 0xFFFFFFFF : (hover ? 0xCCFFFFFF : 0x44000000));
            if (isCur) {
                graphics.renderOutline(sx - 1, swatchY - 1, swatchSize + 2, swatchSize + 2, 0xFF00FFFF);
            }
        }

        // Shared Machine Pool Checkbox Option
        int cbY = y + 104;
        int cbSize = 12;
        int cbX = x + 16;
        boolean cbHover = mouseX >= cbX && mouseX <= x + dialogW - 16 && mouseY >= cbY - 2 && mouseY <= cbY + cbSize + 2;

        graphics.fill(cbX, cbY, cbX + cbSize, cbY + cbSize, sharedMachineMode ? 0xFF10B981 : 0xFF1E293B);
        graphics.renderOutline(cbX, cbY, cbSize, cbSize, cbHover ? 0xFFFFFFFF : 0xFF64748B);
        if (sharedMachineMode) {
            graphics.drawString(font, "✔", cbX + 2, cbY + 2, 0xFFFFFFFF, false);
        }
        String cbLabel = Component.translatable("gui.gtcalcboard.frame_label_shared_machine").getString();
        graphics.drawString(font, cbLabel, cbX + cbSize + 6, cbY + 2, cbHover ? 0xFFFFFFFF : 0xFFCBD5E1, false);

        // Target Capacity Field & Auto Ratio Button (Shared Machine Mode Only)
        if (sharedMachineMode) {
            int capY = y + 124;
            graphics.drawString(font, Component.translatable("gui.gtcalcboard.frame.target_capacity").getString(), x + 16, capY + 4, 0xFF94A3B8, false);

            int capInputX = x + 112;
            int capInputW = 42;
            if (targetCapacityInput != null) {
                targetCapacityInput.setX(capInputX);
                targetCapacityInput.setY(capY + 1);
                targetCapacityInput.render(graphics, mouseX, mouseY, 0);
            }

            int ratioBtnX = capInputX + capInputW + 6;
            int ratioBtnW = (x + dialogW - 16) - ratioBtnX;
            int ratioBtnH = 16;
            boolean ratioHover = mouseX >= ratioBtnX && mouseX <= ratioBtnX + ratioBtnW && mouseY >= capY && mouseY <= capY + ratioBtnH;

            graphics.fill(ratioBtnX, capY, ratioBtnX + ratioBtnW, capY + ratioBtnH, ratioHover ? 0xFF2563EB : 0xFF1D4ED8);
            graphics.renderOutline(ratioBtnX, capY, ratioBtnW, ratioBtnH, ratioHover ? 0xFF93C5FD : 0xFF3B82F6);
            String ratioTxt = "⚖ " + Component.translatable("gui.gtcalcboard.frame.btn_auto_ratio").getString();
            int txtW = font.width(ratioTxt);
            int drawX = ratioBtnX + Math.max(2, (ratioBtnW - txtW) / 2);
            graphics.drawString(font, ratioTxt, drawX, capY + 4, 0xFFFFFFFF, false);
        }

        // Render Inputs
        if (titleInput != null) {
            titleInput.setX(x + 16);
            titleInput.setY(y + 42);
            titleInput.render(graphics, mouseX, mouseY, 0);
        }

        // Buttons [✔ Save] [✕ Cancel]
        int btnW = 70;
        int btnH = 18;
        int btnY = y + dialogH - btnH - 8;

        int saveX = x + dialogW - (btnW * 2) - 18;
        int cancelX = x + dialogW - btnW - 12;

        boolean saveHover = mouseX >= saveX && mouseX <= saveX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        boolean cancelHover = mouseX >= cancelX && mouseX <= cancelX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

        // Save button
        graphics.fill(saveX, btnY, saveX + btnW, btnY + btnH, saveHover ? 0xFF2A5A38 : 0xFF1C3524);
        graphics.renderOutline(saveX, btnY, btnW, btnH, saveHover ? 0xFF55FF88 : 0xFF3B774E);
        String saveTxt = "✔ " + Component.translatable("gui.gtcalcboard.save").getString();
        graphics.drawString(font, saveTxt, saveX + (btnW - font.width(saveTxt)) / 2, btnY + 5, 0xFF55FF88, false);

        // Cancel button
        graphics.fill(cancelX, btnY, cancelX + btnW, btnY + btnH, cancelHover ? 0xFF444444 : 0xFF2A2A2A);
        graphics.renderOutline(cancelX, btnY, btnW, btnH, cancelHover ? 0xFFCCCCCC : 0xFF666666);
        String cancelTxt = "✕ " + Component.translatable("gui.gtcalcboard.cancel").getString();
        graphics.drawString(font, cancelTxt, cancelX + (btnW - font.width(cancelTxt)) / 2, btnY + 5, 0xFFCCCCCC, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || targetFrame == null) return false;

        int dialogW = 280;
        int dialogH = sharedMachineMode ? 186 : 158;
        int x = (parent.width - dialogW) / 2;
        int y = (parent.height - dialogH) / 2;

        // Click outside dialog -> close without saving
        if (mouseX < x || mouseX > x + dialogW || mouseY < y || mouseY > y + dialogH) {
            close();
            return true;
        }

        // Color swatches click
        int swatchY = y + 80;
        int swatchSize = 14;
        int swatchSpacing = 5;
        for (int i = 0; i < CanvasGroupFrame.PALETTE.length; i++) {
            int col = CanvasGroupFrame.PALETTE[i];
            int sx = x + 16 + i * (swatchSize + swatchSpacing);
            if (mouseX >= sx && mouseX <= sx + swatchSize && mouseY >= swatchY && mouseY <= swatchY + swatchSize) {
                selectedColor = CanvasGroupFrame.PALETTE[i];
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F));
                return true;
            }
        }

        // Shared Machine Pool Checkbox Click
        int cbY = y + 104;
        int cbSize = 12;
        int cbX = x + 16;
        if (mouseX >= cbX && mouseX <= x + dialogW - 16 && mouseY >= cbY - 2 && mouseY <= cbY + cbSize + 2) {
            sharedMachineMode = !sharedMachineMode;
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.1F));
            return true;
        }

        // Target Capacity Input and Auto Ratio Button Click
        if (sharedMachineMode) {
            int capY = y + 124;
            int capInputX = x + 112;
            int capInputW = 42;
            if (targetCapacityInput != null && targetCapacityInput.mouseClicked(mouseX, mouseY, button)) {
                targetCapacityInput.setFocused(true);
                return true;
            }

            int ratioBtnX = capInputX + capInputW + 6;
            int ratioBtnW = (x + dialogW - 16) - ratioBtnX;
            int ratioBtnH = 16;
            if (mouseX >= ratioBtnX && mouseX <= ratioBtnX + ratioBtnW && mouseY >= capY && mouseY <= capY + ratioBtnH) {
                double cap = parseTargetCapacity();
                commitSave();
                executeAutoRatioFromDialog(cap);
                return true;
            }
        }

        if (titleInput != null) {
            titleInput.setX(x + 16);
            titleInput.setY(y + 42);
            if (titleInput.mouseClicked(mouseX, mouseY, button)) {
                titleInput.setFocused(true);
                return true;
            }
        }

        int btnW = 70;
        int btnH = 18;
        int btnY = y + dialogH - btnH - 8;
        int saveX = x + dialogW - (btnW * 2) - 18;
        int cancelX = x + dialogW - btnW - 12;

        // Save Click
        if (mouseX >= saveX && mouseX <= saveX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            commitSave();
            return true;
        }

        // Cancel Click
        if (mouseX >= cancelX && mouseX <= cancelX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            close();
            return true;
        }

        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitSave();
            return true;
        }

        if (titleInput != null && titleInput.isFocused()) {
            if (titleInput.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        if (targetCapacityInput != null && targetCapacityInput.isFocused()) {
            if (targetCapacityInput.keyPressed(keyCode, scanCode, modifiers)) return true;
        }

        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (titleInput != null && titleInput.isFocused()) {
            return titleInput.charTyped(codePoint, modifiers);
        }
        if (targetCapacityInput != null && targetCapacityInput.isFocused()) {
            return targetCapacityInput.charTyped(codePoint, modifiers);
        }
        return true;
    }

    private void commitSave() {
        if (targetFrame != null) {
            String newTitle = titleInput != null ? titleInput.getValue().trim() : "";
            if (newTitle.isEmpty() && initialTitle != null) {
                newTitle = initialTitle;
            }
            int newColor = selectedColor;
            boolean newShared = sharedMachineMode;
            double newCapacity = parseTargetCapacity();

            boolean changed = !Objects.equals(initialTitle, newTitle)
                    || initialColor != newColor
                    || initialSharedMode != newShared
                    || Math.abs(initialTargetCapacity - newCapacity) > 0.0001;

            if (changed) {
                targetFrame.setTitle(newTitle);
                targetFrame.setColor(newColor);
                targetFrame.setSharedMachineFrame(newShared);
                targetFrame.setTargetPoolCapacity(newCapacity);
                parent.recordCommand(new BoardCommand.ModifyFramePropertiesCommand(
                        targetFrame.getId(),
                        initialTitle, newTitle,
                        initialColor, newColor,
                        initialSharedMode, newShared,
                        initialTargetCapacity, newCapacity
                ));
                parent.markSummaryDirty();
            }
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F));
        }
        close();
    }

    private double parseTargetCapacity() {
        if (targetCapacityInput == null) return initialTargetCapacity;
        try {
            double parsed = Double.parseDouble(targetCapacityInput.getValue().trim());
            return Math.max(0.01, parsed);
        } catch (NumberFormatException ignored) {
            return initialTargetCapacity;
        }
    }

    private void executeAutoRatioFromDialog(double targetCapacity) {
        FlowGraph graph = parent.getGraph();
        if (graph == null || targetFrame == null) return;

        Map<String, Double> oldCounts = new HashMap<>();
        for (RecipeNode n : graph.getNodes()) {
            oldCounts.put(n.getId(), n.getMachineCount());
        }

        AutoRatioMode mode = Screen.hasAltDown() ? AutoRatioMode.FRACTIONAL
                : (Screen.hasShiftDown() ? AutoRatioMode.HARMONIZED
                : (BoardManager.getInstance().isAutoRatioFractionalDefault() ? AutoRatioMode.FRACTIONAL : AutoRatioMode.INTEGER_CEIL));

        int changed = FlowGraphSolver.autoRatioFromSharedPool(graph, targetFrame, targetCapacity, mode);
        if (changed <= 0) {
            BoardToast.show(Component.literal("§c✕ ").append(Component.translatable("message.gtcalcboard.pool_auto_ratio_empty")));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 0.8F));
            return;
        }

        List<BoardCommand> subCmds = new ArrayList<>();
        for (RecipeNode n : graph.getNodes()) {
            double oldC = oldCounts.getOrDefault(n.getId(), 1.0);
            double newC = n.getMachineCount();
            if (Math.abs(oldC - newC) > 0.0001) {
                subCmds.add(BoardCommand.ModifyPropertyCommand.machineCount(n.getId(), oldC, newC));
            }
        }
        if (!subCmds.isEmpty()) {
            parent.recordCommand(new BoardCommand.CompoundCommand(subCmds, "Pool Auto Ratio: " + targetFrame.getTitle()));
        }

        String capStr = String.format(Locale.ROOT, "%.1f", targetCapacity);
        BoardToast.show(Component.literal("§a⚖ ").append(Component.translatable("message.gtcalcboard.pool_auto_ratio_success", changed, targetFrame.getTitle(), capStr)));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
        parent.markSummaryDirty();
        parent.rebuildWidgets();
    }
}



