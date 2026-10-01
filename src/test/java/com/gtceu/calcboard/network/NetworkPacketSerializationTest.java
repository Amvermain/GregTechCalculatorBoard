package com.gtceu.calcboard.network;

import com.gtceu.calcboard.network.packet.c2s.*;
import com.gtceu.calcboard.network.packet.s2c.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/**
 * Unit tests verifying byte-level serialization and deserialization integrity
 * for Client-to-Server (C2S) and Server-to-Client (S2C) network packets.
 */
public class NetworkPacketSerializationTest {

    @Test
    @DisplayName("C2SAcquireLockPacket serialization roundtrip")
    void testC2SAcquireLockPacketRoundtrip() {
        UUID teamId = UUID.randomUUID();
        String pageId = "page_ore_processing";
        C2SAcquireLockPacket original = new C2SAcquireLockPacket(teamId, pageId);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.encode(buf);

        C2SAcquireLockPacket decoded = new C2SAcquireLockPacket(buf);
        Assertions.assertEquals(original.getTeamId(), decoded.getTeamId());
        Assertions.assertEquals(original.getPageId(), decoded.getPageId());
    }

    @Test
    @DisplayName("C2SReleaseLockPacket serialization roundtrip")
    void testC2SReleaseLockPacketRoundtrip() {
        UUID teamId = UUID.randomUUID();
        String pageId = "page_distillation";
        C2SReleaseLockPacket original = new C2SReleaseLockPacket(teamId, pageId);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.encode(buf);

        C2SReleaseLockPacket decoded = new C2SReleaseLockPacket(buf);
        Assertions.assertEquals(original.getTeamId(), decoded.getTeamId());
        Assertions.assertEquals(original.getPageId(), decoded.getPageId());
    }

    @Test
    @DisplayName("C2SRequestWorkspacePacket serialization roundtrip")
    void testC2SRequestWorkspacePacketRoundtrip() {
        UUID teamId = UUID.randomUUID();
        String pageId = "page_catalyst_loop";
        C2SRequestWorkspacePacket original = new C2SRequestWorkspacePacket(teamId, pageId);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.encode(buf);

        C2SRequestWorkspacePacket decoded = new C2SRequestWorkspacePacket(buf);
        Assertions.assertEquals(teamId, decoded.getTeamId());
        Assertions.assertEquals(pageId, decoded.getRequestedPageId());
    }

    @Test
    @DisplayName("S2CLockResultPacket serialization roundtrip")
    void testS2CLockResultPacketRoundtrip() {
        String pageId = "page_turbine";
        UUID holderUUID = UUID.randomUUID();
        String holderName = "PlayerOne";
        long expires = System.currentTimeMillis() + 60000L;
        S2CLockResultPacket original = new S2CLockResultPacket(pageId, true, holderUUID, holderName, expires);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.encode(buf);

        S2CLockResultPacket decoded = new S2CLockResultPacket(buf);
        Assertions.assertEquals(original.getPageId(), decoded.getPageId());
        Assertions.assertTrue(decoded.isSuccess());
        Assertions.assertEquals(holderUUID, decoded.getLockHolderUUID());
        Assertions.assertEquals(holderName, decoded.getLockHolderName());
        Assertions.assertEquals(expires, decoded.getExpiresTimestamp());
    }

