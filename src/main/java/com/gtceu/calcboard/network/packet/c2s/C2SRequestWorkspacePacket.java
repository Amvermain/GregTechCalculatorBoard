package com.gtceu.calcboard.network.packet.c2s;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CSyncWorkspacePacket;
import com.gtceu.calcboard.server.storage.TeamBoardSavedData;
import com.gtceu.calcboard.server.storage.TeamWorkspaceData;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record C2SRequestWorkspacePacket(UUID teamId, String requestedPageId) implements CustomPacketPayload {

    public static final Type<C2SRequestWorkspacePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GregTechCalcBoard.MOD_ID, "c2s_request_workspace"));
    public static final StreamCodec<FriendlyByteBuf, C2SRequestWorkspacePacket> STREAM_CODEC = CustomPacketPayload.codec(
            C2SRequestWorkspacePacket::write,
            C2SRequestWorkspacePacket::new
    );

    public C2SRequestWorkspacePacket(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readUtf(256));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(teamId != null ? teamId : new UUID(0L, 0L));
        buf.writeUtf(requestedPageId != null ? requestedPageId : "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SRequestWorkspacePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            UUID playerTeamId = TeamProviderRegistry.getInstance().getPlayerTeamId(player);
            if (playerTeamId == null) {
                NetworkHandler.sendToPlayer(player, new S2CSyncWorkspacePacket(new UUID(0L, 0L), "", 0, java.util.Collections.emptyList(), java.util.Collections.emptyList()));
                return;
            }

            TeamBoardSavedData savedData = TeamBoardSavedData.get(player.serverLevel());
            if (savedData != null) {
                String teamName = TeamProviderRegistry.getInstance().getTeamDisplayName(playerTeamId);
                TeamWorkspaceData ws = savedData.getOrCreateWorkspace(playerTeamId, teamName);
                NetworkHandler.sendToPlayer(player, new S2CSyncWorkspacePacket(ws));
                NetworkHandler.broadcastPresenceForTeam(player.serverLevel(), playerTeamId);
            }
        });
    }
}
