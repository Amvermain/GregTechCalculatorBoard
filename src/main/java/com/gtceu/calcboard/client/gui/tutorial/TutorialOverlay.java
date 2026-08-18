package com.gtceu.calcboard.client.gui.tutorial;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.NodeWidget;
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

        // 1. Draw Tutorial Bottom Banner Card
        int bannerW = Math.min(540, screenW - 40);
        int bannerH = 75;
        int bannerX = (screenW - bannerW) / 2;
        int bannerY = screenH - bannerH - 12;

        // Card background & borders
        graphics.fill(bannerX, bannerY, bannerX + bannerW, bannerY + bannerH, 0xF5161C26);
        graphics.renderOutline(bannerX, bannerY, bannerW, bannerH, 0xFF00E676);
        graphics.renderOutline(bannerX + 1, bannerY + 1, bannerW - 2, bannerH - 2, 0x8800E676);

        // Header Title
        String stepTag = step != TutorialStep.COMPLETED ? String.format("§a🎓 [Step %d/6] ", step.getStepNumber()) : "§a🎉 [완료] ";
        String titleStr = stepTag + Component.translatable(step.getTitleKey()).getString();
        graphics.drawString(font, titleStr, bannerX + 10, bannerY + 8, 0xFFFFFFFF, false);

        // Description lines
        String descStr = Component.translatable(step.getDescKey()).getString();
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal("§7" + descStr), bannerW - 140);
        for (int l = 0; l < Math.min(3, lines.size()); l++) {
            graphics.drawString(font, lines.get(l), bannerX + 10, bannerY + 22 + l * 11, 0xFFCCCCCC, false);
        }

        // Action Buttons: [건너뛰기] / [다음] / [닫기]
        int btnW = 60;
        int btnH = 18;
        int nextBtnX = bannerX + bannerW - btnW - 10;
        int nextBtnY = bannerY + bannerH - btnH - 8;

        int skipBtnX = nextBtnX - btnW - 6;
        int skipBtnY = nextBtnY;

        if (step != TutorialStep.COMPLETED) {
            drawTutorialBtn(graphics, font, Component.translatable("gui.gtcalcboard.tutorial.skip").getString(), skipBtnX, skipBtnY, btnW, btnH, mouseX, mouseY, 0xFF888888);
            drawTutorialBtn(graphics, font, Component.translatable("gui.gtcalcboard.tutorial.next").getString(), nextBtnX, nextBtnY, btnW, btnH, mouseX, mouseY, 0xFF00E676);
        } else {
            drawTutorialBtn(graphics, font, Component.translatable("gui.gtcalcboard.tutorial.finish").getString(), nextBtnX, nextBtnY, btnW, btnH, mouseX, mouseY, 0xFF55FFFF);
        }

        // 2. Render Animated Pulse Wire Guides for Interactive Targets
        renderTargetHighlights(graphics, screen, step);
    }

    private static void renderTargetHighlights(GuiGraphics graphics, BoardScreen screen, TutorialStep step) {
        if (step == TutorialStep.STEP_4_NORMAL_WIRING || step == TutorialStep.STEP_5_SHIFT_WIRING) {
            // Render hint dynamic bezier line between Boiler Output port and Turbine Input port
            NodeWidget boilerW = findWidget(screen, TutorialManager.getInstance().getBoilerNodeId());
            NodeWidget turbineW = findWidget(screen, TutorialManager.getInstance().getTurbineNodeId());

            if (boilerW != null && turbineW != null) {
                double zoom = screen.getZoom();
                double panX = screen.getPanX();
                double panY = screen.getPanY();

                float x1 = (float) ((boilerW.getOutputPortX(0) * zoom) + panX);
                float y1 = (float) ((boilerW.getOutputPortY(0) * zoom) + panY);
                float x2 = (float) ((turbineW.getInputPortX(0) * zoom) + panX);
                float y2 = (float) ((turbineW.getInputPortY(0) * zoom) + panY);

                float time = (System.currentTimeMillis() % 1000) / 1000.0f;
                int alpha = (int) (150 + 105 * Math.sin(time * Math.PI));
                int wireGuideColor = (alpha << 24) | 0x00FF88;

                com.gtceu.calcboard.client.gui.ConnectionRenderer.renderBezier(graphics, x1, y1, x2, y2, wireGuideColor, 2.5f);
            }
        }
    }

    public static boolean mouseClicked(BoardScreen screen, int screenW, int screenH, double mouseX, double mouseY, int button) {
        TutorialManager mgr = TutorialManager.getInstance();
        if (!mgr.isActive() || button != 0) return false;

        int bannerW = Math.min(540, screenW - 40);
        int bannerH = 75;
        int bannerX = (screenW - bannerW) / 2;
        int bannerY = screenH - bannerH - 12;

        int btnW = 60;
        int btnH = 18;
        int nextBtnX = bannerX + bannerW - btnW - 10;
        int nextBtnY = bannerY + bannerH - btnH - 8;

        int skipBtnX = nextBtnX - btnW - 6;
        int skipBtnY = nextBtnY;

        if (mouseX >= nextBtnX && mouseX <= nextBtnX + btnW && mouseY >= nextBtnY && mouseY <= nextBtnY + btnH) {
            if (mgr.getCurrentStep() == TutorialStep.COMPLETED) {
                mgr.stopTutorial();
            } else {
                mgr.nextStep();
            }
            return true;
        }

        if (mgr.getCurrentStep() != TutorialStep.COMPLETED) {
            if (mouseX >= skipBtnX && mouseX <= skipBtnX + btnW && mouseY >= skipBtnY && mouseY <= skipBtnY + btnH) {
                mgr.stopTutorial();
                return true;
            }
        }

        return false;
    }

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
