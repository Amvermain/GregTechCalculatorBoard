package com.gtceu.calcboard.network.packet.s2c;

import com.gtceu.calcboard.GregTechCalcBoard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record S2CLockResultPacket(
        String pageId,
        boolean success,
        UUID lockHolderUUID,
        String lockHolderName,
        long expiresTimestamp
) implements CustomPacketPayload {

    public static final Type<S2CLockResultPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GregTechCalcBoard.MOD_ID, "s2c_lock_result"));
    public static final StreamCodec<FriendlyByteBuf, S2CLockResultPacket> STREAM_CODEC = CustomPacketPayload.codec(
            S2CLockResultPacket::write,
            S2CLockResultPacket::new
    );

    public S2CLockResultPacket(FriendlyByteBuf buf) {
        this(
                buf.readUtf(256),
                buf.readBoolean(),
                buf.readBoolean() ? buf.readUUID() : null,
                buf.readUtf(256),
                buf.readLong()
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(pageId != null ? pageId : "default");
        buf.writeBoolean(success);
        boolean hasHolder = lockHolderUUID != null;
        buf.writeBoolean(hasHolder);
        if (hasHolder) {
            buf.writeUUID(lockHolderUUID);
        }
        buf.writeUtf(lockHolderName != null ? lockHolderName : "");
        buf.writeLong(expiresTimestamp);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public String getPageId() {
        return pageId;
    }

    public boolean isSuccess() {
        return success;
    }

    public UUID getLockHolderUUID() {
        return lockHolderUUID;
    }

    public String getLockHolderName() {
        return lockHolderName;
    }

    public long getExpiresTimestamp() {
        return expiresTimestamp;
    }

    public static void handle(S2CLockResultPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketHandler.handleLockResult(packet));
    }
}
