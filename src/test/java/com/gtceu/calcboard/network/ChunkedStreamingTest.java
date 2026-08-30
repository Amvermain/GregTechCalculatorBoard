package com.gtceu.calcboard.network;

import com.gtceu.calcboard.client.team.ChunkedPayloadAssembler;
import com.gtceu.calcboard.network.packet.s2c.S2CChunkedDataPacket;
import com.gtceu.calcboard.network.packet.s2c.S2CSyncWorkspaceMetaPacket;
import com.gtceu.calcboard.server.storage.ChunkedStreamHelper;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * Verifies RFC-004: Chunked Streaming, TTL Buffer Assembler, and Lightweight Metadata Packet.
 */
public class ChunkedStreamingTest {

    @BeforeEach
    void setUp() {
        ChunkedPayloadAssembler.clear();
    }

    @Test
    @DisplayName("RFC-004: Single chunk payload assembler reassembly")
    void testSingleChunkReassembly() {
        UUID transferId = UUID.randomUUID();
        byte[] original = "Hello GTCalcBoard Chunk Streaming!".getBytes();

        byte[] assembled = ChunkedPayloadAssembler.appendChunk(transferId, 0, 1, original);
        Assertions.assertNotNull(assembled);
        Assertions.assertArrayEquals(original, assembled);
    }

    @Test
    @DisplayName("RFC-004: Multi-chunk 2MB large payload streaming & out-of-order reassembly")
    void testMultiChunkReassembly() {
        UUID transferId = UUID.randomUUID();
        int totalSize = 2 * 1024 * 1024; // 2 MB
        byte[] original = new byte[totalSize];
        new Random(42).nextBytes(original);

        int chunkSize = ChunkedStreamHelper.MAX_CHUNK_PAYLOAD_SIZE; // 512 KB
        int totalChunks = (int) Math.ceil((double) totalSize / chunkSize); // 4 chunks
        Assertions.assertEquals(4, totalChunks);

        // Split into chunks
        List<byte[]> chunks = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(totalSize, start + chunkSize);
            chunks.add(Arrays.copyOfRange(original, start, end));
        }

        // Send out-of-order: chunk 2, 0, 3, 1
        int[] order = {2, 0, 3, 1};
        byte[] result = null;
        for (int idx : order) {
            byte[] res = ChunkedPayloadAssembler.appendChunk(transferId, idx, totalChunks, chunks.get(idx));
            if (res != null) {
                result = res;
            }
        }

        Assertions.assertNotNull(result, "All 4 chunks were supplied, result should not be null");
        Assertions.assertEquals(totalSize, result.length);
        Assertions.assertArrayEquals(original, result);
    }

    @Test
    @DisplayName("RFC-004: S2CSyncWorkspaceMetaPacket byte buffer encode & decode")
    void testWorkspaceMetaPacketSerialization() {
        UUID teamId = UUID.randomUUID();
        UUID holderUUID = UUID.randomUUID();
        List<S2CSyncWorkspaceMetaPacket.PageMeta> pages = List.of(
                new S2CSyncWorkspaceMetaPacket.PageMeta("page-1", "Petrochemical Phase 1", 3, holderUUID, "PlayerA", System.currentTimeMillis() + 60000L),
                new S2CSyncWorkspaceMetaPacket.PageMeta("page-2", "PGM Refining", 1, null, "", 0L)
        );

        S2CSyncWorkspaceMetaPacket packet = new S2CSyncWorkspaceMetaPacket(teamId, "Mega Base Alpha", 15, pages);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buf);

        S2CSyncWorkspaceMetaPacket decoded = new S2CSyncWorkspaceMetaPacket(buf);
        Assertions.assertEquals(teamId, decoded.getTeamId());
        Assertions.assertEquals("Mega Base Alpha", decoded.getTeamName());
        Assertions.assertEquals(15, decoded.getGlobalRevision());
        Assertions.assertEquals(2, decoded.getPages().size());

        S2CSyncWorkspaceMetaPacket.PageMeta p1 = decoded.getPages().get(0);
        Assertions.assertEquals("page-1", p1.getPageId());
        Assertions.assertEquals("Petrochemical Phase 1", p1.getTitle());
        Assertions.assertEquals(3, p1.getRevision());
        Assertions.assertEquals(holderUUID, p1.getLockHolderUUID());
        Assertions.assertEquals("PlayerA", p1.getLockHolderName());

        S2CSyncWorkspaceMetaPacket.PageMeta p2 = decoded.getPages().get(1);
        Assertions.assertEquals("page-2", p2.getPageId());
        Assertions.assertNull(p2.getLockHolderUUID());
    }

    @Test
    @DisplayName("RFC-004: S2CChunkedDataPacket byte buffer encode & decode")
    void testChunkedDataPacketSerialization() {
        UUID transferId = UUID.randomUUID();
        byte[] chunk = new byte[]{10, 20, 30, 40, 50};
        S2CChunkedDataPacket packet = new S2CChunkedDataPacket(transferId, "page-test", 4, 1, 3, chunk);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buf);

        S2CChunkedDataPacket decoded = new S2CChunkedDataPacket(buf);
        Assertions.assertEquals(transferId, decoded.getTransferId());
        Assertions.assertEquals("page-test", decoded.getPageId());
        Assertions.assertEquals(4, decoded.getRevision());
        Assertions.assertEquals(1, decoded.getChunkIndex());
        Assertions.assertEquals(3, decoded.getTotalChunks());
        Assertions.assertArrayEquals(chunk, decoded.getChunkBytes());
    }
}
