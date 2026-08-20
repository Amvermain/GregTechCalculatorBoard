package com.gtceu.calcboard.network.packet.c2s;

import com.gtceu.calcboard.server.storage.WorkspaceLockManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class C2SPingPresencePacket {

    private final UUID teamId;
    private final String activePageId;

    public C2SPingPresencePacket(UUID teamId, String activePageId) {
        this.teamId = teamId;
        this.activePageId = activePageId != null ? activePageId : "default";
    }

    public C2SPingPresencePacket(FriendlyByteBuf buf) {
        this.teamId = buf.readUUID();
        this.activePageId = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(teamId);
        buf.writeUtf(activePageId != null ? activePageId : "default");
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            // Renew heartbeat if player holds the lock
            WorkspaceLockManager.getInstance().pingHeartbeat(teamId, activePageId, player.getUUID());
        });
        ctx.setPacketHandled(true);
    }
}
