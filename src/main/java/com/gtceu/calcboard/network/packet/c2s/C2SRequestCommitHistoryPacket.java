package com.gtceu.calcboard.network.packet.c2s;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CSyncCommitHistoryPacket;
import com.gtceu.calcboard.server.storage.CommitLogEntry;
import com.gtceu.calcboard.server.storage.TeamBoardSavedData;
import com.gtceu.calcboard.server.storage.TeamWorkspaceData;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * C2S request packet to fetch paginated commit history on-demand.
 */
public record C2SRequestCommitHistoryPacket(int offset, int limit) implements CustomPacketPayload {

    public static final Type<C2SRequestCommitHistoryPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GregTechCalcBoard.MOD_ID, "c2s_request_commit_history"));
    public static final StreamCodec<FriendlyByteBuf, C2SRequestCommitHistoryPacket> STREAM_CODEC = CustomPacketPayload.codec(
            C2SRequestCommitHistoryPacket::write,
            C2SRequestCommitHistoryPacket::new
    );

    public C2SRequestCommitHistoryPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(Math.max(0, offset));
        buf.writeVarInt(Math.max(1, Math.min(limit, 100)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SRequestCommitHistoryPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            UUID playerTeamId = TeamProviderRegistry.getInstance().getPlayerTeamId(player);
            if (playerTeamId == null) {
                NetworkHandler.sendToPlayer(player, new S2CSyncCommitHistoryPacket(0, Collections.emptyList()));
                return;
            }

            TeamBoardSavedData savedData = TeamBoardSavedData.get(player.serverLevel());
            if (savedData == null) return;

            TeamWorkspaceData ws = savedData.getWorkspace(playerTeamId);
            if (ws == null) {
                NetworkHandler.sendToPlayer(player, new S2CSyncCommitHistoryPacket(0, Collections.emptyList()));
                return;
            }

            List<CommitLogEntry> allCommits = ws.getCommitHistory();
            int total = allCommits.size();
            if (packet.offset() >= total) {
                NetworkHandler.sendToPlayer(player, new S2CSyncCommitHistoryPacket(total, Collections.emptyList()));
                return;
            }

            int toIndex = Math.min(total, packet.offset() + packet.limit());
            List<CommitLogEntry> subList = new ArrayList<>(allCommits.subList(packet.offset(), toIndex));
            NetworkHandler.sendToPlayer(player, new S2CSyncCommitHistoryPacket(total, subList));
        });
    }
}
