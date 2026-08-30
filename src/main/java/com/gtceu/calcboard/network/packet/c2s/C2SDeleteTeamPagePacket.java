package com.gtceu.calcboard.network.packet.c2s;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CLockResultPacket;
import com.gtceu.calcboard.network.packet.s2c.S2CWorkspaceErrorPacket;
import com.gtceu.calcboard.server.storage.CommitLogEntry;
import com.gtceu.calcboard.server.storage.TeamBoardSavedData;
import com.gtceu.calcboard.server.storage.TeamWorkspaceData;
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;
import com.gtceu.calcboard.server.storage.WorkspaceLockManager;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record C2SDeleteTeamPagePacket(UUID teamId, String pageId) implements CustomPacketPayload {

    public static final Type<C2SDeleteTeamPagePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GregTechCalcBoard.MOD_ID, "c2s_delete_team_page"));
    public static final StreamCodec<FriendlyByteBuf, C2SDeleteTeamPagePacket> STREAM_CODEC = CustomPacketPayload.codec(
            C2SDeleteTeamPagePacket::write,
            C2SDeleteTeamPagePacket::new
    );

    public C2SDeleteTeamPagePacket(FriendlyByteBuf buf) {
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

    public static void handle(C2SDeleteTeamPagePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

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

            TeamWorkspacePage removed = ws.removePage(packet.pageId());
            if (removed != null) {
                // Force release lock if held
                WorkspaceLockManager.getInstance().forceReleaseLock(playerTeamId, packet.pageId());

                // Commit log entry
                CommitLogEntry commit = new CommitLogEntry(
                        ws.getGlobalRevision(),
                        player.getUUID(),
                        player.getGameProfile().getName(),
                        System.currentTimeMillis(),
                        packet.pageId(),
                        "Deleted page: " + removed.getTitle(),
                        0, 0, 0
                );
                ws.addCommit(commit);

                savedData.setDirty();

                // Broadcast updated workspace meta and released lock to all teammates
                NetworkHandler.broadcastToTeam(player.serverLevel(), playerTeamId, ws.buildMetaPacket(), null);
                NetworkHandler.broadcastToTeam(player.serverLevel(), playerTeamId, new S2CLockResultPacket(packet.pageId(), false, null, "", 0L), null);
            }
        });
    }
}
