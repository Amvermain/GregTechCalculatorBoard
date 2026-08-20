package com.gtceu.calcboard;

import com.gtceu.calcboard.server.storage.CommitLogEntry;
import com.gtceu.calcboard.server.storage.TeamBoardSavedData;
import com.gtceu.calcboard.server.storage.TeamWorkspaceData;
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TeamBoardStorageTest {

    @Test
    public void testTeamWorkspacePageSerialization() {
        String pageId = "page_petrochem";
        String title = "Petrochemical & Plastics";
        byte[] dummyGraphData = "dummy_compressed_nbt_stream".getBytes(StandardCharsets.UTF_8);

        TeamWorkspacePage page = new TeamWorkspacePage(pageId, title, 3, dummyGraphData);
        UUID lockHolder = UUID.randomUUID();
        long expireTime = System.currentTimeMillis() + 60000L;
        page.setLockHolderUUID(lockHolder);
        page.setLockExpiresTimestamp(expireTime);

        CompoundTag nbt = page.toNBT();
        TeamWorkspacePage restored = TeamWorkspacePage.fromNBT(nbt);

        assertEquals(pageId, restored.getPageId());
        assertEquals(title, restored.getTitle());
        assertEquals(3, restored.getPageRevision());
        assertEquals(lockHolder, restored.getLockHolderUUID());
        assertEquals(expireTime, restored.getLockExpiresTimestamp());
        assertArrayEquals(dummyGraphData, restored.getCompressedGraphData());
    }

    @Test
    public void testCommitLogEntrySerialization() {
        int rev = 42;
        UUID author = UUID.randomUUID();
        String authorName = "StarEngineer";
        long timestamp = System.currentTimeMillis();
        String pageId = "page_main";
        String msg = "Scaled ethylene line to 16x parallel";

        CommitLogEntry entry = new CommitLogEntry(rev, author, authorName, timestamp, pageId, msg, 4, 2, 1);
        CompoundTag nbt = entry.toNBT();
        CommitLogEntry restored = CommitLogEntry.fromNBT(nbt);

        assertEquals(rev, restored.getRevision());
        assertEquals(author, restored.getAuthorUUID());
        assertEquals(authorName, restored.getAuthorName());
        assertEquals(timestamp, restored.getTimestamp());
        assertEquals(pageId, restored.getPageId());
        assertEquals(msg, restored.getMessage());
        assertEquals(4, restored.getAddedNodes());
        assertEquals(2, restored.getModifiedNodes());
        assertEquals(1, restored.getDeletedNodes());
    }

    @Test
    public void testTeamWorkspaceDataSerialization() {
        UUID teamId = UUID.randomUUID();
        String teamName = "Star Pioneers";

        TeamWorkspaceData ws = new TeamWorkspaceData(teamId, teamName);
        assertEquals(1, ws.getPages().size()); // default main page

        // Add extra page
        TeamWorkspacePage page2 = new TeamWorkspacePage("page_titanium", "Titanium Processing", 1, "data".getBytes(StandardCharsets.UTF_8));
        ws.addOrUpdatePage(page2);
        assertEquals(2, ws.getPages().size());

        // Add commit history
        CommitLogEntry commit = new CommitLogEntry(1, UUID.randomUUID(), "Alice", System.currentTimeMillis(), "page_titanium", "Initial commit", 3, 0, 0);
        ws.addCommit(commit);

        CompoundTag nbt = ws.toNBT();
        TeamWorkspaceData restored = TeamWorkspaceData.fromNBT(nbt);

        assertEquals(teamId, restored.getTeamId());
        assertEquals(teamName, restored.getTeamName());
        assertEquals(2, restored.getPages().size());
        assertNotNull(restored.getPage("page_titanium"));
        assertEquals("Titanium Processing", restored.getPage("page_titanium").getTitle());
        assertEquals(1, restored.getCommitHistory().size());
        assertEquals("Initial commit", restored.getCommitHistory().get(0).getMessage());
    }

    @Test
    public void testTeamBoardSavedDataRoundTrip() {
        TeamBoardSavedData savedData = new TeamBoardSavedData();
        UUID team1 = UUID.randomUUID();
        UUID team2 = UUID.randomUUID();

        TeamWorkspaceData ws1 = savedData.getOrCreateWorkspace(team1, "Team 1");
        TeamWorkspaceData ws2 = savedData.getOrCreateWorkspace(team2, "Team 2");

        ws1.addOrUpdatePage(new TeamWorkspacePage("p1", "Page 1"));
        ws2.addOrUpdatePage(new TeamWorkspacePage("p2", "Page 2"));

        CompoundTag nbt = savedData.save(new CompoundTag());
        TeamBoardSavedData loaded = TeamBoardSavedData.load(nbt);

        assertNotNull(loaded.getWorkspace(team1));
        assertNotNull(loaded.getWorkspace(team2));
        assertEquals("Team 1", loaded.getWorkspace(team1).getTeamName());
        assertEquals("Team 2", loaded.getWorkspace(team2).getTeamName());
    }
}
