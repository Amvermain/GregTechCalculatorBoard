package com.gtceu.calcboard.client.team;

import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.c2s.C2SChunkedCommitPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Arrays;
import java.util.UUID;

/**
 * Client-side utility for safely chunking and uploading large page NBT payloads to the server.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientChunkedStreamHelper {

    public static final int MAX_CHUNK_PAYLOAD_SIZE = 512 * 1024; // 512 KB

    private ClientChunkedStreamHelper() {}

    /**
     * Streams the committed page data to the server safely in 512KB chunks.
     */
    public static void commitPageSafely(UUID teamId, String pageId, String pageTitle, int revision,
                                        String commitMessage, byte[] compressedNBT,
                                        int addedNodes, int modifiedNodes, int deletedNodes) {
        byte[] payload = compressedNBT != null ? compressedNBT : new byte[0];

        if (payload.length <= MAX_CHUNK_PAYLOAD_SIZE) {
            UUID transferId = UUID.randomUUID();
            NetworkHandler.sendToServer(new C2SChunkedCommitPacket(
                    transferId, teamId, pageId, pageTitle, revision, commitMessage,
                    0, 1, payload, addedNodes, modifiedNodes, deletedNodes
            ));
            return;
        }

        UUID transferId = UUID.randomUUID();
        int totalChunks = (int) Math.ceil((double) payload.length / MAX_CHUNK_PAYLOAD_SIZE);

        for (int i = 0; i < totalChunks; i++) {
            int start = i * MAX_CHUNK_PAYLOAD_SIZE;
            int end = Math.min(payload.length, start + MAX_CHUNK_PAYLOAD_SIZE);
            byte[] chunk = Arrays.copyOfRange(payload, start, end);

            NetworkHandler.sendToServer(new C2SChunkedCommitPacket(
                    transferId, teamId, pageId, pageTitle, revision, commitMessage,
                    i, totalChunks, chunk, addedNodes, modifiedNodes, deletedNodes
            ));
        }
    }
}
