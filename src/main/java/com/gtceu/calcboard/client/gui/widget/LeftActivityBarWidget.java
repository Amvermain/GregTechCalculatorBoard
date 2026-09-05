package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.c2s.C2SPingPresencePacket;
import com.gtceu.calcboard.network.packet.c2s.C2SRequestWorkspacePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.UUID;

public class LeftActivityBarWidget {

    public static final int BAR_WIDTH = 22;
    private static final int BTN_SIZE = 18;
    private static final int BTN_SPACING = 3;

    private final BoardScreen screen;

    private int pagesBtnY;
    private int favoritesBtnY;
    private int blueprintsBtnY;
    private int teamBtnY;
    private int helpBtnY;
    private int settingsBtnY;

    public LeftActivityBarWidget(BoardScreen screen) {
        this.screen = screen;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int topY = screen.getHeaderBottomY();
        int bottomY = screen.height - AdaptiveStatusBar.BAR_HEIGHT;
        int barHeight = bottomY - topY;
        if (barHeight <= 40) return;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 340.0f);

        renderBackground(graphics, topY, barHeight);

        Font font = Minecraft.getInstance().font;
        renderIconButtons(graphics, font, topY, bottomY, mouseX, mouseY);

        graphics.pose().popPose();
    }

    private void renderBackground(GuiGraphics graphics, int topY, int barHeight) {
        graphics.fill(0, topY, BAR_WIDTH, topY + barHeight, 0xF50A0F1D);
        graphics.fill(BAR_WIDTH - 1, topY, BAR_WIDTH, topY + barHeight, 0xFF1E293B);
    }

    private void renderIconButtons(GuiGraphics graphics, Font font, int topY, int bottomY, int mouseX, int mouseY) {
        int curY = topY + 4;

        this.pagesBtnY = curY;
        boolean pagesActive = screen.getPageBrowserDrawer() != null && screen.getPageBrowserDrawer().isOpen();
        renderButton(graphics, font, 2, pagesBtnY, "📁", pagesActive, mouseX, mouseY, 0xFFFCD34D);
        curY += BTN_SIZE + BTN_SPACING;

        this.favoritesBtnY = curY;
        boolean favActive = screen.getFavoritesDockWidget() != null && screen.getFavoritesDockWidget().isExpanded();
        renderButton(graphics, font, 2, favoritesBtnY, "⭐", favActive, mouseX, mouseY, 0xFF38BDF8);
        curY += BTN_SIZE + BTN_SPACING;

        this.blueprintsBtnY = curY;
        renderButton(graphics, font, 2, blueprintsBtnY, "📋", false, mouseX, mouseY, 0xFFF97316);
        curY += BTN_SIZE + BTN_SPACING;

        this.teamBtnY = curY;
        boolean teamActive = ClientWorkspaceState.getInstance().isTeamMode();
        renderButton(graphics, font, 2, teamBtnY, "👥", teamActive, mouseX, mouseY, 0xFFA855F7);

        this.settingsBtnY = bottomY - BTN_SIZE - 4;
        renderButton(graphics, font, 2, settingsBtnY, "⚙", false, mouseX, mouseY, 0xFFF59E0B);

        this.helpBtnY = settingsBtnY - BTN_SIZE - BTN_SPACING;
        boolean helpActive = screen.getHotkeyHudWidget() != null && screen.getHotkeyHudWidget().isExpanded();
        renderButton(graphics, font, 2, helpBtnY, "?", helpActive, mouseX, mouseY, 0xFF38BDF8);
    }

    private void renderButton(GuiGraphics graphics, Font font, int x, int y, String icon, boolean active, int mouseX, int mouseY, int iconColor) {
        boolean hovered = isHovered(mouseX, mouseY, x, y);

        int bgColor = active ? 0xEE0C4A6E : (hovered ? 0xFF1E293B : 0xFF0F172A);
        int borderColor = active ? 0xFF38BDF8 : (hovered ? 0xFF64748B : 0xFF1E293B);

        graphics.fill(x, y, x + BTN_SIZE, y + BTN_SIZE, bgColor);
        graphics.renderOutline(x, y, BTN_SIZE, BTN_SIZE, borderColor);

        int textW = font.width(icon);
        int textX = x + (BTN_SIZE - textW) / 2;
        int textY = y + 5;
        graphics.drawString(font, icon, textX, textY, active ? 0xFFFFFFFF : iconColor, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (mouseX < 0 || mouseX > BAR_WIDTH) return false;

        if (isHovered(mouseX, mouseY, 2, pagesBtnY)) {
            playClickSound();
            togglePageBrowser();
            return true;
        }
        if (isHovered(mouseX, mouseY, 2, favoritesBtnY)) {
            playClickSound();
            toggleFavoritesDock();
            return true;
        }
        if (isHovered(mouseX, mouseY, 2, blueprintsBtnY)) {
            playClickSound();
            openBlueprintsDialog();
            return true;
        }
        if (isHovered(mouseX, mouseY, 2, teamBtnY)) {
            playClickSound();
            toggleTeamMode();
            return true;
        }
        if (isHovered(mouseX, mouseY, 2, helpBtnY)) {
            playClickSound();
            toggleHotkeyHud();
            return true;
        }
        if (isHovered(mouseX, mouseY, 2, settingsBtnY)) {
            playClickSound();
            screen.openSettingsDialog();
            return true;
        }

        return isInsideBar(mouseY);
    }

    private void toggleHotkeyHud() {
        if (screen.getHotkeyHudWidget() != null) {
            screen.getHotkeyHudWidget().toggle();
        }
    }

    private void togglePageBrowser() {
        if (screen.getPageBrowserDrawer() != null) {
            screen.getPageBrowserDrawer().toggle();
        }
    }

    private void toggleFavoritesDock() {
        if (screen.getFavoritesDockWidget() != null) {
            screen.getFavoritesDockWidget().toggle();
        }
    }

    private void openBlueprintsDialog() {
        if (screen.getDiskBlueprintsDialog() != null) {
            screen.getDiskBlueprintsDialog().open();
        }
    }

    private void toggleTeamMode() {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        if (!state.isCollaborationEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (state.isTeamMode()) {
            state.autoCommitAndRelease(screen, state.getActiveTeamPageId());
            state.setCurrentMode(ClientWorkspaceState.WorkspaceMode.LOCAL);
            NetworkHandler.sendToServer(new C2SPingPresencePacket(state.getCurrentTeamId(), state.getActiveTeamPageId(), false));
        } else {
            state.setCurrentMode(ClientWorkspaceState.WorkspaceMode.TEAM);
            UUID teamId = state.getCurrentTeamId() != null ? state.getCurrentTeamId() : (mc.player != null ? mc.player.getUUID() : UUID.randomUUID());
            String activePageId = state.getActiveTeamPageId() != null ? state.getActiveTeamPageId() : "page_main";
            NetworkHandler.sendToServer(new C2SRequestWorkspacePacket(teamId, activePageId));
            NetworkHandler.sendToServer(new C2SPingPresencePacket(teamId, activePageId, true));
        }
        screen.rebuildWidgets();
        screen.markSummaryDirty();
    }

    public void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (mouseX < 0 || mouseX > BAR_WIDTH) return;

        if (isHovered(mouseX, mouseY, 2, pagesBtnY)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.activity_bar.pages"), mouseX, mouseY, screen.width, screen.height);
            return;
        }
        if (isHovered(mouseX, mouseY, 2, favoritesBtnY)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.activity_bar.favorites"), mouseX, mouseY, screen.width, screen.height);
            return;
        }
        if (isHovered(mouseX, mouseY, 2, blueprintsBtnY)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.activity_bar.blueprints"), mouseX, mouseY, screen.width, screen.height);
            return;
        }
        if (isHovered(mouseX, mouseY, 2, teamBtnY)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.activity_bar.team"), mouseX, mouseY, screen.width, screen.height);
            return;
        }
        if (isHovered(mouseX, mouseY, 2, helpBtnY)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.activity_bar.help"), mouseX, mouseY, screen.width, screen.height);
            return;
        }
        if (isHovered(mouseX, mouseY, 2, settingsBtnY)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.activity_bar.settings"), mouseX, mouseY, screen.width, screen.height);
        }
    }

    private boolean isHovered(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX <= x + BTN_SIZE && mouseY >= y && mouseY <= y + BTN_SIZE;
    }

    private boolean isInsideBar(double mouseY) {
        int topY = screen.getHeaderBottomY();
        int bottomY = screen.height - AdaptiveStatusBar.BAR_HEIGHT;
        return mouseY >= topY && mouseY <= bottomY;
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }
}
