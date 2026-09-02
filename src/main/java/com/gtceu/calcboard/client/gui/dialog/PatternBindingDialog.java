package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.integration.ae2.model.PatternBindingEntry;
import com.gtceu.calcboard.integration.ae2.registry.PatternGraphRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

/**
 * Modal dialog for inspecting and managing the 1:1 binding between the active page and an AE2 autocrafting pattern.
 */
public class PatternBindingDialog {

    private final BoardScreen parent;
    private boolean visible = false;
    private BoardPage targetPage = null;

    public PatternBindingDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public void open(BoardPage page) {
        this.targetPage = page != null ? page : BoardManager.getInstance().getActivePage();
        this.visible = true;
    }

    public void close() {
        this.visible = false;
        this.targetPage = null;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics graphics, int screenW, int screenH, int mouseX, int mouseY) {
        if (!visible || targetPage == null) return;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 700.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();

        graphics.fill(0, 0, screenW, screenH, 0x88000000);

        int modalW = Math.min(280, screenW - 24);
        int modalH = 130;
        int modalX = (screenW - modalW) / 2;
        int modalY = (screenH - modalH) / 2;
        Font font = Minecraft.getInstance().font;

        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xF5161C26);
        graphics.renderOutline(modalX, modalY, modalW, modalH, 0xFF38BDF8);
        graphics.renderOutline(modalX + 1, modalY + 1, modalW - 2, modalH - 2, 0x6638BDF8);

        Component title = Component.translatable("gui.gtcalcboard.ae2.pattern.bind_title");
        graphics.drawString(font, "§b⚡ " + title.getString(), modalX + 12, modalY + 10, 0xFFFFFFFF, false);

        graphics.drawString(font, "§7Page: §f" + targetPage.getName(), modalX + 12, modalY + 28, 0xFFFFFFFF, false);

        Optional<PatternBindingEntry> bindingOpt = PatternGraphRegistry.getInstance().getBindingForPage(targetPage.getId());
        if (bindingOpt.isPresent()) {
            PatternBindingEntry entry = bindingOpt.get();
            graphics.drawString(font, "§a✔ " + Component.translatable("gui.gtcalcboard.ae2.pattern.bound_to", entry.patternId().getDisplayName()).getString(), modalX + 12, modalY + 46, 0xFF55FF88, false);
            graphics.drawString(font, "§8Key: " + font.plainSubstrByWidth(entry.patternId().getKey(), modalW - 54), modalX + 12, modalY + 62, 0xFFAAAAAA, false);

            if (!entry.patternId().getOutputIcon().isEmpty()) {
                graphics.renderItem(entry.patternId().getOutputIcon(), modalX + modalW - 32, modalY + 42);
            }
        } else {
            graphics.drawString(font, "§e⚠ " + Component.translatable("gui.gtcalcboard.ae2.pattern.no_page").getString(), modalX + 12, modalY + 46, 0xFFFFAA00, false);
            graphics.drawString(font, "§7Press Shift+A in AE2 Pattern Terminal", modalX + 12, modalY + 62, 0xFF888888, false);
            graphics.drawString(font, "§7or on a pattern item to link.", modalX + 12, modalY + 74, 0xFF888888, false);
        }

        int btnW = 80;
        int btnH = 20;
        int btnY = modalY + modalH - btnH - 10;

        if (bindingOpt.isPresent()) {
            int unbindBtnX = modalX + 12;
            int closeBtnX = modalX + modalW - btnW - 12;
            drawBtn(graphics, font, Component.translatable("gui.gtcalcboard.ae2.pattern.unbind").getString(), unbindBtnX, btnY, btnW, btnH, mouseX, mouseY, 0xFFFF6666, 0xFF551111, 0xFFAA2222);
            drawBtn(graphics, font, Component.translatable("gui.gtcalcboard.dialog.btn_close").getString(), closeBtnX, btnY, btnW, btnH, mouseX, mouseY, 0xFFFFFFFF, 0xFF282E3B, 0xFF3D4455);
        } else {
            int closeBtnX = modalX + (modalW - btnW) / 2;
            drawBtn(graphics, font, Component.translatable("gui.gtcalcboard.dialog.btn_close").getString(), closeBtnX, btnY, btnW, btnH, mouseX, mouseY, 0xFFFFFFFF, 0xFF282E3B, 0xFF3D4455);
        }

        graphics.pose().popPose();
    }

    private void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textCol, int bg, int border) {
        boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        int activeBg = hover ? (bg + 0x00151515) : bg;
        int activeBorder = hover ? 0xFFFFFFFF : border;
        graphics.fill(bx, by, bx + bw, by + bh, activeBg);
        graphics.renderOutline(bx, by, bw, bh, activeBorder);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + (bh - 8) / 2, textCol);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenW, int screenH) {
        if (!visible || targetPage == null || button != 0) return false;

        int modalW = Math.min(280, screenW - 24);
        int modalH = 130;
        int modalX = (screenW - modalW) / 2;
        int modalY = (screenH - modalH) / 2;

        int btnW = 80;
        int btnH = 20;
        int btnY = modalY + modalH - btnH - 10;

        Optional<PatternBindingEntry> bindingOpt = PatternGraphRegistry.getInstance().getBindingForPage(targetPage.getId());
        if (bindingOpt.isPresent()) {
            int unbindBtnX = modalX + 12;
            int closeBtnX = modalX + modalW - btnW - 12;

            if (mouseX >= unbindBtnX && mouseX <= unbindBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                PatternGraphRegistry.getInstance().unbindPage(targetPage.getId());
                playClickSound();
                close();
                return true;
            }

            if (mouseX >= closeBtnX && mouseX <= closeBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                playClickSound();
                close();
                return true;
            }
        } else {
            int closeBtnX = modalX + (modalW - btnW) / 2;
            if (mouseX >= closeBtnX && mouseX <= closeBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                playClickSound();
                close();
                return true;
            }
        }

        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return true;
    }

    private void playClickSound() {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.6f, 1.0f);
        }
    }
}
