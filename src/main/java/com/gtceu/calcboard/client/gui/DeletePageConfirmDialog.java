package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.BoardManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;

/**
 * Confirmation dialog shown before deleting a board page tab.
 * Informs the user that this action cannot be undone.
 */
public class DeletePageConfirmDialog {
    private final BoardScreen parent;
    private boolean visible = false;
    private int targetPageIndex = -1;
    private String targetPageId = null;
    private String targetPageName = "";
    private boolean isTeamPage = false;

    public DeletePageConfirmDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public void open(int pageIndex, String pageName) {
        this.targetPageIndex = pageIndex;
        this.targetPageId = null;
        this.targetPageName = pageName != null ? pageName : "Page " + (pageIndex + 1);
        this.isTeamPage = false;
        this.visible = true;
    }

    public void openTeamPage(String pageId, String pageName) {
        this.targetPageIndex = -1;
        this.targetPageId = pageId;
        this.targetPageName = pageName != null ? pageName : "Team Page";
        this.isTeamPage = true;
        this.visible = true;
    }

    public void close() {
        this.visible = false;
        this.targetPageIndex = -1;
        this.targetPageId = null;
        this.targetPageName = "";
        this.isTeamPage = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics graphics, int screenW, int screenH, int mouseX, int mouseY) {
        if (!visible) return;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 700.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();

        // 1. Dark semi-transparent full screen backdrop
        graphics.fill(0, 0, screenW, screenH, 0x88000000);

        int modalW = Math.min(320, screenW - 24);
        int modalH = 115;
        int modalX = (screenW - modalW) / 2;
        int modalY = (screenH - modalH) / 2;
        Font font = Minecraft.getInstance().font;

        // 2. Modal Window Box
        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xF5161C26);
        graphics.renderOutline(modalX, modalY, modalW, modalH, 0xFFFF4444);
        graphics.renderOutline(modalX + 1, modalY + 1, modalW - 2, modalH - 2, 0x66FF4444);

        // Header Title
        graphics.drawString(font, "§c[!] " + Component.translatable("gui.gtcalcboard.page_delete.title").getString(), modalX + 12, modalY + 10, 0xFFFFFFFF, false);

        // Description / Warning Text (supporting explicit \n and auto word wrap)
        String desc = Component.translatable("gui.gtcalcboard.page_delete.desc", targetPageName).getString();
        desc = desc.replace("\\n", "\n");
        List<net.minecraft.util.FormattedCharSequence> allLines = new java.util.ArrayList<>();
        for (String paragraph : desc.split("\n")) {
            allLines.addAll(font.split(Component.literal("§7" + paragraph), modalW - 24));
        }
        for (int i = 0; i < Math.min(4, allLines.size()); i++) {
            graphics.drawString(font, allLines.get(i), modalX + 12, modalY + 28 + i * 11, 0xFFDDDDDD, false);
        }

        // Action Buttons: [취소 (Cancel)] vs [영구 삭제 (Delete)]
        int btnW = (modalW - 32) / 2;
        int btnH = 20;
        int cancelBtnX = modalX + 12;
        int cancelBtnY = modalY + modalH - btnH - 10;

        int deleteBtnX = modalX + modalW - btnW - 12;
        int deleteBtnY = cancelBtnY;

        drawBtn(graphics, font, Component.translatable("gui.gtcalcboard.page_delete.cancel").getString(), cancelBtnX, cancelBtnY, btnW, btnH, mouseX, mouseY, 0xFFFFFFFF, 0xFF282E3B, 0xFF3D4455);
        drawBtn(graphics, font, Component.translatable("gui.gtcalcboard.page_delete.confirm").getString(), deleteBtnX, deleteBtnY, btnW, btnH, mouseX, mouseY, 0xFFFF6666, 0xFF551111, 0xFFAA2222);

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
        if (!visible || button != 0) return false;

        int modalW = Math.min(320, screenW - 24);
        int modalH = 115;
        int modalX = (screenW - modalW) / 2;
        int modalY = (screenH - modalH) / 2;

        int btnW = (modalW - 32) / 2;
        int btnH = 20;
        int cancelBtnX = modalX + 12;
        int cancelBtnY = modalY + modalH - btnH - 10;

        int deleteBtnX = modalX + modalW - btnW - 12;
        int deleteBtnY = cancelBtnY;

        // Clicked Cancel
        if (mouseX >= cancelBtnX && mouseX <= cancelBtnX + btnW && mouseY >= cancelBtnY && mouseY <= cancelBtnY + btnH) {
            close();
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
            );
            return true;
        }

        // Clicked Delete
        if (mouseX >= deleteBtnX && mouseX <= deleteBtnX + btnW && mouseY >= deleteBtnY && mouseY <= deleteBtnY + btnH) {
            executeDelete();
            return true;
        }

        // Absorb clicks inside modal
        if (mouseX >= modalX && mouseX <= modalX + modalW && mouseY >= modalY && mouseY <= modalY + modalH) {
            return true;
        }

        // Clicked outside modal -> cancel
        close();
        return true;
    }

    private void executeDelete() {
        if (isTeamPage && targetPageId != null) {
            UUID teamId = com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance().getCurrentTeamId();
            com.gtceu.calcboard.network.NetworkHandler.sendToServer(new com.gtceu.calcboard.network.packet.c2s.C2SDeleteTeamPagePacket(teamId, targetPageId));
        } else if (targetPageIndex >= 0) {
            BoardManager.getInstance().removePage(targetPageIndex);
            parent.rebuildWidgets();
        }
        close();
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK, 1.0F)
        );
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            executeDelete();
            return true;
        }
        return true;
    }
}
