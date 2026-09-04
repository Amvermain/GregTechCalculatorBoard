package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class GridSnapAndUsabilityTest {

    @Test
    public void testBoardManagerGridSnapPersistence() throws IOException {
        BoardManager bm = BoardManager.getInstance();
        bm.resetToDefault();

        Assertions.assertFalse(bm.isGridSnapEnabled());
        Assertions.assertEquals(16, bm.getGridSnapSize());

        bm.setGridSnapEnabled(true);
        bm.setGridSnapSize(32);

        File tempFile = Files.createTempFile("gtcalc_board_test", ".nbt").toFile();
        try {
            bm.saveToFile(tempFile);

            bm.resetToDefault();
            Assertions.assertFalse(bm.isGridSnapEnabled());

            bm.loadFromFile(tempFile);
            Assertions.assertTrue(bm.isGridSnapEnabled());
            Assertions.assertEquals(32, bm.getGridSnapSize());
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    @Test
    public void testCustomParallelOverridesDefaultParallel() {
        RecipeNode node = RecipeNode.create("Centrifuge", 20.0, 30.0, GTVoltageTier.MV);
        node.setParallel(4);
        Assertions.assertEquals(4, node.getTotalParallel());

        // Custom parallel 3 (e.g. specialized batch run)
        node.setCustomParallel(3);
        Assertions.assertEquals(3, node.getTotalParallel());

        // Reset custom parallel -> fallback to hardware parallel (4)
        node.setCustomParallel(0);
        Assertions.assertEquals(4, node.getTotalParallel());
    }

    @Test
    public void testBoardManagerDebugInfoPersistence() throws IOException {
        BoardManager bm = BoardManager.getInstance();
        bm.resetToDefault();

        Assertions.assertFalse(bm.isShowDebugInfo(), "Debug info must be disabled by default");

        bm.toggleDebugInfo();
        Assertions.assertTrue(bm.isShowDebugInfo());

        bm.toggleDebugInfo();
        Assertions.assertFalse(bm.isShowDebugInfo());

        bm.setShowDebugInfo(true);

        File tempFile = Files.createTempFile("gtcalc_board_debug_test", ".nbt").toFile();
        try {
            bm.saveToFile(tempFile);

            bm.resetToDefault();
            Assertions.assertFalse(bm.isShowDebugInfo(), "resetToDefault must reset showDebugInfo to false");

            bm.loadFromFile(tempFile);
            Assertions.assertTrue(bm.isShowDebugInfo(), "loadFromFile must restore showDebugInfo state");
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}
