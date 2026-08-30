package com.gtceu.calcboard.server.storage;

import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CChunkedDataPacket;
import com.gtceu.calcboard.network.packet.s2c.S2CSyncPageDataPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.UUID;

/**
 * Utility for safe chunked streaming of large page NBT payloads across the Netty pipeline.
 */
public final class ChunkedStreamHelper {

    public static final int MAX_CHUNK_PAYLOAD_SIZE = 512 * 1024; // 512 KB

    private ChunkedStreamHelper() {}

    /**
     * Sends page NBT data safely to the player.
     * Uses a single packet if data <= 512KB, or streams chunked packets if data > 512KB.
     */
    public static void sendPageDataSafely(ServerPlayer player, String pageId, int revision, byte[] data) {
        if (player == null) return;
        byte[] payload = data != null ? data : new byte[0];

        if (payload.length <= MAX_CHUNK_PAYLOAD_SIZE) {
            NetworkHandler.sendToPlayer(player, new S2CSyncPageDataPacket(pageId, revision, payload));
            return;
        }

        UUID transferId = UUID.randomUUID();
        int totalChunks = (int) Math.ceil((double) payload.length / MAX_CHUNK_PAYLOAD_SIZE);

        for (int i = 0; i < totalChunks; i++) {
            int start = i * MAX_CHUNK_PAYLOAD_SIZE;
            int end = Math.min(payload.length, start + MAX_CHUNK_PAYLOAD_SIZE);
            byte[] chunk = Arrays.copyOfRange(payload, start, end);
            NetworkHandler.sendToPlayer(player, new S2CChunkedDataPacket(transferId, pageId, revision, i, totalChunks, chunk));
        }
    }
}
