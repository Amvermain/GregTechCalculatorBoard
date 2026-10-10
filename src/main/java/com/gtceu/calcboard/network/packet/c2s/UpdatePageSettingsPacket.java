package com.gtceu.calcboard.network.packet.c2s;

import com.gtceu.calcboard.api.team.TeamWorkspacePage;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.ClientPacketHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CWorkspaceErrorPacket;
import com.gtceu.calcboard.server.storage.TeamBoardSavedData;
import com.gtceu.calcboard.server.storage.TeamWorkspaceData;
import com.gtceu.calcboard.server.storage.WorkspaceLockManager;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Bidirectional packet synchronizing page settings with sender authorization, lock validation, and targeted broadcast.
 */
public class UpdatePageSettingsPacket {

    private final UUID teamId;
    private final String pageId;
    private final int expectedRevision;
    private final int defaultVoltageTierOrdinal;
    private final boolean autoEquipEnergyHatches;

    public UpdatePageSettingsPacket(UUID teamId, String pageId, int expectedRevision, int defaultVoltageTierOrdinal, boolean autoEquipEnergyHatches) {
        this.teamId = teamId;
        this.pageId = pageId != null ? pageId : "";
        this.expectedRevision = expectedRevision;
        this.defaultVoltageTierOrdinal = defaultVoltageTierOrdinal;
        this.autoEquipEnergyHatches = autoEquipEnergyHatches;
    }

    public UpdatePageSettingsPacket(String pageId, int defaultVoltageTierOrdinal) {
        this(null, pageId, 0, defaultVoltageTierOrdinal, true);
    }

    public UpdatePageSettingsPacket(String pageId, int defaultVoltageTierOrdinal, boolean autoEquipEnergyHatches) {
        this(null, pageId, 0, defaultVoltageTierOrdinal, autoEquipEnergyHatches);
    }

    public UpdatePageSettingsPacket(FriendlyByteBuf buf) {
        this.teamId = buf.readBoolean() ? buf.readUUID() : null;
        this.pageId = buf.readUtf(256);
        this.expectedRevision = buf.readVarInt();
        this.defaultVoltageTierOrdinal = buf.readByte();
        this.autoEquipEnergyHatches = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(teamId != null);
        if (teamId != null) {
            buf.writeUUID(teamId);
        }
        buf.writeUtf(pageId, 256);
        buf.writeVarInt(expectedRevision);
        buf.writeByte(defaultVoltageTierOrdinal);
        buf.writeBoolean(autoEquipEnergyHatches);
    }

    public UUID getTeamId() {
        return teamId;
    }

    public String getPageId() {
        return pageId;
    }

    public int getExpectedRevision() {
        return expectedRevision;
    }

    public int getDefaultVoltageTierOrdinal() {
        return defaultVoltageTierOrdinal;
    }

    public boolean isAutoEquipEnergyHatches() {
        return autoEquipEnergyHatches;
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (ctx.getDirection().getReceptionSide().isServer()) {
                handleServer(ctx);
            } else {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleUpdatePageSettings(this));
            }
        });
        ctx.setPacketHandled(true);
    }

    private void handleServer(NetworkEvent.Context ctx) {
        ServerPlayer sender = ctx.getSender();
        if (sender == null || sender.getServer() == null) {
            return;
        }

        UUID playerTeamId = TeamProviderRegistry.getInstance().getPlayerTeamId(sender);
        if (!isAuthorizedSender(sender, playerTeamId)) {
            NetworkHandler.sendToPlayer(sender, new S2CWorkspaceErrorPacket(403, "gui.gtcalcboard.error.access_denied"));
            return;
        }

        if (!WorkspaceLockManager.getInstance().canCommit(playerTeamId, pageId, sender.getUUID())) {
            NetworkHandler.sendToPlayer(sender, new S2CWorkspaceErrorPacket(423, "gui.gtcalcboard.error.locked_by_other"));
            return;
        }

        TeamBoardSavedData savedData = TeamBoardSavedData.get(sender.serverLevel());
        if (savedData == null) {
            return;
        }

        String teamName = TeamProviderRegistry.getInstance().getTeamDisplayName(playerTeamId);
        TeamWorkspaceData ws = savedData.getOrCreateWorkspace(playerTeamId, teamName != null ? teamName : "Team");

        TeamWorkspacePage page = ws.getPage(pageId);
        if (page == null) {
            NetworkHandler.sendToPlayer(sender, new S2CWorkspaceErrorPacket(404, "gui.gtcalcboard.error.generic"));
            return;
        }

        if (expectedRevision > 0 && page.getPageRevision() != expectedRevision) {
            NetworkHandler.sendToPlayer(sender, new S2CWorkspaceErrorPacket(409, "gui.gtcalcboard.error.revision_conflict"));
            return;
        }

        applySettingsAndBroadcast(sender, savedData, page, playerTeamId);
    }

    private boolean isAuthorizedSender(ServerPlayer sender, UUID playerTeamId) {
        if (playerTeamId == null) {
            return false;
        }
        if (this.teamId != null && !playerTeamId.equals(this.teamId)) {
            return false;
        }
        return TeamProviderRegistry.getInstance().canPlayerEdit(sender, playerTeamId);
    }

    private void applySettingsAndBroadcast(ServerPlayer sender, TeamBoardSavedData savedData, TeamWorkspacePage page, UUID playerTeamId) {
        page.applySettings((byte) defaultVoltageTierOrdinal, autoEquipEnergyHatches);
        page.incrementRevision();
        savedData.setDirty();

        UpdatePageSettingsPacket broadcastPacket = new UpdatePageSettingsPacket(
                playerTeamId,
                pageId,
                page.getPageRevision(),
                defaultVoltageTierOrdinal,
                autoEquipEnergyHatches
        );
        NetworkHandler.broadcastToTeam(sender.serverLevel(), playerTeamId, broadcastPacket, null);
    }
}
