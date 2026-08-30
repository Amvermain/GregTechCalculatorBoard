package com.gtceu.calcboard.network.packet.s2c;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.server.storage.CommitLogEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C response packet containing paginated commit history.
 */
public record S2CSyncCommitHistoryPacket(int totalCount, List<CommitLogEntry> commits) implements CustomPacketPayload {

    public static final Type<S2CSyncCommitHistoryPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GregTechCalcBoard.MOD_ID, "s2c_sync_commit_history"));
    public static final StreamCodec<FriendlyByteBuf, S2CSyncCommitHistoryPacket> STREAM_CODEC = CustomPacketPayload.codec(
            S2CSyncCommitHistoryPacket::write,
            S2CSyncCommitHistoryPacket::new
    );

    public S2CSyncCommitHistoryPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), readCommitList(buf));
    }

    private static List<CommitLogEntry> readCommitList(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<CommitLogEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new CommitLogEntry(buf));
        }
        return list;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(totalCount);
        List<CommitLogEntry> list = commits != null ? commits : List.of();
        buf.writeVarInt(list.size());
        for (CommitLogEntry entry : list) {
            entry.encode(buf);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public List<CommitLogEntry> getCommits() {
        return commits;
    }

    public static void handle(S2CSyncCommitHistoryPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketHandler.handleSyncCommitHistory(packet));
    }
}
