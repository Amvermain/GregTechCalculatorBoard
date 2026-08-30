package com.gtceu.calcboard.network.packet.s2c;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.server.storage.CommitLogEntry;
import com.gtceu.calcboard.server.storage.TeamWorkspaceData;
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record S2CSyncWorkspacePacket(
        UUID teamId,
        String teamName,
        int globalRevision,
        List<TeamWorkspacePage> pages,
        List<CommitLogEntry> commits
) implements CustomPacketPayload {

    public static final Type<S2CSyncWorkspacePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GregTechCalcBoard.MOD_ID, "s2c_sync_workspace"));
    public static final StreamCodec<FriendlyByteBuf, S2CSyncWorkspacePacket> STREAM_CODEC = CustomPacketPayload.codec(
            S2CSyncWorkspacePacket::write,
            S2CSyncWorkspacePacket::new
    );

    public S2CSyncWorkspacePacket(TeamWorkspaceData workspace) {
        this(
                workspace.getTeamId(),
                workspace.getTeamName(),
                workspace.getGlobalRevision(),
                new ArrayList<>(workspace.getPages()),
                new ArrayList<>(workspace.getCommitHistory())
        );
    }

    public S2CSyncWorkspacePacket(FriendlyByteBuf buf) {
        this(
                buf.readUUID(),
                buf.readUtf(256),
                buf.readVarInt(),
                readPagesList(buf),
                readCommitsList(buf)
        );
    }

    private static List<TeamWorkspacePage> readPagesList(FriendlyByteBuf buf) {
        int pageCount = buf.readVarInt();
        List<TeamWorkspacePage> pages = new ArrayList<>(pageCount);
        for (int i = 0; i < pageCount; i++) {
            String pageId = buf.readUtf(256);
            String pageTitle = buf.readUtf(256);
            int revision = buf.readVarInt();
            boolean hasLock = buf.readBoolean();
            UUID lockHolder = hasLock ? buf.readUUID() : null;
            String lockHolderName = hasLock ? buf.readUtf(256) : "";
            long lockExpires = hasLock ? buf.readLong() : 0L;
            byte[] data = buf.readByteArray();

            TeamWorkspacePage p = new TeamWorkspacePage(pageId, pageTitle, revision, data);
            p.setLockHolderUUID(lockHolder);
            p.setLockHolderName(lockHolderName);
            p.setLockExpiresTimestamp(lockExpires);
            pages.add(p);
        }
        return pages;
    }

    private static List<CommitLogEntry> readCommitsList(FriendlyByteBuf buf) {
        int commitCount = buf.readVarInt();
        List<CommitLogEntry> commits = new ArrayList<>(commitCount);
        for (int i = 0; i < commitCount; i++) {
            int rev = buf.readVarInt();
            boolean hasAuthor = buf.readBoolean();
            UUID author = hasAuthor ? buf.readUUID() : null;
            String authorName = buf.readUtf(256);
            long ts = buf.readLong();
            String pageId = buf.readUtf(256);
            String msg = buf.readUtf(1024);
            int add = buf.readVarInt();
            int mod = buf.readVarInt();
            int del = buf.readVarInt();
            commits.add(new CommitLogEntry(rev, author, authorName, ts, pageId, msg, add, mod, del));
        }
        return commits;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(teamId != null ? teamId : new UUID(0L, 0L));
        buf.writeUtf(teamName != null ? teamName : "Team Workspace");
        buf.writeVarInt(globalRevision);

        List<TeamWorkspacePage> pageList = pages != null ? pages : List.of();
        buf.writeVarInt(pageList.size());
        for (TeamWorkspacePage p : pageList) {
            buf.writeUtf(p.getPageId());
            buf.writeUtf(p.getTitle() != null ? p.getTitle() : "Page");
            buf.writeVarInt(p.getPageRevision());
            boolean isLocked = p.getLockHolderUUID() != null;
            buf.writeBoolean(isLocked);
            if (isLocked) {
                buf.writeUUID(p.getLockHolderUUID());
                buf.writeUtf(p.getLockHolderName() != null ? p.getLockHolderName() : "");
                buf.writeLong(p.getLockExpiresTimestamp());
            }
            byte[] data = p.getCompressedGraphData();
            buf.writeByteArray(data != null ? data : new byte[0]);
        }

        List<CommitLogEntry> commitList = commits != null ? commits : List.of();
        buf.writeVarInt(commitList.size());
        for (CommitLogEntry c : commitList) {
            buf.writeVarInt(c.getRevision());
            boolean hasAuthor = c.getAuthorUUID() != null;
            buf.writeBoolean(hasAuthor);
            if (hasAuthor) {
                buf.writeUUID(c.getAuthorUUID());
            }
            buf.writeUtf(c.getAuthorName() != null ? c.getAuthorName() : "Unknown");
            buf.writeLong(c.getTimestamp());
            buf.writeUtf(c.getPageId() != null ? c.getPageId() : "default");
            buf.writeUtf(c.getMessage() != null ? c.getMessage() : "");
            buf.writeVarInt(c.getAddedNodes());
            buf.writeVarInt(c.getModifiedNodes());
            buf.writeVarInt(c.getDeletedNodes());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public int getGlobalRevision() {
        return globalRevision;
    }

    public List<TeamWorkspacePage> getPages() {
        return pages;
    }

    public List<CommitLogEntry> getCommits() {
        return commits;
    }

    public static void handle(S2CSyncWorkspacePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketHandler.handleSyncWorkspace(packet));
    }
}
