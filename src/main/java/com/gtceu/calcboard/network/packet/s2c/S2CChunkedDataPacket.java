package com.gtceu.calcboard.network.packet.s2c;

import com.gtceu.calcboard.GregTechCalcBoard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * S2C chunked packet for streaming large payloads (> 512 KB) safely across the Netty pipeline.
 */
public record S2CChunkedDataPacket(
        UUID transferId,
        String pageId,
        int revision,
        int chunkIndex,
        int totalChunks,
        byte[] chunkBytes
) implements CustomPacketPayload {

    public static final Type<S2CChunkedDataPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GregTechCalcBoard.MOD_ID, "s2c_chunked_data"));
    public static final StreamCodec<FriendlyByteBuf, S2CChunkedDataPacket> STREAM_CODEC = CustomPacketPayload.codec(
            S2CChunkedDataPacket::write,
            S2CChunkedDataPacket::new
    );

    public S2CChunkedDataPacket(FriendlyByteBuf buf) {
        this(
                buf.readUUID(),
                buf.readUtf(256),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readByteArray()
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(transferId != null ? transferId : UUID.randomUUID());
        buf.writeUtf(pageId != null ? pageId : "default");
        buf.writeVarInt(revision);
        buf.writeVarInt(chunkIndex);
        buf.writeVarInt(totalChunks);
        buf.writeByteArray(chunkBytes != null ? chunkBytes : new byte[0]);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CChunkedDataPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketHandler.handleChunkedData(packet));
    }
}
