package com.gtceu.calcboard.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C packet carrying a single page's compressed NBT payload (<= 512 KB).
 */
public class S2CSyncPageDataPacket {

    private final String pageId;
    private final int revision;
    private final byte[] compressedNBT;

    public S2CSyncPageDataPacket(String pageId, int revision, byte[] compressedNBT) {
        this.pageId = pageId != null ? pageId : "default";
        this.revision = revision;
        this.compressedNBT = compressedNBT != null ? compressedNBT : new byte[0];
    }

    public S2CSyncPageDataPacket(FriendlyByteBuf buf) {
        this.pageId = buf.readUtf(256);
        this.revision = buf.readVarInt();
        this.compressedNBT = buf.readByteArray();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(pageId);
        buf.writeVarInt(revision);
        buf.writeByteArray(compressedNBT);
    }

    public String getPageId() {
        return pageId;
    }

    public int getRevision() {
        return revision;
    }

    public byte[] getCompressedNBT() {
        return compressedNBT;
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncPageData(this)));
        ctx.setPacketHandled(true);
    }
}
