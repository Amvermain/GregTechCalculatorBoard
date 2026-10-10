package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.solver.WorkspaceFlowCoordinator;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.util.ConfirmLinkHelper;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import com.gtceu.calcboard.client.update.ClientUpdateNotifier;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.c2s.C2SPingPresencePacket;
import com.gtceu.calcboard.network.packet.c2s.C2SRequestWorkspacePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.UUID;

public class LeftActivityBarWidget {

    public static final int BAR_WIDTH = 22;
    private static final int BTN_SIZE = 18;
    private static final int BTN_SPACING = 3;

    private static final int POPUP_W = 160;
    private static final int POPUP_ITEM_H = 18;
    private static final int POPUP_PADDING = 3;
    private static final int POPUP_H = POPUP_PADDING * 2 + POPUP_ITEM_H * 2;

    private final IBoardScreenContext screen;

    private int pagesBtnY;
    private int favoritesBtnY;
    private int blueprintsBtnY;
    private int bomBtnY;
    private int balanceBtnY;
    private int teamBtnY;
    private int communityBtnY = -100;
    private int helpBtnY;
    private int settingsBtnY;

    private boolean communityPopupOpen = false;

    public LeftActivityBarWidget(IBoardScreenContext screen) {
        this.screen = screen;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int topY = screen.getHeaderBottomY();
        int bottomY = screen.getScreenHeight() - AdaptiveStatusBar.BAR_HEIGHT;
        int barHeight = bottomY - topY;
        if (barHeight <= 40) return;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 340.0f);

        renderBackground(graphics, topY, barHeight);

        Font font = Minecraft.getInstance().font;
        renderIconButtons(graphics, font, topY, bottomY, mouseX, mouseY);

        if (communityPopupOpen && communityBtnY > 0) {
            renderCommunityPopup(graphics, font, bottomY, mouseX, mouseY);
        }

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

        if (com.gtceu.calcboard.api.util.ModCompatHelper.isBoMSupported()) {
            this.bomBtnY = curY;
            boolean bomActive = screen.getMultiblockBOMDialog() != null && screen.getMultiblockBOMDialog().isVisible();
            renderButton(graphics, font, 2, bomBtnY, "▦", bomActive, mouseX, mouseY, 0xFFFBBF24);
            curY += BTN_SIZE + BTN_SPACING;
        } else {
            this.bomBtnY = -100;
        }

        this.balanceBtnY = curY;
        boolean balanceActive = screen.getGlobalBalanceDialog() != null && screen.getGlobalBalanceDialog().isVisible();
        renderButton(graphics, font, 2, balanceBtnY, "📊", balanceActive, mouseX, mouseY, 0xFF38BDF8);
        curY += BTN_SIZE + BTN_SPACING;

        boolean showTeam = ClientWorkspaceState.getInstance().isCollaborationEnabled();
        if (showTeam) {
            this.teamBtnY = curY;
            boolean teamActive = ClientWorkspaceState.getInstance().isTeamMode();
            renderButton(graphics, font, 2, teamBtnY, "👥", teamActive, mouseX, mouseY, 0xFFA855F7);
            curY += BTN_SIZE + BTN_SPACING;
        } else {
            this.teamBtnY = -100;
        }

        this.settingsBtnY = bottomY - BTN_SIZE - 4;
        renderButton(graphics, font, 2, settingsBtnY, "⚙", false, mouseX, mouseY, 0xFFF59E0B);
        if (ClientUpdateNotifier.getInstance().isBadgeVisible()) {
            renderUpdateBadge(graphics, 2, settingsBtnY);
        }

        this.helpBtnY = settingsBtnY - BTN_SIZE - BTN_SPACING;
        boolean helpActive = screen.getHotkeyHudWidget() != null && screen.getHotkeyHudWidget().isExpanded();
        renderButton(graphics, font, 2, helpBtnY, "?", helpActive, mouseX, mouseY, 0xFF38BDF8);

