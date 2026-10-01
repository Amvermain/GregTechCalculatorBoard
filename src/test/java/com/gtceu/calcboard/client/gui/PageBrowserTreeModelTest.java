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
        modulePage.setParentPageId(BoardManager.getInstance().getActivePage().getId());
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

    @Test
    public void testTeamWorkspaceFolderTreeHierarchy() {
        com.gtceu.calcboard.client.team.ClientWorkspaceState state = com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance();
        java.util.UUID teamId = java.util.UUID.randomUUID();
        var metaList = java.util.List.of(
                new com.gtceu.calcboard.network.packet.s2c.S2CSyncWorkspaceMetaPacket.PageMeta("p1", "Ore Processing", 1, null, "", 0L, "Metals"),
                new com.gtceu.calcboard.network.packet.s2c.S2CSyncWorkspaceMetaPacket.PageMeta("p2", "Titanium Line", 1, null, "", 0L, "Metals/HighTier"),
                new com.gtceu.calcboard.network.packet.s2c.S2CSyncWorkspaceMetaPacket.PageMeta("p3", "Overview Hub", 1, null, "", 0L, "")
        );
        state.updateWorkspaceMeta(new com.gtceu.calcboard.network.packet.s2c.S2CSyncWorkspaceMetaPacket(teamId, "Alpha Team", 1, metaList));
        state.setCurrentMode(com.gtceu.calcboard.client.team.ClientWorkspaceState.WorkspaceMode.TEAM);

        try {
            PageBrowserTreeModel.FolderTreeNode tree = PageBrowserTreeModel.buildFolderTree("");
            Assertions.assertEquals(1, tree.directPages.size());
            Assertions.assertEquals("Overview Hub", tree.directPages.get(0).page().getName());

            Assertions.assertTrue(tree.subFolders.containsKey("Metals"));
            PageBrowserTreeModel.FolderTreeNode metalsFolder = tree.subFolders.get("Metals");
            Assertions.assertEquals(1, metalsFolder.directPages.size());
            Assertions.assertEquals("Ore Processing", metalsFolder.directPages.get(0).page().getName());

            Assertions.assertTrue(metalsFolder.subFolders.containsKey("HighTier"));
            PageBrowserTreeModel.FolderTreeNode highTierFolder = metalsFolder.subFolders.get("HighTier");
            Assertions.assertEquals("Metals/HighTier", highTierFolder.folderPath);
            Assertions.assertEquals(1, highTierFolder.directPages.size());
            Assertions.assertEquals("Titanium Line", highTierFolder.directPages.get(0).page().getName());
        } finally {
            state.clear();
        }
    }
}
