package com.gtceu.calcboard.client.gui.tutorial;

import com.gtceu.calcboard.client.gui.BoardScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Onboarding welcome modal shown when a user opens the calculator board for the first time.
 */
public class WelcomeTutorialDialog {
    private boolean visible = false;

    public void show() {
        this.visible = true;
    }

    public void hide() {
        this.visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics graphics, int screenW, int screenH, int mouseX, int mouseY) {
        if (!visible) return;

        // Semi-transparent backdrop
        graphics.fill(0, 0, screenW, screenH, 0x99000000);

        Font font = Minecraft.getInstance().font;
        int modalW = 320;
        int modalH = 160;
        int modalX = (screenW - modalW) / 2;
        int modalY = (screenH - modalH) / 2;

        // Dialog background
        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xF5161C26);
        graphics.renderOutline(modalX, modalY, modalW, modalH, 0xFF00E676);
        graphics.renderOutline(modalX + 1, modalY + 1, modalW - 2, modalH - 2, 0x8800E676);

        // Title
        graphics.drawString(font, "§a🎓 " + Component.translatable("gui.gtcalcboard.welcome.title").getString(), modalX + 12, modalY + 12, 0xFFFFFFFF, false);

        // Body Text
        String desc = Component.translatable("gui.gtcalcboard.welcome.desc").getString();
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal("§7" + desc), modalW - 24);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), modalX + 12, modalY + 34 + i * 12, 0xFFDDDDDD, false);
        }

        // Action Buttons: [🎓 튜토리얼 시작] vs [직접 둘러보기]
        int btnW = 135;
        int btnH = 22;
        int startBtnX = modalX + modalW - btnW - 12;
        int startBtnY = modalY + modalH - btnH - 12;

        int closeBtnX = modalX + 12;
        int closeBtnY = startBtnY;

        drawBtn(graphics, font, Component.translatable("gui.gtcalcboard.welcome.skip").getString(), closeBtnX, closeBtnY, btnW, btnH, mouseX, mouseY, 0xFF888888, 0xFF3E475A);
        drawBtn(graphics, font, Component.translatable("gui.gtcalcboard.welcome.start").getString(), startBtnX, startBtnY, btnW, btnH, mouseX, mouseY, 0xFF00FF88, 0xFF1D5A3A);
    }

    public boolean mouseClicked(BoardScreen screen, int screenW, int screenH, double mouseX, double mouseY, int button) {
        if (!visible || button != 0) return false;

        int modalW = 320;
        int modalH = 160;
        int modalX = (screenW - modalW) / 2;
        int modalY = (screenH - modalH) / 2;

        int btnW = 135;
        int btnH = 22;
        int startBtnX = modalX + modalW - btnW - 12;
        int startBtnY = modalY + modalH - btnH - 12;

        int closeBtnX = modalX + 12;
        int closeBtnY = startBtnY;

        if (mouseX >= startBtnX && mouseX <= startBtnX + btnW && mouseY >= startBtnY && mouseY <= startBtnY + btnH) {
            hide();
            TutorialManager.getInstance().startTutorial(screen);
            return true;
        }

        if (mouseX >= closeBtnX && mouseX <= closeBtnX + btnW && mouseY >= closeBtnY && mouseY <= closeBtnY + btnH) {
            hide();
            return true;
        }

        return true; // Modal blocks underlying clicks
    }

    private void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textColor, int bgHover) {
        boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        graphics.fill(bx, by, bx + bw, by + bh, hover ? bgHover : 0xFF222834);
        graphics.renderOutline(bx, by, bw, bh, hover ? 0xFF00E676 : 0xFF3D4B66);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + (bh - 8) / 2, textColor);
    }
}
