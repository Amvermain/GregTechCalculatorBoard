package com.gtceu.calcboard.network.packet.c2s;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.server.storage.TeamPresenceTracker;
import com.gtceu.calcboard.server.storage.WorkspaceLockManager;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record C2SPingPresencePacket(UUID teamId, String activePageId, boolean isOpen) implements CustomPacketPayload {

    public static final Type<C2SPingPresencePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GregTechCalcBoard.MOD_ID, "c2s_ping_presence"));
    public static final StreamCodec<FriendlyByteBuf, C2SPingPresencePacket> STREAM_CODEC = CustomPacketPayload.codec(
            C2SPingPresencePacket::write,
            C2SPingPresencePacket::new
    );

    public C2SPingPresencePacket(UUID teamId, String activePageId) {
        this(teamId, activePageId, true);
    }

    public C2SPingPresencePacket(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readUtf(256), buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(teamId != null ? teamId : new UUID(0L, 0L));
        buf.writeUtf(activePageId != null ? activePageId : "default");
        buf.writeBoolean(isOpen);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SPingPresencePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            UUID playerTeamId = TeamProviderRegistry.getInstance().getPlayerTeamId(player);
            if (playerTeamId == null) return;

            if (packet.isOpen()) {
                // Renew heartbeat if player holds the lock
                WorkspaceLockManager.getInstance().pingHeartbeat(playerTeamId, packet.activePageId(), player.getUUID());
            }

            // Update presence in TeamPresenceTracker
            TeamPresenceTracker.getInstance().updatePresence(player, playerTeamId, packet.activePageId(), packet.isOpen());
        });
    }
}
