package com.gtceu.calcboard.storage;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.IFolderChangeListener;
import com.gtceu.calcboard.api.storage.IPageLifecycleListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FolderLifecycleListenerTest {

    @BeforeEach
    public void setUp() {
        BoardManager.getInstance().getPages().clear();
    }

    @Test
    public void testFolderChangeEventsDispatch() {
        BoardManager bm = BoardManager.getInstance();
        List<IFolderChangeListener.FolderChangeEvent> events = new ArrayList<>();

        IFolderChangeListener listener = events::add;
        bm.addFolderChangeListener(listener);

        BoardPage p1 = bm.addPage("Page 1", "electronics/wafers");
        BoardPage p2 = bm.addPage("Page 2", "electronics/chips");

        // 1. Created notification
        bm.notifyFolderCreated("power/generators");
        assertEquals(1, events.size());
        assertEquals(IFolderChangeListener.FolderAction.CREATED, events.get(0).action());
        assertEquals("power/generators", events.get(0).newPath());

        // 2. Renamed
        bm.renameFolder("electronics", "tech/electronics");
        assertEquals(2, events.size());
        assertEquals(IFolderChangeListener.FolderAction.RENAMED, events.get(1).action());
        assertEquals("electronics", events.get(1).oldPath());
        assertEquals("tech/electronics", events.get(1).newPath());

        // 3. Moved
        bm.moveFolder("tech/electronics", "archive");
        assertEquals(4, events.size()); // renameFolder inside moveFolder dispatches RENAMED then MOVED
        assertEquals(IFolderChangeListener.FolderAction.MOVED, events.get(3).action());
        assertEquals("tech/electronics", events.get(3).oldPath());
        assertEquals("archive", events.get(3).newPath());

        // 4. Deleted
        bm.deleteFolder("archive/tech/electronics");
        assertEquals(5, events.size());
        assertEquals(IFolderChangeListener.FolderAction.DELETED, events.get(4).action());
        assertEquals("archive/tech/electronics", events.get(4).oldPath());

        // Remove listener
        bm.removeFolderChangeListener(listener);
        bm.notifyFolderCreated("another");
        assertEquals(5, events.size(), "Removed listener should not receive events");
    }

    @Test
    public void testPageLifecycleEventsDispatch() {
        BoardManager bm = BoardManager.getInstance();
        List<String> eventLogs = new ArrayList<>();

        IPageLifecycleListener listener = new IPageLifecycleListener() {
            @Override
            public void onPageAdded(BoardPage page, int index) {
                eventLogs.add("ADDED:" + page.getName() + "@" + index);
            }

            @Override
            public void onPageSwitched(BoardPage previousPage, BoardPage newPage, int newIndex) {
                eventLogs.add("SWITCHED:" + (previousPage != null ? previousPage.getName() : "null") + "->" + newPage.getName());
            }

            @Override
            public void onPageRemoved(BoardPage removedPage, int oldIndex) {
                eventLogs.add("REMOVED:" + removedPage.getName() + "@" + oldIndex);
            }

            @Override
            public void onPageFolderChanged(BoardPage page, String oldFolderPath, String newFolderPath) {
                eventLogs.add("FOLDER:" + page.getName() + "[" + oldFolderPath + "->" + newFolderPath + "]");
            }
        };

        bm.addPageLifecycleListener(listener);

        BoardPage p1 = bm.addPage("Factory Alpha", "");
        assertTrue(eventLogs.contains("ADDED:Factory Alpha@0"));

        BoardPage p2 = bm.addPage("Factory Beta", "Sub");
        assertTrue(eventLogs.contains("ADDED:Factory Beta@1"));

        bm.switchPage(0);
        assertTrue(eventLogs.contains("SWITCHED:Factory Beta->Factory Alpha"));

        bm.movePageToFolder(0, "Deep/Folder");
        assertTrue(eventLogs.contains("FOLDER:Factory Alpha[->Deep/Folder]"));

        bm.removePage(0);
        assertTrue(eventLogs.contains("REMOVED:Factory Alpha@0"));

        bm.removePageLifecycleListener(listener);
    }
}
