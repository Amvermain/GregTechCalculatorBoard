package com.gtceu.calcboard.network.packet.c2s;

import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CWorkspaceErrorPacket;
import com.gtceu.calcboard.server.storage.ChunkedStreamHelper;
import com.gtceu.calcboard.server.storage.TeamBoardSavedData;
import com.gtceu.calcboard.server.storage.TeamWorkspaceData;
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * C2S request packet to stream specific page data on-demand.
 */
public class C2SRequestPageDataPacket {

    private final String pageId;
    private final int currentCachedRevision;

    public C2SRequestPageDataPacket(String pageId, int currentCachedRevision) {
        this.pageId = pageId != null ? pageId : "default";
        this.currentCachedRevision = currentCachedRevision;
    }

    public C2SRequestPageDataPacket(FriendlyByteBuf buf) {
        this.pageId = buf.readUtf(256);
        this.currentCachedRevision = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(pageId);
        buf.writeVarInt(currentCachedRevision);
    }

    public String getPageId() {
        return pageId;
    }

    public int getCurrentCachedRevision() {
        return currentCachedRevision;
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

            TeamBoardSavedData savedData = TeamBoardSavedData.get(player.serverLevel());
            if (savedData == null) return;

            TeamWorkspaceData ws = savedData.getWorkspace(playerTeamId);
            if (ws == null) return;

            TeamWorkspacePage page = ws.getPage(pageId);
            if (page == null) return;

            // Cache Hit check: if client already has the identical revision, skip re-sending full payload
            if (currentCachedRevision > 0 && currentCachedRevision == page.getPageRevision()) {
                return; // Client cache is up to date!
            }

            byte[] data = page.getCompressedGraphData();
            ChunkedStreamHelper.sendPageDataSafely(player, pageId, page.getPageRevision(), data);
        });
        ctx.setPacketHandled(true);
    }
}
