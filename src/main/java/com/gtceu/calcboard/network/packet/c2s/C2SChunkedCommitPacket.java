package com.gtceu.calcboard.network.packet.c2s;

import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CLockResultPacket;
import com.gtceu.calcboard.network.packet.s2c.S2CWorkspaceErrorPacket;
import com.gtceu.calcboard.server.storage.*;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * C2S chunked commit packet allowing clients to stream large page NBT payloads in 512KB chunks.
 * Prevents Netty frame overflow (2MB limit) on large factory flow graphs.
 */
public class C2SChunkedCommitPacket {

    private final UUID transferId;
    private final UUID teamId;
    private final String pageId;
    private final String pageTitle;
    private final int revision;
    private final String commitMessage;
    private final int chunkIndex;
    private final int totalChunks;
    private final byte[] chunkData;
    private final int addedNodes;
    private final int modifiedNodes;
    private final int deletedNodes;

    public C2SChunkedCommitPacket(UUID transferId, UUID teamId, String pageId, String pageTitle, int revision,
                                  String commitMessage, int chunkIndex, int totalChunks, byte[] chunkData,
                                  int addedNodes, int modifiedNodes, int deletedNodes) {
        this.transferId = transferId != null ? transferId : UUID.randomUUID();
        this.teamId = teamId != null ? teamId : new UUID(0L, 0L);
        this.pageId = pageId != null ? pageId : "default";
        this.pageTitle = pageTitle != null ? pageTitle : "Page";
        this.revision = revision;
        this.commitMessage = commitMessage != null ? commitMessage : "";
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.chunkData = chunkData != null ? chunkData : new byte[0];
        this.addedNodes = addedNodes;
        this.modifiedNodes = modifiedNodes;
        this.deletedNodes = deletedNodes;
    }

    public C2SChunkedCommitPacket(FriendlyByteBuf buf) {
        this.transferId = buf.readUUID();
        this.teamId = buf.readUUID();
        this.pageId = buf.readUtf(256);
        this.pageTitle = buf.readUtf(256);
        this.revision = buf.readVarInt();
        this.commitMessage = buf.readUtf(1024);
        this.chunkIndex = buf.readVarInt();
        this.totalChunks = buf.readVarInt();
        this.chunkData = buf.readByteArray();
        this.addedNodes = buf.readVarInt();
        this.modifiedNodes = buf.readVarInt();
        this.deletedNodes = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(transferId);
        buf.writeUUID(teamId);
        buf.writeUtf(pageId != null ? pageId : "default");
        buf.writeUtf(pageTitle != null ? pageTitle : "Page");
        buf.writeVarInt(revision);
        buf.writeUtf(commitMessage != null ? commitMessage : "");
        buf.writeVarInt(chunkIndex);
        buf.writeVarInt(totalChunks);
        buf.writeByteArray(chunkData != null ? chunkData : new byte[0]);
        buf.writeVarInt(addedNodes);
        buf.writeVarInt(modifiedNodes);
        buf.writeVarInt(deletedNodes);
    }

    public UUID getTransferId() {
        return transferId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public String getPageId() {
        return pageId;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public int getRevision() {
        return revision;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public byte[] getChunkData() {
        return chunkData;
    }

    public int getAddedNodes() {
        return addedNodes;
    }

    public int getModifiedNodes() {
        return modifiedNodes;
    }

    public int getDeletedNodes() {
        return deletedNodes;
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            byte[] completeNBT = ServerChunkedPayloadAssembler.appendChunk(transferId, chunkIndex, totalChunks, chunkData);
            if (completeNBT == null) {
                // Pending more chunks to arrive
                return;
            }

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
                page = new TeamWorkspacePage(pageId, pageTitle, nextRev, completeNBT);
            } else {
                page.setTitle(pageTitle);
                page.setPageRevision(nextRev);
                page.setCompressedGraphData(completeNBT);
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

            // 8. Targeted Streaming (RFC-003): Stream the committed page data only to active team members currently viewing this page
            Set<UUID> activePageHolders = TeamPresenceTracker.getInstance().getActiveViewersForPage(playerTeamId, pageId);
            for (ServerPlayer member : player.serverLevel().getServer().getPlayerList().getPlayers()) {
                if (playerTeamId.equals(TeamProviderRegistry.getInstance().getPlayerTeamId(member))) {
                    if (activePageHolders.contains(member.getUUID())) {
                        ChunkedStreamHelper.sendPageDataSafely(member, pageId, nextRev, completeNBT);
                    }
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
