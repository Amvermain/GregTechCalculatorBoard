package com.gtceu.calcboard.network.packet.s2c;

import com.gtceu.calcboard.GregTechCalcBoard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record S2CWorkspaceErrorPacket(int errorCode, String messageKey) implements CustomPacketPayload {

    public static final Type<S2CWorkspaceErrorPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GregTechCalcBoard.MOD_ID, "s2c_workspace_error"));
    public static final StreamCodec<FriendlyByteBuf, S2CWorkspaceErrorPacket> STREAM_CODEC = CustomPacketPayload.codec(
            S2CWorkspaceErrorPacket::write,
            S2CWorkspaceErrorPacket::new
    );

    public S2CWorkspaceErrorPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(256));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(errorCode);
        buf.writeUtf(messageKey != null ? messageKey : "gui.gtcalcboard.error.generic");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CWorkspaceErrorPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketHandler.handleError(packet));
    }
}
