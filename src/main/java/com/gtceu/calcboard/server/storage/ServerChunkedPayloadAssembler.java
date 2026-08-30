package com.gtceu.calcboard.server.storage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side chunked payload assembler for incoming C2S upload streams with a 60-second TTL cleanup mechanism.
 */
public class ServerChunkedPayloadAssembler {

    private static final long CHUNK_BUFFER_TTL_MS = 60_000L; // 60 seconds TTL

    private static class BufferEntry {
        private final Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();
        private volatile long lastActivityTimestamp = System.currentTimeMillis();

        public void touch() {
            this.lastActivityTimestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - lastActivityTimestamp > CHUNK_BUFFER_TTL_MS;
        }
    }

    private static final Map<UUID, BufferEntry> CHUNK_BUFFERS = new ConcurrentHashMap<>();

    private ServerChunkedPayloadAssembler() {}

    /**
     * Appends a chunk to the server transfer buffer.
     * Returns the fully assembled byte array when all chunks arrive, or null if pending.
     */
    public static byte[] appendChunk(UUID transferId, int chunkIndex, int totalChunks, byte[] chunkData) {
        if (transferId == null || totalChunks <= 0 || chunkData == null) return null;
        cleanExpiredBuffers();

        BufferEntry entry = CHUNK_BUFFERS.computeIfAbsent(transferId, k -> new BufferEntry());
        entry.touch();
        entry.chunks.put(chunkIndex, chunkData);

        if (entry.chunks.size() >= totalChunks) {
            CHUNK_BUFFERS.remove(transferId);
            int totalBytes = 0;
            for (int i = 0; i < totalChunks; i++) {
                byte[] part = entry.chunks.get(i);
                if (part == null) {
                    return null; // Missing an intermediate chunk
                }
                totalBytes += part.length;
            }

            byte[] completeData = new byte[totalBytes];
            int currentOffset = 0;
            for (int i = 0; i < totalChunks; i++) {
                byte[] part = entry.chunks.get(i);
                System.arraycopy(part, 0, completeData, currentOffset, part.length);
                currentOffset += part.length;
            }
            return completeData;
        }

        return null;
    }

    /**
     * Cleans up uncompleted buffers that have exceeded the 60-second TTL.
     */
    public static void cleanExpiredBuffers() {
        CHUNK_BUFFERS.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    /**
     * Clears all active chunk buffers (called on server shutdown/level unload).
     */
    public static void clear() {
        CHUNK_BUFFERS.clear();
    }
}
