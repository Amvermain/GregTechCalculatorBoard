package com.gtceu.calcboard.network.packet.c2s;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CLockResultPacket;
import com.gtceu.calcboard.server.storage.WorkspaceLockManager;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record C2SAcquireLockPacket(UUID teamId, String pageId) implements CustomPacketPayload {

    public static final Type<C2SAcquireLockPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GregTechCalcBoard.MOD_ID, "c2s_acquire_lock"));
    public static final StreamCodec<FriendlyByteBuf, C2SAcquireLockPacket> STREAM_CODEC = CustomPacketPayload.codec(
            C2SAcquireLockPacket::write,
            C2SAcquireLockPacket::new
    );

    public C2SAcquireLockPacket(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readUtf(256));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(teamId != null ? teamId : new UUID(0L, 0L));
        buf.writeUtf(pageId != null ? pageId : "default");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SAcquireLockPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            UUID playerTeamId = TeamProviderRegistry.getInstance().getPlayerTeamId(player);
            if (playerTeamId == null || !TeamProviderRegistry.getInstance().canPlayerEdit(player, playerTeamId)) {
                NetworkHandler.sendToPlayer(player, new S2CLockResultPacket(packet.pageId(), false, null, "", 0L));
                return;
            }

            boolean acquired = WorkspaceLockManager.getInstance().acquireLock(playerTeamId, packet.pageId(), player.getUUID(), player.getGameProfile().getName());
            WorkspaceLockManager.LockInfo lock = WorkspaceLockManager.getInstance().getLock(playerTeamId, packet.pageId());

            UUID holderUUID = lock != null ? lock.getHolderUUID() : null;
            String holderName = lock != null ? lock.getHolderName() : "";
            long expires = lock != null ? lock.getExpiresTimestamp() : 0L;

            // Notify requester
            NetworkHandler.sendToPlayer(player, new S2CLockResultPacket(packet.pageId(), acquired, holderUUID, holderName, expires));

            // If acquired, broadcast lock state to other team members
            if (acquired) {
                NetworkHandler.broadcastToTeam(player.serverLevel(), playerTeamId, new S2CLockResultPacket(packet.pageId(), true, player.getUUID(), player.getGameProfile().getName(), expires), player.getUUID());
            }
        });
    }
}
