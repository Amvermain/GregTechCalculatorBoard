package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.client.gui.BoardScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class AdaptiveStatusBar {

    public static final int BAR_HEIGHT = 20;
    private final BoardScreen screen;

    private int balanceChipX = 0;
    private int balanceChipW = 0;
    private int bomChipX = 0;
    private int bomChipW = 0;
    private int pauseChipX = 0;
    private int pauseChipW = 0;

    public AdaptiveStatusBar(BoardScreen screen) {
        this.screen = screen;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int width = screen.width;
        int height = screen.height;
        int y = height - BAR_HEIGHT;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 320.0f);

        renderBackground(graphics, width, y);

        Font font = Minecraft.getInstance().font;
        renderLeftStatus(graphics, font, y);
        renderCenterShortcuts(graphics, font, width, y);
        renderRightChips(graphics, font, width, y, mouseX, mouseY);

        graphics.pose().popPose();
    }

    private void renderBackground(GuiGraphics graphics, int width, int y) {
        graphics.fill(0, y, width, y + BAR_HEIGHT, 0xF50B1120);
        graphics.fill(0, y, width, y + 1, 0xFF1E293B);
    }

    private void renderLeftStatus(GuiGraphics graphics, Font font, int y) {
        int selectedCount = screen.getSelectedNodeIds().size();
        String text;
        if (selectedCount > 0) {
            text = Component.translatable("gui.gtcalcboard.statusbar.selected_nodes", selectedCount).getString();
        } else {
            int nodeCount = screen.getGraph().getNodes().size();
            int wireCount = screen.getGraph().getConnections().size();
            text = Component.translatable("gui.gtcalcboard.statusbar.total_nodes", nodeCount, wireCount).getString();
        }
        graphics.drawString(font, "● " + text, 8, y + 6, 0xFF94A3B8, false);
    }

    private void renderCenterShortcuts(GuiGraphics graphics, Font font, int width, int y) {
        if (width < 520) {
            return;
        }
        String hint = Component.translatable("gui.gtcalcboard.statusbar.hint_shortcuts").getString();
        int hintW = font.width(hint);
        int hintX = (width - hintW) / 2;
        graphics.drawString(font, hint, hintX, y + 6, 0xFF64748B, false);
    }

    private void renderRightChips(GuiGraphics graphics, Font font, int width, int y, int mouseX, int mouseY) {
        int curX = width - 8;

        curX = renderPauseChip(graphics, font, y, curX, mouseX, mouseY);
        curX = renderBomChip(graphics, font, y, curX, mouseX, mouseY);
        renderBalanceChip(graphics, font, y, curX, mouseX, mouseY);
    }

    private int renderPauseChip(GuiGraphics graphics, Font font, int y, int rightEdge, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.hasSingleplayerServer()) {
            this.pauseChipW = 0;
            return rightEdge;
        }

        boolean isPaused = BoardManager.getInstance().isPauseGameInSingleplayer();
        String pauseIcon = isPaused ? "⏸" : "▶";
        int chipW = 20;
        int chipX = rightEdge - chipW;

        this.pauseChipX = chipX;
        this.pauseChipW = chipW;

        boolean hovered = mouseX >= chipX && mouseX <= chipX + chipW && mouseY >= y + 2 && mouseY <= y + BAR_HEIGHT - 2;
        int bg = hovered ? 0xFF334155 : (isPaused ? 0xFF1E293B : 0xFF0F172A);
        int border = isPaused ? 0xFF38BDF8 : 0xFF334155;

        graphics.fill(chipX, y + 2, chipX + chipW, y + BAR_HEIGHT - 2, bg);
        graphics.renderOutline(chipX, y + 2, chipW, BAR_HEIGHT - 4, border);
        graphics.drawCenteredString(font, pauseIcon, chipX + chipW / 2, y + 6, isPaused ? 0xFF38BDF8 : 0xFF94A3B8);

        return chipX - 6;
    }

    private int renderBomChip(GuiGraphics graphics, Font font, int y, int rightEdge, int mouseX, int mouseY) {
        if (!ModCompatHelper.isBoMSupported()) {
            this.bomChipW = 0;
            return rightEdge;
        }

        String bomLabel = "▦ BOM";
        int chipW = font.width(bomLabel) + 12;
        int chipX = rightEdge - chipW;

        this.bomChipX = chipX;
        this.bomChipW = chipW;

        boolean hovered = mouseX >= chipX && mouseX <= chipX + chipW && mouseY >= y + 2 && mouseY <= y + BAR_HEIGHT - 2;
        int bg = hovered ? 0xFF422006 : 0xFF1C1917;
        int border = hovered ? 0xFFF59E0B : 0xFF78350F;

        graphics.fill(chipX, y + 2, chipX + chipW, y + BAR_HEIGHT - 2, bg);
        graphics.renderOutline(chipX, y + 2, chipW, BAR_HEIGHT - 4, border);
        graphics.drawCenteredString(font, bomLabel, chipX + chipW / 2, y + 6, 0xFFFBBF24);

        return chipX - 6;
    }

    private void renderBalanceChip(GuiGraphics graphics, Font font, int y, int rightEdge, int mouseX, int mouseY) {
        String balLabel = "∑ Balance";
        int chipW = font.width(balLabel) + 12;
        int chipX = rightEdge - chipW;

        this.balanceChipX = chipX;
        this.balanceChipW = chipW;

        boolean hovered = mouseX >= chipX && mouseX <= chipX + chipW && mouseY >= y + 2 && mouseY <= y + BAR_HEIGHT - 2;
        int bg = hovered ? 0xFF0C4A6E : 0xFF0B192C;
        int border = hovered ? 0xFF38BDF8 : 0xFF0284C7;

        graphics.fill(chipX, y + 2, chipX + chipW, y + BAR_HEIGHT - 2, bg);
        graphics.renderOutline(chipX, y + 2, chipW, BAR_HEIGHT - 4, border);
        graphics.drawCenteredString(font, balLabel, chipX + chipW / 2, y + 6, 0xFF38BDF8);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int y = screen.height - BAR_HEIGHT;
        if (mouseY < y || mouseY > screen.height || button != 0) {
            return false;
        }

        if (isInside(mouseX, balanceChipX, balanceChipW)) {
            if (screen.getGlobalBalanceDialog() != null) {
                screen.getGlobalBalanceDialog().open();
            }
            return true;
        }

        if (isInside(mouseX, bomChipX, bomChipW)) {
            if (screen.getMultiblockBOMDialog() != null) {
                screen.getMultiblockBOMDialog().open();
            }
            return true;
        }

        if (isInside(mouseX, pauseChipX, pauseChipW)) {
            boolean nextVal = !BoardManager.getInstance().isPauseGameInSingleplayer();
            BoardManager.getInstance().setPauseGameInSingleplayer(nextVal);
            screen.rebuildWidgets();
            screen.markSummaryDirty();
            String statusStr = nextVal ? "ON" : "OFF";
            BoardToast.show(Component.literal("§e⚙ ").append(Component.translatable("gui.gtcalcboard.toast.pause_toggle_hint", statusStr)));
            return true;
        }

        return true;
    }

    private boolean isInside(double x, int startX, int width) {
        return width > 0 && x >= startX && x <= startX + width;
    }
}
