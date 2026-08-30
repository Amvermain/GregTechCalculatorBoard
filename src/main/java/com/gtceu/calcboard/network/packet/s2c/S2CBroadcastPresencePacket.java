package com.gtceu.calcboard.network.packet.s2c;

import com.gtceu.calcboard.GregTechCalcBoard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record S2CBroadcastPresencePacket(UUID teamId, List<MemberPresence> activeMembers) implements CustomPacketPayload {

    public static final Type<S2CBroadcastPresencePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GregTechCalcBoard.MOD_ID, "s2c_broadcast_presence"));
    public static final StreamCodec<FriendlyByteBuf, S2CBroadcastPresencePacket> STREAM_CODEC = CustomPacketPayload.codec(
            S2CBroadcastPresencePacket::write,
            S2CBroadcastPresencePacket::new
    );

    public record MemberPresence(UUID playerUUID, String playerName, String activePageId, boolean isEditing) {
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

    public S2CBroadcastPresencePacket(FriendlyByteBuf buf) {
        this(buf.readUUID(), readMemberList(buf));
    }

    private static List<MemberPresence> readMemberList(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<MemberPresence> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID pId = buf.readUUID();
            String name = buf.readUtf(256);
            String page = buf.readUtf(256);
            boolean editing = buf.readBoolean();
            list.add(new MemberPresence(pId, name, page, editing));
        }
        return list;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(teamId != null ? teamId : new UUID(0L, 0L));
        List<MemberPresence> members = activeMembers != null ? activeMembers : List.of();
        buf.writeVarInt(members.size());
        for (MemberPresence m : members) {
            buf.writeUUID(m.playerUUID());
            buf.writeUtf(m.playerName());
            buf.writeUtf(m.activePageId());
            buf.writeBoolean(m.isEditing());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public List<MemberPresence> getActiveMembers() {
        return activeMembers;
    }

    public static void handle(S2CBroadcastPresencePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketHandler.handlePresence(packet));
    }
}
