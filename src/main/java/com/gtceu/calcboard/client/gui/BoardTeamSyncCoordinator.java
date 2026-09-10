package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.c2s.C2SAcquireLockPacket;
import com.gtceu.calcboard.network.packet.c2s.C2SPingPresencePacket;
import com.gtceu.calcboard.network.packet.c2s.C2SRequestWorkspacePacket;
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.UUID;

/**
 * Coordinates multiplayer team workspace presence, edit lock acquisition, and periodic auto-commit.
 */
public class BoardTeamSyncCoordinator {
    private final BoardScreen screen;
    private long lastEditTimestamp = 0;
    private int presencePingTicks = 0;

    public BoardTeamSyncCoordinator(BoardScreen screen) {
        this.screen = screen;
    }

    public void initNetworkPresence() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getConnection() == null) return;
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        UUID teamId = state.getCurrentTeamId();
        String pageId = state.getActiveTeamPageId();
        if (state.isCollaborationEnabled()) {
            NetworkHandler.sendToServer(new C2SPingPresencePacket(teamId, pageId, state.isTeamMode()));
        }
        NetworkHandler.sendToServer(new C2SRequestWorkspacePacket(teamId, pageId != null ? pageId : "page_main"));
    }

    public void tick() {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        if (state.isCollaborationEnabled() && state.isTeamMode()) {
            presencePingTicks++;
            if (presencePingTicks >= 40) {
                presencePingTicks = 0;
                NetworkHandler.sendToServer(new C2SPingPresencePacket(state.getCurrentTeamId(), state.getActiveTeamPageId(), true));
            }
        }
        if (state.isTeamMode()) {
            String activePageId = state.getActiveTeamPageId();
            if (state.isPageDirty(activePageId) && lastEditTimestamp > 0 && (System.currentTimeMillis() - lastEditTimestamp > 3000)) {
                state.autoCommitAndRelease(screen, activePageId);
                screen.rebuildWidgets();
                screen.markSummaryDirty();
            }
        }
    }

    public void markTeamDirty() {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        if (state.isTeamMode()) {
            state.markPageDirty(state.getActiveTeamPageId());
            this.lastEditTimestamp = System.currentTimeMillis();
        }
    }

    public boolean ensureEditPermission() {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        if (!state.isTeamMode()) return true;

        String activePageId = state.getActiveTeamPageId();
        state.markPageDirty(activePageId);
        this.lastEditTimestamp = System.currentTimeMillis();

        if (state.doesHoldLock(activePageId)) return true;

        TeamWorkspacePage page = state.getRemotePage(activePageId);
        if (page != null && page.isLocked() && !state.doesHoldLock(activePageId)) {
            String lockHolder = page.getLockHolderName() != null && !page.getLockHolderName().isEmpty()
                    ? page.getLockHolderName() : state.resolvePlayerName(page.getLockHolderUUID());
            BoardToast.show(Component.literal("§c✕ ").append(Component.translatable("gui.gtcalcboard.lock.locked_by", lockHolder)));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
            return false;
        }

        UUID teamId = state.getCurrentTeamId();
        NetworkHandler.sendToServer(new C2SAcquireLockPacket(teamId, activePageId));
        state.setLockHeld(activePageId, true);
        return true;
    }

    public void onScreenRemoved() {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        if (state.isTeamMode()) {
            state.autoCommitAndRelease(screen, state.getActiveTeamPageId());
            state.releaseCurrentLockIfHeld();
        }
    }

    public void onScreenClosed() {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        if (state.isCollaborationEnabled() && state.isTeamMode()) {
            NetworkHandler.sendToServer(new C2SPingPresencePacket(state.getCurrentTeamId(), state.getActiveTeamPageId(), false));
        }
        if (state.isTeamMode()) {
            state.autoCommitAndRelease(screen, state.getActiveTeamPageId());
        }
    }
}
