package com.gtceu.calcboard.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class S2CLockResultPacket {

    private final String pageId;
    private final boolean success;
    private final UUID lockHolderUUID;
    private final String lockHolderName;
    private final long expiresTimestamp;

    public S2CLockResultPacket(String pageId, boolean success, UUID lockHolderUUID, String lockHolderName, long expiresTimestamp) {
        this.pageId = pageId != null ? pageId : "default";
        this.success = success;
        this.lockHolderUUID = lockHolderUUID;
        this.lockHolderName = lockHolderName != null ? lockHolderName : "";
        this.expiresTimestamp = expiresTimestamp;
    }

    public S2CLockResultPacket(FriendlyByteBuf buf) {
        this.pageId = buf.readUtf(256);
        this.success = buf.readBoolean();
        boolean hasHolder = buf.readBoolean();
        this.lockHolderUUID = hasHolder ? buf.readUUID() : null;
        this.lockHolderName = buf.readUtf(256);
        this.expiresTimestamp = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
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

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleLockResult(this)));
        ctx.setPacketHandled(true);
    }
}
