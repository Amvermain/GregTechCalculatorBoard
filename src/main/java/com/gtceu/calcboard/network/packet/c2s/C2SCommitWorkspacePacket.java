package com.gtceu.calcboard.network.packet.c2s;

import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CLockResultPacket;
import com.gtceu.calcboard.network.packet.s2c.S2CSyncWorkspacePacket;
import com.gtceu.calcboard.network.packet.s2c.S2CWorkspaceErrorPacket;
import com.gtceu.calcboard.server.storage.*;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class C2SCommitWorkspacePacket {

    private final UUID teamId;
    private final String pageId;
    private final String pageTitle;
    private final int revision;
    private final String commitMessage;
    private final byte[] compressedNBT;
    private final int addedNodes;
    private final int modifiedNodes;
    private final int deletedNodes;

    public C2SCommitWorkspacePacket(UUID teamId, String pageId, String pageTitle, int revision, String commitMessage, byte[] compressedNBT, int addedNodes, int modifiedNodes, int deletedNodes) {
        this.teamId = teamId != null ? teamId : new UUID(0L, 0L);
        this.pageId = pageId != null ? pageId : "default";
        this.pageTitle = pageTitle != null ? pageTitle : "Page";
        this.revision = revision;
        this.commitMessage = commitMessage != null ? commitMessage : "";
        this.compressedNBT = compressedNBT != null ? compressedNBT : new byte[0];
        this.addedNodes = addedNodes;
        this.modifiedNodes = modifiedNodes;
        this.deletedNodes = deletedNodes;
    }

    public C2SCommitWorkspacePacket(FriendlyByteBuf buf) {
        this.teamId = buf.readUUID();
        this.pageId = buf.readUtf(256);
        this.pageTitle = buf.readUtf(256);
        this.revision = buf.readVarInt();
        this.commitMessage = buf.readUtf(1024);
        this.compressedNBT = buf.readByteArray();
        this.addedNodes = buf.readVarInt();
        this.modifiedNodes = buf.readVarInt();
        this.deletedNodes = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(teamId);
        buf.writeUtf(pageId != null ? pageId : "default");
        buf.writeUtf(pageTitle != null ? pageTitle : "Page");
        buf.writeVarInt(revision);
        buf.writeUtf(commitMessage != null ? commitMessage : "");
        buf.writeByteArray(compressedNBT != null ? compressedNBT : new byte[0]);
        buf.writeVarInt(addedNodes);
        buf.writeVarInt(modifiedNodes);
        buf.writeVarInt(deletedNodes);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            UUID playerTeamId = TeamProviderRegistry.getInstance().getPlayerTeamId(player);
            if (playerTeamId == null || !TeamProviderRegistry.getInstance().canPlayerEdit(player, playerTeamId)) {
                NetworkHandler.sendToPlayer(player, new S2CWorkspaceErrorPacket(403, "gui.gtcalcboard.error.access_denied"));
                return;
            }

            // 1. Lock ownership verification (RFC-003)
            WorkspaceLockManager lockMgr = WorkspaceLockManager.getInstance();
            if (!lockMgr.canCommit(playerTeamId, pageId, player.getUUID())) {
                NetworkHandler.sendToPlayer(player, new S2CWorkspaceErrorPacket(423, "gui.gtcalcboard.error.locked_by_other"));
                return;
            }

            TeamBoardSavedData savedData = TeamBoardSavedData.get(player.serverLevel());
            if (savedData == null) return;

            String teamName = TeamProviderRegistry.getInstance().getTeamDisplayName(playerTeamId);
            TeamWorkspaceData ws = savedData.getOrCreateWorkspace(playerTeamId, teamName);

            TeamWorkspacePage page = ws.getPage(pageId);

            // 2. Optimistic concurrency control / revision conflict check (RFC-003)
            if (page != null && page.getPageRevision() != this.revision) {
                NetworkHandler.sendToPlayer(player, new S2CWorkspaceErrorPacket(409, "gui.gtcalcboard.error.revision_conflict"));
                return;
            }

            // 3. Update or create page
            int nextRev = (page == null) ? 1 : page.getPageRevision() + 1;
            if (page == null) {
                page = new TeamWorkspacePage(pageId, pageTitle, nextRev, compressedNBT);
            } else {
                page.setTitle(pageTitle);
                page.setPageRevision(nextRev);
                page.setCompressedGraphData(compressedNBT);
            }
            ws.addOrUpdatePage(page);

            // 4. Record commit history
            CommitLogEntry commit = new CommitLogEntry(
                    ws.getGlobalRevision(),
                    player.getUUID(),
                    player.getGameProfile().getName(),
                    System.currentTimeMillis(),
                    pageId,
                    commitMessage,
                    addedNodes,
                    modifiedNodes,
                    deletedNodes
            );
            ws.addCommit(commit);

            // 5. Release lock after committing
            lockMgr.releaseLock(playerTeamId, pageId, player.getUUID());

            // 6. Save world data
            savedData.setDirty();

            // 7. Broadcast lightweight meta packet and released lock to all team members
            NetworkHandler.broadcastToTeam(player.serverLevel(), playerTeamId, ws.buildMetaPacket(), null);
            NetworkHandler.broadcastToTeam(player.serverLevel(), playerTeamId, new S2CLockResultPacket(pageId, false, null, "", 0L), null);

            // 8. Targeted Streaming (RFC-003): Stream the committed page data only to active team members viewing this page
            java.util.Set<UUID> activePageHolders = TeamPresenceTracker.getInstance().getActiveViewersForPage(playerTeamId, pageId);
            for (ServerPlayer member : player.serverLevel().getServer().getPlayerList().getPlayers()) {
                if (playerTeamId.equals(TeamProviderRegistry.getInstance().getPlayerTeamId(member))) {
                    if (activePageHolders.contains(member.getUUID())) {
                        ChunkedStreamHelper.sendPageDataSafely(member, pageId, nextRev, compressedNBT);
                    }
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
