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
        S2CSyncWorkspaceMetaPacket.STREAM_CODEC.encode(buf, packet);

        S2CSyncWorkspaceMetaPacket decoded = S2CSyncWorkspaceMetaPacket.STREAM_CODEC.decode(buf);
        Assertions.assertEquals(teamId, decoded.teamId());
        Assertions.assertEquals("Mega Base Alpha", decoded.teamName());
        Assertions.assertEquals(15, decoded.globalRevision());
        Assertions.assertEquals(2, decoded.pages().size());

        S2CSyncWorkspaceMetaPacket.PageMeta p1 = decoded.pages().get(0);
        Assertions.assertEquals("page-1", p1.pageId());
        Assertions.assertEquals("Petrochemical Phase 1", p1.title());
        Assertions.assertEquals(3, p1.revision());
        Assertions.assertEquals(holderUUID, p1.lockHolderUUID());
        Assertions.assertEquals("PlayerA", p1.lockHolderName());

        S2CSyncWorkspaceMetaPacket.PageMeta p2 = decoded.pages().get(1);
        Assertions.assertEquals("page-2", p2.pageId());
        Assertions.assertNull(p2.lockHolderUUID());
    }

    @Test
    @DisplayName("RFC-004: S2CChunkedDataPacket byte buffer encode & decode")
    void testChunkedDataPacketSerialization() {
        UUID transferId = UUID.randomUUID();
        byte[] chunk = new byte[]{10, 20, 30, 40, 50};
        S2CChunkedDataPacket packet = new S2CChunkedDataPacket(transferId, "page-test", 4, 1, 3, chunk);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        S2CChunkedDataPacket.STREAM_CODEC.encode(buf, packet);

        S2CChunkedDataPacket decoded = S2CChunkedDataPacket.STREAM_CODEC.decode(buf);
        Assertions.assertEquals(transferId, decoded.transferId());
        Assertions.assertEquals("page-test", decoded.pageId());
        Assertions.assertEquals(4, decoded.revision());
        Assertions.assertEquals(1, decoded.chunkIndex());
        Assertions.assertEquals(3, decoded.totalChunks());
        Assertions.assertArrayEquals(chunk, decoded.chunkBytes());
    }
}
