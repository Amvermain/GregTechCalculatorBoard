package com.gtceu.calcboard.network.packet.c2s;

import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CLockResultPacket;
import com.gtceu.calcboard.server.storage.WorkspaceLockManager;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class C2SAcquireLockPacket {

    private final UUID teamId;
    private final String pageId;

    public C2SAcquireLockPacket(UUID teamId, String pageId) {
        this.teamId = teamId != null ? teamId : new UUID(0L, 0L);
        this.pageId = pageId != null ? pageId : "default";
    }

    public C2SAcquireLockPacket(FriendlyByteBuf buf) {
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

            UUID playerTeamId = TeamProviderRegistry.getInstance().getPlayerTeamId(player);
            if (playerTeamId == null) {
                NetworkHandler.sendToPlayer(player, new S2CLockResultPacket(pageId, false, null, "", 0L));
                return;
            }

            boolean acquired = WorkspaceLockManager.getInstance().acquireLock(playerTeamId, pageId, player.getUUID(), player.getGameProfile().getName());
            WorkspaceLockManager.LockInfo lock = WorkspaceLockManager.getInstance().getLock(playerTeamId, pageId);

            UUID holderUUID = lock != null ? lock.getHolderUUID() : null;
            String holderName = lock != null ? lock.getHolderName() : "";
            long expires = lock != null ? lock.getExpiresTimestamp() : 0L;

            // Notify requester
            NetworkHandler.sendToPlayer(player, new S2CLockResultPacket(pageId, acquired, holderUUID, holderName, expires));

            // If acquired, broadcast lock state to other team members
            if (acquired) {
                NetworkHandler.broadcastToTeam(player.serverLevel(), playerTeamId, new S2CLockResultPacket(pageId, true, player.getUUID(), player.getGameProfile().getName(), expires), player.getUUID());
            }
        });
        ctx.setPacketHandled(true);
    }
}
