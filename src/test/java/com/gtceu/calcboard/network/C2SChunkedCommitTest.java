package com.gtceu.calcboard.network;

import com.gtceu.calcboard.client.team.ClientChunkedStreamHelper;
import com.gtceu.calcboard.network.packet.c2s.C2SChunkedCommitPacket;
import com.gtceu.calcboard.server.storage.ServerChunkedPayloadAssembler;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * Verifies RFC-003: C2S Chunked Commit Protocol, Server Reassembly, and Netty buffer safety.
 */
public class C2SChunkedCommitTest {

    @BeforeEach
    void setUp() {
        ServerChunkedPayloadAssembler.clear();
    }

    @Test
    @DisplayName("RFC-003: Single chunk C2S payload assembler reassembly")
    void testSingleChunkReassembly() {
        UUID transferId = UUID.randomUUID();
        byte[] original = "RFC-003 Client to Server Single Chunk Commit".getBytes();

        byte[] assembled = ServerChunkedPayloadAssembler.appendChunk(transferId, 0, 1, original);
        Assertions.assertNotNull(assembled);
        Assertions.assertArrayEquals(original, assembled);
    }

    @Test
    @DisplayName("RFC-003: Multi-chunk 2.5MB large payload streaming & out-of-order reassembly on server")
    void testMultiChunkReassembly() {
        UUID transferId = UUID.randomUUID();
        int totalSize = (int) (2.5 * 1024 * 1024); // 2.5 MB
        byte[] original = new byte[totalSize];
        new Random(777).nextBytes(original);

        int chunkSize = ClientChunkedStreamHelper.MAX_CHUNK_PAYLOAD_SIZE; // 512 KB
        int totalChunks = (int) Math.ceil((double) totalSize / chunkSize); // 5 chunks
        Assertions.assertEquals(5, totalChunks);

        // Split into chunks
        List<byte[]> chunks = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(totalSize, start + chunkSize);
            chunks.add(Arrays.copyOfRange(original, start, end));
        }

        // Send out-of-order: chunk 3, 1, 4, 0, 2
        int[] order = {3, 1, 4, 0, 2};
        byte[] result = null;
        for (int idx : order) {
            byte[] assembled = ServerChunkedPayloadAssembler.appendChunk(transferId, idx, totalChunks, chunks.get(idx));
            if (idx == 2) {
                // Last chunk in order sequence
                result = assembled;
            } else {
                Assertions.assertNull(assembled, "Assembler should return null for incomplete chunks at index: " + idx);
            }
        }

        Assertions.assertNotNull(result, "Assembler must return complete data when all chunks arrive");
        Assertions.assertEquals(totalSize, result.length);
        Assertions.assertArrayEquals(original, result);
    }

    @Test
    @DisplayName("RFC-003: C2SChunkedCommitPacket encode and decode consistency")
    void testC2SChunkedCommitPacketSerialization() {
        UUID transferId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        String pageId = "page_rocket_fuel";
        String pageTitle = "Rocket Fuel Processing";
        int revision = 42;
        String commitMsg = "Optimized cryogenic oxygen loop";
        byte[] chunk = "ChunkPayloadDataBytes".getBytes();

        C2SChunkedCommitPacket packet = new C2SChunkedCommitPacket(
                transferId, teamId, pageId, pageTitle, revision, commitMsg, 1, 3, chunk, 5, 2, 1
        );

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        C2SChunkedCommitPacket.STREAM_CODEC.encode(buf, packet);

        C2SChunkedCommitPacket decoded = C2SChunkedCommitPacket.STREAM_CODEC.decode(buf);
        Assertions.assertEquals(transferId, decoded.transferId());
        Assertions.assertEquals(teamId, decoded.teamId());
        Assertions.assertEquals(pageId, decoded.pageId());
        Assertions.assertEquals(pageTitle, decoded.pageTitle());
        Assertions.assertEquals(revision, decoded.revision());
        Assertions.assertEquals(commitMsg, decoded.commitMessage());
        Assertions.assertEquals(1, decoded.chunkIndex());
        Assertions.assertEquals(3, decoded.totalChunks());
        Assertions.assertArrayEquals(chunk, decoded.chunkData());
        Assertions.assertEquals(5, decoded.addedNodes());
        Assertions.assertEquals(2, decoded.modifiedNodes());
        Assertions.assertEquals(1, decoded.deletedNodes());
    }
}
