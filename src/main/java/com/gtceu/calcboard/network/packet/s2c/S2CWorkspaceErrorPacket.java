package com.gtceu.calcboard.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CWorkspaceErrorPacket {

    private final int errorCode;
    private final String messageKey;

    public S2CWorkspaceErrorPacket(int errorCode, String messageKey) {
        this.errorCode = errorCode;
        this.messageKey = messageKey != null ? messageKey : "gui.gtcalcboard.error.generic";
    }

    public S2CWorkspaceErrorPacket(FriendlyByteBuf buf) {
        this.errorCode = buf.readVarInt();
        this.messageKey = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(errorCode);
        buf.writeUtf(messageKey != null ? messageKey : "gui.gtcalcboard.error.generic");
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleError(this)));
        ctx.setPacketHandled(true);
    }
}
