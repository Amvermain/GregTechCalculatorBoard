package com.gtceu.calcboard.network.packet.c2s;

import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CSyncWorkspacePacket;
import com.gtceu.calcboard.network.packet.s2c.S2CWorkspaceErrorPacket;
import com.gtceu.calcboard.server.storage.TeamBoardSavedData;
import com.gtceu.calcboard.server.storage.TeamWorkspaceData;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class C2SRequestWorkspacePacket {

    private final UUID teamId;
    private final String requestedPageId;

    public C2SRequestWorkspacePacket(UUID teamId, String requestedPageId) {
        this.teamId = teamId != null ? teamId : new UUID(0L, 0L);
        this.requestedPageId = requestedPageId != null ? requestedPageId : "";
    }

    public C2SRequestWorkspacePacket(FriendlyByteBuf buf) {
        this.teamId = buf.readUUID();
        this.requestedPageId = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(teamId);
        buf.writeUtf(requestedPageId != null ? requestedPageId : "");
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

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
        ctx.setPacketHandled(true);
    }
}
