package com.gtceu.calcboard.network;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.network.packet.c2s.*;
import com.gtceu.calcboard.network.packet.s2c.*;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Set;
import java.util.UUID;

/**
 * Manages the NeoForge 1.21.1 network payload pipeline for GTCalcBoard.
 */
@EventBusSubscriber(modid = GregTechCalcBoard.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "2.1.0";

    public static void init() {
        // Initialization hook if needed
    }

    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        // C2S Packets
        registrar.playToServer(
                C2SRequestWorkspacePacket.TYPE,
                C2SRequestWorkspacePacket.STREAM_CODEC,
                C2SRequestWorkspacePacket::handle
        );
        registrar.playToServer(
                C2SAcquireLockPacket.TYPE,
                C2SAcquireLockPacket.STREAM_CODEC,
                C2SAcquireLockPacket::handle
        );
        registrar.playToServer(
                C2SReleaseLockPacket.TYPE,
                C2SReleaseLockPacket.STREAM_CODEC,
                C2SReleaseLockPacket::handle
        );
        registrar.playToServer(
                C2SCommitWorkspacePacket.TYPE,
                C2SCommitWorkspacePacket.STREAM_CODEC,
                C2SCommitWorkspacePacket::handle
        );
        registrar.playToServer(
                C2SChunkedCommitPacket.TYPE,
                C2SChunkedCommitPacket.STREAM_CODEC,
                C2SChunkedCommitPacket::handle
        );
        registrar.playToServer(
                C2SDeleteTeamPagePacket.TYPE,
                C2SDeleteTeamPagePacket.STREAM_CODEC,
                C2SDeleteTeamPagePacket::handle
        );
        registrar.playToServer(
                C2SPingPresencePacket.TYPE,
                C2SPingPresencePacket.STREAM_CODEC,
                C2SPingPresencePacket::handle
        );
        registrar.playToServer(
                C2SRequestPageDataPacket.TYPE,
                C2SRequestPageDataPacket.STREAM_CODEC,
                C2SRequestPageDataPacket::handle
        );
        registrar.playToServer(
                C2SRequestCommitHistoryPacket.TYPE,
                C2SRequestCommitHistoryPacket.STREAM_CODEC,
                C2SRequestCommitHistoryPacket::handle
        );

        // S2C Packets
        registrar.playToClient(
                S2CSyncWorkspacePacket.TYPE,
                S2CSyncWorkspacePacket.STREAM_CODEC,
                S2CSyncWorkspacePacket::handle
        );
        registrar.playToClient(
                S2CLockResultPacket.TYPE,
                S2CLockResultPacket.STREAM_CODEC,
                S2CLockResultPacket::handle
        );
        registrar.playToClient(
                S2CBroadcastPresencePacket.TYPE,
                S2CBroadcastPresencePacket.STREAM_CODEC,
                S2CBroadcastPresencePacket::handle
        );
        registrar.playToClient(
                S2CWorkspaceErrorPacket.TYPE,
                S2CWorkspaceErrorPacket.STREAM_CODEC,
                S2CWorkspaceErrorPacket::handle
        );
        registrar.playToClient(
                S2CSyncWorkspaceMetaPacket.TYPE,
                S2CSyncWorkspaceMetaPacket.STREAM_CODEC,
                S2CSyncWorkspaceMetaPacket::handle
        );
        registrar.playToClient(
                S2CSyncPageDataPacket.TYPE,
                S2CSyncPageDataPacket.STREAM_CODEC,
                S2CSyncPageDataPacket::handle
        );
        registrar.playToClient(
                S2CChunkedDataPacket.TYPE,
                S2CChunkedDataPacket.STREAM_CODEC,
                S2CChunkedDataPacket::handle
        );
        registrar.playToClient(
                S2CSyncCommitHistoryPacket.TYPE,
                S2CSyncCommitHistoryPacket.STREAM_CODEC,
                S2CSyncCommitHistoryPacket::handle
        );
    }

    public static void sendToServer(CustomPacketPayload msg) {
        try {
            PacketDistributor.sendToServer(msg);
        } catch (Throwable ignored) {
        }
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload msg) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, msg);
        }
    }

    public static void broadcastToTeam(ServerLevel level, UUID teamId, CustomPacketPayload msg, UUID excludePlayerUUID) {
        if (level == null || teamId == null) return;
        Set<UUID> members = TeamProviderRegistry.getInstance().getTeamMembers(teamId);

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (excludePlayerUUID != null && player.getUUID().equals(excludePlayerUUID)) {
                continue;
            }
            if (members.contains(player.getUUID()) || teamId.equals(TeamProviderRegistry.getInstance().getPlayerTeamId(player))) {
                sendToPlayer(player, msg);
            }
        }
    }

    public static void broadcastPresenceForTeam(ServerLevel level, UUID teamId) {
        if (level == null || level.getServer() == null || teamId == null) return;
        java.util.List<S2CBroadcastPresencePacket.MemberPresence> presences = new java.util.ArrayList<>();
        Set<UUID> members = TeamProviderRegistry.getInstance().getTeamMembers(teamId);

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (members.contains(player.getUUID()) || teamId.equals(TeamProviderRegistry.getInstance().getPlayerTeamId(player))) {
                presences.add(new S2CBroadcastPresencePacket.MemberPresence(
                        player.getUUID(),
                        player.getGameProfile().getName(),
                        "page_main",
                        false
                ));
            }
        }

        broadcastToTeam(level, teamId, new S2CBroadcastPresencePacket(teamId, presences), null);
    }
}
