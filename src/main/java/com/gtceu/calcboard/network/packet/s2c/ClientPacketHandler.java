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
        state.setCurrentTeamId(packet.getTeamId());
        state.setCurrentTeamName(packet.getTeamName());
        state.setGlobalRevision(packet.getGlobalRevision());
        state.updateRemotePages(packet.getPages());
        state.updateCommitHistory(packet.getCommits());

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
}

