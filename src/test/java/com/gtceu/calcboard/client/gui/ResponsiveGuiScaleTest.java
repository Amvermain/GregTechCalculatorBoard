package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.BoardGuiScale;
import com.gtceu.calcboard.api.type.ToolbarDisplayMode;
import com.gtceu.calcboard.client.gui.util.BoardViewportTransform;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Headless unit tests for RFC-017: Responsive GUI Scale, Viewport Transform, and Adaptive Layout.
 */
public class ResponsiveGuiScaleTest {

    @Test
    public void testBoardGuiScaleResolution() {
        // AUTO follows game scale when <= 2, but adapts down to 2 on 1080p when game scale is 3 or 4
        Assertions.assertEquals(1, BoardGuiScale.AUTO.resolveEffectiveScale(1));
        Assertions.assertEquals(2, BoardGuiScale.AUTO.resolveEffectiveScale(2));
        Assertions.assertEquals(2, BoardGuiScale.AUTO.resolveEffectiveScale(3));
        Assertions.assertEquals(2, BoardGuiScale.AUTO.resolveEffectiveScale(4));

        // Fixed scales override game GUI scale
        Assertions.assertEquals(1, BoardGuiScale.SCALE_1X.resolveEffectiveScale(3));
        Assertions.assertEquals(2, BoardGuiScale.SCALE_2X.resolveEffectiveScale(3));
        Assertions.assertEquals(3, BoardGuiScale.SCALE_3X.resolveEffectiveScale(1));
        Assertions.assertEquals(4, BoardGuiScale.SCALE_4X.resolveEffectiveScale(2));

        // Fallbacks for zero or negative values
        Assertions.assertEquals(1, BoardGuiScale.AUTO.resolveEffectiveScale(0));
        Assertions.assertEquals(2, BoardGuiScale.SCALE_2X.resolveEffectiveScale(0));
    }

    @Test
    public void testViewportTransformIdentityWhenScalesMatch() {
        // Game Scale = 2, Board Scale = 2 on 1920x1080 -> S = 1.0 (Identity)
        BoardViewportTransform transform = new BoardViewportTransform(2, 2, 1920, 1080);

        Assertions.assertFalse(transform.isScaled());
        Assertions.assertEquals(1.0f, transform.getRenderScale(), 1e-6f);
        Assertions.assertEquals(960, transform.getVirtualWidth());
        Assertions.assertEquals(540, transform.getVirtualHeight());

        // Mouse coordinates remain unchanged
        Assertions.assertEquals(480.0, transform.toVirtualX(480.0), 1e-6);
        Assertions.assertEquals(270.0, transform.toVirtualY(270.0), 1e-6);
        Assertions.assertEquals(480.0, transform.toGameX(480.0), 1e-6);
        Assertions.assertEquals(270.0, transform.toGameY(270.0), 1e-6);
    }

    @Test
    public void testViewportTransformDownscalingLargeGui() {
        // Physical resolution 1920x1080
        // Game GUI scale = 4 -> gameWidth = 480, gameHeight = 270
        // Desired Board GUI scale = 2 -> S = 2/4 = 0.5
        // Virtual resolution becomes 1920 / 2 = 960, 1080 / 2 = 540
        BoardViewportTransform transform = new BoardViewportTransform(4, 2, 1920, 1080);

        Assertions.assertTrue(transform.isScaled());
        Assertions.assertEquals(0.5f, transform.getRenderScale(), 1e-6f);
        Assertions.assertEquals(960, transform.getVirtualWidth());
        Assertions.assertEquals(540, transform.getVirtualHeight());

        // Mouse click at Game (100, 150) projects to Virtual (200, 300)
        Assertions.assertEquals(200.0, transform.toVirtualX(100.0), 1e-6);
        Assertions.assertEquals(300.0, transform.toVirtualY(150.0), 1e-6);

        // Virtual (200, 300) projects back to Game (100, 150)
        Assertions.assertEquals(100.0, transform.toGameX(200.0), 1e-6);
        Assertions.assertEquals(150.0, transform.toGameY(300.0), 1e-6);
    }

    @Test
    public void testViewportTransformUpscalingSmallGui() {
        // Physical resolution 1920x1080
        // Game GUI scale = 2 -> gameWidth = 960, gameHeight = 540
        // Desired Board GUI scale = 4 -> S = 4/2 = 2.0
        // Virtual resolution becomes 1920 / 4 = 480, 1080 / 4 = 270
        BoardViewportTransform transform = new BoardViewportTransform(2, 4, 1920, 1080);

        Assertions.assertTrue(transform.isScaled());
        Assertions.assertEquals(2.0f, transform.getRenderScale(), 1e-6f);
        Assertions.assertEquals(480, transform.getVirtualWidth());
        Assertions.assertEquals(270, transform.getVirtualHeight());

        // Mouse click at Game (200, 300) projects to Virtual (100, 150)
        Assertions.assertEquals(100.0, transform.toVirtualX(200.0), 1e-6);
        Assertions.assertEquals(150.0, transform.toVirtualY(300.0), 1e-6);
    }

    @Test
    public void testBoardManagerNbtPersistence() {
        BoardManager bm = BoardManager.getInstance();

        // Test cycle operations
        bm.setBoardGuiScale(BoardGuiScale.SCALE_2X);
        bm.setToolbarDisplayMode(ToolbarDisplayMode.COMPACT);
        bm.setAddonCatalogListView(true);

        CompoundTag tag = bm.serializePreferencesNBT();

        // Verify raw NBT tags
        Assertions.assertEquals("SCALE_2X", tag.getString("boardGuiScale"));
        Assertions.assertEquals("COMPACT", tag.getString("toolbarDisplayMode"));
        Assertions.assertTrue(tag.getBoolean("addonCatalogListView"));

        // Reset to default
        bm.setBoardGuiScale(BoardGuiScale.AUTO);
        bm.setToolbarDisplayMode(ToolbarDisplayMode.AUTO);
        bm.setAddonCatalogListView(false);

        // Load back from NBT
        bm.deserializePreferencesNBT(tag);
        Assertions.assertEquals(BoardGuiScale.SCALE_2X, bm.getBoardGuiScale());
        Assertions.assertEquals(ToolbarDisplayMode.COMPACT, bm.getToolbarDisplayMode());
        Assertions.assertTrue(bm.isAddonCatalogListView());

        // Cleanup
        bm.setBoardGuiScale(BoardGuiScale.AUTO);
        bm.setToolbarDisplayMode(ToolbarDisplayMode.AUTO);
        bm.setAddonCatalogListView(false);
    }
}
