package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class TutorialExitConfirmDialog {
    private final BoardScreen parent;
    private boolean visible = false;
    private int targetPageIndex = -1;
    private boolean isCreateNewPage = false;
    private String targetTeamPageId = null;

    public TutorialExitConfirmDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public void openForSwitch(int targetPageIndex) {
        this.targetPageIndex = targetPageIndex;
        this.isCreateNewPage = false;
        this.targetTeamPageId = null;
        this.visible = true;
    }

    public void openForCreateNewPage() {
        this.targetPageIndex = -1;
        this.isCreateNewPage = true;
        this.targetTeamPageId = null;
        this.visible = true;
    }

    public void openForTeamPage(String teamPageId) {
        this.targetPageIndex = -1;
        this.isCreateNewPage = false;
        this.targetTeamPageId = teamPageId;
        this.visible = true;
    }

    public void close() {
        this.visible = false;
        this.targetPageIndex = -1;
        this.isCreateNewPage = false;
        this.targetTeamPageId = null;
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

        int modalW = Math.min(340, screenW - 24);
        int modalH = 115;
        int modalX = (screenW - modalW) / 2;
        int modalY = (screenH - modalH) / 2;
        Font font = Minecraft.getInstance().font;

        // 2. Modal Window Box
        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xF5161C26);
        graphics.renderOutline(modalX, modalY, modalW, modalH, 0xFF00E676);
        graphics.renderOutline(modalX + 1, modalY + 1, modalW - 2, modalH - 2, 0x6600E676);

        // Header Title
        graphics.drawString(font, "§a🎓 " + Component.translatable("gui.gtcalcboard.tutorial.exit_dialog.title").getString(), modalX + 12, modalY + 10, 0xFFFFFFFF, false);

        // Description / Warning Text
        String desc = Component.translatable("gui.gtcalcboard.tutorial.exit_dialog.desc").getString();
        desc = desc.replace("\\n", "\n");
        List<net.minecraft.util.FormattedCharSequence> allLines = new ArrayList<>();
        for (String paragraph : desc.split("\n")) {
            allLines.addAll(font.split(Component.literal("§7" + paragraph), modalW - 24));
        }
        for (int i = 0; i < Math.min(4, allLines.size()); i++) {
            graphics.drawString(font, allLines.get(i), modalX + 12, modalY + 28 + i * 11, 0xFFDDDDDD, false);
        }

        int btnW = (modalW - 32) / 2;
        int btnH = 20;
        int cancelBtnX = modalX + 12;
        int cancelBtnY = modalY + modalH - btnH - 10;

        int confirmBtnX = modalX + modalW - btnW - 12;
        int confirmBtnY = cancelBtnY;

        drawBtn(graphics, font, Component.translatable("gui.gtcalcboard.tutorial.exit_dialog.cancel").getString(), cancelBtnX, cancelBtnY, btnW, btnH, mouseX, mouseY, 0xFFFFFFFF, 0xFF282E3B, 0xFF3D4455);
        drawBtn(graphics, font, Component.translatable("gui.gtcalcboard.tutorial.exit_dialog.confirm").getString(), confirmBtnX, confirmBtnY, btnW, btnH, mouseX, mouseY, 0xFFFF7777, 0xFF4A1F26, 0xFF883344);

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

        int modalW = Math.min(340, screenW - 24);
        int modalH = 115;
        int modalX = (screenW - modalW) / 2;
        int modalY = (screenH - modalH) / 2;

        int btnW = (modalW - 32) / 2;
        int btnH = 20;
        int cancelBtnX = modalX + 12;
        int cancelBtnY = modalY + modalH - btnH - 10;

        int confirmBtnX = modalX + modalW - btnW - 12;
        int confirmBtnY = cancelBtnY;

        // Clicked Cancel
        if (mouseX >= cancelBtnX && mouseX <= cancelBtnX + btnW && mouseY >= cancelBtnY && mouseY <= cancelBtnY + btnH) {
            close();
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
            );
            return true;
        }

        // Clicked Confirm (Exit Tutorial & Switch)
        if (mouseX >= confirmBtnX && mouseX <= confirmBtnX + btnW && mouseY >= confirmBtnY && mouseY <= confirmBtnY + btnH) {
            executeExitAndSwitch();
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

    private void executeExitAndSwitch() {
        TutorialManager.getInstance().stopTutorial();

        if (isCreateNewPage) {
            BoardManager bm = BoardManager.getInstance();
            bm.addPage("Page " + (bm.getPages().size() + 1));
            parent.rebuildWidgets();
        } else if (targetTeamPageId != null) {
            var teamState = com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance();
            teamState.autoCommitAndRelease(parent, teamState.getActiveTeamPageId());
            teamState.setActiveTeamPageId(targetTeamPageId);
            com.gtceu.calcboard.network.NetworkHandler.sendToServer(
                new com.gtceu.calcboard.network.packet.c2s.C2SPingPresencePacket(teamState.getCurrentTeamId(), targetTeamPageId, true)
            );
            parent.rebuildWidgets();
            parent.markSummaryDirty();
        } else if (targetPageIndex >= 0) {
            BoardManager bm = BoardManager.getInstance();
            BoardPage cur = bm.getActivePage();
            if (cur != null) {
                cur.setPanX(parent.getPanX());
                cur.setPanY(parent.getPanY());
                cur.setZoom(parent.getZoom());
            }
            bm.switchPage(targetPageIndex);
            BoardPage next = bm.getActivePage();
            if (next != null) {
                parent.setPanX(next.getPanX());
                parent.setPanY(next.getPanY());
                parent.setZoom(next.getZoom());
            }
            parent.rebuildWidgets();
        }

        close();
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_OUT, 1.0F)
        );
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            executeExitAndSwitch();
            return true;
        }
        return true;
    }
}