        int candCommunityY = helpBtnY - BTN_SIZE - BTN_SPACING;
        if (candCommunityY > curY + 4) {
            this.communityBtnY = candCommunityY;
            renderButton(graphics, font, 2, communityBtnY, "💬", communityPopupOpen, mouseX, mouseY, 0xFF818CF8);
        } else {
            this.communityBtnY = -100;
        }
    }

    private void renderCommunityPopup(GuiGraphics graphics, Font font, int bottomY, int mouseX, int mouseY) {
        int px = BAR_WIDTH + 3;
        int py = getCommunityPopupY(bottomY);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 100.0f);

        graphics.fill(px, py, px + POPUP_W, py + POPUP_H, 0xF80F172A);
        graphics.renderOutline(px, py, POPUP_W, POPUP_H, 0xFF334155);

        int row1Y = py + POPUP_PADDING;
        int row2Y = row1Y + POPUP_ITEM_H;

        renderPopupRow(graphics, font, px, row1Y, "🐛", "gui.gtcalcboard.community.github_issues", mouseX, mouseY);
        renderPopupRow(graphics, font, px, row2Y, "💬", "gui.gtcalcboard.community.discord", mouseX, mouseY);

        graphics.pose().popPose();
    }

    private void renderPopupRow(GuiGraphics graphics, Font font, int px, int rowY, String icon, String textKey, int mouseX, int mouseY) {
        boolean hovered = isHovered(mouseX, mouseY, px + 2, rowY, POPUP_W - 4, POPUP_ITEM_H);
        if (hovered) {
            graphics.fill(px + 2, rowY, px + POPUP_W - 2, rowY + POPUP_ITEM_H, 0xFF1E293B);
        }

        String label = icon + " " + Component.translatable(textKey).getString();
        int textColor = hovered ? 0xFFFFFFFF : 0xFFCBD5E1;
        graphics.drawString(font, label, px + 6, rowY + 5, textColor, false);
    }

    private int getCommunityPopupY(int bottomY) {
        int maxBottom = bottomY - 4;
        int desiredY = communityBtnY - 6;
        int clampedY = Math.min(desiredY, maxBottom - POPUP_H);
        return Math.max(screen.getHeaderBottomY() + 4, clampedY);
    }

    private void renderUpdateBadge(GuiGraphics graphics, int btnX, int btnY) {
        int dotX = btnX + BTN_SIZE - 5;
        int dotY = btnY + 2;
        graphics.fill(dotX, dotY, dotX + 4, dotY + 4, 0xFF10B981);
        graphics.renderOutline(dotX, dotY, 4, 4, 0xFF064E3B);
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

        if (communityPopupOpen && handleCommunityPopupClick(mouseX, mouseY)) {
            return true;
        }

        if (mouseX < 0 || mouseX > BAR_WIDTH) {
            communityPopupOpen = false;
            return false;
        }

        if (isHovered(mouseX, mouseY, 2, communityBtnY)) {
            playClickSound();
            communityPopupOpen = !communityPopupOpen;
            closePageBrowserIfOpen();
            return true;
        }

        communityPopupOpen = false;

        if (isHovered(mouseX, mouseY, 2, pagesBtnY)) {
            playClickSound();
            togglePageBrowser();
            return true;
        }

        closePageBrowserIfOpen();

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
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isBoMSupported() && isHovered(mouseX, mouseY, 2, bomBtnY)) {
            playClickSound();
            toggleMultiblockBOM();
            return true;
        }
        if (isHovered(mouseX, mouseY, 2, balanceBtnY)) {
            playClickSound();
            toggleGlobalBalance();
            return true;
        }
        if (ClientWorkspaceState.getInstance().isCollaborationEnabled() && isHovered(mouseX, mouseY, 2, teamBtnY)) {
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

    private void closePageBrowserIfOpen() {
        if (screen.getPageBrowserDrawer() != null && screen.getPageBrowserDrawer().isOpen()) {
            screen.getPageBrowserDrawer().setOpen(false);
        }
    }

    private void toggleHotkeyHud() {
        if (screen.getHotkeyHudWidget() != null) {
            screen.getHotkeyHudWidget().toggle();
        }
    }

    private void togglePageBrowser() {
        if (screen.getPageBrowserDrawer() != null) {
            boolean willOpen = !screen.getPageBrowserDrawer().isOpen();
            if (willOpen && screen.getFavoritesDockWidget() != null) {
                screen.getFavoritesDockWidget().setExpanded(false);
            }
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

    private void toggleMultiblockBOM() {
        if (screen.getMultiblockBOMDialog() != null) {
            if (screen.getMultiblockBOMDialog().isVisible()) {
                screen.getMultiblockBOMDialog().close();
            } else {
                screen.getMultiblockBOMDialog().open();
            }
        }
    }

    private void toggleGlobalBalance() {
        if (screen.getGlobalBalanceDialog() != null) {
            if (screen.getGlobalBalanceDialog().isVisible()) {
                screen.getGlobalBalanceDialog().close();
            } else {
                screen.getGlobalBalanceDialog().open();
            }
        }
    }

    private void toggleTeamMode() {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        if (!state.isCollaborationEnabled()) return;

        ClientWorkspaceState.WorkspaceMode target = state.isTeamMode()
                ? ClientWorkspaceState.WorkspaceMode.LOCAL
                : ClientWorkspaceState.WorkspaceMode.TEAM;
        screen.switchToWorkspaceMode(target);
    }

    private boolean handleCommunityPopupClick(double mouseX, double mouseY) {
        int bottomY = screen.getScreenHeight() - AdaptiveStatusBar.BAR_HEIGHT;
        int px = BAR_WIDTH + 3;
        int py = getCommunityPopupY(bottomY);
        int row1Y = py + POPUP_PADDING;
        int row2Y = row1Y + POPUP_ITEM_H;

        if (isHovered(mouseX, mouseY, px + 2, row1Y, POPUP_W - 4, POPUP_ITEM_H)) {
            playClickSound();
            communityPopupOpen = false;
            Screen parentScreen = screen instanceof Screen s ? s : null;
            ConfirmLinkHelper.openExternalLink(parentScreen, GregTechCalcBoard.GITHUB_ISSUES_URL);
            return true;
        }

        if (isHovered(mouseX, mouseY, px + 2, row2Y, POPUP_W - 4, POPUP_ITEM_H)) {
            playClickSound();
            communityPopupOpen = false;
            Screen parentScreen = screen instanceof Screen s ? s : null;
            ConfirmLinkHelper.openExternalLink(parentScreen, GregTechCalcBoard.DISCORD_INVITE_URL);
            return true;
        }

        if (isHovered(mouseX, mouseY, px, py, POPUP_W, POPUP_H)) {
            return true;
        }

        if (!isHovered(mouseX, mouseY, 2, communityBtnY)) {
            communityPopupOpen = false;
        }
        return false;
    }

    public boolean isCommunityPopupOpen() {
        return communityPopupOpen;
    }

    public void closeCommunityPopup() {
        this.communityPopupOpen = false;
    }

    public void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (communityPopupOpen) return;
        if (mouseX < 0 || mouseX > BAR_WIDTH) return;

        if (isHovered(mouseX, mouseY, 2, pagesBtnY)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.activity_bar.pages"), mouseX, mouseY, screen.getScreenWidth(), screen.getScreenHeight());
            return;
        }
        if (isHovered(mouseX, mouseY, 2, favoritesBtnY)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.activity_bar.favorites"), mouseX, mouseY, screen.getScreenWidth(), screen.getScreenHeight());
            return;
        }
        if (isHovered(mouseX, mouseY, 2, blueprintsBtnY)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.activity_bar.blueprints"), mouseX, mouseY, screen.getScreenWidth(), screen.getScreenHeight());
            return;
        }
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isBoMSupported() && isHovered(mouseX, mouseY, 2, bomBtnY)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.activity_bar.bom"), mouseX, mouseY, screen.getScreenWidth(), screen.getScreenHeight());
            return;
        }
        if (isHovered(mouseX, mouseY, 2, balanceBtnY)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.activity_bar.balance"), mouseX, mouseY, screen.getScreenWidth(), screen.getScreenHeight());
            return;
        }
        if (ClientWorkspaceState.getInstance().isCollaborationEnabled() && isHovered(mouseX, mouseY, 2, teamBtnY)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.activity_bar.team"), mouseX, mouseY, screen.getScreenWidth(), screen.getScreenHeight());
            return;
        }
        if (communityBtnY > 0 && isHovered(mouseX, mouseY, 2, communityBtnY)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.activity_bar.community"), mouseX, mouseY, screen.getScreenWidth(), screen.getScreenHeight());
            return;
        }
        if (isHovered(mouseX, mouseY, 2, helpBtnY)) {
            BoardTooltipRenderer.renderTooltip(graphics, font, Component.translatable("gui.gtcalcboard.activity_bar.help"), mouseX, mouseY, screen.getScreenWidth(), screen.getScreenHeight());
            return;
        }
        if (isHovered(mouseX, mouseY, 2, settingsBtnY)) {
            Component tooltip = Component.translatable("gui.gtcalcboard.activity_bar.settings");
            if (ClientUpdateNotifier.getInstance().isBadgeVisible()) {
                tooltip = tooltip.copy().append(" ").append(Component.translatable("gui.gtcalcboard.update.badge_tooltip", ClientUpdateNotifier.getInstance().getLatestVersion()));
            }
            BoardTooltipRenderer.renderTooltip(graphics, font, tooltip, mouseX, mouseY, screen.getScreenWidth(), screen.getScreenHeight());
        }
    }

    private boolean isHovered(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private boolean isHovered(double mouseX, double mouseY, int x, int y) {
        return isHovered(mouseX, mouseY, x, y, BTN_SIZE, BTN_SIZE);
    }

    private boolean isInsideBar(double mouseY) {
        int topY = screen.getHeaderBottomY();
        int bottomY = screen.getScreenHeight() - AdaptiveStatusBar.BAR_HEIGHT;
        return mouseY >= topY && mouseY <= bottomY;
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }
}
