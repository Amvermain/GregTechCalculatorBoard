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

public record C2SReleaseLockPacket(UUID teamId, String pageId) implements CustomPacketPayload {

    public static final Type<C2SReleaseLockPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GregTechCalcBoard.MOD_ID, "c2s_release_lock"));
    public static final StreamCodec<FriendlyByteBuf, C2SReleaseLockPacket> STREAM_CODEC = CustomPacketPayload.codec(
            C2SReleaseLockPacket::write,
            C2SReleaseLockPacket::new
    );

    public C2SReleaseLockPacket(FriendlyByteBuf buf) {
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

    public static void handle(C2SReleaseLockPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            UUID playerTeamId = TeamProviderRegistry.getInstance().getPlayerTeamId(player);
            if (playerTeamId == null) return;

            boolean released = WorkspaceLockManager.getInstance().releaseLock(playerTeamId, packet.pageId(), player.getUUID());
            if (released) {
                // Broadcast unlock to all team members
                NetworkHandler.broadcastToTeam(player.serverLevel(), playerTeamId, new S2CLockResultPacket(packet.pageId(), false, null, "", 0L), null);
            }
        });
    }
}
