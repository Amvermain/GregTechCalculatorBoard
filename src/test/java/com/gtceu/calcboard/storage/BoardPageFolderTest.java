package com.gtceu.calcboard.storage;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BoardPageFolderTest {

    @BeforeEach
    public void setup() {
        BoardManager.getInstance().resetToDefault();
    }

    @Test
    public void testBoardPageFolderAndPinSerialization() {
        BoardPage page = new BoardPage("test-id", "Titanium Refining", new FlowGraph());
        page.setFolderPath("Metals/Refining");
        page.setPinned(false);
        page.setFolderCollapsed(true);

        CompoundTag tag = page.serializeNBT();
        BoardPage loaded = BoardPage.deserializeNBT(tag);

        assertEquals("test-id", loaded.getId());
        assertEquals("Titanium Refining", loaded.getName());
        assertEquals("Metals/Refining", loaded.getFolderPath());
        assertFalse(loaded.isPinned());
        assertTrue(loaded.isFolderCollapsed());
    }

    @Test
    public void testLegacyBoardPageDeserializationDefaults() {
        CompoundTag legacyTag = new CompoundTag();
        legacyTag.putString("id", "legacy-id");
        legacyTag.putString("name", "Legacy Page");

        BoardPage loaded = BoardPage.deserializeNBT(legacyTag);
        assertEquals("legacy-id", loaded.getId());
        assertEquals("Legacy Page", loaded.getName());
        assertEquals("", loaded.getFolderPath());
        assertTrue(loaded.isPinned()); // Defaults to true
        assertFalse(loaded.isFolderCollapsed()); // Defaults to false
    }

    @Test
    public void testBoardManagerFolderOperations() {
        BoardManager bm = BoardManager.getInstance();
        bm.getPages().clear();

        BoardPage p1 = bm.addPage("Iron Ingot", "Metals/Smelting");
        BoardPage p2 = bm.addPage("Titanium Ingot", "Metals/Refining");
        BoardPage p3 = bm.addPage("Tungsten Ingot", "Metals/Refining");
        BoardPage p4 = bm.addPage("EV Circuit", "Electronics");
        BoardPage p5 = bm.addPage("Main Base", "");

        List<String> folders = bm.getAllFolders();
        assertEquals(3, folders.size());
        assertTrue(folders.contains("Electronics"));
        assertTrue(folders.contains("Metals/Refining"));
        assertTrue(folders.contains("Metals/Smelting"));

        List<BoardPage> refiningPages = bm.getPagesInFolder("Metals/Refining");
        assertEquals(2, refiningPages.size());
        assertTrue(refiningPages.contains(p2));
        assertTrue(refiningPages.contains(p3));

        // Move p1 to Metals/Refining
        bm.movePageToFolder(0, "Metals/Refining");
        assertEquals("Metals/Refining", p1.getFolderPath());
        assertEquals(3, bm.getPagesInFolder("Metals/Refining").size());

        // Rename folder Metals -> Factory/Metals
        bm.renameFolder("Metals", "Factory/Metals");
        assertEquals("Factory/Metals/Refining", p1.getFolderPath());
        assertEquals("Factory/Metals/Refining", p2.getFolderPath());
        assertEquals("Factory/Metals/Refining", p3.getFolderPath());

        // Delete folder Factory/Metals
        bm.deleteFolder("Factory/Metals");
        assertEquals("", p1.getFolderPath());
        assertEquals("", p2.getFolderPath());
        assertEquals("", p3.getFolderPath());
    }

    @Test
    public void testMultiPageBatchMoveAndPinOperations() {
        BoardManager bm = BoardManager.getInstance();
        bm.getPages().clear();

        BoardPage p1 = bm.addPage("Page 1", "Folder A");
        BoardPage p2 = bm.addPage("Page 2", "Folder A");
        BoardPage p3 = bm.addPage("Page 3", "Folder B");

        List<BoardPage> batch = List.of(p1, p2);
        for (BoardPage p : batch) {
            p.setFolderPath("Folder C");
            p.setPinned(false);
        }

        assertEquals("Folder C", p1.getFolderPath());
        assertEquals("Folder C", p2.getFolderPath());
        assertEquals("Folder B", p3.getFolderPath());
        assertFalse(p1.isPinned());
        assertFalse(p2.isPinned());
        assertTrue(p3.isPinned());
    }

    @Test
    public void testNestedSubfoldersHierarchyOperations() {
        BoardManager bm = BoardManager.getInstance();
        bm.getPages().clear();

        BoardPage rootPage = bm.addPage("Root Page", "");
        BoardPage l1Page = bm.addPage("Level 1 Page", "reel");
        BoardPage l2Page = bm.addPage("Level 2 Page", "reel/sub_reel");
        BoardPage l3Page = bm.addPage("Level 3 Page", "reel/sub_reel/deep");

        assertEquals("", rootPage.getFolderPath());
        assertEquals("reel", l1Page.getFolderPath());
        assertEquals("reel/sub_reel", l2Page.getFolderPath());
        assertEquals("reel/sub_reel/deep", l3Page.getFolderPath());

        // Rename reel -> archive/reel
        bm.renameFolder("reel", "archive/reel");
        assertEquals("archive/reel", l1Page.getFolderPath());
        assertEquals("archive/reel/sub_reel", l2Page.getFolderPath());
        assertEquals("archive/reel/sub_reel/deep", l3Page.getFolderPath());

        // Delete archive/reel/sub_reel
        bm.deleteFolder("archive/reel/sub_reel");
        assertEquals("archive/reel", l1Page.getFolderPath());
        assertEquals("", l2Page.getFolderPath());
        assertEquals("", l3Page.getFolderPath());
    }

    @Test
    public void testFolderMoveOperations() {
        BoardManager bm = BoardManager.getInstance();
        bm.getPages().clear();

        BoardPage p1 = bm.addPage("P1", "electronics/chips");
        BoardPage p2 = bm.addPage("P2", "electronics/chips/wafer");
        BoardPage p3 = bm.addPage("P3", "factory");

        // Move chips under factory -> factory/chips
        boolean moved = bm.moveFolder("electronics/chips", "factory");
        assertTrue(moved);
        assertEquals("factory/chips", p1.getFolderPath());
        assertEquals("factory/chips/wafer", p2.getFolderPath());
        assertEquals("factory", p3.getFolderPath());

        // Move factory/chips to root ("") -> chips
        boolean movedToRoot = bm.moveFolder("factory/chips", "");
        assertTrue(movedToRoot);
        assertEquals("chips", p1.getFolderPath());
        assertEquals("chips/wafer", p2.getFolderPath());

        // Attempt invalid circular move: chips into chips/wafer -> should fail
        boolean circular = bm.moveFolder("chips", "chips/wafer");
        assertFalse(circular);
        assertEquals("chips", p1.getFolderPath());
        assertEquals("chips/wafer", p2.getFolderPath());
    }
}
