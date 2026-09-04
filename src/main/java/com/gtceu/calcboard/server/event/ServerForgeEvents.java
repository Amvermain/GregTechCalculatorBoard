package com.gtceu.calcboard.server.event;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CSyncWorkspacePacket;
import com.gtceu.calcboard.server.storage.TeamBoardSavedData;
import com.gtceu.calcboard.server.storage.TeamWorkspaceData;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.server.level.ServerPlayer;
import com.gtceu.calcboard.server.storage.WorkspaceLockManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = GregTechCalcBoard.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerForgeEvents {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerTeamId = TeamProviderRegistry.getInstance().getPlayerTeamId(player);
        if (playerTeamId != null) {
            TeamBoardSavedData savedData = TeamBoardSavedData.get(player.serverLevel());
            if (savedData != null) {
                String teamName = TeamProviderRegistry.getInstance().getTeamDisplayName(playerTeamId);
                TeamWorkspaceData ws = savedData.getOrCreateWorkspace(playerTeamId, teamName);
                NetworkHandler.sendToPlayer(player, ws.buildMetaPacket());
                NetworkHandler.broadcastPresenceForTeam(player.serverLevel(), playerTeamId);
                GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [Multiplayer] Synced team workspace meta '{}' ({}) for player '{}'", teamName, playerTeamId, player.getScoreboardName());
            }
        } else {
            NetworkHandler.sendToPlayer(player, new com.gtceu.calcboard.network.packet.s2c.S2CSyncWorkspaceMetaPacket(new UUID(0L, 0L), "", 0, Collections.emptyList()));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();
        UUID playerTeamId = TeamProviderRegistry.getInstance().getPlayerTeamId(player);
        if (playerTeamId != null) {
            java.util.List<String> releasedPages = WorkspaceLockManager.getInstance().releaseAllLocksForPlayer(playerTeamId, playerId);
            com.gtceu.calcboard.server.storage.TeamPresenceTracker.getInstance().removePlayer(player.serverLevel(), playerId);

            for (String pageId : releasedPages) {
                NetworkHandler.broadcastToTeam(
                        player.serverLevel(),
                        playerTeamId,
                        new com.gtceu.calcboard.network.packet.s2c.S2CLockResultPacket(pageId, false, null, "", 0L),
                        null
                );
            }
            GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [Multiplayer] Cleaned up locks and presence for disconnected player '{}' ({})", player.getScoreboardName(), playerId);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [Lifecycle] Server stopped. Resetting workspace locks.");
        try {
            WorkspaceLockManager.getInstance().reset();
        } catch (Throwable t) {
            GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] Failed to reset workspace locks on server stop", t);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel serverLevel) {
            if (serverLevel.dimension() == Level.OVERWORLD) {
                GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [Lifecycle] Overworld unloaded. Resetting workspace locks.");
                try {
                    WorkspaceLockManager.getInstance().reset();
                } catch (Throwable t) {
                    GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] Failed to reset workspace locks on level unload", t);
                }
            }
        }
    }
}