    @Test
    @DisplayName("S2CSyncWorkspaceMetaPacket serialization roundtrip")
    void testS2CSyncWorkspaceMetaPacketRoundtrip() {
        UUID teamId = UUID.randomUUID();
        String teamName = "StarTech Team";
        int rev = 42;
        var page1 = new S2CSyncWorkspaceMetaPacket.PageMeta("page_1", "Benzene Flow", 10, UUID.randomUUID(), "Alice", 1000L, "Petro/Benzene");
        var page2 = new S2CSyncWorkspaceMetaPacket.PageMeta("page_2", "Polymer Hub", 5, null, "", 0L, "Petro/Polymers");
        S2CSyncWorkspaceMetaPacket original = new S2CSyncWorkspaceMetaPacket(teamId, teamName, rev, List.of(page1, page2));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.encode(buf);

        S2CSyncWorkspaceMetaPacket decoded = new S2CSyncWorkspaceMetaPacket(buf);
        Assertions.assertEquals(teamId, decoded.getTeamId());
        Assertions.assertEquals(teamName, decoded.getTeamName());
        Assertions.assertEquals(rev, decoded.getGlobalRevision());
        Assertions.assertEquals(2, decoded.getPages().size());

        var d1 = decoded.getPages().get(0);
        Assertions.assertEquals("page_1", d1.getPageId());
        Assertions.assertEquals("Benzene Flow", d1.getTitle());
        Assertions.assertEquals("Petro/Benzene", d1.getFolderPath());
        Assertions.assertEquals(10, d1.getRevision());
        Assertions.assertEquals(page1.getLockHolderUUID(), d1.getLockHolderUUID());

        var d2 = decoded.getPages().get(1);
        Assertions.assertEquals("page_2", d2.getPageId());
        Assertions.assertEquals("Polymer Hub", d2.getTitle());
        Assertions.assertEquals("Petro/Polymers", d2.getFolderPath());
        Assertions.assertEquals(5, d2.getRevision());
        Assertions.assertNull(d2.getLockHolderUUID());
    }

    @Test
    @DisplayName("S2CSyncWorkspaceMetaPacket backward compatibility with legacy 2.4.0 servers")
    void testS2CSyncWorkspaceMetaPacketLegacyServerCompatibility() {
        UUID teamId = UUID.randomUUID();
        String teamName = "StarTech Team";
        int rev = 42;
        FriendlyByteBuf legacyBuf = new FriendlyByteBuf(Unpooled.buffer());
        legacyBuf.writeUUID(teamId);
        legacyBuf.writeUtf(teamName);
        legacyBuf.writeVarInt(rev);
        legacyBuf.writeVarInt(2); // 2 pages

        // Page 1 (Legacy format: pageId, title, revision, hasLock, (uuid), lockName, lockExpires)
        legacyBuf.writeUtf("page_1");
        legacyBuf.writeUtf("Benzene Flow");
        legacyBuf.writeVarInt(10);
        legacyBuf.writeBoolean(false);
        legacyBuf.writeUtf("");
        legacyBuf.writeLong(0L);

        // Page 2
        legacyBuf.writeUtf("page_2");
        legacyBuf.writeUtf("Polymer Hub");
        legacyBuf.writeVarInt(5);
        legacyBuf.writeBoolean(false);
        legacyBuf.writeUtf("");
        legacyBuf.writeLong(0L);

        // No trailing folder paths (simulating older server payload)
        Assertions.assertDoesNotThrow(() -> {
            S2CSyncWorkspaceMetaPacket decoded = new S2CSyncWorkspaceMetaPacket(legacyBuf);
            Assertions.assertEquals(2, decoded.getPages().size());
            Assertions.assertEquals("", decoded.getPages().get(0).getFolderPath());
            Assertions.assertEquals("", decoded.getPages().get(1).getFolderPath());
        });
    }

    @Test
    @DisplayName("S2CSyncWorkspacePacket roundtrip with folder paths")
    void testS2CSyncWorkspacePacketRoundtripWithFolders() {
        UUID teamId = UUID.randomUUID();
        var page = new com.gtceu.calcboard.api.team.TeamWorkspacePage("page_sub", "Sub Power", "Power/LV", 3, new byte[]{4, 5, 6});
        var commit = new com.gtceu.calcboard.api.team.CommitLogEntry(1, null, "Bob", 777L, "page_sub", "Updated", 2, 1, 0);

        S2CSyncWorkspacePacket original = new S2CSyncWorkspacePacket(teamId, "Team Alpha", 10, List.of(page), List.of(commit));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.encode(buf);

        S2CSyncWorkspacePacket decoded = new S2CSyncWorkspacePacket(buf);
        Assertions.assertEquals(1, decoded.getPages().size());
        Assertions.assertEquals("Sub Power", decoded.getPages().get(0).getTitle());
        Assertions.assertEquals("Power/LV", decoded.getPages().get(0).getFolderPath());
        Assertions.assertEquals(1, decoded.getCommits().size());
    }

