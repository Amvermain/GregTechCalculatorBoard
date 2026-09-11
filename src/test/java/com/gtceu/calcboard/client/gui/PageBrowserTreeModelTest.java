package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.PageType;
import com.gtceu.calcboard.client.gui.widget.PageBrowserTreeModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PageBrowserTreeModelTest {

    @BeforeEach
    public void setup() {
        BoardManager.getInstance().getPageManager().resetToDefault();
        BoardManager.getInstance().getPageManager().getPages().get(0).setName("Main Factory");
    }

    @Test
    public void testModuleSubPageIsolationInTree() {
        BoardPage modulePage = new BoardPage("sub_epoxy", "Epoxy Module", new com.gtceu.calcboard.api.model.FlowGraph());
        modulePage.setPageType(PageType.MODULE);
        BoardManager.getInstance().getPageManager().addPage(modulePage);

        PageBrowserTreeModel.FolderTreeNode tree = PageBrowserTreeModel.buildFolderTree("");
        Assertions.assertEquals(1, tree.directPages.size());
        Assertions.assertEquals("Main Factory", tree.directPages.get(0).page().getName());

        Assertions.assertEquals(1, tree.subFolders.size());
        String moduleFolderName = tree.subFolders.keySet().iterator().next();
        Assertions.assertTrue(moduleFolderName.contains("Process Modules") || moduleFolderName.contains("공정 모듈") || moduleFolderName.contains("module_section"));

        PageBrowserTreeModel.FolderTreeNode moduleFolder = tree.subFolders.get(moduleFolderName);
        Assertions.assertEquals(1, moduleFolder.directPages.size());
        Assertions.assertEquals("Epoxy Module", moduleFolder.directPages.get(0).page().getName());
    }
}
