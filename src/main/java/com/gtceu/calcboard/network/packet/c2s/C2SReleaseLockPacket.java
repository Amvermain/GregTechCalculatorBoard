package com.gtceu.calcboard.network.packet.c2s;

import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CLockResultPacket;
import com.gtceu.calcboard.server.storage.WorkspaceLockManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class C2SReleaseLockPacket {

    private final UUID teamId;
    private final String pageId;

    public C2SReleaseLockPacket(UUID teamId, String pageId) {
        this.teamId = teamId;
        this.pageId = pageId != null ? pageId : "default";
    }

    public C2SReleaseLockPacket(FriendlyByteBuf buf) {
        this.teamId = buf.readUUID();
        this.pageId = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(teamId);
        buf.writeUtf(pageId != null ? pageId : "default");
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            boolean released = WorkspaceLockManager.getInstance().releaseLock(teamId, pageId, player.getUUID());
            if (released) {
                // Broadcast unlock to all team members
                NetworkHandler.broadcastToTeam(player.serverLevel(), teamId, new S2CLockResultPacket(pageId, false, null, "", 0L), null);
            }
        });
        ctx.setPacketHandled(true);
    }
}
