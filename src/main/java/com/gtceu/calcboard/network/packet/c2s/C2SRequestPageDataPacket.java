package com.gtceu.calcboard.network.packet.c2s;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CWorkspaceErrorPacket;
import com.gtceu.calcboard.server.storage.ChunkedStreamHelper;
import com.gtceu.calcboard.server.storage.TeamBoardSavedData;
import com.gtceu.calcboard.server.storage.TeamWorkspaceData;
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * C2S request packet to stream specific page data on-demand.
 */
public record C2SRequestPageDataPacket(String pageId, int currentCachedRevision) implements CustomPacketPayload {

    public static final Type<C2SRequestPageDataPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GregTechCalcBoard.MOD_ID, "c2s_request_page_data"));
    public static final StreamCodec<FriendlyByteBuf, C2SRequestPageDataPacket> STREAM_CODEC = CustomPacketPayload.codec(
            C2SRequestPageDataPacket::write,
            C2SRequestPageDataPacket::new
    );

    public C2SRequestPageDataPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(256), buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(pageId != null ? pageId : "default");
        buf.writeVarInt(currentCachedRevision);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SRequestPageDataPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            UUID playerTeamId = TeamProviderRegistry.getInstance().getPlayerTeamId(player);
            if (playerTeamId == null) {
                NetworkHandler.sendToPlayer(player, new S2CWorkspaceErrorPacket(403, "gui.gtcalcboard.error.access_denied"));
                return;
            }

            TeamBoardSavedData savedData = TeamBoardSavedData.get(player.serverLevel());
            if (savedData == null) return;

            TeamWorkspaceData ws = savedData.getWorkspace(playerTeamId);
            if (ws == null) return;

            TeamWorkspacePage page = ws.getPage(packet.pageId());
            if (page == null) return;

            // Cache Hit check: if client already has the identical revision, skip re-sending full payload
            if (packet.currentCachedRevision() > 0 && packet.currentCachedRevision() == page.getPageRevision()) {
                return; // Client cache is up to date!
            }

            byte[] data = page.getCompressedGraphData();
            ChunkedStreamHelper.sendPageDataSafely(player, packet.pageId(), page.getPageRevision(), data);
        });
    }
}
