package com.gtceu.calcboard.network.packet.s2c;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Handles incoming S2C network packets on the physical client thread.
 */
public class ClientPacketHandler {

    public static void handleSyncWorkspace(S2CSyncWorkspacePacket packet) {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        state.setServerSupported(true);
        UUID teamId = packet.getTeamId();
        if (teamId == null || (teamId.getMostSignificantBits() == 0L && teamId.getLeastSignificantBits() == 0L)) {
            state.setCurrentTeamId(null);
            state.setCurrentTeamName("Team Workspace");
            state.updateRemotePages(java.util.Collections.emptyList());
            state.updateCommitHistory(java.util.Collections.emptyList());
        } else {
            state.setCurrentTeamId(teamId);
            state.setCurrentTeamName(packet.getTeamName());
            state.setGlobalRevision(packet.getGlobalRevision());
            state.updateRemotePages(packet.getPages());
            state.updateCommitHistory(packet.getCommits());
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BoardScreen bs) {
            bs.rebuildWidgets();
            bs.markSummaryDirty();
        }
    }

    public static void handleLockResult(S2CLockResultPacket packet) {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        Minecraft mc = Minecraft.getInstance();
        UUID myUUID = mc.player != null ? mc.player.getUUID() : null;

        boolean isMe = myUUID != null && myUUID.equals(packet.getLockHolderUUID());
        state.setLockHeld(packet.getPageId(), isMe);

        var page = state.getRemotePage(packet.getPageId());
        if (page != null) {
            if (packet.isSuccess() || packet.getLockHolderUUID() != null) {
                page.setLockHolderUUID(packet.getLockHolderUUID());
                page.setLockHolderName(packet.getLockHolderName());
                page.setLockExpiresTimestamp(packet.getExpiresTimestamp());
            } else {
                page.setLockHolderUUID(null);
                page.setLockHolderName("");
                page.setLockExpiresTimestamp(0L);
            }
        }

        if (mc.screen instanceof BoardScreen bs) {
            bs.rebuildWidgets();
            bs.markSummaryDirty();
        }

        if (!packet.isSuccess() && packet.getLockHolderName() != null && !packet.getLockHolderName().isEmpty()) {
            BoardToast.show("gui.gtcalcboard.toast.lock_busy", packet.getLockHolderName());
        }
    }

    public static void handlePresence(S2CBroadcastPresencePacket packet) {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        state.updatePresence(packet.getActiveMembers());

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BoardScreen bs) {
            bs.markSummaryDirty();
        }
    }

    public static void handleError(S2CWorkspaceErrorPacket packet) {
        BoardToast.show("gui.gtcalcboard.toast.error", Component.translatable(packet.getMessageKey()).getString());
    }

    public static void handleSyncWorkspaceMeta(S2CSyncWorkspaceMetaPacket packet) {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        state.updateWorkspaceMeta(packet);

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BoardScreen bs) {
            bs.rebuildWidgets();
            bs.markSummaryDirty();
        }
    }

    public static void handleSyncPageData(S2CSyncPageDataPacket packet) {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        state.applyPageData(packet.getPageId(), packet.getRevision(), packet.getCompressedNBT());
    }

    public static void handleChunkedData(S2CChunkedDataPacket packet) {
        byte[] complete = com.gtceu.calcboard.client.team.ChunkedPayloadAssembler.appendChunk(
                packet.getTransferId(),
                packet.getChunkIndex(),
                packet.getTotalChunks(),
                packet.getChunkBytes()
        );
        if (complete != null) {
            ClientWorkspaceState state = ClientWorkspaceState.getInstance();
            state.applyPageData(packet.getPageId(), packet.getRevision(), complete);
        }
    }

    public static void handleSyncCommitHistory(S2CSyncCommitHistoryPacket packet) {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        state.updateCommitHistory(packet.getCommits());

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BoardScreen bs) {
            bs.markSummaryDirty();
        }
    }
}

