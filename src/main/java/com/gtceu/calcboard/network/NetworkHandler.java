package com.gtceu.calcboard.network;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.network.packet.c2s.*;
import com.gtceu.calcboard.network.packet.s2c.*;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the SimpleChannel network pipeline for GTCalcBoard v2.0.0.
 */
public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "2.1.0";
    private static int packetId = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.tryParse(GregTechCalcBoard.MOD_ID + ":main"),
            () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION::equals),
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION::equals)
    );

    public static void init() {
        // C2S Packets
        registerMessage(C2SRequestWorkspacePacket.class, C2SRequestWorkspacePacket::encode, C2SRequestWorkspacePacket::new, C2SRequestWorkspacePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        registerMessage(C2SAcquireLockPacket.class, C2SAcquireLockPacket::encode, C2SAcquireLockPacket::new, C2SAcquireLockPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        registerMessage(C2SReleaseLockPacket.class, C2SReleaseLockPacket::encode, C2SReleaseLockPacket::new, C2SReleaseLockPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        registerMessage(C2SCommitWorkspacePacket.class, C2SCommitWorkspacePacket::encode, C2SCommitWorkspacePacket::new, C2SCommitWorkspacePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        registerMessage(C2SChunkedCommitPacket.class, C2SChunkedCommitPacket::encode, C2SChunkedCommitPacket::new, C2SChunkedCommitPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        registerMessage(C2SDeleteTeamPagePacket.class, C2SDeleteTeamPagePacket::encode, C2SDeleteTeamPagePacket::new, C2SDeleteTeamPagePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        registerMessage(C2SPingPresencePacket.class, C2SPingPresencePacket::encode, C2SPingPresencePacket::new, C2SPingPresencePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        registerMessage(C2SRequestPageDataPacket.class, C2SRequestPageDataPacket::encode, C2SRequestPageDataPacket::new, C2SRequestPageDataPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        registerMessage(C2SRequestCommitHistoryPacket.class, C2SRequestCommitHistoryPacket::encode, C2SRequestCommitHistoryPacket::new, C2SRequestCommitHistoryPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // S2C Packets
        registerMessage(S2CSyncWorkspacePacket.class, S2CSyncWorkspacePacket::encode, S2CSyncWorkspacePacket::new, S2CSyncWorkspacePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registerMessage(S2CLockResultPacket.class, S2CLockResultPacket::encode, S2CLockResultPacket::new, S2CLockResultPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registerMessage(S2CBroadcastPresencePacket.class, S2CBroadcastPresencePacket::encode, S2CBroadcastPresencePacket::new, S2CBroadcastPresencePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registerMessage(S2CWorkspaceErrorPacket.class, S2CWorkspaceErrorPacket::encode, S2CWorkspaceErrorPacket::new, S2CWorkspaceErrorPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registerMessage(S2CSyncWorkspaceMetaPacket.class, S2CSyncWorkspaceMetaPacket::encode, S2CSyncWorkspaceMetaPacket::new, S2CSyncWorkspaceMetaPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registerMessage(S2CSyncPageDataPacket.class, S2CSyncPageDataPacket::encode, S2CSyncPageDataPacket::new, S2CSyncPageDataPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registerMessage(S2CChunkedDataPacket.class, S2CChunkedDataPacket::encode, S2CChunkedDataPacket::new, S2CChunkedDataPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registerMessage(S2CSyncCommitHistoryPacket.class, S2CSyncCommitHistoryPacket::encode, S2CSyncCommitHistoryPacket::new, S2CSyncCommitHistoryPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static <MSG> void registerMessage(Class<MSG> msgClass,
                                              java.util.function.BiConsumer<MSG, net.minecraft.network.FriendlyByteBuf> encoder,
                                              java.util.function.Function<net.minecraft.network.FriendlyByteBuf, MSG> decoder,
                                              java.util.function.BiConsumer<MSG, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context>> handler,
                                              Optional<NetworkDirection> direction) {
        CHANNEL.registerMessage(packetId++, msgClass, encoder, decoder, handler, direction);
    }

    public static void sendToServer(Object msg) {
        try {
            CHANNEL.sendToServer(msg);
        } catch (Throwable ignored) {
        }
    }

    public static void sendToPlayer(ServerPlayer player, Object msg) {
        if (player != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
        }
    }

    public static void broadcastToTeam(ServerLevel level, UUID teamId, Object msg, UUID excludePlayerUUID) {
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
