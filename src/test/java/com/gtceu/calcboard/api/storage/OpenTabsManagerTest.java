package com.gtceu.calcboard.api.storage;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class OpenTabsManagerTest {

    private BoardManager boardManager;

    @BeforeEach
    public void setup() {
        boardManager = BoardManager.getInstance();
        boardManager.resetToDefault();
    }

    @Test
    public void testInitialStateHasOneOpenTab() {
        List<BoardPage> openPages = boardManager.getOpenPages();
        Assertions.assertEquals(1, openPages.size());
        Assertions.assertEquals(boardManager.getActivePage().getId(), openPages.get(0).getId());
    }

    @Test
    public void testAddPageAutomaticallyOpensTab() {
        BoardPage page2 = boardManager.addPage("Page 2", "ae2");

        List<BoardPage> openPages = boardManager.getOpenPages();
        Assertions.assertEquals(2, openPages.size());
        Assertions.assertTrue(boardManager.isTabOpen(page2.getId()));
        Assertions.assertEquals(page2.getId(), boardManager.getActivePage().getId());
    }

    @Test
    public void testCloseTabUndocksWithoutDeletingPage() {
        BoardPage page1 = boardManager.getActivePage();
        BoardPage page2 = boardManager.addPage("Page 2", "circuits");
        BoardPage page3 = boardManager.addPage("Page 3", "materials");

        Assertions.assertEquals(3, boardManager.getOpenPages().size());
        Assertions.assertEquals(3, boardManager.getPages().size());

        boardManager.closeTab(page2.getId());

        Assertions.assertFalse(boardManager.isTabOpen(page2.getId()));
        Assertions.assertEquals(2, boardManager.getOpenPages().size());

        Assertions.assertEquals(3, boardManager.getPages().size());
        Assertions.assertTrue(boardManager.getPage(page2.getId()).isPresent());
        Assertions.assertEquals("circuits", boardManager.getPage(page2.getId()).get().getFolderPath());
    }

    @Test
    public void testClosingActiveTabSwitchesToAdjacentTab() {
        BoardPage page1 = boardManager.getActivePage();
        BoardPage page2 = boardManager.addPage("Page 2");
        BoardPage page3 = boardManager.addPage("Page 3");

        Assertions.assertEquals(page3.getId(), boardManager.getActivePage().getId());

        boardManager.closeTab(page3.getId());

        Assertions.assertEquals(page2.getId(), boardManager.getActivePage().getId());

        boardManager.closeTab(page2.getId());

        Assertions.assertEquals(page1.getId(), boardManager.getActivePage().getId());
    }

    @Test
    public void testOpenPageDocksAndSwitches() {
        BoardPage page1 = boardManager.getActivePage();
        BoardPage page2 = boardManager.addPage("Page 2", "ae2");
        boardManager.closeTab(page2.getId());

        Assertions.assertFalse(boardManager.isTabOpen(page2.getId()));

        boolean opened = boardManager.openPage(page2.getId());
        Assertions.assertTrue(opened);
        Assertions.assertTrue(boardManager.isTabOpen(page2.getId()));
        Assertions.assertEquals(page2.getId(), boardManager.getActivePage().getId());
    }

    @Test
    public void testCloseOtherTabs() {
        BoardPage page1 = boardManager.getActivePage();
        BoardPage page2 = boardManager.addPage("Page 2");
        BoardPage page3 = boardManager.addPage("Page 3");

        boardManager.closeOtherTabs(page2.getId());

        Assertions.assertEquals(1, boardManager.getOpenPages().size());
        Assertions.assertEquals(page2.getId(), boardManager.getOpenPages().get(0).getId());
        Assertions.assertEquals(page2.getId(), boardManager.getActivePage().getId());
        Assertions.assertEquals(3, boardManager.getPages().size());
    }

    @Test
    public void testPermanentDeleteCleansUpOpenTabs() {
        BoardPage page1 = boardManager.getActivePage();
        BoardPage page2 = boardManager.addPage("Page 2");

        Assertions.assertEquals(2, boardManager.getOpenPages().size());
        Assertions.assertEquals(2, boardManager.getPages().size());

        int idx = boardManager.getPages().indexOf(page2);
        boardManager.removePage(idx);

        Assertions.assertEquals(1, boardManager.getOpenPages().size());
        Assertions.assertEquals(1, boardManager.getPages().size());
        Assertions.assertFalse(boardManager.isTabOpen(page2.getId()));
    }

    @Test
    public void testNbtPersistenceForOpenTabs() throws IOException {
        BoardPage page1 = boardManager.getActivePage();
        BoardPage page2 = boardManager.addPage("Page 2", "ae2");
        BoardPage page3 = boardManager.addPage("Page 3", "ae2");

        boardManager.closeTab(page2.getId());

        File tempFile = File.createTempFile("calcboard_opentabs_test", ".nbt");
        tempFile.deleteOnExit();

        boardManager.saveToFile(tempFile);

        boardManager.resetToDefault();
        boardManager.loadFromFile(tempFile);

        Assertions.assertEquals(3, boardManager.getPages().size());
        List<BoardPage> openPages = boardManager.getOpenPages();
        Assertions.assertEquals(2, openPages.size());
        Assertions.assertTrue(boardManager.isTabOpen(page1.getId()));
        Assertions.assertFalse(boardManager.isTabOpen(page2.getId()));
        Assertions.assertTrue(boardManager.isTabOpen(page3.getId()));
    }
}
