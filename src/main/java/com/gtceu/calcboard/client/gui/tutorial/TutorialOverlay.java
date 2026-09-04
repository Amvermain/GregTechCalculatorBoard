package com.gtceu.calcboard.client.gui.tutorial;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Renders interactive tutorial guidance cards, spotlight bounding boxes, and animated wiring hints.
 */
public class TutorialOverlay {

    public static void render(GuiGraphics graphics, Font font, BoardScreen screen, int screenW, int screenH, int mouseX, int mouseY) {
        TutorialManager mgr = TutorialManager.getInstance();
        if (!mgr.isActive()) return;

        TutorialStep step = mgr.getCurrentStep();
        CardLayout layout = computeLayout(font, step, screenW, screenH);

        graphics.fill(layout.bannerX, layout.bannerY, layout.bannerX + layout.bannerW, layout.bannerY + layout.bannerH, 0xF5161C26);
        graphics.renderOutline(layout.bannerX, layout.bannerY, layout.bannerW, layout.bannerH, 0xFF00E676);
        graphics.renderOutline(layout.bannerX + 1, layout.bannerY + 1, layout.bannerW - 2, layout.bannerH - 2, 0x8800E676);

        graphics.drawString(font, layout.title, layout.bannerX + 10, layout.bannerY + 8, 0xFFFFFFFF, false);

        for (int l = 0; l < layout.lines.size(); l++) {
            graphics.drawString(font, layout.lines.get(l), layout.bannerX + 10, layout.bannerY + 22 + l * 11, 0xFFCCCCCC, false);
        }

        if (step != TutorialStep.COMPLETED) {
            drawTutorialBtn(graphics, font, layout.skipText, layout.skipBtnX, layout.skipBtnY, layout.skipBtnW, layout.skipBtnH, mouseX, mouseY, 0xFF888888);
            drawTutorialBtn(graphics, font, layout.nextText, layout.nextBtnX, layout.nextBtnY, layout.nextBtnW, layout.nextBtnH, mouseX, mouseY, 0xFF00E676);
        } else {
            drawTutorialBtn(graphics, font, layout.nextText, layout.nextBtnX, layout.nextBtnY, layout.nextBtnW, layout.nextBtnH, mouseX, mouseY, 0xFF55FFFF);
        }
    }

    public static boolean mouseClicked(BoardScreen screen, int screenW, int screenH, double mouseX, double mouseY, int button) {
        TutorialManager mgr = TutorialManager.getInstance();
        if (!mgr.isActive() || button != 0) return false;

        TutorialStep step = mgr.getCurrentStep();
        Font font = Minecraft.getInstance().font;
        CardLayout layout = computeLayout(font, step, screenW, screenH);

        if (isInside(mouseX, mouseY, layout.nextBtnX, layout.nextBtnY, layout.nextBtnW, layout.nextBtnH)) {
            if (step == TutorialStep.COMPLETED) {
                mgr.stopTutorial();
            } else {
                mgr.nextStep();
            }
            return true;
        }

        if (step != TutorialStep.COMPLETED && isInside(mouseX, mouseY, layout.skipBtnX, layout.skipBtnY, layout.skipBtnW, layout.skipBtnH)) {
            mgr.stopTutorial();
            return true;
        }

        return false;
    }

    private static CardLayout computeLayout(Font font, TutorialStep step, int screenW, int screenH) {
        int bannerW = Math.min(580, screenW - 32);
        int totalSteps = TutorialStep.values().length - 1;
        String stepTag = step != TutorialStep.COMPLETED 
            ? String.format("§a🎓 [Step %d/%d] ", step.getStepNumber(), totalSteps) 
            : "§a🎉 " + Component.translatable("gui.gtcalcboard.tutorial.completed_tag").getString() + " ";
        String title = stepTag + Component.translatable(step.getTitleKey()).getString();

        String nextText = step != TutorialStep.COMPLETED
            ? Component.translatable("gui.gtcalcboard.tutorial.next").getString()
            : Component.translatable("gui.gtcalcboard.tutorial.finish").getString();
        String skipText = Component.translatable("gui.gtcalcboard.tutorial.skip").getString();

        int btnH = 18;
        int nextBtnW = Math.max(64, font.width(nextText) + 16);
        int skipBtnW = Math.max(54, font.width(skipText) + 16);

        int buttonsTotalW = (step != TutorialStep.COMPLETED ? skipBtnW + 6 : 0) + nextBtnW;
        int textW = Math.max(160, bannerW - buttonsTotalW - 24);

        String descStr = Component.translatable(step.getDescKey()).getString();
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal("§7" + descStr), textW);

        int contentH = 22 + lines.size() * 11 + 10;
        int bannerH = Math.max(72, contentH);
        int bannerX = (screenW - bannerW) / 2;
        int bannerY = screenH - bannerH - 12;

        int nextBtnX = bannerX + bannerW - nextBtnW - 10;
        int nextBtnY = bannerY + bannerH - btnH - 8;
        int skipBtnX = nextBtnX - skipBtnW - 6;
        int skipBtnY = nextBtnY;

        return new CardLayout(
            bannerX, bannerY, bannerW, bannerH,
            title, lines,
            nextText, nextBtnX, nextBtnY, nextBtnW, btnH,
            skipText, skipBtnX, skipBtnY, skipBtnW, btnH
        );
    }

    private static boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private record CardLayout(
        int bannerX,
        int bannerY,
        int bannerW,
        int bannerH,
        String title,
        List<net.minecraft.util.FormattedCharSequence> lines,
        String nextText,
        int nextBtnX,
        int nextBtnY,
        int nextBtnW,
        int nextBtnH,
        String skipText,
        int skipBtnX,
        int skipBtnY,
        int skipBtnW,
        int skipBtnH
    ) {}

    private static NodeWidget findWidget(BoardScreen screen, String nodeId) {
        if (nodeId == null || screen == null) return null;
        for (NodeWidget w : screen.getNodeWidgets()) {
            if (w.getNode().getId().equals(nodeId)) {
                return w;
            }
        }
        return null;
    }

    private static void drawTutorialBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textColor) {
        boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        graphics.fill(bx, by, bx + bw, by + bh, hover ? 0xFF2A3649 : 0xFF1C2432);
        graphics.renderOutline(bx, by, bw, bh, hover ? 0xFF00FFFF : 0xFF3D4B66);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + (bh - 8) / 2, textColor);
    }
}

