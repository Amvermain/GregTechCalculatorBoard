package com.gtceu.calcboard.network.packet.s2c;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.team.TeamWorkspacePage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.network.packet.c2s.UpdatePageSettingsPacket;

import java.util.Set;
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
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        if (packet.getErrorCode() == 409) {
            String activePageId = state.getActiveTeamPageId();
            state.clearPageDirty(activePageId);
            UUID teamId = state.getCurrentTeamId();
            if (teamId != null) {
                com.gtceu.calcboard.network.NetworkHandler.sendToServer(new com.gtceu.calcboard.network.packet.c2s.C2SRequestWorkspacePacket(teamId, activePageId));
                com.gtceu.calcboard.network.NetworkHandler.sendToServer(new com.gtceu.calcboard.network.packet.c2s.C2SRequestPageDataPacket(activePageId, 0));
            }
        }
        if (shouldRollbackSettings(packet.getErrorCode(), state)) {
            rollbackPendingSettings(state);
        }
    }

    private static boolean shouldRollbackSettings(int errorCode, ClientWorkspaceState state) {
        return (errorCode == 403 || errorCode == 423 || errorCode == 409) && state.isTeamMode();
    }

    private static void rollbackPendingSettings(ClientWorkspaceState state) {
        Set<String> pendingPages = state.getPendingSnapshotPageIds();
        if (pendingPages.isEmpty()) {
            rollbackPageSettings(state, state.getActiveTeamPageId());
            return;
        }
        for (String pageId : pendingPages) {
            rollbackPageSettings(state, pageId);
        }
    }

    private static void rollbackPageSettings(ClientWorkspaceState state, String pageId) {
        if (pageId == null) return;
        ClientWorkspaceState.PageSettingsSnapshot snapshot = state.consumePendingSnapshot(pageId);
        TeamWorkspacePage remote = state.getRemotePage(pageId);

        byte targetTierOrdinal = snapshot != null ? snapshot.voltageTierOrdinal() : (remote != null ? remote.getDefaultVoltageTierOrdinal() : -1);
        boolean targetAutoHatch = snapshot != null ? snapshot.autoEquipEnergyHatches() : (remote == null || remote.isAutoEquipEnergyHatches());

        if (remote != null) {
            remote.applySettings(targetTierOrdinal, targetAutoHatch);
        }

        for (BoardPage bp : BoardManager.getInstance().getPageManager().getPages()) {
            if (bp.getId().equals(pageId)) {
                applyVoltageTierToPage(bp, targetTierOrdinal);
                bp.setAutoEquipEnergyHatches(targetAutoHatch);
                break;
            }
        }

        refreshScreenIfActive(pageId);
    }

    public static void handleUpdatePageSettings(UpdatePageSettingsPacket packet) {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        state.clearPendingSnapshot(packet.getPageId());
        if (state.isTeamMode()) {
            TeamWorkspacePage remotePage = state.getRemotePage(packet.getPageId());
            if (remotePage != null) {
                remotePage.applySettings(
                        (byte) packet.getDefaultVoltageTierOrdinal(),
                        packet.isAutoEquipEnergyHatches()
                );
                if (packet.getExpectedRevision() > 0) {
                    remotePage.setPageRevision(packet.getExpectedRevision());
                }
            }
        }

        for (BoardPage page : BoardManager.getInstance().getPageManager().getPages()) {
            if (page.getId().equals(packet.getPageId())) {
                page.setAutoEquipEnergyHatches(packet.isAutoEquipEnergyHatches());
                applyVoltageTierToPage(page, packet.getDefaultVoltageTierOrdinal());
                break;
            }
        }

        refreshScreenIfActive(packet.getPageId());
    }

    private static void applyVoltageTierToPage(BoardPage page, int tierOrdinal) {
        if (tierOrdinal >= 0 && tierOrdinal < GTVoltageTier.values().length) {
            page.setDefaultVoltageTier(GTVoltageTier.values()[tierOrdinal]);
        } else if (tierOrdinal < 0) {
            page.setDefaultVoltageTier(null);
        }
    }

    private static void refreshScreenIfActive(String pageId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.screen instanceof BoardScreen bs) {
            bs.refreshPageSettings(pageId);
        }
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

    public static void handleOpenBoard(S2COpenBoardPacket packet) {
        com.gtceu.calcboard.client.storage.ClientPreferenceManager.getInstance().markWelcomeMessageSeen();
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> BoardScreen.openScreen(mc.screen));
    }

    public static void handleAe2CraftingEta(S2CAe2CraftingEtaPacket packet) {
        com.gtceu.calcboard.client.gui.compat.ae2.ClientCraftingEtaHolder.getInstance().setCurrentResult(packet.getResult());
    }
}

