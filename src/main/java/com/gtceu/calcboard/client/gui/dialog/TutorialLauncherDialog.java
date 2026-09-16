package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.modal.IBoardModal;
import com.gtceu.calcboard.client.gui.dialog.modal.ModalRenderContext;
import com.gtceu.calcboard.client.gui.tutorial.ContextualNudgeManager;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.tutorial.TutorialTrackRegistry;
import com.gtceu.calcboard.client.gui.tutorial.model.ITutorialChapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Modal dialog for selecting onboarding tracks and topic-based academy chapters.
 */
public class TutorialLauncherDialog implements IBoardModal {
    private static final int FOOTER_HOTKEY_BTN_WIDTH = 124;
    private static final int FOOTER_HOTKEY_BTN_HEIGHT = 16;

    private BoardScreen screen;
    private boolean visible = false;

    public TutorialLauncherDialog() {}

    public TutorialLauncherDialog(BoardScreen screen) {
        this.screen = screen;
    }

    public void setScreen(BoardScreen screen) {
        this.screen = screen;
    }

    public void open() {
        this.visible = true;
    }

    @Override
    public void close() {
        this.visible = false;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void renderModal(ModalRenderContext context) {
        render(context.graphics(), context.screenWidth(), context.screenHeight(), context.mouseX(), context.mouseY());
    }

    public void render(GuiGraphics graphics, int screenW, int screenH, int mouseX, int mouseY) {
        if (!visible) return;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 700.0f);
        graphics.fill(0, 0, screenW, screenH, 0xAA000000);

        Font font = Minecraft.getInstance().font;
        int modalW = Math.min(500, screenW - 20);
        int modalH = Math.min(320, screenH - 20);
        int modalX = (screenW - modalW) / 2;
        int modalY = (screenH - modalH) / 2;

        renderBackground(graphics, modalX, modalY, modalW, modalH);
        renderHeader(graphics, font, modalX, modalY, modalW, mouseX, mouseY);
        renderFastTrackCard(graphics, font, modalX + 12, modalY + 30, modalW - 24, mouseX, mouseY);
        renderChapterGrid(graphics, font, modalX + 12, modalY + 104, modalW - 24, mouseX, mouseY);
        renderFooter(graphics, font, modalX + 12, modalY + modalH - 26, modalW - 24, mouseX, mouseY);

        graphics.pose().popPose();
    }