    @Test
    @DisplayName("S2CSyncWorkspacePacket backward compatibility with legacy 2.4.0 servers")
    void testS2CSyncWorkspacePacketLegacyServerCompatibility() {
        UUID teamId = UUID.randomUUID();
        String teamName = "StarTech Team";
        int rev = 7;
        FriendlyByteBuf legacyBuf = new FriendlyByteBuf(Unpooled.buffer());
        legacyBuf.writeUUID(teamId);
        legacyBuf.writeUtf(teamName);
        legacyBuf.writeVarInt(rev);
        legacyBuf.writeVarInt(1); // 1 page

        // Page 1: pageId, title, rev, hasLock, data
        legacyBuf.writeUtf("page_1");
        legacyBuf.writeUtf("Power Grid");
        legacyBuf.writeVarInt(2);
        legacyBuf.writeBoolean(false);
        legacyBuf.writeByteArray(new byte[]{1, 2, 3});

        // 1 commit
        legacyBuf.writeVarInt(1);
        legacyBuf.writeVarInt(1); // commit rev
        legacyBuf.writeBoolean(false); // hasAuthor
        legacyBuf.writeUtf("Alice"); // authorName
        legacyBuf.writeLong(123456L); // ts
        legacyBuf.writeUtf("page_1"); // pageId
        legacyBuf.writeUtf("Initial"); // msg
        legacyBuf.writeVarInt(1); // add
        legacyBuf.writeVarInt(0); // mod
        legacyBuf.writeVarInt(0); // del

        // No trailing folder paths
        Assertions.assertDoesNotThrow(() -> {
            S2CSyncWorkspacePacket decoded = new S2CSyncWorkspacePacket(legacyBuf);
            Assertions.assertEquals(1, decoded.getPages().size());
            Assertions.assertEquals("Power Grid", decoded.getPages().get(0).getTitle());
            Assertions.assertEquals("", decoded.getPages().get(0).getFolderPath());
            Assertions.assertEquals(1, decoded.getCommits().size());
        });
    }

    @Test
    @DisplayName("S2CSyncPageDataPacket serialization roundtrip")
    void testS2CSyncPageDataPacketRoundtrip() {
        String pageId = "page_steam_grid";
        int rev = 15;
        byte[] data = new byte[]{1, 2, 3, 4, 5, 42, 99};
        S2CSyncPageDataPacket original = new S2CSyncPageDataPacket(pageId, rev, data);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.encode(buf);

        S2CSyncPageDataPacket decoded = new S2CSyncPageDataPacket(buf);
        Assertions.assertEquals(pageId, decoded.getPageId());
        Assertions.assertEquals(rev, decoded.getRevision());
        Assertions.assertArrayEquals(data, decoded.getCompressedNBT());
    }

    @Test
    @DisplayName("S2CChunkedDataPacket serialization roundtrip")
    void testS2CChunkedDataPacketRoundtrip() {
        UUID transferId = UUID.randomUUID();
        String pageId = "page_heavy_nbt";
        int rev = 99;
        int chunkIdx = 3;
        int totalChunks = 8;
        byte[] payload = new byte[]{10, 20, 30, 40};
        S2CChunkedDataPacket original = new S2CChunkedDataPacket(transferId, pageId, rev, chunkIdx, totalChunks, payload);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.encode(buf);

        S2CChunkedDataPacket decoded = new S2CChunkedDataPacket(buf);
        Assertions.assertEquals(transferId, decoded.getTransferId());
        Assertions.assertEquals(pageId, decoded.getPageId());
        Assertions.assertEquals(rev, decoded.getRevision());
        Assertions.assertEquals(chunkIdx, decoded.getChunkIndex());
        Assertions.assertEquals(totalChunks, decoded.getTotalChunks());
        Assertions.assertArrayEquals(payload, decoded.getChunkBytes());
    }

    @Test
    @DisplayName("S2CWorkspaceErrorPacket serialization roundtrip")
    void testS2CWorkspaceErrorPacketRoundtrip() {
        int errorCode = 409;
        String key = "gui.gtcalcboard.error.revision_conflict";
        S2CWorkspaceErrorPacket original = new S2CWorkspaceErrorPacket(errorCode, key);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.encode(buf);

        S2CWorkspaceErrorPacket decoded = new S2CWorkspaceErrorPacket(buf);
        Assertions.assertEquals(errorCode, decoded.getErrorCode());
        Assertions.assertEquals(key, decoded.getMessageKey());
    }
}
