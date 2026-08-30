package com.gtceu.calcboard.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Lightweight S2C metadata packet containing workspace summary and page tab headers (< 5 KB).
 */
public class S2CSyncWorkspaceMetaPacket {

    public static class PageMeta {
        private final String pageId;
        private final String title;
        private final int revision;
        private final UUID lockHolderUUID;
        private final String lockHolderName;
        private final long lockExpiresTimestamp;

        public PageMeta(String pageId, String title, int revision, UUID lockHolderUUID, String lockHolderName, long lockExpiresTimestamp) {
            this.pageId = pageId != null ? pageId : "default";
            this.title = title != null ? title : "Page";
            this.revision = revision;
            this.lockHolderUUID = lockHolderUUID;
            this.lockHolderName = lockHolderName != null ? lockHolderName : "";
            this.lockExpiresTimestamp = lockExpiresTimestamp;
        }

        public PageMeta(FriendlyByteBuf buf) {
            this.pageId = buf.readUtf(256);
            this.title = buf.readUtf(256);
            this.revision = buf.readVarInt();
            this.lockHolderUUID = buf.readBoolean() ? buf.readUUID() : null;
            this.lockHolderName = buf.readUtf(256);
            this.lockExpiresTimestamp = buf.readLong();
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(pageId);
            buf.writeUtf(title);
            buf.writeVarInt(revision);
            buf.writeBoolean(lockHolderUUID != null);
            if (lockHolderUUID != null) {
                buf.writeUUID(lockHolderUUID);
            }
            buf.writeUtf(lockHolderName != null ? lockHolderName : "");
            buf.writeLong(lockExpiresTimestamp);
        }

        public String getPageId() {
            return pageId;
        }

        public String getTitle() {
            return title;
        }

        public int getRevision() {
            return revision;
        }

        public UUID getLockHolderUUID() {
            return lockHolderUUID;
        }

        public String getLockHolderName() {
            return lockHolderName;
        }

        public long getLockExpiresTimestamp() {
            return lockExpiresTimestamp;
        }
    }

    private final UUID teamId;
    private final String teamName;
    private final int globalRevision;
    private final List<PageMeta> pages;

    public S2CSyncWorkspaceMetaPacket(UUID teamId, String teamName, int globalRevision, List<PageMeta> pages) {
        this.teamId = teamId != null ? teamId : new UUID(0L, 0L);
        this.teamName = teamName != null ? teamName : "Team Workspace";
        this.globalRevision = globalRevision;
        this.pages = pages != null ? pages : new ArrayList<>();
    }

    public S2CSyncWorkspaceMetaPacket(FriendlyByteBuf buf) {
        this.teamId = buf.readUUID();
        this.teamName = buf.readUtf(256);
        this.globalRevision = buf.readVarInt();
        int count = buf.readVarInt();
        this.pages = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.pages.add(new PageMeta(buf));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(teamId);
        buf.writeUtf(teamName);
        buf.writeVarInt(globalRevision);
        buf.writeVarInt(pages.size());
        for (PageMeta pm : pages) {
            pm.encode(buf);
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

    public List<PageMeta> getPages() {
        return pages;
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncWorkspaceMeta(this)));
        ctx.setPacketHandled(true);
    }
}
