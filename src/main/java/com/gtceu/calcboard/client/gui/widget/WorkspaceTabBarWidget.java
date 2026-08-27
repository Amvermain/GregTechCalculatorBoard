package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.client.gui.BoardScreen;

import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.c2s.C2SRequestWorkspacePacket;
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
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
        if (!state.isCollaborationEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int barY = 2;
        int curX = screen.getDynamicLeftMargin();

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
            String activePageId = state.getActiveTeamPageId();
            if (activePageId == null) activePageId = "page_main";
            var activePage = state.getRemotePage(activePageId);
            boolean isLockedByMe = state.doesHoldLock(activePageId);
            boolean isLockedByOther = activePage != null && activePage.isLocked() && !isLockedByMe;

            String lockBadge;
            int lockCol;
            int lockBg;
            if (isLockedByMe) {
                lockBadge = "✎ " + Component.translatable("gui.gtcalcboard.lock.editing_by_me").getString();
                lockCol = 0xFFFFAA00;
                lockBg = 0xFF423518;
            } else if (isLockedByOther) {
                String holder = activePage.getLockHolderName() != null && !activePage.getLockHolderName().isEmpty()
                        ? activePage.getLockHolderName()
                        : state.resolvePlayerName(activePage.getLockHolderUUID());
                lockBadge = "🔒 " + Component.translatable("gui.gtcalcboard.lock.locked_by", holder).getString();
                lockCol = 0xFFFF6B6B;
                lockBg = 0xFF421C1C;
            } else {
                lockBadge = "● " + Component.translatable("gui.gtcalcboard.lock.editable").getString();
                lockCol = 0xFF55FF88;
                lockBg = 0xFF1C3A24;
            }

            int lockW = font.width(lockBadge) + 10;
            rightX -= lockW;
            graphics.fill(rightX, barY, rightX + lockW, barY + BAR_HEIGHT - 2, lockBg);
            graphics.renderOutline(rightX, barY, lockW, BAR_HEIGHT - 2, lockCol);
            graphics.drawString(font, lockBadge, rightX + 5, barY + 5, lockCol, false);

            // Online Members Presence Badge (Actively viewing calculation board)
            int onlineCount = Math.max(1, state.getActivePresence().size());
            String onlineTxt = "● " + Component.translatable("gui.gtcalcboard.presence.online_count", String.valueOf(onlineCount)).getString();
            int onlineW = font.width(onlineTxt) + 10;
            rightX -= (onlineW + 4);

            boolean presenceHover = mouseX >= rightX && mouseX <= rightX + onlineW && mouseY >= barY && mouseY <= barY + BAR_HEIGHT - 2;

            graphics.fill(rightX, barY, rightX + onlineW, barY + BAR_HEIGHT - 2, presenceHover ? 0xFF2A3A52 : 0xFF1E2838);
            graphics.renderOutline(rightX, barY, onlineW, BAR_HEIGHT - 2, presenceHover ? 0xFF58D3FF : 0xFF3D5577);
            graphics.drawString(font, onlineTxt, rightX + 5, barY + 5, 0xFF66DDFF, false);
        }

        graphics.pose().popPose();
    }

    public void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        if (!state.isCollaborationEnabled() || !state.isTeamMode()) return;

        int barY = 2;
        int rightX = screen.width - 6;

        // Lock Badge Width
        String activePageId = state.getActiveTeamPageId();
        if (activePageId == null) activePageId = "page_main";
        var activePage = state.getRemotePage(activePageId);
        boolean isLockedByMe = state.doesHoldLock(activePageId);
        boolean isLockedByOther = activePage != null && activePage.isLocked() && !isLockedByMe;

        String lockBadge;
        if (isLockedByMe) {
            lockBadge = "✎ " + Component.translatable("gui.gtcalcboard.lock.editing_by_me").getString();
        } else if (isLockedByOther) {
            String holder = activePage.getLockHolderName() != null && !activePage.getLockHolderName().isEmpty()
                    ? activePage.getLockHolderName()
                    : state.resolvePlayerName(activePage.getLockHolderUUID());
            lockBadge = "🔒 " + Component.translatable("gui.gtcalcboard.lock.locked_by", holder).getString();
        } else {
            lockBadge = "● " + Component.translatable("gui.gtcalcboard.lock.editable").getString();
        }
        int lockW = font.width(lockBadge) + 10;
        rightX -= lockW;

        // Online Presence Badge
        int onlineCount = Math.max(1, state.getActivePresence().size());
        String onlineTxt = "● " + Component.translatable("gui.gtcalcboard.presence.online_count", String.valueOf(onlineCount)).getString();
        int onlineW = font.width(onlineTxt) + 10;
        rightX -= (onlineW + 4);

        boolean presenceHover = mouseX >= rightX && mouseX <= rightX + onlineW && mouseY >= barY && mouseY <= barY + BAR_HEIGHT - 2;

        if (presenceHover && !state.getActivePresence().isEmpty()) {
            List<Component> tooltipLines = new ArrayList<>();
            tooltipLines.add(Component.translatable("gui.gtcalcboard.presence.viewing_header", String.valueOf(onlineCount)));
            for (var member : state.getActivePresence()) {
                String pTitle = member.getActivePageId();
                var pObj = state.getRemotePage(pTitle);
                String pageDisplay = pObj != null ? pObj.getTitle() : pTitle;
                tooltipLines.add(Component.literal("§7- §b" + member.getPlayerName() + " §8(§f" + pageDisplay + "§8)"));
            }
            // Pass mouseY + 24 to force the tooltip to render below the top bar and never clip at the top
            graphics.renderTooltip(font, tooltipLines, java.util.Optional.empty(), mouseX, Math.max(30, mouseY + 24));
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        if (!state.isCollaborationEnabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int barY = 2;
        int curX = screen.getDynamicLeftMargin();

        // 1. [👤 Personal Board] Tab Click
        String personalTxt = "👤 " + Component.translatable("gui.gtcalcboard.workspace.personal").getString();
        int persW = font.width(personalTxt) + 14;
        if (mouseX >= curX && mouseX <= curX + persW && mouseY >= barY && mouseY <= barY + BAR_HEIGHT - 2) {
            if (state.isTeamMode()) {
                state.autoCommitAndRelease(screen, state.getActiveTeamPageId());
                state.setCurrentMode(ClientWorkspaceState.WorkspaceMode.LOCAL);
                // Notify server that player left the team board
                NetworkHandler.sendToServer(new com.gtceu.calcboard.network.packet.c2s.C2SPingPresencePacket(state.getCurrentTeamId(), state.getActiveTeamPageId(), false));
                screen.rebuildWidgets();
                screen.markSummaryDirty();
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
                String activePageId = state.getActiveTeamPageId() != null ? state.getActiveTeamPageId() : "page_main";
                NetworkHandler.sendToServer(new C2SRequestWorkspacePacket(teamId, activePageId));
                NetworkHandler.sendToServer(new com.gtceu.calcboard.network.packet.c2s.C2SPingPresencePacket(teamId, activePageId, true));
                screen.rebuildWidgets();
                screen.markSummaryDirty();
            }
            return true;
        }

        return false;
    }
}


