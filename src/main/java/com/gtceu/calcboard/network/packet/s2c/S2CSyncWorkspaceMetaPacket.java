package com.gtceu.calcboard.network.packet.s2c;

import com.gtceu.calcboard.GregTechCalcBoard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight S2C metadata packet containing workspace summary and page tab headers (< 5 KB).
 */
public record S2CSyncWorkspaceMetaPacket(UUID teamId, String teamName, int globalRevision, List<PageMeta> pages) implements CustomPacketPayload {

    public static final Type<S2CSyncWorkspaceMetaPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GregTechCalcBoard.MOD_ID, "s2c_sync_workspace_meta"));
    public static final StreamCodec<FriendlyByteBuf, S2CSyncWorkspaceMetaPacket> STREAM_CODEC = CustomPacketPayload.codec(
            S2CSyncWorkspaceMetaPacket::write,
            S2CSyncWorkspaceMetaPacket::new
    );

    public record PageMeta(String pageId, String title, int revision, UUID lockHolderUUID, String lockHolderName, long lockExpiresTimestamp) {
        public PageMeta(String pageId, String title, int revision, UUID lockHolderUUID, String lockHolderName, long lockExpiresTimestamp) {
            this.pageId = pageId != null ? pageId : "default";
            this.title = title != null ? title : "Page";
            this.revision = revision;
            this.lockHolderUUID = lockHolderUUID;
            this.lockHolderName = lockHolderName != null ? lockHolderName : "";
            this.lockExpiresTimestamp = lockExpiresTimestamp;
        }

        public PageMeta(FriendlyByteBuf buf) {
            this(
                    buf.readUtf(256),
                    buf.readUtf(256),
                    buf.readVarInt(),
                    buf.readBoolean() ? buf.readUUID() : null,
                    buf.readUtf(256),
                    buf.readLong()
            );
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

    public S2CSyncWorkspaceMetaPacket(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readUtf(256), buf.readVarInt(), readPageMetaList(buf));
    }

    private static List<PageMeta> readPageMetaList(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<PageMeta> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new PageMeta(buf));
        }
        return list;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(teamId != null ? teamId : new UUID(0L, 0L));
        buf.writeUtf(teamName != null ? teamName : "Team Workspace");
        buf.writeVarInt(globalRevision);
        List<PageMeta> pageList = pages != null ? pages : List.of();
        buf.writeVarInt(pageList.size());
        for (PageMeta pm : pageList) {
            pm.encode(buf);
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

    public List<PageMeta> getPages() {
        return pages;
    }

    public static void handle(S2CSyncWorkspaceMetaPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketHandler.handleSyncWorkspaceMeta(packet));
    }
}
