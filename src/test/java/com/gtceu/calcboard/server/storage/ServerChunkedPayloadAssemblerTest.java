package com.gtceu.calcboard.server.storage;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class ServerChunkedPayloadAssemblerTest {

    @BeforeEach
    void setUp() {
        ServerChunkedPayloadAssembler.clear();
    }

    @Test
    @DisplayName("RFC-052: Reject streams exceeding MAX_ALLOWED_CHUNKS")
    void testExceedMaxChunksRejected() {
        UUID transferId = UUID.randomUUID();
        byte[] payload = new byte[100];
        int invalidTotal = ServerChunkedPayloadAssembler.MAX_ALLOWED_CHUNKS + 1;

        byte[] result = ServerChunkedPayloadAssembler.appendChunk(transferId, 0, invalidTotal, payload);

        Assertions.assertNull(result);
        Assertions.assertEquals(0, ServerChunkedPayloadAssembler.getActiveBufferCount());
    }

    @Test
    @DisplayName("RFC-052: Reject negative chunk index or index exceeding totalChunks")
    void testInvalidChunkIndexRejected() {
        UUID transferId = UUID.randomUUID();
        byte[] payload = new byte[100];

        byte[] negativeResult = ServerChunkedPayloadAssembler.appendChunk(transferId, -1, 5, payload);
        Assertions.assertNull(negativeResult);
        Assertions.assertEquals(0, ServerChunkedPayloadAssembler.getActiveBufferCount());

        byte[] outOfBoundsResult = ServerChunkedPayloadAssembler.appendChunk(transferId, 5, 5, payload);
        Assertions.assertNull(outOfBoundsResult);
        Assertions.assertEquals(0, ServerChunkedPayloadAssembler.getActiveBufferCount());
    }

    @Test
    @DisplayName("RFC-052: Reject single chunk exceeding MAX_CHUNK_PAYLOAD_SIZE (strict 512KB cap)")
    void testOversizedChunkRejected() {
        UUID transferId = UUID.randomUUID();
        int oversized = ServerChunkedPayloadAssembler.MAX_CHUNK_PAYLOAD_SIZE + 1;
        byte[] payload = new byte[oversized];

        byte[] result = ServerChunkedPayloadAssembler.appendChunk(transferId, 0, 1, payload);

        Assertions.assertNull(result);
        Assertions.assertEquals(0, ServerChunkedPayloadAssembler.getActiveBufferCount());
        Assertions.assertFalse(ServerChunkedPayloadAssembler.hasActiveTransfer(transferId));

        byte[] exactLimit = new byte[ServerChunkedPayloadAssembler.MAX_CHUNK_PAYLOAD_SIZE];
        byte[] exactResult = ServerChunkedPayloadAssembler.appendChunk(UUID.randomUUID(), 0, 1, exactLimit);
        Assertions.assertNotNull(exactResult);
        Assertions.assertEquals(ServerChunkedPayloadAssembler.MAX_CHUNK_PAYLOAD_SIZE, exactResult.length);
    }

    @Test
    @DisplayName("RFC-052: Normal multi-chunk assembly out of order")
    void testValidStreamingAssembly() {
        UUID transferId = UUID.randomUUID();
        int totalSize = 2500 * 1024;
        byte[] original = new byte[totalSize];
        new Random(42).nextBytes(original);

        int chunkSize = ServerChunkedPayloadAssembler.MAX_CHUNK_PAYLOAD_SIZE;
        int totalChunks = (int) Math.ceil((double) totalSize / chunkSize);

        List<byte[]> parts = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(totalSize, start + chunkSize);
            parts.add(Arrays.copyOfRange(original, start, end));
        }

        int[] order = {2, 0, 4, 1, 3};
        byte[] assembled = null;
        for (int idx : order) {
            byte[] res = ServerChunkedPayloadAssembler.appendChunk(transferId, idx, totalChunks, parts.get(idx));
            if (idx == 3) {
                assembled = res;
            } else {
                Assertions.assertNull(res);
                Assertions.assertTrue(ServerChunkedPayloadAssembler.hasActiveTransfer(transferId));
            }
        }

        Assertions.assertNotNull(assembled);
        Assertions.assertArrayEquals(original, assembled);
        Assertions.assertEquals(0, ServerChunkedPayloadAssembler.getActiveBufferCount());
        Assertions.assertFalse(ServerChunkedPayloadAssembler.hasActiveTransfer(transferId));
    }

    @Test
    @DisplayName("RFC-052: Concurrent sessions capped at MAX_ACTIVE_TRANSFERS and recovers on completion")
    void testMaxConcurrentSessionsGuarded() {
        byte[] dummyChunk = new byte[16];
        List<UUID> activeIds = new ArrayList<>();

        for (int i = 0; i < ServerChunkedPayloadAssembler.MAX_ACTIVE_TRANSFERS; i++) {
            UUID tid = UUID.randomUUID();
            activeIds.add(tid);
            ServerChunkedPayloadAssembler.appendChunk(tid, 0, 2, dummyChunk);
        }
        Assertions.assertEquals(ServerChunkedPayloadAssembler.MAX_ACTIVE_TRANSFERS, ServerChunkedPayloadAssembler.getActiveBufferCount());

        UUID extraId = UUID.randomUUID();
        byte[] rejected = ServerChunkedPayloadAssembler.appendChunk(extraId, 0, 2, dummyChunk);
        Assertions.assertNull(rejected);
        Assertions.assertFalse(ServerChunkedPayloadAssembler.hasActiveTransfer(extraId));
        Assertions.assertEquals(ServerChunkedPayloadAssembler.MAX_ACTIVE_TRANSFERS, ServerChunkedPayloadAssembler.getActiveBufferCount());

        UUID completedId = activeIds.get(0);
        byte[] completed = ServerChunkedPayloadAssembler.appendChunk(completedId, 1, 2, dummyChunk);
        Assertions.assertNotNull(completed);
        Assertions.assertEquals(ServerChunkedPayloadAssembler.MAX_ACTIVE_TRANSFERS - 1, ServerChunkedPayloadAssembler.getActiveBufferCount());

        byte[] acceptedNow = ServerChunkedPayloadAssembler.appendChunk(extraId, 0, 2, dummyChunk);
        Assertions.assertNull(acceptedNow);
        Assertions.assertTrue(ServerChunkedPayloadAssembler.hasActiveTransfer(extraId));
        Assertions.assertEquals(ServerChunkedPayloadAssembler.MAX_ACTIVE_TRANSFERS, ServerChunkedPayloadAssembler.getActiveBufferCount());
    }

    @Test
    @DisplayName("RFC-052: Tampered duplicate chunk drops transfer session")
    void testTamperedDuplicateChunkDropsSession() {
        UUID transferId = UUID.randomUUID();
        byte[] chunkA = new byte[]{1, 2, 3};
        byte[] chunkATampered = new byte[]{1, 2, 4};

        ServerChunkedPayloadAssembler.appendChunk(transferId, 0, 3, chunkA);
        Assertions.assertTrue(ServerChunkedPayloadAssembler.hasActiveTransfer(transferId));

        byte[] result = ServerChunkedPayloadAssembler.appendChunk(transferId, 0, 3, chunkATampered);
        Assertions.assertNull(result);
        Assertions.assertFalse(ServerChunkedPayloadAssembler.hasActiveTransfer(transferId));
        Assertions.assertEquals(0, ServerChunkedPayloadAssembler.getActiveBufferCount());
    }

    @Test
    @DisplayName("RFC-052: Inconsistent totalChunks in existing stream drops transfer")
    void testMismatchedTotalChunksDropsSession() {
        UUID transferId = UUID.randomUUID();
        byte[] dummyChunk = new byte[16];

        ServerChunkedPayloadAssembler.appendChunk(transferId, 0, 5, dummyChunk);
        Assertions.assertEquals(1, ServerChunkedPayloadAssembler.getActiveBufferCount());

        byte[] result = ServerChunkedPayloadAssembler.appendChunk(transferId, 1, 6, dummyChunk);
        Assertions.assertNull(result);
        Assertions.assertEquals(0, ServerChunkedPayloadAssembler.getActiveBufferCount());
    }

    @Test
    @DisplayName("RFC-052: Null parameters safely rejected")
    void testNullParametersHandledSafely() {
        Assertions.assertNull(ServerChunkedPayloadAssembler.appendChunk(null, 0, 1, new byte[10]));
        Assertions.assertNull(ServerChunkedPayloadAssembler.appendChunk(UUID.randomUUID(), 0, 1, null));
        Assertions.assertFalse(ServerChunkedPayloadAssembler.validateChunkParameters(null, 0, 1, new byte[10]));
        Assertions.assertFalse(ServerChunkedPayloadAssembler.validateChunkParameters(UUID.randomUUID(), 0, 1, null));
        Assertions.assertFalse(ServerChunkedPayloadAssembler.hasActiveTransfer(null));
    }
}