    private void renderBackground(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xF5161C26);
        graphics.renderOutline(x, y, w, h, 0xFF00E676);
        graphics.renderOutline(x + 1, y + 1, w - 2, h - 2, 0x6600E676);
    }

    private void renderHeader(GuiGraphics graphics, Font font, int x, int y, int w, int mx, int my) {
        String title = "§a▶ " + Component.translatable("gui.gtcalcboard.tutorial.launcher.title").getString();
        graphics.drawString(font, title, x + 12, y + 10, 0xFFFFFFFF, false);

        boolean closeHov = mx >= x + w - 22 && mx <= x + w - 8 && my >= y + 8 && my <= y + 22;
        graphics.drawString(font, "✕", x + w - 18, y + 10, closeHov ? 0xFFFFFFFF : 0xFF888888, false);
    }

    private void renderFastTrackCard(GuiGraphics graphics, Font font, int x, int y, int w, int mx, int my) {
        int cardH = 58;
        graphics.fill(x, y, x + w, y + cardH, 0xFF1C2432);
        graphics.renderOutline(x, y, w, cardH, 0xFF00E676);

        String title = "⚡ " + Component.translatable("gui.gtcalcboard.tutorial.fast_track.title").getString();
        graphics.drawString(font, title, x + 8, y + 8, 0xFF55FF88, false);

        String desc = Component.translatable("gui.gtcalcboard.tutorial.fast_track.desc").getString();
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal("§7" + desc), w - 110);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.drawString(font, lines.get(i), x + 8, y + 24 + i * 11, 0xFFCCCCCC, false);
        }

        int btnW = 86;
        int btnH = 20;
        int btnX = x + w - btnW - 8;
        int btnY = y + (cardH - btnH) / 2;
        String btnText = "▶ " + Component.translatable("gui.gtcalcboard.tutorial.launcher.start").getString();
        drawButton(graphics, font, btnText, btnX, btnY, btnW, btnH, mx, my, 0xFF00FF88, 0xFF1B4D3E);
    }

    private void renderChapterGrid(GuiGraphics graphics, Font font, int x, int y, int w, int mx, int my) {
        String header = "── " + Component.translatable("gui.gtcalcboard.tutorial.launcher.academy_header").getString() + " ──";
        graphics.drawCenteredString(font, header, x + w / 2, y, 0xFF888888);

        List<ITutorialChapter> chapterList = TutorialTrackRegistry.getInstance().getChapters();
        int gridY = y + 14;
        int cardW = (w - 8) / 2;
        int cardH = 68;

        for (int i = 0; i < Math.min(4, chapterList.size()); i++) {
            ITutorialChapter ch = chapterList.get(i);
            int cx = x + (i % 2) * (cardW + 8);
            int cy = gridY + (i / 2) * (cardH + 6);
            renderChapterCard(graphics, font, ch, cx, cy, cardW, cardH, mx, my);
        }
    }

    private void renderChapterCard(GuiGraphics graphics, Font font, ITutorialChapter ch, int cx, int cy, int cw, int chH, int mx, int my) {
        graphics.fill(cx, cy, cx + cw, cy + chH, 0xFF1C2432);
        graphics.renderOutline(cx, cy, cw, chH, 0xFF3D4B66);

        graphics.drawString(font, ch.getTitle(), cx + 6, cy + 6, 0xFFE2E8F0, false);

        List<net.minecraft.util.FormattedCharSequence> lines = font.split(ch.getDescription(), cw - 12);
        for (int l = 0; l < Math.min(2, lines.size()); l++) {
            graphics.drawString(font, lines.get(l), cx + 6, cy + 19 + l * 10, 0xFF94A3B8, false);
        }

        int btnW = 68;
        int btnH = 16;
        int btnX = cx + cw - btnW - 6;
        int btnY = cy + chH - btnH - 6;
        String btnText = Component.translatable("gui.gtcalcboard.tutorial.launcher.practice").getString();
        drawButton(graphics, font, btnText, btnX, btnY, btnW, btnH, mx, my, 0xFF38BDF8, 0xFF0C4A6E);
    }

    private void renderFooter(GuiGraphics graphics, Font font, int x, int y, int w, int mx, int my) {
        boolean nudgesOn = ContextualNudgeManager.getInstance().isNudgesEnabled();
        int checkX = x;
        int checkY = y;
        graphics.fill(checkX, checkY, checkX + 12, checkY + 12, nudgesOn ? 0xFF0284C7 : 0xFF1E293B);
        graphics.renderOutline(checkX, checkY, 12, 12, nudgesOn ? 0xFF38BDF8 : 0xFF475569);
        if (nudgesOn) {
            graphics.drawString(font, "✔", checkX + 2, checkY + 2, 0xFFFFFFFF, false);
        }

        String nudgeLabel = Component.translatable("gui.gtcalcboard.tutorial.launcher.enable_nudges").getString();
        graphics.drawString(font, nudgeLabel, checkX + 16, checkY + 2, 0xFFCBD5E1, false);

        int hudBtnW = FOOTER_HOTKEY_BTN_WIDTH;
        int hudBtnH = FOOTER_HOTKEY_BTN_HEIGHT;
        int hudBtnX = x + w - hudBtnW;
        int hudBtnY = y - 2;
        String hudText = Component.translatable("gui.gtcalcboard.tutorial.launcher.view_hotkeys").getString();
        drawButton(graphics, font, hudText, hudBtnX, hudBtnY, hudBtnW, hudBtnH, mx, my, 0xFFCBD5E1, 0xFF2A3649);
    }

    private void drawButton(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textCol, int bgHover) {
        boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        graphics.fill(bx, by, bx + bw, by + bh, hover ? bgHover : 0xFF1C2432);
        graphics.renderOutline(bx, by, bw, bh, hover ? textCol : 0xFF3D4B66);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + (bh - 8) / 2, textCol);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int w = screen != null ? screen.width : (Minecraft.getInstance() != null && Minecraft.getInstance().getWindow() != null ? Minecraft.getInstance().getWindow().getGuiScaledWidth() : 800);
        int h = screen != null ? screen.height : (Minecraft.getInstance() != null && Minecraft.getInstance().getWindow() != null ? Minecraft.getInstance().getWindow().getGuiScaledHeight() : 600);
        return mouseClicked(mouseX, mouseY, button, w, h);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenW, int screenH) {
        if (!visible || button != 0) return false;

        int modalW = Math.min(500, screenW - 20);
        int modalH = Math.min(320, screenH - 20);
        int modalX = (screenW - modalW) / 2;
        int modalY = (screenH - modalH) / 2;

        if (handleHeaderClick(mouseX, mouseY, modalX, modalY, modalW)) return true;
        if (handleFastTrackClick(mouseX, mouseY, modalX + 12, modalY + 30, modalW - 24)) return true;
        if (handleChapterClick(mouseX, mouseY, modalX + 12, modalY + 104, modalW - 24)) return true;
        if (handleFooterClick(mouseX, mouseY, modalX + 12, modalY + modalH - 26, modalW - 24)) return true;

        return isInside(mouseX, mouseY, modalX, modalY, modalW, modalH);
    }

    private boolean handleHeaderClick(double mx, double my, int x, int y, int w) {
        if (isInside(mx, my, x + w - 22, y + 8, 14, 14)) {
            close();
            return true;
        }
        return false;
    }

    private boolean handleFastTrackClick(double mx, double my, int x, int y, int w) {
        int cardH = 58;
        if (isInside(mx, my, x, y, w, cardH)) {
            close();
            TutorialManager.getInstance().startFastTrack(screen);
            return true;
        }
        return false;
    }

    private boolean handleChapterClick(double mx, double my, int x, int y, int w) {
        List<ITutorialChapter> chapterList = TutorialTrackRegistry.getInstance().getChapters();
        int gridY = y + 14;
        int cardW = (w - 8) / 2;
        int cardH = 68;

        for (int i = 0; i < Math.min(4, chapterList.size()); i++) {
            ITutorialChapter ch = chapterList.get(i);
            int cx = x + (i % 2) * (cardW + 8);
            int cy = gridY + (i / 2) * (cardH + 6);

            if (isInside(mx, my, cx, cy, cardW, cardH)) {
                close();
                TutorialManager.getInstance().startChapter(screen, ch.getChapterId());
                return true;
            }
        }
        return false;
    }

    private boolean handleFooterClick(double mx, double my, int x, int y, int w) {
        if (isInside(mx, my, x, y, 160, 16)) {
            boolean current = ContextualNudgeManager.getInstance().isNudgesEnabled();
            ContextualNudgeManager.getInstance().setNudgesEnabled(!current);
            return true;
        }

        int hudBtnW = FOOTER_HOTKEY_BTN_WIDTH;
        int hudBtnH = FOOTER_HOTKEY_BTN_HEIGHT;
        int hudBtnX = x + w - hudBtnW;
        int hudBtnY = y - 2;

        if (isInside(mx, my, hudBtnX, hudBtnY, hudBtnW, hudBtnH)) {
            if (screen != null && screen.getHotkeyHudWidget() != null) {
                screen.getHotkeyHudWidget().toggle();
            }
            return true;
        }
        return false;
    }

    private boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
