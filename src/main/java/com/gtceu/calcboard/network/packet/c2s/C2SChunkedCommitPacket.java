package com.gtceu.calcboard.network.packet.c2s;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CLockResultPacket;
import com.gtceu.calcboard.network.packet.s2c.S2CWorkspaceErrorPacket;
import com.gtceu.calcboard.server.storage.*;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Set;
import java.util.UUID;

/**
 * C2S chunked commit packet allowing clients to stream large page NBT payloads in 512KB chunks.
 * Prevents Netty frame overflow (2MB limit) on large factory flow graphs.
 */
public record C2SChunkedCommitPacket(
        UUID transferId,
        UUID teamId,
        String pageId,
        String pageTitle,
        int revision,
        String commitMessage,
        int chunkIndex,
        int totalChunks,
        byte[] chunkData,
        int addedNodes,
        int modifiedNodes,
        int deletedNodes
) implements CustomPacketPayload {

    public static final Type<C2SChunkedCommitPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GregTechCalcBoard.MOD_ID, "c2s_chunked_commit"));
    public static final StreamCodec<FriendlyByteBuf, C2SChunkedCommitPacket> STREAM_CODEC = CustomPacketPayload.codec(
            C2SChunkedCommitPacket::write,
            C2SChunkedCommitPacket::new
    );

    public C2SChunkedCommitPacket(FriendlyByteBuf buf) {
        this(
                buf.readUUID(),
                buf.readUUID(),
                buf.readUtf(256),
                buf.readUtf(256),
                buf.readVarInt(),
                buf.readUtf(1024),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readByteArray(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt()
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(transferId != null ? transferId : UUID.randomUUID());
        buf.writeUUID(teamId != null ? teamId : new UUID(0L, 0L));
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SChunkedCommitPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            byte[] completeNBT = ServerChunkedPayloadAssembler.appendChunk(packet.transferId(), packet.chunkIndex(), packet.totalChunks(), packet.chunkData());
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
            if (!lockMgr.canCommit(playerTeamId, packet.pageId(), player.getUUID())) {
                NetworkHandler.sendToPlayer(player, new S2CWorkspaceErrorPacket(423, "gui.gtcalcboard.error.locked_by_other"));
                return;
            }

            TeamBoardSavedData savedData = TeamBoardSavedData.get(player.serverLevel());
            if (savedData == null) return;

            String teamName = TeamProviderRegistry.getInstance().getTeamDisplayName(playerTeamId);
            TeamWorkspaceData ws = savedData.getOrCreateWorkspace(playerTeamId, teamName);

            TeamWorkspacePage page = ws.getPage(packet.pageId());

            // 2. Optimistic concurrency control / revision conflict check (RFC-003)
            if (page != null && page.getPageRevision() != packet.revision()) {
                NetworkHandler.sendToPlayer(player, new S2CWorkspaceErrorPacket(409, "gui.gtcalcboard.error.revision_conflict"));
                return;
            }

            // 3. Update or create page
            int nextRev = (page == null) ? 1 : page.getPageRevision() + 1;
            if (page == null) {
                page = new TeamWorkspacePage(packet.pageId(), packet.pageTitle(), nextRev, completeNBT);
            } else {
                page.setTitle(packet.pageTitle());
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
                    packet.pageId(),
                    packet.commitMessage(),
                    packet.addedNodes(),
                    packet.modifiedNodes(),
                    packet.deletedNodes()
            );
            ws.addCommit(commit);

            // 5. Release lock after committing
            lockMgr.releaseLock(playerTeamId, packet.pageId(), player.getUUID());

            // 6. Save world data
            savedData.setDirty();

            // 7. Broadcast lightweight meta packet and released lock to all team members
            NetworkHandler.broadcastToTeam(player.serverLevel(), playerTeamId, ws.buildMetaPacket(), null);
            NetworkHandler.broadcastToTeam(player.serverLevel(), playerTeamId, new S2CLockResultPacket(packet.pageId(), false, null, "", 0L), null);

            // 8. Targeted Streaming (RFC-003): Stream the committed page data only to active team members currently viewing this page
            Set<UUID> activePageHolders = TeamPresenceTracker.getInstance().getActiveViewersForPage(playerTeamId, packet.pageId());
            for (ServerPlayer member : player.serverLevel().getServer().getPlayerList().getPlayers()) {
                if (playerTeamId.equals(TeamProviderRegistry.getInstance().getPlayerTeamId(member))) {
                    if (activePageHolders.contains(member.getUUID())) {
                        ChunkedStreamHelper.sendPageDataSafely(member, packet.pageId(), nextRev, completeNBT);
                    }
                }
            }
        });
    }
}
