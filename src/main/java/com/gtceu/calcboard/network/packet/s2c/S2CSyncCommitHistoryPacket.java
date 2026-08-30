package com.gtceu.calcboard.network.packet.s2c;

import com.gtceu.calcboard.server.storage.CommitLogEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C response packet containing paginated commit history.
 */
public class S2CSyncCommitHistoryPacket {

    private final int totalCount;
    private final List<CommitLogEntry> commits;

    public S2CSyncCommitHistoryPacket(int totalCount, List<CommitLogEntry> commits) {
        this.totalCount = totalCount;
        this.commits = commits != null ? commits : new ArrayList<>();
    }

    public S2CSyncCommitHistoryPacket(FriendlyByteBuf buf) {
        this.totalCount = buf.readVarInt();
        int count = buf.readVarInt();
        this.commits = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.commits.add(new CommitLogEntry(buf));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(totalCount);
        buf.writeVarInt(commits.size());
        for (CommitLogEntry entry : commits) {
            entry.encode(buf);
        }
    }

    public int getTotalCount() {
        return totalCount;
    }

    public List<CommitLogEntry> getCommits() {
        return commits;
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncCommitHistory(this)));
        ctx.setPacketHandled(true);
    }
}
