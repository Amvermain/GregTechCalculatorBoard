package com.gtceu.calcboard.network.packet.s2c;

import com.gtceu.calcboard.server.storage.CommitLogEntry;
import com.gtceu.calcboard.server.storage.TeamWorkspaceData;
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class S2CSyncWorkspacePacket {

    private final UUID teamId;
    private final String teamName;
    private final int globalRevision;
    private final List<TeamWorkspacePage> pages;
    private final List<CommitLogEntry> commits;

    public S2CSyncWorkspacePacket(TeamWorkspaceData workspace) {
        this.teamId = workspace.getTeamId();
        this.teamName = workspace.getTeamName();
        this.globalRevision = workspace.getGlobalRevision();
        this.pages = new ArrayList<>(workspace.getPages());
        this.commits = new ArrayList<>(workspace.getCommitHistory());
    }

    public S2CSyncWorkspacePacket(UUID teamId, String teamName, int globalRevision, List<TeamWorkspacePage> pages, List<CommitLogEntry> commits) {
        this.teamId = teamId;
        this.teamName = teamName != null ? teamName : "Team Workspace";
        this.globalRevision = globalRevision;
        this.pages = pages != null ? pages : new ArrayList<>();
        this.commits = commits != null ? commits : new ArrayList<>();
    }

    public S2CSyncWorkspacePacket(FriendlyByteBuf buf) {
        this.teamId = buf.readUUID();
        this.teamName = buf.readUtf(256);
        this.globalRevision = buf.readVarInt();

        int pageCount = buf.readVarInt();
        this.pages = new ArrayList<>(pageCount);
        for (int i = 0; i < pageCount; i++) {
            String pageId = buf.readUtf(256);
            String pageTitle = buf.readUtf(256);
            int revision = buf.readVarInt();
            boolean hasLock = buf.readBoolean();
            UUID lockHolder = hasLock ? buf.readUUID() : null;
            long lockExpires = hasLock ? buf.readLong() : 0L;
            byte[] data = buf.readByteArray();

            TeamWorkspacePage p = new TeamWorkspacePage(pageId, pageTitle, revision, data);
            p.setLockHolderUUID(lockHolder);
            p.setLockExpiresTimestamp(lockExpires);
            this.pages.add(p);
        }

        int commitCount = buf.readVarInt();
        this.commits = new ArrayList<>(commitCount);
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
            this.commits.add(new CommitLogEntry(rev, author, authorName, ts, pageId, msg, add, mod, del));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(teamId);
        buf.writeUtf(teamName != null ? teamName : "Team Workspace");
        buf.writeVarInt(globalRevision);

        buf.writeVarInt(pages.size());
        for (TeamWorkspacePage p : pages) {
            buf.writeUtf(p.getPageId());
            buf.writeUtf(p.getTitle() != null ? p.getTitle() : "Page");
            buf.writeVarInt(p.getPageRevision());
            boolean isLocked = p.getLockHolderUUID() != null;
            buf.writeBoolean(isLocked);
            if (isLocked) {
                buf.writeUUID(p.getLockHolderUUID());
                buf.writeLong(p.getLockExpiresTimestamp());
            }
            byte[] data = p.getCompressedGraphData();
            buf.writeByteArray(data != null ? data : new byte[0]);
        }

        buf.writeVarInt(commits.size());
        for (CommitLogEntry c : commits) {
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

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncWorkspace(this)));
        ctx.setPacketHandled(true);
    }
}
