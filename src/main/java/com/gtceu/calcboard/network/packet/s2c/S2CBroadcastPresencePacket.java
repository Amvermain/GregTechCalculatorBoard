package com.gtceu.calcboard.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class S2CBroadcastPresencePacket {

    public static class MemberPresence {
        private final UUID playerUUID;
        private final String playerName;
        private final String activePageId;
        private final boolean isEditing;

        public MemberPresence(UUID playerUUID, String playerName, String activePageId, boolean isEditing) {
            this.playerUUID = playerUUID;
            this.playerName = playerName != null ? playerName : "Player";
            this.activePageId = activePageId != null ? activePageId : "default";
            this.isEditing = isEditing;
        }

        public UUID getPlayerUUID() {
            return playerUUID;
        }

        public String getPlayerName() {
            return playerName;
        }

        public String getActivePageId() {
            return activePageId;
        }

        public boolean isEditing() {
            return isEditing;
        }
    }

    private final UUID teamId;
    private final List<MemberPresence> activeMembers;

    public S2CBroadcastPresencePacket(UUID teamId, List<MemberPresence> activeMembers) {
        this.teamId = teamId;
        this.activeMembers = activeMembers != null ? activeMembers : new ArrayList<>();
    }

    public S2CBroadcastPresencePacket(FriendlyByteBuf buf) {
        this.teamId = buf.readUUID();
        int count = buf.readVarInt();
        this.activeMembers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID pId = buf.readUUID();
            String name = buf.readUtf(256);
            String page = buf.readUtf(256);
            boolean editing = buf.readBoolean();
            this.activeMembers.add(new MemberPresence(pId, name, page, editing));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(teamId);
        buf.writeVarInt(activeMembers.size());
        for (MemberPresence m : activeMembers) {
            buf.writeUUID(m.getPlayerUUID());
            buf.writeUtf(m.getPlayerName());
            buf.writeUtf(m.getActivePageId());
            buf.writeBoolean(m.isEditing());
        }
    }

    public UUID getTeamId() {
        return teamId;
    }

    public List<MemberPresence> getActiveMembers() {
        return activeMembers;
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handlePresence(this)));
        ctx.setPacketHandled(true);
    }
}
