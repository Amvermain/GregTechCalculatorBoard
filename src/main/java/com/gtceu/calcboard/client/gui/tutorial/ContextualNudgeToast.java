package com.gtceu.calcboard.client.gui.tutorial;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.tutorial.model.ContextualNudge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Renders non-intrusive contextual toast notifications in the bottom-right corner.
 */
public class ContextualNudgeToast {
    private static final int TOAST_WIDTH = 300;
    private static final int TOAST_HEIGHT = 48;

    public static void render(GuiGraphics graphics, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        ContextualNudge nudge = ContextualNudgeManager.getInstance().getActiveNudge();
        if (nudge == null) return;

        int x = screenW - TOAST_WIDTH - 16;
        int y = screenH - TOAST_HEIGHT - 16;

        boolean closeHover = mouseX >= x + TOAST_WIDTH - 16 && mouseX <= x + TOAST_WIDTH - 4 && mouseY >= y + 4 && mouseY <= y + 16;
        boolean bodyHover = !closeHover && mouseX >= x && mouseX <= x + TOAST_WIDTH && mouseY >= y && mouseY <= y + TOAST_HEIGHT;
        int borderColor = bodyHover ? 0xFF38BDF8 : 0xFF00E676;

        graphics.fill(x, y, x + TOAST_WIDTH, y + TOAST_HEIGHT, 0xF2161C26);
        graphics.renderOutline(x, y, TOAST_WIDTH, TOAST_HEIGHT, borderColor);
        graphics.renderOutline(x + 1, y + 1, TOAST_WIDTH - 2, TOAST_HEIGHT - 2, bodyHover ? 0x6638BDF8 : 0x4400E676);

        String tag = "💡 " + Component.translatable("gui.gtcalcboard.nudge.tip_tag").getString();
        graphics.drawString(font, tag, x + 8, y + 6, 0xFFFFEE55, false);

        if (nudge.targetShortcutKey() != null && !nudge.targetShortcutKey().isEmpty()) {
            String badge = "[" + nudge.targetShortcutKey() + "]";
            int badgeW = font.width(badge);
            graphics.drawString(font, badge, x + TOAST_WIDTH - badgeW - 20, y + 6, 0xFF38BDF8, false);
        }

        graphics.drawString(font, "✕", x + TOAST_WIDTH - 14, y + 6, closeHover ? 0xFFFFFFFF : 0xFF888888, false);

        List<net.minecraft.util.FormattedCharSequence> lines = font.split(nudge.message(), TOAST_WIDTH - 16);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.drawString(font, lines.get(i), x + 8, y + 20 + i * 11, 0xFFDDDDDD, false);
        }
    }

    public static boolean mouseClicked(BoardScreen screen, int screenW, int screenH, double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        ContextualNudge nudge = ContextualNudgeManager.getInstance().getActiveNudge();
        if (nudge == null) return false;

        int x = screenW - TOAST_WIDTH - 16;
        int y = screenH - TOAST_HEIGHT - 16;

        if (mouseX >= x && mouseX <= x + TOAST_WIDTH && mouseY >= y && mouseY <= y + TOAST_HEIGHT) {
            boolean isCloseClick = mouseX >= x + TOAST_WIDTH - 20 && mouseY <= y + 18;
            ContextualNudgeManager.getInstance().dismissActiveNudge();
            if (!isCloseClick && nudge.relatedChapterId() != null) {
                TutorialManager.getInstance().startChapter(screen, nudge.relatedChapterId());
            }
            return true;
        }
        return false;
    }
}
