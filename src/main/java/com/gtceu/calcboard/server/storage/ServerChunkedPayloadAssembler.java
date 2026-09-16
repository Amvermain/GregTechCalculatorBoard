package com.gtceu.calcboard.server.storage;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side chunked payload assembler for incoming C2S upload streams with DoS upper bound guards and TTL eviction.
 */
public class ServerChunkedPayloadAssembler {

    public static final int MAX_ALLOWED_CHUNKS = 128;
    public static final int MAX_CHUNKS = MAX_ALLOWED_CHUNKS;
    public static final int MAX_CHUNK_PAYLOAD_SIZE = 512 * 1024;
    public static final int MAX_CHUNK_BYTE_SIZE = MAX_CHUNK_PAYLOAD_SIZE;
    public static final int MAX_ACTIVE_TRANSFERS = 64;
    public static final long CHUNK_BUFFER_TTL_MS = 60_000L;
    public static final long MAX_SESSION_LIFETIME_MS = 120_000L;

    private static class BufferEntry {
        private final int expectedTotalChunks;
        private final long creationTimestamp;
        private final Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();
        private volatile long lastActivityTimestamp;

        public BufferEntry(int expectedTotalChunks) {
            this.expectedTotalChunks = expectedTotalChunks;
            long now = System.currentTimeMillis();
            this.creationTimestamp = now;
            this.lastActivityTimestamp = now;
        }

        public void touch() {
            this.lastActivityTimestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            long now = System.currentTimeMillis();
            return (now - lastActivityTimestamp) > CHUNK_BUFFER_TTL_MS
                    || (now - creationTimestamp) > MAX_SESSION_LIFETIME_MS;
        }
    }

    private static final Map<UUID, BufferEntry> CHUNK_BUFFERS = new ConcurrentHashMap<>();

    private ServerChunkedPayloadAssembler() {}

    public static boolean validateChunkParameters(UUID transferId, int chunkIndex, int totalChunks, byte[] chunkData) {
        if (transferId == null || chunkData == null) {
            return false;
        }
        if (totalChunks <= 0 || totalChunks > MAX_ALLOWED_CHUNKS) {
            return false;
        }
        if (chunkIndex < 0 || chunkIndex >= totalChunks) {
            return false;
        }
        if (chunkData.length > MAX_CHUNK_PAYLOAD_SIZE) {
            return false;
        }
        return true;
    }

    public static byte[] appendChunk(UUID transferId, int chunkIndex, int totalChunks, byte[] chunkData) {
        if (!validateChunkParameters(transferId, chunkIndex, totalChunks, chunkData)) {
            dropTransfer(transferId, "Invalid chunk parameters");
            return null;
        }

        cleanExpiredBuffers();

        BufferEntry entry = CHUNK_BUFFERS.get(transferId);
        if (entry == null) {
            if (CHUNK_BUFFERS.size() >= MAX_ACTIVE_TRANSFERS) {
                return null;
            }
            entry = CHUNK_BUFFERS.computeIfAbsent(transferId, k -> {
                if (CHUNK_BUFFERS.size() >= MAX_ACTIVE_TRANSFERS) {
                    return null;
                }
                return new BufferEntry(totalChunks);
            });
            if (entry == null) {
                return null;
            }
        }

        if (entry.expectedTotalChunks != totalChunks) {
            dropTransfer(transferId, "Mismatched totalChunks in stream");
            return null;
        }

        byte[] existingChunk = entry.chunks.get(chunkIndex);
        if (existingChunk != null) {
            if (!Arrays.equals(existingChunk, chunkData)) {
                dropTransfer(transferId, "Tampered chunk data detected");
                return null;
            }
        } else {
            entry.touch();
            entry.chunks.put(chunkIndex, chunkData);
        }

        if (entry.chunks.size() >= totalChunks) {
            return assembleAndRemove(transferId, entry, totalChunks);
        }
        return null;
    }

    private static byte[] assembleAndRemove(UUID transferId, BufferEntry entry, int totalChunks) {
        CHUNK_BUFFERS.remove(transferId);
        int totalBytes = 0;
        for (int i = 0; i < totalChunks; i++) {
            byte[] part = entry.chunks.get(i);
            if (part == null) {
                return null;
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

    public static void dropTransfer(UUID transferId, String reason) {
        if (transferId != null) {
            CHUNK_BUFFERS.remove(transferId);
        }
    }

    public static void dropTransfer(UUID transferId) {
        dropTransfer(transferId, "");
    }

    public static void cleanExpiredBuffers() {
        CHUNK_BUFFERS.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    public static void clear() {
        CHUNK_BUFFERS.clear();
    }

    public static int getActiveBufferCount() {
        return CHUNK_BUFFERS.size();
    }

    public static boolean hasActiveTransfer(UUID transferId) {
        return transferId != null && CHUNK_BUFFERS.containsKey(transferId);
    }
}
