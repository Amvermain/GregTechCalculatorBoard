package com.gtceu.calcboard.network.packet.s2c;

import com.gtceu.calcboard.GregTechCalcBoard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C packet carrying a single page's compressed NBT payload (<= 512 KB).
 */
public record S2CSyncPageDataPacket(String pageId, int revision, byte[] compressedNBT) implements CustomPacketPayload {

    public static final Type<S2CSyncPageDataPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GregTechCalcBoard.MOD_ID, "s2c_sync_page_data"));
    public static final StreamCodec<FriendlyByteBuf, S2CSyncPageDataPacket> STREAM_CODEC = CustomPacketPayload.codec(
            S2CSyncPageDataPacket::write,
            S2CSyncPageDataPacket::new
    );

    public S2CSyncPageDataPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(256), buf.readVarInt(), buf.readByteArray());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(pageId != null ? pageId : "default");
        buf.writeVarInt(revision);
        buf.writeByteArray(compressedNBT != null ? compressedNBT : new byte[0]);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CSyncPageDataPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketHandler.handleSyncPageData(packet));
    }
}
