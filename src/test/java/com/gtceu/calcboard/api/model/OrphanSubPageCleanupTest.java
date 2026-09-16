package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.PageType;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OrphanSubPageCleanupTest {

    @BeforeEach
    public void setup() {
        BoardManager.getInstance().resetToDefault();
    }

    @Test
    public void testParentPageDeletionCascadesToChildSubPage() {
        BoardPage main1 = BoardManager.getInstance().getActivePage();
        main1.setName("Main 1");

        BoardPage sub1 = BoardManager.getInstance().addPage("Sub 1");
        sub1.setPageType(PageType.MODULE);
        sub1.setParentPageId(main1.getId());

        BoardPage main2 = BoardManager.getInstance().addPage("Main 2");

        Assertions.assertEquals(3, BoardManager.getInstance().getPages().size());

        BoardManager.getInstance().removePage(main1.getId());

        Assertions.assertEquals(1, BoardManager.getInstance().getPages().size());
        Assertions.assertEquals(main2.getId(), BoardManager.getInstance().getPages().get(0).getId());
        Assertions.assertTrue(BoardManager.getInstance().getPage(sub1.getId()).isEmpty());
    }

    @Test
    public void testSoleParentPageDeletionReplacesWithDefaultPageAndCleansSubPage() {
        BoardPage mainPage = BoardManager.getInstance().getActivePage();
        mainPage.setName("Tutorial Chapter");

        BoardPage subPage = BoardManager.getInstance().addPage("Sub Module Page");
        subPage.setPageType(PageType.MODULE);
        subPage.setParentPageId(mainPage.getId());

        Assertions.assertEquals(2, BoardManager.getInstance().getPages().size());

        BoardManager.getInstance().removePage(mainPage.getId());

        Assertions.assertEquals(1, BoardManager.getInstance().getPages().size());
        BoardPage remaining = BoardManager.getInstance().getPages().get(0);
        Assertions.assertFalse(remaining.isModuleSubPage());
        Assertions.assertNotEquals(subPage.getId(), remaining.getId());
        Assertions.assertTrue(BoardManager.getInstance().getPage(subPage.getId()).isEmpty());
    }

    @Test
    public void testCleanupOrphanSubpagesWithBrokenParent() {
        BoardPage brokenSub = BoardManager.getInstance().addPage("Broken Sub");
        brokenSub.setPageType(PageType.MODULE);
        brokenSub.setParentPageId("non-existent-parent-id");

        BoardManager.getInstance().cleanupOrphanSubpages();

        Assertions.assertTrue(BoardManager.getInstance().getPage(brokenSub.getId()).isEmpty());
        Assertions.assertFalse(BoardManager.getInstance().getPages().stream().anyMatch(BoardPage::isModuleSubPage));
    }

    @Test
    public void testDeserializeNBTPrunesOrphanSubpages() {
        BoardPage main = BoardManager.getInstance().getActivePage();
        main.setName("Root");

        BoardPage orphanSub = BoardManager.getInstance().addPage("Orphan Sub");
        orphanSub.setPageType(PageType.MODULE);
        orphanSub.setParentPageId("deleted-parent");

        CompoundTag tag = new CompoundTag();
        BoardManager.getInstance().getPageManager().serializeNBT(tag);

        BoardManager.getInstance().getPageManager().deserializeNBT(tag);

        Assertions.assertTrue(BoardManager.getInstance().getPage(orphanSub.getId()).isEmpty());
    }

    @Test
    public void testChapter3ScenarioParentDeletion() {
        BoardPage defaultPage = BoardManager.getInstance().getActivePage();
        defaultPage.setName("My Factory");

        BoardPage chapter3Page = BoardManager.getInstance().addPage("Academy Chapter 3");
        BoardPage subPage = BoardManager.getInstance().addPage("Sub Module Page");
        subPage.setPageType(PageType.MODULE);
        subPage.setParentPageId(chapter3Page.getId());

        BoardManager.getInstance().removePage(chapter3Page.getId());

        Assertions.assertEquals(1, BoardManager.getInstance().getPages().size());
        Assertions.assertEquals("My Factory", BoardManager.getInstance().getActivePage().getName());
        Assertions.assertFalse(BoardManager.getInstance().getActivePage().isModuleSubPage());
        Assertions.assertTrue(BoardManager.getInstance().getPage(subPage.getId()).isEmpty());
    }
}
