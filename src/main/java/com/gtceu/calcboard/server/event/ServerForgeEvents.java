package com.gtceu.calcboard.server.event;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CSyncWorkspacePacket;
import com.gtceu.calcboard.server.storage.TeamBoardSavedData;
import com.gtceu.calcboard.server.storage.TeamWorkspaceData;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
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
                NetworkHandler.sendToPlayer(player, new S2CSyncWorkspacePacket(ws));
                NetworkHandler.broadcastPresenceForTeam(player.serverLevel(), playerTeamId);
                GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [Multiplayer] Synced team workspace '{}' ({}) for player '{}'", teamName, playerTeamId, player.getScoreboardName());
            }
        } else {
            NetworkHandler.sendToPlayer(player, new S2CSyncWorkspacePacket(new UUID(0L, 0L), "", 0, Collections.emptyList(), Collections.emptyList()));
        }
    }
}
