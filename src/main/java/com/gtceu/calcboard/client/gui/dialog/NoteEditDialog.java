package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.client.gui.BoardScreen;

import com.gtceu.calcboard.api.model.CanvasStickyNote;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * Modal dialog for editing Sticky Note Title, Multi-line Content, and Theme Color.
 */
public class NoteEditDialog {
    private final BoardScreen parent;
    private boolean visible = false;
    private CanvasStickyNote targetNote = null;

    private EditBox titleInput;
    private MultiLineEditBox contentInput;
    private int selectedColor;

    public NoteEditDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public void open(CanvasStickyNote note) {
        if (note == null) return;
        this.targetNote = note;
        this.selectedColor = note.getColor();
        this.visible = true;

        Font font = Minecraft.getInstance().font;
        int dialogW = 320;
        int dialogH = 240;
        int x = (parent.width - dialogW) / 2;
        int y = (parent.height - dialogH) / 2;

        this.titleInput = new EditBox(font, x + 16, y + 42, dialogW - 32, 18, Component.literal("Title"));
        this.titleInput.setMaxLength(64);
        this.titleInput.setCanLoseFocus(true);
        this.titleInput.setValue(note.getTitle() != null ? note.getTitle() : "");
        this.titleInput.setFocused(false);

        this.contentInput = new MultiLineEditBox(font, x + 16, y + 82, dialogW - 32, 90, Component.literal(""), Component.literal("Content"));
        this.contentInput.setCharacterLimit(1024);
        this.contentInput.setValue(note.getContent() != null ? note.getContent() : "");
        this.contentInput.setFocused(true);
    }

    public void close() {
        this.visible = false;
        this.targetNote = null;
        this.titleInput = null;
        this.contentInput = null;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics graphics, int screenW, int screenH, int mouseX, int mouseY) {
        if (!visible || targetNote == null) return;

        Font font = Minecraft.getInstance().font;
        int dialogW = 320;
        int dialogH = 240;
        int x = (screenW - dialogW) / 2;
        int y = (screenH - dialogH) / 2;

        // Modal backdrop
        graphics.fill(0, 0, screenW, screenH, 0x88000000);

        // Dialog body
        graphics.fill(x, y, x + dialogW, y + dialogH, 0xF0181F2A);
        graphics.renderOutline(x, y, dialogW, dialogH, (selectedColor & 0x00FFFFFF) | 0xCC000000);

        // Header bar
        graphics.fill(x, y, x + dialogW, y + 24, (selectedColor & 0x00FFFFFF) | 0x99000000);
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.note_edit_title").getString(), x + 10, y + 8, 0xFFFFFFFF, true);

        // Field labels
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.note_label_title").getString(), x + 16, y + 31, 0xFF94A3B8, false);
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.note_label_content").getString(), x + 16, y + 71, 0xFF94A3B8, false);
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.frame_label_color").getString(), x + 16, y + 180, 0xFF94A3B8, false);

        // Color Swatches
        int swatchY = y + 192;
        int swatchSize = 14;
        int swatchSpacing = 5;
        for (int i = 0; i < CanvasStickyNote.PALETTE.length; i++) {
            int col = CanvasStickyNote.PALETTE[i];
            int sx = x + 16 + i * (swatchSize + swatchSpacing);
            boolean isCur = (col == selectedColor);
            boolean hover = mouseX >= sx && mouseX <= sx + swatchSize && mouseY >= swatchY && mouseY <= swatchY + swatchSize;

            graphics.fill(sx, swatchY, sx + swatchSize, swatchY + swatchSize, col);
            graphics.renderOutline(sx, swatchY, swatchSize, swatchSize, isCur ? 0xFFFFFFFF : (hover ? 0xCCFFFFFF : 0x44000000));
            if (isCur) {
                graphics.renderOutline(sx - 1, swatchY - 1, swatchSize + 2, swatchSize + 2, 0xFF00FFFF);
            }
        }

        // Render Inputs
        if (titleInput != null) {
            titleInput.setX(x + 16);
            titleInput.setY(y + 42);
            titleInput.render(graphics, mouseX, mouseY, 0);
        }
        if (contentInput != null) {
            contentInput.setX(x + 16);
            contentInput.setY(y + 82);
            contentInput.render(graphics, mouseX, mouseY, 0);
        }

        // Buttons [✔ Save] [✕ Cancel]
        int btnW = 70;
        int btnH = 18;
        int btnY = y + dialogH - btnH - 10;

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
        if (!visible || targetNote == null) return false;

        int dialogW = 320;
        int dialogH = 240;
        int x = (parent.width - dialogW) / 2;
        int y = (parent.height - dialogH) / 2;

        // Click outside dialog -> close without saving
        if (mouseX < x || mouseX > x + dialogW || mouseY < y || mouseY > y + dialogH) {
            close();
            return true;
        }

        // Color swatches click
        int swatchY = y + 192;
        int swatchSize = 14;
        int swatchSpacing = 5;
        for (int i = 0; i < CanvasStickyNote.PALETTE.length; i++) {
            int sx = x + 16 + i * (swatchSize + swatchSpacing);
            if (mouseX >= sx && mouseX <= sx + swatchSize && mouseY >= swatchY && mouseY <= swatchY + swatchSize) {
                selectedColor = CanvasStickyNote.PALETTE[i];
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F));
                return true;
            }
        }

        if (titleInput != null) {
            titleInput.setX(x + 16);
            titleInput.setY(y + 42);
            if (titleInput.mouseClicked(mouseX, mouseY, button)) {
                titleInput.setFocused(true);
                if (contentInput != null) contentInput.setFocused(false);
                return true;
            }
        }
        if (contentInput != null) {
            contentInput.setX(x + 16);
            contentInput.setY(y + 82);
            if (contentInput.mouseClicked(mouseX, mouseY, button)) {
                contentInput.setFocused(true);
                if (titleInput != null) titleInput.setFocused(false);
                return true;
            }
        }

        int btnW = 70;
        int btnH = 18;
        int btnY = y + dialogH - btnH - 10;
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
        // Ctrl+Enter commits the save from anywhere in the dialog
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            commitSave();
            return true;
        }

        if (titleInput != null && titleInput.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                // Move focus to content
                titleInput.setFocused(false);
                if (contentInput != null) contentInput.setFocused(true);
                return true;
            }
            if (titleInput.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        if (contentInput != null && contentInput.isFocused()) {
            if (contentInput.keyPressed(keyCode, scanCode, modifiers)) return true;
        }

        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (titleInput != null && titleInput.isFocused()) {
            return titleInput.charTyped(codePoint, modifiers);
        }
        if (contentInput != null && contentInput.isFocused()) {
            return contentInput.charTyped(codePoint, modifiers);
        }
        return true;
    }

    private void commitSave() {
        if (targetNote != null) {
            String newTitle = titleInput != null ? titleInput.getValue().trim() : "";
            if (!newTitle.isEmpty()) {
                targetNote.setTitle(newTitle);
            }
            targetNote.setContent(contentInput != null ? contentInput.getValue() : "");
            targetNote.setColor(selectedColor);
            parent.markSummaryDirty();
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        }
        close();
    }
}



