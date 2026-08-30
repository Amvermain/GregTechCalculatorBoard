package com.gtceu.calcboard.network.packet.c2s;

import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CSyncCommitHistoryPacket;
import com.gtceu.calcboard.server.storage.CommitLogEntry;
import com.gtceu.calcboard.server.storage.TeamBoardSavedData;
import com.gtceu.calcboard.server.storage.TeamWorkspaceData;
import com.gtceu.calcboard.server.team.TeamProviderRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * C2S request packet to fetch paginated commit history on-demand.
 */
public class C2SRequestCommitHistoryPacket {

    private final int offset;
    private final int limit;

    public C2SRequestCommitHistoryPacket(int offset, int limit) {
        this.offset = Math.max(0, offset);
        this.limit = Math.max(1, Math.min(limit, 100));
    }

    public C2SRequestCommitHistoryPacket(FriendlyByteBuf buf) {
        this.offset = buf.readVarInt();
        this.limit = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(offset);
        buf.writeVarInt(limit);
    }

    public int getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

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
            if (offset >= total) {
                NetworkHandler.sendToPlayer(player, new S2CSyncCommitHistoryPacket(total, Collections.emptyList()));
                return;
            }

            int toIndex = Math.min(total, offset + limit);
            List<CommitLogEntry> subList = new ArrayList<>(allCommits.subList(offset, toIndex));
            NetworkHandler.sendToPlayer(player, new S2CSyncCommitHistoryPacket(total, subList));
        });
        ctx.setPacketHandled(true);
    }
}
