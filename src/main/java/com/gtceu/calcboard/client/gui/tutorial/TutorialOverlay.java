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

        boolean actionDone = mgr.isStepActionCompleted();
        int outlineColor = actionDone ? TutorialManager.getGlowBorderColor(0xFF00E676) : 0xFF00E676;
        int innerOutline = actionDone ? 0xAA00FFCC : 0x8800E676;

        graphics.fill(layout.bannerX, layout.bannerY, layout.bannerX + layout.bannerW, layout.bannerY + layout.bannerH, 0xF5161C26);
        graphics.renderOutline(layout.bannerX, layout.bannerY, layout.bannerW, layout.bannerH, outlineColor);
        graphics.renderOutline(layout.bannerX + 1, layout.bannerY + 1, layout.bannerW - 2, layout.bannerH - 2, innerOutline);

        graphics.drawString(font, layout.title, layout.bannerX + 10, layout.bannerY + 8, 0xFFFFFFFF, false);

        for (int l = 0; l < layout.lines.size(); l++) {
            graphics.drawString(font, layout.lines.get(l), layout.bannerX + 10, layout.bannerY + 22 + l * 11, 0xFFCCCCCC, false);
        }

        if (step != TutorialStep.COMPLETED) {
            if (layout.hasPrev) {
                drawTutorialBtn(graphics, font, layout.prevText, layout.prevBtnX, layout.prevBtnY, layout.prevBtnW, layout.prevBtnH, mouseX, mouseY, 0xFF94A3B8, false);
            }
            drawTutorialBtn(graphics, font, layout.skipText, layout.skipBtnX, layout.skipBtnY, layout.skipBtnW, layout.skipBtnH, mouseX, mouseY, 0xFF888888, false);
            int nextColor = actionDone ? 0xFF00FFCC : 0xFF00E676;
            drawTutorialBtn(graphics, font, layout.nextText, layout.nextBtnX, layout.nextBtnY, layout.nextBtnW, layout.nextBtnH, mouseX, mouseY, nextColor, actionDone);
        } else {
            drawTutorialBtn(graphics, font, layout.nextText, layout.nextBtnX, layout.nextBtnY, layout.nextBtnW, layout.nextBtnH, mouseX, mouseY, 0xFF55FFFF, true);
        }
    }

    public static boolean mouseClicked(BoardScreen screen, int screenW, int screenH, double mouseX, double mouseY, int button) {
        TutorialManager mgr = TutorialManager.getInstance();
        if (!mgr.isActive() || button != 0) return false;

        TutorialStep step = mgr.getCurrentStep();
        Font font = (screen != null && screen.getMinecraftFont() != null)
                ? screen.getMinecraftFont()
                : (Minecraft.getInstance() != null ? Minecraft.getInstance().font : null);
        if (font == null) return false;
        CardLayout layout = computeLayout(font, step, screenW, screenH);

        if (step != TutorialStep.COMPLETED && layout.hasPrev && isInside(mouseX, mouseY, layout.prevBtnX, layout.prevBtnY, layout.prevBtnW, layout.prevBtnH)) {
            mgr.previousStep();
            return true;
        }

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
        TutorialManager mgr = TutorialManager.getInstance();
        if (step == TutorialStep.COMPLETED) {
            String stepTag = "§a★ " + Component.translatable("gui.gtcalcboard.tutorial.completed_tag").getString() + " ";
            String title = stepTag + Component.translatable("gui.gtcalcboard.tutorial.completed_title").getString();
            String descStr = Component.translatable("gui.gtcalcboard.tutorial.completed_desc").getString();
            return buildLayout(font, mgr, step, screenW, screenH, title, descStr);
        }

        boolean actionDone = mgr.isStepActionCompleted();
        String completedTag = Component.translatable("gui.gtcalcboard.tutorial.action_completed_tag").getString();

        if (mgr.isFastTrackActive()) {
            var fastSteps = TutorialTrackRegistry.getInstance().getFastTrackSteps();
            int totalSteps = fastSteps.size();
            int stepNum = Math.min(mgr.getFastTrackStepIndex() + 1, totalSteps);
            var stepDef = fastSteps.get(stepNum - 1);
            String tagLabel = Component.translatable("gui.gtcalcboard.tutorial.fast_track.tag").getString();
            String stepTag = actionDone
                    ? String.format("§a%s [%s %d/%d] ", completedTag, tagLabel, stepNum, totalSteps)
                    : String.format("§a▶ [%s %d/%d] ", tagLabel, stepNum, totalSteps);
            String title = stepTag + stepDef.getTitle().getString();
            String descStr = actionDone
                    ? resolveResultDesc(mgr, stepDef.getResultDescription().getString())
                    : stepDef.getDescription().getString();
            return buildLayout(font, mgr, step, screenW, screenH, title, descStr);
        }

        if (mgr.isChapterActive()) {
            var steps = TutorialTrackRegistry.getInstance().getChapterSteps(mgr.getActiveChapterId());
            int totalSteps = steps.size();
            int stepNum = Math.min(mgr.getActiveChapterStepIndex() + 1, totalSteps);
            var stepDef = steps.get(stepNum - 1);
            var ch = mgr.getActiveChapter();
            String chTitle = ch != null ? ch.getTitle().getString() : "Academy";
            String stepTag = actionDone
                    ? String.format("§a%s [%s - %d/%d] ", completedTag, chTitle, stepNum, totalSteps)
                    : String.format("§a▶ [%s - %d/%d] ", chTitle, stepNum, totalSteps);
            String title = stepTag + stepDef.getTitle().getString();
            String descStr = actionDone
                    ? resolveResultDesc(mgr, stepDef.getResultDescription().getString())
                    : stepDef.getDescription().getString();
            return buildLayout(font, mgr, step, screenW, screenH, title, descStr);
        }

        int totalSteps = TutorialStep.values().length - 1;
        String stepTag = actionDone
                ? String.format("§a%s [Step %d/%d] ", completedTag, step.getStepNumber(), totalSteps)
                : String.format("§a▶ [Step %d/%d] ", step.getStepNumber(), totalSteps);
        String title = stepTag + Component.translatable(step.getTitleKey()).getString();
        String defaultResult = step.getResultKey() != null ? Component.translatable(step.getResultKey()).getString() : Component.translatable(step.getDescKey()).getString();
        String descStr = actionDone ? resolveResultDesc(mgr, defaultResult) : Component.translatable(step.getDescKey()).getString();
        return buildLayout(font, mgr, step, screenW, screenH, title, descStr);
    }

    private static String resolveResultDesc(TutorialManager mgr, String fallback) {
        if (mgr.getStepResultDescKey() != null) {
            return Component.translatable(mgr.getStepResultDescKey()).getString();
        }
        return fallback;
    }

    private static CardLayout buildLayout(Font font, TutorialManager mgr, TutorialStep step, int screenW, int screenH, String title, String descStr) {
        int bannerW = Math.min(620, screenW - 32);
        String nextText;
        if (step == TutorialStep.COMPLETED) {
            nextText = Component.translatable("gui.gtcalcboard.tutorial.finish").getString();
        } else if (mgr.isStepActionCompleted()) {
            nextText = Component.translatable("gui.gtcalcboard.tutorial.continue_btn").getString();
        } else {
            nextText = Component.translatable("gui.gtcalcboard.tutorial.next").getString();
        }
        String skipText = Component.translatable("gui.gtcalcboard.tutorial.skip").getString();
        String prevText = Component.translatable("gui.gtcalcboard.tutorial.prev").getString();
        boolean hasPrev = mgr.hasPreviousStep();

        int btnH = 18;
        int nextBtnW = Math.max(64, font.width(nextText) + 16);
        int skipBtnW = Math.max(54, font.width(skipText) + 16);
        int prevBtnW = hasPrev ? Math.max(54, font.width(prevText) + 16) : 0;

        int buttonsTotalW = (step != TutorialStep.COMPLETED ? skipBtnW + 6 : 0) + (hasPrev ? prevBtnW + 6 : 0) + nextBtnW;
        int textW = Math.max(160, bannerW - buttonsTotalW - 24);

        String linePrefix = mgr.isStepActionCompleted() ? "§f" : "§7";
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(linePrefix + descStr), textW);

        int contentH = 22 + lines.size() * 11 + 10;
        int bannerH = Math.max(72, contentH);
        int bannerX = (screenW - bannerW) / 2;
        int bannerY = screenH - bannerH - 12;

        int nextBtnX = bannerX + bannerW - nextBtnW - 10;
        int nextBtnY = bannerY + bannerH - btnH - 8;
        int skipBtnX = nextBtnX - skipBtnW - 6;
        int skipBtnY = nextBtnY;
        int prevBtnX = skipBtnX - prevBtnW - 6;
        int prevBtnY = nextBtnY;

        return new CardLayout(
            bannerX, bannerY, bannerW, bannerH,
            title, lines,
            nextText, nextBtnX, nextBtnY, nextBtnW, btnH,
            skipText, skipBtnX, skipBtnY, skipBtnW, btnH,
            hasPrev, prevText, prevBtnX, prevBtnY, prevBtnW, btnH
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
        int skipBtnH,
        boolean hasPrev,
        String prevText,
        int prevBtnX,
        int prevBtnY,
        int prevBtnW,
        int prevBtnH
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

    private static void drawTutorialBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textColor, boolean highlighted) {
        boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        int bg = hover ? 0xFF2A3649 : (highlighted ? 0xFF1B382B : 0xFF1C2432);
        int border = hover ? 0xFF00FFFF : (highlighted ? 0xFF00FFCC : 0xFF3D4B66);
        graphics.fill(bx, by, bx + bw, by + bh, bg);
        graphics.renderOutline(bx, by, bw, bh, border);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + (bh - 8) / 2, textColor);
    }
}

