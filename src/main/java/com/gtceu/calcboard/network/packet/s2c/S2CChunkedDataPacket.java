package com.gtceu.calcboard.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * S2C chunked packet for streaming large payloads (> 512 KB) safely across the Netty pipeline.
 */
public class S2CChunkedDataPacket {

    private final UUID transferId;
    private final String pageId;
    private final int revision;
    private final int chunkIndex;
    private final int totalChunks;
    private final byte[] chunkBytes;

    public S2CChunkedDataPacket(UUID transferId, String pageId, int revision, int chunkIndex, int totalChunks, byte[] chunkBytes) {
        this.transferId = transferId != null ? transferId : UUID.randomUUID();
        this.pageId = pageId != null ? pageId : "default";
        this.revision = revision;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.chunkBytes = chunkBytes != null ? chunkBytes : new byte[0];
    }

    public S2CChunkedDataPacket(FriendlyByteBuf buf) {
        this.transferId = buf.readUUID();
        this.pageId = buf.readUtf(256);
        this.revision = buf.readVarInt();
        this.chunkIndex = buf.readVarInt();
        this.totalChunks = buf.readVarInt();
        this.chunkBytes = buf.readByteArray();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(transferId);
        buf.writeUtf(pageId);
        buf.writeVarInt(revision);
        buf.writeVarInt(chunkIndex);
        buf.writeVarInt(totalChunks);
        buf.writeByteArray(chunkBytes);
    }

    public UUID getTransferId() {
        return transferId;
    }

    public String getPageId() {
        return pageId;
    }

    public int getRevision() {
        return revision;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public byte[] getChunkBytes() {
        return chunkBytes;
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleChunkedData(this)));
        ctx.setPacketHandled(true);
    }
}
