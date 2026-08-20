package com.gtceu.calcboard.network.packet.c2s;

import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CLockResultPacket;
import com.gtceu.calcboard.network.packet.s2c.S2CSyncWorkspacePacket;
import com.gtceu.calcboard.network.packet.s2c.S2CWorkspaceErrorPacket;
import com.gtceu.calcboard.server.storage.CommitLogEntry;
import com.gtceu.calcboard.server.storage.TeamBoardSavedData;
import com.gtceu.calcboard.server.storage.TeamWorkspaceData;
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;
import com.gtceu.calcboard.server.storage.WorkspaceLockManager;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class C2SDeleteTeamPagePacket {

    private final UUID teamId;
    private final String pageId;

    public C2SDeleteTeamPagePacket(UUID teamId, String pageId) {
        this.teamId = teamId != null ? teamId : new UUID(0L, 0L);
        this.pageId = pageId != null ? pageId : "default";
    }

    public C2SDeleteTeamPagePacket(FriendlyByteBuf buf) {
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
                NetworkHandler.sendToPlayer(player, new S2CWorkspaceErrorPacket(403, "gui.gtcalcboard.error.access_denied"));
                return;
            }

            // Check admin / owner permissions
            if (!TeamProviderRegistry.getInstance().canPlayerAdministerTeam(player, playerTeamId)) {
                NetworkHandler.sendToPlayer(player, new S2CWorkspaceErrorPacket(403, "gui.gtcalcboard.error.delete_no_permission"));
                return;
            }

            TeamBoardSavedData savedData = TeamBoardSavedData.get(player.serverLevel());
            if (savedData == null) return;

            String teamName = TeamProviderRegistry.getInstance().getTeamDisplayName(playerTeamId);
            TeamWorkspaceData ws = savedData.getOrCreateWorkspace(playerTeamId, teamName);

            if (ws.getPages().size() <= 1) {
                NetworkHandler.sendToPlayer(player, new S2CWorkspaceErrorPacket(400, "gui.gtcalcboard.error.cannot_delete_last_page"));
                return;
            }

            TeamWorkspacePage removed = ws.removePage(pageId);
            if (removed != null) {
                // Force release lock if held
                WorkspaceLockManager.getInstance().forceReleaseLock(playerTeamId, pageId);

                // Commit log entry
                CommitLogEntry commit = new CommitLogEntry(
                        ws.getGlobalRevision(),
                        player.getUUID(),
                        player.getGameProfile().getName(),
                        System.currentTimeMillis(),
                        pageId,
                        "Deleted page: " + removed.getTitle(),
                        0, 0, 0
                );
                ws.addCommit(commit);

                savedData.setDirty();

                // Broadcast updated workspace and released lock to all teammates
                NetworkHandler.broadcastToTeam(player.serverLevel(), playerTeamId, new S2CSyncWorkspacePacket(ws), null);
                NetworkHandler.broadcastToTeam(player.serverLevel(), playerTeamId, new S2CLockResultPacket(pageId, false, null, "", 0L), null);
            }
        });
        ctx.setPacketHandled(true);
    }
}
