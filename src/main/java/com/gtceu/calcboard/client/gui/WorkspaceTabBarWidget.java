package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.c2s.C2SRequestWorkspacePacket;
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * Top-level workspace tab bar widget for switching between [👤 Personal Board] and [👥 Team Board].
 * Also renders live team presence and page lock status badges.
 */
public class WorkspaceTabBarWidget {

    public static final int BAR_HEIGHT = 20;
    private final BoardScreen screen;

    public WorkspaceTabBarWidget(BoardScreen screen) {
        this.screen = screen;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int barY = 2;
        int curX = 6;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 350.0f);

        // 1. [👤 Personal Board] Tab
        boolean isLocal = !state.isTeamMode();
        String personalTxt = "👤 " + Component.translatable("gui.gtcalcboard.workspace.personal").getString();
        int persW = font.width(personalTxt) + 14;
        boolean persHover = mouseX >= curX && mouseX <= curX + persW && mouseY >= barY && mouseY <= barY + BAR_HEIGHT - 2;

        graphics.fill(curX, barY, curX + persW, barY + BAR_HEIGHT - 2, isLocal ? 0xFF2A364C : (persHover ? 0xFF252C3B : 0xFF1B1E28));
        graphics.renderOutline(curX, barY, persW, BAR_HEIGHT - 2, isLocal ? 0xFF58D3FF : 0xFF353C4D);
        graphics.drawString(font, personalTxt, curX + 7, barY + 5, isLocal ? 0xFF58D3FF : 0xFF9CA5B8, false);

        curX += persW + 4;

        // 2. [👥 Team Board] Tab
        boolean isTeam = state.isTeamMode();
        String teamName = state.getCurrentTeamName();
        String teamTxt = "👥 " + Component.translatable("gui.gtcalcboard.workspace.team", teamName).getString();
        int teamW = font.width(teamTxt) + 14;
        boolean teamHover = mouseX >= curX && mouseX <= curX + teamW && mouseY >= barY && mouseY <= barY + BAR_HEIGHT - 2;

        graphics.fill(curX, barY, curX + teamW, barY + BAR_HEIGHT - 2, isTeam ? 0xFF1C4232 : (teamHover ? 0xFF22352B : 0xFF1B1E28));
        graphics.renderOutline(curX, barY, teamW, BAR_HEIGHT - 2, isTeam ? 0xFF55FF88 : 0xFF353C4D);
        graphics.drawString(font, teamTxt, curX + 7, barY + 5, isTeam ? 0xFF55FF88 : 0xFF9CA5B8, false);

        // 3. Right-side Status Badges (Team mode only)
        if (isTeam) {
            int rightX = screen.width - 6;

            // Lock Status Badge
            String activePageId = "page_main";
            var activePage = state.getRemotePage(activePageId);
            boolean isLockedByMe = state.doesHoldLock(activePageId);
            boolean isLockedByOther = activePage != null && activePage.isLocked() && !isLockedByMe;

            String lockBadge;
            int lockCol;
            int lockBg;
            if (isLockedByMe) {
                lockBadge = "✏️ " + Component.translatable("gui.gtcalcboard.lock.editing_by_me").getString();
                lockCol = 0xFFFFAA00;
                lockBg = 0xFF423518;
            } else if (isLockedByOther) {
                String holder = activePage.getLockHolderUUID() != null ? activePage.getLockHolderUUID().toString().substring(0, 6) : "Player";
                lockBadge = "🔒 " + Component.translatable("gui.gtcalcboard.lock.locked_by", holder).getString();
                lockCol = 0xFFFF6B6B;
                lockBg = 0xFF421C1C;
            } else {
                lockBadge = "🟢 " + Component.translatable("gui.gtcalcboard.lock.editable").getString();
                lockCol = 0xFF55FF88;
                lockBg = 0xFF1C3A24;
            }

            int lockW = font.width(lockBadge) + 10;
            rightX -= lockW;
            graphics.fill(rightX, barY, rightX + lockW, barY + BAR_HEIGHT - 2, lockBg);
            graphics.renderOutline(rightX, barY, lockW, BAR_HEIGHT - 2, lockCol);
            graphics.drawString(font, lockBadge, rightX + 5, barY + 5, lockCol, false);

            // Online Members Presence Badge
            int onlineCount = Math.max(1, state.getActivePresence().size());
            String onlineTxt = "● " + Component.translatable("gui.gtcalcboard.presence.online_count", String.valueOf(onlineCount)).getString();
            int onlineW = font.width(onlineTxt) + 10;
            rightX -= (onlineW + 4);

            graphics.fill(rightX, barY, rightX + onlineW, barY + BAR_HEIGHT - 2, 0xFF1E2838);
            graphics.renderOutline(rightX, barY, onlineW, BAR_HEIGHT - 2, 0xFF3D5577);
            graphics.drawString(font, onlineTxt, rightX + 5, barY + 5, 0xFF66DDFF, false);
        }

        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int barY = 2;
        int curX = 6;

        // 1. [👤 Personal Board] Tab Click
        String personalTxt = "👤 " + Component.translatable("gui.gtcalcboard.workspace.personal").getString();
        int persW = font.width(personalTxt) + 14;
        if (mouseX >= curX && mouseX <= curX + persW && mouseY >= barY && mouseY <= barY + BAR_HEIGHT - 2) {
            if (state.isTeamMode()) {
                state.setCurrentMode(ClientWorkspaceState.WorkspaceMode.LOCAL);
                screen.rebuildWidgets();
                screen.markSummaryDirty();
                BoardToast.show("gui.gtcalcboard.toast.switched_to_personal");
            }
            return true;
        }

        curX += persW + 4;

        // 2. [👥 Team Board] Tab Click
        String teamName = state.getCurrentTeamName();
        String teamTxt = "👥 " + Component.translatable("gui.gtcalcboard.workspace.team", teamName).getString();
        int teamW = font.width(teamTxt) + 14;
        if (mouseX >= curX && mouseX <= curX + teamW && mouseY >= barY && mouseY <= barY + BAR_HEIGHT - 2) {
            if (!state.isTeamMode()) {
                state.setCurrentMode(ClientWorkspaceState.WorkspaceMode.TEAM);
                // Request fresh workspace from server
                UUID teamId = state.getCurrentTeamId() != null ? state.getCurrentTeamId() : (mc.player != null ? mc.player.getUUID() : UUID.randomUUID());
                NetworkHandler.sendToServer(new C2SRequestWorkspacePacket(teamId, "page_main"));
                screen.rebuildWidgets();
                screen.markSummaryDirty();
                BoardToast.show("gui.gtcalcboard.toast.switched_to_team", teamName);
            }
            return true;
        }

        return false;
    }
}
