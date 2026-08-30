package com.gtceu.calcboard.server.storage;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

/**
 * Verifies RFC-003: Atomic File Replacement saving pattern in BoardManager.
 */
public class AtomicFileSaveTest {

    @Test
    @DisplayName("RFC-003: BoardManager atomic saveToFile creates clean file without leaving orphan tmp files")
    void testAtomicSaveToFile(@TempDir Path tempDir) {
        File saveFile = tempDir.resolve("calcboard_save.nbt").toFile();
        File tmpFile = tempDir.resolve("calcboard_save.nbt.tmp").toFile();

        BoardPage testPage = new BoardPage("page_atomic", "Atomic Test", new FlowGraph());
        RecipeNode node = RecipeNode.create("Centrifuge", 10.0, 30.0, com.gtceu.calcboard.api.type.GTVoltageTier.LV);
        node.setMachineCount(4.0);
        testPage.getGraph().addNode(node);

        BoardManager manager = BoardManager.getInstance();
        manager.getPages().clear();
        manager.getPages().set(0, testPage);

        boolean success = manager.saveToFile(saveFile);
        Assertions.assertTrue(success, "saveToFile must return true on valid write");
        Assertions.assertTrue(saveFile.exists(), "Target save file must exist after atomic move");
        Assertions.assertTrue(saveFile.length() > 0, "Target save file must not be 0 bytes");
        Assertions.assertFalse(tmpFile.exists(), "Temporary .tmp file must not remain after successful atomic move");

        // Verify loaded data integrity
        manager.getPages().clear();
        boolean loadSuccess = manager.loadFromFile(saveFile);
        System.out.println("Loaded pages count: " + manager.getPages().size());
        for (BoardPage p : manager.getPages()) {
            System.out.println("Page name: " + p.getName() + " id: " + p.getId());
        }
        Assertions.assertTrue(loadSuccess, "loadFromFile must succeed on atomically saved file");
        Assertions.assertEquals(1, manager.getPages().size());
        Assertions.assertEquals("Atomic Test", manager.getPages().get(0).getName());
        Assertions.assertEquals(1, manager.getPages().get(0).getGraph().getNodes().size());
    }
}
